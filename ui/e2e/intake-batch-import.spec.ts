import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const pendingIntake=(overrides:Record<string,unknown>={})=>({
  id:'retry-intake',batchId:'batch-a',sourceChannelId:'source-a',sourceRecruiterUserId:'hr-a',defaultOwnerUserId:'hr-a',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',handledByUserId:null,
  originalFileName:'启动失败.pdf',mediaType:'application/pdf',sizeBytes:2048,contentSha256:'a'.repeat(64),scanStatus:'CLEAN',failureCode:null,version:1,
  createdAt:'2026-09-10T01:00:00Z',jdVersionId:'jd-v2',jdTitle:'高级前端工程师',status:'PENDING_IMPORT',importStatus:'PENDING',candidateId:null,resumeVersionId:null,
  ...overrides,
});
const parsedJd={id:'jd-a',title:'高级前端工程师（招聘平台与复杂业务系统方向）',currentVersionNo:2,currentParseStatus:'SUCCEEDED',ownerUserId:'hr-a',createTime:'2026-09-01T00:00:00Z'};

test('自动启动失败后从入库列表重试，不重复上传文件',async({page})=>{
  await reviewWorkspace(page);
  let uploads=0,imports=0;
  await page.route('**/api/v1/jds?*',route=>route.fulfill({json:{items:[parsedJd],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/source-channels?*',route=>route.fulfill({json:{items:[{id:'source-a',name:'内推',status:'ACTIVE'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intake-batches',route=>route.fulfill({json:{id:'batch-a'}}));
  await page.route('**/api/v1/intake-batches/batch-a/files',route=>{uploads++;return route.fulfill({json:pendingIntake()});});
  await page.route('**/api/v1/intake-batches/batch-a/complete',route=>route.fulfill({json:{id:'batch-a'}}));
  await page.route('**/api/v1/intakes?*',route=>route.fulfill({json:{items:[pendingIntake()],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intakes/retry-intake',route=>route.fulfill({json:pendingIntake()}));
  await page.route('**/api/v1/intakes/retry-intake/import',route=>{imports++;if(imports===1)return route.fulfill({status:503,json:{title:'服务暂不可用',detail:'启动失败，请稍后重试',status:503,code:'TEST_RETRYABLE',recoverable:true}});expect(route.request().postDataJSON()).toEqual({version:'1'});return route.fulfill({json:pendingIntake({status:'PARSING',importStatus:'PROCESSING',handledByUserId:'hr-a'})});});

  await page.goto('/talents');
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  const drawer=page.getByRole('dialog',{name:'批量导入简历'});
  await drawer.getByLabel('上传来源渠道').click();
  await drawer.getByLabel('上传来源渠道').press('ArrowDown');
  await drawer.getByLabel('上传来源渠道').press('Enter');
  await drawer.getByRole('combobox',{name:'选择已解析成功的 JD'}).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'高级前端工程师'}).click();
  await drawer.locator('input[type=file]').setInputFiles({name:'启动失败.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 retry')});
  await drawer.getByRole('button',{name:'开始导入',exact:true}).click();
  await expect(drawer.getByText(/已接收，自动入库失败/)).toBeVisible();
  await expect(drawer.getByText(/无需重新上传/)).toBeVisible();
  expect(uploads).toBe(1);expect(imports).toBe(1);

  await drawer.getByRole('button',{name:'关闭',exact:true}).click();
  await expect(drawer).not.toBeVisible();
  await page.getByRole('button',{name:'收件校验',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'收件校验',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'重试导入',exact:true}).click();
  await expect(page.getByText('已开始解析并入库')).toBeVisible();
  expect(uploads).toBe(1);expect(imports).toBe(2);
  await expect(page.getByRole('button',{name:'批量入库'})).toHaveCount(0);
  await expect(page.locator('.intake-validation-page tbody input[type="checkbox"]')).toHaveCount(0);
});

test('重复内容收件仍自动启动，并在列表提示核对历史来源',async({page})=>{
  await reviewWorkspace(page);
  let imports=0;
  const duplicate=pendingIntake({id:'duplicate-intake',originalFileName:'重复简历.pdf',duplicateContent:true});
  await page.route('**/api/v1/jds?*',route=>route.fulfill({json:{items:[parsedJd],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/source-channels?*',route=>route.fulfill({json:{items:[{id:'source-a',name:'内推',status:'ACTIVE'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intake-batches',route=>route.fulfill({json:{id:'batch-a'}}));
  await page.route('**/api/v1/intake-batches/batch-a/files',route=>route.fulfill({json:duplicate}));
  await page.route('**/api/v1/intake-batches/batch-a/complete',route=>route.fulfill({json:{id:'batch-a'}}));
  await page.route('**/api/v1/intakes/duplicate-intake',route=>route.fulfill({json:duplicate}));
  await page.route('**/api/v1/intakes/duplicate-intake/import',route=>{imports++;return route.fulfill({json:{...duplicate,status:'PARSING',importStatus:'PROCESSING'}});});
  await page.route('**/api/v1/intakes?*',route=>route.fulfill({json:{items:[duplicate],hasMore:false,nextCursor:null}}));

  await page.goto('/talents');
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  const drawer=page.getByRole('dialog',{name:'批量导入简历'});
  await drawer.getByLabel('上传来源渠道').click();
  await drawer.getByLabel('上传来源渠道').press('ArrowDown');
  await drawer.getByLabel('上传来源渠道').press('Enter');
  await drawer.getByRole('combobox',{name:'选择已解析成功的 JD'}).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'高级前端工程师'}).click();
  await drawer.locator('input[type=file]').setInputFiles({name:'重复简历.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 duplicate')});
  await drawer.getByRole('button',{name:'开始导入',exact:true}).click();
  await expect(drawer.getByRole('list',{name:'本次上传文件结果'})).toContainText('重复简历.pdf：解析中；检测到同内容收件，请核对历史来源');
  expect(imports).toBe(1);

  await drawer.getByRole('button',{name:'查看收件校验',exact:true}).click();
  await expect(page.getByText('同内容重复收件')).toBeVisible();
  await expect(page.getByText('请核对历史来源，避免重复联系或推荐')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'批量入库'})).toHaveCount(0);
  expect((await new AxeBuilder({page}).include('.intake-validation-page').analyze()).violations).toEqual([]);
});

test('收件文件保持紧凑，长文件名可悬停或聚焦查看',async({page},testInfo)=>{
  await page.setViewportSize({width:1440,height:900});
  await reviewWorkspace(page);
  const longName='【售后工程师_北京5-10K】基础设施值班与网络运维候选人_十年以上项目经验_完整原始文件名称.pdf';
  await page.route('**/api/v1/intakes?*',route=>route.fulfill({json:{items:[
    pendingIntake({originalFileName:longName}),
    pendingIntake({id:'duplicate',originalFileName:'重复简历.pdf',duplicateContent:true,status:'COMPLETED',importStatus:'IMPORTED'}),
    pendingIntake({id:'failed',originalFileName:'解析异常.pdf',status:'FAILED_RETRYABLE',failureCode:'MODEL_JSON_INVALID'}),
  ],hasMore:false,nextCursor:null}}));
  await page.goto('/settings/intakes');
  const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
  const filename=drawer.locator('.intake-file-name').filter({hasText:longName});
  await expect(filename).toBeVisible();
  await expect(drawer.locator('.ledger-heading')).toHaveCount(0);
  await filename.focus();
  await expect(page.getByRole('tooltip')).toHaveText(longName);
  await filename.blur();
  await expect(page.getByRole('tooltip')).not.toBeVisible();
  await filename.hover();
  await expect(page.getByRole('tooltip')).toHaveText(longName);
  await page.mouse.move(0,0);
  await expect(drawer.getByText('异常详情',{exact:true})).toHaveCount(0);
  await expect(drawer.locator('details[open]')).toHaveCount(0);
  await expect(drawer.getByText('同内容重复收件',{exact:true})).toHaveCount(1);
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:900});
    await expect.poll(()=>drawer.evaluate(element=>element.getBoundingClientRect().left)).toBeGreaterThanOrEqual(0);
    expect(await filename.evaluate(element=>element.clientHeight<=parseFloat(getComputedStyle(element).lineHeight)*2+1)).toBe(true);
    expect(await drawer.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBe(true);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:testInfo.outputPath(`intake-compact-${width}.png`),fullPage:true});
  }
  expect((await new AxeBuilder({page}).include('.intake-drawer').analyze()).violations).toEqual([]);
});

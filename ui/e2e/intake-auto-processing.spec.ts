import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('pending resume can retry import once and refreshes to imported without scoring',async({page},testInfo)=>{
  await reviewWorkspace(page);
  let reads=0,imports=0,started=false;
  const base={id:'auto-intake',batchId:'batch',sourceChannelId:'source',sourceRecruiterUserId:'hr-a',defaultOwnerUserId:'hr-a',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',handledByUserId:null,originalFileName:'自动导入候选人.pdf',mediaType:'application/pdf',sizeBytes:2048,contentSha256:'a'.repeat(64),scanStatus:'CLEAN',failureCode:null,version:1,createdAt:'2026-09-09T01:00:00Z',jdVersionId:'jd-v2',jdTitle:'高级前端工程师'};
  await page.route('**/api/v1/intakes**',route=>{
    const path=new URL(route.request().url()).pathname;
    if(route.request().method()==='POST'){
      imports++;started=true;
      expect(route.request().url()).toContain('/auto-intake/import');
      expect(route.request().postDataJSON()).toEqual({version:'1'});
      return route.fulfill({json:{...base,status:'PARSING',importStatus:'PROCESSING',processingStatus:'RUNNING',handledByUserId:'hr-a'}});
    }
    reads++;
    const item=!started?{...base,status:'PENDING_IMPORT',importStatus:'PENDING'}:reads<3?{...base,status:'PARSING',importStatus:'PROCESSING',processingStatus:'RUNNING',handledByUserId:'hr-a'}:{...base,status:'COMPLETED',importStatus:'IMPORTED',processingStatus:'SUCCEEDED',handledByUserId:'hr-a',candidateId:'person-a',resumeVersionId:'resume-v1'};
    if(path==='/api/v1/intakes/auto-intake')return route.fulfill({json:item});
    return route.fulfill({json:{items:[item],hasMore:false,nextCursor:null}});
  });
  await page.goto('/settings/intakes');
  await expect(page.getByText('点击“入库”后，AI 自动解析简历并加入待筛选库。')).toHaveCount(0);
  await expect(page.getByText('高级前端工程师',{exact:true})).toBeVisible();
  await expect(page.getByText('待入库',{exact:true})).toBeVisible();
  const importButton=page.getByRole('button',{name:'重试导入',exact:true});
  await expect(importButton).toBeEnabled();
  await importButton.focus();await expect(importButton).toBeFocused();await importButton.press('Enter');
  await expect(importButton).toHaveCount(0);
  await expect(page.getByLabel('候选人姓名')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'核对异常身份'})).toHaveCount(0);
  await expect(page.getByText('已入库',{exact:true})).toBeVisible({timeout:7000});
  expect(imports).toBe(1);
  expect(reads).toBeGreaterThanOrEqual(2);
  await page.reload();
  await expect(page.getByText('已入库',{exact:true})).toBeVisible();
  expect(imports).toBe(1);
  expect((await new AxeBuilder({page}).include('.app-shell').analyze()).violations).toEqual([]);
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:900});
    const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole('button',{name:'批量上传简历'})).toHaveCount(0);
    expect(await drawer.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBe(true);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
    if(width===390||width===1440)await page.screenshot({path:testInfo.outputPath(`intake-imported-${width}.png`),fullPage:true});
  }
});


test('failed import shows diagnostics and keeps keyboard accessible retry',async({page})=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/intake-batches/batch',route=>route.fulfill({json:{id:'batch',status:'COMPLETED',createdAt:'2026-09-09T13:45:00Z',totalCount:1,succeededCount:1,failedCount:0,parsingCount:0,parsedCount:0,attentionCount:1}}));
  await page.route('**/api/v1/intakes?**',route=>route.fulfill({json:{items:[{id:'failed-intake',batchId:'batch',sourceChannelId:'source',ownerUserId:'hr-a',handledByUserId:'hr-a',originalFileName:'Java开发简历_脱敏.pdf',sizeBytes:450355,status:'FAILED_RETRYABLE',processingStatus:'FAILED_FINAL',scanStatus:'CLEAN',failureCode:'MODEL_JSON_INVALID',createdAt:'2026-09-09T13:45:00Z',version:1,jdVersionId:'jd-v2',jdTitle:'IT运维工程师'}],hasMore:false,nextCursor:null}}));
  await page.goto('/settings/intakes?batchId=batch');
  const row=page.getByRole('row').filter({hasText:'Java开发简历_脱敏.pdf'});
  await expect(row.getByText('导入失败',{exact:true})).toBeVisible();
  await expect(row.getByText('待入库',{exact:true})).toHaveCount(0);
  await expect(row.getByText('导入失败，详见处理状态')).toBeVisible();
  await expect(row.getByText('失败原因：解析结果格式不正确，未能读取简历信息')).toBeVisible();
  await row.getByText('查看错误代码').click();
  await expect(row.getByText('MODEL_JSON_INVALID',{exact:true})).toBeVisible();
  const retry=row.getByRole('button',{name:'重试导入',exact:true});
  await expect(retry).toBeEnabled();
  await retry.focus();
  await expect(retry).toBeFocused();
  await page.locator('.intake-drawer').evaluate(async element=>{await Promise.allSettled(element.getAnimations({subtree:true}).filter(animation=>animation.effect?.getComputedTiming().iterations!==Infinity).map(animation=>animation.finished));});
  expect((await new AxeBuilder({page}).include('.intake-validation-page').analyze()).violations).toEqual([]);
});

test('任务分组展示归属人，明细默认待入库且允许查看全部',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const createdAt='2026-09-09T01:00:00Z';
  const batches=['batch-a','batch-b'].map((id,index)=>({id,status:'COMPLETED',sourceChannelId:'source',sourceRecruiterUserId:'uploader',defaultOwnerUserId:index?'owner-b':'hr-a',createdAt,totalCount:2,succeededCount:2,failedCount:0,completedCount:1,processingCount:0,parsingCount:0,parsedCount:1,attentionCount:1,ignoredCount:0}));
  await page.route('**/api/v1/directory/users/resolve?*',route=>route.fulfill({json:[{id:'owner-b',displayName:'归属测试乙',status:'ACTIVE'}]}));
  await page.route('**/api/v1/intake-batches?*',route=>route.fulfill({json:{items:batches,hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intake-batches/batch-*',route=>route.fulfill({json:batches.find(batch=>route.request().url().endsWith(batch.id))}));
  await page.route('**/api/v1/intakes/count?*',route=>route.fulfill({json:{count:new URL(route.request().url()).searchParams.has('importStatus')?1:2,hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intakes?*',route=>{
    const params=new URL(route.request().url()).searchParams,batchId=params.get('batchId');
    const items=['PENDING','IMPORTED'].map(importStatus=>({id:`intake-${batchId}-${importStatus}`,batchId,sourceChannelId:'source',ownerUserId:'hr-a',originalFileName:`${batchId}-${importStatus}.pdf`,status:importStatus==='PENDING'?'PENDING_IMPORT':'COMPLETED',importStatus,scanStatus:'CLEAN',createdAt,version:1}));
    return route.fulfill({json:{items:items.filter(item=>!params.get('importStatus')||item.importStatus===params.get('importStatus')),hasMore:false,nextCursor:null}});
  });
  await page.goto('/settings/intakes');
  const drawer=page.getByRole('dialog',{name:'解析校验'});
  const groups=drawer.getByRole('list',{name:'上传任务'});
  await expect(groups.getByRole('button')).toHaveCount(2);
  await expect(groups.getByRole('button').first()).toContainText('归属：陈佳');
  await expect(groups.getByRole('button').nth(1)).toContainText('归属：归属测试乙');
  await page.screenshot({path:testInfo.outputPath('intake-group-owners.png'),animations:'disabled'});
  await expect(drawer.getByRole('columnheader',{name:'收件文件'})).toHaveCount(0);
  await groups.getByRole('button').first().click();
  await expect(drawer.getByText('batch-a-PENDING.pdf')).toBeVisible();
  await expect(drawer.getByText('batch-a-IMPORTED.pdf')).toHaveCount(0);
  await expect(drawer.locator('.pagination-total')).toContainText('共1条记录');
  await drawer.getByRole('button',{name:'清空入库状态'}).click();
  await drawer.getByRole('button',{name:/^查\s*询$/}).click();
  await expect(drawer.getByText('batch-a-IMPORTED.pdf')).toBeVisible();
  await expect(drawer.locator('.pagination-total')).toContainText('共2条记录');
  expect(new URL(page.url()).searchParams.get('importStatus')).toBe('ALL');
  await page.reload();
  await expect(drawer.getByText('batch-a-IMPORTED.pdf')).toBeVisible();
  await drawer.getByRole('button',{name:/^重\s*置$/}).click();
  await expect(drawer.getByText('batch-a-PENDING.pdf')).toBeVisible();
  await expect(drawer.getByText('batch-a-IMPORTED.pdf')).toHaveCount(0);
  await page.screenshot({path:testInfo.outputPath('intake-default-pending.png'),animations:'disabled'});
  await drawer.getByRole('button',{name:'返回任务分组'}).click();
  await expect(groups).toBeVisible();
  await groups.getByRole('button').nth(1).click();
  await expect(drawer.getByText('batch-b-PENDING.pdf')).toBeVisible();
  await expect(drawer.getByText('batch-b-IMPORTED.pdf')).toHaveCount(0);
  await expect(drawer.getByText('batch-a-PENDING.pdf')).toHaveCount(0);
  expect(await drawer.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBe(true);
});

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty={items:[],nextCursor:null,hasMore:false};
const jd={id:'jd-1',code:'JD_SYSTEM',title:'运维工程师',workRegionName:'北京',onboardedHeadcount:0,distinction:'北京 · 云平台',status:'ACTIVE',currentVersionNo:1,currentParseStatus:'SUCCEEDED',contentShared:false,appliedFromJdVersionId:null,clientId:'client-1',clientName:'示例客户',ownerUserId:'hr-owner',ownerDepartmentId:'dept',recruitmentType:'EXTERNAL',headcount:2,occupiedHeadcount:1,remainingHeadcount:1,recruitmentCycleDays:45,version:'v1',createTime:'2026-09-09T01:00:00Z',createdAt:'2026-09-09T01:00:00Z'};
async function mock(page:Page,superAdmin=false,canManage=true,onDepartmentRequest?:()=>void){
  await page.route('**/api/v1/**',route=>{const path=new URL(route.request().url()).pathname.replace('/api/v1','');let body:unknown=empty;
    if(path==='/me')body={userId:superAdmin?'admin':'hr-reader',displayName:superAdmin?'管理员':'招聘专员',workEmail:'user@example.com',homeDepartmentId:'dept',supportDepartmentIds:['dept-support'],roles:[superAdmin?'SUPER_ADMIN':'HR'],permissions:['recruitment.manage','demand.read',...(canManage?['demand.manage']:[])],superAdmin};
    else if(path==='/auth/csrf')body={headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'test-csrf'};
    else if(['/management/workbench','/workbench','/admin/super-admins'].includes(path))body=[];
    else if(path==='/directory/users/resolve')body=[{id:'hr-owner',displayName:'王招聘',status:'ACTIVE'}];
    else if(path==='/directory/users')body={...empty,items:[{id:'hr-owner',displayName:'王招聘',status:'ACTIVE'}]};
    else if(path==='/directory/departments'){onDepartmentRequest?.();body=[{id:'dept',code:'OPS',name:'运维部',parentDepartmentId:null,status:'ACTIVE'},{id:'dept-support',code:'PLATFORM',name:'平台部',parentDepartmentId:null,status:'ACTIVE'},{id:'dept-unassigned',code:'OTHER',name:'无支持关系部门',parentDepartmentId:null,status:'ACTIVE'}];}
    else if(path==='/clients')body={...empty,items:[{id:'client-1',code:'CLIENT_SYSTEM',name:'示例客户',status:'ACTIVE',ownerUserId:'admin',ownerDepartmentId:'dept',version:1,updatedAt:'2026-09-09'}]};
    else if(path==='/jd-code-options')body={jobTypes:[{code:'01',name:'运维工程师'}],regions:[{code:'02',name:'北京'}]};
    else if(path==='/jds/public-sources/count')body={count:1,nextCursor:null,hasMore:false};
    else if(path==='/jds/public-sources')body={...empty,items:[{id:jd.id,code:jd.code,title:jd.title,workRegionName:jd.workRegionName,currentVersionNo:1,currentVersionId:'jv-1',rawText:null,ownerUserId:'source-owner',ownerUserName:'源JD所属人',ownerDepartmentId:'source-department',ownerDepartmentName:'源JD部门',createTime:jd.createTime}]};
    else if(path==='/jds/public-sources/jd-1')body={id:jd.id,code:jd.code,title:jd.title,workRegionName:jd.workRegionName,currentVersionNo:1,currentVersionId:'jv-1',rawText:'负责 Linux 云平台运维',ownerUserId:'source-owner',ownerUserName:'源JD所属人',ownerDepartmentId:'source-department',ownerDepartmentName:'源JD部门',createTime:jd.createTime};
    else if(path==='/jds')body={...empty,items:[jd]};
    else if(path==='/jds/jd-1/versions')body={...empty,items:[{id:'jv-1',jdId:'jd-1',versionNo:1,title:jd.title,rawText:'负责 Linux 云平台运维',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-owner',createTime:jd.createdAt}]};
    else if(path==='/jd-versions/jv-1/sources')body={...empty,items:[{id:'source-1',jdVersionId:'jv-1',sourceType:'MANUAL',sourceLabel:'手动录入',mailSourceId:null,ownerUserId:'hr-owner',createTime:jd.createdAt}]};
    return route.fulfill({json:body});});
}
async function chooseCity(page:Page, label='北京市 · 北京市'){
  const dialog = page.getByRole('dialog').filter({has:page.getByLabel('工作地区',{exact:true})});
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(dialog).toHaveCSS('opacity','1');
  await page.getByLabel('工作地区',{exact:true}).click();
  await page.getByLabel('工作地区',{exact:true}).fill(label.split(' · ')[0]!);
  const popup = page.locator('.ant-select-dropdown:visible').filter({hasText:label});
  await expect(popup).not.toHaveClass(/ant-slide-up-(enter|appear)/);
  await popup.locator('.ant-select-item-option').filter({hasText:label}).click();
}
async function chooseDepartmentAndType(page:Page,type:'内部招聘'|'外部招聘'='内部招聘'){
  await chooseCity(page);
  await page.getByLabel('JD 归属部门').click(); await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({hasText:'运维部'}).click();
  await page.getByLabel('招聘类型').click(); await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({hasText:type}).click();
}
async function chooseDepartment(page:Page,name:string){
  await page.getByLabel('JD 归属部门').click(); await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({hasText:name}).click();
}

test('JD library keeps the daily flow code-free and accessible',async({page},info)=>{
  await reviewWorkspace(page); await mock(page); await page.goto('/recruitment/setup'); await expect(page.getByRole('heading',{name:'JD 库'})).toBeVisible();
  const departmentButton = page.getByRole('button',{name:'公共 JD 列表'});
  const newButton = page.getByRole('button',{name:/新增 JD/});
  await expect(departmentButton).toBeVisible();
  if (info.project.name === 'desktop') {
    const departmentBox = await departmentButton.boundingBox(); const newBox = await newButton.boundingBox();
    expect(departmentBox).not.toBeNull(); expect(newBox).not.toBeNull();
    expect(departmentBox!.x + departmentBox!.width).toBeLessThan(newBox!.x);
    expect(Math.abs(departmentBox!.y - newBox!.y)).toBeLessThan(2);
  }
  await expect(page.getByText('JD_SYSTEM',{exact:true})).toBeVisible();
  await page.screenshot({path:info.outputPath(`jd-library-heading-${info.project.name}.png`),fullPage:true,animations:'disabled'});
  await expect(page.getByText('北京市',{exact:true})).toBeVisible(); await expect(page.getByText('解析完成')).toBeVisible();
  await page.getByRole('button',{name:/新增 JD/}).click(); await expect(page.getByLabel('岗位名称')).toBeVisible(); await expect(page.getByLabel('招聘人数（HC）')).toBeVisible(); await expect(page.getByLabel('招聘周期（天）')).toHaveValue('45'); await expect(page.getByLabel('JD 原文')).toBeVisible();
  await expect(page.getByRole('dialog')).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(page.getByRole('dialog')).toHaveCSS('opacity', '1');
  await expect(page.getByLabel('岗位类型', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('工作地区', { exact: true })).toBeVisible();
  const titleBox = await page.getByLabel('岗位名称').locator('xpath=ancestor::div[contains(@class, "ant-form-item-control-input")][1]').boundingBox();
  const regionBox = await page.getByLabel('工作地区',{exact:true}).locator('xpath=ancestor::div[contains(@class, "ant-form-item-control-input")][1]').boundingBox();
  const departmentBox = await page.getByLabel('JD 归属部门').locator('xpath=ancestor::div[contains(@class, "ant-form-item-control-input")][1]').boundingBox();
  expect(titleBox).not.toBeNull(); expect(regionBox).not.toBeNull(); expect(departmentBox).not.toBeNull();
  for (const description of ['从部门库选择，保存后作为该 JD 的业务归属。', '填写实际招聘地区，例如杭州、怀来；同一地区请使用一致名称。', '填写本 JD 独立承担的人数；只有实际入职才占用 HC。', '从 JD 创建日起计算；超期且实际入职未达 HC 时提示，更新不会重置起算时间。']) {
    await expect(page.getByRole('dialog').getByText(description, { exact: true })).toHaveCount(0);
  }
  if (info.project.name === 'desktop') {
    expect(Math.abs(departmentBox!.y - titleBox!.y)).toBeLessThan(2);
    expect(Math.abs(titleBox!.y - regionBox!.y)).toBeLessThan(2);
    expect(titleBox!.x).toBeGreaterThan(departmentBox!.x + departmentBox!.width);
    expect(regionBox!.x).toBeGreaterThan(titleBox!.x + titleBox!.width);
  } else {
    expect(titleBox!.y).toBeGreaterThan(departmentBox!.y + departmentBox!.height);
    expect(regionBox!.y).toBeGreaterThan(titleBox!.y + titleBox!.height);
  }
  await page.screenshot({ path: info.outputPath(`jd-form-${info.project.name}.png`), fullPage: true, animations: 'disabled' });
  await page.getByRole('button',{name:'保存并解析'}).click(); await expect(page.getByText('请输入工作地区')).toBeVisible();
  await expect(page.getByLabel(/编码|编号/)).toHaveCount(0); await expect(page.getByLabel('招聘类型')).toBeVisible(); await expect(page.getByLabel('客户')).toHaveCount(0); await chooseDepartmentAndType(page,'外部招聘'); await expect(page.getByLabel('客户')).toBeVisible(); await expect(page.getByLabel('区分说明')).toHaveCount(0); await expect(page.getByRole('checkbox',{name:/作为独立 JD/})).toHaveCount(0);
  // 在弹窗完成淡入后检查对比度，避免采样到半透明的过渡帧。
  await expect(page.getByRole('dialog')).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(page.getByRole('dialog')).toHaveCSS('opacity','1');
  await expect(page.locator('.ant-form-show-help-item-leave')).toHaveCount(0);
  const results=await new AxeBuilder({page}).include('[role="dialog"]').analyze(); expect(results.violations).toEqual([]);
  await page.screenshot({path:info.outputPath(`jd-library-${info.project.name}.png`),fullPage:true,animations:'disabled'});
});

test('public JD can be viewed and applied with personal settings and source version', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page);
  await page.route('**/api/v1/auth/csrf', route=>route.fulfill({json:{headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'test'}}));
  let requestBody: Record<string,unknown> | undefined;
  await page.route('**/api/v1/jds', route => {
    if(route.request().method() !== 'POST') return route.fallback();
    requestBody = route.request().postDataJSON() as Record<string,unknown>;
    return route.fulfill({json:{id:'applied-v1',jdId:'applied-jd',versionNo:1}});
  });
  await page.route('**/api/v1/jd-versions/applied-v1/parse', route=>route.fulfill({json:{id:'parse-task',status:'QUEUED'}}));
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:'公共 JD 列表'}).click();
  await expect(page.getByRole('dialog',{name:'公共 JD 列表'})).toContainText('JD_SYSTEM');
  await expect(page.getByRole('dialog',{name:'公共 JD 列表'})).toContainText('源JD所属人');
  await expect(page.getByRole('dialog',{name:'公共 JD 列表'})).toContainText('源JD部门');
  await page.getByRole('button',{name:'查看原文'}).click();
  await expect(page.getByRole('dialog',{name:'公共 JD 详情'})).toContainText('负责 Linux 云平台运维');
  await expect(page.getByRole('dialog',{name:'公共 JD 详情'})).toContainText('源JD所属人');
  await expect(page.getByRole('dialog',{name:'公共 JD 详情'})).toContainText('源JD部门');
  await expect(page.getByRole('dialog',{name:'公共 JD 详情'}).getByText('邮件主题示例：')).toHaveCount(0);
  await page.getByRole('button',{name:'应用此 JD'}).click();
  await expect(page.getByRole('dialog',{name:'应用公共 JD'})).toBeVisible();
  await expect(page.getByLabel('岗位名称')).toBeDisabled();
  await expect(page.getByLabel('JD 归属部门')).toBeEnabled();
  await expect(page.getByLabel('JD 原文')).toHaveAttribute('readonly','');
  await expect(page.getByLabel('JD 原文')).toHaveValue('负责 Linux 云平台运维');
  await expect(page.getByLabel('招聘人数（HC）')).toHaveValue('');
  await expect(page.getByLabel('招聘周期（天）')).toHaveValue('');
  await expect(page.getByLabel('招聘类型')).toHaveValue('');
  await expect(page.getByLabel('客户')).toHaveCount(0);
  await expect(page.getByRole('checkbox',{name:'公开到公共 JD 列表'})).toHaveCount(0);
  await page.getByRole('button',{name:'保存并解析'}).click();
  await expect(page.getByText('请填写招聘人数（HC）')).toBeVisible();
  await expect(page.getByText('请填写招聘周期')).toBeVisible();
  await expect(page.getByText('请选择招聘类型')).toBeVisible();
  expect(requestBody).toBeUndefined();
  await page.getByLabel('招聘人数（HC）').fill('4');
  await page.getByLabel('招聘周期（天）').fill('45');
  await page.getByLabel('招聘类型').click();
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({hasText:'内部招聘'}).click();
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
  await page.screenshot({path:info.outputPath(`public-jd-apply-${info.project.name}.png`),fullPage:true,animations:'disabled'});
  if (info.project.name === 'mobile') {
    await page.getByLabel('JD 原文').scrollIntoViewIfNeeded();
    await expect(page.getByLabel('JD 原文')).toHaveValue('负责 Linux 云平台运维');
    await page.screenshot({path:info.outputPath('public-jd-apply-bottom-mobile.png'),fullPage:true,animations:'disabled'});
  }
  await page.getByLabel('JD 归属部门').click();
  await expect(page.locator('.ant-select-dropdown:visible').getByText('无支持关系部门',{exact:true})).toHaveCount(0);
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText:'平台部'}).click();
  await chooseCity(page,'杭州市 · 浙江省');
  await page.getByRole('button',{name:'保存并解析'}).click();
  await expect(page.getByText('JD 已保存，系统正在解析')).toBeVisible();
  expect(requestBody).toMatchObject({appliedFromJdVersionId:'jv-1',title:jd.title,rawText:'负责 Linux 云平台运维',workRegionName:'杭州市',ownerDepartmentId:'dept-support',headcount:4,recruitmentCycleDays:45});
  expect(requestBody).toMatchObject({clientId:null,recruitmentType:'INTERNAL'});
  expect(requestBody).not.toHaveProperty('ownerUserId');
  expect(requestBody).not.toHaveProperty('contentShared');
});

test('public JD detail actions stay visible while long content scrolls', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page);
  const rawText=Array.from({length:60},(_,index)=>`${index+1}. 负责 Linux 云平台运维，持续优化服务稳定性与交付流程。`).join('\n');
  await page.route('**/api/v1/jds/public-sources/jd-1', route=>route.fulfill({json:{id:jd.id,code:jd.code,title:jd.title,workRegionName:jd.workRegionName,currentVersionNo:1,currentVersionId:'jv-1',rawText,createTime:jd.createTime}}));
  await page.goto('/recruitment/setup');
  const trigger=page.getByRole('button',{name:'公共 JD 列表',exact:true});
  await trigger.click();
  await page.getByRole('button',{name:'查看原文'}).click();
  const dialog=page.getByRole('dialog',{name:'公共 JD 详情'});
  const back=dialog.getByRole('button',{name:'返回公共 JD 列表'});
  const apply=dialog.getByRole('button',{name:'应用此 JD'});
  await expect(dialog).toContainText(rawText);
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(back).toBeInViewport();
  await expect(apply).toBeInViewport();
  const before=await apply.boundingBox();
  const backBox=await back.boundingBox();
  expect(before).not.toBeNull(); expect(backBox).not.toBeNull();
  expect(Math.abs(before!.y-backBox!.y)).toBeLessThan(2);
  expect(backBox!.x+backBox!.width).toBeLessThan(before!.x);
  const body=dialog.locator('.ant-modal-body');
  await expect.poll(()=>body.evaluate(node=>node.scrollHeight-node.clientHeight)).toBeGreaterThan(0);
  expect(await body.evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
  await dialog.screenshot({path:info.outputPath(`public-jd-detail-top-${info.project.name}.png`),animations:'disabled'});
  await body.evaluate(node=>{node.scrollTop=node.scrollHeight;});
  await expect.poll(()=>body.evaluate(node=>node.scrollTop)).toBeGreaterThan(0);
  await expect(back).toBeInViewport();
  await expect(apply).toBeInViewport();
  const after=await apply.boundingBox();
  expect(Math.abs(after!.y-before!.y)).toBeLessThan(2);
  await dialog.screenshot({path:info.outputPath(`public-jd-detail-bottom-${info.project.name}.png`),animations:'disabled'});
  await back.focus();
  await page.keyboard.press('Tab');
  await expect(apply).toBeFocused();
  await back.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog',{name:'公共 JD 列表'})).toBeVisible();
  await page.getByRole('button',{name:'查看原文'}).click();
  await apply.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog',{name:'应用公共 JD'})).toBeVisible();
  await expect(page.getByLabel('JD 原文')).toHaveValue(rawText);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('an owner can publish an existing JD without starting a new parse', async ({ page }) => {
  await reviewWorkspace(page); await mock(page);
  let updateBody: Record<string,unknown> | undefined;
  let parses=0;
  await page.route('**/api/v1/jds?**', route=>route.fulfill({json:{...empty,items:[{...jd,ownerUserId:'hr-reader',contentShared:false}]}}));
  await page.route('**/api/v1/jds/jd-1/versions**', route=>{
    if(route.request().method() !== 'POST') return route.fallback();
    updateBody=route.request().postDataJSON() as Record<string,unknown>;
    return route.fulfill({json:{id:'jv-1',jdId:jd.id,versionNo:1}});
  });
  await page.route('**/api/v1/jd-versions/jv-1/parse', route=>{parses++;return route.fulfill({json:{id:'parse',status:'QUEUED'}});});
  await page.goto('/recruitment/setup');
  await expect(page.getByText('未公开')).toBeVisible();
  await page.getByRole('button',{name:/更新/}).click();
  await page.getByRole('checkbox',{name:'公开到公共 JD 列表'}).check();
  await page.getByRole('dialog',{name:'更新 JD'}).getByRole('button',{name:'保存修改'}).click();
  await expect(page.getByText('JD 配置已更新')).toBeVisible();
  expect(updateBody).toMatchObject({contentShared:true});
  expect(parses).toBe(0);
});

for (const superAdmin of [false,true]) test(`an applied JD keeps source content read-only and personal configuration editable (super admin: ${superAdmin})`, async ({ page }) => {
  await reviewWorkspace(page); await mock(page,superAdmin);
  const applied={...jd,ownerUserId:superAdmin?'admin':'hr-reader',appliedFromJdVersionId:'source-v1'};
  await page.route('**/api/v1/jds?**', route=>route.fulfill({json:{...empty,items:[applied]}}));
  let updateBody: Record<string,unknown> | undefined;
  await page.route('**/api/v1/jds/jd-1/versions**', route=>{
    if(route.request().method() !== 'POST') return route.fallback();
    updateBody=route.request().postDataJSON() as Record<string,unknown>;
    return route.fulfill({json:{id:'jv-1',jdId:jd.id,versionNo:1}});
  });
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:/更新/}).click();
  await expect(page.getByLabel('岗位名称')).toBeDisabled();
  await expect(page.getByLabel('JD 原文')).toHaveAttribute('readonly','');
  await expect(page.getByRole('checkbox',{name:'公开到公共 JD 列表'})).toHaveCount(0);
  await expect(page.getByLabel('JD 归属部门')).toBeEnabled();
  await chooseDepartment(page,'平台部');
  await chooseCity(page,'杭州市 · 浙江省');
  await expect(page.getByRole('dialog',{name:'更新 JD'})).toContainText('原编号 JD_SYSTEM保持不变');
  await page.getByRole('dialog',{name:'更新 JD'}).getByRole('button',{name:'保存修改'}).click();
  await expect(page.getByText('JD 配置已更新')).toBeVisible();
  expect(updateBody).toMatchObject({title:jd.title,rawText:'负责 Linux 云平台运维',ownerDepartmentId:'dept-support',workRegionName:'杭州市'});
  expect(updateBody).not.toHaveProperty('contentShared');
  expect(updateBody).not.toHaveProperty('code');
});

test('new JD uses direct title input and warns on a same-title same-region source', async ({ page }) => {
  await reviewWorkspace(page); await mock(page,true); await page.goto('/recruitment/setup');
  await expect(page.getByRole('button',{name:'岗位名称审核'})).toHaveCount(0);
  await page.getByRole('button',{name:/新增 JD/}).click();
  await page.getByLabel('岗位名称').fill(jd.title);
  await chooseDepartmentAndType(page);
  await expect(page.getByText('公共 JD 列表已有同名同地区 JD，可先从公共 JD 列表应用')).toBeVisible();
  await expect(page.getByText('公司共享')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'保存并解析'})).toBeEnabled();
});

test('public JD search and pagination use their own scoped query', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page);
  const searches: string[] = [];
  await page.route('**/api/v1/jds/public-sources?**', route => {
    const params = new URL(route.request().url()).searchParams;
    searches.push(`${params.get('query') ?? ''}:${params.get('cursor') ?? ''}`);
    const source = {id:jd.id,code:jd.code,title:jd.title,workRegionName:jd.workRegionName,currentVersionNo:1,currentVersionId:'jv-1',rawText:null,createTime:jd.createTime};
    const item = params.get('query') ? { ...source, id:'jd-3', code:'JD-SEARCH', title:'剪辑师' } : params.get('cursor') ? { ...source, id:'jd-2', code:'JD-SECOND', title:'第二岗位' } : source;
    return route.fulfill({json:{items:[item],nextCursor:params.get('query') || params.get('cursor') ? null : 'next',hasMore:!params.get('query') && !params.get('cursor')}});
  });
  await page.route('**/api/v1/jds/public-sources/count?**', route => route.fulfill({json:{count:new URL(route.request().url()).searchParams.get('query')?1:2,nextCursor:null,hasMore:false}}));
  await page.goto('/recruitment/setup'); await page.getByRole('button',{name:'公共 JD 列表'}).click();
  const dialog=page.getByRole('dialog',{name:'公共 JD 列表'});
  await expect(dialog.getByText('JD_SYSTEM')).toBeVisible();
  const body=await dialog.locator('.ant-modal-body').boundingBox();
  const go=await dialog.getByRole('button',{name:'跳转页面'}).boundingBox();
  expect(body).not.toBeNull(); expect(go).not.toBeNull();
  expect(body!.x+body!.width-go!.x-go!.width).toBeLessThan(50);
  expect(body!.y+body!.height-go!.y-go!.height).toBeLessThan(65);
  await dialog.screenshot({path:info.outputPath(`public-jd-pagination-${info.project.name}.png`),animations:'disabled'});
  const geometry=await dialog.evaluate(element=>{
    const content=element.querySelector('.source-jd-department-table .ant-table-content')!;
    const table=content.getBoundingClientRect();
    const pagination=element.querySelector('.compact-pagination')!.getBoundingClientRect();
    return {gap:Math.round(pagination.top-table.bottom),tableHeight:Math.round(table.height)};
  });
  expect(geometry.gap,JSON.stringify(geometry)).toBeLessThanOrEqual(24);
  await dialog.getByRole('button',{name:'下一页'}).click();
  await expect(dialog.getByText('JD-SECOND')).toBeVisible();
  await dialog.getByRole('searchbox',{name:'搜索公共 JD'}).fill('剪辑');
  await dialog.getByRole('searchbox',{name:'搜索公共 JD'}).press('Enter');
  await expect(dialog.getByText('JD-SEARCH')).toBeVisible();
  expect(searches).toContain(':next'); expect(searches).toContain('剪辑:');
});

test('public JD rows scroll within the table while pagination stays visible', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page);
  await page.route('**/api/v1/jds/public-sources?**', route => route.fulfill({json:{...empty,items:Array.from({length:20},(_,index)=>({id:`jd-${index}`,code:`JD-${index}`,title:`岗位${index}`,workRegionName:'北京',currentVersionNo:1,currentVersionId:`jv-${index}`,rawText:null,createTime:jd.createTime}))}}));
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:'公共 JD 列表'}).click();
  const dialog=page.getByRole('dialog',{name:'公共 JD 列表'});
  const content=dialog.locator('.source-jd-department-table .ant-table-content');
  await expect.poll(()=>content.evaluate(node=>node.scrollHeight-node.clientHeight)).toBeGreaterThan(0);
  await content.evaluate(node=>{node.scrollTop=node.scrollHeight;});
  await expect.poll(()=>content.evaluate(node=>node.scrollTop)).toBeGreaterThan(0);
  await expect(dialog.getByText('JD-19',{exact:true})).toBeInViewport();
  await expect(dialog.getByRole('columnheader',{name:'编号'})).toBeInViewport();
  await expect(dialog.getByRole('navigation',{name:'公共 JD 分页'})).toBeInViewport();
  await dialog.screenshot({path:info.outputPath(`public-jd-scroll-bottom-${info.project.name}.png`),animations:'disabled'});
});

test('another HR JD is visible with its owner but cannot be modified',async({page})=>{
  let departmentRequests=0; await reviewWorkspace(page); await mock(page,false,false,()=>departmentRequests++); await page.goto('/recruitment/setup'); await expect(page.getByText('可直接使用')).toBeVisible();
  await expect(page.getByText('王招聘')).toBeVisible();
  await expect(page.getByRole('button',{name:/更新/})).toHaveCount(0); await expect(page.getByRole('button',{name:/删除/})).toHaveCount(0); await expect(page.getByText('招聘需求')).toHaveCount(0);
  await expect(page.getByRole('button',{name:/新增 JD/})).toHaveCount(0); expect(departmentRequests).toBe(0);
});

test('JD deletion reports usage conflicts and permits retry after linked resumes are cleared', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page, true);
  const detail = '该 JD 仍关联我的简历 1 份、智能筛选 2 份、招聘流程 1 份，请先处理关联简历。';
  let attempts = 0;
  await page.route('**/api/v1/jds/jd-1?**', route => {
    expect(route.request().method()).toBe('DELETE');
    expect(new URL(route.request().url()).searchParams.get('version')).toBe(jd.version);
    attempts++;
    return attempts === 1
      ? route.fulfill({status:409,json:{title:'JD 正在使用',status:409,code:'JD_IN_USE',detail,recoverable:true}})
      : route.fulfill({status:204});
  });
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:/删\s*除/}).click();
  const confirmation = page.getByRole('tooltip');
  await expect(confirmation).toContainText('关联简历仍在我的简历、智能筛选或招聘流程中时不可删除。');
  await expect(confirmation).toContainText('历史招聘记录仍保留。');
  await confirmation.getByRole('button',{name:/删\s*除/}).click();
  await expect(page.getByText(detail,{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:jd.title,exact:true})).toBeVisible();
  expect(attempts).toBe(1);
  await page.screenshot({path:info.outputPath('jd-deletion-in-use.png'),fullPage:true,animations:'disabled'});
  await page.route('**/api/v1/jds?**', route=>route.fulfill({json:empty}));
  await page.getByRole('button',{name:/删\s*除/}).click();
  await confirmation.getByRole('button',{name:/删\s*除/}).click();
  await expect(page.getByText('JD 已删除，历史招聘记录仍可查看',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:jd.title,exact:true})).toHaveCount(0);
  await expect(page.getByText(detail,{exact:true})).toHaveCount(0);
  expect(attempts).toBe(2);
});

test('an administrator must assign an owner when creating a JD', async ({ page }) => {
  await reviewWorkspace(page); await mock(page, true); await page.goto('/recruitment/setup');
  await page.getByRole('button', { name: /新增 JD/ }).click();
  await chooseDepartmentAndType(page);
  await page.getByLabel('岗位名称').fill('平台工程师');
  await page.getByLabel('JD 原文').fill('负责平台工程建设');
  await page.getByRole('button', { name: '保存并解析' }).click();
  await expect(page.getByText('代建 JD 时必须选择所属人')).toBeVisible();
  await expect(page.getByText('设为公共 JD')).toHaveCount(0);
});

test('JD save and parse work when the browser has no randomUUID', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => Object.defineProperty(Crypto.prototype, 'randomUUID', { value: undefined, configurable: true }));
  await reviewWorkspace(page);
  await mock(page);
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'test-csrf' } }));
  const keys: string[] = [];
  await page.route('**/api/v1/jds', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().postDataJSON()).toMatchObject({ title: 'AI剪辑师', workRegionName:'北京市', rawText: '负责视频剪辑及内容创作', headcount: 3, recruitmentCycleDays: 45, ownerDepartmentId:'dept', recruitmentType:'INTERNAL', clientId:null });
    keys.push(route.request().headers()['idempotency-key']);
    await route.fulfill({ json: { id: 'new-version', jdId: 'new-jd', versionNo: 1 } });
  });
  await page.route('**/api/v1/jd-versions/new-version/parse', async route => {
    keys.push(route.request().headers()['idempotency-key']);
    await route.fulfill({ json: { id: 'parse-task', status: 'QUEUED' } });
  });
  await page.goto('/recruitment/setup');
  await page.getByRole('button', { name: /新增 JD/ }).click();
  await chooseDepartmentAndType(page);
  await page.getByLabel('岗位名称').fill('AI剪辑师');
  await page.getByLabel('招聘人数（HC）').fill('3');
  await page.getByLabel('JD 原文').fill('负责视频剪辑及内容创作');
  await page.getByRole('button', { name: '保存并解析' }).click();
  await expect(page.getByText('JD 已保存，系统正在解析')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(keys).toHaveLength(2);
  expect(new Set(keys).size).toBe(2);
  for (const key of keys) expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(errors).toEqual([]);
});

test('JD save failure appears once and preserves inputs for a successful retry', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await reviewWorkspace(page);
  await mock(page);
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({json:{headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'test'}}));
  let saves = 0;
  let parses = 0;
  await page.route('**/api/v1/jds', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    saves++;
    expect(route.request().postDataJSON()).toMatchObject({title:'AI剪辑师',workRegionName:'北京市',rawText:'负责视频剪辑及内容创作',headcount:3,recruitmentCycleDays:45,ownerDepartmentId:'dept',recruitmentType:'INTERNAL',clientId:null});
    if (saves === 1) return route.fulfill({status:409,json:{title:'Conflict',status:409,code:'JD_DUPLICATE',detail:'相同 JD 正在保存，请重试',recoverable:true}});
    return route.fulfill({json:{id:'retry-version',jdId:'retry-jd',versionNo:1}});
  });
  await page.route('**/api/v1/jd-versions/retry-version/parse', route => {
    parses++;
    return route.fulfill({json:{id:'parse-task',status:'QUEUED'}});
  });
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:/新增 JD/}).click();
  await chooseDepartmentAndType(page);
  await page.getByLabel('岗位名称').fill('AI剪辑师');
  await page.getByLabel('招聘人数（HC）').fill('3');
  await page.getByLabel('JD 原文').fill('负责视频剪辑及内容创作');
  await page.getByRole('button',{name:'保存并解析'}).click();
  await expect(page.getByText('填写内容已保留，可以修改后重试。')).toBeVisible();
  await expect(page.getByText('相同 JD 正在保存，请重试',{exact:true})).toHaveCount(1);
  await expect(page.getByLabel('JD 原文')).toHaveValue('负责视频剪辑及内容创作');
  await expect(page.getByRole('dialog', { name: '新增 JD' })).toContainText('北京');
  expect(saves).toBe(1);
  expect(parses).toBe(0);
  await page.getByRole('button',{name:'关闭错误提示'}).click();
  await page.getByRole('button',{name:'保存并解析'}).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('JD 已保存，系统正在解析')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(saves).toBe(2);
  expect(parses).toBe(1);
  expect(errors).toEqual([]);
});

for (const width of [390, 768, 1280, 1440, 1920]) test(`JD HC and cycle are visible and editable without overflow at ${width}px`, async ({ page },info) => {
  await page.setViewportSize({ width, height: 900 });
  await reviewWorkspace(page); await mock(page, true);
  let updateBody: Record<string, unknown> | undefined;
  let parses = 0;
  await page.route('**/api/v1/jds/jd-1/versions**', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    updateBody = route.request().postDataJSON();
    return route.fulfill({ json: { id: 'jv-1', jdId: 'jd-1', versionNo: 1 } });
  });
  await page.route('**/api/v1/jd-versions/jv-1/parse', route => { parses++; return route.fulfill({ json: { id: 'parse-task', status: 'QUEUED' } }); });
  await page.goto('/recruitment/setup');
  await expect(page.getByText('已占用 1 / 2 · 剩余 1')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.getByRole('button', { name: /更新/ }).click();
  const dialog = page.getByRole('dialog', { name: '更新 JD' });
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(dialog).toHaveCSS('opacity', '1');
  await expect(page.getByLabel('招聘人数（HC）')).toHaveValue('2');
  await expect(page.getByLabel('招聘周期（天）')).toHaveValue('45');
  expect(await page.locator('.source-jd-form').evaluate(element=>getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(width < 768 ? 1 : 2);
  await chooseDepartment(page,'平台部');
  await page.getByLabel('招聘人数（HC）').fill('3');
  await page.getByLabel('招聘周期（天）').fill('60');
  await expect(page.getByRole('dialog', { name: jd.title, exact: true })).toHaveCount(0);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  if(width===390||width===1440)await page.screenshot({path:info.outputPath(`jd-update-${width}.png`),fullPage:true,animations:'disabled'});
  await dialog.getByRole('button', { name: '保存修改' }).click();
  await expect(page.getByText('JD 配置已更新')).toBeVisible();
  expect(updateBody).toMatchObject({ headcount: 3, recruitmentCycleDays:60, ownerDepartmentId:'dept-support', title: jd.title, rawText: '负责 Linux 云平台运维' });
  expect(parses).toBe(0);
});

test('an overdue cycle can be updated and a new JD requires its cycle', async ({ page }) => {
  await reviewWorkspace(page); await mock(page, true);
  let cycle = 1;
  let parses = 0;
  const overdueCreateTime = await page.evaluate(() => new Date(Date.now() - 2 * 86_400_000).toISOString());
  await page.route('**/api/v1/jds?**', async route => {
    return route.fulfill({ json: { ...empty, items: [{ ...jd, createTime: overdueCreateTime, recruitmentCycleDays:cycle }] } });
  });
  await page.route('**/api/v1/jds', async route => {
    if (route.request().method() === 'POST') return route.fulfill({ json: { id:'jv-1', jdId:'jd-1', versionNo:1 } });
    return route.fulfill({ json: { ...empty, items: [{ ...jd, createTime: overdueCreateTime, recruitmentCycleDays:cycle }] } });
  });
  await page.route('**/api/v1/jds/jd-1/versions**', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().postDataJSON()).toMatchObject({ recruitmentCycleDays:60 }); cycle = 60;
    return route.fulfill({ json: { id:'jv-1', jdId:'jd-1', versionNo:1 } });
  });
  await page.route('**/api/v1/jd-versions/jv-1/facts', route => route.fulfill({ status: 404, json: { title:'Not Found', status:404, code:'JD_FACTS_NOT_FOUND', detail:'岗位事实尚未生成', recoverable:false } }));
  await page.route('**/api/v1/jd-versions/jv-1/parse', route => { parses++; return route.fulfill({ json: { id:'parse', status:'QUEUED' } }); });
  await page.goto('/recruitment/setup');
  await expect(page.getByText(/招聘已超期/)).toBeVisible();
  await page.getByRole('button',{name:/更新/}).click(); await expect(page.getByLabel('招聘周期（天）')).toHaveValue('1'); await page.getByLabel('招聘周期（天）').fill('60'); await page.getByRole('dialog',{name:'更新 JD'}).getByRole('button',{name:'保存修改'}).click();
  await expect(page.getByText('JD 配置已更新')).toBeVisible(); await expect(page.getByText(/招聘已超期/)).toHaveCount(0); expect(parses).toBe(0);
  await expect(page.getByRole('dialog', { name: '更新 JD' })).toHaveCount(0);
  const detailDialog = page.getByRole('dialog', { name: jd.title, exact: true });
  await expect(detailDialog).toBeVisible();
  await detailDialog.press('Escape');
  await expect(detailDialog).toHaveCount(0);
  const createButton = page.getByRole('button',{name:/新增 JD/});
  await expect(createButton).toBeVisible();
  await createButton.click(); await page.getByLabel('招聘周期（天）').fill(''); await page.getByRole('button',{name:'保存并解析'}).click(); await expect(page.getByText('请填写招聘周期')).toBeVisible();
});

test('JD details load final facts when list polling observes success first', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.clock.install();
  await reviewWorkspace(page);
  await mock(page);
  let completed = false;
  let factReads = 0;
  await page.route('**/api/v1/jds?**', route => route.fulfill({
    json: { ...empty, items: [{ ...jd, currentParseStatus: completed ? 'SUCCEEDED' : 'RUNNING' }] },
  }));
  await page.route('**/api/v1/jd-versions/jv-1/facts', route => {
    factReads++;
    if (!completed) return route.fulfill({ status: 404, json: {
      title: 'Not Found', status: 404, code: 'JD_FACTS_NOT_FOUND', detail: '岗位事实尚未生成', recoverable: false,
    } });
    return route.fulfill({ json: {
      factSnapshotId: 'final-facts', jdVersionId: 'jv-1', snapshotVersion: 1,
      sourceType: 'AI_PARSE', schemaVersion: '1', createdAt: jd.createdAt,
      facts: {
        title: jd.title, summary: '解析完成后的岗位事实',
        professional: {
          coreSkills: [{ id: 'linux', name: 'Linux 生产环境维护', required: true, aliases: [] }],
          roleExperiences: [], minimumYears: 3, preferredYears: null,
          responsibilities: [], industryTags: [], projectTags: [],
        },
        controlledConditions: { ageMin: null, ageMax: null, sex: 'ANY', educations: [], credentials: [], languages: [] },
        fulfillmentConditions: { locations: [], availability: null, salaryMin: null, salaryMax: null, salaryPeriod: null },
      },
    } });
  });
  await page.goto('/recruitment/setup');
  await expect(page.getByText('解析中', { exact: true })).toBeVisible();
  await page.clock.pauseAt(new Date(Date.now() + 100));
  // 列表的轮询先启动，延后打开详情，使事实的下一次轮询晚于列表。
  await page.clock.runFor(1000);
  await page.getByRole('button', { name: jd.title, exact: true }).click();
  await expect.poll(async () => { await page.clock.runFor(20); return factReads; }).toBe(1);
  await page.clock.runFor(100);
  const dialog = page.getByRole('dialog', { name: jd.title, exact: true });
  await expect(dialog.getByText('系统正在解析', { exact: true })).toBeVisible();
  completed = true;
  await page.clock.runFor(1100);
  // 只推进 React 通知；不等到事实自身的下一次 2 秒轮询。
  await expect.poll(async () => { await page.clock.runFor(10); return factReads; }, { timeout: 2000 }).toBe(2);
  await page.clock.runFor(100);
  await expect(dialog.getByText('Linux 生产环境维护 · 必需', { exact: true })).toBeVisible();
  await expect(dialog.getByText('尚无解析结果', { exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: '重新解析', exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('JD detail keeps long text in one scroll surface and adapts its comparison layout', async ({ page }, info) => {
  await reviewWorkspace(page);
  await mock(page, true);
  const rawText = Array.from({ length: 35 }, (_, index) => `岗位职责 ${index + 1}：负责 Linux 云平台运维、故障排查与跨团队协作。`).join('\n');
  const responsibility = '负责大语言模型、多模态模型、传统机器学习模型的选型、微调、优化与迭代，结合业务场景搭建AI应用方案，实现模型从原型到生产环境的落地部署与常态化迭代。';
  await page.route('**/api/v1/jds/jd-1/versions**', route => route.fulfill({ json: { ...empty, items: [
    { id: 'jv-1', jdId: jd.id, versionNo: 1, title: jd.title, rawText, status: 'PUBLISHED', sourceType: 'MANUAL', createId: 'hr-owner', createTime: jd.createdAt },
  ] } }));
  await page.route('**/api/v1/jd-versions/jv-1/facts', route => route.fulfill({ json: {
    factSnapshotId: 'facts-1', jdVersionId: 'jv-1', snapshotVersion: 1, sourceType: 'AI_PARSE', schemaVersion: '1', createdAt: jd.createdAt,
    facts: {
      professional: { coreSkills: [{ id: 'linux', name: 'Linux', required: true, aliases: [] }], roleExperiences: [], minimumYears: 3, preferredYears: 5, responsibilities: [responsibility, 'LongUnbrokenTechnicalName'.repeat(12)], industryTags: [], projectTags: [] },
      controlledConditions: { ageMin: null, ageMax: null, sex: 'ANY', educations: ['本科'], credentials: [], languages: [] },
      fulfillmentConditions: { locations: ['北京'], availability: null, salaryMin: null, salaryMax: null, salaryPeriod: null },
    },
  } }));
  await page.goto('/recruitment/setup');
  const trigger = page.getByRole('button', { name: jd.title, exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: jd.title, exact: true });
  const originalText = dialog.getByRole('region', { name: 'JD 原文', exact: true }).locator('.source-jd-detail-text');
  await expect(dialog.getByText('Linux · 必需')).toBeVisible();
  await expect(originalText).toHaveText(rawText);
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  const [original, facts] = await dialog.evaluate(element => ['JD 原文', '解析事实'].map(label => {
    const region = element.querySelector(`section[aria-label="${label}"]`)!;
    const { x, y, width, height } = region.getBoundingClientRect();
    return { x, y, width, height };
  }));
  if (info.project.name === 'desktop') {
    expect(Math.abs(original!.y - facts!.y)).toBeLessThan(2);
    expect(facts!.x).toBeGreaterThan(original!.x + original!.width);
  } else expect(facts!.y).toBeGreaterThanOrEqual(original!.y + original!.height);
  expect(await dialog.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
  expect(await dialog.locator('.ant-modal-body').evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
  const longTag = dialog.getByText(responsibility, { exact: true });
  expect(await longTag.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
  expect(await longTag.evaluate(element => element.getBoundingClientRect().height)).toBeGreaterThan(40);
  expect(await originalText.evaluate(element => element.scrollHeight - element.clientHeight)).toBeLessThanOrEqual(1);
  await longTag.scrollIntoViewIfNeeded();
  await page.screenshot({ path: info.outputPath(`jd-detail-wrapping-${info.project.name}.png`), animations: 'disabled' });
  await dialog.getByRole('tab', { name: '受控条件' }).click();
  await expect(dialog.getByText('本科', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('tab', { name: /证据与可靠性/ })).toHaveCount(0);
  await expect(dialog.getByText(/原文证据/)).toHaveCount(0);
  await expect(dialog.getByText('每页显示')).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'GO', exact: true })).toHaveCount(0);
  await dialog.locator('.ant-modal-body').evaluate(element => element.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath(`jd-detail-${info.project.name}.png`), animations: 'disabled' });
  await dialog.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('JD list keeps position and region together without wrapping legacy codes', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page, true);
  const code = 'JD-01M2J5CY59Z4KS7TFYBZP6Z0G3';
  await page.route('**/api/v1/jds?**', route => route.fulfill({ json: { ...empty, items: [
    { ...jd, code, title: 'AI工程师', distinction: '北京', ownerUserId: 'admin' },
    { ...jd, id: 'jd-long', code: 'JD-000002', title: '超长岗位名称测试跨团队云平台基础架构与人工智能运维工程师', distinction: '杭州 · 云平台基础架构团队', ownerUserId: 'admin' },
  ] } }));
  await page.route('**/api/v1/jd-versions/jv-1/facts', route => route.fulfill({ status: 404, json: { title: '尚无解析结果', status: 404, code: 'JD_FACTS_NOT_FOUND' } }));
  await page.goto('/recruitment/setup');
  const table = page.locator('.source-jd-table');
  await expect(table.getByRole('button', { name: 'AI工程师', exact: true })).toBeVisible();
  for (const cell of await table.locator('thead th, tbody tr.ant-table-row > td').all()) {
    await expect(cell).toHaveCSS('text-align', 'center');
  }
  await expect(table.locator('.source-jd-title').first()).toHaveCSS('text-align', 'center');
  await expect(table.locator('.source-jd-meta').first()).toHaveCSS('justify-content', 'center');
  for (const stack of await table.locator('.ant-space-vertical').all()) {
    await expect(stack).toHaveCSS('align-items', 'center');
  }
  const identity = table.locator('.source-jd-identity').first();
  await expect(identity.getByText('北京市', { exact: true })).toBeVisible();
  await expect(identity.locator('.source-jd-code')).toHaveAttribute('title', code);
  expect(await identity.locator('.source-jd-code').evaluate(node => node.scrollWidth > node.clientWidth)).toBe(true);
  await expect(table.getByText('V1', { exact: true })).toHaveCount(2);
  if (info.project.name === 'desktop') {
    expect(await table.locator('.ant-table-content').evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await expect(table.locator('.ant-spin-spinning')).toHaveCount(0);
  await expect(table.locator('.ant-spin-blur')).toHaveCount(0);
  const longTitle = table.locator('.source-jd-title').last();
  expect(await longTitle.evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: info.outputPath(`jd-list-${info.project.name}.png`), fullPage: true, animations: 'disabled' });
  await table.getByRole('button', { name: 'AI工程师', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'AI工程师', exact: true })).toBeVisible();
});

test('workspace page titles share the JD library type scale', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page, true);
  await page.route('**/api/v1/admin/**', route => route.fulfill({ status: 503, json: { title: '暂不可用', status: 503, code: 'UNAVAILABLE' } }));
  await page.goto('/recruitment/setup');
  const heading = page.getByRole('heading', { name: 'JD 库', exact: true });
  await expect(heading).toBeVisible();
  const style = await heading.evaluate(node => {
    const css = getComputedStyle(node);
    return { size: css.fontSize, weight: css.fontWeight, lineHeight: css.lineHeight };
  });
  const jdHeadingBox = await heading.boundingBox();
  const jdContentBox = await page.locator('.settings-content').boundingBox();
  for (const [path, title] of [['/monthly-reports', '招聘月度汇报'], ['/admin/task-center', '任务运行中心']]) {
    await page.goto(path!);
    const target = page.getByRole('heading', { name: title!, exact: true, level: 1 });
    await expect(target).toHaveCSS('font-size', style.size);
    await expect(target).toHaveCSS('font-weight', style.weight);
    await expect(target).toHaveCSS('line-height', style.lineHeight);
    if (path === '/monthly-reports') {
      const reportHeadingBox = await target.boundingBox();
      const reportContentBox = await page.locator('.monthly-report-grid').boundingBox();
      expect(reportHeadingBox!.x).toBeCloseTo(jdHeadingBox!.x, 0);
      expect(reportHeadingBox!.y).toBeCloseTo(jdHeadingBox!.y, 0);
      expect(reportContentBox!.x).toBeCloseTo(jdHeadingBox!.x, 0);
      const contentWidth = await page.locator('.settings-content').evaluate(node => node.clientWidth);
      expect(reportContentBox!.x + reportContentBox!.width).toBeCloseTo(jdContentBox!.x + contentWidth, 0);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }

    await page.screenshot({ path: info.outputPath(`${path!.split('/').at(-1)}-title.png`), animations: 'disabled' });
  }
});

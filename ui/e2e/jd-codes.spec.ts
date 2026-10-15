import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty={items:[],nextCursor:null,hasMore:false};
const jd={id:'jd-1',code:'JD-000001',title:'运维工程师',workRegionName:'北京',distinction:'北京 · 云平台',status:'ACTIVE',currentVersionNo:1,currentParseStatus:'SUCCEEDED',clientId:'client-1',clientName:'示例客户',ownerUserId:'hr-owner',ownerDepartmentId:'dept',recruitmentType:'EXTERNAL',headcount:2,occupiedHeadcount:1,remainingHeadcount:1,recruitmentCycleDays:45,version:'v1',createTime:'2026-09-09T01:00:00Z',createdAt:'2026-09-09T01:00:00Z'};
async function mock(page:Page,superAdmin=false,canManage=true,onDepartmentRequest?:()=>void){
  await page.route('**/api/v1/**',route=>{const path=new URL(route.request().url()).pathname.replace('/api/v1','');let body:unknown=empty;
    if(path==='/me')body={userId:superAdmin?'admin':'hr-reader',displayName:superAdmin?'管理员':'招聘专员',workEmail:'user@example.com',homeDepartmentId:'dept',roles:[superAdmin?'SUPER_ADMIN':'HR'],permissions:['recruitment.manage','demand.read',...(canManage?['demand.manage']:[])],superAdmin};
    else if(path==='/auth/csrf')body={headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'test-csrf'};
    else if(['/management/workbench','/workbench','/admin/super-admins'].includes(path))body=[];
    else if(path==='/directory/users/resolve')body=[{id:'hr-owner',displayName:'王招聘',status:'ACTIVE'}];
    else if(path==='/directory/users')body={...empty,items:[{id:'hr-owner',displayName:'王招聘',status:'ACTIVE'}]};
    else if(path==='/directory/departments'){onDepartmentRequest?.();body=[{id:'dept',code:'OPS',name:'运维部',parentDepartmentId:null,status:'ACTIVE'},{id:'dept-support',code:'PLATFORM',name:'平台部',parentDepartmentId:null,status:'ACTIVE'}];}
    else if(path==='/clients')body={...empty,items:[{id:'client-1',code:'CLIENT_SYSTEM',name:'示例客户',status:'ACTIVE',ownerUserId:'admin',ownerDepartmentId:'dept',version:1,updatedAt:'2026-09-09'}]};
    else if(path==='/jd-code-options')body={jobTypes:[{code:'01',name:'运维工程师'}],regions:[{code:'02',name:'北京'}]};
    else if(path==='/jds/public-sources')body={...empty,items:[{id:jd.id,code:jd.code,title:jd.title,workRegionName:jd.workRegionName,currentVersionNo:1,currentVersionId:'jv-1',rawText:null,createTime:jd.createTime}]};
    else if(path==='/jds')body={...empty,items:[jd]};
    else if(path==='/jds/jd-1/versions')body={...empty,items:[{id:'jv-1',jdId:'jd-1',versionNo:1,title:jd.title,rawText:'负责 Linux 云平台运维',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-owner',createTime:jd.createdAt}]};
    else if(path==='/jd-versions/jv-1/facts')body={factSnapshotId:'facts',jdVersionId:'jv-1',snapshotVersion:1,sourceType:'AI_PARSE',schemaVersion:'1.1',createdAt:jd.createdAt,facts:{title:jd.title,summary:'岗位事实',professional:{coreSkills:[],roleExperiences:[],minimumYears:null,preferredYears:null,responsibilities:[],industryTags:[],projectTags:[]},controlledConditions:{ageMin:null,ageMax:null,sex:'ANY',educations:[],credentials:[],languages:[]},fulfillmentConditions:{locations:['北京'],availability:null,salaryMin:null,salaryMax:null,salaryPeriod:null}}};
    else if(path==='/jd-versions/jv-1/sources')body={...empty,items:[{id:'source-1',jdVersionId:'jv-1',sourceType:'MANUAL',sourceLabel:'手动录入',mailSourceId:null,ownerUserId:'hr-owner',createTime:jd.createdAt}]};
    return route.fulfill({json:body});});
}

test('stable JD code is visible, copyable and searchable with same-title guidance', async ({ page }, info) => {
  await reviewWorkspace(page); await mock(page); await page.goto('/recruitment/setup');
  await expect(page.getByText('JD-000001', { exact: true })).toBeVisible();
  const search = page.getByRole('searchbox', { name: '搜索 JD 库' });
  await page.getByRole('button', { name: jd.title, exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(`【${jd.code}】${jd.title}—张三`, { exact: true })).toBeVisible();
  await expect(dialog.getByText(`【${jd.code}】张三.pdf`, { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: /copy|复制/i })).toHaveCount(3);
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  await dialog.getByRole('button', { name: /copy|复制/i }).first().click();
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(jd.code);
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await page.screenshot({ path: info.outputPath('jd-code-detail.png'), fullPage: true, animations: 'disabled' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await dialog.getByRole('button', { name: /Close|关闭/ }).click();
  await search.fill('JD-000001');
  const request = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/jds' && new URL(request.url()).searchParams.get('query') === 'JD-000001');
  await search.press('Enter'); await request;
  await page.getByRole('button', { name: /新增 JD/ }).click();
  await page.getByLabel('岗位名称').fill(jd.title);
  await page.getByLabel('工作地区', { exact: true }).click();
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({ hasText: '北京市 · 北京市' }).click();
  await expect(page.getByText('公共 JD 列表已有同名同地区 JD，可先从公共 JD 列表应用')).toBeVisible();
  await expect(page.getByRole('button', { name: '保存并解析' })).toBeEnabled();
  await page.screenshot({ path: info.outputPath('jd-code-create.png'), fullPage: true, animations: 'disabled' });
});

test('creates an automatic job and region code from names and keeps it on update', async ({page},info) => {
  await reviewWorkspace(page); await mock(page);
  let current={...jd,ownerUserId:'hr-reader',code:'JD-990990999'};
  let createBody:Record<string,unknown>|undefined;
  let updateBody:Record<string,unknown>|undefined;
  let dictionaryRequests=0;
  await page.route('**/api/v1/jd-code-options',route=>{dictionaryRequests++;return route.fulfill({json:{jobTypes:[],regions:[]}});});
  const version=()=>({id:'jv-1',jdId:'jd-1',jdCode:current.code,versionNo:1,title:current.title,rawText:'负责 Linux 云平台运维',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-reader',createTime:jd.createTime});
  await page.route('**/api/v1/jds?*',route=>route.fulfill({json:{...empty,items:[current]}}));
  await page.route('**/api/v1/jds',async route=>{
    createBody=route.request().postDataJSON() as Record<string,unknown>;
    current={...current,title:String(createBody.title),workRegionName:String(createBody.workRegionName)};
    return route.fulfill({json:version()});
  });
  await page.route('**/api/v1/jds/jd-1/versions*',async route=>{
    if(route.request().method()==='GET')return route.fulfill({json:{...empty,items:[version()]}});
    updateBody=route.request().postDataJSON() as Record<string,unknown>;
    current={...current,title:String(updateBody.title)};
    return route.fulfill({json:version()});
  });
  await page.goto('/recruitment/setup');
  await page.getByRole('button',{name:/新增 JD/}).click();
  await expect(page.getByLabel('岗位类型',{exact:true})).toHaveCount(0);
  await expect(page.getByRole('combobox',{name:'工作地区',exact:true})).toBeVisible();
  await page.getByLabel('岗位名称',{exact:true}).fill('新岗位工程师');
  await page.getByLabel('工作地区',{exact:true}).fill('杭州');
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option').filter({hasText:'杭州市 · 浙江省'}).click();
  await page.getByLabel('工作地区',{exact:true}).scrollIntoViewIfNeeded();
  await expect(page.getByLabel('岗位名称',{exact:true})).toBeVisible();
  await expect(page.getByLabel('工作地区',{exact:true})).toBeVisible();
  await expect(page.getByRole('dialog')).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await expect(page.getByRole('dialog')).toHaveCSS('opacity','1');
  await page.screenshot({path:info.outputPath('jd-auto-create.png'),fullPage:true,animations:'disabled'});
  await page.getByLabel('招聘人数（HC）',{exact:true}).fill('3');
  await page.getByLabel('招聘类型',{exact:true}).click(); await page.getByTitle('内部招聘',{exact:true}).click();
  await expect(page.getByLabel('区分说明',{exact:true})).toHaveCount(0);
  await page.getByLabel('JD 原文',{exact:true}).fill('负责 Linux 云平台运维');
  await page.getByRole('button',{name:'保存并解析',exact:true}).click();
  await expect.poll(()=>createBody).toMatchObject({workRegionName:'杭州',title:'新岗位工程师',headcount:3});
  expect(createBody).not.toHaveProperty('distinction'); expect(createBody).not.toHaveProperty('jobTypeCode'); expect(createBody).not.toHaveProperty('workRegionCode'); expect(createBody).not.toHaveProperty('code');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByText('JD-990990999',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:/更新/}).click();
  await expect(page.getByText('原编号 JD-990990999保持不变，修改岗位信息或归属部门不会重新编号。')).toBeVisible();
  await expect(page.getByLabel('工作地区',{exact:true})).toHaveValue('杭州');
  await page.getByLabel('岗位名称',{exact:true}).fill('新岗位工程师（更新）');
  await page.getByRole('button',{name:'保存修改',exact:true}).click();
  await expect.poll(()=>updateBody).toMatchObject({title:'新岗位工程师（更新）',workRegionName:'杭州'});
  expect(updateBody).not.toHaveProperty('code'); expect(updateBody).not.toHaveProperty('distinction');
  await expect(page.getByText('JD-990990999',{exact:true})).toBeVisible();
  expect(dictionaryRequests).toBe(0);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});

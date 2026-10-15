import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const intake=(id:string,batchId:string,fileName:string)=>({
  id,batchId,sourceChannelId:'source-a',sourceRecruiterUserId:'hr-a',defaultOwnerUserId:'hr-a',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',handledByUserId:null,
  originalFileName:fileName,mediaType:'application/pdf',sizeBytes:2048,contentSha256:id.padEnd(64,'a'),scanStatus:'CLEAN',status:'COMPLETED',importStatus:'IMPORTED',failureCode:null,
  candidateId:`candidate-${id}`,resumeVersionId:`resume-${id}`,jdVersionId:null,version:1,createdAt:'2026-09-10T01:00:00Z',
});

test('每次关联收件批次都使用本次选择的 JD，并在请求期间阻止重复提交',async({page},testInfo)=>{
  await page.setViewportSize({width:testInfo.project.name==='mobile'?390:1440,height:900});
  await reviewWorkspace(page);
  const pageErrors:Error[]=[];page.on('pageerror',error=>pageErrors.push(error));
  const records=[intake('intake-a','batch-a','批次甲简历.pdf'),intake('intake-b','batch-b','批次乙简历.pdf')];
  const linked=new Map<string,string>();
  const requests:Array<{batchId:string;body:unknown}>=[];
  let releaseSecond:(()=>void)|undefined;

  await page.route('**/api/v1/source-channels?**',route=>route.fulfill({json:{items:[{id:'source-a',code:'MANUAL',name:'手工上传',channelType:'MANUAL',status:'ACTIVE',version:1}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/identity-conflicts?**',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/jds?**',route=>route.fulfill({json:{items:[
    {id:'jd-a',title:'平台架构师',status:'PUBLISHED',currentVersionNo:2},
    {id:'jd-b',title:'数据产品经理',status:'PUBLISHED',currentVersionNo:3},
  ],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/jds/jd-a/versions?**',route=>route.fulfill({json:{items:[
    {id:'jd-a-v1',jdId:'jd-a',versionNo:1,title:'平台架构师',status:'SUPERSEDED'},
    {id:'jd-a-v2',jdId:'jd-a',versionNo:2,title:'平台架构师',status:'PUBLISHED'},
  ],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/jds/jd-b/versions?**',route=>route.fulfill({json:{items:[
    {id:'jd-b-v2',jdId:'jd-b',versionNo:2,title:'数据产品经理',status:'SUPERSEDED'},
    {id:'jd-b-v3',jdId:'jd-b',versionNo:3,title:'数据产品经理',status:'PUBLISHED'},
  ],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intakes?**',route=>route.fulfill({json:{items:records.map(record=>({...record,jdVersionId:linked.get(record.batchId)??null})),hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intake-batches/*/jd-version',async route=>{
    const batchId=new URL(route.request().url()).pathname.split('/').at(-2)!;
    const body=route.request().postDataJSON();requests.push({batchId,body});
    if(batchId==='batch-b')await new Promise<void>(resolve=>{releaseSecond=resolve;});
    linked.set(batchId,body.jdVersionId);
    await route.fulfill({json:{id:batchId,jdVersionId:body.jdVersionId,sourceChannelId:'source-a',sourceRecruiterUserId:'hr-a',defaultOwnerUserId:'hr-a',ownerDepartmentId:'dept-a',status:'COMPLETED',totalCount:1,succeededCount:1,failedCount:0,createdAt:'2026-09-10T01:00:00Z'}});
  });

  const row=(fileName:string)=>page.getByRole('row').filter({hasText:fileName});
  const open=(fileName:string)=>row(fileName).getByRole('button',{name:'关联 JD'}).click();
  const selectJd=async(title:string)=>{
    const dialog=page.getByRole('dialog',{name:'关联 JD'});
    await dialog.getByRole('combobox',{name:'搜索并选择 JD'}).click();
    await page.locator('.ant-select-item-option-content').filter({hasText:title}).click();
  };

  await page.goto('/settings/intakes');
  await open('批次甲简历.pdf');await selectJd('平台架构师');
  await page.getByRole('combobox',{name:'关联的 JD 版本'}).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'V1 · 平台架构师'}).click();
  await expect(page.getByRole('button',{name:'确认关联'})).toBeEnabled();
  await page.getByRole('button',{name:'确认关联'}).click();
  await expect(page.getByRole('dialog',{name:'关联 JD'})).toHaveCount(0);
  expect(requests).toEqual([{batchId:'batch-a',body:{jdVersionId:'jd-a-v1',reason:'HR 核对历史收件并补关联 JD'}}]);

  await open('批次乙简历.pdf');
  let dialog=page.getByRole('dialog',{name:'关联 JD'});
  await expect(dialog.getByRole('button',{name:'确认关联'})).toBeDisabled();
  await expect(dialog.getByRole('button',{name:'清空岗位筛选'})).toHaveCount(0);
  await expect(dialog.getByRole('combobox',{name:'关联的 JD 版本'})).toBeDisabled();
  await expect(dialog).not.toHaveClass(/appear|enter/);
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('fresh-batch-jd-selection.png'),fullPage:true});

  await selectJd('数据产品经理');
  await dialog.getByRole('button',{name:/^取\s*消$/}).click();
  await expect(dialog).toHaveCount(0);
  await expect(row('批次乙简历.pdf').getByRole('button',{name:'关联 JD'})).toBeFocused();
  await open('批次乙简历.pdf');dialog=page.getByRole('dialog',{name:'关联 JD'});
  await expect(dialog.getByRole('button',{name:'确认关联'})).toBeDisabled();
  await expect(dialog.getByRole('button',{name:'清空岗位筛选'})).toHaveCount(0);

  await selectJd('数据产品经理');
  const submit=dialog.getByRole('button',{name:'确认关联'});await expect(submit).toBeEnabled();await submit.click();
  await expect.poll(()=>requests.length).toBe(2);await expect(submit).toHaveClass(/ant-btn-loading/);
  await dialog.locator('.ant-modal-close').click();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await submit.click();expect(requests).toHaveLength(2);
  await page.screenshot({path:testInfo.outputPath('linking-second-batch.png'),fullPage:true});
  releaseSecond?.();
  await expect(dialog).toHaveCount(0);
  expect(requests[1]).toEqual({batchId:'batch-b',body:{jdVersionId:'jd-b-v3',reason:'HR 核对历史收件并补关联 JD'}});
  expect(linked).toEqual(new Map([['batch-a','jd-a-v1'],['batch-b','jd-b-v3']]));
  expect(pageErrors).toEqual([]);
});

import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const imported of [false,true])test(`deleted ${imported?'imported duplicate':'pending'} receipts leave the list only after success and stay hidden after refresh`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  let attempts=0,ignored=false,releaseRefresh:(()=>void)|undefined;
  const importStatus=imported?'IMPORTED':'PENDING';
  const receipt={id:'ignore-one',originalFileName:imported?'同内容重复简历.pdf':'待入库简历.pdf',ownerUserId:'hr-a',sourceChannelId:'source',sizeBytes:100,jdVersionId:'jd-v2',status:imported?'COMPLETED':'PENDING_IMPORT',importStatus,duplicateContent:imported,candidateId:imported?'person-a':null,resumeVersionId:imported?'resume-v1':null,scanStatus:'CLEAN',version:1};
  const requests:URLSearchParams[]=[];
  await page.route('**/api/v1/intakes**',async route=>{
    if(route.request().method()==='POST'){
      attempts++;
      expect(route.request().url()).toContain('/ignore-one/ignore');
      expect(route.request().postDataJSON()).toEqual({version:1,reason:'HR 删除收件列表记录，保留简历及来源历史'});
      if(attempts===1)return route.fulfill({status:503,json:{status:503,title:'暂时失败',detail:'删除失败，请重试',code:'TEST_FAILED',recoverable:true}});
      ignored=true;return route.fulfill({json:{...receipt,status:'IGNORED',version:2}});
    }
    requests.push(new URL(route.request().url()).searchParams);
    if(ignored&&!releaseRefresh)await new Promise<void>(resolve=>{releaseRefresh=resolve;});
    return route.fulfill({json:{items:ignored?[]:[receipt],hasMore:false,nextCursor:null}});
  });
  await page.goto(`/settings/intakes?query=简历&importStatus=${importStatus}`);
  const row=page.getByRole('row').filter({hasText:receipt.originalFileName});
  const deleteButton=row.getByRole('button',{name:/删\s*除/});
  if(imported)await expect(row.getByRole('button',{name:'重试导入'})).toHaveCount(0);
  else await expect(row.getByRole('button',{name:'重试导入'})).toBeEnabled();
  if(imported)await page.screenshot({path:testInfo.outputPath('duplicate-intake-actions.png'),fullPage:true,animations:'disabled'});
  await deleteButton.focus();await deleteButton.press('Enter');
  await expect(page.getByText('仅移出收件列表，已入库的候选人档案、简历版本和来源历史仍保留。')).toBeVisible();
  await page.getByRole('button',{name:/^取\s*消$/}).click();
  expect(attempts).toBe(0);await expect(row).toBeVisible();
  const confirmButton=page.getByRole('button',{name:/确认删除/});
  await deleteButton.click();await confirmButton.click();
  await expect(page.getByText('删除失败，请重试',{exact:true})).toBeVisible();
  await expect(row).toBeVisible();
  await expect(deleteButton).toBeEnabled();
  await expect(confirmButton).not.toHaveClass(/ant-btn-loading/);
  await confirmButton.click();
  await expect.poll(()=>attempts).toBe(2);
  await expect.poll(()=>Boolean(releaseRefresh)).toBe(true);
  await expect(row).toHaveCount(0);
  releaseRefresh?.();
  await expect(page.getByText('当前条件下暂无导入任务')).toBeVisible();
  await page.reload();await expect(page.getByText('当前条件下暂无导入任务')).toBeVisible();await expect(row).toHaveCount(0);
  expect(requests.every(params=>params.get('query')==='简历'&&params.get('importStatus')===importStatus)).toBe(true);
  const statusFilter=page.getByRole('dialog',{name:'收件校验',exact:true}).locator('.intake-filters .ant-form-item').filter({has:page.getByText('入库状态',{exact:true})}).locator('.ant-select');
  await statusFilter.click();
  await expect(page.locator('.ant-select-item-option-content')).toHaveText(['待入库','解析中','已入库']);
  await statusFilter.locator('input').press('Escape');
  expect((await new AxeBuilder({page}).include('.app-shell').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('ignored-intake-removed.png'),fullPage:true,animations:'disabled'});
});

test('legacy ignored status links return to the active receipt list',async({page})=>{
  await reviewWorkspace(page);const requests:URLSearchParams[]=[];
  await page.route('**/api/v1/intakes?**',route=>{requests.push(new URL(route.request().url()).searchParams);return route.fulfill({json:{items:[],hasMore:false,nextCursor:null}});});
  await page.goto('/settings/intakes?status=IGNORED&cursor=old-cursor&page=2&trail=%5Bnull%5D&query=简历');await expect(page.getByText('当前条件下暂无导入任务')).toBeVisible();
  expect(requests.every(params=>!params.has('status')&&!params.has('importStatus')&&!params.has('cursor')&&params.get('query')==='简历')).toBe(true);
  expect(new URL(page.url()).pathname).toBe('/talents');
  expect(new URL(page.url()).searchParams.get('status')).toBeNull();
  const statusFilter=page.getByRole('dialog',{name:'收件校验',exact:true}).locator('.intake-filters .ant-form-item').filter({has:page.getByText('入库状态',{exact:true})}).locator('.ant-select');
  await expect(statusFilter.locator('.ant-select-selection-item')).toHaveCount(0);
});

test('read-only recruiters cannot access receipts or their actions',async({page})=>{
  await reviewWorkspace(page);
  let intakeReads=0;
  await page.route('**/api/v1/me',route=>route.fulfill({json:{userId:'reader',displayName:'只读用户',roles:['HR'],permissions:['talent.read'],superAdmin:false}}));
  await page.route('**/api/v1/intakes**',route=>{intakeReads++;return route.fulfill({json:{items:[],hasMore:false,nextCursor:null}});});
  await page.goto('/settings/intakes');
  await expect(page.getByText('当前账号没有此页面的访问权限。')).toBeVisible();
  await expect(page.getByRole('button',{name:/^删\s*除$|^入\s*库$|批量上传简历/})).toHaveCount(0);
  expect(intakeReads).toBe(0);
});

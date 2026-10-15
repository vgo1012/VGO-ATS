import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('新建面试按批读取选项姓名并保留无权候选人的缺失提示',async({page})=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/interviews?*',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications?*',route=>route.fulfill({json:{items:[
    {id:'app-a',candidateId:'person-a',jdId:'jd-a',recruitmentType:'INTERNAL'},
    {id:'app-b',candidateId:'person-b',jdId:'jd-a',recruitmentType:'INTERNAL'},
    {id:'app-c',candidateId:'person-a',jdId:'jd-a',recruitmentType:'INTERNAL'},
  ],hasMore:false,nextCursor:null}}));
  const batches:string[][]=[];
  const singleReads:string[]=[];
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('request',request=>{if(/\/api\/v1\/candidates\/person-/.test(request.url()))singleReads.push(request.url());});
  await page.route('**/api/v1/candidate-batch?*',route=>{
    batches.push(new URL(route.request().url()).searchParams.getAll('candidateIds'));
    return route.fulfill({json:[{id:'person-a',fullName:'批量姓名甲',ownerUserId:'hr-a'}]});
  });
  await page.goto('/schedule');
  const open=page.getByRole('button',{name:'新建面试'});
  await open.click();
  const dialog=page.getByRole('dialog',{name:'新建面试',exact:true});
  const selector=dialog.getByRole('combobox',{name:'候选人 / 应聘岗位'});
  await selector.fill('批量姓名甲');
  await expect(page.locator('.ant-select-item-option-content').filter({hasText:'批量姓名甲'})).toHaveCount(2);
  await selector.fill('候选人信息暂不可用');
  await expect(page.locator('.ant-select-item-option-content').filter({hasText:'候选人信息暂不可用'})).toHaveCount(1);
  expect(batches).toEqual([['person-a','person-b']]);
  expect(singleReads).toEqual([]);
  await selector.press('Escape');
  await dialog.getByRole('button',{name:/^取\s*消$/}).click();
  await expect(dialog).not.toBeVisible();
  expect(errors).toEqual([]);
});

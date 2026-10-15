import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('个人池分页文案保留真实数量和上一页下一页行为',async({page})=>{
  await reviewWorkspace(page);
  const people=Array.from({length:21},(_,index)=>({id:`pool-${index}`,fullName:`分页候选人 ${index+1}`,ownerUserId:'hr-a'}));
  await page.route('**/api/v1/candidates?*',route=>{
    const last=new URL(route.request().url()).searchParams.has('cursor');
    return route.fulfill({json:{items:last?people.slice(20):people.slice(0,20),hasMore:!last,nextCursor:last?null:'next'}});
  });
  await page.route('**/api/v1/candidates/count?**',route=>route.fulfill({json:{count:21,hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/talent-search/count?**',route=>route.fulfill({json:{count:21,hasMore:false,nextCursor:null}}));
  await page.goto('/talents');
  const pool=page.getByLabel('简历库分页');
  await expect(pool).toContainText('共21条记录');
  await expect(pool.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await pool.getByRole('button',{name:'下一页'}).click();
  await expect(pool).toContainText('共2页');
  await expect(pool.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  await expect(pool.getByRole('button',{name:'下一页'})).toBeDisabled();
  await pool.getByRole('button',{name:'上一页'}).click();
  await expect(pool).toContainText('共21条记录');
  await expect(pool.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
});

import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const width of [390,1440])test(`个人筛选方案显示真实总数和总页数 ${width}`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.setViewportSize({width,height:900});
  await page.route('**/api/v1/weight-profiles/count?*',route=>route.fulfill({json:{count:43,nextCursor:null,hasMore:false}}));
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));

  await page.goto('/settings/preferences');

  const pagination=page.getByRole('navigation',{name:'筛选方案分页',exact:true});
  await expect(pagination.getByText('条/共43条记录',{exact:true})).toBeVisible();
  await expect(pagination.getByText('共3页',{exact:true})).toBeVisible();
  await expect(pagination).not.toContainText('未提供');
  await pagination.scrollIntoViewIfNeeded();
  await expect(pagination).toBeInViewport();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({page}).include('.weight-profile-panel').analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
  await page.screenshot({path:testInfo.outputPath(`personal-profile-pagination-${width}.png`),animations:'disabled',fullPage:true});
});

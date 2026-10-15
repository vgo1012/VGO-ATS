import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = {items: [], hasMore: false, nextCursor: null};

test('talent filters combine education and keyword, survive reload, and remain accessible',async({page})=>{
  await reviewWorkspace(page);
  const queries:URL[]=[];
  await page.route('**/api/v1/talent-search**',route=>{queries.push(new URL(route.request().url()));return route.fulfill({json:empty});});
  await page.goto('/talents');
  const search=page.getByRole('textbox',{name:'搜索候选人',exact:true});
  await search.fill('Linux');
  await search.press('Enter');
  const education=page.getByRole('group',{name:'学历要求'});
  await education.getByRole('button',{name:'硕士及以上',exact:true}).click();
  await expect(page).toHaveURL(/pool_education=%E7%A1%95%E5%A3%AB%E5%8F%8A%E4%BB%A5%E4%B8%8A/);
  await expect(page).toHaveURL(/pool_q=Linux/);
  await expect.poll(()=>queries.some(url=>url.searchParams.get('q')==='Linux'&&url.searchParams.getAll('degrees').join(',')==='硕士,博士')).toBe(true);
  await page.reload();
  await expect(education.getByRole('button',{name:'硕士及以上',exact:true})).toHaveAttribute('aria-pressed','true');
  await expect(page.getByRole('search',{name:'搜索候选人'}).getByText('Linux',{exact:true})).toBeVisible();
  await education.getByRole('button',{name:'不限',exact:true}).click();
  await expect(education.getByRole('button',{name:'硕士及以上',exact:true})).toHaveAttribute('aria-pressed','false');
  for(const width of [390,768,1280,1440,1920]) {
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
  expect((await new AxeBuilder({page}).include('.talent-filters').include('.multi-condition-search').analyze()).violations).toEqual([]);
});

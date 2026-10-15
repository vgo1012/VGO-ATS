import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('resume filter controls query immediately, reset pagination and remain accessible in the candidate pool', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const queries: URLSearchParams[] = [];
  await page.route('**/api/v1/talent-search/count?**', route => route.fulfill({ json: { count: 0, hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/talent-search?**', route => { queries.push(new URL(route.request().url()).searchParams); return route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } }); });
  await page.route('**/api/v1/workbench', route => route.fulfill({ json: [{stepId:'step',applicationId:'app',candidateId:'person-a',jdId:'jd-a',ownerUserId:'hr-a',stepCode:'COMMUNICATION',overdue:false}] }));
  await page.goto('/talents');
  {
    await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
    const form = page.getByRole('form', { name: '简历筛选条件' });
    const education = form.getByRole('group', { name: '学历要求' });
    await education.getByRole('button', { name: '本科', exact: true }).click();
    await expect.poll(() => queries.at(-1)?.getAll('degrees')).toEqual(['本科']);
    await expect(education.getByRole('button', { name: '本科', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(education.getByRole('button', { name: '不限' })).toHaveAttribute('aria-pressed', 'false');
    await form.getByRole('group',{name:'经验要求'}).getByRole('button',{name:'1-3 年',exact:true}).click();
    await form.getByRole('group',{name:'年龄要求'}).getByRole('button',{name:'25-30',exact:true}).click();
    await form.getByRole('group', { name: '性别', exact: true }).getByRole('button', { name: '女', exact: true }).click();
    await expect(form.getByRole('button', { name: /^筛\s*选$/ })).toHaveCount(0);
    await expect.poll(() => queries.at(-1)?.get('scope')).toBe('PERSONAL_POOL');
    await expect.poll(() => queries.at(-1)?.get('poolStage')).toBeNull();
    await expect.poll(() => queries.at(-1)?.getAll('degrees')).toEqual(['本科']);
    await expect.poll(()=>queries.at(-1)?.get('gender')).toBe('FEMALE');
    expect(queries.at(-1)?.get('minWorkYears')).toBe('1');
    expect(queries.at(-1)?.get('maxWorkYears')).toBe('3');
    expect(queries.at(-1)?.get('maxAge')).toBe('29');
    await page.reload();
    await expect(education.getByRole('button', {name:'本科',exact:true})).toHaveAttribute('aria-pressed','true');
    await form.getByRole('button', { name: /重\s*置/ }).click();
    await expect(education.getByRole('button', { name: '不限' })).toHaveAttribute('aria-pressed', 'true');
    await expect.poll(() => new URL(page.url()).searchParams.has('pool_education')).toBe(false);
    await expect(form.getByRole('group', { name: '性别' }).getByRole('button', { name: '不限' })).toHaveAttribute('aria-pressed', 'true');
    await education.getByRole('button', { name: '本科', exact: true }).focus();
    await expect(education.getByRole('button', { name: '本科', exact: true })).toBeFocused();
    await education.getByRole('button', { name: '本科', exact: true }).press('Enter');
    await expect(education.getByRole('button', { name: '本科', exact: true })).toHaveAttribute('aria-pressed', 'true');
    expect((await new AxeBuilder({ page }).include('.talent-filters').analyze()).violations).toEqual([]);
    for (const width of [390, 768, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }
    await page.setViewportSize({ width: 1633, height: 963 });
    await page.screenshot({ path: testInfo.outputPath('candidate-pool-filters.png'), fullPage: true });
  }
});

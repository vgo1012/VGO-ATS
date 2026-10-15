import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const workspaces = [
  { path: '/talents', label: '搜索候选人', endpoint: 'talent-search', queryKey: 'pool_q', conditionKey: 'pool_conditions' },
  { path: '/shared-library', label: '共享简历关键词搜索', endpoint: 'talent-search', queryKey: 'q', conditionKey: 'conditions' },
  { path: '/onboarded', label: '入职流程关键词搜索', endpoint: 'applications', queryKey: 'q', conditionKey: 'conditions' },
];

for (const workspace of workspaces) {
  test(`${workspace.label} supports multiple required conditions and restores submitted searches`, async ({ page }, testInfo) => {
    await reviewWorkspace(page);
    const queries: URLSearchParams[] = [];
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route(url => url.pathname === `/api/v1/${workspace.endpoint}` || (workspace.path === '/talents' && url.pathname === '/api/v1/candidates'), route => {
      queries.push(new URL(route.request().url()).searchParams);
      return route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } });
    });
    await page.goto(workspace.path);
    const search = page.getByRole('search', { name: workspace.label, exact: true });
    const mainInput = search.getByRole('textbox', { name: workspace.label, exact: true });
    await mainInput.fill('高压电工证');
    const add = search.getByRole('button', { name: '添加搜索条件', exact: true });
    await add.focus();
    await page.keyboard.press('Enter');
    const second = mainInput;
    await expect(second).toBeFocused();
    await second.fill('低压电工证');
    await second.press('Enter');
    await expect.poll(() => queries.at(-1)?.get('q')).toBe('高压电工证');
    await expect.poll(() => queries.at(-1)?.getAll('conditions')).toEqual(['低压电工证']);
    expect(new URL(page.url()).searchParams.get(workspace.queryKey)).toBe('高压电工证');
    expect(new URL(page.url()).searchParams.getAll(workspace.conditionKey)).toEqual(['低压电工证']);
    await page.reload();
    await expect(search.getByRole('button', {name:'删除条件 高压电工证'})).toBeVisible();
    await expect(search.getByRole('button', {name:'删除条件 低压电工证'})).toBeVisible();
    await mainInput.fill('UPS 运维');
    await add.click();
    await search.getByRole('button', { name: '搜索', exact: true }).click();
    await expect.poll(() => queries.at(-1)?.getAll('conditions')).toEqual(['低压电工证', 'UPS 运维']);
    await expect(search.getByText('同时满足全部条件 · 最多 10 条')).toHaveCount(0);

    for (const width of [390, 768, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 1000 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await search.scrollIntoViewIfNeeded();
      await expect(add).toBeVisible();
      await expect(search.getByRole('button', { name: '搜索', exact: true })).toBeVisible();
      await search.screenshot({ path: testInfo.outputPath(`conditions-${width}.png`) });
    }
    expect((await new AxeBuilder({ page }).include('.multi-condition-search').analyze()).violations).toEqual([]);
    const remove = search.getByRole('button', { name: '删除条件 低压电工证', exact: true });
    await remove.focus();
    await page.keyboard.press('Enter');
    await expect(mainInput).toBeFocused();
    await expect(search.getByRole('button', {name:'删除条件 UPS 运维'})).toBeVisible();
    await mainInput.press('Enter');
    await expect.poll(() => queries.at(-1)?.getAll('conditions')).toEqual(['UPS 运维']);
    await search.getByRole('button', { name: '清空条件', exact: true }).click();
    await expect(mainInput).toHaveValue('');
    await expect(mainInput).toBeFocused();
    await expect(search.locator('.search-condition-tag')).toHaveCount(0);
    await expect.poll(() => queries.at(-1)?.getAll('conditions')).toEqual([]);
    expect(queries.at(-1)?.get('q') ?? '').toBe('');
    expect(new URL(page.url()).searchParams.has(workspace.conditionKey)).toBe(false);
    expect(errors).toEqual([]);
  });
}

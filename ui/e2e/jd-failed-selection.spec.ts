import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('failed JD is hidden and a later page remains selectable', async ({ page }) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/jds?**', route => {
    const next = new URL(route.request().url()).searchParams.has('cursor');
    return route.fulfill({ json: {
      items: next
        ? [{ id: 'ready', title: '可用岗位', currentVersionNo: 1, currentParseStatus: 'SUCCEEDED' }]
        : [{ id: 'failed', title: '失败岗位', currentVersionNo: 1, currentParseStatus: 'FAILED' }],
      hasMore: !next, nextCursor: next ? null : 'next',
    } });
  });
  await page.goto('/talents');
  await page.getByRole('combobox', { name: '求职岗位（选择 JD）' }).click();
  await expect(page.getByRole('option', { name: /失败岗位/ })).toHaveCount(0);
  await page.getByRole('button', { name: '加载更多岗位' }).click();
  await page.getByRole('option', { name: /可用岗位/ }).click();
  await expect(page.locator('.jd-selector')).toContainText('可用岗位');
  await expect(page).toHaveURL(/pool_jd=ready/);
});

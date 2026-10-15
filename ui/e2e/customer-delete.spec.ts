import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('客户删除支持取消、引用保护、等待防重和末页回退', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  await reviewWorkspace(page);
  const first = { id: 'referenced', code: 'A', name: '已有业务客户', status: 'ACTIVE', version: 3 };
  const last = { id: 'removable', code: 'B', name: '待删除客户', status: 'ACTIVE', version: 7 };
  let deleted = false;
  let deleteCount = 0;
  let releaseDelete: (() => void) | undefined;
  await page.route('**/api/v1/clients**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET') return route.fulfill({ json: {
      items: url.searchParams.has('cursor') ? (deleted ? [] : [last]) : [first],
      hasMore: !url.searchParams.has('cursor') && !deleted,
      nextCursor: !url.searchParams.has('cursor') && !deleted ? 'last-page' : null,
    } });
    expect(request.method()).toBe('DELETE');
    expect(request.headers()['x-csrf-token']).toBe('fixture');
    deleteCount++;
    expect(request.postDataJSON()).toEqual({ version: url.pathname.endsWith('/referenced') ? '3' : '7', reason: '管理员删除客户' });
    if (url.pathname.endsWith('/referenced')) return route.fulfill({ status: 409, contentType: 'application/problem+json', json: {
      status: 409, title: '客户不能删除', detail: '客户已有业务引用，请改用停用。', code: 'CLIENT_REFERENCED', recoverable: false,
    } });
    await new Promise<void>(resolve => { releaseDelete = resolve; });
    deleted = true;
    await route.fulfill({ status: 204 });
  });
  await page.goto('/admin/clients');
  const firstRow = page.getByRole('row').filter({ hasText: first.name });
  const firstDelete = firstRow.getByRole('button', { name: /删\s*除/ });
  await firstDelete.focus();
  await page.keyboard.press('Enter');
  const confirmation = page.getByRole('tooltip');
  await expect(confirmation).toContainText('已有业务引用的客户不能删除');
  await confirmation.getByRole('button', { name: /取\s*消/ }).click();
  expect(deleteCount).toBe(0);
  await expect(firstRow).toBeVisible();
  await firstDelete.click();
  await confirmation.getByRole('button', { name: '确认删除' }).click();
  await expect(page.getByRole('alert')).toContainText('客户已有业务引用，请改用停用。');
  await expect(firstRow.getByRole('button', { name: /停\s*用/ })).toBeEnabled();
  await expect(firstRow).toBeVisible();
  const pager = page.getByRole('navigation', { name: '客户分页' });
  await pager.getByRole('button', { name: '下一页', exact: true }).click();
  const lastRow = page.getByRole('row').filter({ hasText: last.name });
  await lastRow.getByRole('button', { name: /删\s*除/ }).click();
  await confirmation.getByRole('button', { name: '确认删除' }).click();
  await expect(lastRow.getByRole('button', { name: /删\s*除/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: '新增客户' })).toBeDisabled();
  expect(deleteCount).toBe(2);
  releaseDelete!();
  await expect(firstRow).toBeVisible();
  await expect(page.getByText(last.name, { exact: true })).toHaveCount(0);
  await expect(pager.getByRole('button', { name: '上一页', exact: true })).toBeDisabled();
  await expect(pager.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
  await page.reload();
  await expect(firstRow).toBeVisible();
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    if (width === 390 || width === 1440) await page.screenshot({ path: testInfo.outputPath(`customer-delete-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.settings-content').analyze()).violations).toEqual([]);
});

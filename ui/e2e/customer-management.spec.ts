import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('客户管理独立导航、游标分页恢复与客户维护', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  await reviewWorkspace(page);
  let first = { id: 'client-a', code: 'A', name: '第一页客户', status: 'ACTIVE', version: 1 };
  const second = { id: 'client-b', code: 'B', name: '第二页客户', status: 'ACTIVE', version: 1 };
  let failNext = true;
  let created = false;
  const cursors: (string | null)[] = [];
  await page.route('**/api/v1/clients**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET') {
      if (url.pathname.endsWith('/clients/count')) return route.fulfill({ json: { count: created ? 22 : 21, hasMore: false, nextCursor: null } });
      expect(url.searchParams.get('limit')).toBe('20');
      const cursor = url.searchParams.get('cursor');
      cursors.push(cursor);
      if (cursor && failNext) {
        failNext = false;
        return route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: '暂时不可用', detail: '客户列表暂时不可用', code: 'SERVICE_UNAVAILABLE', recoverable: true } });
      }
      return route.fulfill({ json: { items: cursor ? [second] : [first, ...(created ? [{ ...second, id: 'client-new', name: '新增测试客户' }] : [])], hasMore: !cursor, nextCursor: cursor ? null : 'opaque-client-cursor' } });
    }
    expect(request.headers()['x-csrf-token']).toBe('fixture');
    if (request.method() === 'PATCH') {
      expect(request.postDataJSON()).toEqual({ name: '已更名客户', version: 1 });
      first = { ...first, name: '已更名客户', version: 2 };
      return route.fulfill({ json: first });
    }
    if (url.pathname.endsWith('/disable')) {
      expect(request.postDataJSON().version).toBe(2);
      first = { ...first, status: 'DISABLED', version: 3 };
      return route.fulfill({ json: first });
    }
    expect(request.method()).toBe('POST');
    expect(request.postDataJSON()).toEqual({ name: '新增测试客户' });
    created = true;
    return route.fulfill({ json: { ...second, id: 'client-new', name: '新增测试客户' } });
  });
  await page.goto('/admin/clients');
  await expect(page.getByRole('heading', { name: '客户管理' })).toBeVisible();
  await expect(page.getByText('第一页客户', { exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '设置导航' })).not.toContainText('招聘报表');
  await page.getByRole('button', { name: '打开陈佳的个人菜单' }).click();
  await expect(page.getByRole('menu')).not.toContainText('招聘报表');
  await page.keyboard.press('Escape');
  const pager = page.getByRole('navigation', { name: '客户分页' });
  await expect(pager.getByRole('spinbutton', { name: '每页条数' })).toHaveValue('20');
  await expect(pager.getByRole('button', { name: '上一页', exact: true })).toBeDisabled();
  await pager.getByRole('button', { name: '下一页', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('alert')).toContainText('客户列表暂时不可用');
  await page.getByRole('button', { name: /重\s*试/ }).click();
  await expect(page.getByText('第二页客户', { exact: true })).toBeVisible();
  await expect(pager.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
  expect(cursors).toEqual([null, 'opaque-client-cursor', 'opaque-client-cursor']);
  await pager.getByRole('button', { name: '上一页', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: '第一页客户' });
  await row.getByRole('button', { name: /编\s*辑/ }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel('客户名称').fill('已更名客户');
  await dialog.getByRole('button', { name: /保\s*存/ }).click();
  await expect(dialog).not.toBeVisible();
  const renamed = page.getByRole('row').filter({ hasText: '已更名客户' });
  await expect(renamed).toBeVisible();
  await renamed.getByRole('button', { name: /停\s*用/ }).click();
  await page.getByRole('tooltip').getByRole('button', { name: /确\s*定/ }).click();
  await expect(renamed.getByRole('button', { name: /停\s*用/ })).toHaveCount(0);
  await page.getByRole('button', { name: '新增客户' }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('客户名称').fill('新增测试客户');
  await dialog.getByRole('button', { name: /保\s*存/ }).click();
  await expect(page.getByText('新增测试客户', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('新增测试客户', { exact: true })).toBeVisible();
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    if (width === 390 || width === 1440) await page.screenshot({ path: testInfo.outputPath(`customers-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.settings-content').analyze()).violations).toEqual([]);
  await page.getByRole('navigation', { name: '设置导航' }).getByRole('link', { name: 'JD 库' }).click();
  await expect(page).toHaveURL(/\/recruitment\/setup$/);
  await expect(page.getByRole('tab', { name: '客户管理' })).toHaveCount(0);
  await page.getByRole('navigation', { name: '设置导航' }).getByRole('link', { name: '客户管理' }).click();
  await expect(page).toHaveURL(/\/admin\/clients$/);
});

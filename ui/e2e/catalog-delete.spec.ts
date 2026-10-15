import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };

const channel = {
  id: 'source-delete', code: 'DELETE_TEST', name: '待删除历史来源渠道', channelType: 'MANUAL' as const,
  status: 'ACTIVE' as const, version: 3, updatedAt: '2026-09-14T01:00:00Z',
};
const department = {
  id: 'department-delete', code: 'DELETE_TEST', name: '待删除历史部门', parentDepartmentId: null,
  assignmentQueueOwnerUserId: null, assignmentQueueOwnerName: null, status: 'ACTIVE' as const, version: 3,
};

function problem(detail: string, code: 'SOURCE_CHANNEL_IN_USE' | 'DEPARTMENT_IN_USE') {
  return { status: 409, title: '删除被拒绝', detail, code, recoverable: false };
}

async function expectNoPageOverflow(page: Page, width: number) {
  await page.setViewportSize({ width, height: 900 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
}

test('管理员删除来源渠道和部门：确认、取消、失败恢复、版本和 CSRF', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  await reviewWorkspace(page);
  let channels = [channel];
  let departments = [department];
  let sourceDeletes = 0;
  let departmentDeletes = 0;
  let releaseSourceFailure!: () => void;
  let releaseDepartmentFailure!: () => void;
  const sourceFailure = new Promise<void>(resolve => { releaseSourceFailure = resolve; });
  const departmentFailure = new Promise<void>(resolve => { releaseDepartmentFailure = resolve; });

  await page.route('**/api/v1/source-channels**', async route => {
    const request = route.request();
    if (request.method() === 'GET') return route.fulfill({ json: { ...empty, items: channels } });
    sourceDeletes++;
    expect(request.method()).toBe('DELETE');
    expect(request.postDataJSON()).toEqual({ version: '3', reason: '管理员删除来源渠道' });
    expect(request.headers()['x-csrf-token']).toBe('fixture');
    if (sourceDeletes === 1) {
      await sourceFailure;
      return route.fulfill({ status: 409, contentType: 'application/problem+json', json: problem('该来源渠道暂时无法删除，请稍后重试。', 'SOURCE_CHANNEL_IN_USE') });
    }
    channels = [];
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/admin/departments**', async route => {
    const request = route.request();
    if (request.method() === 'GET') return route.fulfill({ json: departments });
    departmentDeletes++;
    expect(request.method()).toBe('DELETE');
    expect(request.postDataJSON()).toEqual({ version: '3', reason: '管理员删除部门' });
    expect(request.headers()['x-csrf-token']).toBe('fixture');
    if (departmentDeletes === 1) {
      await departmentFailure;
      return route.fulfill({ status: 409, contentType: 'application/problem+json', json: problem('部门仍有关联账号或子部门，不能删除。', 'DEPARTMENT_IN_USE') });
    }
    departments = [];
    return route.fulfill({ status: 204 });
  });

  await page.goto('/admin/source-channels');
  const sourceRow = page.locator('tr', { hasText: channel.name });
  await expect(sourceRow).toBeVisible();
  for (const width of [390, 1280, 1440, 1920]) await expectNoPageOverflow(page, width);
  expect((await new AxeBuilder({ page }).include('.settings-list-page').analyze()).violations).toEqual([]);

  await sourceRow.getByRole('button', { name: /删\s*除/ }).click();
  const sourceConfirm = page.getByRole('tooltip').filter({ hasText: `确认删除来源渠道“${channel.name}”？` });
  await expect(sourceConfirm).toContainText('仅可删除未使用的渠道；已关联收件或简历时请使用停用。');
  await sourceConfirm.screenshot({ path: testInfo.outputPath('source-channel-delete-confirm.png'), animations: 'disabled' });
  await sourceConfirm.getByRole('button', { name: /取\s*消/ }).click();
  await expect(sourceConfirm).not.toBeVisible();
  expect(sourceDeletes).toBe(0);

  await sourceRow.getByRole('button', { name: /删\s*除/ }).click();
  await sourceConfirm.getByRole('button', { name: /确\s*认\s*删\s*除/ }).click();
  await expect(sourceRow.getByRole('button', { name: /删\s*除/ })).toBeDisabled();
  await expect(sourceRow.getByRole('button', { name: /编\s*辑/ })).toBeDisabled();
  await expect.poll(() => sourceDeletes).toBe(1);
  releaseSourceFailure();
  await expect(page.getByRole('alert')).toContainText('该来源渠道暂时无法删除');
  await expect(sourceRow).toBeVisible();
  await expect(sourceRow.getByRole('button', { name: /删\s*除/ })).toBeEnabled();

  await sourceRow.getByRole('button', { name: /删\s*除/ }).click();
  await sourceConfirm.getByRole('button', { name: /确\s*认\s*删\s*除/ }).click();
  await expect(sourceRow).toHaveCount(0);
  expect(sourceDeletes).toBe(2);
  await page.screenshot({ path: testInfo.outputPath('source-channel-delete-complete.png'), fullPage: true, animations: 'disabled' });

  await page.goto('/admin/identity');
  await page.getByRole('tab', { name: /部门/ }).click();
  const departmentRow = page.locator('tr', { hasText: department.name });
  await expect(departmentRow).toBeVisible();
  for (const width of [390, 1280, 1440, 1920]) await expectNoPageOverflow(page, width);
  expect((await new AxeBuilder({ page }).include('.identity-admin-page').analyze()).violations).toEqual([]);

  await departmentRow.getByRole('button', { name: /删\s*除/ }).click();
  const departmentConfirm = page.getByRole('tooltip').filter({ hasText: `确认删除部门“${department.name}”？` });
  await expect(departmentConfirm).toContainText('存在关联账号、子部门或业务记录时无法删除；历史记录保留。');
  await departmentConfirm.screenshot({ path: testInfo.outputPath('department-delete-confirm.png'), animations: 'disabled' });
  await departmentConfirm.getByRole('button', { name: /取\s*消/ }).click();
  expect(departmentDeletes).toBe(0);

  await departmentRow.getByRole('button', { name: /删\s*除/ }).click();
  await departmentConfirm.getByRole('button', { name: /确\s*认\s*删\s*除/ }).click();
  await expect(departmentRow.getByRole('button', { name: /删\s*除/ })).toBeDisabled();
  await expect(departmentRow.getByRole('button', { name: /编\s*辑/ })).toBeDisabled();
  await expect.poll(() => departmentDeletes).toBe(1);
  releaseDepartmentFailure();
  await expect(page.getByRole('alert')).toContainText('部门仍有关联账号或子部门');
  await expect(departmentRow).toBeVisible();
  await expect(departmentRow.getByRole('button', { name: /删\s*除/ })).toBeEnabled();

  await departmentRow.getByRole('button', { name: /删\s*除/ }).click();
  await departmentConfirm.getByRole('button', { name: /确\s*认\s*删\s*除/ }).click();
  await expect(departmentRow).toHaveCount(0);
  expect(departmentDeletes).toBe(2);
  await page.screenshot({ path: testInfo.outputPath('department-delete-complete.png'), fullPage: true, animations: 'disabled' });
});

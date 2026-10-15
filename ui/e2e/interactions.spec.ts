import { expect, test, type Page } from '@playwright/test';
import { reviewDialog } from './dialogReview';

const actor = { userId: 'review-user', displayName: '招聘负责人', workEmail: 'review@example.com', roles: ['SUPER_ADMIN'], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };
const empty = { items: [], hasMore: false, nextCursor: null };

async function workspace(page: Page) {
  await page.clock.setFixedTime(new Date('2026-09-05T12:00:00+08:00'));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = ['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks'].includes(path) ? [] : empty;
    if (path === '/me') body = actor;
    if (['/candidate-pool-statuses', '/candidate-resume-summaries', '/applications/stage-counts'].includes(path)) body = [];
    if (path === '/auth/csrf') body = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'interaction-csrf' };
    if (['/admin/users', '/admin/departments', '/admin/super-admins', '/directory/users/resolve', '/directory/departments'].includes(path)) body = [];
    return route.fulfill({ json: body });
  });
}

test('existing forms fit their task and keep keyboard focus in one layer', async ({ page }) => {
  await workspace(page);
  for (const [url, tab, button, name] of [
    ['/recruitment/setup', '', '新增 JD', 'create-demand'],
    ['/admin/identity', '', '注册账号', 'create-user'],
    ['/admin/identity', '部门', '新建部门', 'create-department'],
  ]) {
    await page.goto(url!);
    if (tab) await page.getByRole('tab', { name: new RegExp(tab) }).click();
    const trigger = page.getByRole('button', { name: new RegExp(`${button}$`) });
    await trigger.click();
    await reviewDialog(page, name!);
    const dialog = page.locator('.workspace-dialog:visible');
    if (name === 'create-user') {
      await dialog.getByRole('checkbox', { name: '招聘专员', exact: true }).uncheck();
      await dialog.getByRole('button', { name: '保存账号' }).click();
      await expect(dialog.getByText('至少选择一个业务角色')).toBeVisible();
      await expect(dialog.getByLabel('姓名', { exact: true })).toBeFocused();
    }
    await page.mouse.click(2, 2);
    await expect(dialog).toBeVisible();
    await dialog.locator('.ant-modal-close').focus();
    for (let step = 0; step < 3; step++) {
      await page.keyboard.press('Shift+Tab');
      expect(await page.evaluate(() => Boolean(document.activeElement?.closest('.workspace-dialog')))).toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
  }
});

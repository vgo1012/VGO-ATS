import { expect, test } from '@playwright/test';

test('updates a short lowercase password after validating confirmation and retries a current-password error', async ({ page }) => {
  const actor = { userId: 'password-user', displayName: '密码用户', workEmail: 'password@example.com', homeDepartmentId: 'dept', roles: [], permissions: [], superAdmin: false, mustChangePassword: true, version: '1' };
  let updated = false;
  let attempts = 0;
  await page.route('**/api/v1/me', route => route.fulfill(updated
    ? { status: 401, json: { code: 'SESSION_REQUIRED' } }
    : { json: actor }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'password-csrf' } }));
  await page.route('**/api/v1/me/password', async route => {
    attempts += 1;
    expect(route.request().postDataJSON()).toEqual({ currentPassword: attempts === 1 ? 'wrong-password' : 'correct-password', newPassword: 'abc' });
    if (attempts === 1) {
      await route.fulfill({ status: 400, json: { title: '密码更新失败', status: 400, detail: '当前密码不正确', code: 'INVALID_PASSWORD', recoverable: true } });
    } else {
      updated = true;
      await route.fulfill({ status: 204 });
    }
  });
  await page.goto('/change-password');
  await expect(page.getByText('请输入新密码，并再次输入以确认。')).toBeVisible();
  await page.getByLabel('当前密码', { exact: true }).fill('wrong-password');
  await page.getByLabel('新密码', { exact: true }).fill('   ');
  await page.getByLabel('确认新密码', { exact: true }).fill('   ');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page.getByText('请输入新密码', { exact: true })).toBeVisible();
  expect(attempts).toBe(0);
  await page.getByLabel('新密码', { exact: true }).fill('abc');
  await page.getByLabel('确认新密码', { exact: true }).fill('def');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page.getByText('两次输入的密码不一致')).toBeVisible();
  expect(attempts).toBe(0);
  await page.getByLabel('确认新密码', { exact: true }).fill('abc');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page.getByText('当前密码不正确')).toBeVisible();
  await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('abc');
  await page.getByLabel('当前密码', { exact: true }).fill('correct-password');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(attempts).toBe(2);
});

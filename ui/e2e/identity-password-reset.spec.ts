import { expect, test } from '@playwright/test';

test('confirms a random-password reset, retries and copies the returned credential', async ({ page }) => {
  const temporaryPassword = 'Synthetic_Reset_Credential_12345';
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: (text: string) => { sessionStorage.setItem('copied-test-credential', text); return Promise.resolve(); } } });
  });
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/me') return route.fulfill({ json: { userId: 'admin', displayName: '管理员', workEmail: 'admin@example.com', roles: ['SUPER_ADMIN'], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf' } });
    if (path === '/admin/users') return route.fulfill({ json: [{ id: 'user-1', displayName: '测试账号', workEmail: 'user@example.com', sourceTitle: null, homeDepartmentId: 'dep', homeDepartmentName: '部门', directLeaderUserId: null, directLeaderName: null, status: 'ACTIVE', mustChangePassword: false, version: 1 }] });
    if (path.startsWith('/admin/')) return route.fulfill({ json: [] });
    return route.fulfill({ json: [] });
  });
  let attempts = 0;
  await page.route('**/api/v1/admin/users/user-1/credentials/reset', route => {
    attempts++;
    if (attempts === 1) return route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: '服务暂不可用', detail: '重置失败，请重试', code: 'SERVICE_UNAVAILABLE', recoverable: true } });
    return route.fulfill({ json: { userId: 'user-1', oneTimeCredential: temporaryPassword, mustChangePassword: true, version: '2' } });
  });
  await page.goto('/admin/identity');
  const reset = page.getByRole('button', { name: '重置凭据' });
  await reset.click();
  await expect(page.getByText('确认生成新的随机临时密码？')).toBeVisible();
  expect(attempts).toBe(0);
  await page.getByRole('button', { name: '确认重置' }).click();
  await expect(page.getByText('重置失败，请重试')).toBeVisible();
  await reset.click();
  await page.getByRole('button', { name: '确认重置' }).click();
  await expect(page.getByRole('dialog')).toContainText('已生成随机临时密码');
  await expect(page.getByRole('dialog')).toContainText(temporaryPassword);
  await expect(page.getByRole('dialog')).not.toContainText('user@example.com');
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  await page.getByRole('button', { name: '复制临时密码', exact: true }).click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('copied-test-credential'))).toBe(temporaryPassword);
  await page.getByRole('button', { name: '我已知悉' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain(temporaryPassword);
  expect(attempts).toBe(2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
});

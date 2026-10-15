import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const actor = { userId: 'login-user', displayName: '登录用户', workEmail: 'login@example.com', homeDepartmentId: 'login-dept', roles: ['SUPER_ADMIN'], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };

async function mockGuest(page: Page) {
  await page.route('**/api/v1/me', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'login-csrf' } }));
}

test('exposes the redesigned login contract without legacy access surfaces', async ({ page }) => {
  await mockGuest(page);
  await page.goto('/login');

  const account = page.getByLabel('账号', { exact: true });
  const password = page.getByLabel('密码', { exact: true });
  await expect(page.getByRole('heading', { name: '欢迎回来', level: 1 })).toBeVisible();
  await expect(page.getByText('登录工作台，继续今天的招聘工作。', { exact: true })).toBeVisible();
  await expect(account).toHaveAttribute('type', 'text');
  await expect(account).toHaveAttribute('autocomplete', 'username');
  await expect(account).toHaveAttribute('placeholder', '请输入账号或工作邮箱');
  await expect(password).toHaveAttribute('type', 'password');
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible();
  await expect(page.getByRole('checkbox', { name: '记住账号' })).not.toBeChecked();
  await expect(page.getByText('在浏览器提示时选择保存密码，下次可自动填充。')).toBeVisible();
  await expect(page.locator('.login-back, .login-identity, .login-sso, .login-foot')).toHaveCount(0);
  await expect(page.locator('a[href^="mailto:"], a[href^="tel:"]')).toHaveCount(0);
  expect((await new AxeBuilder({ page }).include('.login-screen').analyze()).violations).toEqual([]);
});

test('submits the built-in admin login alias', async ({ page }) => {
  await mockGuest(page);
  await page.route('**/api/v1/auth/login', async route => {
    expect(route.request().postDataJSON()).toEqual({ workEmail: 'admin', password: 'Secret-123456' });
    await route.fulfill({ json: actor });
  });
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill('admin');
  await page.getByLabel('密码', { exact: true }).fill('Secret-123456');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
});

test('shows a recoverable message when the gateway limits login requests', async ({ page }) => {
  await mockGuest(page);
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 429,
    contentType: 'text/html',
    body: '<html><body>429 Too Many Requests</body></html>',
  }));
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill('login@example.com');
  await page.getByLabel('密码', { exact: true }).fill('Secret-123456');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('请求过于频繁，请稍后重试。');
  await expect(page).toHaveURL(/\/login$/);
});

test('restores a permitted deep link with its query after login', async ({ page }) => {
  let authenticated = false;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/me') return authenticated
      ? route.fulfill({ json: actor })
      : route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'login-csrf' } });
    if (path === '/auth/login') { authenticated = true; return route.fulfill({ json: actor }); }
    if (path.endsWith('/count')) return route.fulfill({ json: { count: 0, nextCursor: null, hasMore: false } });
    if (['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/candidate-pool-statuses'].includes(path)) return route.fulfill({ json: [] });
    if (path === '/directory/departments') return route.fulfill({ json: [] });
    return route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } });
  });

  await page.goto('/talents?intake=1');
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  await page.getByLabel('账号', { exact: true }).fill('login@example.com');
  await page.getByLabel('密码', { exact: true }).fill('Secret-123456');
  await page.getByRole('button', { name: '登录', exact: true }).click();

  await expect(page).toHaveURL(/\/talents\?intake=1$/);
  await expect(page.getByRole('dialog', { name: '解析校验', exact: true })).toBeVisible();
});

test('keeps the active workspace when logout is not confirmed and succeeds on retry', async ({ page }) => {
  let authenticated = true;
  let logoutAttempts = 0;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/me') return authenticated
      ? route.fulfill({ json: actor })
      : route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'logout-csrf' } });
    if (path === '/auth/logout') {
      logoutAttempts += 1;
      if (logoutAttempts === 1) return route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        json: { title: '服务暂不可用', status: 503, detail: '退出失败，请稍后重试。', code: 'TEMPORARY_UNAVAILABLE', recoverable: true },
      });
      authenticated = false;
      return route.fulfill({ status: 204 });
    }
    if (path.endsWith('/count')) return route.fulfill({ json: { count: 0, nextCursor: null, hasMore: false } });
    if (['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/candidate-pool-statuses'].includes(path)) return route.fulfill({ json: [] });
    if (path === '/directory/departments') return route.fulfill({ json: [] });
    return route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } });
  });

  await page.goto('/talents');
  await expect(page.getByRole('button', { name: '批量上传', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /打开.+的个人菜单/ }).click();
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click();
  await expect(page.getByText('退出失败，请稍后重试。', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/talents$/);
  await expect(page.getByRole('button', { name: '批量上传', exact: true })).toBeVisible();

  await page.getByRole('button', { name: /打开.+的个人菜单/ }).click();
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(logoutAttempts).toBe(2);
});

test('supports logo, password and help actions from the keyboard', async ({ page }) => {
  await mockGuest(page);
  await page.goto('/login');

  const toggle = page.getByRole('button', { name: '显示密码' });
  await toggle.focus();
  await toggle.press('Space');
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'text');
  await expect(page.getByRole('button', { name: '隐藏密码' })).toBeFocused();

  const forgot = page.getByRole('button', { name: '忘记密码？' });
  await forgot.focus();
  await forgot.press('Enter');
  await expect(page.getByRole('status')).toHaveText('请联系组织管理员重置密码；重置后首次登录需设置新密码。');
  await expect(page.getByRole('alert')).toHaveCount(0);

  const administrator = page.getByRole('button', { name: '联系管理员' });
  await administrator.focus();
  await administrator.press('Space');
  await expect(page.getByRole('status')).toHaveText('请联系为你分配工作账号的组织管理员，协助处理登录问题。');
  await expect(page.getByRole('alert')).toHaveCount(0);

  const logo = page.getByRole('button', { name: '返回品牌首页' });
  await logo.focus();
  await logo.press('Enter');
  await expect(page).toHaveURL(/\/$/);
});

test('remember account stores only the account and never restores the password', async ({ page }) => {
  await mockGuest(page);
  await page.route('**/api/v1/auth/login', async route => {
    expect(route.request().postDataJSON()).toEqual({ workEmail: 'login@example.com', password: 'Secret-123456' });
    await route.fulfill({ json: actor });
  });
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill('login@example.com');
  await page.getByLabel('密码', { exact: true }).fill('Secret-123456');
  await page.getByRole('checkbox', { name: '记住账号' }).check();
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  expect(await page.evaluate(() => localStorage.getItem('vgo.remembered-work-email'))).toBe('login@example.com');
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('Secret-123456');

  await page.goto('/login');
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('login@example.com');
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('');
  await expect(page.getByRole('checkbox', { name: '记住账号' })).toBeChecked();
});

test('submits browser-autofilled credentials without input or change events', async ({ page }) => {
  await mockGuest(page);
  await page.route('**/api/v1/auth/login', async route => {
    expect(route.request().postDataJSON()).toEqual({ workEmail: 'autofilled@example.com', password: 'Browser-Secret-123' });
    await route.fulfill({ json: actor });
  });
  await page.goto('/login');

  await page.getByLabel('账号', { exact: true }).evaluate((input: HTMLInputElement) => { input.value = 'autofilled@example.com'; });
  await page.getByLabel('密码', { exact: true }).evaluate((input: HTMLInputElement) => { input.value = 'Browser-Secret-123'; });
  await page.getByRole('button', { name: '登录', exact: true }).click();

  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  expect(await page.evaluate(() => localStorage.getItem('vgo.remembered-work-email'))).toBeNull();
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('Browser-Secret-123');
});

test('stays usable without the illustration and across supported viewports', async ({ page }) => {
  await mockGuest(page);
  await page.route('**/assets/*', route => route.request().resourceType() === 'image' ? route.abort() : route.continue());
  for (const [width, height] of [[390, 844], [768, 900], [960, 540], [1280, 900], [1440, 900], [1672, 900], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), `${width}x${height}`).toBeLessThanOrEqual(1);
  }
});

test('keeps the mandatory password step usable in the shared access layout', async ({ page }) => {
  await mockGuest(page);
  let authenticated = true;
  let mustChangePassword = true;
  await page.route('**/api/v1/me', route => authenticated
    ? route.fulfill({ json: { ...actor, mustChangePassword } })
    : route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  await page.route('**/api/v1/me/password', async route => {
    expect(route.request().postDataJSON()).toEqual({ currentPassword: 'Old-Password-123', newPassword: 'New-Password-123' });
    authenticated = false;
    mustChangePassword = false;
    await route.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/auth/login', async route => {
    expect(route.request().postDataJSON()).toEqual({ workEmail: actor.workEmail, password: 'New-Password-123' });
    authenticated = true;
    await route.fulfill({ json: { ...actor, mustChangePassword } });
  });
  await page.goto('/login');
  await expect(page).toHaveURL(/\/change-password$/);
  await expect(page.getByRole('heading', { name: '设置新密码' })).toBeVisible();
  await page.getByLabel('当前密码', { exact: true }).fill('Old-Password-123');
  await page.getByLabel('新密码', { exact: true }).fill('New-Password-123');
  await page.getByLabel('确认新密码', { exact: true }).fill('Different-Password-123');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page.getByText('两次输入的密码不一致')).toBeVisible();
  await page.getByLabel('确认新密码', { exact: true }).fill('New-Password-123');
  await page.getByRole('button', { name: '更新密码' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  await page.getByLabel('账号', { exact: true }).fill(actor.workEmail);
  await page.getByLabel('密码', { exact: true }).fill('New-Password-123');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  await expect(page.getByRole('heading', { name: '设置新密码' })).toHaveCount(0);
  await page.reload();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
});

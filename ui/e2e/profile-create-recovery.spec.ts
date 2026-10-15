import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

async function workspace(page: Page, rejection?: number) {
  await reviewWorkspace(page);
  const requests: Array<{ key: string; body: Record<string, unknown> }> = [];
  const saved = new Map<string, { signature: string; value: Record<string, unknown> }>();
  const state = { wait: undefined as Promise<void> | undefined };
  await page.route('**/api/v1/weight-profiles**', async route => {
    const request = route.request();
    if (new URL(request.url()).pathname.endsWith('/weight-profiles/count')) {
      return route.fulfill({ json: { count: saved.size, nextCursor: null, hasMore: false } });
    }
    if (request.method() === 'GET') return route.fulfill({ json: { items: [...saved.values()].map(item => item.value), hasMore: false, nextCursor: null } });
    expect(request.method()).toBe('POST');
    const key = request.headers()['idempotency-key'] ?? '';
    const body = request.postDataJSON() as Record<string, unknown>;
    requests.push({ key, body });
    expect(key).not.toBe('');
    if (rejection && requests.length === 1) return route.fulfill({ status: rejection, contentType: 'application/problem+json', json: {
      status: rejection, title: '方案校验未通过', detail: '请修改方案名称后重试', code: 'VALIDATION_ERROR', recoverable: false,
    } });
    const signature = JSON.stringify(body);
    if (saved.has(key)) expect(saved.get(key)!.signature).toBe(signature);
    else saved.set(key, { signature, value: {
      profile: { id: `profile-${saved.size + 1}`, name: body.name, ownerUserId: 'hr-a', status: 'ACTIVE', version: 1 },
      currentVersion: { id: `profile-version-${saved.size + 1}`, versionNo: 1, skillsWeight: 20, roleWeight: 20,
        yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20,
        screeningSettingsJson: JSON.stringify({ criteria: body.criteria, defaultSortMode: body.defaultSortMode }) },
    } });
    await state.wait;
    if (!rejection && requests.length === 1) return route.fulfill({ status: 200, contentType: 'application/json', body: '{"profile":' });
    return route.fulfill({ status: 200, json: saved.get(key)!.value });
  });
  await page.goto('/settings/preferences');
  await page.getByRole('button', { name: /新建方案$/ }).click();
  const dialog = page.getByRole('dialog', { name: '新建个人筛选方案', exact: true });
  await expect(dialog).toBeVisible();
  return { requests, saved, state, dialog };
}

for (const width of [390, 1440]) {
  test(`新建个人方案响应丢失后冻结内容并同键恢复 ${width}`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const api = await workspace(page);
    const name = api.dialog.getByRole('textbox', { name: '方案名称', exact: true });
    await name.fill('平台工程师方案');
    const save = api.dialog.getByRole('button', { name: /保存方案$/ });
    await save.click();
    await expect(api.dialog).toContainText('上次保存结果尚未确认，请重试确认后再修改方案。');
    await expect(name).toBeDisabled();
    await expect(name).toHaveValue('平台工程师方案');
    expect(api.saved.size).toBe(1);
    await api.dialog.getByRole('form', { name: '个人筛选方案内容' }).focus();
    await expect(api.dialog.getByRole('form', { name: '个人筛选方案内容' })).toBeFocused();
    expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
    await page.screenshot({ path: info.outputPath(`profile-create-uncertain-${width}.png`), animations: 'disabled', fullPage: true });
    const dismissError = page.getByRole('button', { name: '关闭错误提示', exact: true });
    if (await dismissError.isVisible()) await dismissError.click();
    await api.dialog.getByRole('button', { name: /^取\s*消$/ }).click();
    await expect(api.dialog).not.toBeVisible();
    await page.getByRole('button', { name: /新建方案$/ }).click();
    await expect(api.dialog).toContainText('上次保存结果尚未确认，请重试确认后再修改方案。');
    await expect(name).toHaveValue('平台工程师方案');
    await expect(name).toBeDisabled();
    let release!: () => void;
    api.state.wait = new Promise<void>(resolve => { release = resolve; });
    await save.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => api.requests.length).toBe(2);
      await expect(save).toHaveClass(/ant-btn-loading/);
      await expect(name).toBeDisabled();
      expect(api.requests[1]).toEqual(api.requests[0]);
      expect(api.saved.size).toBe(1);
    } finally { release(); }
    await expect(api.dialog).not.toBeVisible();
    await expect(page.locator('.weight-profile-panel')).toContainText('平台工程师方案');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    expect(errors).toEqual([]);
  });

  for (const status of [400, 422]) test(`新建个人方案 ${status} 拒绝后可修改并重新保存 ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const api = await workspace(page, status);
    const name = api.dialog.getByRole('textbox', { name: '方案名称', exact: true });
    await name.fill('待修改方案');
    await api.dialog.getByRole('button', { name: /保存方案$/ }).click();
    await expect(page.getByText('请修改方案名称后重试', { exact: true })).toBeVisible();
    await expect(name).toBeEnabled();
    expect(api.saved.size).toBe(0);
    await name.fill('修订后的个人方案');
    await api.dialog.getByRole('button', { name: /保存方案$/ }).click();
    await expect(api.dialog).not.toBeVisible();
    await expect(page.locator('.weight-profile-panel')).toContainText('修订后的个人方案');
    expect(api.requests).toHaveLength(2);
    expect(api.requests[1]!.key).not.toBe(api.requests[0]!.key);
    expect(api.requests[1]!.body.name).toBe('修订后的个人方案');
    expect(api.saved.size).toBe(1);
    expect(errors).toEqual([]);
  });
}

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

async function openHome(page: Page) {
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('让人才与机会在这里相遇');
  await page.evaluate(() => document.fonts.ready);
}

test('shows the recruiting homepage and opens login from the keyboard', async ({ page }) => {
  await openHome(page);
  await expect(page.getByText(/VGO ATS|AI 辅助招聘/)).toHaveCount(0);
  await expect(page.getByRole('list', { name: '招聘能力' })).toHaveText('简历解析智能匹配招聘协同');
  await expect(page.locator('time')).toHaveCount(0);
  await expect(page.getByLabel('今日日历')).toHaveCount(0);
  await expect(page.getByRole('button')).toHaveCount(1);
  await expect.poll(() => page.locator('.landing-art').evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);

  const login = page.getByRole('button', { name: '登录工作台' });
  await login.focus();
  await expect(login).toBeFocused();
  await login.press('Space');
  await expect(page).toHaveURL(/\/login$/);
});

test('keeps text and login usable if the decorative illustration fails', async ({ page }) => {
  await page.route('**/assets/vgo-talent-connection.png', route => route.abort());
  await openHome(page);
  await expect(page.getByText('连接简历、岗位与招聘流程，让每一次选择更有依据。')).toBeVisible();
  await page.getByRole('button', { name: '登录工作台' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('has no automated accessibility violations or page errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await openHome(page);

  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
  await expect(page.locator('video')).toHaveCount(0);
  expect(await page.getByRole('button', { name: '登录工作台' }).evaluate(node => parseFloat(getComputedStyle(node).transitionDuration))).toBeLessThan(0.01);
});

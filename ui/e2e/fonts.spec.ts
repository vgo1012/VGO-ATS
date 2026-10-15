import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('renders public pages, login, workspace and dialogs with the bundled Noto Sans SC', async ({ page }, testInfo) => {
  const fontRequests: string[] = [];
  const errors: string[] = [];
  page.on('request', request => {
    if (request.resourceType() === 'font') fontRequests.push(request.url());
  });
  page.on('pageerror', error => errors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  const protocol = await page.context().newCDPSession(page);
  await protocol.send('DOM.enable');
  await protocol.send('CSS.enable');
  const renderedFonts: unknown[] = [];
  const checkFont = async (selector: string) => {
    await expect(page.locator(selector)).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    const { root } = await protocol.send('DOM.getDocument');
    const { nodeId } = await protocol.send('DOM.querySelector', { nodeId: root.nodeId, selector });
    const { fonts } = await protocol.send('CSS.getPlatformFontsForNode', { nodeId });
    expect(fonts.length).toBeGreaterThan(0);
    for (const font of fonts) {
      expect(font.isCustomFont, selector).toBe(true);
      // The variable font's internal family name includes "Thin"; CSS selects its weight.
      expect(font.familyName, selector).toBe('Noto Sans SC Thin');
    }
    renderedFonts.push({ selector, fonts });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  };

  await page.goto('/');
  await checkFont('.calendar-greeting > span:last-child');
  await expect(page.getByRole('heading', { name: 'VGO ATS' })).toHaveCSS('font-family', /Archivo Black/);
  await page.goto('/login');
  await checkFont('.login-heading h1');
  await expect(page.locator('.login-heading h1')).toHaveCSS('font-weight', '800');
  await page.getByRole('textbox', { name: '账号' }).focus();
  await expect(page.getByRole('textbox', { name: '账号' })).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath('noto-login.png'), fullPage: true });

  await reviewWorkspace(page);
  await page.goto('/talents');
  await checkFont('[role="tab"][aria-selected="true"]');
  await page.screenshot({ path: testInfo.outputPath('noto-talents.png'), fullPage: true });
  const candidateSummary = page.getByRole('button', { name: '林雨桐', exact: true });
  await candidateSummary.click();
  await checkFont('.ant-modal-title');
  await page.getByRole('dialog').press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(candidateSummary).toBeFocused();

  expect(fontRequests.some(url => url.includes('/fonts/noto-sans-sc-v40/'))).toBe(true);
  expect(fontRequests.every(url => new URL(url).origin === new URL(page.url()).origin)).toBe(true);
  expect(errors).toEqual([]);
  await testInfo.attach('rendered-fonts.json', {
    body: JSON.stringify({ fontRequests, renderedFonts }, null, 2),
    contentType: 'application/json',
  });
});

test('keeps login readable and usable when web fonts cannot load', async ({ page }) => {
  await page.route('**/*.woff2', route => route.abort());
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  await page.getByRole('textbox', { name: '账号' }).fill('font-check@example.com');
  await expect(page.getByRole('textbox', { name: '账号' })).toHaveValue('font-check@example.com');
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
});

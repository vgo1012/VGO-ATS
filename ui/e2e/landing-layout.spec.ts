import { expect, test } from '@playwright/test';

test('keeps the recruiting hero readable across screen ratios and zoom', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Explicit viewport matrix.');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));
  await page.goto('/');
  await page.evaluate(() => document.fonts.ready);
  await expect.poll(() => page.locator('.landing-art').evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);

  for (const [width, height] of [[1920, 1080], [1704, 924], [1440, 900], [1280, 720], [960, 540], [768, 1024], [390, 844], [320, 844]]) {
    await page.setViewportSize({ width, height });
    const title = (await page.getByRole('heading', { level: 1 }).boundingBox())!;
    const scene = (await page.locator('.landing-art').boundingBox())!;
    const header = (await page.locator('.landing-header').boundingBox())!;
    const footer = (await page.locator('.landing-footer').boundingBox())!;
    expect(title.y).toBeGreaterThan(header.y + header.height);
    expect(title.y + title.height).toBeLessThan(footer.y);
    expect(title.x).toBeGreaterThanOrEqual(0);
    expect(title.x + title.width).toBeLessThanOrEqual(width);
    expect(scene.y + scene.height).toBeLessThanOrEqual(footer.y);
    if (width < 768) expect(scene.y).toBeGreaterThan(title.y + title.height);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`landing-${width}x${height}.png`), fullPage: true });
    await page.getByRole('button', { name: '登录工作台' }).focus();
    await expect(page.getByRole('button', { name: '登录工作台' })).toBeFocused();
  }
});

test('opens login with a mouse when the illustration extends towards the header', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Explicit desktop viewport matrix.');
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: { code: 'SESSION_REQUIRED' } }));

  for (const [width, height] of [[1670, 910], [1704, 924], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    await page.goto('/');
    await page.locator('.landing-art').evaluate((image: HTMLImageElement) => image.decode());
    await page.getByRole('button', { name: '登录工作台' }).click({ timeout: 3000 });
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  }
});

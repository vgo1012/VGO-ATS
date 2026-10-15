import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

for (const width of [390, 1280, 1440, 1920]) {
  test(`错误浮层自动关闭、键盘关闭与重试 ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await reviewWorkspace(page);
    await page.clock.setSystemTime(new Date());
    await page.route('**/api/v1/candidates?*', route => route.fulfill({ status: 503, json: {
      title: '读取失败', status: 503, detail: '人才池暂时不可用，请稍后重试。', code: 'TEMPORARY_FAILURE', recoverable: true,
    } }));
    await page.goto('/talents');
    const notice = page.getByRole('alert').filter({ hasText: '人才池暂时不可用' });
    await expect(notice).toBeVisible();
    const header = await page.getByRole('banner').boundingBox();
    expect(header).not.toBeNull();
    await expect.poll(async () => (await notice.boundingBox())?.y ?? -1)
      .toBeGreaterThanOrEqual(header!.y + header!.height + 8);
    const box = await notice.boundingBox();
    expect(box).not.toBeNull();
    if (width <= 600) expect(box!.x + box!.width).toBeLessThanOrEqual(width - 56);
    else expect(Math.abs(box!.x + box!.width / 2 - width / 2)).toBeLessThan(12);
    expect(box!.y).toBeGreaterThanOrEqual(header!.y + header!.height + 8);
    expect(box!.y).toBeLessThanOrEqual(header!.y + header!.height + 32);
    const navigation = page.getByRole('navigation', { name: width <= 900 ? '移动端主导航' : '主导航', exact: true });
    await navigation.getByRole('button', { name: 'AI 评估', exact: true }).click({ trial: true });
    await expect(notice).toHaveCSS('background-color', 'rgb(180, 35, 60)');
    await page.screenshot({ path: testInfo.outputPath(`error-popup-${width}.png`) });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    expect((await new AxeBuilder({ page }).include('.ant-notification').analyze()).violations).toEqual([]);
    await expect(notice).toHaveCount(0, { timeout: 6500 });
    const retry = page.getByRole('button', { name: /^重\s*试$/ });
    await expect(retry).toBeVisible();
    await retry.click();
    await expect(notice).toBeVisible();
    const close = page.getByRole('button', { name: '关闭错误提示' }).last();
    await close.focus();
    await page.keyboard.press('Enter');
    await expect(notice).toHaveCount(0);
    await expect(retry).toBeVisible();
    expect(errors).toEqual([]);
  });
}

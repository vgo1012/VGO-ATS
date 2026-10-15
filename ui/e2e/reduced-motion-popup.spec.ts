import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('reduced motion keeps a dialog select popup aligned without expanding the document', async ({ page }, testInfo) => {
  const width = testInfo.project.name === 'mobile' ? 390 : 1440;
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await reviewWorkspace(page);
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));

  await page.goto('/settings/preferences');
  await page.getByRole('button', { name: '新建方案' }).click();
  const dialog = page.getByRole('dialog', { name: '新建个人筛选方案' });
  await expect(dialog).toBeVisible();

  const select = dialog.getByRole('combobox', { name: '默认排序' });
  await select.scrollIntoViewIfNeeded();
  await select.click();
  const option = page.locator('.ant-select-item-option-content').filter({ hasText: '专业适配分排序' });
  await expect(option).toBeVisible();

  const popup = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)');
  await expect(popup).toBeVisible();
  const box = await popup.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.y).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(width);
  expect(box!.y + box!.height).toBeLessThanOrEqual(900);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await expect(page.locator('.ant-modal')).not.toHaveClass(/appear|enter/);
  await expect(popup).not.toHaveClass(/appear|enter/);
  await page.screenshot({ path: testInfo.outputPath(`reduced-motion-popup-${width}.png`), fullPage: true });

  await option.click();
  await expect(select).toHaveAttribute('aria-expanded', 'false');
  await expect(dialog.getByText('专业适配分排序', { exact: true }).last()).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  expect(pageErrors).toEqual([]);
});

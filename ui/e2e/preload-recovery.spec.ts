import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('发布后旧标签页缺少懒加载资源时刷新到新版本并恢复操作', async ({ page }) => {
  await reviewWorkspace(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  let missingChunks = 0;
  await page.route(/\/_build\/IntakeValidationPage-[^/]+\.js$/, async route => {
    if (missingChunks === 0) {
      missingChunks++;
      await route.fulfill({ status: 404, contentType: 'text/plain', body: 'simulated old deployment chunk' });
      return;
    }
    await route.continue();
  });

  await page.goto('/talents');
  const shortcut = page.getByRole('button', { name: '收件校验', exact: true });
  await expect(shortcut).toBeEnabled();
  const reloaded = page.waitForEvent('load');
  await shortcut.click();
  await reloaded;

  await expect(page).toHaveURL(/\/talents\?intake=1$/);
  await expect(page.getByRole('dialog', { name: '收件校验', exact: true })).toBeVisible();
  expect(missingChunks).toBe(1);
  expect(errors).toEqual([]);
});

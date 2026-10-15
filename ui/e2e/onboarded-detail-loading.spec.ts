import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('已入职列表按需加载简历详情，慢下载可取消且关闭返回原记录', async ({ page }) => {
  await reviewWorkspace(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/applications?*', route => route.fulfill({ json: {
    items: [{ id: 'app-lazy', candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'resume-a', ownerUserId: 'hr-a', currentStage: 'ONBOARDED' }], hasMore: false,
  } }));
  let detailReads = 0;
  await page.route('**/api/v1/candidates/person-a', route => {
    detailReads++;
    return route.fulfill({ status: 403, json: { status: 403, title: '无权读取', code: 'FORBIDDEN', detail: '该候选人需重新授权', recoverable: false } });
  });
  let downloads = 0;
  let release!: () => void;
  const pendingDownload = new Promise<void>(resolve => { release = resolve; });
  await page.route(/\/_build\/CandidateDetails[^/]*\.js$/, async route => {
    downloads++;
    await pendingDownload;
    await route.continue();
  });
  await page.goto('/onboarded');
  const open = page.getByRole('button', { name: '查看简历', exact: true });
  await expect(open).toBeVisible();
  expect(downloads).toBe(0);
  expect(detailReads).toBe(0);
  await open.focus();
  await open.press('Enter');
  const loading = page.getByRole('status').filter({ hasText: '正在加载简历详情…' });
  await expect(loading).toBeVisible();
  await expect.poll(() => downloads).toBe(1);
  await page.getByRole('button', { name: '取消加载详情' }).click();
  release();
  await expect(loading).toHaveCount(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(detailReads).toBe(0);
  await open.focus();
  await open.press('Enter');
  const dialog = page.getByRole('dialog', { name: '候选人详情' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('暂无该候选人的查看权限', { exact: true })).toBeVisible();
  expect(downloads).toBe(1);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(open).toBeFocused();
  await expect(page).toHaveURL(/\/onboarded$/);
  expect(errors).toEqual([]);
});

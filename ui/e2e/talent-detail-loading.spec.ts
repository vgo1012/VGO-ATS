import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('共享列表按需加载招聘详情，慢下载可取消且关闭返回原记录', async ({ page }) => {
  await reviewWorkspace(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/talent-search?**', route => route.fulfill({json:{items:[{candidateId:'person-a',resumeVersionId:'resume-a',fullName:'林雨桐',skills:[],degrees:[],conditionMatches:[],engagementLock:null}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidate-pool-statuses?**', route => route.fulfill({json:[{
    candidateId:'person-a',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:false,stages:['IN_PROCESS'],
    jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'平台开发岗位',versionNo:2,resumeVersionId:'resume-a',status:'ADVANCED',stage:'IN_PROCESS',applicationId:'app-lazy',matchRunId:null}],
  }]}));
  let detailReads = 0;
  await page.route('**/api/v1/applications/app-lazy', route => {
    detailReads++;
    return route.fulfill({ status: 403, json: { status: 403, title: '无权读取', code: 'FORBIDDEN', detail: '该招聘记录需重新授权', recoverable: false } });
  });
  let downloads = 0;
  let release!: () => void;
  const pendingDownload = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/_build/ApplicationDetailsDialog-*.js', async route => {
    downloads++;
    await pendingDownload;
    await route.continue();
  });
  await page.goto('/shared-library');
  const open = page.locator('[data-application-id="app-lazy"]');
  await expect(open).toBeVisible();
  expect(downloads).toBe(0);
  expect(detailReads).toBe(0);
  await open.focus();
  await open.press('Enter');
  const loading = page.getByRole('status').filter({ hasText: '正在加载招聘详情…' });
  await expect(loading).toBeVisible();
  await expect.poll(() => downloads).toBe(1);
  await page.getByRole('button', { name: '取消加载详情' }).click();
  release();
  await expect(loading).toHaveCount(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(detailReads).toBe(0);
  await expect(open).toBeFocused();
  await open.focus();
  await open.press('Enter');
  const dialog = page.getByRole('dialog', { name: '招聘活动详情' });
  await expect(dialog).toBeVisible();
  await expect(page.getByText('该招聘记录需重新授权', { exact: true })).toBeVisible();
  expect(downloads).toBe(1);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(open).toBeFocused();
  await expect(page).toHaveURL(/\/shared-library$/);
  expect(errors).toEqual([]);
});

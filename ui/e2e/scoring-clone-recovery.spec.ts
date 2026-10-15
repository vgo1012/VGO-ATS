import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page, type Route, type TestInfo } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const pageResult = (items: unknown[]) => ({ items, hasMore: false, nextCursor: null });
const draft = (id: string, versionNo: number) => ({
  ...companyScoring,
  version: { ...companyScoring.version, id, versionNo, version: 0, status: 'DRAFT' },
});

async function prepare(page: Page, testInfo: TestInfo) {
  await page.setViewportSize({ width: testInfo.project.name === 'mobile' ? 390 : 1440, height: 900 });
  await reviewWorkspace(page);
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  return pageErrors;
}

async function rejectCloneResponse(route: Route) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: '{invalid-json' });
}

test('retries an uncertain company scoring clone with the same operation key and blocks duplicate actions', async ({ page }, testInfo) => {
  const pageErrors = await prepare(page, testInfo);
  const cloneRequests: Array<{ url: string; key: string | undefined }> = [];
  const created = new Map<string, ReturnType<typeof draft>>();
  let releaseReplay: (() => void) | undefined;

  await page.route('**/api/v1/admin/jd-scoring-configs**', async route => {
    const request = route.request();
    if (request.method() === 'POST' && request.url().endsWith('/company-v1/clone')) {
      cloneRequests.push({ url: request.url(), key: request.headers()['idempotency-key'] });
      const key = request.headers()['idempotency-key'] ?? '';
      if (!created.has(key)) created.set(key, draft(`company-v${created.size + 2}`, created.size + 2));
      if (cloneRequests.length === 1) return rejectCloneResponse(route);
      await new Promise<void>(resolve => { releaseReplay = resolve; });
      return route.fulfill({ json: created.get(key) });
    }
    return route.fulfill({ json: pageResult([...created.values(), companyScoring]) });
  });

  await page.goto('/admin/scoring');
  await page.getByRole('button', { name: '创建 V2 草稿' }).click();
  const retry = page.getByRole('button', { name: '重试', exact: true });
  await expect(retry).toBeVisible();
  await page.getByRole('button', { name: '关闭错误提示' }).click();
  await page.context().setOffline(true);
  await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
  await page.clock.setFixedTime(new Date('2026-09-07T01:31:00Z'));
  await page.context().setOffline(false);
  // 隔离容器没有外部网络接口；明确派发恢复事件，验证当前页的重连查询。
  await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
  await expect(page.getByRole('row').filter({ hasText: 'V2' })).toBeVisible();
  if (testInfo.project.name === 'mobile') {
    const header = page.getByRole('row', { name: '评分方案版本表头，可横向滚动' });
    await header.focus();
    await header.press('ArrowRight');
    await expect.poll(() => page.locator('.ant-table-content').evaluate(element => element.scrollLeft)).toBeGreaterThan(0);
  }
  expect((await new AxeBuilder({ page }).include('.scoring-governance-page').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('clone-unconfirmed.png'), animations: 'disabled', fullPage: true });

  await retry.click();
  await expect.poll(() => cloneRequests.length).toBe(2);
  await expect(retry).toBeDisabled();
  await expect(page.getByRole('button', { name: /^编\s*辑$/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /^激\s*活$/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: /复制|创建 V/ }).last()).toBeDisabled();
  await retry.evaluate((button: HTMLButtonElement) => button.click());
  expect(cloneRequests).toHaveLength(2);
  expect(cloneRequests[0]?.url).toBe(cloneRequests[1]?.url);
  expect(cloneRequests[0]?.key).toBeTruthy();
  expect(cloneRequests[1]?.key).toBe(cloneRequests[0]?.key);
  expect(created.size).toBe(1);

  releaseReplay?.();
  await expect(page.getByRole('dialog')).toHaveAccessibleName('编辑评分方案 · V2');
  await expect(page.getByRole('dialog', { name: '编辑评分方案 · V2' })).toHaveCount(1);
  await expect(page.getByRole('dialog')).not.toHaveClass(/appear|enter/);
  expect(cloneRequests).toHaveLength(2);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath(`scoring-clone-recovery-${testInfo.project.name}.png`), fullPage: true });
  expect(pageErrors).toEqual([]);
});

test('keeps the originally selected historical version as the clone source when retrying', async ({ page }, testInfo) => {
  const pageErrors = await prepare(page, testInfo);
  const historicalV1 = { ...companyScoring, version: { ...companyScoring.version, status: 'PUBLISHED' } };
  const activeV2 = { ...companyScoring, version: { ...companyScoring.version, id: 'company-v2', versionNo: 2, version: 2 } };
  const v3 = draft('company-v3', 3);
  const cloneUrls: string[] = [];

  await page.route('**/api/v1/scoring-defaults', route => route.fulfill({ json: { scoringConfiguration: activeV2 } }));
  await page.route('**/api/v1/admin/jd-scoring-configs**', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      cloneUrls.push(request.url());
      if (cloneUrls.length === 1) return rejectCloneResponse(route);
      return route.fulfill({ json: v3 });
    }
    return route.fulfill({ json: pageResult([activeV2, historicalV1]) });
  });

  await page.goto('/admin/scoring');
  await page.getByRole('row').filter({ hasText: 'V1' }).getByRole('button', { name: '复制为下一版' }).click();
  await page.getByRole('button', { name: '关闭错误提示' }).click();
  await page.getByRole('button', { name: '重试创建草稿' }).click();

  await expect(page.getByRole('dialog')).toHaveAccessibleName('编辑评分方案 · V3');
  await expect(page.getByRole('dialog')).not.toHaveClass(/appear|enter/);
  expect(cloneUrls).toHaveLength(2);
  expect(cloneUrls.every(url => url.endsWith('/company-v1/clone'))).toBe(true);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath(`scoring-clone-historical-${testInfo.project.name}.png`), fullPage: true });
  expect(pageErrors).toEqual([]);
});

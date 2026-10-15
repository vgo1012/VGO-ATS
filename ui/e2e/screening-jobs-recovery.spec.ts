import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, nextStatus: 200, requests: [] as Array<string | null>, wait: undefined as Promise<void> | undefined };
  const jobs = ['岗位甲', '岗位乙', '第二页岗位丙'].map((title, index) => ({ id: `jd-${index}`, title,
    status: 'ACTIVE', currentVersionNo: 1, clientId: null, clientName: null, ownerUserId: 'hr-a',
    ownerDepartmentId: 'dept-a', createTime: '2026-09-07T00:00:00Z' }));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path.startsWith('/me/jd-scoring-configs/') && path.endsWith('/preferences')) return route.fulfill({ json: null });
    if (path.startsWith('/jd-versions/') && path.endsWith('/facts')) return route.fulfill({ json: null });
    if (path.startsWith('/jd-versions/') && path.endsWith('/active-scoring-config')) return route.fulfill({ json: {
      jdVersionId: path.split('/')[2], scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    const versions = path.match(/^\/jds\/(jd-\d)\/versions$/);
    if (versions) return route.fulfill({ json: { items: [{ id: `${versions[1]}-v1`, jdId: versions[1], versionNo: 1,
      title: jobs.find(job => job.id === versions[1])!.title, rawText: '独立岗位原文', status: 'PUBLISHED', sourceType: 'MANUAL',
      createId: 'hr-a', createTime: '2026-09-07T00:00:00Z' }], hasMore: false, nextCursor: null } });
    if (path !== '/jds') return route.fallback();
    const cursor = url.searchParams.get('cursor');
    state.requests.push(cursor);
    await state.wait;
    const status = cursor ? state.nextStatus : state.status;
    if (status !== 200) return route.fulfill({ status, contentType: 'application/problem+json', json: {
      status, title: '岗位加载失败', detail: status === 403 ? '招聘岗位读取权限已撤销' : '招聘岗位服务暂不可用',
      code: status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: status === 503,
    } });
    return route.fulfill({ json: { items: cursor ? jobs.slice(2) : jobs.slice(0, 2), hasMore: !cursor, nextCursor: cursor ? null : 'jobs-next' } });
  });
  return state;
}

for (const scenario of ['403', '503', 'next503', 'next403'] as const) {
  test(`招聘岗位选择 ${scenario} 后按权限恢复并保留失败页游标`, async ({ page }, testInfo) => {
    const width = testInfo.project.name === 'mobile' ? 390 : 1440;
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching');
    await expect(page.getByRole('button', { name: '选择招聘岗位', exact: true })).toContainText('岗位甲');
    await page.getByRole('button', { name: '选择招聘岗位', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '选择招聘岗位', exact: true });
    const cards = dialog.locator('.screening-jd-option');
    await expect(cards).toHaveCount(2);
    if (scenario === 'next503' || scenario === 'next403') {
      state.nextStatus = scenario === 'next403' ? 403 : 503;
      await dialog.getByRole('button', { name: '加载更多岗位', exact: true }).click();
    } else {
      state.status = Number(scenario);
      await page.context().setOffline(true);
      await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
      await page.clock.setFixedTime(new Date('2026-09-07T01:31:00Z'));
      await page.context().setOffline(false);
      // 隔离容器没有外部网络接口；明确派发恢复事件，验证当前页的重连查询。
      await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
    }
    const denied = scenario === '403' || scenario === 'next403';
    await expect(dialog.getByText(denied ? '暂无招聘岗位的查看权限' : '岗位加载失败', { exact: true })).toBeVisible();
    if (denied) {
      await expect(cards).toHaveCount(0);
      await expect(dialog.getByRole('button', { name: '加载更多岗位', exact: true })).toHaveCount(0);
    } else {
      await expect(cards).toHaveCount(2);
      await expect(cards.nth(1)).toBeEnabled();
    }
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`screening-jobs-${scenario}-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    state.status = 200;
    state.nextStatus = 200;
    let release!: () => void;
    state.wait = new Promise<void>(resolve => { release = resolve; });
    const before = state.requests.length;
    const retry = dialog.getByRole('button', { name: '重试岗位列表', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.requests.length).toBeGreaterThan(before);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      release();
    }
    await expect(retry).toHaveCount(0);
    await expect(cards).toHaveCount(scenario === 'next503' ? 3 : 2);
    expect(state.requests.slice(before)).toEqual([scenario === 'next503' ? 'jobs-next' : null]);
    await cards.nth(1).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: '选择招聘岗位', exact: true })).toContainText('岗位乙');
    expect(errors).toEqual([]);
  });
}

test('招聘岗位首次加载失败后弹窗慢重试持续显示忙碌按钮', async ({ page }, testInfo) => {
  const width = testInfo.project.name === 'mobile' ? 390 : 1440;
  await page.setViewportSize({ width, height: 1000 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const state = await fixture(page);
  state.status = 503;
  await page.goto('/matching');
  await expect(page.getByText('岗位列表读取失败', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '关闭错误提示', exact: true }).click();
  await page.getByRole('button', { name: '选择招聘岗位', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '选择招聘岗位', exact: true });
  const cards = dialog.locator('.screening-jd-option');
  const retry = dialog.getByRole('button', { name: '重试岗位列表', exact: true });
  await expect(dialog.getByText('岗位加载失败', { exact: true })).toBeVisible();
  await expect(retry).toBeEnabled();
  await expect(cards).toHaveCount(0);
  state.status = 200;
  let release!: () => void;
  state.wait = new Promise<void>(resolve => { release = resolve; });
  const before = state.requests.length;
  await retry.focus();
  await page.keyboard.press('Enter');
  try {
    await expect.poll(() => state.requests.length).toBeGreaterThan(before);
    await expect(retry).toBeVisible();
    await expect(retry).toBeDisabled();
    await expect(retry).toHaveAttribute('aria-busy', 'true');
    await expect(cards).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath(`screening-jobs-initial-retry-pending-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  } finally {
    release();
  }
  await expect(retry).toHaveCount(0);
  await expect(cards).toHaveCount(2);
  expect(state.requests.slice(before)).toEqual([null]);
  await cards.nth(1).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('button', { name: '选择招聘岗位', exact: true })).toContainText('岗位乙');
});

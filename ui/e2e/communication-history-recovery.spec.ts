import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const plan = {
  id: 'plan-history', applicationId: 'history-app', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2',
  recruitmentType: 'INTERNAL', clientId: null, clientName: null, ownerUserId: 'hr-a',
  startsAt: '2026-09-07T01:00:00Z', endsAt: '2026-09-07T01:30:00Z', channel: 'PHONE',
  note: '沟通记录恢复测试预约', status: 'SCHEDULED', version: '1',
};
const historyEvent = {
  id: 'history-first', planId: plan.id, action: 'CREATED', reason: '已确认联系时间', actorUserId: 'hr-a',
  occurredAt: '2026-09-06T01:00:00Z', before: null, after: { ...plan, note: '首批预约历史内容' },
};
const actual = {
  id: 'actual-first', applicationId: plan.applicationId, channel: 'PHONE', result: 'FOLLOW_UP',
  summary: '实际沟通独有内容', nextPlan: '明天再次联系', recordedByUserId: 'hr-a', communicatedAt: '2026-09-07T01:00:00Z',
};

async function fixture(page: Page, options: { historyFailure?: boolean; actualFailure?: boolean; pagination?: boolean; slow?: 'history' | 'actual' } = {}) {
  await reviewWorkspace(page);
  let release = () => {};
  const pending = new Promise<void>(resolve => { release = resolve; });
  const state = {
    historyFailure: options.historyFailure ?? false, actualFailure: options.actualFailure ?? false,
    nextFailure: Boolean(options.pagination), historyCursors: [] as Array<string | null>, actualRequests: 0, release,
  };
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    const failure = (detail: string) => route.fulfill({ status: 503, contentType: 'application/problem+json', json: {
      status: 503, code: 'SERVICE_UNAVAILABLE', title: '沟通记录读取失败', detail, recoverable: true,
    } });
    if (path === '/interviews') return route.fulfill({ json: empty });
    if (path === '/follow-up-plans') return route.fulfill({ json: { ...empty, items: [plan] } });
    if (path === '/applications/history-app') return route.fulfill({ json: {
      application: { id: plan.applicationId, ownerUserId: 'hr-a', terminalAt: null }, collaboratorUserIds: [], workflowSteps: [],
    } });
    if (path === '/applications/history-app/communications') {
      state.actualRequests++;
      if (options.slow === 'actual') await pending;
      return state.actualFailure ? failure('实际沟通服务暂不可用') : route.fulfill({ json: [actual] });
    }
    if (path === '/applications/history-app/follow-up-plan-history') {
      const cursor = url.searchParams.get('cursor');
      state.historyCursors.push(cursor);
      if (options.slow === 'history') await pending;
      if (state.historyFailure || cursor && state.nextFailure) return failure('预约历史服务暂不可用');
      return route.fulfill({ json: cursor ? {
        ...empty, items: [{ ...historyEvent, id: 'history-second', after: { ...plan, note: '后页预约历史内容' } }],
      } : { items: [historyEvent], hasMore: Boolean(options.pagination), nextCursor: options.pagination ? 'history-page-2' : null } });
    }
    return route.fallback();
  });
  return state;
}

async function openHistory(page: Page) {
  await page.goto('/schedule');
  await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button').filter({ hasText: '林雨桐' }).filter({ hasText: '沟通预约' }).click();
  return page.getByRole('dialog', { name: '沟通预约详情', exact: true }).getByRole('region', { name: '沟通记录', exact: true });
}

test('预约历史失败仍显示实际沟通及部分提示，键盘重试恢复', async ({ page }, testInfo) => {
  const state = await fixture(page, { historyFailure: true });
  const history = await openHistory(page);
  await expect(history.getByText('实际沟通独有内容', { exact: true })).toBeVisible();
  await expect(history.getByText('部分沟通记录未能读取，已显示可用记录。', { exact: true })).toBeVisible();
  await expect(history.getByText('尚无沟通记录', { exact: true })).toHaveCount(0);
  for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.getByRole('dialog', { name: '沟通预约详情', exact: true }).screenshot({ path: testInfo.outputPath('communication-history-partial.png') });
  state.historyFailure = false;
  await history.getByRole('button', { name: '重试预约历史', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(history.getByText('首批预约历史内容', { exact: true })).toBeVisible();
  await expect(history.getByText('部分沟通记录未能读取，已显示可用记录。', { exact: true })).toHaveCount(0);
  await expect(history.getByText('实际沟通独有内容', { exact: true })).toBeVisible();
  expect(state.actualRequests).toBe(1);
});

test('双路失败不显示空历史，分别重试后渐进恢复', async ({ page }) => {
  const state = await fixture(page, { historyFailure: true, actualFailure: true });
  const history = await openHistory(page);
  await expect(history.getByRole('button', { name: '重试预约历史', exact: true })).toBeVisible();
  await expect(history.getByRole('button', { name: '重试实际沟通', exact: true })).toBeVisible();
  await expect(history.getByText('沟通记录暂不可用，请重试。', { exact: true })).toBeVisible();
  await expect(history.getByText('尚无沟通记录', { exact: true })).toHaveCount(0);
  state.actualFailure = false;
  await history.getByRole('button', { name: '重试实际沟通', exact: true }).click();
  await expect(history.getByText('实际沟通独有内容', { exact: true })).toBeVisible();
  await expect(history.getByText('部分沟通记录未能读取，已显示可用记录。', { exact: true })).toBeVisible();
  state.historyFailure = false;
  await history.getByRole('button', { name: '重试预约历史', exact: true }).click();
  await expect(history.getByText('首批预约历史内容', { exact: true })).toBeVisible();
  await expect(history.getByText('沟通记录暂不可用，请重试。', { exact: true })).toHaveCount(0);
  await expect(history.getByText('部分沟通记录未能读取，已显示可用记录。', { exact: true })).toHaveCount(0);
});

test('预约历史后页失败重试同一游标，保留已加载历史和实际沟通', async ({ page }) => {
  const state = await fixture(page, { pagination: true });
  const history = await openHistory(page);
  await expect(history.getByText('首批预约历史内容', { exact: true })).toBeVisible();
  await history.getByRole('button', { name: '加载更多沟通记录', exact: true }).click();
  const retry = history.getByRole('button', { name: '重试预约历史', exact: true });
  await expect(retry).toBeVisible();
  await expect(history.getByText('首批预约历史内容', { exact: true })).toBeVisible();
  await expect(history.getByText('实际沟通独有内容', { exact: true })).toBeVisible();
  await expect(history.getByText('部分沟通记录未能读取，已显示可用记录。', { exact: true })).toBeVisible();
  state.nextFailure = false;
  await retry.click();
  await expect(history.getByText('后页预约历史内容', { exact: true })).toBeVisible();
  expect(state.historyCursors.filter(cursor => cursor === null)).toHaveLength(1);
  expect(state.historyCursors.slice(1).every(cursor => cursor === 'history-page-2')).toBe(true);
  await expect(history.getByRole('button', { name: '加载更多沟通记录', exact: true })).toHaveCount(0);
});

for (const slow of ['history', 'actual'] as const) {
  test(`${slow === 'history' ? '预约历史' : '实际沟通'}缓慢时仍显示另一来源记录及加载状态`, async ({ page }) => {
    const state = await fixture(page, { slow });
    try {
      const history = await openHistory(page);
      await expect(history.getByText(slow === 'history' ? '实际沟通独有内容' : '首批预约历史内容', { exact: true })).toBeVisible();
      await expect(history.getByRole('status').filter({ hasText: '加载沟通记录' })).toBeVisible();
      await expect(history.getByText('尚无沟通记录', { exact: true })).toHaveCount(0);
      state.release();
      await expect(history.getByText(slow === 'history' ? '首批预约历史内容' : '实际沟通独有内容', { exact: true })).toBeVisible();
      await expect(history.getByRole('status').filter({ hasText: '加载沟通记录' })).toHaveCount(0);
    } finally {
      state.release();
    }
  });
}

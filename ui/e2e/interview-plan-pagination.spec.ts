import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { FollowUpPlan } from '../src/recruitment/followUpPlansApi';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const currentWeek = '2026-09-06T16:00:00.000Z';
const nextCursor = 'plans-page-2';
const basePlan: FollowUpPlan = {
  id: 'plan-1', applicationId: 'a', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2',
  recruitmentType: 'INTERNAL', clientId: null, clientName: null, ownerUserId: 'hr-a',
  startsAt: '2026-09-07T01:00:00Z', endsAt: '2026-09-07T01:30:00Z', channel: 'PHONE',
  note: '首批预约 1', status: 'SCHEDULED', version: '1',
  createTime: '2026-09-06T00:00:00Z', updateTime: '2026-09-06T00:00:00Z',
};

const laterPlanButton = (calendar: ReturnType<Page['getByRole']>) => calendar
  .getByRole('region', { name: /9\/8 上午日程/ })
  .getByRole('button', { name: /沟通预约/ });

async function fixture(page: Page, failNextPage = false) {
  await reviewWorkspace(page);
  const state = { failNextPage, requests: [] as URL[] };
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/interviews') return route.fulfill({ json: empty });
    if (path === '/applications/a') return route.fulfill({ json: {
      application: { id: 'a', ownerUserId: 'hr-a', terminalAt: null }, collaboratorUserIds: [],
    } });
    if (path === '/applications/a/follow-up-plan-history') return route.fulfill({ json: empty });
    if (path !== '/follow-up-plans') return route.fallback();
    state.requests.push(url);
    if (url.searchParams.get('from') !== currentWeek) return route.fulfill({ json: {
      ...empty, items: [{ ...basePlan, id: 'previous-week', note: '上一周预约', startsAt: '2026-08-31T01:00:00Z', endsAt: '2026-08-31T01:30:00Z' }],
    } });
    if (!url.searchParams.has('cursor')) return route.fulfill({ json: {
      items: Array.from({ length: 20 }, (_, index) => ({ ...basePlan, id: `plan-${index + 1}`, note: `首批预约 ${index + 1}` })),
      hasMore: true, nextCursor,
    } });
    if (state.failNextPage) return route.fulfill({ status: 503, contentType: 'application/problem+json', json: {
      status: 503, title: '预约分页暂不可用', detail: '预约分页暂不可用，请重试', code: 'SERVICE_UNAVAILABLE', recoverable: true,
    } });
    return route.fulfill({ json: { ...empty, items: [{ ...basePlan, id: 'plan-51', note: '后页独有预约', startsAt: '2026-09-08T01:00:00Z', endsAt: '2026-09-08T01:30:00Z' }] } });
  });
  return state;
}

test('周历通过游标加载第 21 条沟通预约，并可键盘打开详情', async ({ page }, testInfo) => {
  const state = await fixture(page);
  await page.goto('/schedule');
  const calendar = page.getByRole('region', { name: '所选周面试日历' });
  await expect(calendar.getByRole('button', { name: /沟通预约/ })).toHaveCount(20);
  await expect(page.getByText('已显示 20 条沟通预约，该周还有更多预约', { exact: true })).toBeVisible();
  await expect(laterPlanButton(calendar)).toHaveCount(0);
  const load = page.getByRole('button', { name: '加载更多沟通预约', exact: true });
  const originalViewport = page.viewportSize()!;
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await load.scrollIntoViewIfNeeded();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await load.click({ trial: true, timeout: 5000 });
    await page.screenshot({ path: testInfo.outputPath(`plan-pagination-${width}.png`), animations: 'disabled' });
  }
  await page.setViewportSize(originalViewport);
  expect((await new AxeBuilder({ page }).include('.schedule-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await load.focus();
  await page.keyboard.press('Enter');
  const later = laterPlanButton(calendar);
  await expect(later).toBeVisible();
  await expect(calendar.getByRole('button', { name: /沟通预约/ })).toHaveCount(21);
  await expect(load).toHaveCount(0);
  expect(state.requests.map(url => url.searchParams.get('cursor'))).toEqual([null, nextCursor]);
  expect(state.requests.every(url => url.searchParams.get('limit') === '20' && url.searchParams.get('status') === 'SCHEDULED')).toBe(true);
  await later.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog', { name: '沟通预约详情', exact: true })).toContainText('后页独有预约');
});

test('沟通预约下一页失败保留首批内容，重试只请求失败游标', async ({ page }) => {
  const state = await fixture(page, true);
  await page.goto('/schedule');
  const calendar = page.getByRole('region', { name: '所选周面试日历' });
  await page.getByRole('button', { name: '加载更多沟通预约', exact: true }).click();
  const retry = page.getByRole('button', { name: '重试加载沟通预约', exact: true });
  await expect(retry).toBeEnabled();
  await expect(calendar.getByRole('button', { name: /沟通预约/ })).toHaveCount(20);
  expect(state.requests.filter(url => !url.searchParams.has('cursor'))).toHaveLength(1);
  state.failNextPage = false;
  await retry.focus();
  await page.keyboard.press('Enter');
  await expect(laterPlanButton(calendar)).toBeVisible();
  await expect(calendar.getByRole('button', { name: /沟通预约/ })).toHaveCount(21);
  await expect(retry).toHaveCount(0);
  expect(state.requests.slice(1).every(url => url.searchParams.get('cursor') === nextCursor)).toBe(true);
  expect(state.requests.filter(url => !url.searchParams.has('cursor'))).toHaveLength(1);
});

test('加载后页后切换上一周，从无游标的新范围开始且清除旧预约', async ({ page }) => {
  const state = await fixture(page);
  await page.goto('/schedule');
  const calendar = page.getByRole('region', { name: '所选周面试日历' });
  await page.getByRole('button', { name: '加载更多沟通预约', exact: true }).click();
  await expect(laterPlanButton(calendar)).toBeVisible();
  await page.getByRole('button', { name: '上一周', exact: true }).click();
  await expect(calendar.getByRole('region', { name: /8\/31 上午日程/ }).getByRole('button', { name: /沟通预约/ })).toBeVisible();
  await expect(calendar.getByRole('button', { name: /沟通预约/ })).toHaveCount(1);
  await expect(calendar.getByRole('region', { name: /9\/8 上午日程/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '加载更多沟通预约', exact: true })).toHaveCount(0);
  const previous = state.requests.filter(url => url.searchParams.get('from') !== currentWeek);
  expect(previous).toHaveLength(1);
  expect(previous[0]!.searchParams.get('from')).toBe('2026-08-30T16:00:00.000Z');
  expect(previous[0]!.searchParams.has('cursor')).toBe(false);
});

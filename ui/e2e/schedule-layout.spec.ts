import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('北京时间12点归下午，跨午只显示一次，跨日延续归当天上午', async ({ page }) => {
  await reviewWorkspace(page);
  const base = { status: 'SCHEDULED', interviewType: 'INTERNAL', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, meetingMode: 'ONLINE', jdTitle: '软件工程师' };
  await page.route('**/api/v1/interviews?*', route => route.fulfill({ json: { items: [
    { ...base, id: 'before-noon', candidateName: 'before-noon', scheduledStartAt: '2026-09-07T03:59:00Z', scheduledEndAt: '2026-09-07T05:00:00Z' },
    { ...base, id: 'at-noon', candidateName: 'at-noon', scheduledStartAt: '2026-09-07T04:00:00Z', scheduledEndAt: '2026-09-07T04:30:00Z' },
    { ...base, id: 'overnight', candidateName: 'overnight', scheduledStartAt: '2026-09-06T15:00:00Z', scheduledEndAt: '2026-09-06T17:00:00Z' },
  ], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/follow-up-plans?*', route => route.fulfill({ json: { items: [{ id: 'noon-plan', candidateId: 'person-a', status: 'SCHEDULED', startsAt: '2026-09-07T04:00:00Z', endsAt: '2026-09-07T04:30:00Z', channel: 'PHONE', note: '中午沟通' }], hasMore: false, nextCursor: null } }));
  const firstRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/interviews');
  await page.goto('/schedule');
  expect(new URL((await firstRequest).url()).searchParams.get('status')).toBe('SCHEDULED');
  await expect(page.locator('.interview-filters')).toContainText('进行中');
  await page.getByRole('combobox', { name: '状态', exact: true }).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'已完成'}).click();
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect(page.locator('.interview-filters')).toContainText('进行中');
  const morning = page.getByRole('region', { name: '9/7 上午日程，可上下滚动', exact: true });
  const afternoon = page.getByRole('region', { name: '9/7 下午日程，可上下滚动', exact: true });
  await expect(morning.getByRole('button', { name: /before-noon/ })).toHaveCount(1);
  await expect(morning.getByRole('button', { name: /overnight/ })).toContainText('前日续');
  await expect(afternoon.getByRole('button', { name: /at-noon/ })).toHaveCount(1);
  await expect(afternoon.getByRole('button', { name: /沟通预约/ })).toHaveCount(1);
  await expect(page.locator('.interview-event')).toHaveCount(4);
});

test('日程周末使用红色底色，班休文字保留工作日历事实', async ({ page }) => {
  await reviewWorkspace(page);
  await page.clock.setFixedTime(new Date('2026-09-20T03:00:00Z'));
  await page.route('**/api/v1/work-calendars/2026/published', route => route.fulfill({ json: { year: 2026, revisionId: 'r1', days: Array.from({ length: 7 }, (_, i) => ({ date: `2026-09-${14 + i}`, dayType: i === 0 ? 'HOLIDAY' : i === 5 ? 'RESTDAY' : 'WORKDAY', description: null })) } }));
  await page.goto('/schedule');
  const days = page.locator('.interview-week-grid > section');
  await expect(days.nth(6).locator('header')).toContainText('工作日今天');
  await expect(days.nth(6)).toHaveClass(/weekend/);
  await expect(days.nth(0)).not.toHaveClass(/weekend/);
  await expect(days.nth(0).locator('header')).toContainText('节假日');
  await expect(days.nth(5).locator('header')).toContainText('休息日');
  const weekendColor = await days.nth(5).locator('header > b').evaluate(el => getComputedStyle(el).color);
  const red = await page.locator('.schedule-calendar-legend .overdue-legend').evaluate(el => getComputedStyle(el).color);
  expect(weekendColor).toBe(red);
  expect((await new AxeBuilder({ page }).include('.interview-workspace-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: test.info().outputPath('schedule-work-calendar.png'), fullPage: true });
});

test('跨年周读取两年工作日历，未发布与失败不误标休息并支持重试', async ({ page }) => {
  await reviewWorkspace(page);
  await page.clock.setFixedTime(new Date('2026-12-31T03:00:00Z'));
  let failed = true;
  await page.route('**/api/v1/work-calendars/2026/published', route => route.fulfill({ json: { year: 2026, revisionId: null, days: [] } }));
  await page.route('**/api/v1/work-calendars/2027/published', route => failed ? route.fulfill({ status: 503, json: { title: '暂不可用', status: 503 } }) : route.fulfill({ json: { year: 2027, revisionId: 'r2', days: [{ date: '2027-01-01', dayType: 'HOLIDAY', description: '元旦' }, { date: '2027-01-03', dayType: 'WORKDAY', description: '补班' }] } }));
  await page.goto('/schedule');
  await expect(page.getByText('2026 年工作日历尚未发布，班休暂未确认')).toBeVisible();
  await expect(page.getByRole('button', { name: '重试工作日历' })).toBeVisible({ timeout: 15000 });
  await expect(page.locator('.interview-week-grid > section.weekend')).toHaveCount(2);
  failed = false;
  await page.getByRole('button', { name: '重试工作日历' }).click();
  const days = page.locator('.interview-week-grid > section');
  await expect(days.nth(4).locator('header')).toContainText('节假日');
  await expect(days.nth(4)).not.toHaveClass(/weekend/);
  await expect(days.nth(6).locator('header')).toContainText('工作日');
  await expect(days.nth(5).locator('header')).toContainText('未确认');
  await page.getByRole('button', { name: '下一周' }).click();
  await expect(days.nth(0).locator('header')).toContainText('1/4');
  await expect(days.nth(0).locator('header')).toContainText('未确认');
});

test('面试与沟通统一标记超时，终态不误报并随时间自动更新', async ({ page }) => {
  await reviewWorkspace(page);
  await page.clock.install({ time: new Date('2026-09-07T01:30:00Z') });
  await page.clock.setFixedTime(new Date('2026-09-07T01:30:00Z'));
  const base = { scheduledStartAt: '2026-09-07T00:00:00Z', scheduledEndAt: '2026-09-07T01:00:00Z', status: 'SCHEDULED', interviewType: 'INTERNAL', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, meetingMode: 'ONLINE' };
  await page.route('**/api/v1/interviews?*', route => route.fulfill({ json: { items: [
    { ...base, id: 'late-internal' },
    { ...base, id: 'late-client', interviewType: 'EXTERNAL_CLIENT' },
    { ...base, id: 'complete', status: 'COMPLETED' },
    { ...base, id: 'cancel', status: 'CANCELLED' },
    { ...base, id: 'boundary', scheduledEndAt: '2026-09-07T01:30:00Z' },
    { ...base, id: 'future', scheduledEndAt: '2026-09-07T02:30:00Z' },
  ], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/interviews/*/materials', route => route.fulfill({ json: { candidateName: '超时测试候选人', jdTitle: '软件工程师' } }));
  await page.route('**/api/v1/follow-up-plans?*', route => route.fulfill({ json: { items: [{
    id: 'late-plan', candidateId: 'person-a', applicationId: 'application-a', status: 'SCHEDULED',
    startsAt: '2026-09-07T00:00:00Z', endsAt: '2026-09-07T01:00:00Z', channel: 'PHONE', note: '确认到岗意向',
  }], hasMore: false, nextCursor: null } }));
  await page.goto('/schedule');
  await expect(page.locator('.interview-event')).toHaveCount(7);
  await expect(page.locator('.interview-week-grid section.today header em').filter({ hasText: '今天' })).toHaveCSS('color', 'rgb(37, 99, 235)');
  await expect(page.locator('.interview-week-grid section.today > header')).toHaveCSS('background-color', 'rgb(239, 246, 255)');
  await expect(page.locator('.schedule-status-badge')).toHaveCount(7);
  await expect(page.locator('.interview-event.completed .schedule-status-badge')).toHaveText('已完成');
  await expect(page.locator('.interview-event.cancelled .schedule-status-badge')).toHaveText('已取消');
  await expect(page.locator('.schedule-status-badge').filter({ hasText: '待进行' })).toHaveCount(2);
  await expect(page.locator('.schedule-overdue-badge')).toHaveCount(3);
  await expect(page.locator('.interview-event.communication')).toContainText('已超时');
  await expect(page.locator('.interview-event.external')).toContainText('已超时');
  await expect(page.locator('.interview-event.completed, .interview-event.cancelled').getByText('已超时')).toHaveCount(0);
  await page.clock.setFixedTime(new Date('2026-09-07T01:30:31Z'));
  await page.clock.fastForward(31_000);
  await expect(page.locator('.schedule-overdue-badge')).toHaveCount(4);
  expect((await page.locator('.interview-event').first().boundingBox())!.height).toBeLessThanOrEqual(80);
  expect((await new AxeBuilder({ page }).include('.interview-workspace-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: test.info().outputPath('schedule-overdue.png'), fullPage: true });
});

test('上午三张下午四张独立滚动，桌面七天对齐且移动逐日堆叠', async ({ page }) => {
  await reviewWorkspace(page);
  let count = 2;
  await page.route('**/api/v1/interviews?*', route => route.fulfill({ json: {
    items: Array.from({ length: count }, (_, index) => ({
      id: `dense-${index}`, scheduledStartAt: index<15?'2026-09-07T03:00:00Z':'2026-09-07T05:00:00Z', scheduledEndAt: index<15?'2026-09-07T03:30:00Z':'2026-09-07T05:30:00Z',
      status: index % 3 === 0 ? 'COMPLETED' : index % 3 === 1 ? 'SCHEDULED' : 'CANCELLED',
      interviewType: index % 2 ? 'EXTERNAL_CLIENT' : 'INTERNAL', stepCode: 'INTERVIEW_ROUND_1', roundNo: index + 1, meetingMode: 'ONLINE',
    })), hasMore: false, nextCursor: null,
  } }));
  await page.route('**/api/v1/interviews/dense-*/materials', route => route.fulfill({ json: { candidateName: '日程测试候选人', jdTitle: '高级软件工程师' } }));
  await page.goto('/schedule');
  await expect(page.locator('.interview-event')).toHaveCount(2);
  const calendar = page.getByRole('region', { name: '9/7 上午日程，可上下滚动', exact: true });
  const afternoon = page.getByRole('region', { name: '9/7 下午日程，可上下滚动', exact: true });
  const before = await calendar.boundingBox();
  const afternoonTop = (await afternoon.boundingBox())!.y;
  const pageHeight = await page.evaluate(() => document.documentElement.scrollHeight);
  count = 30;
  await page.reload();
  await expect(page.locator('.interview-event')).toHaveCount(30);
  expect(Math.abs((await calendar.boundingBox())!.height - before!.height)).toBeLessThan(2);
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBe(pageHeight);
  const cards = calendar.locator('.interview-event');
  expect((await afternoon.boundingBox())!.y).toBe(afternoonTop);
  await expect(afternoon.locator('.interview-event')).toHaveCount(15);
  expect((await afternoon.locator('.interview-event').nth(3).boundingBox())!.y + 80).toBeLessThanOrEqual((await afternoon.boundingBox())!.y + (await afternoon.boundingBox())!.height);
  const dividers = await page.locator('.schedule-period.afternoon').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().y));
  expect(new Set(dividers).size).toBe((page.viewportSize()?.width??0)<768?7:1);
  expect((await cards.first().boundingBox())!.height).toBeLessThanOrEqual(80);
  expect((await cards.nth(2).boundingBox())!.y + 80).toBeLessThanOrEqual((await calendar.boundingBox())!.y + (await calendar.boundingBox())!.height);
  const header = page.locator('.interview-week-grid > section > header').first();
  await calendar.focus();
  const headerTop = (await header.boundingBox())!.y - (await calendar.boundingBox())!.y;
  await page.keyboard.press('End');
  await expect.poll(() => calendar.evaluate(el => el.scrollTop)).toBeGreaterThan(100);
  await expect.poll(() => calendar.evaluate(el => el.scrollHeight - el.clientHeight - el.scrollTop)).toBeLessThanOrEqual(1);
  await expect.poll(async () => Math.abs((await header.boundingBox())!.y - (await calendar.boundingBox())!.y - headerTop)).toBeLessThan(2);
  await expect.poll(() => calendar.evaluate(el => el.querySelector('.interview-event:last-child')!.getBoundingClientRect().bottom - el.getBoundingClientRect().bottom)).toBeLessThanOrEqual(0);
  expect(await afternoon.evaluate(el => el.scrollTop)).toBe(0);
  await cards.last().focus();
  expect(await cards.last().evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe('none');
  const colors = await page.locator('.interview-event').evaluateAll(elements => new Set(elements.map(el => getComputedStyle(el).borderLeftColor)).size);
  expect(colors).toBe(4);
  await calendar.evaluate(el => { el.scrollTop = 0; });
  await page.evaluate(() => window.scrollTo(0, 0));
  expect((await new AxeBuilder({ page }).include('.interview-workspace-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: test.info().outputPath('schedule-dense.png'), fullPage: true });
});

test('日程参考布局、周切换、筛选和响应式', async ({ page }) => {
  await reviewWorkspace(page);
  await page.clock.setFixedTime(new Date('2026-09-10T03:00:00Z'));
  const queries: URL[] = [];
  await page.route('**/api/v1/interviews?*', route => {
    const url = new URL(route.request().url());
    queries.push(url);
    return route.fulfill({ json: { items: [{ id: 'interview-a', candidateName: '魏馨绎', jdTitle: 'IT运维工程师', scheduledStartAt: '2026-09-10T03:10:00Z', scheduledEndAt: '2026-09-10T03:40:00Z', status: 'SCHEDULED', interviewType: 'EXTERNAL_CLIENT', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, meetingMode: 'ONLINE', clientName: '阿里巴巴' }], hasMore: false, nextCursor: null } });
  });
  await page.route('**/api/v1/applications/stage-counts?*', route => route.fulfill({ json: [{ stage: 'PATH_SELECTION', count: 5 }, { stage: 'IN_PROCESS', count: 2 }, { stage: 'ONBOARDED', count: 1 }, { stage: 'TERMINATED', count: 1 }] }));
  await page.goto('/schedule');
  await expect(page.getByRole('heading', { name: '任务栏', exact: true })).toBeVisible();
  await expect(page.locator('.interview-event')).toContainText('IT运维工程师');
  const compactCard = page.locator('.interview-event');
  await expect(compactCard).toHaveText('魏馨绎待进行IT运维工程师第 1 轮·11:10–11:40');
  const name = compactCard.locator('.schedule-event-name');
  const badge = compactCard.locator('.schedule-status-badge');
  expect(await name.evaluate(el => getComputedStyle(el).fontSize)).toBe('14px');
  expect(await name.evaluate(el => getComputedStyle(el).fontWeight)).toBe('700');
  expect((await badge.boundingBox())!.x).toBeGreaterThan((await name.boundingBox())!.x);
  expect((await badge.boundingBox())!.y - (await compactCard.boundingBox())!.y).toBeLessThan(16);
  await expect(page.locator('.schedule-filter-field > span')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重置', exact: true })).toHaveClass(/ant-btn-primary/);
  await expect(page.locator('.interview-calendar > .ant-card-head')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '上一周', exact: true })).toHaveCount(1);
  await expect(page.locator('.talent-tab-bar')).toHaveCount(0);
  await expect(page.locator('.schedule-heading')).toHaveCount(0);
  await expect(page.locator('.schedule-task-grid > div').first()).toHaveText('全部1');
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 940 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    const timing = compactCard.locator('.schedule-event-timing');
    const cardBox = (await compactCard.boundingBox())!;
    const timingBox = (await timing.boundingBox())!;
    expect(cardBox.height).toBeGreaterThanOrEqual(68);
    expect(cardBox.y + cardBox.height - timingBox.y - timingBox.height).toBeLessThanOrEqual(8);
    const week = page.locator('.schedule-calendar-scroll');
    expect(await week.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
    await week.evaluate(el => { el.scrollLeft = 200; });
    expect(await week.evaluate(el => el.scrollLeft)).toBe(0);
    if (width >= 1280) {
      const filter = (await page.locator('.interview-filters').boundingBox())!;
      const fields = await page.locator('.schedule-filter-field').evaluateAll(elements => elements.map(el => el.getBoundingClientRect().toJSON()));
      const reset = (await page.getByRole('button', { name: '重置', exact: true }).boundingBox())!;
      expect(filter.height).toBeLessThanOrEqual(64);
      expect(new Set(fields.map(field => field.y)).size).toBe(1);
      expect(Math.abs(fields[0]!.y + fields[0]!.height / 2 - reset.y - reset.height / 2)).toBeLessThanOrEqual(1);
    }
    if (width > 1000) {
      const columns = await page.locator('.interview-week-grid > section').evaluateAll(elements => elements.map(el => el.getBoundingClientRect()));
      expect(new Set(columns.map(box => box.y)).size).toBe(1);
      expect(Math.max(...columns.map(box => box.width)) - Math.min(...columns.map(box => box.width))).toBeLessThan(1);
    }
    if (width >= 1280) {
      const main = await page.locator('.schedule-main').boundingBox();
      const rail = await page.locator('.schedule-rail').boundingBox();
      expect(Math.abs(main!.y - rail!.y)).toBeLessThan(2);
      expect(rail!.x).toBeGreaterThan(main!.x + main!.width);
    }
    await page.screenshot({ path: test.info().outputPath(`schedule-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.interview-workspace-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: '上一周', exact: true }).click();
  await expect.poll(() => queries.at(-1)?.searchParams.get('from')).toBe('2026-08-30T16:00:00.000Z');
  await page.getByRole('button', { name: /本\s*周/ }).first().click();
  await expect(page.locator('.interview-event')).toContainText('IT运维工程师');
  await page.getByRole('combobox', { name: '面试类型', exact: true }).click();
  await page.getByText('外部面试', { exact: true }).click();
  await expect.poll(() => queries.at(-1)?.searchParams.get('interviewType')).toBe('EXTERNAL');
  await page.getByRole('button', { name: '自定义', exact: true }).click();
  await expect(page.getByText('面试台账', { exact: true })).toBeVisible();
});

test('招聘专员可见待反馈区域，紧凑周历与侧栏底部对齐', async ({ page }) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/me', route => route.fulfill({ json: { userId: 'hr-a', displayName: '测试专员', roles: ['RECRUITER'], superAdmin: false, permissions: ['interview.feedback.read-authorized', 'interview.schedule', 'recruitment.manage'] } }));
  let taskRequests = 0;
  page.on('request', request => { if (request.url().includes('/my/interview-tasks')) taskRequests++; });
  await page.goto('/schedule');
  await expect(page.getByRole('region', { name: '待反馈', exact: true })).toContainText('暂无待跟进面评');
  await expect(page.getByRole('region', { name: '待反馈', exact: true })).toContainText('0 项');
  await expect(page.getByRole('button', { name: '预约沟通', exact: true })).toHaveCount(0);
  await expect(page.getByText('更多筛选', { exact: true })).toHaveCount(0);
  await expect(page.locator('.schedule-main .ant-segmented')).toHaveCount(0);
  await expect(page.getByText(/所选日期内创建的流程/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: /重\s*置/ })).toBeVisible();
  for (const width of [390, 1440, 1920]) {
    await page.setViewportSize({ width, height: 940 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: test.info().outputPath(`schedule-recruiter-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.interview-workspace-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  for (const height of [940, 1200]) {
    await page.setViewportSize({ width: 1440, height });
    const rail = await page.locator('.schedule-rail').boundingBox();
    for (const panel of await page.locator('.schedule-rail > section').all()) {
      expect(Math.round((await panel.boundingBox())!.width)).toBe(Math.round(rail!.width));
    }
    // 上午三张、下午四张，容量不随视口或其他日期的日程数量变化。
    expect((await page.locator('.schedule-period.morning > .schedule-period-scroll').first().boundingBox())!.height).toBe(264);
    expect((await page.locator('.schedule-period.afternoon > .schedule-period-scroll').first().boundingBox())!.height).toBe(350);
    const calendarBox = (await page.locator('.interview-calendar').boundingBox())!;
    const feedbackBox = (await page.locator('.schedule-rail > section:last-child').boundingBox())!;
    expect(Math.abs(calendarBox.y + calendarBox.height - feedbackBox.y - feedbackBox.height)).toBeLessThan(2);
    await page.screenshot({ path: test.info().outputPath(`schedule-bottom-${height}.png`), fullPage: true, animations: 'disabled' });
  }
  expect(taskRequests).toBeGreaterThan(0);
});

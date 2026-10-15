import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { CalendarRevision, CalendarYear } from '../src/work-calendar/api';

async function fixture(page: Page, admin = true, published = false) {
  const days = Array.from({ length: 365 }, (_, index) => {
    const date = new Date(Date.UTC(2026, 0, index + 1));
    return { date: date.toISOString().slice(0, 10), dayType: date.getUTCDay() === 0 || date.getUTCDay() === 6 ? 'RESTDAY' as const : 'WORKDAY' as const, description: null };
  });
  const base: CalendarRevision = { id: 'calendar-1', year: 2026, revision: 1, status: 'PUBLISHED', version: '1', sourceNote: null, publishedBy: null, publishedName: null, publishReason: null, publishedAt: null, recalculationId: null, days };
  const year: CalendarYear = { year: 2026, published: published ? structuredClone(base) : null, draft: null, nextYearMissing: true };
  let conflict: 'unchanged' | 'changed' | null = null;
  let saves = 0;
  const legacyCalls: string[] = [];
  let calendarReads = 0;
  let runReads = 0;
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/v1/me') return route.fulfill({ json: { userId: 'admin-calendar', displayName: '日历管理员', workEmail: 'calendar@example.test', homeDepartmentId: 'hr', roles: [admin ? 'SUPER_ADMIN' : 'RECRUITER'], permissions: ['talent.read'], superAdmin: admin, mustChangePassword: false, version: '1' } });
    if (path === '/api/v1/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'fixture' } });
    if(path.includes('/api/v1/admin/resume-lifecycle/'))runReads++;
    if (path === '/api/v1/admin/work-calendars/2026' && request.method() === 'GET') { calendarReads++; return route.fulfill({ json: year }); }
    if (path.endsWith('/2026/history')) return route.fulfill({ json: year.published ? [year.published] : [] });
    if (path.includes('/drafts') || path.endsWith('/preview') || path.endsWith('/publish')) legacyCalls.push(path);
    if (path === '/api/v1/admin/work-calendars/2026' && request.method() === 'PUT') {
      expect(request.headers()['idempotency-key']).toBeTruthy();
      const body = request.postDataJSON() as { publishedRevisionId: string | null; expectedVersion: string | null; days: CalendarRevision['days']; sourceNote: string; reason: string };
      expect(body.publishedRevisionId).toBe(year.published?.id ?? null);
      expect(body.expectedVersion).toBe(year.published?.version ?? null);
      expect(body.days).toHaveLength(365);
      expect(new Set(body.days.map(day => day.date)).size).toBe(365);
      expect(body.sourceNote.trim()).not.toBe(''); expect(body.reason.trim()).not.toBe('');
      saves++;
      if (conflict) {
        if (conflict === 'changed') year.published = { ...base, id: 'calendar-2', revision: 2, version: '2', days: base.days.map(day => day.date === '2026-01-05' ? { ...day, dayType: 'HOLIDAY', description: '其他管理员修改' } : day) };
        conflict = null;
        return route.fulfill({ status: 409, json: { title: '日历版本冲突', status: 409, code: 'CONFLICT', detail: '日历已被其他管理员更新', recoverable: true } });
      }
      const revision = (year.published?.revision ?? 0) + 1;
      year.published = { ...base, id: `calendar-${revision}`, revision, version: String(revision), days: body.days, sourceNote: body.sourceNote, publishReason: body.reason, publishedBy: 'admin-calendar', publishedName: '日历管理员', publishedAt: '2026-09-14T08:00:00Z', recalculationId: `recalc-${revision}` };
      return route.fulfill({ json: year.published });
    }
    return route.fulfill({ status: 404, json: { title: '未定义测试接口', status: 404, detail: path, code: 'FIXTURE_NOT_FOUND', recoverable: false } });
  });
  return { year, legacyCalls, saves: () => saves, conflict: (value: 'unchanged' | 'changed') => { conflict = value; }, calendarReads: () => calendarReads, runReads: () => runReads };
}

async function fillSaveDetails(page: Page, reason = '年度保存测试') {
  await page.getByLabel('节假日与补班信息来源').fill('合成日历，仅用于测试');
  await page.getByLabel('保存或更正原因').fill(reason);
}

test('administrator saves and refreshes a new calendar without automatic lifecycle APIs', async ({ page }) => {
  const state = await fixture(page);
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/admin/work-calendars?year=2026');
  await expect(page.getByText('2027 年日历尚未配置')).toBeVisible();
  await expect(page.locator('.annual-calendar-day')).toHaveCount(365);
  await expect(page.getByRole('button', { name: /生成全年草稿|预览发布影响|确认发布/ })).toHaveCount(0);
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存并生效' })).toBeEnabled();
  await page.getByLabel('起始日期').fill('2026-01-01'); await page.getByLabel('结束日期').fill('2026-01-03');
  await page.getByRole('textbox', { name: '节日或调休说明' }).fill('合成测试假期');
  await page.getByRole('button', { name: '标记连续日期' }).click();
  await fillSaveDetails(page);
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('已发布 · 第 1 版')).toBeVisible();
  await expect(page.getByText('凌晨流转任务', {exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'重试此运行'})).toHaveCount(0);
  expect(state.runReads()).toBe(0);
  expect(state.saves()).toBe(1);
  await page.reload();
  await expect(page.getByRole('button', { name: '2026-01-01 节假日，合成测试假期', exact: true })).toBeVisible();
  await expect(page.locator('.annual-calendar-day.holiday')).toHaveCount(3);
  expect((await new AxeBuilder({ page }).include('.annual-calendar').analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: '维护历史', exact: true }).click();
  await expect(page.getByRole('cell', { name: '年度保存测试', exact: true })).toBeVisible();
  expect(state.legacyCalls).toEqual([]); expect(state.runReads()).toBe(0); expect(errors).toEqual([]);
});

test('published calendars remain editable and save a new published revision', async ({ page }) => {
  const state = await fixture(page, true, true);
  await page.goto('/admin/work-calendars?year=2026');
  await expect(page.getByText('已发布 · 第 1 版')).toBeVisible();
  await page.getByRole('button', { name: '2026-01-01 工作日', exact: true }).click();
  await fillSaveDetails(page, '更正节假日');
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('已发布 · 第 2 版')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: '2026-01-01 节假日，节假日', exact: true })).toBeVisible();
  expect(state.saves()).toBe(1); expect(state.legacyCalls).toEqual([]);
});

test('unchanged conflict baseline preserves inputs and permits retry', async ({ page }) => {
  const state = await fixture(page, true, true); state.conflict('unchanged');
  await page.goto('/admin/work-calendars?year=2026');
  await page.getByRole('button', { name: '2026-01-01 工作日', exact: true }).click();
  await fillSaveDetails(page);
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('日历已被其他管理员更新').first()).toBeVisible();
  await expect(page.getByLabel('保存或更正原因')).toHaveValue('年度保存测试');
  await expect(page.getByRole('button', { name: '保存并生效' })).toBeEnabled();
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('已发布 · 第 2 版')).toBeVisible();
  expect(state.saves()).toBe(2);
});

test('changed conflict baseline shows latest calendar and requires discarding stale marks', async ({ page }) => {
  const state = await fixture(page, true, true); state.conflict('changed');
  await page.goto('/admin/work-calendars?year=2026');
  await page.getByRole('button', { name: '2026-01-01 工作日', exact: true }).click();
  await fillSaveDetails(page);
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('日历已被其他管理员更新').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '2026-01-05 节假日，其他管理员修改', exact: true })).toBeVisible();
  await expect(page.getByLabel('保存或更正原因')).toHaveValue('年度保存测试');
  await expect(page.getByRole('button', { name: '保存并生效' })).toBeDisabled();
  await page.getByRole('button', { name: '丢弃旧版本标记' }).click();
  await expect(page.getByRole('button', { name: '2026-01-01 工作日', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '2026-01-02 工作日', exact: true }).click();
  await page.getByRole('button', { name: '保存并生效' }).click();
  await expect(page.getByText('已发布 · 第 3 版')).toBeVisible();
});

test('calendar stays within narrow and desktop viewports with keyboard access', async ({ page }, info) => {
  const state = await fixture(page);
  await page.goto('/admin/work-calendars?year=2026');
  await expect(page.getByText('凌晨流转任务',{exact:true})).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重试此运行' })).toHaveCount(0);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(page.getByRole('heading', { name: '工作日历', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.getByLabel('起始日期').focus(); await expect(page.getByLabel('起始日期')).toBeFocused();
    // 原生日期控件分别接收年、月、日和日历按钮焦点；完整走过内部段后必须抵达下一字段。
    const endDate = page.getByLabel('结束日期');
    for (let step = 0; step < 6 && !await endDate.evaluate(element => element === document.activeElement); step++) await page.keyboard.press('Tab');
    await expect(endDate).toBeFocused();
    await expect(page.locator('.annual-calendar-month')).toHaveCount(12);
    await expect(page.locator('.annual-calendar-day')).toHaveCount(365);
    const firstDate = page.getByRole('button', { name: /2026-01-01/ });
    await firstDate.scrollIntoViewIfNeeded();
    await firstDate.focus(); await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('button', { name: /2026-01-02/ })).toBeFocused();
    await page.screenshot({ path: info.outputPath(`work-calendar-${width}.png`), fullPage: true, animations: 'disabled' });
    expect(state.runReads()).toBe(0);
  }
  expect((await new AxeBuilder({ page }).include('.settings-content').analyze()).violations).toEqual([]);
});

test('non-administrators cannot load calendar maintenance APIs', async ({ page }) => {
  const state = await fixture(page, false);
  await page.goto('/admin/work-calendars?year=2026');
  await expect(page.getByText(/无权访问|没有权限|权限不足|仅管理员/).first()).toBeVisible();
  expect(state.calendarReads()).toBe(0); expect(state.runReads()).toBe(0);
});

test('annual view supports continuous marking across months and three visible types', async ({ page }, info) => {
  await fixture(page);
  await page.setViewportSize({ width: 1920, height: 1600 });
  await page.goto('/admin/work-calendars?year=2026');
  await page.getByRole('button', { name: '2026-01-31 休息日', exact: true }).click();
  await page.getByRole('button', { name: '2026-02-02 工作日', exact: true }).click({ modifiers: ['Shift'] });
  await expect(page.locator('.annual-calendar-day.holiday')).toHaveCount(3);
  await expect(page.getByText('3 天待保存', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '2026-01-31 节假日，节假日，未保存', exact: true }).click();
  await expect(page.getByRole('button', { name: '2026-01-31 休息日', exact: true })).toBeVisible();
  await expect(page.getByText('2 天待保存', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '标记工作日', exact: true }).click();
  await page.getByRole('button', { name: '2026-02-07 休息日', exact: true }).click();
  await expect(page.getByRole('button', { name: '2026-02-07 工作日，工作日，未保存', exact: true })).toBeVisible();
  await page.locator('.annual-calendar').screenshot({ path: info.outputPath('annual-calendar.png'), animations: 'disabled' });
  await page.getByRole('button', { name: '撤销未保存修改' }).click();
  await expect(page.locator('.annual-calendar-day.holiday')).toHaveCount(0);
});

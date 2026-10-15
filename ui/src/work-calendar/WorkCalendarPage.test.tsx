import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { calendarApi, type CalendarRevision } from './api';
import { WorkCalendarPage } from './WorkCalendarPage';
import type * as CalendarApiModule from './api';

vi.setConfig({ testTimeout: 20000 });

const session = vi.hoisted(() => ({ actor: { superAdmin: true } }));
vi.mock('../auth/session', () => ({ useSession: () => session }));
// 页面测试聚焦保存与版本恢复；完整365天布局、键盘和日期对应由浏览器测试验证。
vi.mock('./AnnualCalendar', () => ({ AnnualCalendar: ({ days, onMark }: { days: { date: string; dayType: string }[]; onMark: (dates: string[]) => void }) => <div>{days.slice(0, 2).map(day => <button type="button" key={day.date} onClick={() => onMark([day.date])}>{day.date} {day.dayType === 'WORKDAY' ? '工作日' : day.dayType === 'HOLIDAY' ? '节假日' : '休息日'}</button>)}</div> }));
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof CalendarApiModule>(), calendarApi: { year: vi.fn(), history: vi.fn(), save: vi.fn() } }));
const published: CalendarRevision = { id: '12', year: 2026, revision: 1, status: 'PUBLISHED', version: '3', sourceNote: null, publishedBy: null, publishedName: null, publishReason: null, publishedAt: null, recalculationId: null, days: [
  { date: '2026-01-01', dayType: 'WORKDAY', description: null },
  { date: '2026-01-02', dayType: 'WORKDAY', description: null },
] };
beforeEach(() => {
  vi.resetAllMocks(); session.actor.superAdmin = true;
  vi.mocked(calendarApi.year).mockResolvedValue({ year: 2026, published, draft: null, nextYearMissing: true });
  vi.mocked(calendarApi.save).mockImplementation((year, body) => {
    const saved = { ...published, year, id: '13', revision: 2, version: '4', days: body.days, sourceNote: body.sourceNote, publishReason: body.reason };
    vi.mocked(calendarApi.year).mockResolvedValue({ year, published: saved, draft: null, nextYearMissing: true });
    return Promise.resolve(saved);
  });
});
afterEach(cleanup);
function show(year = 2026) {
  const router = createMemoryRouter([{ path: '/admin/work-calendars', element: <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><WorkCalendarPage /></QueryClientProvider></App></ConfigProvider> }], { initialEntries: [`/admin/work-calendars?year=${year}`] });
  render(<RouterProvider router={router} />);
  return router;
}
function details(reason = '更正原因') {
  fireEvent.change(screen.getByRole('textbox', { name: /节假日与补班信息来源/ }), { target: { value: '来源说明' } });
  fireEvent.change(screen.getByRole('textbox', { name: /保存或更正原因/ }), { target: { value: reason } });
}
function save() { fireEvent.click(screen.getByRole('button', { name: '保存并生效' })); }
it('clicking the same marking restores the saved date', async () => {
  show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' }));
  expect(screen.getByText('1 天待保存')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '2026-01-01 节假日' }));
  expect(screen.getByRole('button', { name: '2026-01-01 工作日' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存并生效' })).toBeDisabled();
  expect(calendarApi.save).not.toHaveBeenCalled();
});
it('saves mixed date types atomically in one request against the published revision', async () => {
  show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' }));
  fireEvent.click(screen.getByRole('button', { name: '标记休息日' }));
  fireEvent.click(screen.getByRole('button', { name: '2026-01-02 工作日' }));
  expect(calendarApi.save).not.toHaveBeenCalled(); details(); save();
  await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(1));
  expect(calendarApi.save).toHaveBeenCalledWith(2026, { publishedRevisionId: '12', expectedVersion: '3', days: [
    { date: '2026-01-01', dayType: 'HOLIDAY', description: '节假日' },
    { date: '2026-01-02', dayType: 'RESTDAY', description: '休息日' },
  ], sourceNote: '来源说明', reason: '更正原因' }, expect.any(String));
  await screen.findByText('已发布 · 第 2 版');
  expect(screen.getByText('所有修改已保存')).toBeInTheDocument();
});
it('retains failed edits and reuses the idempotency key only for an unchanged payload', async () => {
  vi.mocked(calendarApi.save).mockRejectedValue(new Error('网络断开'));
  show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' })); details(); save();
  await screen.findByText('保存未完成，输入已保留，请重试。');
  expect(screen.getByText('1 天待保存')).toBeInTheDocument();
  save(); await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(2));
  expect(vi.mocked(calendarApi.save).mock.calls[1]).toEqual(vi.mocked(calendarApi.save).mock.calls[0]);
  await waitFor(() => expect(screen.getByRole('button', { name: '保存并生效' })).toBeEnabled());
  details('调整更正原因'); save();
  await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(3));
  expect(vi.mocked(calendarApi.save).mock.calls[2]![2]).not.toBe(vi.mocked(calendarApi.save).mock.calls[0]![2]);
});
it('does not overlay stale edits over another administrator revision', async () => {
  vi.mocked(calendarApi.save).mockRejectedValue(new ApiProblemError({ status: 409, code: 'CONFLICT', title: '版本冲突', detail: '读取最新日历', recoverable: true }));
  vi.mocked(calendarApi.year).mockResolvedValueOnce({ year: 2026, draft: null, published, nextYearMissing: false }).mockResolvedValue({ year: 2026, draft: null, published: { ...published, id: '13', version: '4', revision: 2 }, nextYearMissing: false });
  show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' })); details(); save();
  await screen.findByText('日历已被其他管理员更新');
  expect(screen.getByRole('button', { name: '2026-01-01 工作日' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '保存并生效' })).toBeDisabled();
  expect(screen.getByRole('textbox', { name: /保存或更正原因/ })).toHaveValue('更正原因');
  fireEvent.click(screen.getByRole('button', { name: '丢弃旧版本标记' }));
  expect(screen.queryByText('日历已被其他管理员更新')).not.toBeInTheDocument();
  expect(calendarApi.save).toHaveBeenCalledTimes(1);
});
it('does not request administrator calendars for a recruiter', () => {
  session.actor.superAdmin = false; show();
  expect(screen.getByText('仅管理员可以维护工作日历')).toBeInTheDocument();
  expect(calendarApi.year).not.toHaveBeenCalled();
});
it.each([[2026, 365], [2028, 366]])('generates and saves all %i calendar dates locally (%i days)', async (year, count) => {
  vi.mocked(calendarApi.year).mockResolvedValue({ year, draft: null, published: null, nextYearMissing: false });
  show(year); await screen.findByText(`共 ${count} 天`);
  expect(screen.queryByRole('button', { name: /生成全年草稿|预览发布影响|确认发布/ })).not.toBeInTheDocument();
  details(); save(); await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(1));
  const body = vi.mocked(calendarApi.save).mock.calls[0]![1];
  expect(body.publishedRevisionId).toBeNull(); expect(body.expectedVersion).toBeNull();
  expect(body.days).toHaveLength(count); expect(new Set(body.days.map(day => day.date)).size).toBe(count);
  expect(body.days[0]!.date).toBe(`${year}-01-01`); expect(body.days.at(-1)!.date).toBe(`${year}-12-31`);
  expect(body.days.find(day => day.date === `${year}-02-29`)).toBe(year === 2028 ? body.days[59] : undefined);
  expect(body.days.find(day => day.date === (year === 2026 ? '2026-01-03' : '2028-01-01'))!.dayType).toBe('RESTDAY');
});
it('requires nonblank source and reason without a publication confirmation checkbox', async () => {
  show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' })); save();
  await screen.findByText('请填写信息来源'); await screen.findByText('请填写保存原因');
  expect(calendarApi.save).not.toHaveBeenCalled(); expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  details('   '); save(); await waitFor(() => expect(screen.getByText('请填写保存原因')).toBeInTheDocument());
  expect(calendarApi.save).not.toHaveBeenCalled();
});
it('protects unsaved markings when navigation changes only the year query', async () => {
  const router = show(); fireEvent.click(await screen.findByRole('button', { name: '2026-01-01 工作日' }));
  void router.navigate('/admin/work-calendars?year=2025'); await screen.findByText('有尚未保存的日历输入');
  expect(router.state.location.search).toBe('?year=2026');
  fireEvent.click(screen.getByRole('button', { name: '继续编辑' })); expect(screen.getByText('1 天待保存')).toBeInTheDocument();
});
it('protects source and reason input even without marked dates', async () => {
  const router = show(); await screen.findByRole('button', { name: '2026-01-01 工作日' }); details();
  void router.navigate('/admin/work-calendars?year=2025'); await screen.findByText('有尚未保存的日历输入');
  expect(router.state.location.search).toBe('?year=2026');
  fireEvent.click(screen.getByRole('button', { name: '丢弃输入并离开' }));
  await waitFor(() => expect(router.state.location.search).toBe('?year=2025'));
});

it('imports legacy unpublished dates locally and saves them against an empty published baseline', async () => {
  const legacy = { ...published, status: 'DRAFT', days: [
    { date: '2026-01-01', dayType: 'HOLIDAY' as const, description: '此前节假日安排' },
    published.days[1]!,
  ] };
  vi.mocked(calendarApi.year).mockResolvedValue({ year: 2026, draft: legacy, published: null, nextYearMissing: false });
  show();
  await screen.findByText(/已带入此前未生效的日期安排，请核对后保存。/);
  expect(screen.getByRole('button', { name: '2026-01-01 节假日' })).toBeInTheDocument();
  expect(calendarApi.save).not.toHaveBeenCalled();
  details(); save();
  await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(1));
  expect(calendarApi.save).toHaveBeenCalledWith(2026, {
    publishedRevisionId: null, expectedVersion: null, days: legacy.days,
    sourceNote: '来源说明', reason: '更正原因',
  }, expect.any(String));
});
it('uses the published calendar when a legacy draft also exists', async () => {
  const legacy = { ...published, status: 'DRAFT', days: published.days.map(day => ({ ...day, dayType: 'HOLIDAY' as const })) };
  vi.mocked(calendarApi.year).mockResolvedValue({ year: 2026, draft: legacy, published, nextYearMissing: false });
  show();
  await screen.findByRole('button', { name: '2026-01-01 工作日' });
  expect(screen.queryByText(/已带入此前未生效的日期安排，请核对后保存。/)).not.toBeInTheDocument();
  details(); save();
  await waitFor(() => expect(calendarApi.save).toHaveBeenCalledTimes(1));
  expect(calendarApi.save).toHaveBeenCalledWith(2026, {
    publishedRevisionId: published.id, expectedVersion: published.version, days: published.days,
    sourceNote: '来源说明', reason: '更正原因',
  }, expect.any(String));
});

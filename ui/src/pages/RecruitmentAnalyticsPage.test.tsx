import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { analyticsApi, type AnalyticsPortrait, type RecruitmentAnalytics } from '../reporting/analyticsApi';
import type * as AnalyticsApiModule from '../reporting/analyticsApi';
import { useAnalyticsMotion } from '../reporting/analyticsMotion';
import { RecruitmentAnalyticsPage } from './RecruitmentAnalyticsPage';
import { businessDay, dateRangeParams } from '../components/dateRange';
import { periodRange } from '../reporting/analyticsPeriod';

vi.mock('../reporting/analyticsApi', async importOriginal => ({ ...await importOriginal<typeof AnalyticsApiModule>(), analyticsApi: { report: vi.fn() } }));
vi.mock('../demand/JdSelector', () => ({ JdSelector: () => <span>岗位选择</span> }));
vi.mock('../reporting/ReferenceCharts', () => ({
  ChannelDonut: () => <div>渠道图表</div>, CompletionDonut: () => <div>HC占用图表</div>,
  LocationDistribution: () => <div>地区分布图表</div>,
  ReferenceTrend: () => <div aria-label="趋势动效状态" data-motion={String(useAnalyticsMotion())}>趋势图表</div>,
  PortraitOverview: ({ rows, result, mode }: { rows: AnalyticsPortrait[]; result: string; mode: string }) => <div role="img" aria-label={`${mode === 'education' ? '学历' : '画像'} ${result} ${rows.map(row => `${row.label}:${row.passed}/${row.rejected}/${row.pending}/${row.withdrawn}`).join(';')}`} />,
}));

const response: RecruitmentAnalytics = {
  from: '2026-08-31T16:00:00Z', to: '2026-09-30T16:00:00Z', asOf: '2026-09-15T08:00:00Z',
  demand: { activeJobs: 2, headcount: 5, unknownHeadcountJobs: 1, occupied: 3, openHeadcount: 2 },
  risk: { activeBlacklists: 3 },
  funnel: [{ key: 'RESUME', label: '全部简历', count: 15 }, { key: 'APPLICATION', label: '进入流程', count: 10 },
    { key: 'INTERVIEW_1', label: '一面', count: 6 }, { key: 'INTERVIEW_2', label: '二面', count: 3 },
    { key: 'INTERVIEW_3_PLUS', label: '三面+', count: 2 }, { key: 'OFFER', label: 'Offer', count: 2 }, { key: 'ONBOARDED', label: '已入职', count: 1 }],
  trend: [{ date: '2026-09-01', applications: 10, offers: 2, onboarded: 1 }],
  channels: [{ id: 'boss', name: 'BOSS', resumes: 15, applications: 10, offers: 2, onboarded: 1 }],
  portraits: [{ dimension: 'AGE', label: '未知', resumes: 15, passed: 2, rejected: 1, pending: 7, withdrawn: 0 }],
  efficiency: { completedCount: 0, averageTotalHours: null, activeCount: 10, averageActiveHours: 20 },
  departments: [{ id: 'dept-a', name: '技术部' }], notes: ['按 JD 绑定部门筛选。'],
};

const clients: QueryClient[] = [];
function mount(url = '/recruitment-analysis?period=month&date=2026-09-15') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  clients.push(client);
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[url]}><RecruitmentAnalyticsPage /></MemoryRouter></QueryClientProvider>);
  return client;
}
beforeEach(() => { vi.mocked(analyticsApi.report).mockReset(); vi.mocked(analyticsApi.report).mockResolvedValue(response); });
afterEach(() => { cleanup(); clients.splice(0).forEach(client => client.clear()); });

it('defaults to the current month and resets back to it after selecting cumulative', async () => {
  const monthFilters = dateRangeParams(periodRange('month', businessDay()));
  mount('/recruitment-analysis?departmentId=dept-a&jdId=jd-a');
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  expect(screen.getByRole('radio', { name: '月' })).toBeChecked();
  expect(screen.getByLabelText('选择统计期间')).toHaveValue(businessDay().format('YYYY-MM'));
  expect(analyticsApi.report).toHaveBeenLastCalledWith({ ...monthFilters, departmentId: 'dept-a', jdId: 'jd-a' }, expect.any(AbortSignal));
  expect(screen.getByText('本期入库简历')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '累计' }));
  await waitFor(() => expect(analyticsApi.report).toHaveBeenLastCalledWith({ departmentId: 'dept-a', jdId: 'jd-a' }, expect.any(AbortSignal)));
  expect(screen.getByRole('radio', { name: '累计' })).toBeChecked();
  expect(screen.queryByLabelText('选择统计期间')).not.toBeInTheDocument();
  expect(await screen.findByText('累计入库简历')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重置筛选' }));
  await waitFor(() => expect(analyticsApi.report).toHaveBeenLastCalledWith(monthFilters, expect.any(AbortSignal)));
  expect(screen.getByRole('radio', { name: '月' })).toBeChecked();
  expect(screen.getByLabelText('选择统计期间')).toHaveValue(businessDay().format('YYYY-MM'));
});

it('restores URL filters and presents current HC separately from the cohort', async () => {
  mount('/recruitment-analysis?period=year&date=2026-05-01&departmentId=dept-a&jdId=jd-a&recruitmentType=EXTERNAL');
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  expect(analyticsApi.report).toHaveBeenCalledWith({ from: '2025-12-31T16:00:00.000Z', to: '2026-12-31T16:00:00.000Z', departmentId: 'dept-a', jdId: 'jd-a', recruitmentType: 'EXTERNAL' }, expect.any(AbortSignal));
  expect(screen.queryByRole('button', { name: '查看统计口径与数据说明' })).not.toBeInTheDocument();
  expect(screen.queryByText('看清渠道质量，理解人才结构')).not.toBeInTheDocument();
  expect(screen.getByText(/1 个岗位未填写编制/)).toBeInTheDocument();
  expect(screen.queryByText(/HC 含已发 Offer、已接受及已入职/)).not.toBeInTheDocument();
  expect(screen.queryByText(/黑名单简历在流程结束后进入共享库/)).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('有效黑名单').nextElementSibling).toHaveTextContent('3'));
  expect(screen.getByRole('img', { name: '画像 ALL 未知:2/1/7/0' })).toBeInTheDocument();
});

it('presents summary, conversion and portraits in a clear dashboard hierarchy', async () => {
  mount();
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  expect(screen.getByRole('heading', { level: 1, name: '招聘分析' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '招聘流程转化' })).toBeInTheDocument();
  expect(screen.getByText('渠道图表')).toBeInTheDocument();
  expect(screen.getByText('地区分布图表')).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: '全部' })).toBeChecked();
  expect(screen.getByText('本期入库简历')).toBeInTheDocument();
  const summary = document.querySelector('.analytics-summary') as HTMLElement;
  expect([...summary.querySelectorAll('dt')].map(element => element.textContent)).toEqual(['入库简历', '进入流程', '进入入职流程', '已入职']);
  await waitFor(() => expect([...summary.querySelectorAll('.sr-only')].map(element => element.textContent)).toEqual(['15', '10', '2', '1']));
  expect(document.querySelector('.analytics-capacity-track')).toBeInTheDocument();
  expect(document.querySelector('.analytics-hc-grid')).not.toBeInTheDocument();
});

it('retains system motion preference without a decorative motion control or extra requests', async () => {
  mount();
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  const root = document.querySelector('.analytics-page')!;
  expect(screen.getByLabelText('趋势动效状态')).toHaveAttribute('data-motion', 'true');
  expect(root).toHaveAttribute('data-motion', 'on');
  expect(screen.queryByRole('combobox', { name: '动效速度' })).not.toBeInTheDocument();
  expect(root).not.toHaveAttribute('data-motion-effect');
  expect(document.querySelector('.analytics-ambient')).not.toBeInTheDocument();
  expect(analyticsApi.report).toHaveBeenCalledTimes(1);
});

it('shows stage shares against the application cohort without mixing resume intake', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response,
    funnel: response.funnel.map(row => ({ ...row, count: ({ RESUME: 267, APPLICATION: 18, INTERVIEW_1: 11, INTERVIEW_2: 8, INTERVIEW_3_PLUS: 6, OFFER: 5, ONBOARDED: 2 } as Record<string, number>)[row.key] ?? 0 })),
    portraits: response.portraits.map(row => ({ ...row, rejected: 2 })),
  });
  mount();
  await screen.findByRole('heading', { name: '招聘流程转化' });
  const stages = [...document.querySelectorAll('.analytics-funnel-stage')];
  expect(stages).toHaveLength(8);
  expect(within(stages[0] as HTMLElement).getByText('本期简历')).toBeInTheDocument();
  expect(within(stages[0] as HTMLElement).getByRole('img', { name: '入库简历与进入流程统计队列不同，不计算转化率' })).toHaveTextContent('按人去重');
  expect(within(stages[1] as HTMLElement).getByText('进入流程')).toBeInTheDocument();
  expect(within(stages[1] as HTMLElement).getByRole('img', { name: '进入流程为比例基数 18 条' })).toHaveTextContent('比例基数');
  for (const [index, label, numerator, percentage] of [[2, '一面', 11, 61], [3, '二面', 8, 44], [4, '三面+', 6, 33], [5, 'Offer', 5, 28], [6, '已入职', 2, 11], [7, '不通过', 2, 11]] as const) {
    expect(within(stages[index] as HTMLElement).getByRole('img', { name: `${label}占进入流程比例 ${percentage}%（${numerator} ÷ 18）` })).toHaveTextContent(`占流程 ${percentage}%`);
  }
  expect(screen.getByText('流程至 Offer')).toHaveTextContent('28%');
  expect(screen.getByText('流程至入职')).toHaveTextContent('11%');
  expect(screen.queryByRole('note', { name: '漏斗统计口径' })).not.toBeInTheDocument();
  expect(document.querySelectorAll('.analytics-funnel-bar')).toHaveLength(8);
  expect(stages[6]!.querySelector('.analytics-funnel-arrow i')).not.toBeInTheDocument();
  expect(stages[7]!.querySelector('.analytics-funnel-arrow i')).not.toBeInTheDocument();
  expect(screen.getByText('流程至 Offer')).toBeInTheDocument();
  expect(screen.getByText('1 天')).toBeInTheDocument();
  expect(within(stages[5] as HTMLElement).getByText('Offer')).toBeInTheDocument();
  expect(within(stages[6] as HTMLElement).getByText('已入职')).toBeInTheDocument();
  expect(screen.queryByText('筛选通过')).not.toBeInTheDocument();
});

it('retains department and JD when moving to another period', async () => {
  mount('/recruitment-analysis?period=month&date=2026-09-15&departmentId=dept-a&jdId=jd-a');
  await screen.findByRole('heading', { name: '招聘流程转化' });
  fireEvent.click(screen.getByRole('button', { name: '上一周期' }));
  await waitFor(() => expect(analyticsApi.report).toHaveBeenLastCalledWith({ from: '2026-07-31T16:00:00.000Z', to: '2026-08-31T16:00:00.000Z', departmentId: 'dept-a', jdId: 'jd-a' }, expect.any(AbortSignal)));
});

it('does not fabricate numbers during initial load and supports retry', async () => {
  vi.mocked(analyticsApi.report).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(response);
  mount();
  expect(screen.queryByRole('heading', { name: '招聘需求与风险' })).not.toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button', { name: /重\s*试/ }));
  expect(await screen.findByRole('heading', { name: '招聘需求与风险' })).toBeInTheDocument();
});

it('removes cached aggregate data when refreshed authorization is forbidden', async () => {
  const client = mount();
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  vi.mocked(analyticsApi.report).mockRejectedValue(new ApiProblemError({ status: 403, code: 'FORBIDDEN', title: '无权限', detail: '无权限', recoverable: false }));
  await act(async () => { await client.refetchQueries({ queryKey: ['reporting', 'recruitment-analysis'] }); });
  expect(await screen.findByText('暂无招聘分析查看权限')).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '招聘需求与风险' })).not.toBeInTheDocument();
});

it('shows missing durations and zero-denominator rates as unavailable rather than zero efficiency', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, funnel: response.funnel.map(row => ({ ...row, count: 0 })), trend: [], channels: [], portraits: [], efficiency: { completedCount: 0, averageTotalHours: null, activeCount: 0, averageActiveHours: null } });
  mount();
  await screen.findByRole('heading', { name: '招聘流程转化' });
  expect(screen.getByRole('img', { name: '画像 ALL' })).toBeInTheDocument();
  expect(screen.getAllByText('—', { exact: false }).length).toBeGreaterThanOrEqual(4);
  expect(screen.getAllByText('— 天')).toHaveLength(2);
  for (const stage of [...document.querySelectorAll('.analytics-funnel-stage')].slice(2)) {
    expect(within(stage as HTMLElement).getByRole('img', { name: /占进入流程比例 —/ })).toHaveTextContent('占流程 —');
  }
});

it('passes the selected portrait result with the authorized rows to the chart', async () => {
  mount();
  await screen.findByRole('img', { name: '画像 ALL 未知:2/1/7/0' });
  fireEvent.click(screen.getByRole('radio', { name: '通过' }));
  expect(screen.getByRole('img', { name: '画像 PASSED 未知:2/1/7/0' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '不通过' }));
  expect(screen.getByRole('img', { name: '画像 REJECTED 未知:2/1/7/0' })).toBeInTheDocument();
});

const hrWorkload = [
  { ownerUserId: 'hr-a', displayName: '王芳', resumes: 20, interview1: 8, interview2: 5, interview3Plus: 2, self: true, inactive: false, applications: 12, interviews: 8, offers: 3, onboarded: 2, rejected: 1 },
  { ownerUserId: 'hr-b', displayName: '李明', resumes: 0, interview1: 0, interview2: 0, interview3Plus: 0, self: false, inactive: true, applications: 10, interviews: 6, offers: 2, onboarded: 1, rejected: 2 },
];

it('does not expose an HR section without authorized workload data', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload: null });
  mount();
  await screen.findByRole('heading', { name: '招聘需求与风险' });
  expect(screen.queryByRole('heading', { name: '员工招聘统计' })).not.toBeInTheDocument();
  expect(screen.queryByRole('combobox', { name: '招聘人员' })).not.toBeInTheDocument();
});

it('places authorized HR workload below the dashboard and filters personnel', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload });
  mount();
  const heading = await screen.findByRole('heading', { name: '员工招聘统计' });
  expect(screen.getByRole('heading', { name: '招聘需求与风险' }).compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  const table = screen.getAllByRole('table').find(item => within(item).queryByText('王芳'))!;
  expect(within(table).getByRole('columnheader', { name: 'Offer' })).toBeInTheDocument();
  expect(within(table).getByText('王芳')).toBeInTheDocument();
  expect(within(table).getByText('李明')).toBeInTheDocument();
  expect(screen.getByText('共 2 位员工')).toBeInTheDocument();
  expect(screen.queryByText(/沿用上方日期/)).not.toBeInTheDocument();
  expect(screen.queryByText(/不代表历史操作工作量/)).not.toBeInTheDocument();
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '招聘人员' }));
  fireEvent.click(await screen.findByText('王芳（我）', { selector: '.ant-select-item-option-content' }));
  await waitFor(() => expect(screen.queryByRole('row', { name: /李明/ })).not.toBeInTheDocument());
  expect(screen.getByRole('row', { name: /王芳 我/ })).toBeInTheDocument();
  expect(screen.getByText('共 2 位员工')).toBeInTheDocument();
  expect(analyticsApi.report).toHaveBeenCalledTimes(1);
  const resumeStage = screen.getByText('本期简历', { selector: '.analytics-funnel-step > span' }).closest<HTMLElement>('.analytics-funnel-stage');
  expect(resumeStage).not.toBeNull();
  expect(within(resumeStage!).getByText('15', { selector: '.sr-only' })).toBeInTheDocument();
  expect(within(resumeStage!).getByText('人')).toBeInTheDocument();
});

it('does not present skipped interviews as impossible adjacent conversion rates', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, funnel: response.funnel.map(row => ({ ...row, count: ({ RESUME: 141, APPLICATION: 55, INTERVIEW_1: 3, INTERVIEW_2: 0, INTERVIEW_3_PLUS: 1, OFFER: 4, ONBOARDED: 2 } as Record<string, number>)[row.key] ?? 0 })) });
  mount();
  await screen.findByRole('heading', { name: '招聘流程转化' });
  const stages = [...document.querySelectorAll<HTMLElement>('.analytics-funnel-stage')];
  expect(within(stages[4]!).getByRole('img', { name: '三面+占进入流程比例 2%（1 ÷ 55）' })).toBeInTheDocument();
  expect(within(stages[5]!).getByRole('img', { name: 'Offer占进入流程比例 7%（4 ÷ 55）' })).toBeInTheDocument();
  expect(screen.getByText('流程至 Offer')).toHaveTextContent('7%');
  expect(screen.queryByText('400%')).not.toBeInTheDocument();
  expect(screen.queryByText('133%')).not.toBeInTheDocument();
});

it('removes previously visible HR workload after a forbidden refresh', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload });
  const client = mount();
  await screen.findByRole('heading', { name: '员工招聘统计' });
  vi.mocked(analyticsApi.report).mockRejectedValue(new ApiProblemError({ status: 403, code: 'FORBIDDEN', title: '无权限', detail: '无权限', recoverable: false }));
  await act(async () => { await client.refetchQueries({ queryKey: ['reporting', 'recruitment-analysis'] }); });
  await screen.findByText('暂无招聘分析查看权限');
  expect(screen.queryByRole('heading', { name: '员工招聘统计' })).not.toBeInTheDocument();
  expect(screen.queryByText('王芳')).not.toBeInTheDocument();
});

it('returns to all personnel when the selected HR is absent from the next scope', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload });
  mount();
  await screen.findByRole('heading', { name: '员工招聘统计' });
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '招聘人员' }));
  fireEvent.click(await screen.findByText('王芳（我）', { selector: '.ant-select-item-option-content' }));
  expect(screen.getByRole('row', { name: /王芳 我 20 12/ })).toBeInTheDocument();
  expect(screen.queryByRole('row', { name: /李明 已停用 0 10/ })).not.toBeInTheDocument();
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload: [hrWorkload[1]!] });
  fireEvent.click(screen.getByRole('button', { name: '上一周期' }));
  expect(await screen.findByRole('row', { name: /李明 已停用 0 10/ })).toBeInTheDocument();
  expect(screen.queryByRole('row', { name: /王芳 我 20 12/ })).not.toBeInTheDocument();
  expect(screen.getByText('全部人员')).toBeInTheDocument();
}, 15_000);

it('keeps zero-count employees and distinguishes an authorized empty roster', async () => {
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload: [{ ...hrWorkload[0]!, resumes: 0, applications: 0, interviews: 0, interview1: 0, interview2: 0, interview3Plus: 0, offers: 0, rejected: 0, onboarded: 0 }] });
  const client = mount();
  const row = await screen.findByRole('row', { name: /王芳 我/ });
  expect(within(row).getAllByRole('cell', { name: '0' })).toHaveLength(8);
  vi.mocked(analyticsApi.report).mockResolvedValue({ ...response, hrWorkload: [] });
  await act(async () => { await client.refetchQueries({ queryKey: ['reporting', 'recruitment-analysis'] }); });
  expect(screen.getByRole('heading', { name: '员工招聘统计' })).toBeInTheDocument();
  expect(await screen.findByText('当前权限范围内暂无员工')).toBeInTheDocument();
});

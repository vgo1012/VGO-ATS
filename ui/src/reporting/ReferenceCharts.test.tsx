import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ChannelDonut, CompletionDonut, LocationDistribution, PortraitOverview, ReferenceTrend } from './ReferenceCharts';
import type { AnalyticsPortrait } from './analyticsApi';
import dayjs from 'dayjs';
import { periodRange, trendBuckets, type AnalyticsPeriod } from './analyticsPeriod';
import { AnalyticsMotionContext } from './analyticsMotion';

const charts = vi.hoisted(() => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn(), dispatchAction: vi.fn<(action: { type: string; name?: string; geoIndex?: number; seriesIndex?: number; dataIndex?: number }) => void>() }));
vi.mock('echarts/core', () => ({
  init: vi.fn(() => ({ setOption: charts.setOption, dispose: charts.dispose, resize: charts.resize, dispatchAction: charts.dispatchAction })),
  use: vi.fn(),
}));
beforeEach(() => { vi.clearAllMocks(); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.useRealTimers(); });

it('keeps the chart instance when values change and replaces removed series', () => {
  const view = render(<CompletionDonut occupied={2} headcount={5} />);
  view.rerender(<CompletionDonut occupied={3} headcount={5} />);
  expect(charts.dispose).not.toHaveBeenCalled();
  expect(charts.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animation: true }), { replaceMerge: ['series'] });
  view.unmount();
  expect(charts.dispose).toHaveBeenCalledOnce();
});

it('updates trend values without automatic tooltip tours or idle timers', async () => {
  vi.useFakeTimers();
  const rows = [{ date: '2026-09-01', applications: 4, offers: 1, onboarded: 0 }, { date: '2026-09-02', applications: 7, offers: 2, onboarded: 1 }];
  const view = render(<AnalyticsMotionContext value={true}><ReferenceTrend rows={rows} /></AnalyticsMotionContext>);
  await act(() => vi.advanceTimersByTime(12000));
  expect(charts.dispatchAction).not.toHaveBeenCalled();
  const chart = screen.getByRole('img');
  expect(chart).not.toHaveAttribute('tabindex');
  expect(charts.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animation: true }), { replaceMerge: ['series'] });
  view.rerender(<AnalyticsMotionContext value={false}><ReferenceTrend rows={[{ ...rows[0]!, applications: 9 }]} /></AnalyticsMotionContext>);
  expect(charts.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animation: false,
    series: [expect.objectContaining({ data: [9] }), expect.objectContaining({ data: [1] }), expect.objectContaining({ data: [0] })],
  }), { replaceMerge: ['series'] });
  view.unmount();
  expect(vi.getTimerCount()).toBe(0);
});

it('respects reduced motion for chart rendering', async () => {
  vi.useFakeTimers();
  const media = window.matchMedia('(prefers-reduced-motion: reduce)');
  vi.spyOn(window, 'matchMedia').mockReturnValue({ ...media, matches: true });
  render(<ReferenceTrend rows={[{ date: '2026-09-01', applications: 4, offers: 1, onboarded: 0 }, { date: '2026-09-02', applications: 7, offers: 2, onboarded: 1 }]} />);
  expect(charts.setOption).toHaveBeenLastCalledWith(expect.objectContaining({ animation: false }), { replaceMerge: ['series'] });
  await act(() => vi.advanceTimersByTime(6400));
  expect(charts.dispatchAction).not.toHaveBeenCalled();
});

const portraits: AnalyticsPortrait[] = [
  { dimension: 'AGE', label: '26–30岁', resumes: 9, passed: 3, rejected: 1, pending: 4, withdrawn: 2 },
  { dimension: 'AGE', label: '未知', resumes: 6, passed: 1, rejected: 2, pending: 0, withdrawn: 0 },
  { dimension: 'EDUCATION', label: '本科', resumes: 15, passed: 4, rejected: 3, pending: 4, withdrawn: 2 },
];

it('updates actual age series and accessible counts when portrait results change', () => {
  const { rerender } = render(<PortraitOverview rows={portraits} result="ALL" />);
  const age = screen.getByRole('img', { name: '26–30岁 9，60%；未知 6，40%' });
  expect(age.querySelectorAll('.reference-age-row')).toHaveLength(2);
  expect(within(age).getByText('60%')).toBeInTheDocument();
  expect(within(age).getByText('40%')).toBeInTheDocument();
  expect(charts.setOption).toHaveBeenCalledWith(expect.objectContaining({ tooltip: { show: false }, series: [expect.objectContaining({ stack: 'education' })] }), { replaceMerge: ['series'] });
  charts.setOption.mockClear();
  rerender(<PortraitOverview rows={portraits} result="PASSED" />);
  expect(screen.getByRole('img', { name: '26–30岁 3，75%；未知 1，25%' })).toBeInTheDocument();
  expect(within(screen.getByRole('img', { name: '26–30岁 3，75%；未知 1，25%' })).getByText('75%')).toBeInTheDocument();
  rerender(<PortraitOverview rows={portraits} result="REJECTED" />);
  expect(screen.getByRole('img', { name: '26–30岁 1，33%；未知 2，67%' })).toBeInTheDocument();
});

it('sizes age bars by share and keeps all six age buckets readable', () => {
  const ages = ['25岁以下', '25–29岁', '30–34岁', '35–39岁', '40岁及以上', '未知'].map((label, index) => ({
    dimension: 'AGE' as const, label, resumes: index + 1, passed: index + 1, rejected: 0, pending: 0, withdrawn: 0,
  }));
  render(<PortraitOverview rows={ages} result="ALL" mode="age" />);
  const age = screen.getByRole('img');
  expect(age.querySelectorAll('.reference-age-row')).toHaveLength(6);
  const widths = [...age.querySelectorAll<HTMLElement>('.reference-age-fill')].map(bar => Number.parseFloat(bar.style.width));
  expect(widths[5]).toBeGreaterThan(widths[0]!);
  expect(widths.reduce((sum, value) => sum + value, 0)).toBeCloseTo(100);
  for (const label of ages.map(row => row.label)) expect(within(age).getByText(label)).toBeInTheDocument();
});

it('retains unknown and multi-location labels in selected portrait results', () => {
  const rows: AnalyticsPortrait[] = [
    { dimension: 'INTENDED_LOCATION', label: '上海', resumes: 5, passed: 3, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '上海、北京', resumes: 7, passed: 7, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '未知', resumes: 2, passed: 2, rejected: 0, pending: 0, withdrawn: 0 },
  ];
  const view = render(<PortraitOverview rows={rows} result="ALL" />);
  const list = screen.getByRole('list', { name: '入库简历工作城市数据' });
  expect(within(list).getByText('上海、北京')).toBeInTheDocument();
  expect(within(list).getByText('未知')).toBeInTheDocument();
  expect(within(list).getByText('上海')).toBeInTheDocument();
  expect(screen.getByText(/多地点和未知记录保留原始标签/)).toBeInTheDocument();
  expect(charts.setOption).not.toHaveBeenCalled();
  view.rerender(<PortraitOverview rows={rows} result="PASSED" />);
  expect(within(screen.getByRole('list', { name: '入库简历工作城市数据' })).getByText('3')).toBeInTheDocument();
  expect(document.querySelector('.reference-location-summary')).toHaveTextContent('合计 12 人');
  view.rerender(<PortraitOverview rows={rows} result="REJECTED" />);
  expect(screen.getByText('当前结果暂无岗位工作地区数据')).toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
});

it('keeps zero-result portraits and zero channels empty instead of inventing proportions', () => {
  render(<><PortraitOverview rows={portraits.map(row => ({ ...row, passed: 0 }))} result="PASSED" /><ChannelDonut rows={[]} /><CompletionDonut occupied={0} headcount={0} /></>);
  expect(screen.getByText('当前结果暂无年龄数据')).toBeInTheDocument();
  expect(screen.getByText('当前结果暂无学历数据')).toBeInTheDocument();
  expect(screen.getByText('暂无招聘渠道数据')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: 'HC 占用 0，计划 0，占用率 —' })).toBeInTheDocument();
  expect(screen.queryByText('100%')).not.toBeInTheDocument();
});

for (const result of ['ALL', 'PASSED', 'REJECTED'] as const) {
  it(`orders ${result} education from highest to lowest without changing counts`, () => {
    const rows: AnalyticsPortrait[] = ['专科', '未知', '本科', '其他/未标准化', '硕士', '高中/中专', '博士'].map((label, index) => ({
      dimension: 'EDUCATION', label, resumes: index + 1, passed: 7 - index, rejected: index === 6 ? 0 : index + 2, pending: 0, withdrawn: 0,
    }));
    const order = ['博士', '硕士', '本科', '专科', '高中/中专', '其他/未标准化', '未知'];
    const field = result === 'ALL' ? 'resumes' : result === 'PASSED' ? 'passed' : 'rejected';
    const counts = order.map(label => rows.find(row => row.label === label)![field]);
    render(<PortraitOverview rows={rows} result={result} mode="education" />);
    expect(charts.setOption).toHaveBeenLastCalledWith(expect.objectContaining({
      xAxis: expect.objectContaining({ data: order }) as unknown,
      series: [expect.objectContaining({ data: counts })],
    }), { replaceMerge: ['series'] });
    const total = counts.reduce((sum, count) => sum + count, 0);
    expect(screen.getByRole('img')).toHaveAccessibleName(order.map((label, index) => `${label} ${counts[index]}，${Math.round(counts[index]! / total * 100)}%`).join('；'));
    expect(rows.map(row => row.label)).toEqual(['专科', '未知', '本科', '其他/未标准化', '硕士', '高中/中专', '博士']);
  });
}

it('shows intake distributions even when none of the resumes entered a flow', () => {
  const rows: AnalyticsPortrait[] = [
    { dimension: 'AGE', label: '未知', resumes: 4, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'EDUCATION', label: '未知', resumes: 4, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
  ];
  render(<><PortraitOverview rows={rows} result="ALL" mode="age" /><PortraitOverview rows={rows} result="ALL" mode="education" /><ChannelDonut rows={[{ id: 'a', name: '上传', resumes: 4, applications: 0, offers: 0, onboarded: 0 }]} /></>);
  expect(document.querySelector('.reference-age')).toHaveAttribute('aria-label', '未知 4，100%');
  expect(document.querySelector('.reference-education-bars')).toHaveAttribute('aria-label', '未知 4，100%');
  expect(screen.getByRole('img', { name: '招聘渠道，共 4 份入库简历，明细见右侧' })).toBeInTheDocument();
});

it('shows channel counts without redundant percentages and retains unknown channels', () => {
  render(<ChannelDonut rows={[
    { id: 'a', name: '招聘平台', resumes: 12, applications: 6, offers: 1, onboarded: 0 },
    { id: 'b', name: '未知', resumes: 3, applications: 2, offers: 2, onboarded: 1 },
  ]} />);
  expect(screen.getByRole('img', { name: '招聘渠道，共 15 份入库简历，明细见右侧' })).toBeInTheDocument();
  expect(screen.getAllByRole('listitem').map(row => row.textContent)).toEqual(['招聘平台12', '未知3']);
  expect(screen.getByText('入库简历来源占比')).toBeInTheDocument();
  expect(charts.setOption).toHaveBeenCalledWith(expect.objectContaining({ tooltip: { show: false } }), { replaceMerge: ['series'] });
});

it('aggregates trimmed city labels, retains unknown and multi-city records, and ranks every count', () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const rows: AnalyticsPortrait[] = [
    { dimension: 'INTENDED_LOCATION', label: '上海市', resumes: 5, passed: 1, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: ' 上海市 ', resumes: 3, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '六安', resumes: 36, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '未知', resumes: 4, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: ' ', resumes: 2, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '上海、北京', resumes: 7, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '零数量', resumes: 0, passed: 10, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'AGE', label: '其他维度', resumes: 100, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
  ];
  const view = render(<LocationDistribution rows={rows} />);
  const list = screen.getByRole('list', { name: '入库简历工作城市数据' });
  expect(within(list).getAllByRole('listitem')).toHaveLength(4);
  expect([...list.querySelectorAll('.reference-location-label')].map(row => row.textContent))
    .toEqual(['六安36 人63%', '上海市8 人14%', '上海、北京7 人12%', '未知6 人11%']);
  expect(document.querySelector('.reference-location-summary')).toHaveTextContent('4 个地区分组合计 57 人');
  expect(screen.queryByText('零数量')).not.toBeInTheDocument();
  expect(screen.queryByText('其他维度')).not.toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();
  expect(charts.setOption).not.toHaveBeenCalled();
  const widths = [...list.querySelectorAll<HTMLElement>('.reference-location-fill')].map(bar => Number.parseFloat(bar.style.width));
  expect(widths[0]).toBe(100);
  expect(widths[1]).toBeCloseTo(8 / 36 * 100);
  view.rerender(<LocationDistribution rows={[...rows, { ...rows[0]!, label: '北京', resumes: 195 }]} />);
  expect(screen.getByRole('list', { name: '入库简历工作城市数据' }).firstElementChild).toHaveTextContent('北京195 人');
  expect(document.querySelector('.reference-location-summary')).toHaveTextContent('合计 252 人');
});

it('keeps every full regional label accessible without truncating the list', () => {
  const rows: AnalyticsPortrait[] = Array.from({ length: 24 }, (_, index) => ({
    dimension: 'INTENDED_LOCATION', label: `工作城市分组 ${index}：上海、北京、杭州及其他已记录地点`,
    resumes: index + 1, passed: 0, rejected: 0, pending: 0, withdrawn: 0,
  }));
  render(<LocationDistribution rows={rows} />);
  const list = screen.getByRole('list', { name: '入库简历工作城市数据' });
  expect(within(list).getAllByRole('listitem')).toHaveLength(24);
  for (const row of rows) expect(within(list).getByText(row.label)).toBeInTheDocument();
  expect(document.querySelector('.reference-location-summary')).toHaveTextContent('合计 300 人');
});

it('shows a truthful empty region state when there are no positive intake counts', () => {
  render(<LocationDistribution rows={[{ dimension: 'INTENDED_LOCATION', label: '北京', resumes: 0, passed: 12, rejected: 2, pending: 0, withdrawn: 0 }]} />);
  expect(screen.getByText('暂无岗位工作城市数据')).toBeInTheDocument();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
  expect(screen.queryByText('北京')).not.toBeInTheDocument();
});

it('exposes exact trend cohort counts in an expandable table with truthful labels', async () => {
  render(<ReferenceTrend rows={[
    { date: '2026-09-01', applications: 12, offers: 4, onboarded: 1 },
    { date: '2026-09-02', applications: 7, offers: 2, onboarded: 0 },
  ]} />);
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(screen.queryByText('新增简历')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '查看数据表' }));
  const table = screen.getAllByRole('table').find(item => within(item).queryByText('2026-09-01'))!;
  expect(screen.getByRole('columnheader', { name: '流程创建日期' })).toBeInTheDocument();
  expect(within(table).getByRole('row', { name: '2026-09-01 12 4 1' })).toBeInTheDocument();
  expect(within(table).getByRole('row', { name: '2026-09-02 7 2 0' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '收起数据表' }));
  await waitFor(() => expect(screen.queryByRole('table')).not.toBeInTheDocument());
});

it('shows a true empty trend and disposes chart resources on unmount', () => {
  const empty = render(<ReferenceTrend rows={[]} />);
  expect(screen.getByText('所选期间暂无招聘流程')).toBeInTheDocument();
  expect(charts.setOption).not.toHaveBeenCalled();
  empty.unmount();
  const chart = render(<CompletionDonut occupied={2} headcount={5} />);
  chart.unmount();
  expect(charts.dispose).toHaveBeenCalledOnce();
});

it('shows each equal value only once per date, including coincident zero values', () => {
  render(<ReferenceTrend rows={[
    { date: '2026-09-01', applications: 0, offers: 0, onboarded: 0 },
    { date: '2026-09-02', applications: 5, offers: 5, onboarded: 0 },
    { date: '2026-09-03', applications: 8, offers: 3, onboarded: 3 },
  ]} />);
  const option = charts.setOption.mock.lastCall![0] as {
    series: Array<{
      data: number[];
      label: { show: boolean; formatter: (point: { dataIndex: number; value: number }) => string };
      labelLayout: { hideOverlap: boolean };
    }>;
  };
  const visibleLabels = (dataIndex: number) => option.series.map(series => series.label.formatter({ dataIndex, value: series.data[dataIndex]! }));
  expect(visibleLabels(0)).toEqual(['0', '', '']);
  expect(visibleLabels(1)).toEqual(['5', '', '0']);
  expect(visibleLabels(2)).toEqual(['8', '3', '']);
  expect(option.series.every(series => series.label.show && series.labelLayout.hideOverlap)).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: '查看数据表' }));
  expect(screen.getByRole('row', { name: '2026-09-01 0 0 0' })).toBeInTheDocument();
  expect(screen.getByRole('row', { name: '2026-09-02 5 5 0' })).toBeInTheDocument();
});

it.each<[AnalyticsPeriod, string[], string]>([
  ['week', ['9/14', '9/15', '9/16', '9/17', '9/18', '9/19', '9/20'], '2026-09-15'],
  ['month', ['第1周', '第2周', '第3周', '第4周', '第5周'], '2026-09-14 ～ 2026-09-20'],
  ['quarter', ['7月', '8月', '9月'], '2026-09-01 ～ 2026-09-30'],
  ['year', ['第1季度', '第2季度', '第3季度', '第4季度'], '2026-07-01 ～ 2026-09-30'],
])('labels %s trend buckets and preserves accurate aggregated table counts', (period: AnalyticsPeriod, labels: string[], dateLabel: string) => {
  const range = periodRange(period, dayjs('2026-09-15'));
  const rows = trendBuckets([
    { date: '2026-09-15', applications: 7, offers: 2, onboarded: 1 },
  ], period, range);
  render(<ReferenceTrend rows={rows} period={period} range={range} />);
  const option = charts.setOption.mock.lastCall![0] as { xAxis: { data: string[] } };
  expect(option.xAxis.data).toEqual(labels);
  fireEvent.click(screen.getByRole('button', { name: '查看数据表' }));
  expect(screen.getByRole('row', { name: `${dateLabel} 7 2 1` })).toBeInTheDocument();
  if (period === 'month') {
    expect(screen.getByRole('row', { name: '2026-09-01 ～ 2026-09-06 0 0 0' })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: '2026-09-28 ～ 2026-09-30 0 0 0' })).toBeInTheDocument();
  }
});

it('sorts channels by resume count and combines only rows outside the top three', () => {
  render(<ChannelDonut rows={[1, 8, 2, 6, 3].map((resumes, index) => ({ id: String(index), name: `渠道${index}`, resumes, applications: 0, offers: 0, onboarded: 0 }))} />);
  expect(screen.getAllByRole('listitem').map(row => row.textContent)).toEqual(['渠道18', '渠道36', '渠道43', '其它3']);
  expect(charts.setOption).toHaveBeenCalledWith(expect.objectContaining({ series: [expect.objectContaining({ data: [{ name: '渠道1', value: 8 }, { name: '渠道3', value: 6 }, { name: '渠道4', value: 3 }, { name: '其它', value: 3 }] })] }), { replaceMerge: ['series'] });
});

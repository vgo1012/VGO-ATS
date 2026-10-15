import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { MatchingWorkspacePage } from './MatchingWorkspacePage';

const mocks = vi.hoisted(() => ({ run: vi.fn(), results: vi.fn() }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'user', permissions: [] } }) }));
vi.mock('../matching/useScreeningJobs', () => ({ useScreeningJobs: () => ({ data: { pages: [] }, isFetching: false }) }));
vi.mock('../matching/ScreeningJobHeader', () => ({ ScreeningJobHeader: () => null }));
vi.mock('../matching/WeightProfiles', () => ({ WeightProfiles: () => null }));
vi.mock('../demand/api', () => ({ demandApi: { scoringDefaults: () => Promise.resolve(null), weightProfiles: () => Promise.resolve({ items: [] }) } }));
vi.mock('../talent/api', () => ({ talentApi: { screeningCandidates: () => Promise.resolve([]) } }));
vi.mock('../matching/api', () => ({ matchingApi: { run: mocks.run, results: mocks.results } }));
vi.mock('../tasks/task', () => ({ taskApi: { get: () => Promise.resolve({ id: 'task', status: 'FAILED', retryable: false, progressPercent: 70 }) }, taskPollingInterval: () => false }));
const originalRun = { id: 'personal-run', jdVersionId: 'job', taskId: 'task', status: 'PARTIAL', succeededCount: 7, failedCount: 3, totalCount: 10 };
const page = { items: [], hasMore: true, nextCursor: 'second-cursor' };
const restoredRun = { ...originalRun, succeededCount: 8, failedCount: 2 };
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '匹配读取失败', detail: '暂不可读取', recoverable: status === 503 });
const key = (source: string) => source === 'run' ? ['matching', 'run', 'personal-run'] : ['matching', 'results', 'personal-run'];
beforeEach(() => { mocks.run.mockReset().mockResolvedValue(originalRun); mocks.results.mockReset().mockResolvedValue(page); });
afterEach(() => { cleanup(); });
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MemoryRouter initialEntries={['/matching?run=personal-run']}><MatchingWorkspacePage /></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  return client;
}
it.each(['run', 'results'].flatMap(source => [401, 403, 503].map(status => ({ source, status }))))('$source $status only hides unauthorized personal matching data and recovers', async ({ source, status }) => {
  const client = mount();
  await screen.findByRole('button', { name: '全选本次全部结果' });
  expect(screen.getByText('成功 7 / 失败 3 / 共 10')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '返回岗位全部结果' })).not.toBeInTheDocument();
  (source === 'run' ? mocks.run : mocks.results).mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: key(source) }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryByRole('button', { name: '全选本次全部结果' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '下一页结果' })).not.toBeInTheDocument();
    if (source === 'run') {
      expect(screen.queryByText('成功 7 / 失败 3 / 共 10')).not.toBeInTheDocument();
      expect(screen.queryByLabelText('候选人匹配进度')).not.toBeInTheDocument();
    } else {
      expect(screen.getByText('成功 7 / 失败 3 / 共 10')).toBeInTheDocument();
    }
    expect(screen.getByText('暂无匹配进度或结果的查看权限')).toBeInTheDocument();
  } else expect(screen.getByRole('button', { name: '全选本次全部结果' })).toBeInTheDocument();
  mocks.run.mockResolvedValue(restoredRun); mocks.results.mockResolvedValue(page);
  fireEvent.click(await screen.findByRole('button', { name: '重试匹配结果' }));
  await screen.findByText('成功 8 / 失败 2 / 共 10');
  await screen.findByRole('button', { name: '全选本次全部结果' });
}, 15_000);

it.each(['run', 'results'])('first %s failure is recoverable instead of an endless results skeleton', async source => {
  if (source === 'results') mocks.run.mockResolvedValue({ ...originalRun, status: 'SUCCEEDED' });
  (source === 'run' ? mocks.run : mocks.results).mockRejectedValue(problem(503));
  mount();
  const retry = await screen.findByRole('button', { name: '重试匹配结果' });
  expect(screen.queryByLabelText('正在加载智能筛选')).not.toBeInTheDocument();
  mocks.run.mockResolvedValue({ ...restoredRun, status: 'SUCCEEDED' }); mocks.results.mockResolvedValue(page);
  fireEvent.click(retry);
  await screen.findByRole('button', { name: '全选本次全部结果' });
}, 15_000);

it('keeps the second result cursor and disables duplicate slow recovery', async () => {
  const client = mount();
  await screen.findByRole('button', { name: '全选本次全部结果' });
  fireEvent.click(screen.getByRole('button', { name: '下一页结果' }));
  await screen.findByText('本次成功 7 / 共 10');
  expect(screen.getByRole('spinbutton',{name:'页码'})).toHaveValue(2);
  mocks.results.mockRejectedValue(problem(503));
  await act(() => client.invalidateQueries({ queryKey: key('results') }));
  let resolve!: (value: typeof page) => void;
  mocks.results.mockImplementation(() => new Promise(done => { resolve = done; }));
  const before = mocks.results.mock.calls.length;
  const retry = await screen.findByRole('button', { name: '重试匹配结果' });
  fireEvent.click(retry); fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  await waitFor(() => expect(mocks.results).toHaveBeenCalledTimes(before + 1));
  expect(mocks.results).toHaveBeenLastCalledWith('personal-run', 'second-cursor', 20);
  await act(() => { resolve(page); return Promise.resolve(); });
  await screen.findByText('本次成功 7 / 共 10');
  expect(screen.getByRole('spinbutton',{name:'页码'})).toHaveValue(2);
  expect(screen.queryByRole('button', { name: '重试匹配结果' })).not.toBeInTheDocument();
}, 15_000);

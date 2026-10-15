import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { MatchingWorkspacePage } from './MatchingWorkspacePage';

const readTask = vi.hoisted(() => vi.fn());
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'user', permissions: [] } }) }));
vi.mock('../matching/useScreeningJobs', () => ({ useScreeningJobs: () => ({ data: { pages: [] }, isFetching: false }) }));
vi.mock('../matching/ScreeningJobHeader', () => ({ ScreeningJobHeader: () => null }));
vi.mock('../matching/WeightProfiles', () => ({ WeightProfiles: () => null }));
vi.mock('../demand/api', () => ({ demandApi: { scoringDefaults: () => Promise.resolve(null), weightProfiles: () => Promise.resolve({ items: [] }) } }));
vi.mock('../talent/api', () => ({ talentApi: { screeningCandidates: () => Promise.resolve([]) } }));
vi.mock('../matching/api', () => ({ matchingApi: {
  run: () => Promise.resolve({ id: 'personal-run', jdVersionId: 'job', taskId: 'task', status: 'PARTIAL', succeededCount: 7, failedCount: 3, totalCount: 10 }),
  results: () => Promise.resolve({ items: [], hasMore: false, nextCursor: null }),
} }));
vi.mock('../tasks/task', () => ({ taskApi: { get: readTask }, taskPollingInterval: () => false }));
const original = { id: 'task', status: 'FAILED', retryable: true, progressPercent: 73 };
const restored = { ...original, progressPercent: 81 };
const key = ['tasks', 'task'];
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '任务读取失败', detail: '暂不可读取', recoverable: status === 503 });
beforeEach(() => { readTask.mockReset().mockResolvedValue(original); });
afterEach(() => { cleanup(); });
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MemoryRouter initialEntries={['/matching?run=personal-run']}><MatchingWorkspacePage /></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  return client;
}
function authorizedResultsRemain() {
  expect(screen.getByText('成功 7 / 失败 3 / 共 10')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '返回岗位全部结果' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '全选本次全部结果' })).toBeInTheDocument();
}

it.each([401, 403, 503])('task %s hides only denied progress and task actions and restores the same task', async status => {
  const client = mount();
  await screen.findByRole('button', { name: '全选本次全部结果' });
  await waitFor(() => expect(screen.getByLabelText('候选人匹配进度')).toHaveAttribute('aria-valuenow', '73'));
  expect(screen.getByRole('button', { name: '仅重试失败项' })).toBeInTheDocument();
  readTask.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: key }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryByLabelText('候选人匹配进度')).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '仅重试失败项' })).not.toBeInTheDocument();
    expect(screen.getByText('暂无任务进度的查看权限')).toBeInTheDocument();
  } else expect(screen.getByLabelText('候选人匹配进度')).toHaveAttribute('aria-valuenow', '73');
  authorizedResultsRemain();
  readTask.mockResolvedValue(restored);
  fireEvent.click(await screen.findByRole('button', { name: '重试任务进度' }));
  await waitFor(() => expect(screen.getByLabelText('候选人匹配进度')).toHaveAttribute('aria-valuenow', '81'));
  expect(screen.getByRole('button', { name: '仅重试失败项' })).toBeInTheDocument();
  expect(readTask.mock.calls.every(args => args.length === 1 && args[0] === 'task')).toBe(true);
}, 15_000);

it('hides cancellation from a cached running task after permission denial', async () => {
  readTask.mockResolvedValue({ ...original, status: 'RUNNING', retryable: false });
  const client = mount();
  await screen.findByRole('button', { name: /^取\s*消$/ });
  await screen.findByRole('button', { name: '全选本次全部结果' });
  readTask.mockRejectedValue(problem(403));
  await act(() => client.invalidateQueries({ queryKey: key }));
  await waitFor(() => expect(screen.queryByRole('button', { name: /^取\s*消$/ })).not.toBeInTheDocument());
  expect(screen.queryByLabelText('候选人匹配进度')).not.toBeInTheDocument();
  authorizedResultsRemain();
}, 15_000);

it('first task failure never fabricates zero progress and keeps slow recovery disabled', async () => {
  readTask.mockRejectedValue(problem(503));
  mount();
  const retry = await screen.findByRole('button', { name: '重试任务进度' });
  await screen.findByRole('button', { name: '全选本次全部结果' });
  expect(screen.queryByLabelText('候选人匹配进度')).not.toBeInTheDocument();
  authorizedResultsRemain();
  let resolve!: (value: typeof restored) => void;
  readTask.mockImplementation(() => new Promise(done => { resolve = done; }));
  const before = readTask.mock.calls.length;
  fireEvent.click(retry); fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  expect(retry).toBeInTheDocument();
  expect(readTask).toHaveBeenCalledTimes(before + 1);
  await act(() => { resolve(restored); return Promise.resolve(); });
  await waitFor(() => expect(screen.getByLabelText('候选人匹配进度')).toHaveAttribute('aria-valuenow', '81'));
}, 15_000);

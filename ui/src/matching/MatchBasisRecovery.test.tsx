import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { MatchJobWorkspace } from './MatchJobWorkspace';

const mocks = vi.hoisted(() => ({ run: vi.fn(), results: vi.fn() }));
vi.mock('./api', () => ({ matchingApi: { jobs: () => Promise.resolve({ items: [] }), run: mocks.run, jobResults: mocks.results, jobResultsSummary: () => Promise.resolve({total:41,strong:0,consider:0,reject:0,missing:41}) } }));
const run = { id: 'basis', jdVersionId: 'job', status: 'SUCCEEDED' };
const first = { items: [{ result: { resultId: 'first', candidateId: '首批候选人' } }], hasMore: true, nextCursor: 'cursor-two' };
const second = { items: [{ result: { resultId: 'second', candidateId: '第二页候选人' } }], hasMore: true, nextCursor: 'cursor-three' };
const restored = { ...second, items: [{ result: { resultId: 'restored', candidateId: '恢复候选人' } }] };
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '读取失败', detail: '结果暂不可用', recoverable: status === 503 });
beforeEach(() => {
  mocks.run.mockReset().mockResolvedValue(run);
  mocks.results.mockReset().mockImplementation((_job: string, _basis: string, cursor?: string) => Promise.resolve(cursor ? second : first));
});
afterEach(() => { cleanup(); });
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MatchJobWorkspace jdVersionId="job" basisRunId="basis" schemeLabel="个人方案" renderResults={items => <section aria-label="评分列表">{items.map(item => <div key={item.result.resultId}>{item.result.candidateId}<button>推荐此候选人</button></div>)}</section>} /></QueryClientProvider></App></ConfigProvider>);
  return client;
}
const queryKey = (source: string) => source === 'run' ? ['matching', 'run', 'basis'] : ['matching', 'job-results', 'job', 'basis'];

it.each(['run', 'results'].flatMap(source => [401, 403, 503].map(status => ({ source, status }))))('$source $status hides denied cached results and actions but preserves transient failure data', async ({ source, status }) => {
  const client = mount();
  await screen.findByText('首批候选人');
  const failing = source === 'run' ? mocks.run : mocks.results;
  failing.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: queryKey(source) }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryByText('首批候选人')).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '推荐此候选人' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '下一页结果' })).not.toBeInTheDocument();
    expect(screen.queryByText(/第 1 页/)).not.toBeInTheDocument();
    expect(screen.getByText('暂无岗位结果的查看权限')).toBeInTheDocument();
  } else {
    expect(screen.getByText('首批候选人')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '推荐此候选人' })).toBeInTheDocument();
  }
  mocks.run.mockResolvedValue(run);
  mocks.results.mockResolvedValue(restored);
  fireEvent.click(await screen.findByRole('button', { name: '重试岗位结果' }));
  await screen.findByText('恢复候选人');
  expect(screen.getByRole('button', { name: '推荐此候选人' })).toBeInTheDocument();
}, 15_000);

it('retries a failed second page at its current cursor without loading the first page again', async () => {
  const client = mount();
  await screen.findByText('首批候选人');
  fireEvent.click(screen.getByRole('button', { name: '下一页结果' }));
  await screen.findByText('第二页候选人');
  mocks.results.mockRejectedValue(problem(503));
  await act(() => client.invalidateQueries({ queryKey: queryKey('results') }));
  expect(screen.getByText('第二页候选人')).toBeInTheDocument();
  const firstReads = mocks.results.mock.calls.filter(args => args[2] === undefined).length;
  mocks.results.mockImplementation((_job: string, _basis: string, cursor?: string) => Promise.resolve(cursor ? restored : first));
  fireEvent.click(await screen.findByRole('button', { name: '重试岗位结果' }));
  await screen.findByText('恢复候选人');
  expect(screen.getByRole('spinbutton',{name:'页码'})).toHaveValue(2);
  expect(mocks.results.mock.calls.filter(args => args[2] === undefined)).toHaveLength(firstReads);
  expect(mocks.results).toHaveBeenLastCalledWith('job', 'basis', 'cursor-two', 20);
}, 15_000);

it.each([false, true])('disables a shared slow recovery and sends each failed read only once (initial failure: %s)', async initialFailure => {
  if (initialFailure) { mocks.run.mockRejectedValue(problem(503)); mocks.results.mockRejectedValue(problem(503)); }
  const client = mount();
  if (!initialFailure) {
    await screen.findByText('首批候选人');
    mocks.run.mockRejectedValue(problem(503)); mocks.results.mockRejectedValue(problem(503));
    await act(() => Promise.all([client.invalidateQueries({ queryKey: queryKey('run') }), client.invalidateQueries({ queryKey: queryKey('results') })]));
  } else {
    await waitFor(() => { expect(client.getQueryState(queryKey('run'))?.status).toBe('error'); expect(client.getQueryState([...queryKey('results'), undefined, 20])?.status).toBe('error'); });
  }
  let resolveRun!: (value: typeof run) => void;
  let resolveResults!: (value: typeof restored) => void;
  mocks.run.mockImplementation(() => new Promise(done => { resolveRun = done; }));
  mocks.results.mockImplementation(() => new Promise(done => { resolveResults = done; }));
  const beforeRun = mocks.run.mock.calls.length;
  const beforeResults = mocks.results.mock.calls.length;
  const retry = await screen.findByRole('button', { name: '重试岗位结果' });
  fireEvent.click(retry); fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  expect(retry).toBeInTheDocument();
  expect(mocks.run).toHaveBeenCalledTimes(beforeRun + 1);
  expect(mocks.results).toHaveBeenCalledTimes(beforeResults + 1);
  await act(() => { resolveRun(run); resolveResults(restored); return Promise.resolve(); });
  await screen.findByText('恢复候选人');
}, 15_000);

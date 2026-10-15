import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { MatchJobWorkspace } from './MatchJobWorkspace';
import type { ScoringBasisSelection } from './selectScoringBasis';

const selectBasis = vi.hoisted(() => vi.fn());
vi.mock('./selectScoringBasis', () => ({ selectScoringBasis: selectBasis }));
vi.mock('./api', () => ({ matchingApi: {
  jobs: () => Promise.resolve({ items: [] }),
  run: (id: string) => Promise.resolve({ id, jdVersionId: 'job', status: 'SUCCEEDED' }),
  jobResultsSummary: () => Promise.resolve({total:0,strong:0,consider:0,reject:0,missing:0}),
  jobResults: () => Promise.resolve({ items: [], hasMore: false, nextCursor: null }),
} }));
const selection: ScoringBasisSelection = {
  planVersionId: 'plan', selectedCriterionIds: [], sortMode: 'REFERENCE_SCORE',
  profile: { profile: { id: 'profile', ownerUserId: 'hr', name: '个人', status: 'ACTIVE', currentVersionNo: 1, version: 1, updatedAt: '' }, currentVersion: { id: 'weights', versionNo: 1, skillsWeight: 47, roleWeight: 18, yearsWeight: 21, responsibilityWeight: 14, industryProjectWeight: 0 } },
};
const key = ['matching', 'scheme-basis', 'job', selection];
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '依据失败', detail: '判断依据暂不可用', recoverable: status === 503 });
beforeEach(() => { selectBasis.mockReset().mockResolvedValue('auto-old'); });
afterEach(() => { cleanup(); });
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const content = (basisRunId?: string, basisSelection = selection) => <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MatchJobWorkspace jdVersionId="job" basisSelection={basisSelection} basisRunId={basisRunId} schemeLabel="个人方案" renderResults={(_items, run) => <section><div>{run.id}候选人<button>推荐候选人</button></div></section>} /></QueryClientProvider></App></ConfigProvider>;
  const view = render(content());
  return { client, explicit: () => view.rerender(content('history')), changeSelection: (value: ScoringBasisSelection) => view.rerender(content(undefined, value)) };
}

it('uses a cached scoring basis without requesting basis selection again', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(key, 'cached-run');
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><MatchJobWorkspace jdVersionId="job" basisSelection={selection} schemeLabel="个人方案" renderResults={(_items, run) => <span>{run.id}候选人</span>} /></QueryClientProvider></App></ConfigProvider>);
  await screen.findByText('cached-run候选人');
  expect(selectBasis).not.toHaveBeenCalled();
});

it.each([401, 403, 503])('automatic basis %s protects the cached selection and restores fresh results', async status => {
  const { client } = mount();
  await screen.findByText('auto-old候选人');
  selectBasis.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: key }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryByText('auto-old候选人')).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '推荐候选人' })).not.toBeInTheDocument();
    expect(screen.queryByText('暂无个人方案的评分结果')).not.toBeInTheDocument();
    expect(screen.getByText('暂无判断依据的查看权限')).toBeInTheDocument();
  } else expect(screen.getByText('auto-old候选人')).toBeInTheDocument();
  selectBasis.mockResolvedValue('auto-new');
  fireEvent.click(await screen.findByRole('button', { name: '重试判断依据' }));
  await screen.findByText('auto-new候选人');
  expect(screen.queryByText('auto-old候选人')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '推荐候选人' })).toBeInTheDocument();
}, 15_000);

it('an explicit historical basis ignores a denied automatic selection and does not retry it', async () => {
  const { client, explicit } = mount();
  await screen.findByText('auto-old候选人');
  selectBasis.mockRejectedValue(problem(403));
  await act(() => client.invalidateQueries({ queryKey: key }));
  await screen.findByRole('button', { name: '重试判断依据' });
  const before = selectBasis.mock.calls.length;
  explicit();
  await screen.findByText('history候选人');
  expect(screen.queryByRole('button', { name: '重试判断依据' })).not.toBeInTheDocument();
  expect(screen.queryByText('暂无判断依据的查看权限')).not.toBeInTheDocument();
  await act(() => client.invalidateQueries({ queryKey: key }));
  expect(selectBasis).toHaveBeenCalledTimes(before);
}, 15_000);

it('disables a pending automatic basis retry and keeps the cached results', async () => {
  const { client } = mount();
  await screen.findByText('auto-old候选人');
  selectBasis.mockRejectedValue(problem(503));
  await act(() => client.invalidateQueries({ queryKey: key }));
  let resolve!: (value: string) => void;
  selectBasis.mockImplementation(() => new Promise(done => { resolve = done; }));
  const before = selectBasis.mock.calls.length;
  const retry = await screen.findByRole('button', { name: '重试判断依据' });
  fireEvent.click(retry); fireEvent.click(retry);
  await waitFor(() => expect(retry).toBeDisabled());
  expect(selectBasis).toHaveBeenCalledTimes(before + 1);
  expect(screen.getByText('auto-old候选人')).toBeInTheDocument();
  await act(() => { resolve('auto-new'); return Promise.resolve(); });
  await screen.findByText('auto-new候选人');
}, 15_000);

it('keeps the first failed read retry visible across equivalent selection renders', async () => {
  selectBasis.mockRejectedValue(problem(503));
  const { changeSelection } = mount();
  const retry = await screen.findByRole('button', { name: '重试判断依据' });
  let resolve!: (value: string) => void;
  selectBasis.mockImplementation(() => new Promise(done => { resolve = done; }));
  fireEvent.click(retry);
  changeSelection({ ...selection });
  await waitFor(() => { expect(retry).toBeInTheDocument(); expect(retry).toBeDisabled(); });
  expect(retry).toHaveAttribute('aria-busy', 'true');
  fireEvent.click(retry);
  expect(selectBasis).toHaveBeenCalledTimes(2);
  await act(() => { resolve('recovered'); return Promise.resolve(); });
  await screen.findByText('recovered候选人');
  expect(screen.queryByRole('button', { name: '重试判断依据' })).not.toBeInTheDocument();
});

it('does not carry a pending recovery into another scoring selection', async () => {
  selectBasis.mockRejectedValue(problem(503));
  const { changeSelection } = mount();
  const retry = await screen.findByRole('button', { name: '重试判断依据' });
  let resolve!: (value: string) => void;
  selectBasis.mockImplementationOnce(() => new Promise(done => { resolve = done; })).mockResolvedValue('other-selection');
  fireEvent.click(retry);
  changeSelection({ ...selection, planVersionId: 'other-plan' });
  await screen.findByText('other-selection候选人');
  expect(screen.queryByRole('button', { name: '重试判断依据' })).not.toBeInTheDocument();
  await act(() => { resolve('old-selection'); return Promise.resolve(); });
  expect(screen.getByText('other-selection候选人')).toBeInTheDocument();
  expect(screen.queryByText('old-selection候选人')).not.toBeInTheDocument();
});

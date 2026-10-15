import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import type { JdSummary, JdVersion } from '../demand/api';
import { ScreeningJobHeader } from './ScreeningJobHeader';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: null }) }));

const readFacts = vi.hoisted(() => vi.fn());
vi.mock('../demand/api', () => ({ demandApi: { jdFacts: readFacts } }));
vi.mock('./useScreeningJobs', () => ({ useScreeningJobs: () => ({ data: { pages: [] }, isFetching: false, isPending: false }) }));

const jd: JdSummary = { id: 'jd', title: '授权招聘岗位', status: 'ACTIVE', currentVersionNo: 3, contentShared: false, appliedFromJdVersionId: null, clientId: null, clientName: null, ownerUserId: 'owner', ownerDepartmentId: 'department', createTime: '2026-09-01T00:00:00Z' };
const version: JdVersion = { id: 'jd-version-3', jdId: 'jd', versionNo: 3, title: jd.title, rawText: '独立授权岗位原文', status: 'PUBLISHED', sourceType: 'MANUAL', createId: 'owner', createTime: jd.createTime };
const original = { facts: { summary: '敏感旧岗位摘要', professional: { minimumYears: 7, coreSkills: [{ id: 'skill', name: '敏感旧技能' }], responsibilities: ['敏感旧岗位职责'] }, controlledConditions: { educations: ['MASTER'], ageMin: 28, ageMax: 40 } } };
const restored = { facts: { ...original.facts, summary: '恢复新岗位摘要', professional: { ...original.facts.professional, coreSkills: [{ id: 'new-skill', name: '恢复新技能' }], responsibilities: ['恢复新岗位职责'] } } };
const problem = (status: number) => new ApiProblemError({ title: '岗位事实失败', detail: '暂时无法读取岗位事实', status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', recoverable: status === 503 });
beforeEach(() => { readFacts.mockReset().mockResolvedValue(original); });
afterEach(() => { cleanup(); });

function mount(rawText = version.rawText) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><ScreeningJobHeader jd={jd} version={{ ...version, rawText }} loading={false} disabled={false} onChange={vi.fn()} schemeControls={null} onRescore={vi.fn()} rescoreDisabled weights={[]} /></QueryClientProvider></App></ConfigProvider>);
  return client;
}
async function openDetails() {
  fireEvent.click(screen.getByRole('button', { name: 'JD 详情' }));
  return screen.findByRole('dialog', { name: '授权招聘岗位 · JD 详情' });
}

it.each([401, 403, 503])('facts %s restricts only its own cached fields and retries the selected version', async status => {
  const client = mount();
  await screen.findByText('敏感旧技能');
  const dialog = await openDetails();
  expect(within(dialog).getByText('敏感旧岗位职责')).toBeInTheDocument();
  readFacts.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-facts', version.id] }));
  if (status !== 503) {
    await waitFor(() => expect(screen.queryAllByText('敏感旧岗位摘要')).toHaveLength(0));
    for (const text of ['敏感旧技能', '敏感旧岗位职责', '硕士', '7年以上', '年龄≥28', '年龄≤40']) expect(screen.queryAllByText(text)).toHaveLength(0);
    expect(within(dialog).getByText('暂无岗位事实的查看权限')).toBeInTheDocument();
  } else {
    for (const text of ['敏感旧岗位摘要', '敏感旧技能', '敏感旧岗位职责', '硕士']) expect(screen.getAllByText(text).length).toBeGreaterThan(0);
  }
  expect(within(dialog).getByText('独立授权岗位原文')).toBeInTheDocument();
  readFacts.mockResolvedValue(restored);
  fireEvent.click(await within(dialog).findByRole('button', { name: '重试岗位事实' }));
  await within(dialog).findByText('恢复新岗位摘要');
  expect(within(dialog).getAllByText('恢复新技能').length).toBeGreaterThan(0);
  expect(within(dialog).getByText('恢复新岗位职责')).toBeInTheDocument();
  expect(readFacts.mock.calls.every(args => args.length === 1 && args[0] === version.id)).toBe(true);
}, 15_000);

it('first failed facts read offers recovery without claiming the summary is empty', async () => {
  readFacts.mockRejectedValue(problem(503));
  const client = mount('');
  expect(screen.getByRole('group', { name: '评分配置与操作' })).toContainElement(screen.getByRole('button', { name: '重新评分' }));
  await waitFor(() => expect(client.getQueryState(['demand', 'jd-facts', version.id])?.status).toBe('error'));
  await waitFor(() => expect(screen.queryByText('暂无岗位摘要，可在 JD 详情中核对原文。')).not.toBeInTheDocument());
  const retry = await screen.findByRole('button', { name: '重试岗位事实' });
  expect(screen.getByText('岗位事实读取失败')).toBeInTheDocument();
  let resolve!: (value: typeof restored) => void;
  readFacts.mockImplementation(() => new Promise(done => { resolve = done; }));
  fireEvent.click(retry);
  await waitFor(() => { expect(retry).toBeInTheDocument(); expect(retry).toBeDisabled(); });
  expect(retry).toHaveAttribute('aria-busy', 'true');
  fireEvent.click(retry);
  expect(readFacts).toHaveBeenCalledTimes(2);
  await act(() => { resolve(restored); return Promise.resolve(); });
  await screen.findByText('恢复新技能');
}, 15_000);

it('shares one pending recovery between the header and open details', async () => {
  const client = mount();
  await screen.findByText('敏感旧技能');
  const dialog = await openDetails();
  readFacts.mockRejectedValue(problem(503));
  await act(() => client.invalidateQueries({ queryKey: ['demand', 'jd-facts', version.id] }));
  const retry = await within(dialog).findByRole('button', { name: '重试岗位事实' });
  const headerRetry = screen.getAllByRole('button', { name: '重试岗位事实' }).find(button => !dialog.contains(button));
  expect(headerRetry).toBeDefined();
  let resolve!: (value: typeof restored) => void;
  readFacts.mockImplementation(() => new Promise(done => { resolve = done; }));
  const before = readFacts.mock.calls.length;
  fireEvent.click(retry);
  fireEvent.click(headerRetry!);
  await waitFor(() => { expect(retry).toBeDisabled(); expect(headerRetry).toBeDisabled(); });
  expect(readFacts).toHaveBeenCalledTimes(before + 1);
  expect(readFacts).toHaveBeenLastCalledWith(version.id);
  await act(() => { resolve(restored); return Promise.resolve(); });
  await within(dialog).findByText('恢复新岗位摘要');
}, 15_000);

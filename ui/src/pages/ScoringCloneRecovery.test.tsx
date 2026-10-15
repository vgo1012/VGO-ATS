import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { ApiProblemError } from '../api/problem';
import { ScoringGovernancePage } from './ScoringGovernancePage';

vi.mock('../api/http', () => ({ request: vi.fn() }));
const active = { plan: { id: 'plan', name: '公司评分' }, version: { id: 'v1', versionNo: 1, version: 1, status: 'ACTIVE', skillsEnabled: true, roleEnabled: true, yearsEnabled: true, responsibilityEnabled: true, industryProjectEnabled: true }, criterionPlan: { planMaxBonusPoints: 0 }, criteria: [] };
const draft = { ...active, version: { ...active.version, id: 'v2', versionNo: 2, status: 'DRAFT' } };
const posts = () => vi.mocked(request).mock.calls.filter(([, options]) => options?.method === 'POST');
beforeEach(() => vi.mocked(request).mockReset());
afterEach(cleanup);
function mount(failures: Error[] = [new SyntaxError('响应丢失')]) {
  let attempts = 0;
  vi.mocked(request).mockImplementation((path, options) => {
    if (options?.method === 'POST') {
      const error = failures[attempts++];
      return error ? Promise.reject(error) : Promise.resolve(draft);
    }
    if (path === '/scoring-defaults') return Promise.resolve({ scoringConfiguration: active });
    return Promise.resolve({ items: attempts ? [draft, active] : [active], hasMore: false, nextCursor: null });
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<MemoryRouter><ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><ScoringGovernancePage /></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  return client;
}
async function loseResponse() {
  fireEvent.click(await screen.findByRole('button', { name: '创建 V2 草稿' }));
  await screen.findByRole('button', { name: /^重\s*试$/ });
}

it('retries the original clone key and source after the list learns about the saved draft', async () => {
  const client = mount();
  await loseResponse();
  await client.invalidateQueries({ queryKey: ['matching', 'scoring-plans'] });
  fireEvent.click(await screen.findByRole('button', { name: /创建 V3 草稿|重试创建草稿/ }));
  await screen.findByRole('dialog', { name: '编辑评分方案 · V2' });
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[1]?.[0]).toBe(calls[0]?.[0]);
  expect(calls[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).toBe(calls[0]?.[1]?.idempotencyKey);
}, 15_000);

it.each([400, 403, 409, 422])('releases other operations after an initial explicit %s rejection', async status => {
  const client = mount([new ApiProblemError({ status, title: '拒绝', detail: '创建已拒绝', code: 'REJECTED', recoverable: true })]);
  await loseResponse();
  await client.invalidateQueries({ queryKey: ['matching', 'scoring-plans'] });
  expect(await screen.findByRole('button', { name: /^编\s*辑$/ })).toBeEnabled();
  expect(screen.getByRole('button', { name: /^激\s*活$/ })).toBeEnabled();
  expect(screen.getByRole('button', { name: '复制为下一版' })).toBeEnabled();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await screen.findByRole('dialog', { name: '编辑评分方案 · V2' });
  expect(posts()[1]?.[0]).toBe(posts()[0]?.[0]);
  expect(posts()[1]?.[1]?.idempotencyKey).not.toBe(posts()[0]?.[1]?.idempotencyKey);
}, 15_000);

it('does not discard an uncertain operation when its retry is temporarily forbidden', async () => {
  mount([new SyntaxError('响应丢失'), new ApiProblemError({ status: 403, title: '拒绝', detail: '暂无权限', code: 'FORBIDDEN', recoverable: true })]);
  await loseResponse();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await screen.findByText('暂无权限');
  await waitFor(() => expect(screen.getByRole('button', { name: /^重\s*试$/ })).toBeEnabled());
  expect(screen.getByRole('button', { name: '复制为下一版' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await screen.findByRole('dialog', { name: '编辑评分方案 · V2' });
  expect(posts()).toHaveLength(3);
  expect(new Set(posts().map(([, options]) => options?.idempotencyKey)).size).toBe(1);
}, 15_000);

it('uses the error recovery button to retry creation instead of only refreshing the list', async () => {
  mount();
  await loseResponse();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await waitFor(() => expect(posts()).toHaveLength(2));
  await screen.findByRole('dialog', { name: '编辑评分方案 · V2' });
}, 15_000);

it('blocks conflicting edits while creation is uncertain and releases the key after success', async () => {
  const client = mount();
  await loseResponse();
  await client.invalidateQueries({ queryKey: ['matching', 'scoring-plans'] });
  expect(await screen.findByRole('button', { name: /^编\s*辑$/ })).toBeDisabled();
  expect(screen.getByRole('button', { name: /^激\s*活$/ })).toBeDisabled();
  expect(screen.getByRole('button', { name: '复制为下一版' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: /^重\s*试$/ }));
  await screen.findByRole('dialog', { name: '编辑评分方案 · V2' });
  fireEvent.click(screen.getByRole('button', { name: /^(Cancel|取\s*消)$/ }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: '创建 V3 草稿' }));
  await waitFor(() => expect(posts()).toHaveLength(3));
  expect(posts()[2]?.[0]).toBe('/admin/jd-scoring-configs/v2/clone');
  expect(posts()[2]?.[1]?.idempotencyKey).not.toBe(posts()[0]?.[1]?.idempotencyKey);
}, 15_000);

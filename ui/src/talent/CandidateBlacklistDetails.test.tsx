import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CandidateBlacklistDetails } from './CandidateBlacklistDetails';
import { submissionApi, type RestrictionSummary } from '../submission/api';

const session = vi.hoisted(() => ({ allowed: true }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { superAdmin: false, permissions: session.allowed ? ['risk.read'] : [] } }) }));
vi.mock('../submission/api', () => ({ submissionApi: { restrictions: vi.fn() } }));
const record: RestrictionSummary = { restrictionType: 'CLIENT_RESTRICTION', clientId: 'company', clientName: '指定公司', reason: '已核验的标签理由', sourceChannel: 'MANUAL', occurredAt: '2026-09-15T01:00:00Z', expiresAt: null, blocking: false };
const clients: QueryClient[] = [];
function mount(active = true, sharedLibrary = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  clients.push(client);
  return render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><CandidateBlacklistDetails candidateId="candidate" active={active} sharedLibrary={sharedLibrary}/></QueryClientProvider></App></ConfigProvider>);
}
beforeEach(() => { session.allowed = true; vi.mocked(submissionApi.restrictions).mockReset(); });
afterEach(() => { cleanup(); clients.forEach(client => client.clear()); clients.length = 0; });

it('shows the original company and reason as a nonblocking reminder', async () => {
  vi.mocked(submissionApi.restrictions).mockResolvedValue([record]);
  mount();
  expect(await screen.findByText('指定公司')).toBeInTheDocument();
  expect(screen.getByText('已核验的标签理由')).toBeInTheDocument();
  expect(screen.getByText('有效黑名单简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。')).toBeInTheDocument();
  expect(submissionApi.restrictions).toHaveBeenCalledExactlyOnceWith('candidate');
});
it('shows loading without claiming there are no tags', () => {
  vi.mocked(submissionApi.restrictions).mockReturnValue(new Promise(() => {}));
  mount();
  expect(screen.getByRole('status')).toHaveTextContent('正在读取黑名单标签');
  expect(screen.queryByText('暂无黑名单标签')).not.toBeInTheDocument();
});
it('shows the confirmed empty state', async () => {
  vi.mocked(submissionApi.restrictions).mockResolvedValue([]);
  mount();
  expect(await screen.findByText('暂无黑名单标签')).toBeInTheDocument();
});
it('does not request records without permission', () => {
  session.allowed = false;
  mount();
  expect(screen.getByText('暂无黑名单查看权限')).toBeInTheDocument();
  expect(submissionApi.restrictions).not.toHaveBeenCalled();
});
it('reads shared candidate tags for a signed-in user without separate risk permission', async () => {
  session.allowed = false;
  vi.mocked(submissionApi.restrictions).mockResolvedValue([record]);
  mount(true, true);
  expect(await screen.findByText('已核验的标签理由')).toBeInTheDocument();
  expect(screen.queryByText('暂无黑名单查看权限')).not.toBeInTheDocument();
});
it('does not request records while the detail is inactive', () => {
  mount(false);
  expect(submissionApi.restrictions).not.toHaveBeenCalled();
});
it('keeps an error distinct from empty and retries successfully', async () => {
  vi.mocked(submissionApi.restrictions).mockRejectedValueOnce(new Error('offline')).mockResolvedValue([record]);
  mount();
  fireEvent.click(await screen.findByRole('button', { name: '重试标签详情' }));
  expect(await screen.findByText('已核验的标签理由')).toBeInTheDocument();
  await waitFor(() => expect(submissionApi.restrictions).toHaveBeenCalledTimes(2));
  expect(screen.queryByText('暂无黑名单标签')).not.toBeInTheDocument();
});

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { FollowUpPlans } from './FollowUpPlans';
import { followUpPlansApi, type FollowUpPlan } from './followUpPlansApi';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'hr-1' } }) }));
vi.mock('./followUpPlansApi', () => ({ followUpPlansApi: { list: vi.fn() } }));

const plan: FollowUpPlan = {
  id: 'plan-1', applicationId: 'app-1', candidateId: 'candidate-1', jdId: 'jd-1', jdVersionId: 'version-1',
  recruitmentType: 'INTERNAL', clientId: null, clientName: null, ownerUserId: 'hr-1',
  startsAt: '2026-09-09T02:00:00Z', endsAt: '2026-09-09T02:30:00Z', channel: 'PHONE',
  note: '候选人私人沟通事项', status: 'SCHEDULED', version: '1', createTime: '2026-09-08T02:00:00Z', updateTime: '2026-09-08T02:00:00Z',
};

describe('沟通预约读取权限失效', () => {
  afterEach(() => { cleanup(); vi.clearAllMocks(); });

  it.each([401, 403])('刷新返回 %s 后隐藏已有预约内容', async (status) => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.mocked(followUpPlansApi.list).mockResolvedValue({ items: [plan], hasMore: false, nextCursor: null });
    render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><FollowUpPlans applicationId="app-1" canManage={false} /></QueryClientProvider></App></ConfigProvider>);
    expect(await screen.findByText(plan.note)).toBeInTheDocument();
    vi.mocked(followUpPlansApi.list).mockRejectedValue(new ApiProblemError({
      title: '无法访问', status, detail: '当前无法读取沟通预约', code: 'ACCESS_DENIED', recoverable: false,
    }));
    await client.refetchQueries({ queryKey: ['follow-up-plans'] });
    await waitFor(() => expect(screen.queryByText(plan.note)).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: '重试沟通预约' })).toBeInTheDocument();
    client.clear();
  });

  it('待办只展示未完成预约，已沟通与取消的记录归入历史', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    vi.mocked(followUpPlansApi.list).mockResolvedValue({ items: [
      plan, {...plan,id:'done',status:'COMPLETED',note:'已经沟通完成'}, {...plan,id:'cancelled',status:'CANCELLED',note:'已经取消的约定'},
    ], hasMore: false, nextCursor: null });
    render(<ConfigProvider><QueryClientProvider client={client}><FollowUpPlans applicationId="app-1" canManage={false}/></QueryClientProvider></ConfigProvider>);
    expect(await screen.findByText(plan.note)).toBeInTheDocument();
    expect(screen.queryByText('已经沟通完成')).not.toBeInTheDocument();
    expect(screen.queryByText('已经取消的约定')).not.toBeInTheDocument();
    client.clear();
  });
});


it('嵌入跟进区时仅有已完成预约不显示空表或独立说明', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.mocked(followUpPlansApi.list).mockResolvedValue({ items: [{ ...plan, status: 'COMPLETED' }], hasMore: false, nextCursor: null });
  const view = render(<ConfigProvider><QueryClientProvider client={client}><FollowUpPlans embedded applicationId="app-1" canManage /></QueryClientProvider></ConfigProvider>);
  await waitFor(() => expect(client.isFetching()).toBe(0));
  expect(view.container.querySelector('section')).toBeNull();
  expect(view.container.textContent).toBe('');
  view.unmount();
  client.clear();
});

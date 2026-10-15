import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppointmentDetailsDialog } from './AppointmentDetailsDialog';
import { recruitmentApi, type ApplicationDetails } from './api';
import { followUpPlansApi, type FollowUpPlan } from './followUpPlansApi';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'hr' } }) }));
vi.mock('../display/BusinessReferences', () => ({ CandidateName: () => '张三', JobName: () => 'Java 工程师', UserName: () => '招聘专员' }));
vi.mock('./api', () => ({ recruitmentApi: { application: vi.fn() } }));
vi.mock('./followUpPlansApi', () => ({ followUpPlansApi: { finish: vi.fn(), history: vi.fn(), list: vi.fn() }, refreshFollowUpPlans: vi.fn().mockResolvedValue(undefined) }));

const plan: FollowUpPlan = { id: 'plan', applicationId: 'app', candidateId: 'candidate', jdId: 'jd', jdVersionId: 'jv', recruitmentType: 'INTERNAL', clientId: null, clientName: null, ownerUserId: 'hr', startsAt: '2026-01-01T01:00:00Z', endsAt: '2026-01-01T01:30:00Z', channel: 'PHONE', note: '确认到岗意向', status: 'SCHEDULED', version: '2', createTime: '', updateTime: '' };
const details = { application: { id: 'app', ownerUserId: 'hr', terminalAt: null }, collaboratorUserIds: [], workflowSteps: [] } as unknown as ApplicationDetails;
function setup() {
  const onClose = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<ConfigProvider button={{ autoInsertSpace: false }} theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><AppointmentDetailsDialog plan={plan} onClose={onClose} /></QueryClientProvider></App></ConfigProvider>);
  return { onClose, client };
}

describe('沟通预约独立详情', () => {
  beforeEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() });
    vi.mocked(recruitmentApi.application).mockResolvedValue(details);
    vi.mocked(followUpPlansApi.history).mockResolvedValue({ items: [], hasMore: false, nextCursor: null });
    vi.mocked(followUpPlansApi.finish).mockResolvedValue({ ...plan, status: 'CANCELLED' });
  });
  afterEach(() => { cleanup(); vi.clearAllMocks(); });

  it('就地查看候选人与时间并取消选中的预约', async () => {
    const { onClose } = setup();
    expect(await screen.findByText('确认到岗意向')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '取消预约' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认取消' }));
    await waitFor(() => expect(followUpPlansApi.finish).toHaveBeenCalledWith(plan, 'cancel', ''));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it('失败后保留取消原因且可重试，不提前关闭预约', async () => {
    vi.mocked(followUpPlansApi.finish).mockRejectedValueOnce(new Error('预约已更新，请重试'));
    vi.mocked(followUpPlansApi.list).mockResolvedValue({items:[{...plan,version:'3'}],hasMore:false,nextCursor:null});
    const { onClose } = setup();
    fireEvent.click(await screen.findByRole('button', { name: '取消预约' }));
    fireEvent.change(screen.getByRole('textbox', { name: '取消原因（选填）' }), { target: { value: '双方重新约定' } });
    fireEvent.click(screen.getByRole('button', { name: '确认取消' }));
    expect(await screen.findByText('预约已更新，请重试')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '取消原因（选填）' })).toHaveValue('双方重新约定');
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '确认取消' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '核对最新预约' }));
    const retry=await screen.findByRole('button', { name: '按最新版本重试' });
    expect(followUpPlansApi.finish).toHaveBeenCalledOnce();
    fireEvent.click(retry);
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(followUpPlansApi.finish).toHaveBeenLastCalledWith({...plan,version:'3'},'cancel','双方重新约定');
  });

  it('允许显式协作人处理，但不因管理查看范围授予处理权限', async () => {
    vi.mocked(recruitmentApi.application).mockResolvedValue({ ...details, application: { ...details.application, ownerUserId: 'another' }, collaboratorUserIds: ['hr'] });
    const { client } = setup();
    expect(await screen.findByRole('button', { name: '改期' })).toBeInTheDocument();
    vi.mocked(recruitmentApi.application).mockResolvedValue({ ...details, application: { ...details.application, ownerUserId: 'another' } });
    await client.refetchQueries({ queryKey: ['application', 'app'] });
    await waitFor(() => expect(screen.queryByRole('button', { name: '改期' })).not.toBeInTheDocument());
  });

  it('读取失败不暴露旧详情或处理入口，并提供重试', async () => {
    vi.mocked(recruitmentApi.application).mockRejectedValue(new Error('无权读取招聘活动'));
    setup();
    expect(await screen.findByRole('button', { name: '重试预约详情' })).toBeInTheDocument();
    expect(screen.queryByText('确认到岗意向')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '改期' })).not.toBeInTheDocument();
  });
});

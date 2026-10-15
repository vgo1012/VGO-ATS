import { MemoryRouter, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { InterviewWorkspacePage } from './InterviewWorkspacePage';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'hr', permissions: ['recruitment.manage'], roles: [], superAdmin: false } }) }));
vi.mock('../components/WorkspaceDialog', () => ({
  WorkspaceDialogGroup: ({ children }: { children: ReactNode }) => <>{children}</>,
  WorkspaceDialog: ({ open, title, children }: { open: boolean; title: string; children?: ReactNode }) => open ? <div role="dialog" aria-label={title}>{children}</div> : null,
}));
vi.mock('../interview/api', () => ({ interviewApi: { list: vi.fn().mockResolvedValue({ items: [], hasMore: false }), myTasks: vi.fn().mockResolvedValue([]) } }));
vi.mock('../recruitment/api', () => ({ recruitmentApi: { stageCounts: vi.fn().mockResolvedValue([]), application: vi.fn().mockResolvedValue({ application: { ownerUserId: 'hr' }, collaboratorUserIds: [] }) } }));
vi.mock('../recruitment/followUpPlansApi', () => ({ followUpPlansApi: { list: vi.fn().mockResolvedValue({ items: [{ id: 'plan', applicationId: 'a', candidateId: 'c', jdId: 'j', startsAt: new Date().toISOString(), endsAt: new Date(Date.now() + 60000).toISOString(), channel: 'PHONE', note: '约定联系', status: 'SCHEDULED', ownerUserId: 'hr' }], hasMore: false }), history: vi.fn().mockResolvedValue({ items: [], hasMore: false }) } }));
vi.mock('../demand/JdSelector', () => ({ JdSelector: () => null }));
vi.mock('../recruitment/ClientFilter', () => ({ ClientFilter: () => null }));
vi.mock('../display/BusinessReferences', () => ({ CandidateName: () => '候选人', JobName: () => '岗位', UserName: () => 'HR' }));
function Location() { return <output aria-label="当前位置">{useLocation().pathname}</output>; }
function setup() { render(<MemoryRouter initialEntries={['/schedule']}><ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><InterviewWorkspacePage /><Location /></QueryClientProvider></App></ConfigProvider></MemoryRouter>); }
afterEach(cleanup);
beforeEach(() => Object.defineProperty(HTMLElement.prototype, 'scrollTo', { configurable: true, value: vi.fn() }));

it('日程移除标记操作和更多筛选，保留待反馈区域', () => {
  setup();
  expect(screen.queryByRole('button', { name: '预约沟通' })).not.toBeInTheDocument();
  expect(screen.queryByText('周历')).not.toBeInTheDocument();
  expect(screen.queryByText('列表')).not.toBeInTheDocument();
  expect(screen.queryByText('更多筛选')).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '待反馈' })).toBeInTheDocument();
});

it('点击沟通日历卡打开预约详情并留在日程', async () => {
  setup();
  expect(screen.queryByRole('button', { name: '继续跟进' })).not.toBeInTheDocument();
  fireEvent.click(await within(await screen.findByRole('region', { name: '所选周面试日历' })).findByRole('button', { name: /沟通预约/ }));
  expect(await screen.findByRole('dialog', { name: '沟通预约详情' })).toBeInTheDocument();
  for (const name of ['待定', '面试', '放弃', '入职', '黑名单', '第 6 轮面试']) expect(await screen.findByRole('button', { name })).toBeInTheDocument();
  expect(screen.getByLabelText('当前位置')).toHaveTextContent('/schedule');
});

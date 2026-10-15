import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AppShell } from './AppShell';

vi.mock('../talent/BatchParseNotifications',()=>({BatchParseNotifications:()=>null}));

const mocks=vi.hoisted(()=>({logout:vi.fn()}));
vi.mock('../auth/session', () => ({
  useSession: () => ({
    actor: {
      userId: 'admin-1',
      displayName: '管理员',
      workEmail: 'admin@example.com',
      permissions: [],
      superAdmin: true,
      mustChangePassword: false,
    },
    logout: mocks.logout,
  }),
}));

afterEach(()=>{cleanup();mocks.logout.mockReset();});

describe('application shell', () => {
  it('restores the current smart-screening JD after visiting another workspace', async () => {
    function MatchingStub() {
      const location = useLocation();
      const navigate = useNavigate();
      const jdId = (location.state as { jdId?: string } | null)?.jdId;
      return <div><span>{jdId ?? '未选择岗位'}</span><button onClick={() => navigate('/matching?scoreJob=version-2', { state: { jdId: 'job-2' } })}>选择第二岗位</button></div>;
    }
    const router = createMemoryRouter([{ element: <AppShell />, children: [
      { path: '/matching', element: <MatchingStub /> },
      { path: '/recruitment-analysis', element: <div>招聘分析页面</div> },
    ] }], { initialEntries: ['/matching'] });
    render(<ConfigProvider><App><RouterProvider router={router}/></App></ConfigProvider>);

    fireEvent.click(screen.getByRole('button', { name: '选择第二岗位' }));
    await waitFor(() => expect(router.state.location.state).toEqual({ jdId: 'job-2' }));
    const mainNavigation = within(screen.getByRole('navigation', { name: '主导航' }));
    fireEvent.click(mainNavigation.getByRole('button', { name: '招聘洞察' }));
    await screen.findByText('招聘分析页面');
    fireEvent.click(mainNavigation.getByRole('button', { name: 'AI 评估' }));

    await screen.findByText('job-2');
    expect(router.state.location.pathname).toBe('/matching');
    expect(router.state.location.search).toBe('?scoreJob=version-2');
    expect(router.state.location.state).toEqual({ jdId: 'job-2' });
  });

  it('keeps settings and logout in the account menu without a recruitment report entry', async () => {
    const router = createMemoryRouter([{ element: <AppShell />, children: [
      { path: '/recruitment-analysis', element: <div>招聘分析</div> },
    ] }], { initialEntries: ['/recruitment-analysis'] });
    render(<ConfigProvider><App><RouterProvider router={router}/></App></ConfigProvider>);
    const trigger = screen.getByRole('button', { name: '打开管理员的个人菜单' });
    trigger.focus();
    fireEvent.click(trigger);
    expect(screen.getByRole('button', { name: '打开管理员的个人菜单' })).toHaveAttribute('aria-expanded', 'true');
    await waitFor(() => expect(screen.getByText('admin@example.com')).toBeVisible());
    expect(screen.getByText('系统管理员')).toBeVisible();
    expect(await screen.findByRole('menuitem', { name: '工作区设置' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '退出登录' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: '招聘报表' })).not.toBeInTheDocument();
    const settings = screen.getByRole('menuitem', { name: '工作区设置' });
    settings.focus();
    fireEvent.keyDown(settings, { key: 'Escape', keyCode: 27 });
    await waitFor(() => expect(screen.getByRole('button', { name: '打开管理员的个人菜单' })).toHaveAttribute('aria-expanded', 'false'));
    expect(screen.getByRole('button', { name: '打开管理员的个人菜单' })).toHaveFocus();
  });
  it('keeps the workspace visible and reports an unconfirmed logout', async () => {
    mocks.logout.mockRejectedValue(new Error('network lost'));
    const router = createMemoryRouter([{ element: <AppShell />, children: [
      { path: '/recruitment-analysis', element: <div>招聘分析</div> },
    ] }], { initialEntries: ['/recruitment-analysis'] });
    render(<ConfigProvider><App><RouterProvider router={router}/></App></ConfigProvider>);
    fireEvent.click(screen.getByRole('button', { name: '打开管理员的个人菜单' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '退出登录' }));
    expect(await screen.findByText('退出失败，请检查网络后重试。')).toBeVisible();
    expect(router.state.location.pathname).toBe('/recruitment-analysis');
    expect(screen.getByText('招聘分析')).toBeVisible();
  });
  it('does not mark the recruitment ledger active inside recruitment settings', () => {
    const router = createMemoryRouter([{ element: <AppShell />, children: [
      { path: '/recruitment/setup', element: <div>招聘设置</div> },
    ] }], { initialEntries: ['/recruitment/setup'] });
    render(<ConfigProvider><App><RouterProvider router={router}/></App></ConfigProvider>);
    for (const button of screen.getAllByRole('button', { name: /招募管道/ })) {
      expect(button).not.toHaveAttribute('aria-current', 'page');
    }
  });
  it('renders production navigation and an honest empty state', () => {
    const router = createMemoryRouter(
      [
        {
          path: '/',
          element: <AppShell />,
          children: [
            {
              path: 'recruitment-analysis',
              element: <div><h1>招聘分析</h1><p>身份、组织与权限已接入</p></div>,
            },
          ],
        },
      ],
      { initialEntries: ['/recruitment-analysis'] },
    );

    render(
      <ConfigProvider><App>
        <QueryClientProvider client={new QueryClient()}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </App></ConfigProvider>,
    );

    expect(screen.getByRole('heading', { name: '招聘分析' })).toBeInTheDocument();
    const mainNavigation = within(screen.getByRole('navigation', { name: '主导航' }));
    expect(mainNavigation.getByRole('button', { name: '招聘洞察' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: '候选人库' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: 'AI 评估' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: '招募管道' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: '面试日历' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: '人才公海' })).toBeInTheDocument();
    expect(mainNavigation.getByRole('button', { name: '录用交付' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '概览' })).not.toBeInTheDocument();
    expect(screen.getByText('身份、组织与权限已接入')).toBeInTheDocument();
    expect(screen.queryByText(/候选人\s*128/)).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /人才公海/ })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: /录用交付/ })).toHaveLength(2);
  });
});

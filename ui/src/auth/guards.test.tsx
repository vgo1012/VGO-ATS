import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { RequireSession, RequireWorkspaceAccess } from './guards';

const mocks = vi.hoisted(() => ({ actor: null as null | { superAdmin: boolean; permissions: string[]; mustChangePassword: boolean } }));
vi.mock('./session', () => ({ useSession: () => ({ actor: mocks.actor, loading: false, unavailable: false }) }));
beforeEach(() => { mocks.actor = { superAdmin: false, permissions: ['interview.feedback.read-authorized'], mustChangePassword: false }; });
afterEach(cleanup);

it.each(['/talents', '/admin/clients'])('does not mount forbidden %s or start its data queries', (path) => {
  const PrivatePage = vi.fn(() => <div>候选人查询</div>);
  render(<MemoryRouter initialEntries={[path]}><RequireWorkspaceAccess><PrivatePage /></RequireWorkspaceAccess></MemoryRouter>);
  expect(screen.getByText('无权访问')).toBeInTheDocument();
  expect(PrivatePage).not.toHaveBeenCalled();
  expect(screen.getByRole('link', { name: '返回我的工作区' })).toHaveAttribute('href', '/schedule');
});
it('shows a missing page without mounting an unknown protected route', () => {
  const UnknownPage = vi.fn(() => <div>未知页面查询</div>);
  render(<MemoryRouter initialEntries={['/retired-workspace']}><RequireWorkspaceAccess><UnknownPage /></RequireWorkspaceAccess></MemoryRouter>);
  expect(screen.getByText('页面不存在')).toBeInTheDocument();
  expect(UnknownPage).not.toHaveBeenCalled();
});
it('recovers an interviewer opening an old overview bookmark', async () => {
  render(<MemoryRouter initialEntries={['/overview']}><Routes>
    <Route path="/overview" element={<RequireWorkspaceAccess><div>招聘概览查询</div></RequireWorkspaceAccess>} />
    <Route path="/schedule" element={<div>我的日程</div>} />
  </Routes></MemoryRouter>);
  expect(await screen.findByText('我的日程')).toBeInTheDocument();
  expect(screen.queryByText('招聘概览查询')).not.toBeInTheDocument();
});

it('does not preserve the completed password step as a post-login destination', async () => {
  mocks.actor = null;
  const LoginState = () => <div>{(useLocation().state as { from?: string } | null)?.from ?? '无返回地址'}</div>;
  render(<MemoryRouter initialEntries={['/change-password']}><Routes>
    <Route path="/change-password" element={<RequireSession><div>修改密码</div></RequireSession>} />
    <Route path="/login" element={<LoginState />} />
  </Routes></MemoryRouter>);
  expect(await screen.findByText('无返回地址')).toBeInTheDocument();
});

it('preserves the query and fragment of an internal post-login destination', async () => {
  mocks.actor = null;
  const LoginState = () => <div>{(useLocation().state as { from?: string } | null)?.from ?? '无返回地址'}</div>;
  render(<MemoryRouter initialEntries={['/talents?intake=1#upload']}><Routes>
    <Route path="/talents" element={<RequireSession><div>人才库</div></RequireSession>} />
    <Route path="/login" element={<LoginState />} />
  </Routes></MemoryRouter>);
  expect(await screen.findByText('/talents?intake=1#upload')).toBeInTheDocument();
});

it('sends an actor who completed the forced password step to their workspace', async () => {
  render(<MemoryRouter initialEntries={['/change-password']}><Routes>
    <Route path="/change-password" element={<RequireSession><div>修改密码</div></RequireSession>} />
    <Route path="/schedule" element={<div>我的日程</div>} />
  </Routes></MemoryRouter>);
  expect(await screen.findByText('我的日程')).toBeInTheDocument();
  expect(screen.queryByText('修改密码')).not.toBeInTheDocument();
});

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { LoginPage } from './LoginPage';

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  actor: undefined as undefined | { superAdmin: boolean; mustChangePassword: boolean; permissions: string[] },
}));

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: mocks.actor, login: mocks.login }) }));

beforeEach(() => {
  mocks.actor = undefined;
  mocks.login.mockReset();
  window.localStorage.clear();
});
afterEach(cleanup);

it('sends an authenticated interviewer to their usable interview workspace', async () => {
  mocks.actor = { superAdmin: false, mustChangePassword: false, permissions: ['interview.feedback.read-authorized', 'interview.feedback.submit-own'] };
  render(<MemoryRouter initialEntries={['/login']}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/recruitment-analysis" element={<div>无权限的招聘分析</div>} />
    <Route path="/schedule" element={<div>面试官日程</div>} />
  </Routes></MemoryRouter>);
  expect(await screen.findByText('面试官日程')).toBeInTheDocument();
  expect(screen.queryByText('无权限的招聘分析')).not.toBeInTheDocument();
});

it('submits credentials filled by the browser without React change events', async () => {
  mocks.login.mockResolvedValue({ superAdmin: false, mustChangePassword: false, permissions: [] });
  render(<MemoryRouter initialEntries={['/login']}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/recruitment-analysis" element={<div>招聘分析</div>} />
    <Route path="/shared-library" element={<div>共享简历库</div>} />
  </Routes></MemoryRouter>);

  expect(screen.getByText('在浏览器提示时选择保存密码，下次可自动填充。')).toBeInTheDocument();
  const account = screen.getByLabelText('账号');
  const password = screen.getByLabelText('密码');
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.bind(account)('autofilled@example.com');
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.bind(password)('browser-filled-secret');
  fireEvent.submit(screen.getByRole('button', { name: '登录' }).closest('form')!);

  await waitFor(() => expect(mocks.login).toHaveBeenCalledWith({
    workEmail: 'autofilled@example.com',
    password: 'browser-filled-secret',
  }));
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(window.localStorage.getItem('vgo.remembered-work-email')).toBeNull();
  expect(await screen.findByText('共享简历库')).toBeInTheDocument();
});

it('keeps missing credential guidance readable inside the form', () => {
  render(<MemoryRouter><LoginPage /></MemoryRouter>);

  fireEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(screen.getByRole('alert')).toHaveTextContent('请输入账号和密码。');
  expect(mocks.login).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('账号'), { target: { value: 'hr@example.com' } });
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('clears a previous login error when opening account recovery guidance', () => {
  render(<MemoryRouter><LoginPage /></MemoryRouter>);

  fireEvent.click(screen.getByRole('button', { name: '登录' }));
  expect(screen.getByRole('alert')).toHaveTextContent('请输入账号和密码。');
  fireEvent.click(screen.getByRole('button', { name: '忘记密码？' }));
  expect(screen.getByRole('status')).toHaveTextContent('请联系组织管理员重置密码');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: '登录' }));
  fireEvent.click(screen.getByRole('button', { name: '联系管理员' }));
  expect(screen.getByRole('status')).toHaveTextContent('请联系为你分配工作账号的组织管理员');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('replaces help with an authentication error when a pending login fails', async () => {
  let rejectLogin!: (error: unknown) => void;
  mocks.login.mockReturnValue(new Promise((_, reject) => { rejectLogin = reject; }));
  render(<MemoryRouter><LoginPage /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('账号'), { target: { value: 'hr@example.com' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'incorrect' } });
  fireEvent.click(screen.getByRole('button', { name: '登录' }));
  fireEvent.click(screen.getByRole('button', { name: '忘记密码？' }));
  expect(screen.getByRole('status')).toHaveTextContent('请联系组织管理员重置密码');

  await act(async () => { rejectLogin(new ApiProblemError({ title: '认证失败', detail: '凭据不正确', status: 401, code: 'INVALID_CREDENTIALS', recoverable: false })); await Promise.resolve(); });
  expect(screen.getByRole('alert')).toHaveTextContent('凭据不正确');
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});

it('keeps an authentication failure in the form until the user edits it', async () => {
  mocks.login.mockRejectedValue(new ApiProblemError({ title: '认证失败', detail: '凭据不正确', status: 401, code: 'INVALID_CREDENTIALS', recoverable: false }));
  render(<MemoryRouter><LoginPage /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('账号'), { target: { value: 'hr@example.com' } });
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'incorrect' } });

  fireEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('凭据不正确');
  fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'corrected' } });
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

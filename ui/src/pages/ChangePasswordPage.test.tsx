import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { ChangePasswordPage } from './ChangePasswordPage';

const mocks = vi.hoisted(() => ({ changePassword: vi.fn() }));
vi.mock('../auth/session', () => ({ useSession: () => ({ changePassword: mocks.changePassword }) }));

beforeEach(() => mocks.changePassword.mockReset());
afterEach(cleanup);

function renderPage() {
  render(<MemoryRouter initialEntries={['/change-password']}><Routes>
    <Route path="/change-password" element={<ChangePasswordPage />} />
    <Route path="/login" element={<div>请重新登录</div>} />
  </Routes></MemoryRouter>);
}

function submit(newPassword: string, confirmation = newPassword, currentPassword = 'old-password') {
  fireEvent.change(screen.getByLabelText('当前密码'), { target: { value: currentPassword } });
  fireEvent.change(screen.getByLabelText('新密码', { exact: true }), { target: { value: newPassword } });
  fireEvent.change(screen.getByLabelText('确认新密码'), { target: { value: confirmation } });
  fireEvent.submit(screen.getByRole('button', { name: '更新密码' }).closest('form')!);
}

it.each(['a', 'ABC', '123456', 'lowercase', '带空 格'])('accepts a nonblank password without length or character-class restrictions: %s', async (password) => {
  mocks.changePassword.mockResolvedValue(undefined);
  renderPage();
  expect(screen.getByText('请输入新密码，并再次输入以确认。')).toBeInTheDocument();
  submit(password);
  await waitFor(() => expect(mocks.changePassword).toHaveBeenCalledWith({ currentPassword: 'old-password', newPassword: password }));
  expect(await screen.findByText('请重新登录')).toBeInTheDocument();
});

it.each(['', '   '])('rejects an empty or whitespace-only new password', async (password) => {
  renderPage();
  submit(password);
  expect(await screen.findByText('请输入新密码')).toBeInTheDocument();
  expect(mocks.changePassword).not.toHaveBeenCalled();
});

it('requires matching confirmation and the current password', async () => {
  renderPage();
  submit('abc', 'def', '');
  expect(await screen.findByText('两次输入的密码不一致')).toBeInTheDocument();
  expect(await screen.findByText('请输入当前密码')).toBeInTheDocument();
  expect(mocks.changePassword).not.toHaveBeenCalled();
});

it('shows a backend rejection and keeps inputs available for retry', async () => {
  mocks.changePassword.mockRejectedValue(new ApiProblemError({ title: '密码更新失败', status: 400, detail: '当前密码不正确', code: 'INVALID_PASSWORD', recoverable: true }));
  renderPage();
  submit('abc');
  expect(await screen.findByText('当前密码不正确')).toBeInTheDocument();
  expect(screen.getByLabelText('新密码', { exact: true })).toHaveValue('abc');
  expect(screen.queryByText('请重新登录')).not.toBeInTheDocument();
  mocks.changePassword.mockResolvedValue(undefined);
  submit('abc', 'abc', 'correct-old-password');
  expect(await screen.findByText('请重新登录')).toBeInTheDocument();
});

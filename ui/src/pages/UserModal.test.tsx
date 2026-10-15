import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { UserModal } from './IdentityAdminPage';
import { identityApi, type AdminUser, type Department } from '../identity/api';

vi.mock('../identity/api', () => ({ identityApi: { organizationAssignments: vi.fn(), updateUser: vi.fn(), assignRole: vi.fn(), revokeRole: vi.fn() } }));
beforeEach(() => { cleanup(); vi.resetAllMocks(); });
const user = { id: 'hr', displayName: '验收HR', workEmail: 'qa-hr@example.invalid', sourceTitle: null, homeDepartmentId: 'dept', directLeaderUserId: null, version: 1 } as AdminUser;
const assignments = { userId: 'hr', supportDepartmentIds: ['dept'], roles: ['RECRUITER'] };
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = (open: boolean) => <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><UserModal open={open} editing={user} departments={[{ id: 'dept', name: '验收部门' }] as Department[]} users={[user]} onClose={vi.fn()} onCreated={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)} /></QueryClientProvider></App></ConfigProvider>;
  const result = render(view(true));
  return { reopen: () => { result.rerender(view(false)); result.rerender(view(true)); } };
}
it('restores saved account fields and preserves support departments when reopening a cancelled edit', async () => {
  vi.mocked(identityApi.organizationAssignments).mockResolvedValue(assignments);
  const view = mount();
  await waitFor(() => expect(screen.getByRole('checkbox', { name: '招聘专员' })).toBeChecked());
  expect(screen.getByLabelText('工作邮箱')).toHaveValue(user.workEmail);
  fireEvent.change(screen.getByLabelText('岗位名称'), { target: { value: '未保存岗位' } });
  view.reopen();
  await waitFor(() => expect(screen.getByLabelText('岗位名称')).toHaveValue(''));
  fireEvent.click(screen.getByRole('button', { name: '保存账号' }));
  await waitFor(() => expect(identityApi.updateUser).toHaveBeenCalledWith('hr', expect.objectContaining({ workEmail: user.workEmail, supportDepartmentIds: ['dept'], directLeaderUserId: undefined })));
});
it('blocks account saves when authorization data failed to load and recovers on retry', async () => {
  vi.mocked(identityApi.organizationAssignments).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(assignments);
  mount();
  expect(await screen.findByText('账号授权资料加载失败，读取成功后才能保存。')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存账号' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试授权资料' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '保存账号' })).not.toBeDisabled());
  expect(identityApi.updateUser).not.toHaveBeenCalled();
});

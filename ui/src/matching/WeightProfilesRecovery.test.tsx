import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { ApiProblemError } from '../api/problem';
import { WeightProfiles } from './WeightProfiles';

vi.mock('../api/http', () => ({ request: vi.fn() }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'hr', roles: ['RECRUITER'], superAdmin: false } }) }));
const savedProfile = { profile: { id: 'profile', name: '恢复方案', version: 1 }, currentVersion: { id: 'version', versionNo: 1 } };
const posts = () => vi.mocked(request).mock.calls.filter(([, options]) => options?.method === 'POST');
beforeEach(() => { vi.mocked(request).mockReset(); });
afterEach(() => { cleanup(); });
async function mount() {
  const onSaved = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><WeightProfiles onSaved={onSaved} /></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(screen.getByRole('button', { name: /新建方案/ }));
  const dialog = await screen.findByRole('dialog', { name: '新建个人筛选方案' });
  fireEvent.change(within(dialog).getByRole('textbox', { name: '方案名称' }), { target: { value: '恢复方案' } });
  return { onSaved, dialog };
}
function failFirst(error: Error) {
  let attempts = 0;
  vi.mocked(request).mockImplementation((path, options) => {
    if (options?.method !== 'POST') return Promise.resolve(path.startsWith('/weight-profiles/count') ? { count: 0, nextCursor: null, hasMore: false } : { items: [], hasMore: false });
    attempts++;
    return attempts === 1 ? Promise.reject(error) : Promise.resolve(savedProfile);
  });
}

it('replays the same create key and body after a lost success response', async () => {
  failFirst(new SyntaxError('响应内容不完整'));
  const { onSaved, dialog } = await mount();
  const save = within(dialog).getByRole('button', { name: '保存方案' });
  fireEvent.click(save);
  await screen.findByText('保存响应未能确认，请重试核对。');
  await waitFor(() => expect(save).toBeEnabled());
  fireEvent.click(save);
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).toBe(calls[0]?.[1]?.idempotencyKey);
  expect(calls[1]?.[1]?.body).toEqual(calls[0]?.[1]?.body);
}, 15_000);

it('resumes the uncertain create attempt after closing and reopening the form', async () => {
  failFirst(new TypeError('创建响应丢失'));
  const { onSaved, dialog } = await mount();
  fireEvent.click(within(dialog).getByRole('button', { name: '保存方案' }));
  await screen.findByText('保存响应未能确认，请重试核对。');
  fireEvent.click(within(dialog).getByRole('button', { name: /^(Cancel|取\s*消)$/ }));
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '新建个人筛选方案' })).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: /新建方案/ }));
  const reopened = await screen.findByRole('dialog', { name: '新建个人筛选方案' });
  expect(within(reopened).getByRole('textbox', { name: '方案名称' })).toHaveValue('恢复方案');
  expect(within(reopened).getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  fireEvent.click(within(reopened).getByRole('button', { name: '保存方案' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[1]?.[1]?.idempotencyKey).toBe(calls[0]?.[1]?.idempotencyKey);
  expect(calls[1]?.[1]?.body).toEqual(calls[0]?.[1]?.body);
}, 15_000);

it('freezes uncertain submitted fields while keeping save available to confirm the attempt', async () => {
  failFirst(new TypeError('网络响应丢失'));
  const { dialog } = await mount();
  fireEvent.click(within(dialog).getByRole('button', { name: '保存方案' }));
  await screen.findByText('保存响应未能确认，请重试核对。');
  await waitFor(() => expect(within(dialog).getByRole('textbox', { name: '方案名称' })).toBeDisabled());
  expect(within(dialog).getByRole('spinbutton', { name: '核心技能' })).toBeDisabled();
  expect(within(dialog).getByRole('combobox', { name: '默认排序' })).toBeDisabled();
  expect(screen.getByText('上次保存结果尚未确认，请重试确认后再修改方案。')).toBeInTheDocument();
  expect(within(dialog).getByRole('button', { name: '保存方案' })).toBeEnabled();
}, 15_000);

it('starts a fresh editable operation after the prior create was confirmed successfully', async () => {
  failFirst(new TypeError('首次响应丢失'));
  const { onSaved, dialog } = await mount();
  const save = within(dialog).getByRole('button', { name: '保存方案' });
  fireEvent.click(save);
  await screen.findByText('保存响应未能确认，请重试核对。');
  await waitFor(() => expect(save).toBeEnabled());
  fireEvent.click(save);
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '新建个人筛选方案' })).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: /新建方案/ }));
  const next = await screen.findByRole('dialog', { name: '新建个人筛选方案' });
  const name = within(next).getByRole('textbox', { name: '方案名称' });
  expect(name).toBeEnabled();
  expect(name).toHaveValue('');
  fireEvent.change(name, { target: { value: '第二个独立方案' } });
  fireEvent.click(within(next).getByRole('button', { name: '保存方案' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2));
  const calls = posts();
  expect(calls).toHaveLength(3);
  expect(calls[1]?.[1]?.idempotencyKey).toBe(calls[0]?.[1]?.idempotencyKey);
  expect(calls[2]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[2]?.[1]?.idempotencyKey).not.toBe(calls[1]?.[1]?.idempotencyKey);
  expect(calls[2]?.[1]?.body).toMatchObject({ name: '第二个独立方案' });
}, 15_000);

it.each([400, 422])('allows a corrected name with a fresh operation key after definite %s rejection', async status => {
  failFirst(new ApiProblemError({ status, code: 'VALIDATION_FAILED', title: '校验失败', detail: '方案名称需要修改', recoverable: true }));
  const { onSaved, dialog } = await mount();
  const save = within(dialog).getByRole('button', { name: '保存方案' });
  fireEvent.click(save);
  await screen.findByText('方案名称需要修改');
  const name = within(dialog).getByRole('textbox', { name: '方案名称' });
  await waitFor(() => expect(name).toBeEnabled());
  fireEvent.change(name, { target: { value: '修正方案' } });
  fireEvent.click(save);
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[1]?.[1]?.body).toMatchObject({ name: '修正方案' });
  expect(calls[1]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).not.toBe(calls[0]?.[1]?.idempotencyKey);
}, 15_000);

it('saves a zero-weight dimension when the five weights total 100 percent', async () => {
  vi.mocked(request).mockImplementation((path, options) => Promise.resolve(options?.method === 'POST' ? savedProfile : path.startsWith('/weight-profiles/count') ? { count: 0, nextCursor: null, hasMore: false } : { items: [], hasMore: false }));
  const { dialog, onSaved } = await mount();
  fireEvent.change(within(dialog).getByRole('spinbutton', { name: '相关年限' }), { target: { value: '0' } });
  fireEvent.change(within(dialog).getByRole('spinbutton', { name: '核心技能' }), { target: { value: '40' } });
  const save = within(dialog).getByRole('button', { name: '保存方案' });
  expect(save).toBeEnabled();
  fireEvent.click(save);
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(posts()[0]?.[1]?.body).toMatchObject({ weights: { skills: 40, relatedYears: 0 } });
});

it('shows the exact total for personal screening profiles', async () => {
  vi.mocked(request).mockImplementation(path => Promise.resolve(path.startsWith('/weight-profiles/count') ? { count: 43, nextCursor: null, hasMore: false } : { items: [], nextCursor: null, hasMore: false }));
  await mount();
  expect(await screen.findByText('条/共43条记录')).toBeInTheDocument();
  expect(screen.getByText('共3页')).toBeInTheDocument();
});

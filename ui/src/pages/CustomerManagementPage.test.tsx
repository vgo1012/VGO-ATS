import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CustomerManagementPage } from './CustomerManagementPage';
import { demandApi, type Client } from '../demand/api';
import { deleteClient } from '../demand/clientDeletion';
import { ApiProblemError } from '../api/problem';

vi.mock('../demand/api', () => ({ demandApi: { clients: vi.fn(), countClients: vi.fn(), createClient: vi.fn(), updateClient: vi.fn(), disableClient: vi.fn() } }));
vi.mock('../demand/clientDeletion', () => ({ deleteClient: vi.fn() }));
const first: Client = { id: 'first', name: '第一页客户', code: 'first', status: 'ACTIVE', ownerUserId: 'owner', ownerDepartmentId: 'department', version: 1, updatedAt: '2026-09-14' };
const second = { ...first, id: 'second', name: '第二页客户' };
beforeEach(() => { vi.resetAllMocks(); vi.mocked(demandApi.countClients).mockResolvedValue(21); });
afterEach(cleanup);
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><CustomerManagementPage /></QueryClientProvider></App></ConfigProvider>);
}

it('按服务端游标翻页，前后页不累加展示，失败后可在当前页重试', async () => {
  let fail = true;
  vi.mocked(demandApi.clients).mockImplementation((_status, cursor) => {
    if (!cursor) return Promise.resolve({ items: [first], hasMore: true, nextCursor: 'cursor-two' });
    if (fail) return Promise.reject(new Error('offline'));
    return Promise.resolve({ items: [second], hasMore: false, nextCursor: null });
  });
  mount();
  await screen.findByText(first.name);
  expect(await screen.findByText('条/共21条记录')).toBeVisible();
  const controls = within(screen.getByRole('navigation', { name: '客户分页' }));
  expect(controls.getByRole('button', { name: '上一页' })).toBeDisabled();
  fireEvent.click(controls.getByRole('button', { name: '下一页' }));
  await waitFor(() => expect(demandApi.clients).toHaveBeenLastCalledWith(undefined, 'cursor-two', 20));
  expect(screen.queryByText(first.name)).not.toBeInTheDocument();
  const retry = await screen.findByRole('button', { name: /重\s*试/ });
  expect(controls.getByRole('button', { name: '下一页' })).toBeDisabled();
  fail = false;
  fireEvent.click(retry);
  await screen.findByText(second.name);
  expect(controls.getByRole('spinbutton', { name: '页码' })).toHaveValue(2);
  fireEvent.click(controls.getByRole('button', { name: '上一页' }));
  await screen.findByText(first.name);
  expect(screen.queryByText(second.name)).not.toBeInTheDocument();
}, 15000);

it('取消不删除，确认删除末页唯一客户后返回上一页', async () => {
  let removed = false;
  vi.mocked(demandApi.clients).mockImplementation((_status, cursor) => Promise.resolve({
    items: cursor ? (removed ? [] : [second]) : [first],
    hasMore: !cursor && !removed, nextCursor: !cursor && !removed ? 'cursor-two' : null,
  }));
  vi.mocked(deleteClient).mockImplementation(() => { removed = true; return Promise.resolve(); });
  mount();
  await screen.findByText(first.name);
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await screen.findByText(second.name);
  fireEvent.click(screen.getByRole('button', { name: /删\s*除/ }));
  fireEvent.click(await screen.findByRole('button', { name: /取\s*消/ }));
  expect(deleteClient).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /^删\s*除$/ }));
  fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(vi.mocked(deleteClient).mock.calls[0]?.[0]).toEqual(second));
  await screen.findByText(first.name);
  expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(1);
  expect(screen.queryByText(second.name)).not.toBeInTheDocument();
}, 15000);

it('被引用的客户删除失败后保留行并显示服务端原因', async () => {
  vi.mocked(demandApi.clients).mockResolvedValue({ items: [first], hasMore: false, nextCursor: null });
  vi.mocked(deleteClient).mockRejectedValue(new ApiProblemError({ status: 409, code: 'CLIENT_IN_USE', title: '客户不可删除', detail: '客户已有业务引用，请改用停用', recoverable: true }));
  mount();
  await screen.findByText(first.name);
  fireEvent.click(screen.getByRole('button', { name: /^删\s*除$/ }));
  fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));
  await screen.findByText('客户已有业务引用，请改用停用');
  expect(screen.getByText(first.name)).toBeInTheDocument();
  expect(demandApi.disableClient).not.toHaveBeenCalled();
}, 15000);

it('删除等待期间切换页面，成功后不会错误退回上一页', async () => {
  vi.mocked(demandApi.clients).mockImplementation((_status, cursor) => Promise.resolve({
    items: [cursor ? second : first], hasMore: !cursor, nextCursor: cursor ? null : 'cursor-two',
  }));
  let finish = () => {};
  vi.mocked(deleteClient).mockReturnValue(new Promise<void>(resolve => { finish = resolve; }));
  mount();
  await screen.findByText(first.name);
  fireEvent.click(screen.getByRole('button', { name: /^删\s*除$/ }));
  fireEvent.click(await screen.findByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleteClient).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await screen.findByText(second.name);
  await act(() => { finish(); return Promise.resolve(); });
  await waitFor(() => expect(screen.getByRole('button', { name: /^删\s*除$/ })).toBeEnabled());
  expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(2);
  expect(screen.getByText(second.name)).toBeInTheDocument();
}, 15000);

it('独立页面仍可编辑客户并刷新当前列表', async () => {
  vi.mocked(demandApi.clients).mockResolvedValue({ items: [first], nextCursor: null, hasMore: false });
  vi.mocked(demandApi.updateClient).mockImplementation(() => {
    const updated = { ...first, name: '核实后的客户', version: 2 };
    vi.mocked(demandApi.clients).mockResolvedValue({ items: [updated], nextCursor: null, hasMore: false });
    return Promise.resolve(updated);
  });
  mount();
  await screen.findByText(first.name);
  fireEvent.click(screen.getByRole('button', { name: /编\s*辑/ }));
  const name = await screen.findByLabelText('客户名称');
  fireEvent.change(name, { target: { value: '核实后的客户' } });
  fireEvent.click(screen.getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(demandApi.updateClient).toHaveBeenCalledWith('first', { name: '核实后的客户', version: 1 }));
  await screen.findByText('核实后的客户');
}, 15000);

it('修改每页条数后用新数量请求第一页并清空旧游标', async () => {
  vi.mocked(demandApi.clients).mockImplementation((_status, cursor) => Promise.resolve({ items: [cursor ? second : first], hasMore: !cursor, nextCursor: cursor ? null : 'cursor-two' }));
  mount();
  await screen.findByText(first.name);
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await screen.findByText(second.name);
  const size = screen.getByRole('spinbutton', { name: '每页条数' });
  fireEvent.change(size, { target: { value: '30' } });
  fireEvent.blur(size);
  await waitFor(() => expect(demandApi.clients).toHaveBeenLastCalledWith(undefined, undefined, 30));
  expect(screen.getByRole('spinbutton', { name: '页码' })).toHaveValue(1);
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
});

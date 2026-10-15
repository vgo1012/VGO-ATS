import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { directoryApi } from '../identity/directory';
import { ScreeningJobHeader } from './ScreeningJobHeader';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'self', displayName: '当前招聘员' } }) }));
vi.mock('../identity/directory', () => ({ directoryApi: { resolve: vi.fn() } }));
vi.mock('./useScreeningJobs', () => ({ useScreeningJobs: () => ({ data: { pages: [{ items: [
  { id: 'a', title: '电气工程师', currentVersionNo: 1, ownerUserId: 'other' },
  { id: 'b', title: '暖通工程师', currentVersionNo: 2, ownerUserId: 'other' },
  { id: 'c', title: '测试工程师', currentVersionNo: 1, ownerUserId: 'self' },
] }] }, isFetching: false, isPending: false }) }));
afterEach(cleanup);

it('在版本号旁展示真实所属人，批量去重解析且保留岗位选择', async () => {
  vi.mocked(directoryApi.resolve).mockResolvedValue([{ id: 'other', displayName: '张国玉', homeDepartmentId: 'dept', status: 'ACTIVE' }]);
  const onChange = vi.fn();
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient()}>
    <ScreeningJobHeader jd={undefined} version={undefined} loading={false} disabled={false} onChange={onChange}
      schemeControls={null} onRescore={vi.fn()} rescoreDisabled weights={[]} />
  </QueryClientProvider></App></ConfigProvider>);
  expect(directoryApi.resolve).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '选择招聘岗位' }));
  const dialog = await screen.findByRole('dialog', { name: '选择招聘岗位' });
  expect(await within(dialog).findByText('V1 · 所属人：张国玉')).toHaveAttribute('title', 'V1 · 所属人：张国玉');
  expect(within(dialog).getByText('V2 · 所属人：张国玉')).toBeInTheDocument();
  expect(within(dialog).getByText('V1 · 所属人：当前招聘员')).toBeInTheDocument();
  expect(directoryApi.resolve).toHaveBeenCalledExactlyOnceWith(['other']);
  const search = within(dialog).getByRole('textbox', { name: '搜索招聘岗位' });
  fireEvent.change(search, { target: { value: '临时查询' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '清空搜索' }));
  expect(search).toHaveValue('');
  fireEvent.click(within(dialog).getByRole('button', { name: /暖通工程师/ }));
  expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }));
});

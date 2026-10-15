import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { useScreeningJobs } from './useScreeningJobs';

const readJobs = vi.hoisted(() => vi.fn());
vi.mock('../demand/api', () => ({ demandApi: { jds: readJobs } }));
const first = { items: [{ id: 'first', title: '首批岗位' }], hasMore: true, nextCursor: 'second' };
const second = { items: [{ id: 'second', title: '第二批岗位' }], hasMore: true, nextCursor: 'third' };
const key = ['demand', 'jd-selector', '研发'];
const problem = (status: number) => new ApiProblemError({ status, code: status === 503 ? 'SERVICE_UNAVAILABLE' : 'ACCESS_DENIED', title: '岗位读取失败', detail: '暂不可读取', recoverable: status === 503 });
function succeed() { readJobs.mockImplementation((_search: string, cursor?: string) => Promise.resolve(cursor ? second : first)); }
beforeEach(() => { readJobs.mockReset(); succeed(); });
afterEach(() => { cleanup(); });
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, notifyOnChangeProps: 'all' } } });
  const wrapper = ({ children }: PropsWithChildren) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const hook = renderHook(() => ({ header: useScreeningJobs('研发'), page: useScreeningJobs('研发') }), { wrapper });
  return { client, result: hook.result };
}

it.each([401, 403, 503])('refresh %s applies the same visible-cache contract to both consumers', async status => {
  const { client, result } = mount();
  await waitFor(() => expect(result.current.header.data?.pages).toHaveLength(1));
  await act(() => result.current.header.fetchNextPage());
  await waitFor(() => expect(result.current.page.data?.pages).toHaveLength(2));
  readJobs.mockRejectedValue(problem(status));
  await act(() => client.invalidateQueries({ queryKey: key }));
  await waitFor(() => expect(result.current.header.isError).toBe(true));
  for (const consumer of [result.current.header, result.current.page]) {
    if (status !== 503) { expect(consumer.data).toBeUndefined(); expect(consumer.hasNextPage).toBe(false); }
    else expect(consumer.data?.pages.flatMap(page => page.items.map(item => item.id))).toEqual(['first', 'second']);
  }
  expect(client.getQueryData<{ pages: unknown[] }>(key)?.pages).toHaveLength(2);
  const before = readJobs.mock.calls.length;
  succeed();
  await act(() => result.current.header.refetch());
  await waitFor(() => expect(result.current.page.isError).toBe(false));
  for (const consumer of [result.current.header, result.current.page]) expect(consumer.data?.pages.flatMap(page => page.items.map(item => item.id))).toEqual(['first', 'second']);
  expect(readJobs.mock.calls.slice(before)).toEqual([['研发', undefined], ['研发', 'second']]);
}, 15_000);

it.each([401, 403])('append %s also hides earlier pages from both consumers until retry succeeds', async status => {
  const { result } = mount();
  await waitFor(() => expect(result.current.page.data?.pages).toHaveLength(1));
  readJobs.mockRejectedValue(problem(status));
  await act(() => result.current.header.fetchNextPage());
  await waitFor(() => expect(result.current.page.isFetchNextPageError).toBe(true));
  for (const consumer of [result.current.header, result.current.page]) { expect(consumer.data).toBeUndefined(); expect(consumer.hasNextPage).toBe(false); }
  succeed();
  await act(() => result.current.page.refetch());
  await waitFor(() => expect(result.current.header.isError).toBe(false));
  expect(result.current.header.data?.pages.flatMap(page => page.items.map(item => item.id))).toEqual(['first']);
  await act(() => result.current.header.fetchNextPage());
  await waitFor(() => expect(result.current.page.data?.pages.flatMap(page => page.items.map(item => item.id))).toEqual(['first', 'second']));
  expect(readJobs.mock.calls.every(args => args[0] === '研发')).toBe(true);
}, 15_000);

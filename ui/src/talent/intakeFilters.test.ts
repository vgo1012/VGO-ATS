import { afterEach, expect, it, vi } from 'vitest';
import { talentApi } from './api';
import { request } from '../api/http';

vi.mock('../api/http', () => ({ request: vi.fn() }));
afterEach(() => vi.clearAllMocks());

it('reads every page of the shared source catalogue instead of hiding later sources',async()=>{
  vi.mocked(request).mockResolvedValueOnce({items:[{id:'first'}],hasMore:true,nextCursor:'next'}).mockResolvedValueOnce({items:[{id:'later'}],hasMore:false,nextCursor:null});
  const page=await talentApi.channels();
  expect(page.items.map(item=>item.id)).toEqual(['first','later']);
  expect(vi.mocked(request).mock.calls[1]?.[0]).toContain('cursor=next');
});

it('sends combined intake filters and cursor to the server and normalizes returned dates', async () => {
  vi.mocked(request).mockResolvedValue({ items: [{ id: 'later-page', createTime: '2026-09-09T01:00:00Z' }], hasMore: false, nextCursor: null });
  const page = await talentApi.intakes('IMPORTED', 'next&cursor', { query: '开发 & 测试', name: '张三', from: '2026-09-08T16:00:00.000Z', to: '2026-09-09T16:00:00.000Z' });
  const url = new URL(vi.mocked(request).mock.calls[0]![0], 'https://example.invalid');
  expect(Object.fromEntries(url.searchParams)).toEqual({ importStatus: 'IMPORTED', cursor: 'next&cursor', query: '开发 & 测试', name: '张三', from: '2026-09-08T16:00:00.000Z', to: '2026-09-09T16:00:00.000Z', limit: '20' });
  expect(page.items[0]?.createdAt).toBe('2026-09-09T01:00:00Z');
});

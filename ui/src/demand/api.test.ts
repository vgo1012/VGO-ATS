import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { demandApi } from './api';

vi.mock('../api/http', () => ({ request: vi.fn().mockResolvedValue({ id: 'version-1' }) }));

afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

it('creates a JD with an idempotency key when randomUUID is unavailable', async () => {
  vi.stubGlobal('crypto', { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) });
  const body = { workRegionName: '杭州', jobTypeCode: '01', workRegionCode: '02', title: 'AI剪辑师', distinction: '上海 · 短视频', rawText: '负责视频剪辑及内容创作', headcount: 3, recruitmentCycleDays: 45, ownerDepartmentId: 'dept-1', recruitmentType: 'INTERNAL' as const };
  await demandApi.createSourceJd(body);
  const [path, options] = vi.mocked(request).mock.calls[0]!;
  expect(path).toBe('/jds');
  expect(options).toMatchObject({ method: 'POST', body });
  expect(options?.idempotencyKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

it('continues public JD count with the server cursor', async () => {
  vi.mocked(request).mockResolvedValueOnce({count:20,nextCursor:'next',hasMore:true}).mockResolvedValueOnce({count:1,nextCursor:null,hasMore:false});
  expect(await demandApi.countPublicJds('运维')).toBe(21);
  expect(vi.mocked(request).mock.calls.map(([path])=>path)).toEqual(['/jds/public-sources/count?query=%E8%BF%90%E7%BB%B4','/jds/public-sources/count?query=%E8%BF%90%E7%BB%B4&cursor=next']);
});

it('reads the public JD list and detail from public source endpoints', async () => {
  await demandApi.publicJds('运维', undefined, 20);
  await demandApi.publicJd('jd-1');
  expect(vi.mocked(request).mock.calls.map(([path])=>path)).toEqual(['/jds/public-sources?query=%E8%BF%90%E7%BB%B4&limit=20','/jds/public-sources/jd-1']);
});

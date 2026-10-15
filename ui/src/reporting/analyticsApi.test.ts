import { afterEach, expect, it, vi } from 'vitest';
import { analyticsApi } from './analyticsApi';

afterEach(() => vi.unstubAllGlobals());

it('omits both time bounds for cumulative data while retaining business filters', async () => {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetchMock);
  await analyticsApi.report({ departmentId: 'department-a', jdId: 'jd-a' });
  const [path] = fetchMock.mock.calls[0]!;
  const url = new URL(path instanceof Request ? path.url : path, 'http://localhost');
  expect(Object.fromEntries(url.searchParams)).toEqual({ departmentId: 'department-a', jdId: 'jd-a' });
});

it('sends period and JD-bound department filters to the aggregate endpoint and permits cancellation', async () => {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ demand: {} }), { headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetchMock);
  const controller = new AbortController();
  await analyticsApi.report({ from: '2026-08-31T16:00:00Z', to: '2026-09-30T16:00:00Z', departmentId: 'department-a', jdId: 'jd-a', recruitmentType: 'EXTERNAL' }, controller.signal);
  const [path, options] = fetchMock.mock.calls[0]!;
  const url = new URL(path instanceof Request ? path.url : path, 'http://localhost');
  expect(url.pathname).toBe('/api/v1/reports/recruitment-analysis');
  expect(Object.fromEntries(url.searchParams)).toEqual({ from: '2026-08-31T16:00:00Z', to: '2026-09-30T16:00:00Z', departmentId: 'department-a', jdId: 'jd-a', recruitmentType: 'EXTERNAL' });
  expect(options?.signal).toBe(controller.signal);
});

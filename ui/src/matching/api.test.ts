import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { matchingApi } from './api';

vi.mock('../api/http', () => ({ request: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('reads results within the API page limit and encodes the next-page cursor', async () => {
  vi.mocked(request).mockImplementation((path) => {
    const url = new URL(path, 'https://test.invalid');
    if (Number(url.searchParams.get('limit')) > 100) throw new Error('page limit exceeded');
    return Promise.resolve({ items: [], hasMore: false, nextCursor: null });
  });
  await matchingApi.results('run');
  expect(request).toHaveBeenLastCalledWith('/match-runs/run/results?limit=20');
  await matchingApi.results('run', 'next+/=');
  expect(request).toHaveBeenLastCalledWith('/match-runs/run/results?limit=20&cursor=next%2B%2F%3D');
});

it('岗位、判断依据与汇总结果由服务端分页，并编码过滤条件和游标', async () => {
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await matchingApi.jobs('jd+/','next+/=');
  expect(request).toHaveBeenLastCalledWith('/match-jobs?limit=20&jdVersionId=jd%2B%2F&cursor=next%2B%2F%3D',{});
  await matchingApi.bases('jd+/','next+/=');
  expect(request).toHaveBeenLastCalledWith('/match-jobs/jd%2B%2F/bases?limit=20&cursor=next%2B%2F%3D',{});
  await matchingApi.jobResults('jd+/','basis+/','next+/=');
  expect(request).toHaveBeenLastCalledWith('/match-jobs/jd%2B%2F/results?limit=20&basisRunId=basis%2B%2F&cursor=next%2B%2F%3D');
});

it('岗位与判断依据读取将取消信号传给底层请求',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const signal=new AbortController().signal;
  await matchingApi.jobs('jd',undefined,signal);
  expect(request).toHaveBeenLastCalledWith('/match-jobs?limit=20&jdVersionId=jd',{signal});
  await matchingApi.bases('jd',undefined,signal);
  expect(request).toHaveBeenLastCalledWith('/match-jobs/jd/bases?limit=20',{signal});
});

it('重新评分只请求当前岗位已准入选择接口，并编码姓名和游标',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await matchingApi.screeningCandidates('jd+/','张 三','next+/=',20);
  expect(request).toHaveBeenLastCalledWith('/jd-versions/jd%2B%2F/screening-candidates?query=%E5%BC%A0+%E4%B8%89&limit=20&cursor=next%2B%2F%3D');
});


it('岗位汇总按当前岗位和依据请求，不携带分页游标', async () => {
  vi.mocked(request).mockResolvedValue({total:41,strong:12,consider:24,reject:4,missing:1});
  await expect(matchingApi.jobResultsSummary('jd+/','basis+/')).resolves.toMatchObject({total:41});
  expect(request).toHaveBeenLastCalledWith('/match-jobs/jd%2B%2F/results-summary?basisRunId=basis%2B%2F');
});

it('自定义条数传到岗位结果、运行结果和候选人分页请求',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await matchingApi.jobResults('jd','basis',undefined,30);
  expect(request).toHaveBeenLastCalledWith('/match-jobs/jd/results?limit=30&basisRunId=basis');
  await matchingApi.results('run',undefined,30);
  expect(request).toHaveBeenLastCalledWith('/match-runs/run/results?limit=30');
  await matchingApi.screeningCandidates('jd','',undefined,30);
  expect(request).toHaveBeenLastCalledWith('/jd-versions/jd/screening-candidates?query=&limit=30');
});

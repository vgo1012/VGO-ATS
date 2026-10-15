import { afterEach, expect, it, vi } from 'vitest';
import { demandApi } from '../demand/api';
import { mailSourceApi } from '../demand/mailSourceApi';
import { mailResumeApi } from '../mail-resume/api';
import { matchingApi } from '../matching/api';
import { recruitmentApi } from '../recruitment/api';
import { request } from './http';

vi.mock('./http',()=>({request:vi.fn()}));
afterEach(()=>vi.resetAllMocks());

it('requests exact totals for direct-count lists with their active filters',async()=>{
  const signal=new AbortController().signal;
  vi.mocked(request).mockResolvedValue({count:7,nextCursor:null,hasMore:false});
  await expect(demandApi.countJds('运维',signal)).resolves.toBe(7);
  await expect(demandApi.countClients('ACTIVE',signal)).resolves.toBe(7);
  await expect(demandApi.countWeightProfiles(true,signal)).resolves.toBe(7);
  await expect(mailSourceApi.countSources('jd+/=',signal)).resolves.toBe(7);
  await expect(mailResumeApi.countReviews({title:'Java 开发',region:'杭州'},signal)).resolves.toBe(7);
  const paths=vi.mocked(request).mock.calls.map(([path])=>String(path));
  expect(paths).toEqual([
    '/jds/count?query=%E8%BF%90%E7%BB%B4',
    '/clients/count?status=ACTIVE',
    '/weight-profiles/count?mineOnly=true',
    '/jd-versions/jd+/=/resume-sources/count',
    '/mail-resume-reviews/count?title=Java+%E5%BC%80%E5%8F%91&region=%E6%9D%AD%E5%B7%9E',
  ]);
  expect(vi.mocked(request).mock.calls.every(([,options])=>options?.signal===signal)).toBe(true);
});

it('accumulates bounded screening and application counts without leaking list cursors or limits',async()=>{
  const signal=new AbortController().signal;
  vi.mocked(request)
    .mockResolvedValueOnce({count:100,nextCursor:'screen-next',hasMore:true})
    .mockResolvedValueOnce({count:4,nextCursor:null,hasMore:false})
    .mockResolvedValueOnce({count:100,nextCursor:'app-next',hasMore:true})
    .mockResolvedValueOnce({count:23,nextCursor:null,hasMore:false});
  await expect(matchingApi.countScreeningCandidates('jd+/','张 三',signal)).resolves.toBe(104);
  await expect(recruitmentApi.countApplications({stage:'ONBOARDED',conditions:['本科','Java'],cursor:'list-cursor',limit:30},signal)).resolves.toBe(123);
  const urls=vi.mocked(request).mock.calls.map(([path])=>new URL(String(path),'https://test.invalid'));
  expect(urls[0]!.pathname).toBe('/jd-versions/jd%2B%2F/screening-candidates/count');
  expect(urls[0]!.searchParams.get('query')).toBe('张 三');
  expect(urls[1]!.searchParams.get('cursor')).toBe('screen-next');
  expect(urls[2]!.pathname).toBe('/applications/count');
  expect(urls[2]!.searchParams.get('stage')).toBe('ONBOARDED');
  expect(urls[2]!.searchParams.getAll('conditions')).toEqual(['本科','Java']);
  expect(urls[2]!.searchParams.has('limit')).toBe(false);
  expect(urls[2]!.searchParams.has('cursor')).toBe(false);
  expect(urls[3]!.searchParams.get('cursor')).toBe('app-next');
});

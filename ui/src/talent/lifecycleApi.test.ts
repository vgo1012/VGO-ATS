import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { emptyCriteria, searchApi } from '../search/api';
import { talentApi } from './api';

vi.mock('../api/http',()=>({request:vi.fn()}));
afterEach(()=>vi.resetAllMocks());
it('filters the plain personal candidate page on the server',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],nextCursor:null,hasMore:false});
  await talentApi.candidates('',undefined,20,true,'PENDING_SCREENING');
  const params=new URL(String(vi.mocked(request).mock.calls[0]?.[0]),'http://localhost').searchParams;
  expect(params.get('poolOnly')).toBe('true');
  expect(params.get('poolStage')).toBe('PENDING_SCREENING');
});
it('preserves the overdue phase through filtered server pagination',async()=>{
  vi.mocked(request).mockResolvedValue({items:[],nextCursor:'next',hasMore:true});
  const page=await searchApi.personalCandidates({...emptyCriteria(),poolStage:'PENDING_DISPOSAL',jdId:'jd',query:'简历'},'cursor');
  const params=new URL(String(vi.mocked(request).mock.calls[0]?.[0]),'http://localhost').searchParams;
  expect(params.get('scope')).toBe('PERSONAL_POOL');
  expect(params.get('poolStage')).toBe('PENDING_DISPOSAL');
  expect(params.get('cursor')).toBe('cursor');
  expect(params.get('jdId')).toBe('jd');
  expect(page.nextCursor).toBe('next');
});

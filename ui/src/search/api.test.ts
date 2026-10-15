import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { criteriaFromUrl, criteriaToUrl, emptyCriteria, searchApi } from './api';

vi.mock('../api/http', () => ({ request: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('omits a cleared numeric control but preserves zero years and real search values', async () => {
  vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null});
  const cleared = { ...emptyCriteria(), query: 'Synthetic', minAge: null,
    minWorkYears: 0, cities: ['上海'], gender: 'MALE' };
  expect(criteriaToUrl(cleared).toString()).not.toContain('null');
  await searchApi.search(cleared);
  expect(request).toHaveBeenLastCalledWith('/talent-search?q=Synthetic&cities=%E4%B8%8A%E6%B5%B7&minWorkYears=0&gender=MALE&limit=20');
});

it('does not turn invalid numeric URL conditions into NaN API parameters', () => {
  const criteria = criteriaFromUrl(new URLSearchParams('q=Synthetic&minAge=null&maxAge=NaN&minWorkYears=0'));
  expect(criteria).toEqual({ ...emptyCriteria(), query: 'Synthetic', minWorkYears: 0 });
});

it('pages personal candidates in the server scope with JD and search criteria', async () => {
  vi.mocked(request).mockResolvedValueOnce({items:[{candidateId:'a'}],hasMore:false,nextCursor:null}).mockResolvedValueOnce([{id:'a',fullName:'候选人'}]);
  const page=await searchApi.personalCandidates({...emptyCriteria(),jdId:'jd-a',query:'候选人',minWorkYears:1,maxWorkYears:3},'next',20);
  expect(page.items).toEqual([{id:'a',fullName:'候选人'}]);
  const params=new URL(String(vi.mocked(request).mock.calls[0]?.[0]),'http://localhost').searchParams;
  expect(params.get('scope')).toBe('PERSONAL_POOL');
  expect(params.has('candidateIds')).toBe(false);
  expect(params.get('jdId')).toBe('jd-a');
  expect(params.get('q')).toBe('候选人');
  expect(params.get('cursor')).toBe('next');
  expect(params.get('limit')).toBe('20');
  expect(params.get('maxWorkYears')).toBe('3');
});



it('preserves the JD filter through URLs and personal-pool pagination',async()=>{
  const criteria={...emptyCriteria(),jdId:'jd-a'};
  expect(criteriaFromUrl(criteriaToUrl(criteria))).toEqual(criteria);
  vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null});
  await searchApi.personalCandidates(criteria,'next-page',100);
  const params=new URL(String(vi.mocked(request).mock.calls[0]?.[0]),'http://localhost').searchParams;
  expect(params.get('jdId')).toBe('jd-a');
  expect(params.get('scope')).toBe('PERSONAL_POOL');
  expect(params.get('cursor')).toBe('next-page');
  expect(params.get('limit')).toBe('100');
});

it('loads a personal-pool page with one batch request',async()=>{
  const ids=Array.from({length:20},(_,index)=>`candidate-${index}`);
  vi.mocked(request).mockResolvedValueOnce({items:ids.map(candidateId=>({candidateId})),hasMore:true,nextCursor:'next'});
  vi.mocked(request).mockResolvedValueOnce(ids.map(id=>({id,fullName:'合成候选人'})));
  const page=await searchApi.personalCandidates(emptyCriteria());
  expect(page.items.map(item=>item.id)).toEqual(ids);
  expect(page.nextCursor).toBe('next');
  expect(request).toHaveBeenCalledTimes(2);
  const batch=new URL(String(vi.mocked(request).mock.calls[1]?.[0]),'http://localhost');
  expect(batch.pathname).toBe('/candidate-batch');
  expect(batch.searchParams.getAll('candidateIds')).toEqual(ids);
});

it('keeps the search continuation when permissions remove all batch candidates',async()=>{
  vi.mocked(request).mockResolvedValueOnce({items:[{candidateId:'removed'}],hasMore:true,nextCursor:'next'}).mockResolvedValueOnce([]);
  expect(await searchApi.personalCandidates(emptyCriteria())).toEqual({items:[],hasMore:true,nextCursor:'next'});
});

it('skips empty incomplete search batches before displaying personal candidates',async()=>{
  vi.mocked(request)
    .mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'scan-1',scanIncomplete:true})
    .mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'scan-2',scanIncomplete:true})
    .mockResolvedValueOnce({items:[{candidateId:'visible'}],hasMore:true,nextCursor:'next-page',scanIncomplete:false})
    .mockResolvedValueOnce([{id:'visible',fullName:'合成候选人'}]);
  const page=await searchApi.personalCandidates({...emptyCriteria(),query:'合成'},undefined,20);
  expect(page.items).toEqual([{id:'visible',fullName:'合成候选人'}]);
  expect(page.nextCursor).toBe('next-page');
  expect(page.hasMore).toBe(true);
  const calls=vi.mocked(request).mock.calls.slice(0,3).map(([url])=>new URL(String(url),'http://localhost').searchParams);
  expect(calls.map(params=>params.get('cursor'))).toEqual([null,'scan-1','scan-2']);
  expect(calls.every(params=>params.get('q')==='合成'&&params.get('limit')==='20'&&params.get('scope')==='PERSONAL_POOL')).toBe(true);
  expect(calls.every(params=>!params.has('poolStage'))).toBe(true);
});

it('finishes an empty search only after the provider exhausts the continuation',async()=>{
  vi.mocked(request)
    .mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'scan-1',scanIncomplete:true})
    .mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null,scanIncomplete:false});
  expect(await searchApi.search(emptyCriteria())).toEqual({items:[],hasMore:false,nextCursor:null,scanIncomplete:false});
  expect(request).toHaveBeenCalledTimes(2);
});

it.each([null,'start','loop'] as const)('rejects a broken empty-search continuation %s',async continuation=>{
  vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:true,nextCursor:continuation,scanIncomplete:true});
  if(continuation==='loop')vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'loop',scanIncomplete:true});
  await expect(searchApi.search(emptyCriteria(),'start')).rejects.toThrow('检索游标未推进，请刷新后重试');
  expect(request).toHaveBeenCalledTimes(continuation==='loop'?2:1);
});


it('preserves each custom condition through URLs and cursor requests without splitting punctuation', async () => {
  vi.mocked(request).mockResolvedValueOnce({items:[],hasMore:false,nextCursor:null});
  const criteria = { ...emptyCriteria(), query: '高压电工证', conditions: ['低压电工证', 'C++, Java'] };
  expect(criteriaFromUrl(criteriaToUrl(criteria))).toEqual(criteria);
  await searchApi.search(criteria, 'next-page');
  const params = new URL(String(vi.mocked(request).mock.calls[0]?.[0]), 'http://localhost').searchParams;
  expect(params.get('q')).toBe('高压电工证');
  expect(params.getAll('conditions')).toEqual(['低压电工证', 'C++, Java']);
  expect(params.get('cursor')).toBe('next-page');
});

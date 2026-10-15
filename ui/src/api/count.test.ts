import { afterEach, expect, it, vi } from 'vitest';
import { request } from './http';
import { collectCount } from './count';
import { talentApi } from '../talent/api';
import { emptyCriteria, searchApi } from '../search/api';
vi.mock('./http',()=>({request:vi.fn()}));
afterEach(()=>vi.resetAllMocks());
it('sums bounded count responses without fetching candidates and preserves filters and cancellation',async()=>{
 const controller=new AbortController();
 vi.mocked(request).mockResolvedValueOnce({count:200,nextCursor:'next',hasMore:true}).mockResolvedValueOnce({count:23,nextCursor:null,hasMore:false});
 expect(await talentApi.countCandidates('张',true,'PENDING_SCREENING',controller.signal)).toBe(223);
 const urls=vi.mocked(request).mock.calls.map(([path])=>new URL(path,'http://test'));
 expect(urls.every(url=>url.pathname==='/candidates/count'&&url.searchParams.get('query')==='张'&&url.searchParams.get('poolOnly')==='true'&&url.searchParams.get('poolStage')==='PENDING_SCREENING')).toBe(true);
 expect(urls[1]!.searchParams.get('cursor')).toBe('next');
 expect(vi.mocked(request).mock.calls.every(([,options])=>options?.signal===controller.signal)).toBe(true);
});
it.each(['SHARED','PERSONAL_POOL'] as const)('counts search scope %s with the same search filters',async scope=>{
 vi.mocked(request).mockResolvedValueOnce({count:0,nextCursor:'scan',hasMore:true}).mockResolvedValueOnce({count:2,nextCursor:null,hasMore:false});
 expect(await searchApi.count({...emptyCriteria(),query:'工程师',conditions:['证书'],degrees:['本科']},scope,['one'])).toBe(2);
 const url=new URL(vi.mocked(request).mock.calls[0]![0],'http://test');
 expect(url.pathname).toBe('/talent-search/count');
 expect(url.searchParams.get('scope')??'SHARED').toBe(scope);
 expect(url.searchParams.get('candidateIds')).toBe('one');
 expect(url.searchParams.get('q')).toBe('工程师');
 expect(url.searchParams.get('conditions')).toBe('证书');
 expect(url.searchParams.get('degrees')).toBe('本科');
 expect(url.searchParams.has('limit')).toBe(false);
});
it('rejects repeated cursors and invalid count results',async()=>{
 const load=vi.fn().mockResolvedValue({count:1,nextCursor:'same',hasMore:true});
 await expect(collectCount(load)).rejects.toThrow('统计游标未推进');
 expect(load).toHaveBeenCalledTimes(2);
 await expect(collectCount(()=>Promise.resolve({count:-1,nextCursor:null,hasMore:false}))).rejects.toThrow('统计结果异常');
});
it('cancels before following a continuation',async()=>{
 const controller=new AbortController();
 const load=vi.fn().mockImplementation(()=>{controller.abort();return Promise.resolve({count:2,nextCursor:'next',hasMore:true});});
 await expect(collectCount(load,controller.signal)).rejects.toThrow();
 expect(load).toHaveBeenCalledOnce();
});

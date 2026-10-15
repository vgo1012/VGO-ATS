import { afterEach, expect, it, vi } from 'vitest';
import { emptyCriteria, searchApi } from '../search/api';
import { talentApi, type Candidate } from '../talent/api';
import { selectedResumeVersions, selectMatchingCandidates } from './selectionCandidates';

afterEach(() => vi.restoreAllMocks());
it('selects matches beyond the first server page and deduplicates a repeated candidate', async () => {
  const candidates = vi.spyOn(talentApi, 'candidates').mockResolvedValueOnce({ items: [{ id: 'a' }] as Candidate[], hasMore: true, nextCursor: 'page2' })
    .mockResolvedValueOnce({ items: [{ id: 'a' }, { id: 'b' }] as Candidate[], hasMore: false, nextCursor: null });
  expect((await selectMatchingCandidates('测试')).map(person => person.id)).toEqual(['a', 'b']);
  expect(candidates).toHaveBeenLastCalledWith('测试', 'page2', 100);
});
it('rejects partial bulk selection on network errors and broken pagination', async () => {
  const candidates = vi.spyOn(talentApi, 'candidates').mockResolvedValue({ items: [], hasMore: true, nextCursor: 'repeated' });
  await expect(selectMatchingCandidates('')).rejects.toThrow('分页发生变化');
  candidates.mockRejectedValue(new Error('离线'));
  await expect(selectMatchingCandidates('')).rejects.toThrow('离线');
});
it('never silently truncates a selection at the backend limit', async () => {
  vi.spyOn(talentApi, 'candidates').mockResolvedValue({ items: Array.from({ length: 1001 }, (_, i) => ({ id: String(i) })) as Candidate[], hasMore: false, nextCursor: null });
  await expect(selectMatchingCandidates('')).rejects.toThrow('最多 1000 人');
});
it('uses the newest version even if the response is unordered, preserving candidate order', async () => {
  vi.spyOn(talentApi, 'resumeVersions').mockImplementation(id => Promise.resolve({ items: [{ id: `${id}-old`, versionNo: 1 }, { id: `${id}-new`, versionNo: 2 }], hasMore: false, nextCursor: null } as Awaited<ReturnType<typeof talentApi.resumeVersions>>));
  expect(await selectedResumeVersions(['a', 'b'])).toEqual(['a-new', 'b-new']);
});

it('uses the same ES criteria across every personal pool bulk-selection page', async () => {
  const criteria={...emptyCriteria(),degrees:['本科'],minWorkYears:1,maxWorkYears:3};
  const search=vi.spyOn(searchApi,'personalCandidates').mockResolvedValueOnce({items:[{id:'a'}] as Candidate[],hasMore:true,nextCursor:'next'}).mockResolvedValueOnce({items:[{id:'b'}] as Candidate[],hasMore:false,nextCursor:null});
  const sql=vi.spyOn(talentApi,'candidates');
  expect((await selectMatchingCandidates('13800138000',true,criteria)).map(person=>person.id)).toEqual(['a','b']);
  expect(search).toHaveBeenLastCalledWith({...criteria,query:'13800138000'},'next',100);
  expect(sql).not.toHaveBeenCalled();
});

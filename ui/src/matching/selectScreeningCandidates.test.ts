import { afterEach, expect, it, vi } from 'vitest';
import { matchingApi, type ScreeningSelectionCandidate } from './api';
import { selectScreeningCandidates } from './selectScreeningCandidates';

afterEach(() => vi.restoreAllMocks());
const item = (resumeVersionId: string): ScreeningSelectionCandidate => ({
  candidateId: 'same-person', candidateName: '林雨桐', resumeVersionId, resumeVersionNo: 1,
  originalFileName: 'resume.pdf', jdVersionId: 'jd', admissionId: `admission-${resumeVersionId}`,
  defaultMatchRunId: 'company-run', professionalScore: 80, referenceScore: 75,
});

it('preserves different admitted versions of one person across scoped search pages', async () => {
  const query = vi.spyOn(matchingApi, 'screeningCandidates')
    .mockResolvedValueOnce({items:[item('old')],hasMore:true,nextCursor:'next'})
    .mockResolvedValueOnce({items:[item('old'),item('new')],hasMore:false,nextCursor:null});
  expect(await selectScreeningCandidates('jd','林')).toEqual([
    {candidateId:'same-person',resumeVersionId:'old'},
    {candidateId:'same-person',resumeVersionId:'new'},
  ]);
  expect(query).toHaveBeenLastCalledWith('jd','林','next',100);
});

it('accepts exactly 1000 resumes but rejects overflow instead of silently truncating', async () => {
  const rows = Array.from({length:1000},(_,index)=>item(String(index)));
  const query = vi.spyOn(matchingApi,'screeningCandidates').mockResolvedValue({items:rows,hasMore:false,nextCursor:null});
  expect(await selectScreeningCandidates('jd','')).toHaveLength(1000);
  query.mockResolvedValue({items:rows,hasMore:true,nextCursor:'next'});
  await expect(selectScreeningCandidates('jd','')).rejects.toThrow('最多选择 1000 份简历');
  query.mockResolvedValue({items:[...rows,item('extra')],hasMore:false,nextCursor:null});
  await expect(selectScreeningCandidates('jd','')).rejects.toThrow('原有选择已保留');
});

it('rejects incomplete bulk selection when paging breaks or a later request fails', async () => {
  const query = vi.spyOn(matchingApi,'screeningCandidates').mockResolvedValue({items:[item('old')],hasMore:true,nextCursor:'repeat'});
  await expect(selectScreeningCandidates('jd','')).rejects.toThrow('分页发生变化');
  query.mockResolvedValue({items:[],hasMore:true,nextCursor:null});
  await expect(selectScreeningCandidates('jd','')).rejects.toThrow('分页发生变化');
  query.mockResolvedValueOnce({items:[item('old')],hasMore:true,nextCursor:'next'}).mockRejectedValueOnce(new Error('离线'));
  await expect(selectScreeningCandidates('jd','')).rejects.toThrow('离线');
});

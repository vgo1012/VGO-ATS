import { afterEach, expect, it, vi } from 'vitest';
import { matchingApi, type MatchBasis } from './api';
import { matchesScoringBasis, selectScoringBasis, type ScoringBasisSelection } from './selectScoringBasis';

const weights = {skillsWeight:47,roleWeight:18,yearsWeight:21,responsibilityWeight:14,industryProjectWeight:0};
const selection: ScoringBasisSelection = {
  profile: {profile:{id:'personal',ownerUserId:'hr',name:'个人',status:'ACTIVE',currentVersionNo:1,version:1,updatedAt:''},currentVersion:{id:'personal-v1',versionNo:1,...weights}},
  planVersionId:'plan',sortMode:'REFERENCE_SCORE',selectedCriterionIds:[],
};
const basis: MatchBasis = {basisRunId:'personal-run',jdVersionId:'jd',basisKey:'key',planVersionId:'plan',weightProfileVersionId:'equivalent-profile-version',sortMode:'REFERENCE_SCORE',lastRunAt:'',runCount:1,candidateCount:1,scoredCandidateCount:1,...weights,selectedCriterionIds:[],personalScreeningSettings:null};
afterEach(() => vi.restoreAllMocks());

it('实际条件等价时可使用其他代表版本，但不能混用公司权重、旧规则或不同排序', () => {
  expect(matchesScoringBasis(basis,selection)).toBe(true);
  expect(matchesScoringBasis({...basis,skillsWeight:29},selection)).toBe(false);
  expect(matchesScoringBasis({...basis,planVersionId:'old'},selection)).toBe(false);
  expect(matchesScoringBasis({...basis,sortMode:'BONUS_SCORE'},selection)).toBe(false);
  expect(matchesScoringBasis({...basis,selectedCriterionIds:['extra']},selection)).toBe(false);
});

it('跨页寻找当前方案，不误用最新的另一套分数', async () => {
  const load=vi.spyOn(matchingApi,'bases').mockResolvedValueOnce({items:[{...basis,skillsWeight:29},{...basis,basisRunId:'queued-run',scoredCandidateCount:0}],hasMore:true,nextCursor:'next'}).mockResolvedValueOnce({items:[basis],hasMore:false,nextCursor:null});
  const signal=new AbortController().signal;
  await expect(selectScoringBasis('jd',selection,signal)).resolves.toBe('personal-run');
  expect(load).toHaveBeenLastCalledWith('jd','next',signal);
});

it('找不到、分页失败和取消分别返回空态或错误，不能回退其他方案', async () => {
  const load=vi.spyOn(matchingApi,'bases').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  await expect(selectScoringBasis('jd',selection,new AbortController().signal)).resolves.toBeNull();
  load.mockRejectedValue(new Error('读取失败'));
  await expect(selectScoringBasis('jd',selection,new AbortController().signal)).rejects.toThrow('读取失败');
  const abort=new AbortController();abort.abort();load.mockClear();
  await expect(selectScoringBasis('jd',selection,abort.signal)).rejects.toThrow();
  expect(load).not.toHaveBeenCalled();
});

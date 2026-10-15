import { beforeEach, describe, expect, it, vi } from 'vitest';
import { candidateActionsApi } from './candidateActionsApi';
import { talentApi } from '../talent/api';
import { executeCandidateActions, summarizeCandidateActions } from './candidateActions';
import type { CandidateActionOutcome } from './candidateActionsApi';
vi.mock('./candidateActionsApi', () => ({ candidateActionsApi: { advance: vi.fn() } }));
vi.mock('../talent/api', () => ({ talentApi: { candidate:vi.fn(), resumeVersions:vi.fn(), poolStatuses:vi.fn(), screenOut:vi.fn() } }));
beforeEach(() => vi.resetAllMocks());

it('一次汇总批量结果并保持状态、原对象和首条匹配语义',()=>{
  const failure:CandidateActionOutcome={candidateId:'retry',status:'FAILED',message:'待重试'};
  const existing:CandidateActionOutcome={candidateId:'existing',status:'ALREADY_EXISTS',applicationId:'old'};
  const success:CandidateActionOutcome={candidateId:'retry',status:'SUCCEEDED',applicationId:'new'};
  const summary=summarizeCandidateActions([failure,existing,success]);
  expect(summary.succeeded).toEqual([existing,success]);
  expect(summary.failed).toEqual([failure]);
  expect([...summary.completedIds]).toEqual(['existing','retry']);
  expect(summary.byCandidate.get('retry')).toBe(failure);
  expect(summary.byCandidate.get('existing')).toBe(existing);
  expect(summary.byCandidate.get('missing')).toBeUndefined();
  expect(summarizeCandidateActions([]).completedIds.size).toBe(0);
});

it('千人结果汇总及末页查找仅各读取一次身份和状态',()=>{
  let identityReads=0,statusReads=0;
  const results:CandidateActionOutcome[]=Array.from({length:1000},(_,index)=>({
    get candidateId(){identityReads++;return `candidate-${index}`;},
    get status(){statusReads++;return index%2?'FAILED' as const:'SUCCEEDED' as const;},
  }));
  const summary=summarizeCandidateActions(results);
  for(let index=990;index<1000;index++)expect(summary.byCandidate.get(`candidate-${index}`)).toBe(results[index]);
  expect(summary.succeeded).toHaveLength(500);
  expect(summary.failed).toHaveLength(500);
  expect(identityReads).toBeLessThanOrEqual(1000);
  expect(statusReads).toBeLessThanOrEqual(1000);
});
describe('批量候选人动作', () => {
  it('百人以上分组推进，并保留单个业务失败和已在流程的结果', async () => {
    vi.mocked(candidateActionsApi.advance).mockImplementation((_, items) => Promise.resolve({items:items.map(item=>({...item,status:item.candidateId==='c1'?'FAILED':item.candidateId==='c2'?'ALREADY_EXISTS':'SUCCEEDED',errorCode:item.candidateId==='c1'?'COMPANY_BLACKLIST_BLOCKED':undefined}))}));
    const candidates=Array.from({length:205},(_,i)=>({candidateId:`c${i}`,resumeVersionId:`r${i}`}));
    const progress=vi.fn();
    const result=await executeCandidateActions({mode:'advance',candidates,jdVersionId:'jd',reason:'人工复核',onProgress:progress});
    expect(vi.mocked(candidateActionsApi.advance).mock.calls.map(call=>call[1].length)).toEqual([25,25,25,25,25,25,25,25,5]);
    expect(result).toHaveLength(205);
    expect(result.filter(row=>row.status==='FAILED')).toHaveLength(1);
    expect(result[2]?.status).toBe('ALREADY_EXISTS');
    expect(progress).toHaveBeenCalledTimes(9);
    expect(progress).toHaveBeenLastCalledWith(result);
  });
  it('批次网络失败保留结果，不丢失后续候选人', async () => {
    vi.mocked(candidateActionsApi.advance).mockRejectedValueOnce(new Error('网络中断')).mockImplementation((_,items)=>Promise.resolve({items:items.map(item=>({...item,status:'SUCCEEDED'}))}));
    const result=await executeCandidateActions({mode:'advance',candidates:Array.from({length:101},(_,i)=>({candidateId:`c${i}`,resumeVersionId:`r${i}`})),jdVersionId:'jd',reason:'复核'});
    expect(result.filter(row=>row.status==='FAILED')).toHaveLength(25);
    expect(result[25]?.status).toBe('SUCCEEDED');
  });
  it('旧评分对应的简历已更新时，禁止把新简历转共享', async () => {
    vi.mocked(talentApi.poolStatuses).mockResolvedValue([{candidateId:'c',resumeVersionId:'new',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}]);
    const result=await executeCandidateActions({mode:'share',candidates:[{candidateId:'c',resumeVersionId:'old'}],reason:'不适合本岗位'});
    expect(result[0]?.status).toBe('FAILED');
    expect(result[0]?.message).toContain('已更新');
    expect(talentApi.screenOut).not.toHaveBeenCalled();
  });
  it('已共享的项目不重复写入筛选记录', async () => {
    vi.mocked(talentApi.poolStatuses).mockResolvedValue([{candidateId:'c',resumeVersionId:'r',libraryEligible:true,screenedOut:true,canScreenOut:false,stages:[]}]);
    const result=await executeCandidateActions({mode:'share',candidates:[{candidateId:'c',resumeVersionId:'r'}],reason:'筛选结束'});
    expect(result[0]?.status).toBe('ALREADY_EXISTS');
    expect(talentApi.screenOut).not.toHaveBeenCalled();
  });
});

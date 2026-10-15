import type { CandidatePoolStatus, ScreeningCandidate } from './api';

export interface ScreeningSelectionIssue { candidateId:string; reason:string }
export class ScreeningSelectionError extends Error {
  constructor(public readonly issues:ScreeningSelectionIssue[],public readonly jdId?:string) {
    super(issues[0]!.reason);
    this.name='ScreeningSelectionError';
  }
}

export function selectScreeningContexts(candidateIds:string[],contexts:ScreeningCandidate[],statuses:CandidatePoolStatus[],jdId?:string) {
  const contextsByCandidate=new Map<string,ScreeningCandidate[]>();
  for(const context of contexts){
    const id=context.candidateId;
    const group=contextsByCandidate.get(id);
    if(group)group.push(context);
    else contextsByCandidate.set(id,[context]);
  }
  const statusByCandidate=new Map(statuses.map(value=>[value.candidateId,value]));
  const selected:{context:ScreeningCandidate;jdId:string}[]=[];
  const issues:ScreeningSelectionIssue[]=[];
  for(const candidateId of candidateIds){
    const state=statusByCandidate.get(candidateId);
    if(state?.libraryEligible){issues.push({candidateId,reason:'所选简历已进入共享库，请重新选择 JD、评分并转入后再筛选'});continue;}
    const currentJobs=state?.jobContexts?.filter(value=>value.status!=='FINISHED'&&value.resumeVersionId===state.resumeVersionId&&(!jdId||value.jdId===jdId))??[];
    const selectedVersions=new Set(currentJobs.map(value=>value.jdVersionId));
    const scored=(contextsByCandidate.get(candidateId)??[]).filter(value=>value.resumeVersionId===state?.resumeVersionId&&(!jdId||selectedVersions.has(value.jdVersionId))&&(!currentJobs.length||currentJobs.some(job=>job.jdVersionId===value.jdVersionId&&(!job.matchRunId||job.matchRunId===value.defaultMatchRunId))));
    const uniqueVersions=new Set(scored.map(value=>value.jdVersionId));
    if(scored.length!==1||uniqueVersions.size!==1){
      issues.push({candidateId,reason:scored.length===0?'所选简历尚未完成公司默认评分，请刷新后重试':'所选简历关联了多个已评分岗位，无法确定唯一岗位'});
      continue;
    }
    const context=scored[0]!;
    const jobContexts=currentJobs.filter(value=>value.jdVersionId===context.jdVersionId);
    const jdIds=[...new Set(jobContexts.map(value=>value.jdId))];
    if(jdIds.length!==1){issues.push({candidateId,reason:'已评分岗位上下文不完整，请刷新后重试'});continue;}
    selected.push({context,jdId:jdIds[0]!});
  }
  if(issues.length)throw new ScreeningSelectionError(issues,jdId);
  return selected;
}

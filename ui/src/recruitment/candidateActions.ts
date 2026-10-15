import { createIdempotencyKey } from '../api/idempotency';
import { ApiProblemError } from '../api/problem';
import { talentApi } from '../talent/api';
import { candidateActionsApi, type CandidateActionOutcome, type CandidateActionTarget } from './candidateActionsApi';

export const CANDIDATE_ACTION_LIMIT = 1000;
// Keep each HTTP request short enough to report progress before the full selection completes.
const ACTION_BATCH_SIZE = 25;
export const actionError = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : error instanceof Error ? error.message : '操作失败，请重试';
const failed = (target: CandidateActionTarget, error: unknown): CandidateActionOutcome => ({...target,status:'FAILED',message:actionError(error),errorCode:error instanceof ApiProblemError?error.problem.code:'REQUEST_FAILED'});

export function summarizeCandidateActions(results:CandidateActionOutcome[]) {
  const succeeded:CandidateActionOutcome[]=[],failures:CandidateActionOutcome[]=[];
  const completedIds=new Set<string>();
  const byCandidate=new Map<string,CandidateActionOutcome>();
  for(const item of results){
    const id=item.candidateId;
    if(!byCandidate.has(id))byCandidate.set(id,item);
    if(item.status==='FAILED')failures.push(item);
    else {succeeded.push(item);completedIds.add(id);}
  }
  return {succeeded,failed:failures,completedIds,byCandidate};
}

/** One failed candidate must not discard successful work or prevent the remaining candidates. */
export async function executeCandidateActions({mode,candidates,jdVersionId,reason,onProgress,stopped,operationKey}: {
  mode:'advance'|'share'; candidates:CandidateActionTarget[]; jdVersionId?:string|undefined; reason:string;
  operationKey?:string;
  onProgress?:(results:CandidateActionOutcome[])=>void; stopped?:()=>boolean;
}): Promise<CandidateActionOutcome[]> {
  if (!candidates.length || candidates.length>CANDIDATE_ACTION_LIMIT) throw new Error(`每次请选择 1–${CANDIDATE_ACTION_LIMIT} 位候选人`);
  if (new Set(candidates.map(item=>item.candidateId)).size!==candidates.length) throw new Error('候选人重复，请重新选择');
  if (mode==='advance'&&!jdVersionId) throw new Error('请先选择推荐岗位');
  const result:CandidateActionOutcome[]=[];
  const requestKey=operationKey??createIdempotencyKey();
  const publish=(items:CandidateActionOutcome[])=>{result.push(...items);onProgress?.([...result]);};
  for(let offset=0;offset<candidates.length;offset+=ACTION_BATCH_SIZE) {
    if(stopped?.())break;
    const chunk=candidates.slice(offset,offset+ACTION_BATCH_SIZE);
    if(mode==='advance') {
      const resolved:Array<{candidateId:string;resumeVersionId:string}>=[];
      const resolvedByIndex:Array<{candidateId:string;resumeVersionId:string}|undefined>=new Array<{candidateId:string;resumeVersionId:string}|undefined>(chunk.length);
      let next=0;
      await Promise.all(Array.from({length:Math.min(6,chunk.length)},async()=>{
        while(next<chunk.length&&!stopped?.()) {
          const index=next++;const target=chunk[index]!;
          try {
            const resumeVersionId=target.resumeVersionId??(await talentApi.resumeVersions(target.candidateId)).items.reduce<{id:string;versionNo:number}|undefined>((latest,item)=>!latest||item.versionNo>latest.versionNo?item:latest,undefined)?.id;
            if(!resumeVersionId)throw new Error('尚无已确认入库的简历，请先在解析校验确认身份');
            resolvedByIndex[index]={candidateId:target.candidateId,resumeVersionId};
          }catch(error){publish([failed(target,error)]);}
        }
      }));
      resolved.push(...resolvedByIndex.filter((item):item is {candidateId:string;resumeVersionId:string}=>Boolean(item)));
      if(resolved.length) {
        try {
          const response=await candidateActionsApi.advance(jdVersionId!,resolved,reason,requestKey);
          const byCandidate=new Map(response.items.map(item=>[item.candidateId,item]));
          publish(resolved.map(item=>byCandidate.get(item.candidateId)??failed(item,new Error('服务未返回该候选人的处理结果，请重试核对'))));
        } catch(error) {publish(resolved.map(item=>failed(item,error)));}
      }
    } else {
      let index=0;
      await Promise.all(Array.from({length:Math.min(4,chunk.length)},async()=>{
        while(index<chunk.length&&!stopped?.()) {
          const target=chunk[index++]!;
          try {
            const state=(await talentApi.poolStatuses([target.candidateId]))[0];
            if(!state)throw new Error('候选人状态不可用，请重新读取');
            if(target.resumeVersionId&&target.resumeVersionId!==state.resumeVersionId)throw new Error('简历已更新，请查看最新简历后再转共享库');
            if(state.libraryEligible) {publish([{...target,status:'ALREADY_EXISTS',message:'已在共享简历库'}]);continue;}
            if(!state.canScreenOut)throw new Error('当前无权结束筛选，或仍有进行中的招聘流程');
            const person=await talentApi.candidate(target.candidateId);
            await talentApi.screenOut(person,reason,state.resumeVersionId??undefined);
            publish([{...target,status:'SUCCEEDED',message:'已转入共享简历库'}]);
          } catch(error) {publish([failed(target,error)]);}
        }
      }));
    }
  }
  return result;
}

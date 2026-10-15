import { MATCH_CANDIDATE_LIMIT } from './selectionCandidates';

/** History state carries only IDs; backend authorization remains authoritative. */
export function talentHandoff(value:unknown):{candidateIds:string[];jdId?:string|undefined} {
  if(!value||typeof value!=='object')return {candidateIds:[]};
  const input=value as {candidateIds?:unknown;jdId?:unknown};
  const candidateIds=Array.isArray(input.candidateIds)?[...new Set(input.candidateIds.filter((id):id is string=>typeof id==='string'&&id.length>0&&id.length<=100))]:[];
  if(candidateIds.length>MATCH_CANDIDATE_LIMIT)return {candidateIds:[]};
  return {candidateIds,jdId:typeof input.jdId==='string'&&input.jdId.length<=100?input.jdId:undefined};
}

import { collectCount, type CountPage } from '../api/count';
import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import { talentApi, type PersonalPoolStage } from '../talent/api';

export interface SearchCriteria { conditions?:string[];poolStage?:PersonalPoolStage; jdId?:string;query?:string;skills:string[];employers:string[];roles:string[];projects:string[];certificates:string[];languages:string[];degrees:string[];cities:string[];industries:string[];tags:string[];minWorkYears?:number;maxWorkYears?:number;sourceChannel?:string;intention?:string;gender?:string;minAge?:number;maxAge?:number;updatedFrom?:string;updatedTo?:string;naturalCondition?:string }
export interface LockSummary { lockId:string;candidateId:string;ownerUserId:string;ownerDepartmentId:string;contactName:string;acquiredAt:string;expiresAt:string;canContact:boolean;collaborationStatus:string|null }
export interface ConditionMatch { code:string;expected:string;matched:boolean;matchedValues:string[] }
export interface TalentSearchResult { candidateId:string;resumeVersionId:string;factSnapshotId:string;fullName:string|null;email:string|null;phone:string|null;currentRole:string|null;city:string|null;age:number|null;gender:string|null;jobIntention:string|null;sourceChannel:string|null;factUpdatedAt:string;skills:string[];employers:string[];roles:string[];projects:string[];certificates:string[];languages:string[];degrees:string[];industries:string[];tags:string[];workYears:number|null;engagementLock:LockSummary|null;conditionMatches:ConditionMatch[];applicationSummaries:unknown[];interviewSummaries:unknown[];clientSubmissionSummaries:unknown[] }
export interface SearchPage { items:TalentSearchResult[];nextCursor:string|null;hasMore:boolean;scanIncomplete?:boolean }
type NullableSearchCriteria = { [Key in keyof SearchCriteria]: SearchCriteria[Key] | null };

const values=(value:string|undefined)=>value?.split(/[,，]/).map(v=>v.trim()).filter(Boolean)??[];
export const emptyCriteria=():SearchCriteria=>({skills:[],employers:[],roles:[],projects:[],certificates:[],languages:[],degrees:[],cities:[],industries:[],tags:[]});
export const criteriaFromUrl=(params:URLSearchParams):SearchCriteria=>{const out=emptyCriteria();if(params.has('conditions'))out.conditions=params.getAll('conditions');const query=params.get('q');if(query)out.query=query;for(const key of ['skills','employers','roles','projects','certificates','languages','degrees','cities','industries','tags'] as const)out[key]=values(params.get(key)??undefined);for(const key of ['jdId','sourceChannel','intention','gender','updatedFrom','updatedTo','naturalCondition'] as const){const value=params.get(key);if(value)out[key]=value;}for(const key of ['minWorkYears','maxWorkYears','minAge','maxAge'] as const){const value=params.get(key);if(value!==null&&value!==''&&Number.isFinite(Number(value)))out[key]=Number(value);}return out;};
export const criteriaToUrl=(criteria:NullableSearchCriteria)=>{const p=new URLSearchParams();if(criteria.query)p.set('q',criteria.query);for(const value of criteria.conditions??[])p.append('conditions',value);for(const key of ['skills','employers','roles','projects','certificates','languages','degrees','cities','industries','tags'] as const){const selected=criteria[key];if(selected?.length)p.set(key,selected.join(','));}for(const key of ['poolStage','minWorkYears','maxWorkYears','jdId','sourceChannel','intention','gender','minAge','maxAge','updatedFrom','updatedTo','naturalCondition'] as const){const value=criteria[key];if(value!=null&&value!==''&&(typeof value!=='number'||Number.isFinite(value)))p.set(key,String(value));}return p;};
const searchQuery=(criteria:NullableSearchCriteria,cursor?:string,scope='SHARED',limit=20,candidateIds:string[]=[])=>{const p=new URLSearchParams();if(scope!=='SHARED')p.set('scope',scope);candidateIds.forEach(id=>p.append('candidateIds',id));if(criteria.query)p.set('q',criteria.query);for(const value of criteria.conditions??[])p.append('conditions',value);for(const key of ['skills','employers','roles','projects','certificates','languages','degrees','cities','industries','tags'] as const)for(const value of criteria[key]??[])p.append(key,value);for(const key of ['poolStage','minWorkYears','maxWorkYears','jdId','sourceChannel','intention','gender','minAge','maxAge','updatedFrom','updatedTo','naturalCondition'] as const){const value=criteria[key];if(value!=null&&value!==''&&(typeof value!=='number'||Number.isFinite(value)))p.set(key,String(value));}if(cursor)p.set('cursor',cursor);p.set('limit',String(limit));return p.toString();};
const idempotency=()=>createIdempotencyKey();
export const searchApi={
  count:(criteria:NullableSearchCriteria,scope:'SHARED'|'PERSONAL_POOL'='SHARED',candidateIds:string[]=[],signal?:AbortSignal)=>collectCount(cursor=>{const params=new URLSearchParams(searchQuery(criteria,cursor,scope,100,candidateIds));params.delete('limit');return request<CountPage>(`/talent-search/count?${params}`,{signal:signal??null});},signal),
  search:async(criteria:NullableSearchCriteria,cursor?:string,scope:'SHARED'|'PERSONAL_POOL'='SHARED',limit=20,candidateIds:string[]=[]):Promise<SearchPage>=>{
    let next=cursor;
    const seen=new Set<string>(cursor?[cursor]:[]);
    for(;;){
      const page=await request<SearchPage>(`/talent-search?${searchQuery(criteria,next,scope,limit,candidateIds)}`);
      // 权限与阶段实时过滤可能耗尽单次扫描预算，空批次不能作为用户看到的一页。
      if(page.items.length||!page.scanIncomplete||!page.hasMore)return page;
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('检索游标未推进，请刷新后重试');
      seen.add(page.nextCursor);
      next=page.nextCursor;
    }
  },
  personalCandidates:async(criteria:SearchCriteria,cursor?:string,limit=20)=>{
    const page=await searchApi.search(criteria,cursor,'PERSONAL_POOL',limit);
    const items=await talentApi.candidateBatch(page.items.map(hit=>hit.candidateId));
    return {...page,items};
  },
  rebuild:()=>request<{id:string;taskId:string;status:string}>('/search-index-rebuilds',{method:'POST',idempotencyKey:idempotency()}),
};

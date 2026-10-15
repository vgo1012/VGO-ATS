import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import { collectCount, type CountPage } from '../api/count';
import { normalizeDates, type DatedWire } from '../api/dates';
import type { CursorPage, PersonalScreeningSettings } from '../demand/api';

export type MatchSortMode = 'REFERENCE_SCORE' | 'PROFESSIONAL_SCORE' | 'BONUS_SCORE';
export interface MatchRun { ownerUserId?:string; id:string; taskId:string; jdVersionId:string; jdFactSnapshotId:string; planVersionId:string; weightProfileVersionId:string; personalPreferenceVersionId:string|null; selectionSnapshotId:string; sortMode:MatchSortMode; status:'QUEUED'|'RUNNING'|'SUCCEEDED'|'PARTIAL'|'FAILED'|'CANCELLED'; totalCount:number; succeededCount:number; failedCount:number; errorCode:string|null; version:number; createdAt:string; startedAt:string|null; finishedAt:string|null; updatedAt:string }
export interface DimensionResult { code:'CORE_SKILLS'|'ROLE_EXPERIENCE'|'RELATED_YEARS'|'RESPONSIBILITY_MATCH'|'INDUSTRY_AND_PROJECTS'; enabled:boolean; configuredWeight:number; status:'KNOWN'|'UNKNOWN'; score:number; explanation:string; highlights:string[] }
export interface CriterionResult { definitionId:string; code:string; level:'MUST'|'PREFER'|'DISPLAY_ONLY'; status:'PASS'|'FAIL'|'UNKNOWN'; selected:boolean; configuredBonus:number; awardedBonus:number; reason:string; actual:string[]; expected:string[] }
export interface AiAssessment { assessmentId:string; provider:string; model:string; promptVersion:string; schemaVersion:string; evaluatedOn:string; defaultProfessionalScore:number; summary:string }
export interface MatchDetails { jdFactSnapshotId:string; resumeFactSnapshotId:string; candidateId:string; resumeVersionId:string; professionalScore:number; bonusScore:number; referenceScore:number; mustTier:'PASS'|'UNKNOWN'|'FAIL'; selectedBonusCap:number; algorithmVersion?:string; aiAssessment?:AiAssessment; dimensions:DimensionResult[]; criteria:CriterionResult[] }
export interface MatchResult { resultId:string; baseEvaluationId:string; candidateId:string; resumeVersionId:string; professionalScore:number; bonusScore:number; referenceScore:number; mustTier:'PASS'|'UNKNOWN'|'FAIL'; stableRank:number; details:MatchDetails }
export interface MatchResultPage { items:MatchResult[]; nextCursor:string|null; hasMore:boolean }
export interface MatchJob { jdVersionId:string; latestRunId:string; lastRunAt:string; runCount:number; evaluationCount:number; succeededCount:number; failedCount:number; candidateCount:number; scoredCandidateCount:number; activeRunCount:number }
export interface MatchBasis { basisRunId:string; jdVersionId:string; basisKey:string; planVersionId:string; weightProfileVersionId:string; sortMode:MatchSortMode; lastRunAt:string; runCount:number; candidateCount:number; scoredCandidateCount:number; skillsWeight:number; roleWeight:number; yearsWeight:number; responsibilityWeight:number; industryProjectWeight:number; selectedCriterionIds:string[]; personalScreeningSettings:PersonalScreeningSettings|null }
export interface ScreeningSummary { total:number; strong:number; consider:number; reject:number; missing:number }
export interface JobMatchResult { runId:string; scoredAt:string; result:MatchResult }
export interface ScreeningSelectionIdentity { candidateId:string; resumeVersionId:string }
export interface ScreeningSelectionCandidate extends ScreeningSelectionIdentity { candidateName:string; resumeVersionNo:number; originalFileName:string; jdVersionId:string; defaultMatchRunId:string; admissionId:string; professionalScore:number; referenceScore:number }

const idempotency=()=>createIdempotencyKey();
export const matchingApi={
  screeningCandidates:(jdVersionId:string,query='',cursor?:string,limit=20)=>request<CursorPage<ScreeningSelectionCandidate>>(`/jd-versions/${encodeURIComponent(jdVersionId)}/screening-candidates?${new URLSearchParams({query,limit:String(limit),...(cursor?{cursor}:{})})}`),
  countScreeningCandidates:(jdVersionId:string,query='',signal?:AbortSignal)=>collectCount(cursor=>request<CountPage>(`/jd-versions/${encodeURIComponent(jdVersionId)}/screening-candidates/count?${new URLSearchParams({query,...(cursor?{cursor}:{})})}`,{signal:signal??null}),signal),
  jobs:(jdVersionId?:string,cursor?:string,signal?:AbortSignal)=>{const q=new URLSearchParams({limit:'20'});if(jdVersionId)q.set('jdVersionId',jdVersionId);if(cursor)q.set('cursor',cursor);return request<CursorPage<MatchJob>>(`/match-jobs?${q}`,signal?{signal}:{});},
  bases:(jdVersionId:string,cursor?:string,signal?:AbortSignal)=>request<CursorPage<MatchBasis>>(`/match-jobs/${encodeURIComponent(jdVersionId)}/bases?limit=20${cursor?`&cursor=${encodeURIComponent(cursor)}`:''}`,signal?{signal}:{}),
  jobResultsSummary:(jdVersionId:string,basisRunId:string)=>request<ScreeningSummary>(`/match-jobs/${encodeURIComponent(jdVersionId)}/results-summary?${new URLSearchParams({basisRunId})}`),
  jobResults:(jdVersionId:string,basisRunId:string,cursor?:string,limit=20)=>{const q=new URLSearchParams({limit:String(limit),basisRunId});if(cursor)q.set('cursor',cursor);return request<CursorPage<JobMatchResult>>(`/match-jobs/${encodeURIComponent(jdVersionId)}/results?${q}`);},
  createRun:(body:{jdVersionId:string;resumeVersionIds:string[];weightProfileVersionId:string;personalPreferenceVersionId?:string;selectedCriterionIds:string[];sortMode:MatchSortMode})=>request<DatedWire<MatchRun>>('/match-runs',{method:'POST',body,idempotencyKey:idempotency()}).then(normalizeDates<MatchRun>),
  run:(id:string)=>request<DatedWire<MatchRun>>(`/match-runs/${id}`).then(normalizeDates<MatchRun>),
  results:(id:string,cursor?:string,limit=20)=>request<MatchResultPage>(`/match-runs/${id}/results?limit=${limit}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`),
  cancel:(id:string)=>request(`/match-runs/${id}/cancel`,{method:'POST',body:{reason:'HR 取消本次批量匹配'}}),
  retry:(id:string)=>request(`/match-runs/${id}/retry-failed`,{method:'POST',body:{reason:'HR 重试失败的批量匹配'}}),
};

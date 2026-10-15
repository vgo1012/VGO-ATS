import { activityHeaders, type ActivityOrigin } from './activityApi';
import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import { collectCount, type CountPage } from '../api/count';
import type { CursorPage } from '../demand/api';

export type ApplicationStage='PATH_SELECTION'|'IN_PROCESS'|'DECISION'|'OFFER'|'TERMINATED'|'ONBOARDED';
export type StepStatus='AVAILABLE'|'NOT_SELECTED'|'PENDING'|'IN_PROGRESS'|'PASSED'|'FAILED'|'WITHDRAWN'|'SKIPPED';
export interface RecruitmentApplication {latestCompletedInterviewRoundNo?:number|null;activeInterviewRoundNos?:number[];currentInterviewType?:string|null;currentStepCode?:string|null;currentStepRoundNo?:number|null;currentStepStatus?:StepStatus|null;progressiveFollowUp?:boolean;clientId?:string|null;clientName?:string|null;onboardingStatus?:OnboardingProcessStatus|null;onboardingStartedAt?:string|null;actualOnboardedAt?:string|null;onboardingElapsedSeconds?:number|null;id:string;candidateId:string;demandId:string;jdId:string;jdVersionId:string;resumeVersionId:string;workflowTemplateVersionId:string;matchPlanVersionId:string;recruitmentType:'INTERNAL'|'EXTERNAL';currentStage:ApplicationStage;outcome:'PASS'|'REJECT'|'WITHDRAW'|null;ownerUserId:string;ownerDepartmentId:string;terminalAt:string|null;version:number;createId:string;createTime:string;updateId:string;updateTime:string}
export interface WorkflowStep {roundNo?:number;id:string;applicationId:string;templateVersionId:string;stepDefinitionId:string|null;stepCode:string;sequenceNo:number;candidateOptional:boolean;defaultFollowUpChannel:string|null;status:StepStatus;ownerUserId:string|null;dueAt:string|null;startedAt:string|null;completedAt:string|null;resultReason:string|null;clientSubmissionId:string|null;version:number}
export interface Decision {id:string;applicationId:string;recordType:'DECISION'|'CORRECTION';decision:'PASS'|'REJECT'|'WITHDRAW';reason:string;correctsRecordId:string|null;clientSubmissionId:string|null;decidedByUserId:string;decidedAt:string}
export interface OfferRecord {id:string;applicationId:string;recordType:'STATUS'|'CORRECTION'|'REOPEN';status:'NOT_STARTED'|'IN_PROGRESS'|'ISSUED'|'ACCEPTED'|'TERMINATED'|'ONBOARDED';terminationReason:string|null;applicationOutcome:string|null;reason:string;correctsRecordId:string|null;reopensRecordId:string|null;source:string;sourceReference:string|null;sourceStatus:string|null;occurredAt:string;operatedByUserId:string}
export interface OnboardingEvent {id:string;applicationId:string;onboardedAt:string;source:string;sourceReference:string|null;reason:string;operatedByUserId:string;createTime:string}
export type OnboardingProcessStatus='IN_PROGRESS'|'ONBOARDED'|'RETURNED';
export interface OnboardingProcess {applicationId:string;status:OnboardingProcessStatus;startedAt:string;actualOnboardedAt:string|null;closedAt:string|null;elapsedSeconds:number}
export interface ApplicationDetails {application:RecruitmentApplication;collaboratorUserIds:string[];workflowSteps:WorkflowStep[];decisions:Decision[];offerStatusRecords:OfferRecord[];elapsedSeconds:number}
export interface RecruitmentProcessingAccess {processingAllowed:boolean}
export interface RecruitmentProcessingAccess {processingAllowed:boolean}
export interface FollowUp {id:string;workflowStepId:string;channel:string;summary:string;nextPlan:string;contactedAt:string;recordedByUserId:string}
export interface Communication {id:string;applicationId:string;channel:string;summary:string;result:string;nextPlan:string;communicatedAt:string;recordedByUserId:string}
export interface CommunicationInput {channel:string;summary:string;result:string;nextPlan:string;communicatedAt?:string}
export interface ApplicationIntention {id:string;candidateId:string;applicationId:string;expectedSalaryMin:number|null;expectedSalaryMax:number|null;salaryPeriod:string|null;availability:string|null;intentionLocations:string|null;reason:string;recordedByUserId:string;createTime:string}

const query=(params:Record<string,string|number|string[]|undefined>)=>{const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>{if(Array.isArray(v))v.forEach(value=>q.append(k,value));else if(v!==undefined&&v!=='')q.set(k,String(v));});return q.size?`?${q}`:'';};
const idempotency=()=>createIdempotencyKey();
export const recruitmentApi={
  jdProcessingAccess:(jdVersionId:string)=>request<RecruitmentProcessingAccess>(`/jd-versions/${jdVersionId}/recruitment-processing-access`),
  stageCounts:(params:{from:string;to:string;jdId?:string|undefined;jdVersionId?:string|undefined})=>request<Array<{stage:string;count:number}>>(`/applications/stage-counts${query(params)}`),
  applications:(params:Record<string,string|number|string[]|undefined>={})=>request<CursorPage<RecruitmentApplication>>(`/applications${query({limit:20,...params})}`),
  countApplications:(params:Record<string,string|number|string[]|undefined>={},signal?:AbortSignal)=>{
    const filters={...params};delete filters.cursor;delete filters.limit;
    return collectCount(cursor=>request<CountPage>(`/applications/count${query({...filters,cursor})}`,{signal:signal??null}),signal);
  },
  application:(id:string)=>request<ApplicationDetails>(`/applications/${id}`),
  create:(body:{candidateId:string;jdVersionId:string;resumeVersionId:string;ownerUserId?:string|undefined;reason:string},key?:string)=>request<ApplicationDetails>('/applications',{method:'POST',body,idempotencyKey:key??idempotency()}),
  updateCollaborators:(value:RecruitmentApplication,collaboratorUserIds:string[],reason:string)=>request<ApplicationDetails>(`/applications/${value.id}`,{method:'PATCH',body:{collaboratorUserIds,version:value.version,reason}}),
  selectPath:(value:RecruitmentApplication,selectedStepIds:string[],reason:string,amend=false)=>request<ApplicationDetails>(`/applications/${value.id}/workflow-path-${amend?'amendments':'selections'}`,{method:'POST',body:{selectedStepIds,version:value.version,reason}}),
  nextAction:(value:RecruitmentApplication,stepCode:string,reason:string,origin?:ActivityOrigin,roundNo?:number)=>request<ApplicationDetails>(`/applications/${value.id}/next-actions`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body:{stepCode,reason,version:String(value.version),...(roundNo!==undefined?{roundNo}:{})}}),
  startStep:(applicationId:string,step:WorkflowStep)=>request<WorkflowStep>(`/applications/${applicationId}/workflow-steps/${step.id}/start`,{method:'POST',body:{version:step.version}}),
  followUp:(applicationId:string,stepId:string,body:{channel:string;summary:string;nextPlan:string;contactedAt?:string})=>request<FollowUp>(`/applications/${applicationId}/workflow-steps/${stepId}/follow-ups`,{method:'POST',body}),
  followUps:(applicationId:string,stepId:string)=>request<FollowUp[]>(`/applications/${applicationId}/workflow-steps/${stepId}/follow-ups`),
  communications:(applicationId:string)=>request<Communication[]>(`/applications/${applicationId}/communications`),
  intentions:(applicationId:string)=>request<ApplicationIntention[]>(`/applications/${applicationId}/intentions`),
  communicate:(applicationId:string,body:CommunicationInput,idempotencyKey:string,origin?:ActivityOrigin)=>request<Communication>(`/applications/${applicationId}/communications`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey}),
  completeStep:(applicationId:string,step:WorkflowStep,result:string,reason:string,clientSubmissionId?:string,communication?:{channel?:string|undefined;nextPlan?:string|undefined},origin?:ActivityOrigin)=>request<ApplicationDetails>(`/applications/${applicationId}/workflow-steps/${step.id}/complete`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body:{result,reason,clientSubmissionId,version:step.version,...communication}}),
  skipStep:(applicationId:string,step:WorkflowStep,reason:string)=>request<ApplicationDetails>(`/applications/${applicationId}/workflow-steps/${step.id}/skip`,{method:'POST',body:{reason,version:step.version}}),
  decide:(applicationId:string,decision:string,reason:string,clientSubmissionId?:string,origin?:ActivityOrigin)=>request<Decision>(`/applications/${applicationId}/decisions`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body:{decision,reason,clientSubmissionId},idempotencyKey:origin?.operationId??idempotency()}),
  correctDecision:(applicationId:string,decisionId:string,decision:string,reason:string,clientSubmissionId?:string)=>request<Decision>(`/applications/${applicationId}/decisions/${decisionId}/corrections`,{method:'POST',body:{decision,reason,clientSubmissionId},idempotencyKey:idempotency()}),
  transfer:(value:RecruitmentApplication,targetUserId:string,reason:string)=>request<ApplicationDetails>(`/applications/${value.id}/transfer`,{method:'POST',body:{targetUserId,reason,version:value.version}}),
  addOffer:(applicationId:string,body:{status:string;terminationReason?:string|undefined;applicationOutcome?:string|undefined;reason:string;occurredAt?:string|undefined;sourceReference?:string|undefined;sourceStatus?:string|undefined},origin?:ActivityOrigin)=>request<OfferRecord>(`/applications/${applicationId}/offer-status-records`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:origin?.operationId??idempotency()}),
  correctOffer:(applicationId:string,recordId:string,body:{status:string;terminationReason?:string|undefined;applicationOutcome?:string|undefined;reason:string},origin?:ActivityOrigin)=>request<OfferRecord>(`/applications/${applicationId}/offer-status-records/${recordId}/corrections`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:origin?.operationId??idempotency()}),
  onboard:(applicationId:string,body:{onboardedAt?:string;sourceReference?:string;reason:string},origin?:ActivityOrigin)=>request(`/applications/${applicationId}/onboarding-events`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:origin?.operationId??idempotency()}),
  hire:(applicationId:string,body:{offerStatus:'IN_PROGRESS'|'ISSUED';reason:string},origin?:ActivityOrigin)=>request<OnboardingProcess>(`/applications/${applicationId}/hire`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:origin?.operationId??idempotency()}),
  reviewOnboarding:(applicationId:string,body:{onboardedAt:string;reason?:string})=>request<OnboardingProcess>(`/applications/${applicationId}/onboarding-reviews`,{method:'POST',body,idempotencyKey:idempotency()}),
  returnOnboardingToShared:(applicationId:string,body:{reasonCategory?:'LIBRARY_RETURN'|'CANDIDATE'|'POSITION'|'OTHER';reasonDetail?:string})=>request<OfferRecord>(`/applications/${applicationId}/onboarding-return`,{method:'POST',body,idempotencyKey:idempotency()}),
};

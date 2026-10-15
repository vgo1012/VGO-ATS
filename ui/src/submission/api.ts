import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import type { CursorPage } from '../demand/api';

export interface ClientSubmission {
  id:string; applicationId:string; candidateId:string; demandId:string; clientId:string; projectId:string|null;
  positionId:string; jdId:string; jdVersionId:string; resumeVersionId:string; deliveryStatus:'DRAFT'|'SENT'|'WITHDRAWN';
  processStatus:'NOT_STARTED'|'SCREENING'|'INTERVIEWING'|'ACCEPTED'|'REJECTED'|'EXPIRED'; sentAt:string|null;
  withdrawnAt:string|null; withdrawalReason:string|null; ownerUserId:string; version:number; createdAt:string; updatedAt:string;
}
export interface SendSnapshot {id:string;submissionId:string;resumeVersionId:string;recipient:string;channel:string;fileName:string;contentSha256:string;sentByUserId:string;sentAt:string}
export interface SubmissionFeedback {id:string;submissionId:string;result:string;reasonCategory:string;nextAction:string;note:string|null;sourceChannel:string;sourceOccurredAt:string;recordedByUserId:string;createdAt:string}
export interface RiskAcknowledgement {id:string;submissionId:string;riskCode:string;reason:string;acknowledgedByUserId:string;createdAt:string}
export interface SubmissionDetails {submission:ClientSubmission;sendSnapshot:SendSnapshot|null;feedback:SubmissionFeedback[];riskAcknowledgements:RiskAcknowledgement[]}
export interface Eligibility {applicationId:string;candidateId:string;clientId:string;companyBlacklist:boolean;clientRestriction:boolean;eligible:boolean;blockingCodes:string[]}
export interface RestrictionSummary {restrictionType:'COMPANY_BLACKLIST'|'CLIENT_RESTRICTION';clientId:string|null;clientName:string|null;reason:string;sourceChannel:string;occurredAt:string;expiresAt:string|null;blocking:boolean}
export interface EducationRecord {id:string;candidateId:string;educationFactId:string;applicationId:string;recordType:string;verificationStatus:string;studyMode:string;sourceChannel:string;evidenceReference:string|null;note:string|null;recordedByUserId:string;occurredAt:string}
export interface CompanyBlacklistView {record:{id:string;candidateId:string;recordType:string;status:string;reasonCode:string;reasonDetail:string;sourceChannel:string;evidenceReference:string;occurredAt:string};affectedWorkflowCount:number}

const idempotency=()=>createIdempotencyKey();
const q=(params:Record<string,string|number|undefined>)=>{const search=new URLSearchParams();Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')search.set(key,String(value));});const value=search.toString();return value?`?${value}`:'';};

export const submissionApi={
  list:(applicationId:string)=>request<CursorPage<ClientSubmission>>(`/client-submissions${q({applicationId,limit:20})}`),
  details:(id:string)=>request<SubmissionDetails>(`/client-submissions/${id}`),
  eligibility:(applicationId:string)=>request<Eligibility>(`/client-submissions/eligibility${q({applicationId})}`),
  create:(applicationId:string,key?:string)=>request<SubmissionDetails>('/client-submissions',{method:'POST',body:{applicationId},idempotencyKey:key??idempotency()}),
  send:(value:ClientSubmission,body:{recipient:string;channel:string;reason:string},key?:string)=>request<SubmissionDetails>(`/client-submissions/${value.id}/send`,{method:'POST',body:{...body,version:value.version},idempotencyKey:key??idempotency()}),
  feedback:(id:string,body:{result:string;reasonCategory:string;nextAction:string;note?:string;sourceChannel:string;sourceOccurredAt?:string;explicitClientBan:boolean;restrictionExpiresAt?:string;restrictionIntent?:string})=>request<SubmissionFeedback>(`/client-submissions/${id}/feedback`,{method:'POST',body,idempotencyKey:idempotency()}),
  withdraw:(value:ClientSubmission,reason:string)=>request<SubmissionDetails>(`/client-submissions/${value.id}/withdraw`,{method:'POST',body:{reason,version:value.version},idempotencyKey:idempotency()}),
  acknowledgeRisk:(id:string,riskCode:string,reason:string)=>request<RiskAcknowledgement>(`/client-submissions/${id}/risk-acknowledgements`,{method:'POST',body:{riskCode,reason}}),
  restrictions:(candidateId:string)=>request<RestrictionSummary[]>(`/candidates/${candidateId}/restriction-summaries`),
  education:(candidateId:string)=>request<EducationRecord[]>(`/candidates/${candidateId}/education-verification-records?limit=20`),
  addEducation:(applicationId:string,body:{candidateId:string;educationFactId:string;recordType:string;verificationStatus:string;studyMode:string;sourceChannel:string;evidenceReference?:string;note?:string})=>request<EducationRecord>(`/applications/${applicationId}/education-verification-records`,{method:'POST',body}),
  addCompanyBlacklist:(candidateId:string,body:{reasonCode:string;reasonDetail:string;sourceChannel:string;evidenceReference:string})=>request<CompanyBlacklistView>(`/candidates/${candidateId}/company-blacklist-records`,{method:'POST',body}),
};

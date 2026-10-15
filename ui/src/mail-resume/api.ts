import { request } from '../api/http';
import { collectCount, type CountPage } from '../api/count';
import type { CursorPage } from '../demand/api';

export interface MailResumeReview {
  id:string; receivedAt:string; sender:string|null; subject:string|null; attachmentName:string|null;
  parsedJdCode:string|null; parsedJdTitle:string|null; parsedRegion:string|null;
  failureCode:string|null; failureDetail:string|null; status:string; matchType:string|null;
  jdId:string|null; jdVersionId:string|null; intakeId:string|null; linkedByUserId:string|null;
  linkedAt:string|null; version:string;
}

export interface MailJdOption { id:string; code:string|null; title:string; currentVersionNo:number; regions:string[] }
export interface MailIngestRun {
  id:string; status:'QUEUED'|'RUNNING'|'PARTIAL'|'SUCCEEDED'|'FAILED'; scannedCount:number;
  attachmentCount:number; autoMatchedCount:number; reviewRequiredCount:number; duplicateCount:number;
  failedCount:number; recentErrorCode:string|null; recentErrorDetail:string|null;
}

export interface MailResumeFilters { from?:string; to?:string; title?:string; region?:string; attachment?:string }
const query=(values:Record<string,string|number|undefined>)=>{const params=new URLSearchParams();Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&value!=='')params.set(key,String(value));});return params.toString();};

export const mailResumeApi={
  reviews:(cursor:string|undefined,filters:MailResumeFilters,limit=20)=>request<CursorPage<MailResumeReview>>(`/mail-resume-reviews?${query({...filters,cursor,limit})}`),
  countReviews:(filters:MailResumeFilters,signal?:AbortSignal)=>collectCount(
    ()=>{const suffix=query({...filters});return request<CountPage>(`/mail-resume-reviews/count${suffix?`?${suffix}`:''}`,{signal:signal??null});},signal),
  jds:(search:string)=>request<MailJdOption[]>(`/mail-resume-jds?${query({query:search})}`),
  link:(id:string,body:{jdId:string;reason:string;saveExactRule:boolean})=>request<MailResumeReview>(`/mail-resume-reviews/${id}/jd`,{method:'PUT',body}),
  run:(id:string)=>request<MailIngestRun>(`/admin/mail-resume-ingest-runs/${id}`),
};

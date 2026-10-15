import { request } from '../api/http';
import type { CursorPage } from '../demand/api';

export interface BlacklistRecord {
  id:string; candidateId:string; recordType:string; status:string; reasonCode:string;
  reasonDetail:string; sourceChannel:string; evidenceReference:string; correctsRecordId:string|null;
  recordedByUserId:string; occurredAt:string; createTime:string;
}
export interface BlacklistHistory {current:BlacklistRecord|null; active:boolean; records:CursorPage<BlacklistRecord>}
export interface AffectedWorkflow {resourceType:string; resourceId:string; applicationId:string; clientId:string|null; jdId:string; status:string; ownerUserId:string; updateTime:string}
export interface BlacklistCommand {reasonCode:string; reasonDetail:string; sourceChannel:string; evidenceReference:string}
const query=(cursor?:string)=>{const params=new URLSearchParams({limit:'20'});if(cursor)params.set('cursor',cursor);return params.toString();};
export const riskApi={
  history:(candidateId:string,cursor?:string)=>request<BlacklistHistory>(`/candidates/${candidateId}/company-blacklist-records?${query(cursor)}`),
  affected:(recordId:string,cursor?:string)=>request<CursorPage<AffectedWorkflow>>(`/company-blacklist-records/${recordId}/affected-workflows/page?${query(cursor)}`),
  correct:(recordId:string,body:BlacklistCommand)=>request(`/company-blacklist-records/${recordId}/corrections`,{method:'POST',body}),
  disable:(recordId:string,reason:string)=>request(`/company-blacklist-records/${recordId}/disable`,{method:'POST',body:{reason}}),
};

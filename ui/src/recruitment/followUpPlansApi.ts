import { activityHeaders, type ActivityOrigin } from './activityApi';
import { request } from '../api/http';
import type { CursorPage } from '../demand/api';
import type { QueryClient } from '@tanstack/react-query';

export interface FollowUpPlan {
  id:string;applicationId:string;candidateId:string;jdId:string;jdVersionId:string;
  recruitmentType:'INTERNAL'|'EXTERNAL';clientId:string|null;clientName:string|null;ownerUserId:string;
  startsAt:string;endsAt:string;channel:string;note:string;resultReason?:string|null;status:'SCHEDULED'|'COMPLETED'|'CANCELLED';
  version:string;createTime:string;updateTime:string;
}
export interface PlanFields {startsAt:string;endsAt:string;channel:string;note:string;version:string}
export interface PlanCompletion {
  result:'CONTINUE'|'DECLINED'|'NO_RESPONSE'|'FOLLOW_UP';
  nextPlan?:string;workflowStepId?:string;workflowStepVersion?:string;
}
export type PlanSnapshot=Pick<FollowUpPlan,'startsAt'|'endsAt'|'channel'|'note'|'status'|'resultReason'>;
export interface FollowUpPlanEvent {
  id:string;planId:string;action:string;reason:string;actorUserId:string;occurredAt:string;
  before:PlanSnapshot|null;after:PlanSnapshot;
}
export async function refreshFollowUpPlans(client:QueryClient,applicationId:string) {
  await Promise.all([
    ['follow-up-plans'],['follow-up-plan-history',applicationId],['application',applicationId],
    ['application-communications',applicationId],['applications'],
  ].map(queryKey=>client.invalidateQueries({queryKey})));
}
export const followUpPlansApi={
  list:(params:Record<string,string|number|undefined>)=>{const query=new URLSearchParams();Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')query.set(key,String(value));});query.set('limit','20');return request<CursorPage<FollowUpPlan>>(`/follow-up-plans?${query}`);},
  create:(applicationId:string,body:PlanFields,key:string,origin?:ActivityOrigin)=>request<FollowUpPlan>(`/applications/${applicationId}/follow-up-plans`,{...(origin?{headers:activityHeaders(origin)}:{}),method:'POST',body,idempotencyKey:key}),
  reschedule:(plan:FollowUpPlan,body:PlanFields,origin?:ActivityOrigin)=>request<FollowUpPlan>(`/applications/${plan.applicationId}/follow-up-plans/${plan.id}`,{...(origin?{headers:activityHeaders(origin)}:{}),method:'PATCH',body}),
  finish:(plan:FollowUpPlan,action:'cancel'|'complete',reason:string,completion?:PlanCompletion,idempotencyKey?:string,origin?:ActivityOrigin)=>request<FollowUpPlan>(`/applications/${plan.applicationId}/follow-up-plans/${plan.id}/${action}`,{...(origin?{headers:activityHeaders(origin)}:{}),method:'POST',body:{reason,version:plan.version,...completion},...(idempotencyKey?{idempotencyKey}:{})}),
  history:(applicationId:string,cursor?:string)=>request<CursorPage<FollowUpPlanEvent>>(`/applications/${applicationId}/follow-up-plan-history?${new URLSearchParams({limit:'20',...(cursor?{cursor}:{})})}`),
};

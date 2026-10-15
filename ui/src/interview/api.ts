import { activityHeaders, type ActivityOrigin } from '../recruitment/activityApi';
import { createIdempotencyKey } from '../api/idempotency';
import { request, requestFile } from '../api/http';
import { downloadFile } from '../api/download';
import { normalizeDates } from '../api/dates';
import type { CursorPage } from '../demand/api';

export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type InterviewType = 'INTERNAL' | 'EXTERNAL' | 'EXTERNAL_CLIENT' | 'EXTERNAL_CLIENT_AI';

export interface Interview {
  candidateName?:string|null;jdTitle?:string|null;jdCode?:string|null;
  clientId?:string|null;clientName?:string|null;initialComment?:string|null;
  id:string; applicationId:string; candidateId:string; jdId:string; jdVersionId:string;
  workflowTemplateVersionId:string; workflowStepId:string; stepCode:string; interviewType:InterviewType;
  roundNo:number; clientSubmissionId:string|null; status:InterviewStatus; scheduledStartAt:string;
  scheduledEndAt:string; timezoneId:string; meetingMode:string; locationText:string|null; meetingUrl:string|null;
  candidateNotificationChannel:string; candidateConfirmationStatus:string; ownerUserId:string;
  ownerDepartmentId:string; nextResponsibleUserId:string; version:number; createdByUserId:string;
  createdAt:string; updatedByUserId:string; updatedAt:string;
}

export interface InterviewParticipant {interviewId:string;userId:string;participantRole:string;responseStatus:string;createdAt:string}
export type InterviewExternalCalendarMode='LOCAL_ONLY'|'WECOM_SCHEDULE'|'WECOM_MEETING';
export interface InterviewExternalCalendarConfig {mode:InterviewExternalCalendarMode;title?:string;description?:string;adminUserId?:string;additionalInviteeUserIds?:string[];reminderMinutes?:number;waitingRoomEnabled?:boolean;allowEnterBeforeHost?:boolean;enterMuteEnabled?:boolean}
export interface InterviewExternalCalendar {mode:InterviewExternalCalendarMode;activeType:Exclude<InterviewExternalCalendarMode,'LOCAL_ONLY'>|null;status:'PENDING'|'PROCESSING'|'SYNCED'|'CANCELLED'|'FAILED';title:string|null;description:string|null;adminUserId:string|null;inviteeUserIds:string[];reminderMinutes:number;waitingRoomEnabled:boolean;allowEnterBeforeHost:boolean;enterMuteEnabled:boolean;externalObjectId:string|null;meetingCode:string|null;meetingLink:string|null;remoteStatus:string|null;lastErrorCode:string|null;lastErrorDetail:string|null;attemptCount:number;lastSyncedAt:string|null}
export interface WecomCollaborationMember {userId:string;name:string;position:string|null;linkedUserAccountId:string|null}
export interface ScheduleHistory {id:string;interviewId:string;actionType:string;beforeSnapshot:string|null;afterSnapshot:string;reason:string;operatedByUserId:string;occurredAt:string}
export interface Feedback {id:string;interviewId:string;authorUserId:string;feedbackKind:string;status:'DRAFT'|'SUBMITTED';currentVersionNo:number;createdAt:string;updatedAt:string}
export interface FeedbackVersion {id:string;feedbackId:string;versionNo:number;verdict:'PASS'|'FAIL';shortComment:string|null;externalFeedbackSource:string|null;feedbackOccurredAt:string;changeReason:string;createdByUserId:string;createdAt:string}
export interface FeedbackView {feedback:Feedback;currentVersion:FeedbackVersion}
export interface FeedbackBatch {visibleInterviewIds:string[];items:FeedbackView[]}
export interface NotificationIntent {id:string;interviewId:string;notificationType:string;recipientType:string;recipientReference:string;channel:string;templateVersionId:string;renderedSubject:string;renderedBody:string;status:string;externalMessageId:string|null;lastErrorCode:string|null;lastErrorDetail:string|null;retryCount:number;version:number;createdAt:string;updatedAt:string}
export interface InterviewDetails {interview:Interview;participants:InterviewParticipant[];externalCalendar?:InterviewExternalCalendar|null}
export interface InterviewJobOption {jdId:string;jdVersionId:string;jdVersionNo:number;title:string}
export interface InterviewMaterial {
  interviewId:string; candidateId:string; candidateName:string; jdId:string; jdVersionId:string;
  jdVersionNo:number; jdTitle:string; jdCode?:string|null; jdRawText:string; resumeVersionId:string; resumeVersionNo:number;
  resumeFileName:string; resumeMediaType:string; resumeSizeBytes:number;
}
export interface InterviewTask {candidateName?:string|null;interviewId:string;applicationId:string;candidateId:string;jdId:string;stepCode:string;scheduledStartAt:string;scheduledEndAt:string;timezoneId:string;meetingMode:string;locationText:string|null;meetingUrl:string|null;feedbackDueAt:string;overdue:boolean;submitted:boolean}

export interface InterviewCreate {
  applicationId:string; workflowStepId:string; startAt:string; endAt:string; timezoneId:string;
  meetingMode:string; locationText?:string; meetingUrl?:string; participantUserIds:string[];
  clientSubmissionId?:string; reason:string; interviewType?:'INTERNAL'|'EXTERNAL'; initialComment?:string;
  externalCalendar?:InterviewExternalCalendarConfig;
}

const query=(params:Record<string,string|number|undefined>)=>{const search=new URLSearchParams();Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')search.set(key,String(value));});return search.size?`?${search}`:'';};
const key=()=>createIdempotencyKey();
const normalizeFeedback=(value:FeedbackView):FeedbackView=>({...value,feedback:normalizeDates(value.feedback),currentVersion:normalizeDates(value.currentVersion)});
const normalizeDetails=(value:InterviewDetails):InterviewDetails=>({...value,interview:normalizeDates(value.interview),participants:value.participants.map(normalizeDates)});

export const interviewApi={
  feedbackBatchPage:(ids:string[],cursor?:string)=>{
    const params=new URLSearchParams(ids.map(id=>['interviewIds',id]));
    params.set('limit','20');
    if(cursor)params.set('cursor',cursor);
    return request<CursorPage<FeedbackView>&FeedbackBatch>(`/interview-feedback-batch?${params}`).then(page=>({...page,items:page.items.map(normalizeFeedback)}));
  },
  feedbackBatchAll:async(ids:string[]):Promise<FeedbackBatch>=>{
    const requested=[...new Set(ids)].sort();
    if(!requested.length)return {visibleInterviewIds:[],items:[]};
    if(requested.length>100)throw new Error('一次最多读取 100 场面试的面评');
    const items:FeedbackView[]=[];const seen=new Set<string>();let cursor:string|undefined;
    for(;;){
      const page=await interviewApi.feedbackBatchPage(requested,cursor);
      items.push(...page.items);
      if(!page.hasMore){
        const visible=new Set(page.visibleInterviewIds);
        return {visibleInterviewIds:page.visibleInterviewIds,items:items.filter(item=>visible.has(item.feedback.interviewId))};
      }
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('面评分页读取异常，请重试');
      seen.add(page.nextCursor);cursor=page.nextCursor;
    }
  },
  invitedJobs:(params:{query?:string;jdId?:string;cursor?:string})=>request<CursorPage<InterviewJobOption>>(`/my/interview-job-options${query({...params,limit:20})}`),
  materials:(id:string)=>request<InterviewMaterial>(`/interviews/${id}/materials`),
  previewResume:(id:string)=>requestFile(`/interviews/${id}/materials/resume`),
  downloadResume:(id:string,fileName:string)=>downloadFile(`/interviews/${id}/materials/resume`,fileName),
  list:(params:Record<string,string|number|undefined>={})=>request<CursorPage<Interview>>(`/interviews${query({...params,limit:20})}`).then(page=>({...page,items:page.items.map(normalizeDates)})),
  allForApplication:async(applicationId:string):Promise<Interview[]>=>{
    const items:Interview[]=[]; const seen=new Set<string>(); let cursor:string|undefined;
    for(;;){
      const page=await interviewApi.list({applicationId,cursor});
      items.push(...page.items);
      if(!page.hasMore)return items;
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('流程面试分页读取异常，请重试');
      seen.add(page.nextCursor);cursor=page.nextCursor;
    }
  },
  get:(id:string)=>request<InterviewDetails>(`/interviews/${id}`).then(normalizeDetails),
  feedbackPage:(id:string,cursor?:string)=>request<CursorPage<FeedbackView>>(`/interviews/${id}/feedback${query({cursor,limit:20})}`).then(page=>({...page,items:page.items.map(normalizeFeedback)})),
  feedbackByAuthor:async(id:string,authorId:string):Promise<FeedbackView|null>=>{
    let cursor:string|undefined; const seen=new Set<string>();
    for(;;){
      const page=await interviewApi.feedbackPage(id,cursor);
      const own=page.items.find(item=>item.feedback.authorUserId===authorId);
      if(own)return own;
      if(!page.hasMore)return null;
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('面评分页读取异常，请重试');
      seen.add(page.nextCursor);cursor=page.nextCursor;
    }
  },
  feedbackAll:async(id:string):Promise<FeedbackView[]>=>{
    const items:FeedbackView[]=[]; const seen=new Set<string>(); let cursor:string|undefined;
    for(;;){
      const page=await interviewApi.feedbackPage(id,cursor);
      items.push(...page.items);
      if(!page.hasMore)return items;
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('面评分页读取异常，请重试');
      seen.add(page.nextCursor);cursor=page.nextCursor;
    }
  },
  notificationsPage:(id:string,cursor?:string)=>request<CursorPage<NotificationIntent>>(`/interviews/${id}/notifications${query({cursor,limit:20})}`).then(page=>({...page,items:page.items.map(normalizeDates)})),
  historyPage:(id:string,cursor?:string)=>request<CursorPage<ScheduleHistory>>(`/interviews/${id}/schedule-history${query({cursor,limit:20})}`),
  create:(body:InterviewCreate,requestKey?:string,origin?:ActivityOrigin)=>request<InterviewDetails>('/interviews',{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:requestKey??key()}).then(normalizeDetails),
  cancel:(value:Interview,reason:string,origin?:ActivityOrigin)=>request<InterviewDetails>(`/interviews/${value.id}/cancel`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body:{version:value.version,reason},idempotencyKey:origin?.operationId??key()}).then(normalizeDetails),
  retryExternalCalendar:(id:string)=>request<InterviewDetails>(`/interviews/${id}/external-calendar/retry`,{method:'POST'}).then(normalizeDetails),
  feedback:(id:string,body:{verdict:string;shortComment?:string;externalFeedbackSource?:string;feedbackOccurredAt?:string;status:string;changeReason:string},requestKey=key(),origin?:ActivityOrigin)=>request<FeedbackView>(`/interviews/${id}/feedback`,{method:'POST',...(origin?{headers:activityHeaders(origin)}:{}),body,idempotencyKey:requestKey}).then(normalizeFeedback),
  recordManualSend:(id:string,body:{channel:string;externalMessageId?:string;sentAt?:string})=>request<NotificationIntent>(`/notifications/${id}/record-manual-send`,{method:'POST',body,idempotencyKey:key()}),
  retry:(id:string)=>request<NotificationIntent>(`/notifications/${id}/retry`,{method:'POST',idempotencyKey:key()}),
  myTasks:()=>request<InterviewTask[]>('/my/interview-tasks?limit=20'),
};

export const wecomCollaborationApi={
  members:(queryText='')=>request<WecomCollaborationMember[]>(`/wecom/collaboration/members${query({query:queryText,limit:50})}`),
};

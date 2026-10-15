import { collectCount, type CountPage } from '../api/count';
import { createIdempotencyKey } from '../api/idempotency';
import { request, requestFile } from '../api/http';
import { downloadFile } from '../api/download';
import { normalizeDates, type DatedWire } from '../api/dates';
import type { CursorPage } from '../demand/api';
export interface CandidateJobContext {jdCode?:string|null;jdId:string;jdVersionId:string;title:string;versionNo:number;resumeVersionId:string;status:'PENDING'|'AI_SCREENED'|'ADVANCED'|'FINISHED';stage:string|null;applicationId:string|null;matchRunId:string|null}
export type PersonalPoolStage = 'PENDING_SCREENING'|'PENDING_DISPOSAL';
export interface CandidateLifecycle { phase:PersonalPoolStage|'SHARED'|'CLOSED'; warningLevel:'NORMAL'|'YELLOW'|'RED'; enteredAt:string; elapsedWorkdays:number; nextTransitionAt:string|null; shareDueAt:string|null; pauseReason:string|null; sharedAt:string|null; shareReason:string|null; version:string; cycleId:string }
export interface CandidatePoolStatus {candidateId:string;candidateName?:string;resumeVersionId:string|null;libraryEligible:boolean;screenedOut:boolean;canScreenOut:boolean;openIdentityConflictCount?:number;stages:string[];jobContexts?:CandidateJobContext[];lifecycle?:CandidateLifecycle|null}
export interface ScreeningCandidate {
  candidateId:string;
  resumeVersionId:string;
  jdVersionId:string;
  defaultMatchRunId:string;
  defaultResult:{professionalScore:number;bonusScore:number;referenceScore:number;mustTier:'PASS'|'UNKNOWN'|'FAIL'};
  admissionStatus:'READY'|'ADMITTED';
  ownedByCurrentActor?:boolean;
  admissionId:string|null;
  admissionVersion:string|null;
  admittedAt:string|null;
}
export interface ScreeningAdmissionResult {
  requestedCount:number;
  admittedCount:number;
  alreadyAdmittedCount:number;
  items:Array<Pick<ScreeningCandidate,'candidateId'|'resumeVersionId'|'jdVersionId'|'defaultMatchRunId'|'admissionStatus'|'admissionId'|'admittedAt'>>;
}
export interface ScreeningReturnItem { admissionId:string; expectedVersion:string }
export interface ScreeningReturnResult { requestedCount:number; returnedCount:number; alreadyReturnedCount:number }
export interface SharedCandidateReclaimResult { reclaimedCount:number; candidateIds:string[] }
export interface SharedCandidateReclaimScoreResult { operationId:string; requestedCount:number; items:Array<{scoreBindingId:string;candidateId:string;resumeVersionId:string;jdId:string;jdVersionId:string;defaultMatchRunId:string;taskId:string}> }
export interface CandidateRecruitmentHistory {
  items:Array<{
    applicationId:string;jdId:string;jdVersionId:string;resumeVersionId:string;recruitmentType:'INTERNAL'|'EXTERNAL';currentStage:string;outcome:string|null;originalOwnerUserId:string|null;currentOwnerUserId:string;createTime:string;terminalAt:string|null;
    stageEvents:Array<{fromStage:string|null;toStage:string;reason:string|null;operatedByUserId:string;occurredAt:string}>;
    ownershipEvents:Array<{actionType:string;fromUserId:string|null;toUserId:string;reason:string|null;operatedByUserId:string;occurredAt:string}>;
    interviews:Array<{interviewId:string;roundNo:number;interviewType:string;status:string;scheduledStartAt:string;scheduledEndAt:string;ownerUserId:string;feedbacks:Array<{feedbackId:string;authorUserId:string;feedbackKind:string;verdict:string|null;shortComment:string|null;feedbackOccurredAt:string|null}>}>;
  }>;
}

export interface CurrentIntakeImportResult {
  intake:ResumeIntake;
  outcome:'STARTED'|'ALREADY_PROCESSING'|'ALREADY_IMPORTED';
}

export interface SourceChannel { id:string; code:string; name:string; channelType:'MANUAL'|'BATCH'|'MAIL'|'BOSS'; status:'ACTIVE'|'DISABLED'; createId?:string|null; version:number; updatedAt:string }
export interface IntakeBatch { id:string; jdVersionId?:string|null; sourceChannelId:string; sourceRecruiterUserId:string; defaultOwnerUserId:string; ownerDepartmentId:string; status:string; totalCount:number; succeededCount:number; failedCount:number; completedCount:number; processingCount:number; parsingCount:number; parsedCount:number; attentionCount:number; ignoredCount:number; createdAt:string }
export interface ResumeIntake { jdCode?:string|null; id:string; jdVersionId?:string|null; jdTitle?:string|null; batchId:string; sourceChannelId:string; sourceRecruiterUserId:string; defaultOwnerUserId:string; ownerUserId:string; ownerDepartmentId:string; handledByUserId:string|null; originalFileName:string; mediaType:string; sizeBytes:number; contentSha256:string; scanStatus:'CLEAN'|'MALICIOUS'|'UNAVAILABLE'; status:'PENDING_IMPORT'|'PARSING'|'IDENTITY_REVIEW_REQUIRED'|'COMPLETED'|'READY_FOR_CONFIRMATION'|'CONFIRMED'|'IGNORED'|'REJECTED'|'QUARANTINED'|'FAILED_RETRYABLE'; importStatus:'PENDING'|'PROCESSING'|'IMPORTED'; processingId?:string|null; processingStatus?:'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED_RETRYABLE'|'FAILED_FINAL'|null; processingAttempt?:number|null; parsedFullName?:string|null; parsedEmail?:string|null; parsedPhone?:string|null; duplicateContent?:boolean; scoreRunId?:string|null; scoreStatus?:'NOT_APPLICABLE'|'QUEUED'|'WAITING_JD'|'WAITING_CONFIG'|'RUNNING'|'SUCCEEDED'|'FAILED_RETRYABLE'|'FAILED_FINAL'|null; failureCode:string|null; candidateId:string|null; resumeVersionId:string|null; version:number; createdAt:string }
export interface Candidate { id:string; fullName:string; primaryEmail:string|null; primaryPhone:string|null; ownerUserId:string; ownerDepartmentId:string; status:'ACTIVE'|'DELETED'; version:number; createdAt:string; updatedAt:string }
export interface ResumeSource { intakeId:string; sourceChannelId:string; sourceChannelName:string|null; channelType:string|null; sourceRecruiterUserId:string; receivedAt:string }
export interface ResumeVersionSource { resumeVersionId:string; source:ResumeSource|null }
export interface ResumeVersion { id:string; resumeId:string; intakeId:string; versionNo:number; originalFileName:string; mediaType:string; sizeBytes:number; contentSha256:string; status:string; createdAt:string }
export type ResumeVersionReference = Pick<ResumeVersion,'id'|'versionNo'|'originalFileName'>;
export interface IdentityConflict { id:string; intakeId:string; candidateIdA:string|null; candidateIdB:string|null; conflictType:string; status:'OPEN'|'RESOLVED'; resolution:string|null; chosenCandidateId:string|null; reason:string|null; createdAt:string }
export interface ParseTask { taskId:string; resumeVersionId?:string; jdVersionId?:string; status:string; attemptCount:number; maxAttempts:number; errorCode:string|null; retryable:boolean; resultFactSnapshotId:string|null; createdAt:string; updatedAt:string }
export interface ResumeCandidateFacts { fullName:string|null; email:string|null; phone:string|null; currentRole:string|null; city:string|null; age:number|null; birthDate?:string|null; gender:'MALE'|'FEMALE'|'OTHER'|null; jobIntention:string|null }
export interface ResumeWorkFact { sourceIndex?:number|null; startDateText?:string|null; endDateText?:string|null; id:string; factSnapshotId:string; itemKey:string; employer:string|null; roleName:string|null; startDate:string|null; endDate:string|null; descriptionText:string|null; responsibilitiesJson:string; achievementsJson:string }
export interface ResumeProjectFact { sourceIndex?:number|null; startDateText?:string|null; endDateText?:string|null; id:string; factSnapshotId:string; itemKey:string; projectName:string|null; roleName:string|null; startDate:string|null; endDate:string|null; descriptionText:string|null; technologiesJson:string; achievementsJson:string }
export interface ResumeEducationFact { startDateText?:string|null; endDateText?:string|null; id:string; factSnapshotId:string; itemKey:string; school:string|null; degreeName:string|null; major:string|null; startDate:string|null; endDate:string|null; learningMode:string|null }
export interface ResumeSkillFact { id:string; factSnapshotId:string; itemKey:string; skillName:string; levelName:string|null; yearsValue:number|null }
export interface ResumeSimpleFact { sourceIndex?:number|null; startDateText?:string|null; endDateText?:string|null; id:string; factSnapshotId:string; factType:'CREDENTIAL'|'LANGUAGE'|'INDUSTRY_TAG'|'RESPONSIBILITY_TAG'|'UNKNOWN'; itemKey:string; nameValue:string|null; levelValue:string|null; issuerValue:string|null; startDate:string|null; endDate:string|null }
export interface ResumeFactSnapshot extends ResumeCandidateFacts { ageAsOf?:string|null; id:string; resumeVersionId:string; parseSnapshotId:string|null; previousSnapshotId:string|null; snapshotVersion:number; sourceType:'AI_PARSE'|'HUMAN_CORRECTION'; currentMarker:string; correctedByUserId:string|null; correctionReason:string|null; createdAt:string }
export interface ResumeFactBundle { snapshot:ResumeFactSnapshot; workExperiences:ResumeWorkFact[]; projectExperiences:ResumeProjectFact[]; educations:ResumeEducationFact[]; skills:ResumeSkillFact[]; simpleFacts:ResumeSimpleFact[] }
export interface CandidateResumeSummary { candidateId:string; resumeVersion:ResumeVersion|null; source:ResumeSource|null; facts:ResumeFactBundle|null; factsStatus:'READY'|'FORBIDDEN'|'NOT_READY'|'NO_RESUME' }
export interface ResumeVersionFactSummary { resumeVersionId:string; facts:ResumeFactBundle|null }
type CandidateResumeSummaryWire = Omit<CandidateResumeSummary,'resumeVersion'> & { resumeVersion:DatedWire<ResumeVersion>|null };
export interface ResumeFactsCommand { candidate:ResumeCandidateFacts; workExperiences:Array<{id:string;employer:string|null;role:string|null;startDate:string|null;endDate:string|null;description:string|null;responsibilities:string[];achievements:string[]}>; projectExperiences:Array<{id:string;projectName:string|null;role:string|null;startDate:string|null;endDate:string|null;description:string|null;technologies:string[];achievements:string[]}>; educations:Array<{id:string;school:string|null;degree:string|null;major:string|null;startDate:string|null;endDate:string|null;learningMode:string|null}>; skills:Array<{id:string;name:string;level:string|null;years:number|null}>; credentials:Array<{id:string;name:string;issuer:string|null;issuedDate:string|null;expiryDate:string|null}>; languages:Array<{id:string;name:string;level:string|null}>; industryTags:string[]; responsibilityTags:string[]; unknownItems:string[] }
export interface CorrectionResult { correctionId:string; factSnapshotId:string; snapshotVersion:number; affectedCapabilities:string[] }

const q=(params:Record<string,string|number|undefined>)=>{const search=new URLSearchParams();Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')search.set(key,String(value));});const text=search.toString();return text?`?${text}`:'';};
const idempotency=()=>createIdempotencyKey();

const datedPage = <T extends {createdAt?:string;updatedAt?:string}>(page:CursorPage<DatedWire<T>>):CursorPage<T> => ({...page,items:page.items.map(normalizeDates)});
type ResumeIntakeWire = Omit<DatedWire<ResumeIntake>,'importStatus'> & {importStatus?:ResumeIntake['importStatus']|null};
const normalizeIntake=(value:ResumeIntakeWire):ResumeIntake=>{
  // 兼容前后端分批更新：旧响应没有业务状态，按后端同一规则恢复三态。
  const importStatus=value.importStatus??(
    value.status==='COMPLETED'||(value.status==='CONFIRMED'&&value.processingId==null)?'IMPORTED':
    value.status==='PARSING'||value.status==='CONFIRMED'?'PROCESSING':'PENDING');
  return {...normalizeDates(value),importStatus};
};
const normalizeCandidateResumeSummary=(value:CandidateResumeSummaryWire):CandidateResumeSummary=>({...value,resumeVersion:value.resumeVersion?normalizeDates(value.resumeVersion):null});

export const talentApi={
  countCandidates:(query='',poolOnly=false,poolStage?:PersonalPoolStage,signal?:AbortSignal)=>collectCount(cursor=>request<CountPage>(`/candidates/count${q({query,poolOnly:poolOnly?'true':undefined,poolStage,cursor})}`,{signal:signal??null}),signal),
  resumeVersionSources:(ids:string[])=>ids.length?request<ResumeVersionSource[]>(`/resume-version-sources?${new URLSearchParams(ids.map(id=>['resumeVersionIds',id])).toString()}`):Promise.resolve([] as ResumeVersionSource[]),
  resumeVersionFactSummaries:(ids:string[])=>ids.length?request<ResumeVersionFactSummary[]>(`/resume-version-fact-summaries?${new URLSearchParams(ids.map(id=>['resumeVersionIds',id])).toString()}`):Promise.resolve([] as ResumeVersionFactSummary[]),
  candidateBatch:(ids:string[])=>ids.length?request<DatedWire<Candidate>[]>(`/candidate-batch?${new URLSearchParams(ids.map(id=>['candidateIds',id])).toString()}`).then(values=>values.map(normalizeDates<Candidate>)):Promise.resolve([] as Candidate[]),
  poolStatuses:(ids:string[])=>request<CandidatePoolStatus[]>(`/candidate-pool-statuses?${new URLSearchParams(ids.map(id=>['candidateIds',id])).toString()}`),
  screeningCandidates:(ids?:string[])=>request<ScreeningCandidate[]>(`/screening-candidates${ids?.length?`?${new URLSearchParams(ids.map(id=>['candidateIds',id])).toString()}`:''}`),
  admitScreening:(items:Array<Pick<ScreeningCandidate,'candidateId'|'resumeVersionId'|'jdVersionId'|'defaultMatchRunId'>>)=>request<ScreeningAdmissionResult>('/screening-admissions',{method:'POST',body:{items},idempotencyKey:idempotency()}),
  returnScreening:(items:ScreeningReturnItem[],operationKey:string)=>request<ScreeningReturnResult>('/screening-admissions/return',{method:'POST',body:{items},idempotencyKey:operationKey}),
  reclaimShared:(items:Array<{candidateId:string;resumeVersionId:string;candidateVersion:number;scoreBindingId:string}>)=>request<SharedCandidateReclaimResult>('/shared-candidate-reclaims',{method:'POST',body:{items},idempotencyKey:idempotency()}),
  scoreSharedReclaims:(jdVersionId:string,items:Array<{candidateId:string;resumeVersionId:string}>)=>request<SharedCandidateReclaimScoreResult>('/shared-candidate-reclaim-scores',{method:'POST',body:{jdVersionId,items},idempotencyKey:idempotency()}),
  recruitmentHistory:(candidateId:string)=>request<CandidateRecruitmentHistory>(`/candidates/${candidateId}/recruitment-history`),
  candidateResumeSummaries:(ids:string[])=>request<CandidateResumeSummaryWire[]>(`/candidate-resume-summaries?${new URLSearchParams(ids.map(id=>['candidateIds',id])).toString()}`).then(values=>values.map(normalizeCandidateResumeSummary)),
  screenOut:(candidate:Candidate,reason:string,resumeVersionId?:string)=>request<CandidatePoolStatus>(`/candidates/${candidate.id}/screen-out`,{method:'POST',body:{version:String(candidate.version),reason,...(resumeVersionId?{resumeVersionId}:{})}}),
  async channels(this: void): Promise<CursorPage<SourceChannel>> {
    const items:SourceChannel[]=[];
    const seen=new Set<string>();
    let cursor:string|undefined;
    for(;;){
      const page=await request<CursorPage<SourceChannel>>(`/source-channels${q({limit:20,cursor})}`);
      items.push(...page.items);
      if(!page.hasMore)return {items,hasMore:false,nextCursor:null};
      if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('渠道分页异常，请重试');
      cursor=page.nextCursor;seen.add(cursor);
    }
  },
  createChannel:(body:{name:string;channelType:string})=>request<SourceChannel>('/source-channels',{method:'POST',body,idempotencyKey:idempotency()}),
  updateChannel:(value:SourceChannel,body:{name:string;channelType:string})=>request<SourceChannel>(`/source-channels/${value.id}`,{method:'PATCH',body:{...body,version:value.version}}),
  disableChannel:(value:SourceChannel)=>request<SourceChannel>(`/source-channels/${value.id}/disable`,{method:'POST',body:{version:value.version,reason:'HR 停用来源渠道'}}),
  deleteChannel:(value:SourceChannel)=>request<void>(`/source-channels/${value.id}`,{method:'DELETE',body:{version:String(value.version),reason:'管理员删除来源渠道'}}),
  createBatch:(sourceChannelId:string,jdVersionId:string,idempotencyKey=idempotency())=>request<IntakeBatch>('/intake-batches',{method:'POST',body:{sourceChannelId,jdVersionId},idempotencyKey}),
  batches:(cursor?:string,limit=10)=>request<CursorPage<DatedWire<IntakeBatch>>>(`/intake-batches${q({cursor,limit})}`).then(datedPage<IntakeBatch>),
  batch:(id:string)=>request<DatedWire<IntakeBatch>>(`/intake-batches/${id}`).then(normalizeDates<IntakeBatch>),
  linkBatchJd:(batchId:string,jdVersionId:string)=>request<IntakeBatch>(`/intake-batches/${batchId}/jd-version`,{method:'PUT',body:{jdVersionId,reason:'HR 核对历史收件并补关联 JD'}}),
  upload:async(batchId:string,file:File,idempotencyKey=idempotency())=>{const body=new FormData();body.set('file',file);return request<ResumeIntakeWire>(`/intake-batches/${batchId}/files`,{method:'POST',body,idempotencyKey}).then(normalizeIntake);},
  completeBatch:(batchId:string)=>request<IntakeBatch>(`/intake-batches/${batchId}/complete`,{method:'POST'}),
  countIntakes:(importStatus?:string,filters:{query?:string;name?:string;from?:string;to?:string;duplicateContent?:boolean;problemStatus?:string;batchId?:string}={},signal?:AbortSignal)=>collectCount(()=>request<CountPage>(`/intakes/count${q({...filters,status:filters.problemStatus,problemStatus:undefined,duplicateContent:filters.duplicateContent?'true':undefined,importStatus})}`,{signal:signal??null}),signal),
  intakes:(importStatus?:string,cursor?:string,filters:{query?:string;name?:string;from?:string;to?:string;duplicateContent?:boolean;problemStatus?:string;batchId?:string}={},limit=20)=>request<CursorPage<ResumeIntakeWire>>(`/intakes${q({...filters,status:filters.problemStatus,problemStatus:undefined,duplicateContent:filters.duplicateContent?'true':undefined,importStatus,cursor,limit})}`).then(page=>({...page,items:page.items.map(normalizeIntake)})),
  intake:(id:string)=>request<ResumeIntakeWire>(`/intakes/${id}`).then(normalizeIntake),
  previewIntake:(id:string)=>requestFile(`/intakes/${id}/file`),
  downloadIntake:(id:string,fileName:string)=>downloadFile(`/intakes/${id}/file`,fileName),
  importIntake:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/import`,{method:'POST',body:{version:String(value.version)}}).then(normalizeIntake),
  confirm:(value:ResumeIntake,body:{candidateId?:string;fullName:string;email?:string;phone?:string})=>request<ResumeIntakeWire>(`/intakes/${value.id}/confirm`,{method:'POST',body:{...body,version:value.version}}).then(normalizeIntake),
  ignore:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/ignore`,{method:'POST',body:{version:value.version,reason:'HR 删除收件列表记录，保留简历及来源历史'}}).then(normalizeIntake),
  retry:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/retry`,{method:'POST',body:{version:value.version,reason:'HR 重试入库'}}).then(normalizeIntake),
  reparse:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/reparse`,{method:'POST',body:{version:value.version,reason:'HR 重新解析身份信息缺失的收件'}}).then(normalizeIntake),
  retryScore:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/score/retry`,{method:'POST',body:{version:value.version,reason:'HR 重试自动评分'}}).then(normalizeIntake),
  recalculateScore:(value:ResumeIntake)=>request<ResumeIntakeWire>(`/intakes/${value.id}/score/recalculate`,{method:'POST',body:{version:value.version,reason:'HR 重新解析简历后更新岗位默认评分'}}).then(normalizeIntake),
  candidates:(query?:string,cursor?:string,limit=20,poolOnly=false,poolStage?:PersonalPoolStage)=>request<CursorPage<DatedWire<Candidate>>>(`/candidates${q({query,cursor,limit,poolOnly:poolOnly?'true':undefined,poolStage})}`).then(datedPage<Candidate>),
  candidate:(id:string)=>request<DatedWire<Candidate>>(`/candidates/${id}`).then(normalizeDates<Candidate>),
  updateCandidate:(value:Candidate,body:{fullName:string;email?:string;phone?:string})=>request<Candidate>(`/candidates/${value.id}`,{method:'PATCH',body:{...body,version:value.version}}),
  resumeVersions:(candidateId:string,cursor?:string,limit=20)=>request<CursorPage<DatedWire<ResumeVersion>>>(`/candidates/${candidateId}/resume-versions${q({cursor,limit})}`).then(datedPage<ResumeVersion>),
  resumeVersionReferences:(ids:string[])=>ids.length?request<ResumeVersionReference[]>(`/resume-version-references?${new URLSearchParams(ids.map(id=>['resumeVersionIds',id])).toString()}`):Promise.resolve([] as ResumeVersionReference[]),
  resumeSource:(candidateId:string,versionId:string)=>request<ResumeSource>(`/candidates/${candidateId}/resume-versions/${versionId}/source`),
  parseResume:(id:string)=>request<ParseTask>(`/resume-versions/${id}/parse`,{method:'POST',idempotencyKey:idempotency()}),
  resumeFacts:(id:string,snapshotId?:string)=>request<ResumeFactBundle>(`/resume-versions/${id}/facts${snapshotId ? `/${snapshotId}` : ''}`),
  correctFacts:(body:{resumeVersionId:string;baseFactSnapshotId:string;reason:string;facts:ResumeFactsCommand})=>request<CorrectionResult>('/fact-corrections',{method:'POST',body}),
  authorizeDownload:(id:string)=>request<{token:string;expiresAt:string}>(`/resume-versions/${id}/download-authorizations`,{method:'POST'}),
  previewResume:async(id:string)=>{
    const grant=await talentApi.authorizeDownload(id);
    return requestFile(`/download-authorizations/${encodeURIComponent(grant.token)}`);
  },
  downloadResume:async(id:string,fileName:string)=>{
    const grant=await talentApi.authorizeDownload(id);
    await downloadFile(`/download-authorizations/${encodeURIComponent(grant.token)}`,fileName);
  },
  conflicts:(intakeIds?:string[],status:'OPEN'|'RESOLVED'='OPEN')=>request<CursorPage<IdentityConflict>>(`/identity-conflicts?status=${status}&limit=20`+(intakeIds?.length?'&'+new URLSearchParams(intakeIds.map(id=>['intakeIds',id])).toString():'')),
  candidateConflicts:(candidateId:string,cursor?:string)=>request<CursorPage<IdentityConflict>>('/identity-conflicts?'+new URLSearchParams({status:'OPEN',limit:'20',candidateId,...(cursor?{cursor}:{})}).toString()),
  resolveConflict:(id:string,body:{resolution:string;chosenCandidateId?:string;reason:string})=>request<IdentityConflict>(`/identity-conflicts/${id}/resolve`,{method:'POST',body}),
};

/** 精确读取收件状态，恢复响应丢失及另一页面已确认的操作。 */
export async function confirmCurrentIntake(value:ResumeIntake,body:{fullName:string;email?:string;phone?:string}):Promise<ResumeIntake>{
  const latest=await talentApi.intake(value.id);
  if(latest.status==='CONFIRMED')return latest;
  if(!['READY_FOR_CONFIRMATION','IDENTITY_REVIEW_REQUIRED'].includes(latest.status))throw new Error('当前收件已被处理或状态已变化，请刷新解析校验');
  return talentApi.confirm(latest,body);
}

/** 批量操作前读取最新版本；并发页面已启动或完成时直接复用服务端事实。 */
export async function importCurrentIntake(value:ResumeIntake):Promise<CurrentIntakeImportResult>{
  const latest=await talentApi.intake(value.id);
  if(latest.importStatus==='IMPORTED'||latest.status==='COMPLETED'||latest.status==='CONFIRMED'){
    return {intake:latest,outcome:'ALREADY_IMPORTED'};
  }
  if(latest.importStatus==='PROCESSING'||latest.status==='PARSING'){
    return {intake:latest,outcome:'ALREADY_PROCESSING'};
  }
  if(latest.status!=='PENDING_IMPORT'||latest.importStatus!=='PENDING'){
    throw new Error('当前收件状态已变化，请返回列表处理行内提示');
  }
  if(latest.scanStatus!=='CLEAN')throw new Error('文件安全检查未通过，不能入库');
  if(!latest.jdVersionId)throw new Error('请先关联 JD，再启动入库');
  const started=await talentApi.importIntake(latest);
  return {intake:started,outcome:started.importStatus==='IMPORTED'?'ALREADY_IMPORTED':'STARTED'};
}

import { CopyInterviewInfo } from './CopyInterviewInfo';
import './InterviewDialogs.css';
import { RecruitmentStageActions } from '../recruitment/RecruitmentStage';
import type { ActivityOrigin } from '../recruitment/activityApi';
import { createIdempotencyKey } from '../api/idempotency';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { LongTextPreview } from '../components/LongTextPreview';
import { useInfiniteQuery,useMutation,useQueries,useQuery,useQueryClient } from '@tanstack/react-query';
import { Button,Card,Descriptions,Empty,Form,Input,Radio,Select,Space,Switch,Tag,Timeline,Typography } from 'antd';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useEffect,useRef,useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { canReadRecruitmentLedger } from '../auth/permissions';
import { useSession } from '../auth/session';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import { CandidateName } from '../display/BusinessReferences';
import { interviewApi,wecomCollaborationApi,type FeedbackView,type Interview,type InterviewDetails,type InterviewExternalCalendar } from '../interview/api';
import { HistoryPagination } from '../interview/HistoryPagination';
import { InterviewFeedbackModal } from '../interview/InterviewFeedbackModal';
import { recruitmentApi,type ApplicationDetails,type WorkflowStep } from '../recruitment/api';
import { ApplicationInterviewFeedbacks } from '../recruitment/ApplicationInterviewFeedbacks';
import { ContinueRecruitmentDialog } from '../recruitment/ContinueRecruitmentDialog';
import { canProcessRecruitment } from '../recruitment/processingPermissions';
import { interviewStepLabel } from '../recruitment/workflowLabels';

import { ScheduleDateTimePicker } from '../components/ScheduleDateTimePicker';
import { addThirtyMinutes,defaultScheduleStart,tenMinuteRule } from '../components/scheduleTime';
import { demandApi } from '../demand/api';
import { PeopleSelect,TechnicalDetails,UserName } from '../display/BusinessReferences';
import { displayEnum,formatDateTime,safeMeetingUrl } from '../display/labels';
import { InterviewMaterials,InterviewReference } from '../interview/InterviewMaterials';
import { confirmationLabel, interviewTypeLabels, interviewStatusLabels } from '../interview/presentation';
import { talentApi } from '../talent/api';
import { submissionApi } from '../submission/api';
import { currentSubmission } from '../submission/currentSubmission';

dayjs.extend(utc);
dayjs.extend(timezone);
const businessTime=(value?:string)=>dayjs(value).tz('Asia/Shanghai');

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';
const accessDenied=(error:unknown)=>error instanceof ApiProblemError&&[401,403].includes(error.problem.status);
const interviewSteps=(value:ApplicationDetails|undefined)=>value?.workflowSteps.filter(step=>(['PRE_SUBMISSION_INTERNAL_INTERVIEW','CLIENT_AI_INTERVIEW'].includes(step.stepCode)||step.stepCode.startsWith('INTERVIEW_ROUND_'))&&['PENDING','IN_PROGRESS'].includes(step.status))??[];
const toIso=(value:string,zone='Asia/Shanghai')=>dayjs.tz(value,zone).toISOString();

export function InterviewDrawer({value,loading,error,open,onClose,onChanged}:{value:InterviewDetails|undefined;loading:boolean;error:unknown;open:boolean;onClose:()=>void;onChanged:()=>Promise<unknown>}){
  return <Modal size="form" width="min(1000px, calc(100vw - 32px))" className="interview-facts-dialog" title="面试事实与处理" open={open} onCancel={onClose} footer={null}><InterviewContent value={value} loading={loading} error={error} onChanged={onChanged}/></Modal>;
}

type InterviewContentProps={value:InterviewDetails|undefined;loading:boolean;error:unknown;onChanged:()=>Promise<unknown>;origin?:ActivityOrigin|undefined;onRoundCompleted?:()=>Promise<unknown>};
export function InterviewContent({value,loading,error,onChanged,origin,onRoundCompleted}:InterviewContentProps){
  if(accessDenied(error))return <Alert occurrence={error} type="error" showIcon title={errorText(error)} action={<Button type="primary" onClick={()=>void onChanged()}>重试面试详情</Button>}/>;
  return <AccessibleInterviewContent value={value} loading={loading} error={error} onChanged={onChanged} origin={origin} {...(onRoundCompleted?{onRoundCompleted}: {})}/>;
}

function AccessibleInterviewContent({value,loading,error,onChanged,origin,onRoundCompleted}:InterviewContentProps){
  const [action,setAction]=useState<'cancel'|'feedback'>();
  const [operationId,setOperationId]=useState(createIdempotencyKey);
  const actionOrigin=origin?{...origin,operationId}:undefined;
  const openAction=(next:'cancel'|'feedback')=>{setOperationId(createIdempotencyKey());setAction(next);};
  const [continueOpen,setContinueOpen]=useState(false);
  const [roundResultError,setRoundResultError]=useState<unknown>();
  const [roundResultSaving,setRoundResultSaving]=useState(false);
  const [pendingRoundFeedback,setPendingRoundFeedback]=useState<FeedbackView>();
  const {actor}=useSession();
  const current=value?.interview;
  const meetingLink=safeMeetingUrl(value?.externalCalendar?.meetingLink);
  const qc=useQueryClient();
  const application=useQuery({queryKey:['application',current?.applicationId],queryFn:()=>recruitmentApi.application(current!.applicationId),enabled:Boolean(current)&&!error&&canReadRecruitmentLedger(actor),retry:false});
  const recruitmentAccessDenied=application.error instanceof ApiProblemError&&application.error.problem.status===403;
  const canContinue=Boolean(!application.error&&application.data&&!application.data.application.terminalAt&&canReadRecruitmentLedger(actor)&&canProcessRecruitment(actor,{ownerUserId:application.data.application.ownerUserId,collaboratorUserIds:application.data.collaboratorUserIds}));
  const feedback=useInfiniteQuery({queryKey:['interview-history',current?.id,'feedback'],queryFn:({pageParam})=>interviewApi.feedbackPage(current!.id,pageParam),enabled:Boolean(current)&&!error,initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore&&page.nextCursor?page.nextCursor:undefined,retry:false});
  const history=useInfiniteQuery({queryKey:['interview-history',current?.id,'schedule-history'],queryFn:({pageParam})=>interviewApi.historyPage(current!.id,pageParam),enabled:Boolean(current)&&!error&&!origin,initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore&&page.nextCursor?page.nextCursor:undefined,retry:false});
  const feedbackItems=accessDenied(feedback.error)?[]:feedback.data?.pages.flatMap(page=>page.items)??[];
  const submitted=feedbackItems.some(item=>item.feedback.status==='SUBMITTED');
  const recoverableFeedback=feedbackItems.find(item=>item.feedback.status==='SUBMITTED');
  const historyItems=accessDenied(history.error)?[]:history.data?.pages.flatMap(page=>page.items)??[];
  const refresh=async()=>{await Promise.all([onChanged(),qc.invalidateQueries({queryKey:['interview-history',current?.id]}),...(current?.applicationId?[qc.invalidateQueries({queryKey:['application',current.applicationId]}),qc.invalidateQueries({queryKey:['applications']}),qc.invalidateQueries({queryKey:['application-interviews',current.applicationId]}),qc.invalidateQueries({queryKey:['application-interview-feedback',current.applicationId]})]:[])]);};
  const externalRetry=useMutation({mutationFn:()=>interviewApi.retryExternalCalendar(current!.id),onSuccess:refresh});
  const canExternal=Boolean(actor?.superAdmin||actor?.permissions.includes('interview.external-feedback.record'));
  const defineRoundResult=async(saved:FeedbackView)=>{
    setAction(undefined);setRoundResultError(undefined);setPendingRoundFeedback(saved);await refresh();
    if(!canContinue||saved.feedback.status!=='SUBMITTED')return;
    setRoundResultSaving(true);
    try{
      const latestInterview=await interviewApi.get(current!.id);
      if(latestInterview.interview.status!=='COMPLETED'){setPendingRoundFeedback(undefined);return;}
      const latestApplication=await recruitmentApi.application(current!.applicationId);
      const step=latestApplication.workflowSteps.find(item=>item.id===current!.workflowStepId);
      if(step&&['PENDING','IN_PROGRESS'].includes(step.status)){
        await recruitmentApi.completeStep(current!.applicationId,step,saved.currentVersion.verdict==='PASS'?'PASSED':'FAILED',`正式面评已提交，本轮结果：${saved.currentVersion.verdict==='PASS'?'通过':'不通过'}`,current!.clientSubmissionId??step.clientSubmissionId??undefined,...((actionOrigin?[undefined,actionOrigin]:[]) as [undefined?,ActivityOrigin?]));
      }
      setPendingRoundFeedback(undefined);await refresh();if(onRoundCompleted)await onRoundCompleted();else setContinueOpen(true);
    }catch(nextError){setRoundResultError(nextError);}finally{setRoundResultSaving(false);}
  };
  return <Space orientation="vertical" size={16} style={{width:'100%'}}>
    {loading?<Card loading/>:null}{error?<Alert occurrence={error} type="error" showIcon title={errorText(error)} action={<Button onClick={()=>void onChanged()}>重试面试详情</Button>}/>:null}
    {current&&value?<><Card className="interview-summary-card">{!origin?<div className="interview-person-heading"><Typography.Title level={3}><InterviewReference id={current.id} field="candidateName"/></Typography.Title><Typography.Text type="secondary"><InterviewReference id={current.id} field="jdTitle"/></Typography.Text></div>:null}{!origin?<div className="interview-summary-columns">
      <Descriptions className="interview-summary-column" column={1} size="small" items={[
        {key:'status',label:'状态',children:<Tag color={current.status==='COMPLETED'?'green':current.status==='CANCELLED'?'default':'blue'}>{interviewStatusLabels[current.status]}</Tag>},
        ...(!['INTERNAL','EXTERNAL'].includes(current.interviewType)?[{key:'type',label:'面试类型',children:interviewTypeLabels[current.interviewType]}]:[]),
        ...(current.clientName?[{key:'client',label:'甲方公司',children:current.clientName}]:[]),
        {key:'time',label:'时间',children:`${formatDateTime(current.scheduledStartAt)} — ${formatDateTime(current.scheduledEndAt)}（北京时间）`},
        {key:'mode',label:'方式 / 地点',children:current.meetingMode==='ONLINE'?displayEnum(current.meetingMode):<span>{displayEnum(current.meetingMode)} · <MeetingPlace value={current}/></span>},
        {key:'owner',label:'跟进负责人',children:<UserName id={current.nextResponsibleUserId}/>},
      ]}/>
      <Descriptions className="interview-summary-column" column={1} size="small" items={[
        {key:'step',label:'面试轮次',children:interviewStepLabel(current)},
        {key:'duration',label:'预计时长',children:`${businessTime(current.scheduledEndAt).diff(businessTime(current.scheduledStartAt),'minute')} 分钟`},
        {key:'confirmation',label:'候选人确认',children:confirmationLabel(current.candidateConfirmationStatus)},
        {key:'participants',label:'实际面试官（记录）',children:value.participants.length?value.participants.map(v=><Tag key={v.userId}><UserName id={v.userId}/></Tag>):current.interviewType==='EXTERNAL'?'未记录':'由甲方安排'},
      ]}/>
    </div>:<Descriptions column={{xs:1,sm:2}} size="small" items={[
      {key:'status',label:'状态',children:<Tag color={current.status==='COMPLETED'?'green':current.status==='CANCELLED'?'default':'blue'}>{interviewStatusLabels[current.status]}</Tag>},
      {key:'step',label:'面试轮次',children:interviewStepLabel(current)},
      {key:'time',label:'时间',span:'filled',children:`${formatDateTime(current.scheduledStartAt)} — ${formatDateTime(current.scheduledEndAt)}（北京时间）`},
      {key:'mode',label:'方式 / 地点',children:current.meetingMode==='ONLINE'?displayEnum(current.meetingMode):<span>{displayEnum(current.meetingMode)} · <MeetingPlace value={current}/></span>},
      {key:'confirmation',label:'候选人确认',children:confirmationLabel(current.candidateConfirmationStatus)},
      {key:'participants',label:'实际面试官（记录）',children:value.participants.length?value.participants.map(v=><Tag key={v.userId}><UserName id={v.userId}/></Tag>):current.interviewType==='EXTERNAL'?'未记录':'由甲方安排'},
      ...(value.externalCalendar?[{key:'externalCalendar',label:'企业微信联动',span:'filled' as const,children:<Space wrap><Tag color={value.externalCalendar.status==='SYNCED'?'green':value.externalCalendar.status==='FAILED'?'red':value.externalCalendar.status==='CANCELLED'?'default':'blue'}>{displayEnum(value.externalCalendar.status)}</Tag><span>{value.externalCalendar.mode==='WECOM_MEETING'?'企业微信会议':value.externalCalendar.mode==='WECOM_SCHEDULE'?'企业微信日程':'直接填写面评'}</span>{value.externalCalendar.meetingCode?<span>会议号 {value.externalCalendar.meetingCode}</span>:null}{meetingLink?<a href={meetingLink} target="_blank" rel="noreferrer">打开会议</a>:null}<CopyInterviewInfo value={value} disabled={loading||Boolean(error)}/></Space>}]:[]),
    ]}/>} {!origin&&value.externalCalendar?<div className="interview-conference-row"><div className="interview-conference-content"><span className="interview-conference-label">会议：</span><Space wrap><Tag color={value.externalCalendar.status==='SYNCED'?'green':value.externalCalendar.status==='FAILED'?'red':value.externalCalendar.status==='CANCELLED'?'default':'blue'}>{displayEnum(value.externalCalendar.status)}</Tag><span>{value.externalCalendar.mode==='WECOM_MEETING'?'企业微信会议':value.externalCalendar.mode==='WECOM_SCHEDULE'?'企业微信日程':'直接填写面评'}</span>{value.externalCalendar.meetingCode?<span>会议号 {value.externalCalendar.meetingCode}</span>:null}{meetingLink?<a href={meetingLink} target="_blank" rel="noreferrer">打开会议</a>:null}<CopyInterviewInfo value={value} disabled={loading||Boolean(error)}/></Space></div></div>:null}<div className="interview-detail-actions"><div className="interview-current-actions">{!origin?<span className="interview-action-label">本次面试</span>:null}<Space wrap><InterviewMaterials id={current.id}/>{!value.externalCalendar?<CopyInterviewInfo value={value} disabled={loading||Boolean(error)}/>:null}{current.status==='SCHEDULED'&&canContinue&&(actor?.superAdmin||actor?.permissions.includes('interview.schedule'))?<Button danger onClick={()=>openAction('cancel')}>取消</Button>:null}{(['INTERNAL','EXTERNAL'].includes(current.interviewType)?canContinue:canExternal&&(!origin||canContinue))&&current.status==='SCHEDULED'&&!submitted?<Button type="primary" onClick={()=>openAction('feedback')}>{['INTERNAL','EXTERNAL'].includes(current.interviewType)?'填写面评':'代录甲方反馈'}</Button>:null}{submitted?<Tag color="green">面评已正式提交</Tag>:null}</Space></div>{!origin&&!error&&canContinue&&application.data?<div className="schedule-recruitment-actions"><span className="interview-action-label">当前阶段</span><RecruitmentStageActions value={application.data.application}/></div>:null}</div></Card>
      {value.externalCalendar?.status==='FAILED'?<Alert type="error" showIcon title="企业微信日程同步失败，本项目排期已保存" description={value.externalCalendar.lastErrorDetail??'可重试同步；不会重复创建已确认的远端对象。'} action={canContinue&&(actor?.superAdmin||actor?.permissions.includes('interview.schedule'))?<Button loading={externalRetry.isPending} onClick={()=>externalRetry.mutate()}>重试企业微信同步</Button>:undefined}/>:null}
      {!origin&&!error&&recruitmentAccessDenied?<Typography.Text type="secondary"><strong>仅可查看本次面试</strong>；取消安排和招聘跟进由该流程负责人或协作人处理。</Typography.Text>:null}
      {!origin&&!error&&application.error&&!recruitmentAccessDenied?<Alert occurrence={application.error} type="error" showIcon title="招聘操作读取失败" action={<Button onClick={()=>void application.refetch()}>重试招聘操作</Button>}/>:null}
      {roundResultError&&pendingRoundFeedback?<Alert type="error" showIcon title="面评已正式提交，本轮结果暂未同步" description={errorText(roundResultError)} action={<Button loading={roundResultSaving} onClick={()=>void defineRoundResult(pendingRoundFeedback)}>重试同步本轮结果</Button>}/>:null}
      {canContinue&&recoverableFeedback&&current.status==='COMPLETED'?<Button type="primary" loading={roundResultSaving} onClick={()=>void defineRoundResult(recoverableFeedback)}>按正式面评继续招聘跟进</Button>:null}
      {current.status==='COMPLETED'?<Alert type="info" showIcon title={`${current.interviewType==='INTERNAL'?'内部面试':current.interviewType==='EXTERNAL'?'外部面试':'甲方面试'}已完成 · 记录只读`} description="可查看已正式提交的面评和排期历史，不能再新增、修改或补记该面试记录。"/>:current.interviewType.startsWith('EXTERNAL_CLIENT')?<Alert type="info" showIcon title="外部流程 · 人工记录" description="请根据甲方实际反馈记录结果，并注明联系人或反馈渠道。"/>:null}
      {origin?<>
        {feedback.error?<Alert occurrence={feedback.error} type="error" title="本轮面评状态读取失败" action={<Button onClick={()=>void feedback.refetch()}>重试面评状态</Button>}/>:null}
        <Typography.Paragraph type="secondary">各轮面试进度与结果请在「详情与版本 → 流程记录」查看。</Typography.Paragraph>
      </>:<><ApplicationInterviewFeedbacks applicationId={current.applicationId} recruitmentType={current.interviewType==='INTERNAL'?'INTERNAL':'EXTERNAL'}/>
      <Card title="排期变更历史" loading={history.isPending&&!history.error}>{historyItems.length?<Timeline items={historyItems.map(item=>({content:<span><b>{displayEnum(item.actionType,'排期更新')}</b> · <LongTextPreview text={item.reason} label="排期变更原因"/><br/><small><UserName id={item.operatedByUserId}/> · {formatDateTime(item.occurredAt)}</small></span>}))}/>:history.data&&!history.error?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE}/>:null}<HistoryPagination label="排期历史" count={historyItems.length} loaded={Boolean(history.data)&&!accessDenied(history.error)} hasMore={Boolean(history.hasNextPage)} loading={history.isFetching} error={history.error} onNext={()=>void history.fetchNextPage()} onRetry={()=>void (history.isFetchNextPageError?history.fetchNextPage():history.refetch())}/></Card>
      <TechnicalDetails items={[["面试记录",current.id],["应聘记录",current.applicationId],["候选人",current.candidateId],["流程记录",current.workflowStepId],["岗位版本",current.jdVersionId]]}/>
      </>}
      {action==='feedback'&&current.status==='SCHEDULED'&&(current.interviewType.startsWith('EXTERNAL_CLIENT')||canContinue)?<InterviewFeedbackModal origin={actionOrigin} value={current} onClose={()=>setAction(undefined)} onSaved={defineRoundResult}/>:<InterviewActionModal origin={actionOrigin} action={action==='feedback'||!canContinue?undefined:action} value={current} {...(value.externalCalendar!==undefined?{externalCalendar:value.externalCalendar}:{})} onClose={()=>setAction(undefined)} onSaved={async()=>{setAction(undefined);await refresh();}}/>}
      {continueOpen?<ContinueRecruitmentDialog applicationId={current.applicationId} onClose={()=>setContinueOpen(false)}/>:null}
    </>:null}
  </Space>;
}

type ActionValues={reason:string};
export function InterviewActionModal({action,value,externalCalendar,onClose,onSaved,origin}:{origin?:ActivityOrigin|undefined;action:'cancel'|undefined;value:Interview;externalCalendar?:InterviewExternalCalendar|null;onClose:()=>void;onSaved:()=>Promise<unknown>}){
  const [form]=Form.useForm<ActionValues>();
  const mutation=useMutation({mutationFn:async(data:ActionValues):Promise<unknown>=>interviewApi.cancel(value,data.reason,...(origin?[origin]:[])),onSuccess:onSaved});
  return <Modal resizeSurface size="form" className="interview-operation-dialog" {...(origin?{cancelText:'返回'}:{})} okText="确认取消" title={origin?'面试':'取消面试'} open={Boolean(action)} onCancel={onClose} confirmLoading={mutation.isPending} onOk={()=>form.validateFields().then(data=>mutation.mutate(data))} afterOpenChange={open=>{if(open){mutation.reset();form.setFieldsValue({reason:''});}else form.resetFields();}} destroyOnHidden>
    {mutation.error?<Alert occurrence={mutation.error} className="modal-alert" type="error" showIcon title={errorText(mutation.error)}/>:null}
    {action==='cancel'&&externalCalendar?.activeType?<Alert className="modal-alert" type="warning" showIcon title="确认取消后，本项目会异步取消已创建的企业微信日程或会议" description="本项目面试立即标记为已取消；若企业微信暂时不可用，可在详情中查看状态并重试。"/>:null}
    {origin?<p><InterviewReference id={value.id} field="candidateName"/> · <InterviewReference id={value.id} field="jdTitle"/> · {value.clientName??'内部招聘'} · 取消面试</p>:null}
    <Form form={form} layout="vertical" className="interview-operation-grid"><Form.Item className="interview-operation-full" name="reason" label={origin?"评论（选填）":"操作原因（选填）"} rules={[{max:500}]}><Input.TextArea rows={3}/></Form.Item></Form>
  </Modal>;
}

type CreateValues={interviewType:'INTERNAL'|'EXTERNAL';applicationId:string;workflowStepId:string;startAt:string;endAt:string;timezoneId:string;meetingMode:string;locationText?:string;participantUserIds?:string[];reason:string;externalCalendarMode:'LOCAL_ONLY'|'WECOM_SCHEDULE'|'WECOM_MEETING';externalCalendarTitle?:string;externalCalendarDescription?:string;externalCalendarInviteeUserIds?:string[];externalCalendarReminderMinutes?:number;waitingRoomEnabled?:boolean;allowEnterBeforeHost?:boolean;enterMuteEnabled?:boolean};
export function CreateInterviewModal({open,application,workflowStep,onClose,onSaved,origin,nextStepOptions,prepareWorkflowStep,context}:{open:boolean;application?:ApplicationDetails;workflowStep?:WorkflowStep;onClose:()=>void;onSaved:(value:InterviewDetails)=>Promise<unknown>;origin?:ActivityOrigin|undefined;nextStepOptions?:{value:string;label:string;disabled?:boolean}[];prepareWorkflowStep?:(code:string,reason:string)=>Promise<WorkflowStep>;context?:React.ReactNode}){
  const {actor}=useSession();
  const [form]=Form.useForm<CreateValues>(); const watchedApplication=Form.useWatch('applicationId',form); const applicationId=application?.application.id??watchedApplication; const mode=Form.useWatch('meetingMode',form); const interviewType=Form.useWatch('interviewType',form);const stepId=Form.useWatch('workflowStepId',form);const externalCalendarMode=Form.useWatch('externalCalendarMode',form);
  const requestDraft=useRef<{signature:string;key:string}|undefined>(undefined); const apps=useQuery({queryKey:['applications','interview-create'],queryFn:()=>recruitmentApi.applications(),enabled:open&&!application});
  const detail=useQuery({queryKey:['application','interview-create',applicationId],queryFn:()=>recruitmentApi.application(applicationId),enabled:Boolean(open&&applicationId&&!application)});
  const applicationDetails=application??detail.data;
  const titleCandidateId=applicationDetails?.application.candidateId;
  const titleJdVersionId=applicationDetails?.application.jdVersionId;
  const needsCalendarTitle=open&&['WECOM_SCHEDULE','WECOM_MEETING'].includes(externalCalendarMode);
  const titleCandidate=useQuery({queryKey:['talent','candidate',titleCandidateId],queryFn:()=>talentApi.candidate(titleCandidateId!),enabled:needsCalendarTitle&&Boolean(titleCandidateId),retry:false});
  const titleJob=useQuery({queryKey:['demand','jd-version',titleJdVersionId],queryFn:()=>demandApi.jdVersion(titleJdVersionId!),enabled:needsCalendarTitle&&Boolean(titleJdVersionId),retry:false});
  const generatedTitle=useRef('');
  useEffect(()=>{
    if(!needsCalendarTitle)return;
    const name=titleCandidate.data?.fullName?.trim();
    const job=titleJob.data?.title?.trim();
    if(!name||!job)return;
    const current:unknown=form.getFieldValue('externalCalendarTitle');
    if(current&&current!==generatedTitle.current)return;
    const limit=externalCalendarMode==='WECOM_MEETING'?20:120;
    const suffix=` · ${name}`;
    const room=Math.max(1,limit-suffix.length);
    const title=(job.length>room?`${job.slice(0,Math.max(0,room-1))}…`:job)+suffix;
    generatedTitle.current=title.slice(0,limit);
    form.setFieldValue('externalCalendarTitle',generatedTitle.current);
  },[needsCalendarTitle,externalCalendarMode,titleCandidate.data,titleJob.data,form]);
  const [preparedStep,setPreparedStep]=useState<WorkflowStep>();
  const selectedStep=workflowStep??preparedStep??interviewSteps(applicationDetails).find(step=>step.id===stepId);
  const legacyClient=selectedStep?.stepCode==='CLIENT_AI_INTERVIEW';
  const legacyInternal=selectedStep?.stepCode==='PRE_SUBMISSION_INTERNAL_INTERVIEW';
  const external=legacyClient||(!legacyInternal&&interviewType==='EXTERNAL');
  const [wecomMemberQuery,setWecomMemberQuery]=useState('');
  const wecomMembers=useQuery({queryKey:['wecom-collaboration-members',wecomMemberQuery],queryFn:()=>wecomCollaborationApi.members(wecomMemberQuery),enabled:open&&['WECOM_SCHEDULE','WECOM_MEETING'].includes(externalCalendarMode),staleTime:60_000,retry:false});
  const wecomMemberOptions=(wecomMembers.data??[]).map(member=>({value:member.userId,label:`${member.name}${member.position?` · ${member.position}`:''}${member.linkedUserAccountId?' · 已关联本地账号':''}`}));
  const submissions=useQuery({queryKey:['client-submissions',applicationId],queryFn:()=>submissionApi.list(applicationId),enabled:Boolean(open&&applicationId&&legacyClient)});
  const recommended=applicationDetails?currentSubmission(applicationDetails.application,submissions.data?.items??[],selectedStep):undefined;
  const mutation=useMutation({mutationFn:async(data:CreateValues)=>{if(legacyClient&&!recommended)throw new Error('请先完成客户推荐');const targetStep=workflowStep??preparedStep??(prepareWorkflowStep?await prepareWorkflowStep(data.workflowStepId,data.reason??''):undefined);if(targetStep)setPreparedStep(targetStep);const body={applicationId,workflowStepId:targetStep?.id??data.workflowStepId,startAt:toIso(data.startAt,data.timezoneId),endAt:toIso(data.endAt,data.timezoneId),timezoneId:data.timezoneId,meetingMode:data.meetingMode,reason:data.reason??'',participantUserIds:legacyClient||(!legacyInternal&&data.interviewType==='EXTERNAL')?[]:data.participantUserIds??[],...(legacyClient?{clientSubmissionId:recommended!.id}:{interviewType:legacyInternal?'INTERNAL' as const:data.interviewType}),externalCalendar:{mode:data.externalCalendarMode,...(data.externalCalendarMode!=='LOCAL_ONLY'?{title:data.externalCalendarTitle,description:data.externalCalendarDescription,additionalInviteeUserIds:data.externalCalendarInviteeUserIds??[],reminderMinutes:data.externalCalendarReminderMinutes??5,waitingRoomEnabled:data.waitingRoomEnabled??false,allowEnterBeforeHost:data.allowEnterBeforeHost??true,enterMuteEnabled:data.enterMuteEnabled??true}:{})},...(data.meetingMode==='ONSITE'&&data.locationText?{locationText:data.locationText}:{})};const signature=JSON.stringify(body);if(requestDraft.current?.signature!==signature)requestDraft.current={signature,key:createIdempotencyKey()};return interviewApi.create(body,requestDraft.current.key,...(origin?[origin]:[]));},onSuccess:saved=>onSaved(saved)});
  const activeApps=(apps.data?.items??[]).filter(item=>!item.terminalAt);
  const candidateIds=[...new Set(activeApps.map(item=>item.candidateId))];
  const optionPeople=useQuery({queryKey:['talent','candidates','interview-create',candidateIds],queryFn:()=>talentApi.candidateBatch(candidateIds),enabled:open&&!application&&candidateIds.length>0,staleTime:60_000,retry:false});
  const peopleById=new Map((optionPeople.error?[]:optionPeople.data??[]).map(person=>[person.id,person]));
  const optionJobs=useQuery({queryKey:['demand','jds',''],queryFn:()=>demandApi.jds(),enabled:open&&!application,staleTime:60_000,retry:false});
  const missingJdIds=[...new Set(activeApps.filter(item=>!optionJobs.data?.items.some(job=>job.id===item.jdId)).map(item=>item.jdId))];
  const optionVersions=useQueries({queries:missingJdIds.map(id=>({queryKey:['demand','jd-versions',id],queryFn:()=>demandApi.jdVersions(id),enabled:open&&!application,staleTime:60_000,retry:false}))});
  const options=activeApps.map(item=>({value:item.id,label:`${peopleById.get(item.candidateId)?.fullName??'候选人信息暂不可用'} · ${optionJobs.data?.items.find(job=>job.id===item.jdId)?.title??optionVersions[missingJdIds.indexOf(item.jdId)]?.data?.items[0]?.title??'岗位名称暂不可用'}`}));
  return <Modal resizeSurface width="min(94vw,820px)" className="interview-operation-dialog" okText={origin?"保存":"创建排期"} title={origin?"面试":"新建面试"} open={open} onCancel={onClose} confirmLoading={mutation.isPending} okButtonProps={{disabled:Boolean((!application&&(apps.error||detail.error))||(legacyClient&&(submissions.isPending||submissions.error||!recommended)))}} onOk={()=>form.validateFields().then(data=>mutation.mutate(data))} afterOpenChange={visible=>{if(visible){const startAt=defaultScheduleStart();form.setFieldsValue({...(application?{applicationId:application.application.id}:{}),...(workflowStep?{workflowStepId:workflowStep.id}:nextStepOptions?.length?{workflowStepId:nextStepOptions[0]!.value}:{}),startAt,endAt:addThirtyMinutes(startAt),timezoneId:'Asia/Shanghai',interviewType:'INTERNAL',meetingMode:'ONLINE',participantUserIds:actor?[actor.userId]:[],externalCalendarMode:'LOCAL_ONLY',externalCalendarTitle:'',externalCalendarInviteeUserIds:[],externalCalendarReminderMinutes:5,waitingRoomEnabled:false,allowEnterBeforeHost:true,enterMuteEnabled:true,reason:''});}else {form.resetFields();requestDraft.current=undefined;setPreparedStep(undefined);setWecomMemberQuery('');mutation.reset();}}} destroyOnHidden>
    {legacyClient&&submissions.error?<Alert occurrence={submissions.error} type="error" title="客户推荐读取失败" action={<Button onClick={()=>void submissions.refetch()}>重试客户推荐</Button>}/>:null}{!application&&(apps.error||detail.error)?<Alert occurrence={apps.error??detail.error} type="error" showIcon title="排期所需资料读取失败" action={<Button onClick={()=>void Promise.all([apps.refetch(),...(applicationId?[detail.refetch()]:[])])}>重试排期资料</Button>}/>:null}{wecomMembers.error?<Alert occurrence={wecomMembers.error} className="modal-alert" type="error" showIcon title="企业微信成员目录读取失败" action={<Button onClick={()=>void wecomMembers.refetch()}>重试成员目录</Button>}/>:null}{mutation.error?<Alert occurrence={mutation.error} className="modal-alert" type="error" showIcon title={preparedStep?`面试步骤已保存，排期尚未确认：${errorText(mutation.error)}；重试将复用该步骤。`:errorText(mutation.error)}/>:null}<Alert className="modal-alert" type="info" showIcon title="本项目排期始终为主记录；企业微信日程或会议在保存后异步同步。已有排期请先取消后重新安排。"/>
    <Form form={form} layout="vertical" className="interview-operation-grid">
      <div className="interview-operation-full">{context??(application?<p><CandidateName id={application.application.candidateId}/> · {workflowStep?interviewStepLabel(workflowStep):'安排面试'} · {external?'外部面试':'内部面试'}</p>:<Form.Item name="applicationId" label="候选人 / 应聘岗位" rules={[{required:true}]}><Select showSearch optionFilterProp="label" loading={apps.isPending} options={options} onChange={()=>{form.setFieldValue('workflowStepId',undefined);form.resetFields(['externalCalendarTitle']);}}/></Form.Item>)}</div>
      {!workflowStep?<Form.Item name="workflowStepId" label="面试轮次" rules={[{required:true}]}><Select disabled={Boolean(preparedStep)} loading={!application&&detail.isPending} options={nextStepOptions??interviewSteps(applicationDetails).map((step:WorkflowStep)=>({value:step.id,label:`${interviewStepLabel(step)} · ${displayEnum(step.status)}`}))}/></Form.Item>:null}
      {applicationId&&!workflowStep&&!nextStepOptions&&interviewSteps(applicationDetails).length===0&&!detail.isPending?<Alert className="modal-alert" type="warning" showIcon title="该应聘记录当前没有可排期的面试步骤"/>:null}
      {legacyClient?<Alert className="modal-alert" type="info" title={recommended?"甲方 AI 面试沿用已保存的客户推荐":"请先完成客户推荐后安排甲方 AI 面试"}/>:legacyInternal?<Tag>内部预面</Tag>:<Form.Item name="interviewType" label="面试类型" rules={[{required:true}]}><Radio.Group optionType="button" buttonStyle="solid" options={[{value:"INTERNAL",label:"内部面试"},{value:"EXTERNAL",label:"外部面试"}]}/></Form.Item>}
      <Form.Item name="startAt" label="开始时间" rules={[{required:true},tenMinuteRule,{validator:(_,value:unknown)=>typeof value!=='string'||!value||dayjs(toIso(value)).isAfter(dayjs())?Promise.resolve():Promise.reject(new Error('请选择未来的面试时间'))}]}><ScheduleDateTimePicker onChange={startAt=>{form.setFieldValue('startAt',startAt);form.setFieldValue('endAt',addThirtyMinutes(startAt));}}/></Form.Item>
      <Form.Item name="endAt" label="结束时间" dependencies={['startAt']} rules={[{required:true},tenMinuteRule,{validator:(_,value)=>!value||value>form.getFieldValue('startAt')?Promise.resolve():Promise.reject(new Error('结束时间必须晚于开始时间'))}]}><ScheduleDateTimePicker onChange={endAt=>form.setFieldValue('endAt',endAt)}/></Form.Item>
      <Form.Item name="timezoneId" label="时区" rules={[{required:true}]}><Select options={[{value:'Asia/Shanghai',label:'北京时间（中国）'}]}/></Form.Item>
      <Form.Item name="meetingMode" label="面试方式" rules={[{required:true}]}><Select options={['ONLINE','ONSITE','PHONE','CLIENT_MANAGED'].map(value=>({value,label:displayEnum(value)}))}/></Form.Item>
      {mode==='ONSITE'?<Form.Item name="locationText" label="面试地点" rules={[{required:true},{max:500}]}><Input/></Form.Item>:null}
      {!external?<Form.Item name="participantUserIds" label="实际面试官" rules={[{required:true}]}><PeopleSelect mode="multiple" aria-label="实际面试官"/></Form.Item>:null}
      <Form.Item className="interview-operation-full" name="externalCalendarMode" label="企业微信联动" rules={[{required:true}]}><Radio.Group optionType="button" buttonStyle="solid" options={[{value:'LOCAL_ONLY',label:'直接填写面评'},{value:'WECOM_SCHEDULE',label:'企业微信日程'},{value:'WECOM_MEETING',label:'企业微信会议'}]}/></Form.Item>
      {externalCalendarMode!=='LOCAL_ONLY'?<>
        <Form.Item name="externalCalendarTitle" label={externalCalendarMode==='WECOM_MEETING'?'会议标题':'日程标题'} rules={[{required:true},{max:externalCalendarMode==='WECOM_MEETING'?20:120}]}><Input/></Form.Item>
        <Form.Item className="interview-operation-full" name="externalCalendarInviteeUserIds" label="额外企业微信参与人" tooltip="实际面试官会按账号关联自动加入，无需重复选择"><Select mode="multiple" showSearch filterOption={false} onSearch={setWecomMemberQuery} loading={wecomMembers.isPending} options={wecomMemberOptions}/></Form.Item>
        <Form.Item name="externalCalendarReminderMinutes" label="提前提醒" rules={[{required:true}]}><Select options={[{value:0,label:'开始时'},{value:5,label:'5 分钟前'},{value:15,label:'15 分钟前'},{value:60,label:'1 小时前'},{value:1440,label:'1 天前'}]}/></Form.Item>
        <Form.Item className="interview-operation-full" name="externalCalendarDescription" label="参会说明（可选）" extra="同步到企业微信日程或会议，用于准备事项、地点说明等参会信息。" rules={[{max:500}]}><Input.TextArea rows={2}/></Form.Item>
        {externalCalendarMode==='WECOM_MEETING'?<div className="interview-meeting-switches"><Form.Item name="waitingRoomEnabled" label="开启等候室" valuePropName="checked"><Switch/></Form.Item><Form.Item name="allowEnterBeforeHost" label="允许提前入会" valuePropName="checked"><Switch/></Form.Item><Form.Item name="enterMuteEnabled" label="入会自动静音" valuePropName="checked"><Switch/></Form.Item></div>:null}
      </>:null}

    </Form>
  </Modal>;
}

export function MeetingPlace({value}:{value:Pick<Interview,'meetingMode'|'meetingUrl'|'locationText'>}) {
  return value.meetingMode==='ONLINE'?<span>线上面试</span>:<span>{value.locationText??(value.meetingMode==='PHONE'?'按约定电话联系':value.meetingMode==='CLIENT_MANAGED'?'参会方式由甲方确认':'地点待补充')}</span>;
}

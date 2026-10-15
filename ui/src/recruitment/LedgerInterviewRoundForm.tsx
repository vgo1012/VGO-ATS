import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Form, Input, Popconfirm, Space, Tag } from 'antd';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useState, type ReactNode } from 'react';
import { createIdempotencyKey } from '../api/idempotency';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { LoadingStatus } from '../components/LoadingStatus';
import { UserName } from '../display/BusinessReferences';
import { displayEnum } from '../display/labels';
import { interviewApi, type FeedbackView, type InterviewDetails } from '../interview/api';
import { InterviewFeedbackModal } from '../interview/InterviewFeedbackModal';
import { InterviewReference } from '../interview/InterviewMaterials';
import type { ActivityOrigin } from './activityApi';
import { recruitmentApi } from './api';
import { canProcessRecruitment, recruitmentProcessingDeniedMessage } from './processingPermissions';
import { interviewStepLabel } from './workflowLabels';
import './LedgerInterviewRoundForm.css';

dayjs.extend(utc);
dayjs.extend(timezone);
type Props={value:InterviewDetails;origin:ActivityOrigin;onClose:()=>void;onChanged:()=>Promise<unknown>};
type CancelAttempt={value:InterviewDetails['interview'];origin:ActivityOrigin};
const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请重试。';

export function LedgerInterviewRoundForm(props:Props) {
  const {actor}=useSession();
  return <RoundForm key={`${props.value.interview.id}:${actor?.userId}`} {...props}/>;
}

function RoundForm({value,origin,onClose,onChanged}:Props) {
  const current=value.interview;
  const {actor}=useSession();
  const [attempt,setAttempt]=useState<CancelAttempt>();
  const [syncError,setSyncError]=useState<unknown>();
  const [syncBusy,setSyncBusy]=useState(false);
  const [feedbackLocked,setFeedbackLocked]=useState(false);
  const application=useQuery({queryKey:['application',current.applicationId],queryFn:()=>recruitmentApi.application(current.applicationId),staleTime:0,refetchOnMount:'always',retry:false});
  const feedback=useQuery({queryKey:['interview-history',current.id,'round-feedback'],queryFn:()=>interviewApi.feedbackAll(current.id),staleTime:0,retry:false});
  const submitted=feedback.data?.find(item=>item.feedback.status==='SUBMITTED');
  const canProcess=Boolean(application.data&&!application.error&&(application.isFetchedAfterMount||!application.isFetching)&&!application.data.application.terminalAt&&canProcessRecruitment(actor,{ownerUserId:application.data.application.ownerUserId,collaboratorUserIds:application.data.collaboratorUserIds}));
  const canManageSchedule=canProcess&&Boolean(actor?.superAdmin||actor?.permissions.includes('interview.schedule'))&&current.status==='SCHEDULED'&&!submitted&&!feedback.isPending&&!feedback.error;
  const canFeedback=canProcess&&(['INTERNAL','EXTERNAL'].includes(current.interviewType)||Boolean(actor?.superAdmin||actor?.permissions.includes('interview.external-feedback.record')))&&current.status==='SCHEDULED'&&!submitted&&!feedback.isPending&&!feedback.error;
  const mutation=useMutation({mutationFn:(data:CancelAttempt)=>interviewApi.cancel(data.value,'取消本轮面试',data.origin),onError:error=>{if(error instanceof ApiProblemError&&[400,422].includes(error.problem.status))setAttempt(undefined);}});
  const cancelRound=async()=>{
    let next=attempt;
    if(!next){
      next={value:current,origin:{...origin,operationId:createIdempotencyKey()}};setAttempt(next);
    }
    const confirmed=mutation.data??await mutation.mutateAsync(next);
    if(confirmed){await onChanged();setAttempt(undefined);mutation.reset();}
  };
  const completeRound=async(saved:FeedbackView)=>{
    setSyncError(undefined);setSyncBusy(true);
    try{
      const latest=await interviewApi.get(current.id);
      if(latest.interview.status==='COMPLETED'){
        const latestApp=await recruitmentApi.application(current.applicationId);
        const step=latestApp.workflowSteps.find(item=>item.id===current.workflowStepId);
        if(step&&['PENDING','IN_PROGRESS'].includes(step.status)&&!latestApp.application.terminalAt&&canProcessRecruitment(actor,{ownerUserId:latestApp.application.ownerUserId,collaboratorUserIds:latestApp.collaboratorUserIds})){
          await recruitmentApi.completeStep(current.applicationId,step,saved.currentVersion.verdict==='PASS'?'PASSED':'FAILED',`正式面评已提交，本轮结果：${saved.currentVersion.verdict==='PASS'?'通过':'不通过'}`,current.clientSubmissionId??step.clientSubmissionId??undefined,undefined,origin);
        }
      }
      await onChanged();await feedback.refetch();
    }catch(error){setSyncError(error);throw error;}finally{setSyncBusy(false);}
  };
  const step=application.data?.workflowSteps.find(item=>item.id===current.workflowStepId);
  const pendingResult=canProcess&&submitted&&current.status==='COMPLETED'&&step&&['PENDING','IN_PROGRESS'].includes(step.status);
  const roundActions=(submit?:ReactNode)=>(canManageSchedule||submit)?<div className="ledger-interview-actions">
    {canManageSchedule?<Popconfirm title="确认取消本轮面试？" onConfirm={()=>cancelRound().catch(()=>undefined)} disabled={Boolean(attempt)||mutation.isPending||feedbackLocked||syncBusy}><Button type="text" danger disabled={Boolean(attempt)||mutation.isPending||feedbackLocked||syncBusy}>取消本轮面试</Button></Popconfirm>:<span/>}
    <div className="ledger-interview-actions-main"><Button autoInsertSpace={false} disabled={Boolean(attempt)||mutation.isPending||feedbackLocked||syncBusy} onClick={onClose}>关闭</Button>{submit}</div>
  </div>:null;
  return <WorkspaceDialog open size="form" resizeSurface className="ledger-interview-dialog" title="面试" onCancel={onClose} footer={null}>
    <p className="ledger-interview-heading"><InterviewReference id={current.id} field="candidateName"/> · <InterviewReference id={current.id} field="jdTitle"/> · {current.clientName??'内部招聘'}</p>
    {application.isPending?<LoadingStatus label="读取处理权限"/>:application.error?<Alert occurrence={application.error} type="error" title="招聘活动读取失败" action={<Button onClick={()=>void application.refetch()}>重试处理权限</Button>}/>:!canProcess?<Alert type="info" title={application.data?.application.terminalAt?'招聘活动已结束，记录只读。':recruitmentProcessingDeniedMessage}/>:null}
    {mutation.error?<Alert occurrence={mutation.error} type="error" title={errorText(mutation.error)}/>:null}
    {attempt&&!mutation.isPending?<Alert type="warning" title={mutation.data?'本轮面试已取消，请重试完成界面同步。':'上次取消结果尚未确认，请重试。'} action={<Button onClick={()=>void cancelRound().catch(()=>undefined)}>重试取消</Button>}/>:null}
    <Form layout="vertical" className="ledger-interview-facts">
      <Form.Item label="面试轮次"><Input aria-label="面试轮次" readOnly value={interviewStepLabel(current,current.interviewType==='INTERNAL'?'INTERNAL':'EXTERNAL')}/></Form.Item>
      <Form.Item label="开始时间"><Input aria-label="开始时间" readOnly value={dayjs(current.scheduledStartAt).tz(current.timezoneId).format('YYYY-MM-DD HH:mm')}/></Form.Item><Form.Item label="结束时间"><Input aria-label="结束时间" readOnly value={dayjs(current.scheduledEndAt).tz(current.timezoneId).format('YYYY-MM-DD HH:mm')}/></Form.Item>
      <Form.Item label="时区"><Input aria-label="时区" readOnly value={current.timezoneId==='Asia/Shanghai'?'北京时间（中国）':current.timezoneId}/></Form.Item><Form.Item label="面试方式"><Input aria-label="面试方式" readOnly value={displayEnum(current.meetingMode)}/></Form.Item>
      {current.locationText?<Form.Item label="面试地点"><Input aria-label="面试地点" readOnly value={current.locationText}/></Form.Item>:null}
      <Form.Item label="实际面试官"><Space wrap>{value.participants.length?value.participants.map(person=><Tag key={person.userId}><UserName id={person.userId}/></Tag>):current.interviewType==='EXTERNAL'?'未记录': '由甲方安排'}</Space></Form.Item>
    </Form>
    {feedback.error?<Alert occurrence={feedback.error} type="error" title="本轮面评读取失败" action={<Button onClick={()=>void feedback.refetch()}>重试面评状态</Button>}/>:feedback.isPending?<LoadingStatus label="读取本轮面评"/>:submitted?<><Tag color="green">面评已正式提交</Tag><p>本轮结果：{submitted.currentVersion.verdict==='PASS'?'通过':'不通过'}</p><Form layout="vertical"><Form.Item label="面评"><Input.TextArea aria-label="面评" readOnly value={submitted.currentVersion.shortComment??''} rows={4}/></Form.Item></Form>{submitted.currentVersion.externalFeedbackSource?<p>甲方反馈实际来源：{submitted.currentVersion.externalFeedbackSource}</p>:null}</>:current.status!=='SCHEDULED'?<Alert type="info" title={current.status==='CANCELLED'?'本轮面试已取消':'本轮面试已完成，记录只读'}/>:canFeedback?<InterviewFeedbackModal inline renderInlineActions={roundActions} onLockedChange={setFeedbackLocked} value={current} origin={origin} initialComment={current.initialComment??undefined} disabled={Boolean(attempt)||mutation.isPending} onClose={onClose} onSaved={completeRound}/>:<Alert type="info" title="当前账号无权填写本轮面评。"/>}
    {syncError?<Alert occurrence={syncError} type="error" title={`面评已保存，本轮结果尚未同步：${errorText(syncError)}`}/>:null}
    {pendingResult?<Button loading={syncBusy} onClick={()=>void completeRound(submitted).catch(()=>undefined)}>重试同步本轮结果</Button>:null}
    {!canFeedback?roundActions():null}
  </WorkspaceDialog>;
}

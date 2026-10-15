import { useQuery } from '@tanstack/react-query';
import { Button } from 'antd';
import { useState } from 'react';
import { createIdempotencyKey } from '../api/idempotency';
import { useSession } from '../auth/session';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { LoadingStatus } from '../components/LoadingStatus';
import { CandidateName, JobName } from '../display/BusinessReferences';
import { interviewApi } from '../interview/api';
import { CreateInterviewModal } from '../interview/InterviewDialogs';
import { LedgerInterviewRoundForm } from './LedgerInterviewRoundForm';
import { recruitmentApi, type ApplicationDetails } from './api';
import type { ActivityOrigin } from './activityApi';
import { actionError } from './candidateActions';
import { canProcessRecruitment, recruitmentProcessingDeniedMessage } from './processingPermissions';
import { interviewStepLabel } from './workflowLabels';

const isInterview=(code:string)=>code==='PRE_SUBMISSION_INTERNAL_INTERVIEW'||code==='CLIENT_AI_INTERVIEW'||code.startsWith('INTERVIEW_ROUND_');

/** List action owns the scheduling flow; historical facts remain in the activity reader. */
export function LedgerInterviewDialog({value,onClose,onChanged,roundNo}:{roundNo?:number;value:ApplicationDetails;onClose:()=>void;onChanged:()=>Promise<unknown>}) {
  const {actor}=useSession();
  const [selectedStepId,setSelectedStepId]=useState(()=>roundNo?undefined:value.workflowSteps.filter(item=>['PENDING','IN_PROGRESS'].includes(item.status)).sort((a,b)=>a.sequenceNo-b.sequenceNo).find(item=>isInterview(item.stepCode))?.id);
  const [origin]=useState<ActivityOrigin>(()=>({action:'INTERVIEW',operationId:createIdempotencyKey()}));
  const application=useQuery({queryKey:['application',value.application.id],queryFn:()=>recruitmentApi.application(value.application.id),initialData:value,refetchOnMount:'always',retry:false});
  const current=application.data;
  const app=current.application;
  const external=app.recruitmentType==='EXTERNAL';
  const scheduled=useQuery({queryKey:['application-interviews',app.id],queryFn:()=>interviewApi.allForApplication(app.id),retry:false});
  const step=current.workflowSteps.filter(item=>['PENDING','IN_PROGRESS'].includes(item.status)).sort((a,b)=>a.sequenceNo-b.sequenceNo)[0];
  const activeInterviewStep=step&&isInterview(step.stepCode)?step:undefined;
  const selectedStep=selectedStepId?current.workflowSteps.find(item=>item.id===selectedStepId):roundNo?current.workflowSteps.filter(item=>isInterview(item.stepCode)&&(item.roundNo??(Number(item.stepCode.match(/ROUND_(\d+)/)?.[1])||1))===roundNo).sort((a,b)=>Number(['PENDING','IN_PROGRESS'].includes(b.status))-Number(['PENDING','IN_PROGRESS'].includes(a.status))||b.sequenceNo-a.sequenceNo)[0]:activeInterviewStep;
  const interviewStep=selectedStep&&((selectedStep.roundNo??1)!==1||['PENDING','IN_PROGRESS'].includes(selectedStep.status)||(scheduled.data??[]).some(item=>item.workflowStepId===selectedStep.id&&item.status!=='CANCELLED'))?selectedStep:undefined;
  const existing=interviewStep?scheduled.data?.filter(item=>item.workflowStepId===interviewStep.id&&item.status!=='CANCELLED').sort((a,b)=>b.createdAt.localeCompare(a.createdAt))[0]:undefined;
  const detail=useQuery({queryKey:['interview',existing?.id],queryFn:()=>interviewApi.get(existing!.id),enabled:Boolean(existing),retry:false});
  const canProcess=canProcessRecruitment(actor,{ownerUserId:app.ownerUserId,collaboratorUserIds:current.collaboratorUserIds});
  const canSchedule=canProcess&&Boolean(actor?.superAdmin||actor?.permissions.includes('interview.schedule'));
  const arranged=(scheduled.data??[]).filter(item=>item.status!=='CANCELLED');
  const highestRound=Math.max(0,...arranged.map(item=>item.roundNo));
  const firstArranged=arranged.some(item=>item.roundNo===1);
  const round=highestRound+1;
  const nextStepOptions=(roundNo?[roundNo]:!firstArranged?[1]:Array.from({length:Math.max(1,6-highestRound)},(_,index)=>round+index))
    .filter(number=>firstArranged||number===1)
    .map(number=>({value:String(number),label:`第 ${number} 轮`}));
  const context=<p><CandidateName id={app.candidateId}/> · <JobName jdId={app.jdId}/> · {external?app.clientName??'甲方信息暂不可用':'内部招聘'}{interviewStep?` · ${interviewStepLabel(interviewStep,app.recruitmentType)}`:''}</p>;
  const loading=(!application.isFetchedAfterMount&&application.isFetching)||scheduled.isPending;
  const error=application.error??scheduled.error;
  const ready=!loading&&!error&&!app.terminalAt&&canSchedule&&(Boolean(interviewStep)||nextStepOptions.length>0)&&!existing;
  const requestedUnavailable=Boolean(roundNo&&!interviewStep&&(roundNo>1&&(!firstArranged||roundNo<=highestRound)));
  const canCreate=ready&&!requestedUnavailable&&(!interviewStep||['PENDING','IN_PROGRESS'].includes(interviewStep.status));
  return <WorkspaceDialog open size="form" title="面试" footer={null} onCancel={onClose}>
    {context}
    {loading?<LoadingStatus label="读取当前面试"/>:error?<Alert occurrence={error} type="error" title={actionError(error)} action={<Button onClick={()=>void Promise.all([application.refetch(),scheduled.refetch()])}>重试面试资料</Button>}/>:<>
      {!canProcess?<Alert type="info" title={recruitmentProcessingDeniedMessage}/>:null}
      {app.terminalAt?<Alert type="info" title="招聘活动已结束，面试历史请在详情与版本中查看。"/>:null}
      {!existing&&canProcess&&!canSchedule?<Alert type="info" title="当前账号没有面试排期权限。"/>:null}
      {requestedUnavailable?<Alert type="info" title={firstArranged?`第 ${roundNo} 轮已跳过，请选择更高轮次。`:"请先安排第 1 轮面试，再选择后续轮次。"}/>:null}
      {existing&&detail.isPending?<LoadingStatus label="读取本轮面试"/>:null}
      {existing&&detail.error?<Alert type="error" title={actionError(detail.error)} action={<Button onClick={()=>void detail.refetch()}>重试本轮面试</Button>}/>:null}
      {existing&&detail.data?<LedgerInterviewRoundForm key={existing.id} value={detail.data} origin={origin} onClose={onClose} onChanged={onChanged}/>:null}
    </>}
    {canCreate?<CreateInterviewModal open application={current} {...(interviewStep?{workflowStep:interviewStep}:{nextStepOptions,prepareWorkflowStep:async(code:string,reason:string)=>{
      const requestedRound=Number(code);
      const latest=await recruitmentApi.application(app.id);
      const pending=latest.workflowSteps.find(item=>['PENDING','IN_PROGRESS'].includes(item.status)&&item.stepCode.startsWith('INTERVIEW_ROUND_')&&(item.roundNo??Number(item.stepCode.match(/ROUND_(\d+)/)?.[1]))===requestedRound);
      if(pending)return pending;
      const updated=await recruitmentApi.nextAction(latest.application,'INTERVIEW_ROUND_1',reason,origin,requestedRound);
      const created=updated.workflowSteps.find(item=>['PENDING','IN_PROGRESS'].includes(item.status)&&item.stepCode.startsWith('INTERVIEW_ROUND_')&&item.roundNo===requestedRound);
      if(!created)throw new Error('未读取到新面试步骤，请关闭后重新打开查看最新进展。');
      return created;
    }})} origin={origin} context={context} onClose={onClose} onSaved={async(saved)=>{setSelectedStepId(saved.interview.workflowStepId);await onChanged();}}/>:null}
  </WorkspaceDialog>;
}

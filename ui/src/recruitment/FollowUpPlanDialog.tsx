import { createIdempotencyKey } from '../api/idempotency';
import { ApiProblemError } from '../api/problem';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Descriptions, Form, Input, Select } from 'antd';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import { useRef, useState, type ReactNode } from 'react';
import { type ActivityOrigin } from './activityApi';
import { Controller, useForm } from 'react-hook-form';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ScheduleDateTimePicker } from '../components/ScheduleDateTimePicker';
import { addThirtyMinutes, isTenMinuteTime } from '../components/scheduleTime';
import { actionError } from './candidateActions';
import { recruitmentApi, type RecruitmentApplication } from './api';
import { followUpPlansApi, type FollowUpPlan, type PlanCompletion, type PlanFields } from './followUpPlansApi';
import { findCurrentFollowUpPlan, matchesReschedule } from './followUpPlanRecovery';
import { displayEnum, formatDateTime } from '../display/labels';

dayjs.extend(utc);dayjs.extend(timezone);
type PlanFormFields={startsAt:string;endsAt:string;channel:string;note:string;reason:string;result:string;nextPlan:string};
const draftSignature=(fields:PlanFormFields)=>JSON.stringify([fields.startsAt,fields.endsAt,fields.channel,fields.note.trim()]);
export function FollowUpPlanDialog({application,plan,action='schedule',onClose,onSaved,origin,introduction,title}:{origin?:ActivityOrigin|undefined;introduction?:ReactNode;title?:string;application?:RecruitmentApplication;plan?:FollowUpPlan;action?:'schedule'|'cancel'|'complete';onClose:()=>void;onSaved:(result?:string)=>Promise<unknown>}) {
  const requestDraft=useRef<{signature:string;key:string}|undefined>(undefined);
  const submitted=useRef<{body:PlanFields;draft:string}|undefined>(undefined);
  const submittedCancel=useRef<{reason:string;draft:string}|undefined>(undefined);
  const submittedCompletion=useRef<{plan:FollowUpPlan;reason:string;completion:PlanCompletion;key:string}|undefined>(undefined);
  const [completionFrozen,setCompletionFrozen]=useState(false);
  const [completionRejected,setCompletionRejected]=useState(false);
  const completionPlan=useRef<FollowUpPlan|undefined>(undefined);
  const [needsRecovery,setNeedsRecovery]=useState(false);
  const [currentPlan,setCurrentPlan]=useState<FollowUpPlan>();
  const inFlight=useRef(false);
  const {control,handleSubmit,getValues,setValue,formState:{errors}}=useForm({defaultValues:{startsAt:plan?dayjs(plan.startsAt).tz('Asia/Shanghai').format('YYYY-MM-DDTHH:mm'):'',endsAt:plan?dayjs(plan.endsAt).tz('Asia/Shanghai').format('YYYY-MM-DDTHH:mm'):'',channel:plan?.channel??'PHONE',note:plan?.note??'',reason:'',result:'',nextPlan:''}});
  const details=useQuery({queryKey:['application',plan?.applicationId],queryFn:()=>recruitmentApi.application(plan!.applicationId),enabled:action==='complete'&&Boolean(plan),refetchOnWindowFocus:false});
  const currentStep=details.data?.workflowSteps.filter(step=>['PENDING','IN_PROGRESS'].includes(step.status)).sort((a,b)=>a.sequenceNo-b.sequenceNo)[0];
  const communicationStep=currentStep?.stepCode==='CANDIDATE_COMMUNICATION'?currentStep:undefined;
  const mutation=useMutation({mutationFn:async({fields,acceptedVersion}:{fields:PlanFormFields;acceptedVersion?:string})=>{
    if(action!=='schedule'){
      if(!plan)throw new Error('预约不存在，请刷新后重试');
      if(action==='cancel'){
        if(needsRecovery&&!acceptedVersion)throw new Error('请先核对最新预约。');
        const reason=fields.reason.trim();
        submittedCancel.current={reason:reason||'HR 取消沟通预约',draft:reason};
        setNeedsRecovery(true);setCurrentPlan(undefined);
        const target={...plan,version:acceptedVersion??plan.version};
        return origin?followUpPlansApi.finish(target,action,reason,undefined,undefined,origin):followUpPlansApi.finish(target,action,reason);
      }
      if(!submittedCompletion.current){
        if(details.isFetching||details.error||!details.data)throw new Error('请先读取最新的招聘进展');
        submittedCompletion.current={plan:{...(completionPlan.current??plan)},reason:fields.reason.trim(),key:createIdempotencyKey(),completion:{
          result:fields.result as PlanCompletion['result'],nextPlan:fields.nextPlan.trim(),
          ...(communicationStep?{workflowStepId:communicationStep.id,workflowStepVersion:String(communicationStep.version)}:{}),
        }};
        setCompletionFrozen(true);
      }
      const original=submittedCompletion.current;
      try{return await followUpPlansApi.finish(original.plan,action,original.reason,original.completion,original.key,...(origin?[origin] as const:[] as const));}
      catch(error){
        if(error instanceof ApiProblemError&&error.problem.status===409&&['FOLLOW_UP_STEP_CHANGED','FOLLOW_UP_PLAN_CHANGED'].includes(error.problem.code))setCompletionRejected(true);
        throw error;
      }
    }
    if(plan&&needsRecovery&&!acceptedVersion)throw new Error('请先核对最新预约。');
    const body={startsAt:dayjs.tz(fields.startsAt,'Asia/Shanghai').toISOString(),endsAt:dayjs.tz(fields.endsAt,'Asia/Shanghai').toISOString(),channel:fields.channel,note:fields.note.trim(),version:acceptedVersion??plan?.version??String(application!.version)};
    const signature=JSON.stringify(body);if(requestDraft.current?.signature!==signature)requestDraft.current={signature,key:createIdempotencyKey()};
    if(plan){submitted.current={body,draft:draftSignature(fields)};setNeedsRecovery(true);setCurrentPlan(undefined);}
    return plan?followUpPlansApi.reschedule(plan,body,...(origin?[origin] as const:[] as const)):followUpPlansApi.create(application!.id,body,requestDraft.current.key,...(origin?[origin] as const:[] as const));
  },onSuccess:async()=>{await onSaved(action==='complete'?submittedCompletion.current?.completion.result:undefined);setNeedsRecovery(false);}});
  const recovery=useMutation({mutationFn:async()=>{
    const latest=await findCurrentFollowUpPlan(plan!.applicationId,plan!.id);
    const matches=action==='cancel'
      ?submittedCancel.current&&latest.status==='CANCELLED'&&latest.resultReason===submittedCancel.current.reason&&getValues('reason').trim()===submittedCancel.current.draft
      :submitted.current&&matchesReschedule(latest,submitted.current.body)&&submitted.current.draft===draftSignature(getValues());
    if(matches){
      await onSaved();setNeedsRecovery(false);setCurrentPlan(undefined);
    }else setCurrentPlan(latest);
  }});
  const prepareCompletion=useMutation({mutationFn:async()=>{
    const latest=await findCurrentFollowUpPlan(plan!.applicationId,plan!.id);
    setCurrentPlan(latest);
    if(latest.status!=='SCHEDULED')throw new Error('当前预约已结束，不能继续记录完成。填写内容已保留。');
    const refreshed=await details.refetch();
    if(refreshed.error)throw refreshed.error;
    if(!refreshed.data)throw new Error('请先读取最新的招聘进展');
    completionPlan.current=latest;submittedCompletion.current=undefined;
    setCompletionFrozen(false);setCompletionRejected(false);setCurrentPlan(undefined);mutation.reset();
  }});
  const busy=mutation.isPending||recovery.isPending||prepareCompletion.isPending;
  const submit=(acceptedVersion?:string)=>{
    if(inFlight.current)return;
    inFlight.current=true;
    return handleSubmit(values=>mutation.mutateAsync({fields:values,...(acceptedVersion?{acceptedVersion}:{})}).catch(()=>undefined))().finally(()=>{inFlight.current=false;});
  };
  const checkCurrent=()=>{if(inFlight.current)return;inFlight.current=true;setCurrentPlan(undefined);void recovery.mutateAsync().catch(()=>undefined).finally(()=>{inFlight.current=false;});};
  const checkCompletion=()=>{if(inFlight.current)return;inFlight.current=true;void prepareCompletion.mutateAsync().catch(()=>undefined).finally(()=>{inFlight.current=false;});};
  return <WorkspaceDialog open size="form" title={title??(action==='cancel'?'取消沟通预约':action==='complete'?'确认沟通已完成':plan?'调整沟通预约':'预约沟通')} okText={action==='schedule'?'保存到日程':action==='cancel'?'确认取消':'记录完成'} onCancel={onClose} closable={!busy} keyboard={!busy} mask={{closable:!busy}} cancelButtonProps={{disabled:busy}} confirmLoading={busy} okButtonProps={{disabled:busy||needsRecovery||action==='complete'&&(completionFrozen||details.isFetching||Boolean(details.error)||!details.data)}} onOk={()=>submit()}>
    {introduction}
    {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={actionError(mutation.error)} action={action==='complete'&&!completionFrozen?<Button onClick={()=>void details.refetch()}>重新核对当前步骤</Button>:undefined}/>:null}
    {completionFrozen?completionRejected?<><p>完成记录未保存，预约或步骤已变化。填写内容已保留，请重新核对后记录。</p><Button disabled={busy} loading={prepareCompletion.isPending} onClick={checkCompletion}>重新核对当前步骤</Button></>:<><p>完成记录已固定，重试会恢复同一笔记录；填写内容已保留，暂不能修改。</p><Button disabled={busy} loading={mutation.isPending} onClick={()=>void submit()}>重试记录完成</Button></>:null}
    {prepareCompletion.error?<Alert occurrence={prepareCompletion.error} type="error" showIcon title={actionError(prepareCompletion.error)}/>:null}
    {needsRecovery?<><p>{action==='cancel'?'取消':'改期'}结果需要核对，填写内容仍保留。</p><Button loading={recovery.isPending} disabled={busy} onClick={checkCurrent}>核对最新预约</Button></>:null}
    {recovery.error?<Alert occurrence={recovery.error} type="error" showIcon title={actionError(recovery.error)}/>:null}
    {currentPlan?<section aria-label="当前预约"><Alert type="warning" showIcon title={currentPlan.status==='SCHEDULED'?'当前预约已变化，填写内容已保留':action==='cancel'?'当前预约已结束，不能继续取消':action==='complete'?'当前预约已结束，不能继续记录完成':'当前预约已结束，不能继续改期'}/><Descriptions column={1} items={[{key:'time',label:'当前时间（北京时间）',children:`${formatDateTime(currentPlan.startsAt)} 至 ${formatDateTime(currentPlan.endsAt)}`},{key:'channel',label:'当前沟通方式',children:displayEnum(currentPlan.channel)},{key:'note',label:'当前沟通事项',children:currentPlan.note||'未填写'},{key:'status',label:'当前状态',children:({SCHEDULED:'待沟通',COMPLETED:'已沟通',CANCELLED:'已取消'})[currentPlan.status]},...(action==='cancel'?[{key:'reason',label:'当前结果原因',children:currentPlan.resultReason||'未填写'}]:[])]}/>{currentPlan.status==='SCHEDULED'&&action!=='complete'?<Button disabled={busy} onClick={()=>void submit(currentPlan.version)}>按最新版本重试</Button>:null}</section>:null}
    <p>{action==='schedule'?'填写约定的联系时间，保存后进入日程协同。时间为北京时间，沟通事项选填。':action==='complete'?'本次结果只需记录一次，保存后自动归入沟通和跟进历史，并移出待办日历。':'取消后自动归入历史，需要时可以重新预约。'}</p>
    {action==='complete'&&!completionFrozen?details.error?<Alert occurrence={details.error} type="error" showIcon title={actionError(details.error)} action={<Button onClick={()=>void details.refetch()}>重试读取招聘进展</Button>}/>:details.isFetching?<p>正在核对当前步骤…</p>:communicationStep?<Alert type="info" showIcon title="同步当前候选人沟通步骤" description="选择“沟通完成，继续推进”会同时完成此沟通步骤；已有下一步骤将自动进行中。"/>:<p>本次记录保存在活动沟通历史，面试和招聘决定按各自结果推进。</p>:null}
    <Form layout="vertical" component="div" disabled={busy||completionFrozen}>{action==='schedule'?<>
      <Form.Item label="开始时间（北京时间）" htmlFor="plan-start" validateStatus={errors.startsAt?'error':''}>
      <Controller name="startsAt" control={control} rules={{required:'请选择约定的开始时间',validate:{interval:value=>isTenMinuteTime(value)||'分钟请选择 00、10、20、30、40 或 50',future:value=>dayjs.tz(value,'Asia/Shanghai').isAfter(dayjs())||'请选择未来的沟通时间'}}} render={({field})=><ScheduleDateTimePicker {...field} onChange={value=>{field.onChange(value);setValue('endsAt',addThirtyMinutes(value),{shouldValidate:true});}} id="plan-start" aria-invalid={Boolean(errors.startsAt)}/>}/>
      {errors.startsAt?<p role="alert">{errors.startsAt.message}</p>:null}</Form.Item>
      <Form.Item label="结束时间（北京时间）" htmlFor="plan-end" validateStatus={errors.endsAt?'error':''}>
      <Controller name="endsAt" control={control} rules={{required:'请选择预计结束时间',validate:{interval:value=>isTenMinuteTime(value)||'分钟请选择 00、10、20、30、40 或 50',order:value=>value>getValues('startsAt')||'结束时间必须晚于开始时间'}}} render={({field})=><ScheduleDateTimePicker {...field} id="plan-end" aria-invalid={Boolean(errors.endsAt)}/>}/>
      {errors.endsAt?<p role="alert">{errors.endsAt.message}</p>:null}</Form.Item>
      <Form.Item label="沟通方式" htmlFor="plan-channel">
      <Controller name="channel" control={control} render={({field})=><Select {...field} id="plan-channel" style={{width:'100%'}} options={[{value:'PHONE',label:'电话'},{value:'WECHAT',label:'微信'},{value:'ENTERPRISE_WECHAT',label:'企业微信'},{value:'EMAIL',label:'邮件'},{value:'IN_PERSON',label:'当面'},{value:'OTHER',label:'其他'}]}/>}/></Form.Item>
      <Form.Item label="沟通事项（选填）" htmlFor="plan-note" validateStatus={errors.note?'error':''}>
      <Controller name="note" control={control} rules={{maxLength:500}} render={({field})=><Input.TextArea {...field} id="plan-note" rows={3} maxLength={500}/>}/>
      {errors.note?<p role="alert">{errors.note.message??'最多 500 字'}</p>:null}</Form.Item>
    </>:<>
      {action==='complete'?<Form.Item label="本次沟通结果" htmlFor="plan-result" validateStatus={errors.result?'error':''}>
        <Controller name="result" control={control} rules={{required:'请选择实际沟通结果'}} render={({field})=><Select {...field} id="plan-result" placeholder="请选择实际结果" options={[{value:'CONTINUE',label:'沟通完成，继续推进'},{value:'FOLLOW_UP',label:'还需跟进'},{value:'NO_RESPONSE',label:'未联系上'},{value:'DECLINED',label:'候选人暂无意向'}]}/>}/>
        {errors.result?<p role="alert">{errors.result.message}</p>:null}
      </Form.Item>:null}
      <Form.Item htmlFor="plan-reason" label={action==='complete'?'实际沟通情况（选填）':'取消原因（选填）'} validateStatus={errors.reason?'error':''}>
      <Controller name="reason" control={control} rules={{maxLength:500}} render={({field})=><Input.TextArea {...field} id="plan-reason" rows={3} maxLength={500}/>}/>
      {errors.reason?<p role="alert">{errors.reason.message??'最多 500 字'}</p>:null}</Form.Item>
      {action==='complete'?<Form.Item label="下一步计划（选填）" htmlFor="plan-next"><Controller name="nextPlan" control={control} rules={{maxLength:500}} render={({field})=><Input.TextArea {...field} id="plan-next" rows={2} maxLength={500}/>}/></Form.Item>:null}
    </>}</Form>
  </WorkspaceDialog>;
}

import { InterviewReference } from './InterviewMaterials';
import type { ActivityOrigin } from '../recruitment/activityApi';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Card, Form, Input, Select } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { createIdempotencyKey } from '../api/idempotency';
import { useSession } from '../auth/session';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { interviewApi, type FeedbackView, type Interview } from './api';

type Props={renderInlineActions?:(submit:ReactNode)=>ReactNode;onLockedChange?:(locked:boolean)=>void;inline?:boolean;initialComment?:string|undefined;disabled?:boolean;origin?:ActivityOrigin|undefined;value:Interview;onClose:()=>void;onSaved:(value:FeedbackView)=>Promise<unknown>};
type Values={verdict:string;shortComment?:string;externalFeedbackSource?:string};
type Submission={key:string;body:Parameters<typeof interviewApi.feedback>[1]};
const title=(value:Interview)=>['INTERNAL','EXTERNAL'].includes(value.interviewType)?'填写面评':'代录甲方外部反馈';

export function InterviewFeedbackModal(props:Props) {
  const {actor}=useSession();
  return <FeedbackSession key={JSON.stringify([props.value.id,actor?.userId])} {...props} authorId={actor?.userId}/>;
}

function FeedbackSession(props:Props&{authorId:string|undefined}) {
  const saved=useQuery({queryKey:['interview-own-feedback',props.value.id,props.authorId],
    queryFn:()=>interviewApi.feedbackByAuthor(props.value.id,props.authorId!),enabled:Boolean(props.authorId),staleTime:0,
    refetchOnWindowFocus:false,refetchOnReconnect:false,retry:false});
  const [form]=Form.useForm<Values>();
  const [submission,setSubmission]=useState<Submission>();
  const [inlineBusy,setInlineBusy]=useState(false);
  const [inlineError,setInlineError]=useState<unknown>();
  const ready=!saved.isPending&&!saved.isFetching&&!saved.error;
  const feedback=saved.data;
  const submitted=feedback?.feedback.status==='SUBMITTED';
  const mutation=useMutation({mutationFn:(attempt:Submission)=>interviewApi.feedback(props.value.id,attempt.body,attempt.key,...(props.origin?[props.origin]:[])),
    onError:error=>{
      if(error instanceof ApiProblemError&&[400,422].includes(error.problem.status))setSubmission(undefined);
    }});
  const locked=Boolean(submission)||mutation.isPending||inlineBusy;
  const onLockedChange=props.onLockedChange;
  useEffect(()=>{onLockedChange?.(locked);},[locked,onLockedChange]);
  const submit=async()=>{
    let attempt=submission;
    if(!attempt){
      const data=await form.validateFields();
      attempt={key:createIdempotencyKey(),body:{
        verdict:data.verdict,status:'SUBMITTED',shortComment:data.shortComment??'',
        ...(data.externalFeedbackSource?{externalFeedbackSource:data.externalFeedbackSource}:{}),
        changeReason:'正式提交面评',feedbackOccurredAt:feedback?.currentVersion.feedbackOccurredAt??new Date().toISOString(),
      }};
      setSubmission(attempt);
    }
    // 已确认保存后，只重试后续界面同步，不再发送面评写入。
    const confirmed=mutation.data??await mutation.mutateAsync(attempt).catch(()=>undefined);
    if(confirmed)await props.onSaved(confirmed);
  };
  const content=<>
    {inlineError?<Alert occurrence={inlineError} type="error" title={inlineError instanceof Error?inlineError.message:'面评后续处理失败，请重试。'}/>:null}
    {props.origin&&!props.inline?<p><InterviewReference id={props.value.id} field="candidateName"/> · <InterviewReference id={props.value.id} field="jdTitle"/> · {props.value.clientName??'内部招聘'} · {title(props.value)}</p>:null}
    {saved.error?<Alert occurrence={saved.error} type="error" showIcon title="已保存面评读取失败，读取成功后才能编辑。" action={<Button onClick={()=>void saved.refetch()}>重试面评</Button>}/>:saved.isPending||saved.isFetching?<Card loading/>:<>
    {submitted?<Alert type="info" showIcon title="面评已正式提交" description="正式提交后不再支持修改，也不提供版本入口。"/>:<>
    {['INTERNAL','EXTERNAL'].includes(props.value.interviewType)&&!props.inline?<Alert type="info" showIcon title="实际面试官仅作记录" description="当前有权处理招聘活动的账号可填写本轮面评，提交人按当前登录账号记录。"/>:null}
    {submission&&!mutation.isPending?<Alert type="warning" showIcon title={mutation.data?'面评已保存，后续界面更新未完成，请重试继续。':'上次面评提交结果尚未确认，请重试确认后再修改。'}/>:null}
    {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={mutation.error instanceof ApiProblemError?mutation.error.problem.detail:'面评保存失败，请重试。'}/>:null}
    <Form form={form} layout="vertical" aria-label="面评提交内容" tabIndex={0} disabled={Boolean(props.disabled)||Boolean(submission)||mutation.isPending||inlineBusy} initialValues={{verdict:feedback?.currentVersion.verdict??'PASS',
      shortComment:feedback?.currentVersion.shortComment??props.initialComment??props.value.initialComment??'',externalFeedbackSource:feedback?.currentVersion.externalFeedbackSource??''}}>
      <Form.Item name="verdict" label="本轮结果" rules={[{required:true}]}><Select options={[{value:'PASS',label:'通过'},{value:'FAIL',label:'不通过'}]}/></Form.Item>
      <Form.Item name="shortComment" label="面评" rules={[{max:1000}]}><Input.TextArea rows={4}/></Form.Item>
      {props.value.interviewType.startsWith('EXTERNAL_CLIENT')?<Form.Item name="externalFeedbackSource" label="甲方反馈实际来源" rules={[{required:true},{max:500}]}><Input placeholder="例如：甲方联系人王经理电话确认"/></Form.Item>:null}
    </Form></>}</>}
  </>;
  const inlineSubmit=ready&&!submitted?<Button type="primary" disabled={Boolean(props.disabled)} loading={inlineBusy||mutation.isPending} onClick={()=>{
    if(inlineBusy)return;
    setInlineBusy(true);setInlineError(undefined);
    void submit().catch(error=>{if(!(error&&typeof error==='object'&&'errorFields' in error))setInlineError(error);}).finally(()=>setInlineBusy(false));
  }}>正式提交面评</Button>:null;
  if(props.inline)return <>{content}{props.renderInlineActions?props.renderInlineActions(inlineSubmit):inlineSubmit}</>;
  return <WorkspaceDialog open {...(props.origin?{cancelText:'返回'}:{})} title={props.origin?'面试':title(props.value)} {...(ready&&!submitted?{okText:'正式提交面评',onOk:submit}:{footer:null})} onCancel={props.onClose}
    confirmLoading={mutation.isPending} okButtonProps={{'aria-label':'正式提交面评'}}>
    {content}
  </WorkspaceDialog>;
}

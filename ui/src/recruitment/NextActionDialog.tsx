import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation } from '@tanstack/react-query';
import { Form, Input, Radio, Space } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { actionError } from './candidateActions';
import { recruitmentApi, type ApplicationDetails } from './api';

export function NextActionDialog({value,onClose,onSaved}:{value:ApplicationDetails;onClose:()=>void;onSaved:(value:ApplicationDetails)=>Promise<unknown>}) {
  const {control,handleSubmit,formState:{errors}}=useForm({defaultValues:{stepCode:'',reason:''}});
  const mutation=useMutation({mutationFn:({stepCode,reason}:{stepCode:string;reason:string})=>recruitmentApi.nextAction(value.application,stepCode,reason.trim()),onSuccess:onSaved});
  const external=value.application.recruitmentType==='EXTERNAL';
  const round=value.workflowSteps.filter(step=>step.stepCode.startsWith('INTERVIEW_ROUND_')&&['PASSED','FAILED','WITHDRAWN','SKIPPED'].includes(step.status)).length+1;
  const roundName=round===1?'一面':round===2?'二面':round===3?'三面':`第 ${round} 轮面试`;
  const options=[{value:'CANDIDATE_COMMUNICATION',label:'继续沟通',description:'确认意向、补充信息，或约定下次联系。'},
    ...(external?[{value:'PRE_SUBMISSION_INTERNAL_INTERVIEW',label:'安排内部面试',description:'内部预面，由当前招聘活动处理人填写面评。'},{value:'INTERVIEW_ROUND_1',label:`安排甲方面试 · ${roundName}`,description:'按甲方反馈逐轮安排，次数不预定。'},{value:'CLIENT_AI_INTERVIEW',label:'安排甲方 AI 面试',description:'外部流程，由 HR 人工记录。'}]:[{value:'INTERVIEW_ROUND_1',label:`安排下一轮面试 · ${roundName}`,description:'只安排这一轮，面评后再决定是否继续。'}]),
    {value:'DECISION',label:'人工招聘决定',description:'不再追加面试，复核已有事实后作出人工决定。'}];
  return <WorkspaceDialog open size="form" title="选择下一步" okText="确认下一步" confirmLoading={mutation.isPending} onCancel={onClose} onOk={()=>handleSubmit(fields=>mutation.mutateAsync(fields).catch(()=>undefined))()}>
    <p>只确定眼前这一步。沟通与面试时间在双方确认后填写；完成后可以继续沟通、追加面试或作出决定。</p>
    {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={actionError(mutation.error)}/>:null}
    <Form layout="vertical" component="div"><Form.Item label="本次要做的事">
    <Controller name="stepCode" control={control} rules={{required:'请选择本次要做的事'}} render={({field})=><Radio.Group {...field} aria-label="下一步安排"><Space orientation="vertical" size={12}>{options.map(option=><Radio key={option.value} value={option.value}><b>{option.label}</b><br/><span>{option.description}</span></Radio>)}</Space></Radio.Group>}/>
    {errors.stepCode?<p role="alert">{errors.stepCode.message}</p>:null}
    </Form.Item><Form.Item label="本次安排依据（选填）" htmlFor="next-action-reason">
    <Controller name="reason" control={control} rules={{maxLength:{value:500,message:'最多 500 字'}}} render={({field})=><Input.TextArea {...field} id="next-action-reason" rows={3} maxLength={500} aria-invalid={Boolean(errors.reason)}/>}/>
    {errors.reason?<p role="alert">{errors.reason.message}</p>:null}</Form.Item></Form>
  </WorkspaceDialog>;
}

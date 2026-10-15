import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useLayoutEffect } from 'react';
import { useMutation } from '@tanstack/react-query';
import { App, Col, Form, Input, Row, Select } from 'antd';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import { ApiProblemError } from '../api/problem';
import { confirmCurrentIntake, talentApi, type IdentityConflict, type ResumeIntake } from './api';
import { intakeIdentityReview } from './intakeIdentityReview';
import { IdentityConflictDetails } from './IdentityConflictDetails';
import { useIdentityConflictDetails } from './useIdentityConflictDetails';

const errorMessage=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';

export function ConfirmIntakeModal({value,onClose,onSaved}:{value?:ResumeIntake;onClose:()=>void;onSaved:()=>Promise<unknown>}){
  const [form]=Form.useForm<{fullName:string;email?:string;phone?:string}>(); const {message}=App.useApp();
  const review=value?intakeIdentityReview(value):undefined;
  const conflictPending=value?.failureCode==='IDENTITY_CONFLICT_REQUIRES_REVIEW';
  const ready=value?.status==='READY_FOR_CONFIRMATION'&&!value.failureCode;
  const mutation=useMutation({mutationFn:(body:{fullName:string;email?:string;phone?:string})=>confirmCurrentIntake(value!,body),onSuccess:async()=>{message.success('收件已确认入库，可在人才池查看');await onSaved();onClose();},onError:async error=>{if(error instanceof ApiProblemError&&error.problem.status===409)await onSaved();}});
  const resetMutation=mutation.reset;
  useLayoutEffect(()=>{if(value){form.setFieldsValue({fullName:value.parsedFullName??'',email:value.parsedEmail??'',phone:value.parsedPhone??''});resetMutation();}},[form,value,resetMutation]);
  return <Modal okText={ready?'确认入库':'确认异常身份'} title={`${ready?'确认入库':'核对异常身份'} · ${value?.originalFileName??''}`} open={Boolean(value)} onCancel={()=>{if(!mutation.isPending)onClose();}} confirmLoading={mutation.isPending} okButtonProps={{disabled:mutation.isPending||conflictPending}} cancelButtonProps={{disabled:mutation.isPending}} closable={!mutation.isPending} maskClosable={!mutation.isPending} keyboard={!mutation.isPending} onOk={()=>{if(conflictPending)return;return form.validateFields().then(v=>mutation.mutateAsync(v).catch(()=>undefined));}} destroyOnHidden>
    {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={errorMessage(mutation.error)}/>:null}{review?<Alert type="warning" showIcon title={review.reason} description={review.advice}/>:null}
    <Form form={form} layout="vertical"><Form.Item name="fullName" label="候选人姓名" rules={[{required:true,message:'请输入候选人姓名'}]}><Input/></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="email" label="邮箱"><Input/></Form.Item></Col><Col span={12}><Form.Item name="phone" label="手机号"><Input/></Form.Item></Col></Row></Form>
  </Modal>;
}

export function ConflictResolutionModal({value,onClose,onSaved}:{value?:IdentityConflict;onClose:()=>void;onSaved:()=>Promise<unknown>}){
  const [form]=Form.useForm<{resolution:string;chosenCandidateId?:string;reason:string}>(); const {message}=App.useApp();
  const details=useIdentityConflictDetails(value);
  const mutation=useMutation({mutationFn:(body:{resolution:string;chosenCandidateId?:string;reason:string})=>talentApi.resolveConflict(value!.id,body),onSuccess:async()=>{message.success('身份冲突已处理并留痕');await onSaved();onClose();},onError:async error=>{if(error instanceof ApiProblemError&&error.problem.status===409)await onSaved();}});
  const resolution=Form.useWatch('resolution',form);
  const resetMutation=mutation.reset;
  useLayoutEffect(()=>{if(value){form.resetFields();form.setFieldsValue({resolution:'USE_EXISTING',...(value.candidateIdA?{chosenCandidateId:value.candidateIdA}:{})});resetMutation();}},[form,value,resetMutation]);
  return <Modal size="form" okText="确认处理" title="人工处理身份冲突" open={Boolean(value)} onCancel={()=>{if(!mutation.isPending)onClose();}} confirmLoading={mutation.isPending} okButtonProps={{disabled:mutation.isPending||!details.ready}} cancelButtonProps={{disabled:mutation.isPending}} closable={!mutation.isPending} maskClosable={!mutation.isPending} keyboard={!mutation.isPending} onOk={()=>{if(!details.ready)return;return form.validateFields().then(v=>mutation.mutateAsync(v.resolution==='CREATE_SEPARATE'?{resolution:v.resolution,reason:v.reason}:v).catch(()=>undefined));}} destroyOnHidden>
    {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={errorMessage(mutation.error)}/>:null}{value?<IdentityConflictDetails conflict={value} details={details}/>:null}
    {!details.restricted?<Form form={form} layout="vertical">
      <Form.Item name="resolution" label="处理方式" rules={[{required:true}]}>
        <Select disabled={!details.ready} options={[{value:'USE_EXISTING',label:'关联已有候选人'},{value:'CREATE_SEPARATE',label:'创建独立候选人'},...(value?.candidateIdB?[{value:'MERGE',label:'合并候选人'}]:[])]}/>
      </Form.Item>
      {resolution!=='CREATE_SEPARATE'&&details.ready?<Form.Item name="chosenCandidateId" label="保留/关联候选人" rules={[{required:true}]}>
        <Select options={details.ids.map((id,index)=>({value:id,label:`已有档案 ${index+1} · ${details.candidates[index]?.data?.fullName??''}`}))}/>
      </Form.Item>:null}
      <Form.Item name="reason" label="处理原因" rules={[{required:true},{max:500}]}><Input.TextArea rows={3}/></Form.Item>
    </Form>:null}
  </Modal>;
}

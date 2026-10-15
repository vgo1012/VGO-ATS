import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Modal, Space } from 'antd';
import { useState } from 'react';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { demandApi, type JdSummary } from '../demand/api';
import { JdSelector } from '../demand/JdSelector';
import { taskApi } from '../tasks/task';
import { talentApi } from './api';

export interface SharedCandidateTransferTarget {
  candidateId:string;
  resumeVersionId:string;
  candidateVersion:number;
}

const pause=(milliseconds:number)=>new Promise(resolve=>setTimeout(resolve,milliseconds));

async function requireSuccessfulScore(taskId:string){
  for(let attempt=0;attempt<450;attempt++){
    const task=await taskApi.get(taskId);
    if(task.status==='SUCCEEDED')return;
    if(['FAILED','CANCELLED'].includes(task.status)){
      throw new Error(`AI 评分失败${task.errorCode?`（${task.errorCode}）`:''}，简历仍保留在共享库`);
    }
    await pause(2_000);
  }
  throw new Error('AI 评分等待超时，简历仍保留在共享库，请稍后重试转入');
}

export function SharedCandidateTransferDialog({open,candidates,onClose,onCompleted}:{
  open:boolean;
  candidates:SharedCandidateTransferTarget[];
  onClose:()=>void;
  onCompleted:()=>void;
}){
  const [jd,setJd]=useState<JdSummary>();
  const [phase,setPhase]=useState<'SCORING'|'TRANSFERRING'>('SCORING');
  const {message}=App.useApp();
  const qc=useQueryClient();
  const mutation=useMutation({mutationFn:async()=>{
    if(!jd)throw new Error('请选择转入后对应的 JD');
    const versions=await demandApi.jdVersions(jd.id);
    const current=versions.items.find(value=>value.versionNo===jd.currentVersionNo&&value.status==='PUBLISHED');
    if(!current)throw new Error('当前 JD 版本不可用于评分，请重新选择');
    setPhase('SCORING');
    const scored=await talentApi.scoreSharedReclaims(current.id,candidates.map(value=>({
      candidateId:value.candidateId,resumeVersionId:value.resumeVersionId,
    })));
    await Promise.all(scored.items.map(value=>requireSuccessfulScore(value.taskId)));
    const bindings=new Map(scored.items.map(value=>[
      `${value.candidateId}:${value.resumeVersionId}`,value.scoreBindingId,
    ]));
    setPhase('TRANSFERRING');
    return talentApi.reclaimShared(candidates.map(value=>{
      const scoreBindingId=bindings.get(`${value.candidateId}:${value.resumeVersionId}`);
      if(!scoreBindingId)throw new Error('评分结果与所选简历不一致，未转入任何简历');
      return {...value,scoreBindingId};
    }));
  },onSuccess:async value=>{
    await Promise.all([
      qc.invalidateQueries({queryKey:['talent-search']}),
      qc.invalidateQueries({queryKey:['talent']}),
      qc.invalidateQueries({queryKey:['matching']}),
    ]);
    void message.success(`已评分并将 ${value.reclaimedCount} 份简历转入我的简历`);
    setJd(undefined);onCompleted();onClose();
  }});
  const close=()=>{if(mutation.isPending)return;setJd(undefined);mutation.reset();onClose();};
  return <Modal centered width={560} rootClassName="workspace-dialog-root" className="workspace-dialog" transitionName="workspace-dialog-motion" maskTransitionName="workspace-mask-motion" title="转入我的简历" open={open} okText={mutation.isPending?(phase==='SCORING'?'正在 AI 评分':'正在转入'):'评分并转入'} cancelText="取消" closable={!mutation.isPending} maskClosable={!mutation.isPending} keyboard={!mutation.isPending} okButtonProps={{disabled:!jd||!candidates.length}} cancelButtonProps={{disabled:mutation.isPending}} confirmLoading={mutation.isPending} onCancel={close} onOk={()=>mutation.mutate()} destroyOnHidden>
    <Space orientation="vertical" size={12} style={{width:'100%'}}>
      <Alert type="info" showIcon title={`已选择 ${candidates.length} 份共享简历`} description="历史 JD 和评分仅供查看；本次需重新选择 JD 并完成新的公司默认 AI 评分。全部评分成功才会转入我的简历；失败时简历仍留在共享简历库。"/>
      {mutation.error?<Alert occurrence={mutation.error} type="error" showIcon title={mutation.error instanceof Error?mutation.error.message:'评分或转入失败，简历仍保留在共享库'}/>:null}
      <JdSelector parsedOnly disabled={mutation.isPending} {...(jd?{value:jd.id}:{})} onChange={setJd} placeholder="选择转入对应的 JD"/>
    </Space>
  </Modal>;
}

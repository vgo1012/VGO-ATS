import { createIdempotencyKey } from '../api/idempotency';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { talentApi, type ScreeningReturnItem } from '../talent/api';
import { ApiProblemError } from '../api/problem';

export interface ScreeningReturnTarget extends ScreeningReturnItem { candidateId:string }

export function ScreeningReturnDialog({targets,onClose,onCompleted}:{
  targets:ScreeningReturnTarget[];onClose:()=>void;onCompleted:(candidateIds:string[])=>void;
}) {
  const client=useQueryClient();
  const {message}=App.useApp();
  const [operationKey]=useState(()=>createIdempotencyKey());
  const action=useMutation({
    mutationFn:()=>talentApi.returnScreening(targets.map(({admissionId,expectedVersion})=>({admissionId,expectedVersion})),operationKey),
    onSuccess:async()=>{
      await Promise.all([
        client.invalidateQueries({queryKey:['matching']}),
        client.invalidateQueries({queryKey:['talent']}),
        client.invalidateQueries({queryKey:['talent-search']}),
      ]);
      onCompleted(targets.map(item=>item.candidateId));
      void message.success('退回操作已完成，列表已更新');
      onClose();
    },
  });
  const stateConflict=action.error instanceof ApiProblemError&&action.error.problem.status===409&&
    ['SCREENING_ADMISSION_VERSION_CONFLICT','SCREENING_RECRUITMENT_ALREADY_ENTERED'].includes(action.error.problem.code);
  const refreshState=async()=>{
    await client.invalidateQueries({queryKey:['matching','screening-candidates']},{throwOnError:true});
    onClose();
  };
  const close=()=>{if(!action.isPending)onClose();};
  return <WorkspaceDialog title="退回我的简历" open onCancel={close} onOk={()=>stateConflict?refreshState():action.mutateAsync().catch(()=>undefined)}
    okText={stateConflict?'刷新筛选状态':action.isError?'重试退回':'确认退回'} cancelText="取消" confirmLoading={action.isPending}
    cancelButtonProps={{disabled:action.isPending}} closable={!action.isPending} maskClosable={!action.isPending} keyboard={!action.isPending}>
    <p>将选中的 {targets.length} 份简历退回“我的简历”，之后可重新智能筛选或直接推荐。</p>
    <p>已有评分和操作记录会保留。已进入招聘流程的简历不能退回。</p>
    {stateConflict?<Alert type="warning" showIcon title="筛选状态已变化" description="请刷新筛选状态后重新核对可退回的简历。此次刷新不会执行退回，也不会清除当前选择。"/>:null}
    {action.error?<Alert type="error" showIcon title="退回未完成"
      description={action.error instanceof ApiProblemError?action.error.problem.detail:action.error instanceof Error?action.error.message:'请稍后重试；如状态已变化，请关闭弹窗并刷新列表。'}/>:null}
  </WorkspaceDialog>;
}

import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { ApiProblemError } from '../api/problem';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Card, Empty, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { useSession } from '../auth/session';
import { CandidateName, JobName } from '../display/BusinessReferences';
import { formatDateTime, displayEnum } from '../display/labels';

import { actionError } from './candidateActions';
import { FollowUpPlanDialog } from './FollowUpPlanDialog';
import { refreshFollowUpPlans, type FollowUpPlan } from './followUpPlansApi';
import { useFollowUpPlans } from './useFollowUpPlans';

const planStatusLabel={SCHEDULED:'待沟通',COMPLETED:'已沟通',CANCELLED:'已取消'};
export function FollowUpPlans({applicationId,canManage,filters={},onCompleted,onOpenApplication,onOpenPlan,embedded=false}:{embedded?:boolean;applicationId?:string;canManage?:boolean;filters?:Record<string,string|number|undefined>;onCompleted?:(applicationId:string)=>Promise<unknown>|void;onOpenApplication?:(id:string)=>void;onOpenPlan?:(plan:FollowUpPlan)=>void}) {
  const plans=useFollowUpPlans(applicationId,filters);
  const [editing,setEditing]=useState<{plan:FollowUpPlan;action:'schedule'|'cancel'|'complete'}>();
  const {actor}=useSession();const qc=useQueryClient();
  const denied=plans.error instanceof ApiProblemError&&[401,403].includes(plans.error.problem.status);
  const items=denied?[]:(plans.data?.pages.flatMap(page=>page.items)??[]).filter(plan=>plan.status==='SCHEDULED');
  if(embedded&&!plans.isPending&&!plans.error&&items.length===0&&!plans.hasNextPage)return null;
  const content=<>

    {plans.error?<Alert occurrence={plans.error} type="error" showIcon title={actionError(plans.error)} action={<Button onClick={()=>void plans.refetch()}>重试沟通预约</Button>}/>:null}
    <Table<FollowUpPlan> onHeaderRow={() => scrollableTableHeader('沟通预约表，可横向滚动')} size="small" rowKey="id" loading={plans.isPending} pagination={false} dataSource={items} scroll={{x:760}} locale={{emptyText:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无沟通预约；与候选人约定时间后点击“预约沟通”添加"/>}} columns={[
      {title:'约定时间',width:175,render:(_,item)=><span>{formatDateTime(item.startsAt)}<br/><small>至 {formatDateTime(item.endsAt)}</small></span>},
      ...(!applicationId?[{title:'候选人 / 岗位',render:(_:unknown,item:FollowUpPlan)=><span><CandidateName id={item.candidateId}/><br/><small><JobName jdId={item.jdId}/></small></span>}]:[]),
      {title:'沟通事项',render:(_,item)=><span>{item.note}{item.resultReason?<><br/><b>{item.status==='COMPLETED'?'沟通情况':'取消原因'}：</b>{item.resultReason}</>:null}<br/><small>{displayEnum(item.channel)} · {item.recruitmentType==='EXTERNAL'?`外部招聘 · ${item.clientName??'甲方信息待核对'}`:'内部招聘'}</small></span>},
      {title:'状态',width:95,render:(_,item)=><Tag>{planStatusLabel[item.status]}</Tag>},
      {title:'操作', className: 'table-action-cell',width:235,render:(_,item)=><Space wrap>{item.status==='SCHEDULED'&&(applicationId?canManage:actor?.superAdmin||item.ownerUserId===actor?.userId)?<><Button size="small" disabled={Date.parse(item.startsAt)>Date.now()} onClick={()=>setEditing({plan:item,action:'complete'})}>已沟通</Button><Button size="small" onClick={()=>setEditing({plan:item,action:'schedule'})}>改期</Button><Button size="small" onClick={()=>setEditing({plan:item,action:'cancel'})}>取消预约</Button></>:null}{!applicationId&&onOpenPlan?<Button size="small" onClick={()=>onOpenPlan(item)}>查看预约</Button>:null}{!applicationId&&onOpenApplication?<Button size="small" onClick={()=>onOpenApplication(item.applicationId)}>继续跟进</Button>:null}</Space>},
    ]}/>
    {plans.hasNextPage?<div className="data-page-controls"><Button loading={plans.isFetchingNextPage} onClick={()=>void plans.fetchNextPage()}>加载更多预约</Button></div>:null}
    {editing?<FollowUpPlanDialog {...editing} onClose={()=>setEditing(undefined)} onSaved={async result=>{await refreshFollowUpPlans(qc,editing.plan.applicationId);setEditing(undefined);if(editing.action==='complete'&&result==='CONTINUE')await onCompleted?.(editing.plan.applicationId);}}/>:null}
  </>;
  return embedded?<section aria-label="待沟通预约">{content}</section>:<Card size="small" title="沟通预约">{content}</Card>;
}

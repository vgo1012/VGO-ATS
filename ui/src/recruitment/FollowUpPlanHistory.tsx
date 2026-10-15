import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Button, Empty, Timeline, Typography } from 'antd';
import { ApiProblemError } from '../api/problem';
import { LongTextPreview } from '../components/LongTextPreview';
import { LoadingStatus } from '../components/LoadingStatus';
import { UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { actionError } from './candidateActions';
import { recruitmentApi } from './api';
import { followUpPlansApi } from './followUpPlansApi';

const actionLabels:Record<string,string>={CREATED:'已预约沟通',RESCHEDULED:'沟通已改期',COMPLETED:'已沟通',CANCELLED:'预约已取消',APPLICATION_TERMINATED:'流程已结束，预约已取消'};

export function FollowUpPlanHistory({applicationId}:{applicationId:string}) {
  const history=useInfiniteQuery({
    queryKey:['follow-up-plan-history',applicationId],
    queryFn:({pageParam})=>followUpPlansApi.history(applicationId,pageParam),
    initialPageParam:undefined as string|undefined,
    getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,
  });
  const communications=useQuery({
    queryKey:['application-communications',applicationId],
    queryFn:()=>recruitmentApi.communications(applicationId),
    retry:false,
  });
  const denied=history.error instanceof ApiProblemError&&[401,403].includes(history.error.problem.status);
  const communicationDenied=communications.error instanceof ApiProblemError&&[401,403].includes(communications.error.problem.status);
  const appointments=denied?[]:history.data?.pages.flatMap(page=>page.items)??[];
  const actual=communicationDenied?[]:communications.data??[];
  const pending=history.isPending||communications.isPending;
  const failed=Boolean(history.error||communications.error);
  const items=[
    ...appointments.map(event=>({id:`appointment-${event.id}`,occurredAt:event.occurredAt,content:<div><b>{actionLabels[event.action]??'预约已更新'}</b> · {formatDateTime(event.occurredAt)} · <UserName id={event.actorUserId}/>
      <div>{displayEnum(event.after.channel)} · {formatDateTime(event.after.startsAt)} 至 {formatDateTime(event.after.endsAt)}</div>
      {event.action==='RESCHEDULED'&&event.before?<small>原约定：{formatDateTime(event.before.startsAt)} 至 {formatDateTime(event.before.endsAt)}</small>:null}
      {event.after.note?<div><LongTextPreview text={event.after.note} label="预约备注"/></div>:null}
      {event.reason?<div>{event.action==='COMPLETED'?'沟通情况：':event.action==='CANCELLED'?'取消原因：':'备注：'}<LongTextPreview text={event.reason} label="沟通记录"/></div>:null}
    </div>})),
    ...actual.map(event=>({id:`communication-${event.id}`,occurredAt:event.communicatedAt,content:<div><b>实际沟通</b> · {formatDateTime(event.communicatedAt)} · <UserName id={event.recordedByUserId}/>
      <div>{displayEnum(event.channel)} · {displayEnum(event.result)}</div>
      {event.summary?<div><LongTextPreview text={event.summary} label="沟通情况"/></div>:null}
      {event.nextPlan?<div>下一步：<LongTextPreview text={event.nextPlan} label="下一步计划"/></div>:null}
    </div>})),
  ].sort((left,right)=>right.occurredAt.localeCompare(left.occurredAt));
  return <section aria-label="沟通记录">
    <Typography.Title level={2} style={{fontSize:'var(--type-section)',lineHeight:1.5}}>沟通记录</Typography.Title>
    <Typography.Paragraph type="secondary">预约、改期、取消和实际沟通结果统一按时间展示。</Typography.Paragraph>
    {history.error?<Alert occurrence={history.error} type="error" showIcon title={actionError(history.error)} action={<Button loading={history.isFetching} disabled={history.isFetching} onClick={()=>void (history.isFetchNextPageError&&!denied?history.fetchNextPage({cancelRefetch:false}):history.refetch())}>重试预约历史</Button>}/>:null}
    {communications.error?<Alert occurrence={communications.error} type="error" showIcon title={actionError(communications.error)} action={<Button loading={communications.isFetching} disabled={communications.isFetching} onClick={()=>void communications.refetch()}>重试实际沟通</Button>}/>:null}
    {pending?<LoadingStatus label="加载沟通记录"/>:null}
    {failed?<Typography.Paragraph role="status">{items.length?'部分沟通记录未能读取，已显示可用记录。':'沟通记录暂不可用，请重试。'}</Typography.Paragraph>:null}
    {items.length?<Timeline items={items.map(event=>({key:event.id,content:event.content}))}/>:!pending&&!failed?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={history.hasNextPage?'当前已加载页暂无沟通记录':'尚无沟通记录'}/>:null}
    {history.hasNextPage&&!denied&&!history.isFetchNextPageError?<Button loading={history.isFetchingNextPage} disabled={history.isFetching} onClick={()=>void history.fetchNextPage({cancelRefetch:false})}>加载更多沟通记录</Button>:null}
  </section>;
}

import { useQuery } from '@tanstack/react-query';
import { Button, Card, Collapse, Descriptions, Empty, Space, Table, Tag, Timeline } from 'antd';
import { useState } from 'react';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { LongTextPreview } from '../components/LongTextPreview';
import { JobName, UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { LazyApplicationDetailsDialog } from '../recruitment/LazyApplicationDetailsDialog';
import { interviewStepLabel } from '../recruitment/workflowLabels';
import { talentApi, type CandidateRecruitmentHistory } from './api';

type ApplicationHistory=CandidateRecruitmentHistory['items'][number];
const ownershipActionLabels:Record<string,string>={ASSIGN:'归属更新',TRANSFER:'转交',HANDOVER:'交接'};

export function CandidateRecruitmentHistoryPanel({candidateId,active,sharedLibrary=false}:{candidateId:string;active:boolean;sharedLibrary?:boolean}){
  const [applicationId,setApplicationId]=useState<string>();
  const history=useQuery({queryKey:['talent','candidate-recruitment-history',candidateId],queryFn:()=>talentApi.recruitmentHistory(candidateId),enabled:active,retry:false});
  return <Card size="small" title="历史招聘记录" role="region" aria-label="历史招聘记录">
    {history.error?<Alert occurrence={history.error} type="warning" showIcon title="历史招聘记录读取失败" action={<Button onClick={()=>void history.refetch()}>重试历史记录</Button>}/>:null}
    {history.isPending?<p role="status">正在读取历史招聘记录…</p>:history.data?.items.length?<Collapse items={history.data.items.map(item=>({key:item.applicationId,label:<Space wrap><JobName jdId={item.jdId} jdVersionId={sharedLibrary?item.jdVersionId:undefined}/><Tag>{displayEnum(item.currentStage)}</Tag>{item.interviews.length?<Tag color="blue">已进入面试</Tag>:<Tag>未进入面试</Tag>}</Space>,children:<HistoryDetails value={item} onOpen={()=>setApplicationId(item.applicationId)}/>}))}/>:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无本系统招聘流程记录"/>}
    <LazyApplicationDetailsDialog sharedLibrary={sharedLibrary} applicationId={applicationId} onClose={()=>setApplicationId(undefined)}/>
  </Card>;
}

function HistoryDetails({value,onOpen}:{value:ApplicationHistory;onOpen:()=>void}){
  // 创建流程时的初始归属已由上方归属人展示，不作为待处理分配或转交历史重复呈现。
  const ownershipChanges=value.ownershipEvents.filter(event=>event.actionType!=='ASSIGN'||Boolean(event.fromUserId));
  return <Space className="candidate-recruitment-history" orientation="vertical" size={12} style={{width:'100%'}}>
    <Descriptions size="small" column={{xs:1,md:2}} items={[
      {key:'created',label:'进入系统流程时间',children:formatDateTime(value.createTime)},
      {key:'terminal',label:'流程结束时间',children:value.terminalAt?formatDateTime(value.terminalAt):'仍在进行'},
      {key:'originalOwner',label:'原归属人',children:<UserName id={value.originalOwnerUserId}/>},
      {key:'currentOwner',label:'当前流程归属人',children:<UserName id={value.currentOwnerUserId}/>},
      {key:'interview',label:'是否进入面试',children:value.interviews.length?'是':'否'},
      {key:'outcome',label:'流程结果',children:displayEnum(value.outcome,'未形成结论')},
    ]}/>
    {value.stageEvents.length?<Timeline items={value.stageEvents.map((event,index)=>({key:`stage-${index}`,content:<span>{formatDateTime(event.occurredAt)} · {displayEnum(event.fromStage,'开始')} → {displayEnum(event.toStage)} · 操作人 <UserName id={event.operatedByUserId}/>{event.reason?<> · <LongTextPreview text={event.reason} label="阶段变更原因"/></>:null}</span>}))}/>:null}
    {ownershipChanges.length?<Timeline items={ownershipChanges.map((event,index)=>({key:`owner-${index}`,color:'gray',content:<span>{formatDateTime(event.occurredAt)} · {ownershipActionLabels[event.actionType]??'归属变更'} · {event.fromUserId?<><UserName id={event.fromUserId}/> → </>:null}<UserName id={event.toUserId}/> · 操作人 <UserName id={event.operatedByUserId}/></span>}))}/>:null}
    {value.interviews.length?<Table rowKey="interviewId" scroll={{x:800}} size="small" pagination={false} dataSource={value.interviews} columns={[
      {title:'面试',render:(_,interview)=>interviewStepLabel({...interview,stepCode:`INTERVIEW_ROUND_${interview.roundNo}`})},
      {title:'面试时间',dataIndex:'scheduledStartAt',render:(time:string)=>formatDateTime(time)},
      {title:'面试状态',dataIndex:'status',render:(status:string)=>displayEnum(status)},
      {title:'面评时间 / 面评人',render:(_,interview)=>interview.feedbacks.length?<Space orientation="vertical" size={0}>{interview.feedbacks.map(feedback=><span key={feedback.feedbackId}>{feedback.feedbackOccurredAt?formatDateTime(feedback.feedbackOccurredAt):'时间未记录'} · <UserName id={feedback.authorUserId}/> · {displayEnum(feedback.verdict,'未记录结论')}</span>)}</Space>:'未提交正式面评'},
      {title:'面评内容',render:(_,interview)=><Space orientation="vertical" size={4}>{interview.feedbacks.map(feedback=><LongTextPreview key={feedback.feedbackId} text={feedback.shortComment?.trim()||'未填写面评文字'} label="面评" limit={16}/>)}</Space>},
    ]}/>:null}
    <Button onClick={onOpen}>查看该流程详情</Button>
  </Space>;
}

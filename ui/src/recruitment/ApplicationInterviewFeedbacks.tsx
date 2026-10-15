import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import type { HTMLAttributes, ReactNode } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { Button, Card, Empty, Space, Table, Tag, Typography } from 'antd';
import { LongTextPreview } from '../components/LongTextPreview';
import { ApiProblemError } from '../api/problem';
import { UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { interviewApi, type FeedbackView } from '../interview/api';
import { interviewStepLabel } from './workflowLabels';

const FeedbackTable=(props:HTMLAttributes<HTMLTableElement>)=><table {...props} tabIndex={0} aria-label="流程记录"/>;

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'面评读取失败，请重试。';

export function ApplicationInterviewFeedbacks({applicationId,recruitmentType,children,sharedLibrary=false}:{sharedLibrary?:boolean;applicationId:string;recruitmentType:'INTERNAL'|'EXTERNAL';children?:ReactNode}) {
  const interviews=useQuery({queryKey:['application-interviews',applicationId],queryFn:()=>interviewApi.allForApplication(applicationId),retry:false});
  const denied=interviews.error instanceof ApiProblemError&&[401,403].includes(interviews.error.problem.status);
  const ordered=denied?[]:[...(interviews.data??[])].sort((left,right)=>right.scheduledStartAt.localeCompare(left.scheduledStartAt));
  const batches=Array.from({length:Math.ceil(ordered.length/100)},(_,index)=>ordered.slice(index*100,index*100+100).map(value=>value.id));
  const feedback=useQueries({queries:batches.map(ids=>({queryKey:['application-interview-feedback',applicationId,'batch',ids],queryFn:()=>interviewApi.feedbackBatchAll(ids),retry:false}))});
  const feedbackByInterview=new Map(feedback.flatMap(query=>{
    const grouped=new Map<string,FeedbackView[]>();
    for(const item of query.data?.items??[])if(sharedLibrary||item.feedback.status==='SUBMITTED'){
      const values=grouped.get(item.feedback.interviewId)??[];values.push(item);grouped.set(item.feedback.interviewId,values);
    }
    return [...grouped];
  }));
  return <Card className="application-process" title="流程记录">
    {interviews.error?<Alert occurrence={interviews.error} type="error" showIcon title={errorText(interviews.error)} action={<Button onClick={()=>void interviews.refetch()}>重试流程记录</Button>}/>:null}
    {feedback.map((query,index)=>query.error?<Alert key={index} occurrence={query.error} type="error" showIcon title={errorText(query.error)} action={<Button disabled={query.isFetching} onClick={()=>void query.refetch()}>重试本批面评</Button>}/>:null)}
    {interviews.isPending?<Card loading/>:ordered.length?<Table size="small" tableLayout="fixed" components={{table:FeedbackTable}} rowKey="id" pagination={false} dataSource={ordered} scroll={{x:720}} columns={[
      {title:'步骤',align:'center',width:150,render:(_,interview)=>interviewStepLabel(interview,recruitmentType)},
      {title:'面试时间',align:'center',width:150,render:(_,interview)=>formatDateTime(interview.scheduledStartAt)},
      {title:'状态',align:'center',width:80,render:(_,interview)=><Tag>{{SCHEDULED:'待进行',COMPLETED:'已完成',CANCELLED:'已取消'}[interview.status]??displayEnum(interview.status)}</Tag>},
      {title:'本轮结果',align:'center',width:90,render:(_,interview,index)=>{
        const query=feedback[Math.floor(index/100)]; const items=feedbackByInterview.get(interview.id)??[];
        const omitted=query?.isSuccess&&!query.data.visibleInterviewIds.includes(interview.id);
        if(query?.error)return <Typography.Text type="secondary">暂不可用</Typography.Text>;
        if(query?.isPending)return <span role="status">结果读取中…</span>;
        if(omitted)return <Typography.Text type="secondary">需授权</Typography.Text>;
        if(!items.length)return <Typography.Text type="secondary">{interview.status==='SCHEDULED'?'待提交':interview.status==='COMPLETED'?'未提交':'—'}</Typography.Text>;
        const verdicts=[...new Set(items.filter(item=>item.feedback.status==='SUBMITTED').map(item=>item.currentVersion.verdict))];
        return <Space size={4} wrap>{verdicts.map(verdict=><Tag key={verdict} color={verdict==='PASS'?'green':'red'}>{verdict==='PASS'?'通过':'不通过'}</Tag>)}{sharedLibrary&&items.some(item=>item.feedback.status==='DRAFT')?<Tag>含未提交草稿</Tag>:null}</Space>;
      }},
      {title:'面评',align:'center',render:(_,interview,index)=>{
        const query=feedback[Math.floor(index/100)]; const items=(feedbackByInterview.get(interview.id)??[]).filter(item=>sharedLibrary||item.currentVersion.shortComment?.trim());
        const omitted=query?.isSuccess&&!query.data.visibleInterviewIds.includes(interview.id);
        return <div style={{overflowWrap:'anywhere'}}>
          {query?.error?<Typography.Text type="secondary">本批面评暂不可用，请重试</Typography.Text>:query?.isPending?<span role="status">面评读取中…</span>:omitted?<Space orientation="vertical" align="center"><Typography.Text>本轮面评需授权或已不可用</Typography.Text><Button size="small" disabled={query.isFetching} onClick={()=>void query.refetch()}>重试本轮面评</Button></Space>:items.length?<Space orientation="vertical" align="center">{items.map(item=><div key={item.feedback.id}>{sharedLibrary?<div><Tag>{item.feedback.status==='DRAFT'?'草稿':'正式面评'}</Tag><UserName id={item.feedback.authorUserId}/> · {formatDateTime(item.currentVersion.feedbackOccurredAt)}{item.currentVersion.externalFeedbackSource?<> · 来源：<LongTextPreview text={item.currentVersion.externalFeedbackSource} label="反馈来源"/></>:null}</div>:null}<LongTextPreview text={item.currentVersion.shortComment?.trim()||'未填写面评文字'} label="面评" limit={16}/></div>)}</Space>:<span>本轮暂无面评</span>}
        </div>;
      }},
    ]}/>:!interviews.error?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前流程尚无面试与面评"/>:null}
    {children}
  </Card>;
}

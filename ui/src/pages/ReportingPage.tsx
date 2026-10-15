import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Empty, Row, Segmented, Space, Skeleton, Statistic, Table, Typography } from 'antd';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { DateRangeFilter } from '../components/DateRangeFilter';
import { businessDay, dateRangeParams, type DateRange } from '../components/dateRange';
import { JdSelector } from '../demand/JdSelector';
import { reportingApi, type MetricDefinition, type MetricPoint } from '../reporting/api';
import { ApiProblemError } from '../api/problem';

const labels:Record<string,string>={RESUME_INGESTED:'简历入库',RESUME_PARSED:'解析完成',HUMAN_SCREENED:'人工筛选',APPLICATION_CREATED:'招聘活动',SUBMISSION_SENT:'实际推送',INTERVIEW_PASSED:'面试通过',ONBOARDED:'到岗'};
type ReportType='funnel'|'workload'|'ai-effect';
type MetricRow={definition:MetricDefinition;point:MetricPoint|undefined};

export function ReportingPage(){
  const [type,setType]=useState<ReportType>('funnel'); const [jdId,setJdId]=useState<string>(); const [range,setRange]=useState<DateRange>([businessDay().subtract(29,'day'),businessDay()]);
  const filters=useMemo(()=>({...dateRangeParams(range),...(jdId?{jdId}:{})}),[range,jdId]);
  const report=useQuery({queryKey:['reporting','report',type,filters],queryFn:()=>reportingApi.report(type,filters)});
  const points=new Map((report.data?.points??[]).map(p=>[p.eventType,p]));
  const ordered:MetricRow[]=report.data?.definitions.map(d=>({definition:d,point:points.get(d.eventType)}))??[];
  const coverage=report.data?.parseCoverage;
  const problemCode=report.error instanceof ApiProblemError ? report.error.problem.code : undefined;
  const preparing=problemCode==='METRIC_PROJECTION_INITIALIZING';
  const disabled=problemCode==='METRIC_PROJECTION_DISABLED';
  return <div className="workspace-page">
    <section className="page-hero"><Typography.Title level={1}>招聘报表</Typography.Title><Typography.Paragraph>选择日期和岗位，查看当前账号可见范围内的招聘进展。</Typography.Paragraph></section>
    <Card className="report-filter-card"><Space wrap><Segmented value={type} onChange={v=>setType(v as ReportType)} options={[{value:'funnel',label:'招聘漏斗'},{value:'workload',label:'招聘工作量'},{value:'ai-effect',label:'AI 效果'}]}/><DateRangeFilter value={range} onChange={setRange}/><div className="report-jd-filter"><JdSelector {...(jdId?{value:jdId}:{})} onChange={jd=>setJdId(jd?.id)} placeholder="搜索岗位（包含历史版本）"/></div></Space></Card>
    {report.error?<Alert occurrence={report.error} type={preparing?'info':disabled?'warning':'error'} showIcon title={preparing?'统计准备中':disabled?'统计暂未启用':report.data?'统计更新失败，保留上次结果':'报表暂时不可用'} description={preparing?'统计尚未完成首次更新，请稍后重试。':disabled?'统计服务当前关闭，请联系管理员确认。':undefined} action={<Button loading={report.isFetching} onClick={()=>void report.refetch()}>重试</Button>}/>:null}
    {report.data?.projectionStatus==='LAGGING'?<Alert type="warning" showIcon title="统计更新延迟" description="以下为上次已完成的统计，最新招聘进展可能尚未计入。"/>:null}
    {report.isFetching&&report.data?<Typography.Text role="status" type="secondary">正在更新统计，已加载结果仍可查看。</Typography.Text>:null}
    {report.isPending ? <Card><Skeleton active paragraph={{rows:3}} /></Card> : report.data ? <>
    <Row gutter={[16,16]}>{ordered.map(({definition,point})=><Col xs={12} md={8} xl={6} key={definition.code}><Card className="metric-card"><Statistic title={definition.name} value={point?.eventCount??0}/></Card></Col>)}</Row>
    {type==='ai-effect'?<Card title="解析覆盖率" className="metric-explain-card">{coverage?<><Statistic value={coverage.resumeVersionCount?Math.round(coverage.parsedResumeVersionCount/coverage.resumeVersionCount*100):0} suffix="%"/><Typography.Paragraph>本期入库 {coverage.resumeVersionCount} 个简历版本，其中 {coverage.parsedResumeVersionCount} 个已有有效解析。</Typography.Paragraph><Typography.Text type="secondary">按简历版本去重，重复解析只计一次；反映本期入库简历的当前解析情况，不进入专业适配分。</Typography.Text></>:<Alert type="info" showIcon title="解析覆盖率暂不可用" description="当前报表未返回去重版本统计，请更新服务后重试。"/>}</Card>:null}
    <Card title="指标说明" extra={report.data?<Typography.Text type="secondary">更新于 {dayjs(report.data.dataUpdatedAt).format('YYYY-MM-DD HH:mm')}</Typography.Text>:null}><Table<MetricRow> scroll={{x:820}} onHeaderRow={() => scrollableTableHeader("招聘指标表头，可横向滚动")} rowKey={r=>r.definition.code} loading={report.isLoading} pagination={false} dataSource={ordered} locale={{emptyText:<Empty description="当前范围没有真实指标事件"/>}} columns={[{title:'指标',width:100,render:(_:unknown,r)=>labels[r.definition.eventType]??r.definition.name},{title:'数量',width:70,render:(_:unknown,r)=>r.point?.eventCount??0},{title:'口径版本',width:90,render:(_:unknown,r)=>`V${r.definition.versionNo}`},{title:'统计说明',width:370,render:(_:unknown,r)=>r.definition.definitionText},{title:'最近事件',width:190,render:(_:unknown,r)=>r.point?.lastOccurredAt?dayjs(r.point.lastOccurredAt).format('YYYY-MM-DD HH:mm'):'未发生'}]} /></Card>
    </> : null}
  </div>;
}

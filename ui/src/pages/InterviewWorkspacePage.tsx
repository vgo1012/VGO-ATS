import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CalendarOutlined,PlusOutlined } from '@ant-design/icons';
import { useInfiniteQuery,useQueries,useQuery,useQueryClient } from '@tanstack/react-query';
import { Button,Card,Col,DatePicker,Empty,Select,Table,Tag,Typography } from 'antd';
import dayjs from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';
import { useEffect,useState } from 'react';
import './interview-workspace.css';
import { calendarApi,calendarKeys } from '../work-calendar/api';
import { AppointmentDetailsDialog } from '../recruitment/AppointmentDetailsDialog';
import { ApiProblemError } from '../api/problem';
import { canManageInterviewFeedback,canReadRecruitmentLedger,canScheduleInterviews } from '../auth/permissions';
import { useSession } from '../auth/session';
import { WorkspaceDialogGroup } from '../components/WorkspaceDialog';
import { CandidateName,JobName } from '../display/BusinessReferences';
import { interviewApi,type Interview,type InterviewTask } from '../interview/api';
import { CreateInterviewModal,InterviewDrawer,MeetingPlace } from '../interview/InterviewDialogs';
import { InvitedJobFilters } from '../interview/InvitedJobFilters';
import { ClientFilter } from '../recruitment/ClientFilter';
import { interviewStepLabel } from '../recruitment/workflowLabels';
import { interviewTypeLabels, interviewStatusLabels } from '../interview/presentation';
import { followUpPlansApi,type FollowUpPlan } from '../recruitment/followUpPlansApi';

import { businessDay,businessWeek,dateRangeParams,type DateRange } from '../components/dateRange';
import { DateRangeFilter } from '../components/DateRangeFilter';
import { demandApi,type JdSummary } from '../demand/api';
import { JdSelector } from '../demand/JdSelector';
import { UserName } from '../display/BusinessReferences';
import { displayEnum } from '../display/labels';
import { jobLabel } from '../demand/jobLabel';

dayjs.extend(utc);
dayjs.extend(timezone);
const businessTime=(value?:string)=>dayjs(value).tz('Asia/Shanghai');

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';
const accessDenied=(error:unknown)=>error instanceof ApiProblemError&&[401,403].includes(error.problem.status);

export function InterviewWorkspacePage(){
  const {actor}=useSession();

  const canFeedback=canManageInterviewFeedback(actor);
  const canSchedule=canScheduleInterviews(actor);
  const canReadApplications=canReadRecruitmentLedger(actor);
  const [weekDate,setWeekDate]=useState(businessDay);
  const [range,setRange]=useState<DateRange>(()=>[businessDay().subtract(6,'day'),businessDay()]);
  const weekStart=businessWeek(weekDate)[0];
  const [view,setView]=useState<'week'|'list'>('week');
  const [filters,setFilters]=useState<Record<string,string|number|undefined>>({status:'SCHEDULED'});
  const [selected,setSelected]=useState<string>();
  const [createOpen,setCreateOpen]=useState(false);
  const [selectedPlan,setSelectedPlan]=useState<FollowUpPlan>();
  const qc=useQueryClient();
  const dateFilters=dateRangeParams(view==='week'?[weekStart,weekStart.add(6,'day')]:range);
  const interviews=useInfiniteQuery({queryKey:['interviews',filters,dateFilters],queryFn:({pageParam})=>interviewApi.list({...filters,...dateFilters,cursor:pageParam}),initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore&&page.nextCursor?page.nextCursor:undefined});
  const planFilters={status:'SCHEDULED',...dateFilters,jdId:typeof filters.jdId==='string'?filters.jdId:undefined,jdVersionId:typeof filters.jdVersionId==='string'?filters.jdVersionId:undefined,clientId:typeof filters.clientId==='string'?filters.clientId:undefined};
  const plans=useInfiniteQuery({queryKey:['follow-up-plans',undefined,planFilters],queryFn:({pageParam})=>followUpPlansApi.list({...planFilters,cursor:pageParam}),initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,enabled:canReadApplications&&view==='week'});
  const planItems=accessDenied(plans.error)?[]:(plans.data?.pages.flatMap(page=>page.items)??[]).filter(plan=>plan.status==='SCHEDULED');
  const tasks=useQuery({queryKey:['interview-tasks'],queryFn:interviewApi.myTasks,retry:false,enabled:canFeedback});
  const detail=useQuery({queryKey:['interview',selected],queryFn:()=>interviewApi.get(selected!),enabled:Boolean(selected)});
  const refresh=async()=>Promise.all([qc.invalidateQueries({queryKey:['interviews']}),qc.invalidateQueries({queryKey:['interview-tasks']}),qc.invalidateQueries({queryKey:['interview']})]);
  const items=accessDenied(interviews.error)?[]:interviews.data?.pages.flatMap(page=>page.items)??[];
  const weekItems=items.filter(item=>Date.parse(item.scheduledStartAt)<weekStart.add(7,'day').valueOf()&&Date.parse(item.scheduledEndAt)>weekStart.valueOf());

  return <div className="workspace-page interview-workspace-page">
    <section className="schedule-toolbar" aria-label="日程时间范围">
      <div className="schedule-range-controls"><b>时间范围</b><Button onClick={()=>{setView('week');setWeekDate(value=>value.subtract(7,'day'));}}>上一周</Button><Button className={view==='week'&&businessDay().valueOf()>=weekStart.valueOf()&&businessDay().valueOf()<weekStart.add(7,'day').valueOf()?'schedule-current-week':''} onClick={()=>{setView('week');setWeekDate(businessDay());}}>本周</Button><Button onClick={()=>{setView('week');setWeekDate(value=>value.add(7,'day'));}}>下一周</Button><Button onClick={()=>setView('list')}>自定义</Button>{view==='week'?<span className="schedule-range-label"><CalendarOutlined/>{weekStart.format('M 月 D 日')} — {weekStart.add(6,'day').format('M 月 D 日')}</span>:<DateRangeFilter value={range} onChange={setRange} label="面试日期"/>}</div>
      <div className="schedule-toolbar-actions"><span>跳转日期</span><DatePicker aria-label="跳转日期" value={weekDate} allowClear={false} onChange={value=>{if(value){setWeekDate(value);setView('week');}}}/>{canSchedule?<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setCreateOpen(true)}>新建面试</Button>:null}</div>
    </section>
    <div className="schedule-layout">
      <section className="schedule-main">
        <InterviewFilters value={filters} onChange={setFilters}/>
        {interviews.error?<Alert occurrence={interviews.error} type="error" showIcon title={errorText(interviews.error)} action={<Button onClick={()=>void interviews.refetch()}>重试日程</Button>}/>:null}
        {view==='week'&&canReadApplications&&plans.error?<Alert occurrence={plans.error} type="error" showIcon title="沟通预约加载失败" description={errorText(plans.error)} action={!plans.isFetchNextPageError||accessDenied(plans.error)?<Button loading={plans.isFetching} onClick={()=>void plans.refetch()}>重试沟通日历</Button>:undefined}/>:null}
        {view==='week'?<WeekCalendar items={weekItems} plans={planItems} start={weekStart} loading={interviews.isPending||canReadApplications&&plans.isPending} incomplete={Boolean(interviews.hasNextPage||interviews.error||canReadApplications&&(plans.hasNextPage||plans.error))} onOpen={setSelected} onOpenPlan={setSelectedPlan}/>:<InterviewList items={items} loading={interviews.isPending} onOpen={setSelected}/>}
        {interviews.hasNextPage&&!accessDenied(interviews.error)?<div className="data-page-controls"><span>已显示 {items.length} 场，该日期范围还有更多面试</span><Button loading={interviews.isFetchingNextPage} onClick={()=>void interviews.fetchNextPage()}>加载更多面试</Button></div>:null}
        {view==='week'&&canReadApplications&&plans.hasNextPage&&!accessDenied(plans.error)?<div className="data-page-controls"><span aria-live="polite">已显示 {planItems.length} 条沟通预约，该周还有更多预约</span><Button loading={plans.isFetchingNextPage} disabled={plans.isFetching} onClick={()=>void plans.fetchNextPage({cancelRefetch:false})}>{plans.isFetchNextPageError?'重试加载沟通预约':'加载更多沟通预约'}</Button></div>:null}
      </section>
      <ScheduleRail items={view==='week'?weekItems:items} interviewsLoading={interviews.isPending} interviewsError={interviews.error} hasMore={Boolean(interviews.hasNextPage)} tasks={tasks.data??[]} canFeedback={canFeedback} tasksLoading={tasks.isPending} tasksError={tasks.error} onRetryTasks={()=>{void tasks.refetch();}} onOpen={setSelected}/>
    </div>
    <WorkspaceDialogGroup>
    {canReadApplications&&selectedPlan?<AppointmentDetailsDialog key={selectedPlan.id} plan={selectedPlan} onClose={()=>setSelectedPlan(undefined)}/>:null}
    <InterviewDrawer value={detail.data} loading={detail.isPending} error={detail.error} open={Boolean(selected)} onClose={()=>setSelected(undefined)} onChanged={refresh}/>
    {canSchedule?<CreateInterviewModal open={createOpen} onClose={()=>setCreateOpen(false)} onSaved={async value=>{await refresh();setCreateOpen(false);setSelected(value.interview.id);}}/>:null}
    </WorkspaceDialogGroup>
  </div>;
}

function ScheduleRail({items,interviewsLoading,interviewsError,hasMore,tasks,canFeedback,tasksLoading,tasksError,onRetryTasks,onOpen}:{items:Interview[];interviewsLoading:boolean;interviewsError:unknown;hasMore:boolean;tasks:InterviewTask[];canFeedback:boolean;tasksLoading:boolean;tasksError:unknown;onRetryTasks:()=>void;onOpen:(id:string)=>void}){
  const pendingTasks=tasks.filter(value=>!value.submitted);
  return <aside className="schedule-rail" aria-label="日程跟进摘要" aria-busy={canFeedback&&tasksLoading}>
    <section className="schedule-task-panel"><header><h2>任务栏</h2><span>当前筛选{hasMore?' · 已加载':''}</span></header><div className="schedule-task-grid">{[
      ['全部',interviewsLoading||interviewsError?'—':items.length],
      ...(canFeedback?[['待提交',tasksLoading||tasksError?'—':pendingTasks.length]]:[]),
      ...[1,2,3].map((round)=>[['一面','二面','三面'][round-1],interviewsLoading||interviewsError?'—':items.filter(item=>item.roundNo===round).length]),
      ...(canFeedback?[['已超时',tasksLoading||tasksError?'—':pendingTasks.filter(task=>task.overdue).length]]:[]),
      ['已完成',interviewsLoading||interviewsError?'—':items.filter(item=>item.status==='COMPLETED').length],
      ['已取消',interviewsLoading||interviewsError?'—':items.filter(item=>item.status==='CANCELLED').length],
    ].map(([label,count])=><div key={label} className={label==='全部'?'active':label==='已超时'?'overdue':''}><span>{label}</span><strong>{count}</strong></div>)}</div>{canFeedback?<p>待提交、已超时为当前账号负责处理的面评任务。</p>:null}</section>

    <section aria-label="待反馈">{canFeedback&&tasksError?<Alert occurrence={tasksError} type="error" showIcon title={errorText(tasksError)} action={<Button onClick={onRetryTasks}>重试面评跟进</Button>}/>:null}<header><h2>待反馈</h2><span>{!canFeedback?'未开放':tasksLoading||tasksError?'—':`${pendingTasks.length} 项`}</span></header>{!canFeedback?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前账号不能处理招聘面评"/>:pendingTasks.length?<div className="rail-task-list">{[...pendingTasks].sort((a,b)=>a.feedbackDueAt.localeCompare(b.feedbackDueAt)).slice(0,5).map(value=><button type="button" key={value.interviewId} onClick={()=>onOpen(value.interviewId)}><time>{businessTime(value.feedbackDueAt).format('MM-DD HH:mm')}</time><b>{value.stepCode.startsWith('INTERVIEW_ROUND_')?'面试':interviewStepLabel(value)}</b><small>{value.candidateName??'候选人暂不可用'} · {value.submitted?'已提交':value.overdue?'已超时':'待面评'}</small></button>)}</div>:tasksLoading?<Card loading/>:tasksError?<p>跟进数据暂不可用</p>:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待跟进面评"/>}</section>
  </aside>;
}

function InterviewFilters({value,onChange}:{value:Record<string,string|number|undefined>;onChange:(value:Record<string,string|number|undefined>)=>void}){
  const {actor}=useSession();
  return <section className="interview-filters" aria-label="筛选行程">
    <b>筛选行程</b>
    <div className="schedule-primary-filters">
      <div className="schedule-filter-field"><Select allowClear aria-label="面试类型" placeholder="全部面试类型" value={value.interviewType??null} options={[{value:'INTERNAL',label:'内部面试'},{value:'EXTERNAL',label:'外部面试'}]} onChange={interviewType=>onChange({...value,interviewType})}/></div>
      {canReadRecruitmentLedger(actor)?<div className="schedule-filter-field"><ClientFilter value={typeof value.clientId==='string'?value.clientId:undefined} onChange={clientId=>onChange({...value,clientId})}/></div>:null}
      <div className="schedule-filter-field"><div className="schedule-job-filter"><InterviewJobFilters value={value} onChange={onChange}/></div></div>
      <div className="schedule-filter-field"><Select allowClear value={value.status} aria-label="状态" placeholder="全部状态" onChange={status=>onChange({...value,status})} options={Object.entries(interviewStatusLabels).map(([v,label])=>({value:v,label:v==='SCHEDULED'?'进行中':label}))}/></div>
    </div>
    <Button type="primary" aria-label="重置" onClick={()=>onChange({status:'SCHEDULED'})}>重置</Button>
  </section>;
}

function CalendarStatusBadge({status,overdue=false}:{status:string;overdue?:boolean}){
  return <span className={`schedule-status-badge schedule-status-${status.toLowerCase()}${overdue?' schedule-overdue-badge':''}`} title={overdue?'约定结束时间已过，尚未记录完成':undefined}>{overdue?'已超时':interviewStatusLabels[status]}</span>;
}

function WeekCalendar({items,plans,start,loading,incomplete,onOpen,onOpenPlan}:{items:Interview[];plans:FollowUpPlan[];start:dayjs.Dayjs;loading:boolean;incomplete:boolean;onOpen:(id:string)=>void;onOpenPlan:(plan:FollowUpPlan)=>void}){
  const years=[...new Set([start.year(),start.add(6,'day').year()])];
  const calendars=useQueries({queries:years.map(year=>({queryKey:calendarKeys.published(year),queryFn:({signal}:{signal:AbortSignal})=>calendarApi.published(year,signal)}))});
  const calendarDays=new Map(calendars.flatMap(calendar=>calendar.isError?[]:calendar.data?.days??[]).map(day=>[day.date,day]));
  const [now,setNow]=useState(Date.now);
  useEffect(()=>{
    const refresh=()=>setNow(Date.now());
    const timer=window.setInterval(refresh,30_000);
    window.addEventListener('focus',refresh);
    return ()=>{window.clearInterval(timer);window.removeEventListener('focus',refresh);};
  },[]);
  if(loading)return <Card loading/>;
  return <Card className="interview-calendar">
    {calendars.map((calendar,index)=>calendar.isError?<Alert key={years[index]} type="error" title={`${years[index]} 年工作日历加载失败，班休暂未确认`} action={<Button onClick={()=>void calendar.refetch()}>重试工作日历</Button>}/>:calendar.isPending?<p key={years[index]} role="status">正在读取 {years[index]} 年工作日历</p>:!calendar.data?.revisionId?<p key={years[index]} role="status">{years[index]} 年工作日历尚未发布，班休暂未确认</p>:null)}
    <div className="schedule-calendar-legend" role="group" aria-label="日程颜色说明"><span className="scheduled">内部面试 · 待进行</span><span className="external">甲方面试 · 待进行</span><span className="completed">已完成</span><span className="cancelled">已取消</span><span className="overdue-legend" title="约定结束时间已过，仍为待进行；不代表面评逾期">已超时 · 待完成</span></div>
    <div className="schedule-calendar-scroll" key={start.toISOString()} tabIndex={0} role="region" aria-label="所选周面试日历" aria-description="上午固定三张、下午固定四张卡片，各日期的上午和下午可独立上下滚动；12:00 起归下午。"><div className="interview-week-grid">{Array.from({length:7},(_,index)=>{
      const day=start.add(index,'day');
      const arrangement=calendarDays.get(day.format('YYYY-MM-DD'));
      const restDay=arrangement?.dayType==='RESTDAY'||arrangement?.dayType==='HOLIDAY';
      const overlaps=(from:string,to:string)=>Date.parse(from)<day.add(1,'day').valueOf()&&Date.parse(to)>day.valueOf();
      const events=[...items.filter(item=>overlaps(item.scheduledStartAt,item.scheduledEndAt)).map(item=>({kind:'interview' as const,time:item.scheduledStartAt,id:item.id,item})),...plans.filter(item=>item.status==='SCHEDULED'&&overlaps(item.startsAt,item.endsAt)).map(item=>({kind:'communication' as const,time:item.startsAt,id:item.id,item}))].sort((a,b)=>Date.parse(a.time)-Date.parse(b.time)).map(event=>({...event,overdue:event.item.status==='SCHEDULED'&&Date.parse(event.kind==='interview'?event.item.scheduledEndAt:event.item.endsAt)<now}));
      const renderEvent=(event:typeof events[number])=>{
        const interview=event.kind==='interview';
        const endsAt=interview?event.item.scheduledEndAt:event.item.endsAt;
        return <button key={event.id} className={`interview-event ${interview?`${event.item.interviewType?.startsWith('EXTERNAL')?'external ':''}${event.item.status.toLowerCase()}`:'communication'}${event.overdue?' is-overdue':''}`} onClick={()=>interview?onOpen(event.id):onOpenPlan(event.item)}>
          <div className="schedule-event-heading"><b className="schedule-event-name">{interview?(event.item.candidateName??'候选人暂不可用'):<CandidateName id={event.item.candidateId}/>}</b><CalendarStatusBadge status={event.item.status} overdue={event.overdue}/></div>
          <span className="schedule-event-job">{interview?(event.item.jdTitle?jobLabel(event.item.jdTitle,event.item.jdCode):'岗位暂不可用'):event.item.jdId?<JobName jdId={event.item.jdId}/>:null}</span>
          <div className="schedule-event-timing"><span className="schedule-event-round">{interview?(interviewStepLabel(event.item).match(/第\s*\d+\s*[轮次]/)?.[0]??'面试'):'沟通预约'}</span><span aria-hidden="true">·</span><time>{Date.parse(event.time)<day.valueOf()?'前日续':businessTime(event.time).format('HH:mm')}–{Date.parse(endsAt)>day.add(1,'day').valueOf()?'跨日':businessTime(endsAt).format('HH:mm')}</time></div>
        </button>;
      };
      return <section key={day.toISOString()} className={`${day.day()===0||day.day()===6?'weekend ':''}${day.isSame(businessTime(),'day')?'today':''}`}><header><b>{['周一','周二','周三','周四','周五','周六','周日'][index]}<span className="schedule-day-markers">{arrangement?<em className={restDay?'schedule-weekend-badge':undefined} title={arrangement.description??undefined}>{{WORKDAY:'工作日',RESTDAY:'休息日',HOLIDAY:'节假日'}[arrangement.dayType]}</em>:<em>未确认</em>}{day.isSame(businessTime(),'day')?<em>今天</em>:null}</span></b><span>{day.format('M/D')}<small>{incomplete ? `已载 ${events.length} 项` : events.length ? `${events.length} 项` : '无安排'}</small></span></header><div className="schedule-day-periods">{(['morning','afternoon'] as const).map(period=>{
        const morning=period==='morning';
        const noon=day.hour(12).valueOf();
        // 跨午只按开始时间归类；跨日延续在当天上午显示，避免同一天重复计数。
        const periodEvents=events.filter(event=>(Math.max(Date.parse(event.time),day.valueOf())<noon)===morning);
        const label=morning?'上午':'下午';
        return <div key={period} className={`schedule-period ${period}`}>
          <div className="schedule-period-scroll" tabIndex={0} role="region" aria-label={`${day.format('M/D')} ${label}日程，可上下滚动`}>
            {periodEvents.map(renderEvent)}{!periodEvents.length?<span className="calendar-empty">{incomplete?`${label}已加载日程中暂无安排`:`${label}暂无安排`}</span>:null}
          </div>
        </div>;
      })}</div></section>;
    })}</div></div>
  </Card>;
}

function InterviewList({items,loading,onOpen}:{items:Interview[];loading:boolean;onOpen:(id:string)=>void}){
  return <Card title="面试台账" extra={<Typography.Text type="secondary">取消记录保留，不删除历史</Typography.Text>}><Table rowKey="id" loading={loading} dataSource={items} pagination={false} scroll={{x:1100}} locale={{emptyText:<Empty description="当前条件下暂无面试"/>}} columns={[
    {title:'时间',render:(_,v)=><span><b>{businessTime(v.scheduledStartAt).format('MM-DD HH:mm')}</b><br/><small>北京时间</small></span>},
    {title:'候选人 / 应聘岗位',render:(_,v)=><span><Button type="link" className="table-link" onClick={()=>onOpen(v.id)}>{v.candidateName??'候选人暂不可用'}</Button><br/><small>{v.jdTitle?jobLabel(v.jdTitle,v.jdCode):'岗位暂不可用'}</small></span>},
    {title:'步骤',render:(_,v)=><span>{interviewStepLabel(v)}<br/><small>{interviewTypeLabels[v.interviewType]}{v.clientName?` · ${v.clientName}`:''}</small></span>},
    {title:'方式',render:(_,v)=><span>{displayEnum(v.meetingMode)}<br/><MeetingPlace value={v}/></span>},
    {title:'状态',render:(_,v)=><Tag color={v.status==='COMPLETED'?'green':v.status==='CANCELLED'?'default':'blue'}>{interviewStatusLabels[v.status]}</Tag>},
    {title:'负责人',dataIndex:'nextResponsibleUserId',render:(id:string)=><UserName id={id}/>},
    {title:'操作', className: 'table-action-cell',render:(_,v)=><Button size="small" onClick={()=>onOpen(v.id)}>查看与处理</Button>},
  ]}/></Card>;
}

function InterviewJobFilters({value,onChange}:{value:Record<string,string|number|undefined>;onChange:(value:Record<string,string|number|undefined>)=>void}) {
  const {actor}=useSession();
  return canReadRecruitmentLedger(actor)?<RecruitmentJobFilters value={value} onChange={onChange}/>:<InvitedJobFilters value={value} onChange={onChange}/>;
}

function RecruitmentJobFilters({value,onChange}:{value:Record<string,string|number|undefined>;onChange:(value:Record<string,string|number|undefined>)=>void}) {
  const [jd,setJd]=useState<JdSummary>();
  const versions=useQuery({queryKey:['demand','jd-versions',jd?.id],queryFn:()=>demandApi.jdVersions(jd!.id),enabled:Boolean(jd?.id&&value.jdId===jd.id)});
  return <><Col xs={24} md={6}><JdSelector {...(typeof value.jdId==='string'?{value:value.jdId}:{})} placeholder="搜索应聘岗位" onChange={item=>{setJd(item);onChange({...value,jdId:item?.id,jdVersionId:undefined});}}/></Col><Col xs={24} md={6}><Select allowClear disabled={!value.jdId} value={value.jdVersionId??null} aria-label="岗位版本" placeholder="全部岗位版本" onChange={jdVersionId=>onChange({...value,jdVersionId})} options={(versions.data?.items??[]).map(item=>({value:item.id,label:`第 ${item.versionNo} 版 · ${item.title}`}))}/></Col></>;
}

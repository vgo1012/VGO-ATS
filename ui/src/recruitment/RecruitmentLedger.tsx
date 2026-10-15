import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { LedgerActionDialog } from './LedgerActionDialog';
import { type LedgerAction } from './activityApi';
import { CompactPagination, type CompactPaginationProps } from '../components/CompactPagination';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CalendarOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, DatePicker, Empty, Grid, Input, Select, Space, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { businessWeek, dateRangeParams, type DateRange } from '../components/dateRange';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { JdSelector } from '../demand/JdSelector';
import { JobName, ResumeVersionName, UserName } from '../display/BusinessReferences';
import { talentApi, type Candidate, type ResumeFactBundle } from '../talent/api';
import { CandidateGenderIcon } from '../talent/CandidateGenderIcon';
import { resumeReviewFlags } from '../talent/resumeReview';
import { ResumeReviewPanel } from '../talent/ResumeReviewPanel';
import type { RecruitmentApplication } from './api';
import { ClientFilter } from './ClientFilter';
import { recruitmentStatus } from './currentStageLabel';
import { RecruitmentStage } from './RecruitmentStage';
import './recruitment-ledger.css';

type Filters = Record<string, string | undefined>;

function RecruitmentDates({ value, onChange }: { value: Filters; onChange: (value: Filters) => void }) {
  const [custom, setCustom] = useState(false);
  const range: DateRange = value.from && value.to ? [dayjs(value.from).tz('Asia/Shanghai'), dayjs(value.to).tz('Asia/Shanghai').subtract(1, 'day')] : businessWeek();
  const apply = (next: DateRange) => onChange({ ...value, ...dateRangeParams(next) });
  const thisWeek = businessWeek();
  return <section className="recruitment-date-toolbar" aria-label="招聘记录更新时间范围">
    <div className="recruitment-date-controls"><b className="recruitment-toolbar-title" title="按招聘记录更新时间筛选">时间范围</b>
      <Button onClick={() => apply([range[0].subtract(7, 'day'), range[1].subtract(7, 'day')])}>上一周</Button>
      <Button className={range[0].isSame(thisWeek[0], 'day') && range[1].isSame(thisWeek[1], 'day') ? 'current-week' : ''} onClick={() => { setCustom(false); apply(thisWeek); }}>本周</Button>
      <Button onClick={() => apply([range[0].add(7, 'day'), range[1].add(7, 'day')])}>下一周</Button>
      <Button aria-expanded={custom} onClick={() => setCustom(!custom)}>自定义</Button>
      {custom ? <DatePicker.RangePicker aria-label="招聘记录更新时间" allowClear={false} value={range} onChange={dates => { if (dates?.[0] && dates[1]) apply([dates[0], dates[1]]); }} /> : <span className="recruitment-date-label"><CalendarOutlined />{range[0].format('M月D日')} - {range[1].format('M月D日')}</span>}
    </div>
    <label className="recruitment-date-jump">跳转日期<DatePicker aria-label="跳转日期" allowClear={false} value={range[0]} onChange={date => { if (date) { setCustom(false); apply(businessWeek(date)); } }} /></label>
  </section>;
}

function RecruitmentFilters({ value, onChange }: { value: Filters; onChange: (value: Filters) => void }) {
  const searchCandidate=(query:string)=>onChange({...value,q:query.trim()||undefined});
  return <section className="recruitment-filter-card" aria-label="智能筛选">
    <b className="recruitment-toolbar-title">智能筛选</b>
    <div className="recruitment-filter-grid">
      <label className="recruitment-candidate-search"><Input.Search key={value.q??''} allowClear enterButton="搜索" aria-label="搜索候选人姓名" placeholder="搜索候选人姓名" defaultValue={value.q??''} onChange={event=>{if(!event.target.value&&value.q)searchCandidate('');}} onSearch={searchCandidate}/></label>
      <label><JdSelector {...(value.jdId ? { value: value.jdId } : {})} placeholder="搜索应聘岗位" onChange={jd => onChange({ ...value, jdId: jd?.id })} /></label>
      <label><ClientFilter value={value.clientId} onChange={clientId => onChange({ ...value, clientId })} /></label>
      <label><Select aria-label="任务状态" value={value.stage==='ONBOARDED'?'COMPLETED':value.stage==='TERMINATED'?'CANCELLED':value.lifecycle==='ALL'?'ALL':'ACTIVE'} onChange={status=>onChange({...value,lifecycle:status==='ACTIVE'?'ACTIVE':'ALL',stage:status==='COMPLETED'?'ONBOARDED':status==='CANCELLED'?'TERMINATED':undefined})} options={[{value:'ALL',label:'全部状态'},{value:'ACTIVE',label:'进行中'},{value:'COMPLETED',label:'已完成'},{value:'CANCELLED',label:'已取消'}]}/></label>
    </div>
    <Button type="primary" aria-label="重置" onClick={() => onChange({ from: value.from, to: value.to, lifecycle: 'ACTIVE' })}>重置</Button>
  </section>;
}

function RecruitmentCandidate({ candidate, summary, loading, onOpen }: { candidate:Candidate|undefined; summary:ResumeFactBundle|null|undefined; loading:boolean; onOpen: () => void }) {
  const name = candidate?.fullName ?? (loading?'加载候选人…':'候选人信息暂不可用');
  return <Space orientation="vertical" size={0}><Button type="link" className="table-link" title={name} onClick={onOpen}><span className="candidate-name">{name}</span><CandidateGenderIcon gender={summary?.snapshot?.gender} /></Button><small className="talent-contact">{candidate?.primaryPhone ?? candidate?.primaryEmail ?? '联系方式未提供'}</small></Space>;
}

function RecruitmentActions({ value, summary, summaryLoading, summaryError, onRetry, onOpen }: { value: RecruitmentApplication; summary:ResumeFactBundle|null|undefined; summaryLoading:boolean; summaryError:boolean; onRetry:()=>void; onOpen: () => void }) {
  const [reviewOpen, setReviewOpen] = useState(false);
  const facts = useQuery({ queryKey: ['talent', 'resume-facts', value.resumeVersionId], queryFn: () => talentApi.resumeFacts(value.resumeVersionId), enabled: reviewOpen&&Boolean(value.resumeVersionId), staleTime: 60_000, retry: false });
  const bundle = facts.data?.snapshot ? facts.data : undefined;
  const flags = summary?.snapshot ? resumeReviewFlags(summary) : [];
  return <><Space className="recruitment-row-actions table-action-stack" orientation="vertical" size={6}>
    <Button type="link" onClick={onOpen}>详情与版本</Button>
    {summary?.snapshot ? flags.length ? <Button type="link" onClick={() => setReviewOpen(true)}>复核 {flags.length} 项</Button> : <small>简历无待复核</small> : summaryError ? <Button type="link" onClick={onRetry}>重试复核信息</Button> : <small>{summaryLoading ? '读取复核信息…' : '复核信息暂不可用'}</small>}
  </Space><WorkspaceDialog title="简历待复核" size="wide" open={reviewOpen} onCancel={() => setReviewOpen(false)} footer={null}>
    {reviewOpen && bundle ? <><p><ResumeVersionName candidateId={value.candidateId} versionId={value.resumeVersionId} /></p><ResumeReviewPanel bundle={bundle} /></> : null}
    {reviewOpen && facts.isPending ? <p role="status">正在读取完整复核信息…</p> : null}
    {reviewOpen && facts.isError ? <Alert occurrence={facts.error} type="error" showIcon title="复核信息读取失败" action={<Button onClick={()=>void facts.refetch()}>重试完整复核信息</Button>}/> : null}
  </WorkspaceDialog></>;
}

export function Applications({ countProps, items, loading, error, filters, onFilters, onOpen, page, pageSize, onPageSizeChange, hasPrevious, hasNext, onPrevious, onNext, onPageChange }: { countProps?: Pick<CompactPaginationProps,'total'|'totalStatus'|'onRetryTotal'>; items: RecruitmentApplication[]; loading: boolean; error: unknown; filters: Filters; onFilters: (value: Filters) => void; onOpen: (id: string) => void; page: number; pageSize: number; onPageSizeChange: (size: number) => void; hasPrevious: boolean; hasNext: boolean; onPrevious: () => void; onNext: () => void; onPageChange?: (page: number) => void }) {
  const queryClient = useQueryClient();
  const fitColumns = Boolean(Grid.useBreakpoint().xl);
  const [actionTarget, setActionTarget] = useState<{applicationId:string;action:LedgerAction;trigger:HTMLElement;roundNo?:number}>();
  const [selectedIds, setSelectedIds] = useState<React.Key[]>([]);
  const selectedCount = items.filter(item => selectedIds.includes(item.id)).length;
  const candidateIds=[...new Set(items.map(item=>item.candidateId))];
  const versionIds=[...new Set(items.map(item=>item.resumeVersionId).filter(Boolean))];
  const candidates=useQuery({queryKey:['talent','candidates','ledger',candidateIds],queryFn:()=>talentApi.candidateBatch(candidateIds),enabled:candidateIds.length>0,staleTime:60_000,retry:false});
  // 独立摘要键，不写入完整 facts 缓存；保留更正事实时的摘要失效范围。
  const summaries=useQuery({queryKey:['talent','candidate-resume-summaries','by-version',versionIds],queryFn:()=>talentApi.resumeVersionFactSummaries(versionIds),enabled:versionIds.length>0,staleTime:60_000,retry:false});
  const candidateById=new Map(candidates.data?.map(candidate=>[candidate.id,candidate]));
  const summaryByVersion=new Map(summaries.data?.map(summary=>[summary.resumeVersionId,summary.facts]));
  return <div className="recruitment-ledger"><div className="recruitment-toolbars"><RecruitmentDates value={filters} onChange={onFilters} /><RecruitmentFilters value={filters} onChange={onFilters} /></div>
    <Card className="pipeline-table-card paged-panel">
      {error ? <Alert occurrence={error} type="error" showIcon title={error instanceof ApiProblemError ? error.problem.detail : error instanceof Error ? error.message : '招聘记录加载失败'} action={<Button onClick={() => void queryClient.invalidateQueries({ queryKey: ['applications'] })}>重试</Button>} /> : null}
      {candidates.isError ? <Alert occurrence={candidates.error} type="error" showIcon title="候选人资料读取失败" action={<Button onClick={()=>void candidates.refetch()}>重试候选人资料</Button>}/> : null}
      <p className="recruitment-table-scroll-hint">横向滚动表格，查看当前阶段、状态和操作</p>
      <Table<RecruitmentApplication> className="candidate-centered-table" onHeaderRow={() => scrollableTableHeader(fitColumns ? '招聘流程表头' : '招聘流程表头，可横向滚动')} rowKey="id" rowSelection={{ getTitleCheckboxProps: () => ({ 'aria-label': '全选本页招聘记录', disabled: loading || !items.length }), getCheckboxProps: () => ({ disabled: loading }), columnWidth: fitColumns ? '3%' : 40, selectedRowKeys: selectedIds, onChange: setSelectedIds }} loading={loading} pagination={false} dataSource={items} locale={{ emptyText: <Empty description="当前条件下暂无招聘记录，请调整日期或筛选条件" /> }} scroll={fitColumns ? {} : { x: 1430 }} columns={[
        { title: '#', width: fitColumns ? '3%' : 48, render: (_: unknown, _value: RecruitmentApplication, index: number) => (page - 1) * pageSize + index + 1 },
        { title: '候选人', className: 'talent-identity-cell', width: fitColumns ? '14%' : 185, render: (_, value) => <RecruitmentCandidate candidate={candidateById.get(value.candidateId)} summary={summaryByVersion.get(value.resumeVersionId)} loading={candidates.isPending} onOpen={() => onOpen(value.id)} /> },
        { title: '应聘岗位', width: fitColumns ? '16%' : 220, render: (_, value) => <JobName jdId={value.jdId} demandId={value.demandId} /> },
        { title: '甲方公司', width: fitColumns ? '10%' : 140, render: (_, value) => value.recruitmentType === 'EXTERNAL' ? value.clientName ?? '甲方信息待核对' : '—' },
        { title: '当前阶段', width: fitColumns ? '28%' : 410, render: (_, value) => <RecruitmentStage value={value} onAction={(action,trigger,roundNo) => setActionTarget({applicationId:value.id,action,trigger,...(roundNo?{roundNo}:{})})} /> },
        { title: '状态', width: fitColumns ? '8%' : 100, render: (_, value) => { const status = recruitmentStatus(value); return <Tag color={status === '已完成' ? 'success' : status === '已取消' ? 'default' : 'processing'}>{status}</Tag>; } },
        { title: '负责人', width: fitColumns ? '9%' : 110, render: (_, value) => <UserName id={value.ownerUserId} /> },
        { title: '操作', className: 'table-action-cell', width: fitColumns ? '12%' : 135, render: (_, value) => <RecruitmentActions key={value.resumeVersionId} value={value} summary={summaryByVersion.get(value.resumeVersionId)} summaryLoading={summaries.isPending} summaryError={summaries.isError} onRetry={()=>void summaries.refetch()} onOpen={() => onOpen(value.id)} /> },
      ]} />
      <CompactPagination label="招聘流程分页" page={page} pageSize={pageSize} onPageSizeChange={size=>{setSelectedIds([]);onPageSizeChange(size);}} loading={loading} hasPrevious={hasPrevious} hasNext={hasNext} onPrevious={onPrevious} onNext={onNext} {...(onPageChange?{onPageChange,maxPage:page}:{})} {...(!countProps&&page===1&&!hasNext&&!loading&&!error?{total:items.length}:{})} {...countProps} note={selectedCount?`已选 ${selectedCount} 条`:''} />
    </Card>
    {actionTarget ? <LedgerActionDialog key={`${actionTarget.applicationId}-${actionTarget.action}`} {...actionTarget} onClose={() => {
      setActionTarget(undefined);
      requestAnimationFrame(() => {
        const row = [...document.querySelectorAll<HTMLTableRowElement>('.pipeline-table-card tr[data-row-key]')]
          .find(element => element.dataset.rowKey === actionTarget.applicationId);
        const actionButton = actionTarget.roundNo
          ? row?.querySelector<HTMLElement>(`[aria-label="第 ${actionTarget.roundNo} 轮面试"]`)
          : row?.querySelector<HTMLElement>(`.stage-${actionTarget.action.toLowerCase()}`);
        // 保存后列表会刷新；原按钮可能已经从 DOM 移除。
        const focusTarget = [actionButton, actionTarget.trigger, row?.querySelector<HTMLElement>('.table-link'), document.querySelector<HTMLElement>('.recruitment-date-controls button')]
          .find(element => element?.isConnected && !element.matches(':disabled'));
        focusTarget?.focus({ preventScroll: true });
      });
    }} /> : null}
  </div>;
}

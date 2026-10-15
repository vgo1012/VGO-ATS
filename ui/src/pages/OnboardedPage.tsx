import { CompactPagination } from '../components/CompactPagination';
import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { countPaginationProps } from '../components/countPaginationProps';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { MultiConditionSearch } from '../talent/MultiConditionSearch';
import { mergeSearchTerms } from '../talent/searchTerms';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Empty, Space, Table, Tag, Typography } from 'antd';
import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSession } from '../auth/session';
import { canDownloadResume } from '../auth/permissions';
import { ApiProblemError } from '../api/problem';
import { JdSelector } from '../demand/JdSelector';
import { JobName, UserName } from '../display/BusinessReferences';
import { formatDateTime } from '../display/labels';
import { recruitmentApi, type RecruitmentApplication } from '../recruitment/api';
import { OnboardingFlowActions } from '../recruitment/OnboardingFlowActions';
import { CandidateSkills } from '../talent/CandidateSkills';
import { CandidateGenderIcon } from '../talent/CandidateGenderIcon';
import { TalentFilters } from '../talent/TalentFilters';
import { talentApi } from '../talent/api';
import { educationSummary, workYears } from '../talent/summary';
import { talentFilterCriteria, talentFiltersFromUrl, type TalentFilterChoices } from '../talent/talentFilterCriteria';

const CandidateDialog = lazy(() => import('../talent/CandidateDetails').then(module => ({ default: module.CandidateDialog })));

const errorText = (error: unknown) => error instanceof ApiProblemError ? error.problem.detail : error instanceof Error ? error.message : '入职流程记录加载失败。';
const accessDenied = (error: unknown) => error instanceof ApiProblemError && [401, 403].includes(error.problem.status);
const formatDuration = (seconds?: number | null) => {
  if (seconds == null) return '—';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return [days ? `${days}天` : '', hours ? `${hours}小时` : '', !days && minutes ? `${minutes}分钟` : ''].filter(Boolean).join('') || '不足1分钟';
};

export function OnboardedPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { actor } = useSession();
  const [params, setParams] = useSearchParams();
  const filters = talentFiltersFromUrl(params, 'onboarded');
  const criteria = talentFilterCriteria(filters);
  const jdId = params.get('jd') ?? undefined;
  const query = params.get('q') ?? '';
  const [pageSize, setPageSize] = useState(20);
  const [cursors, setCursors] = useState<string[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [candidateId, setCandidateId] = useState<string>();
  const detailTrigger = useRef<HTMLElement | null>(null);
  const openDetails = (id: string, trigger: HTMLElement) => { detailTrigger.current = trigger; setCandidateId(id); };
  // 懒加载取消或详情卸载后，由列表恢复原触发点，避免异步弹窗丢失焦点。
  useEffect(() => { if (!candidateId) detailTrigger.current?.focus({ preventScroll: true }); }, [candidateId]);
  const requestFilters = { stage: 'ONBOARDED', onboarding: 'true', ...(jdId ? { jdId } : {}), ...(query ? { q: query } : {}), ...(params.has('conditions') ? { conditions: params.getAll('conditions') } : {}), ...(criteria.degrees.length ? { degrees: criteria.degrees.join(',') } : {}), ...Object.fromEntries(['minWorkYears', 'maxWorkYears', 'minAge', 'maxAge', 'gender'].flatMap(key => {
    const value = criteria[key as keyof typeof criteria];
    return value == null ? [] : [[key, String(value)]];
  })) };
  const scope = JSON.stringify(requestFilters);
  const [previousScope, setPreviousScope] = useState(scope);
  if (scope !== previousScope) { setPreviousScope(scope); setSelectedIds([]); setCursors([]); }
  const cursor = cursors.at(-1);
  const applications = useQuery({ queryKey: ['applications', 'onboarded', requestFilters, cursor, pageSize], queryFn: () => recruitmentApi.applications({ ...requestFilters, cursor, limit: pageSize }) });
  const total = useQuery({ queryKey: ['applications', 'onboarded', 'count', requestFilters], queryFn: ({ signal }) => recruitmentApi.countApplications(requestFilters, signal), retry: false });
  const items = applications.data?.items ?? [];
  const candidateIds = [...new Set(items.map(item => item.candidateId))];
  const resumeIds = [...new Set(items.map(item => item.resumeVersionId))];
  const facts = useQuery({ queryKey: ['talent', 'candidate-resume-summaries', 'by-version', resumeIds], queryFn: () => talentApi.resumeVersionFactSummaries(resumeIds), enabled: resumeIds.length > 0, staleTime: 60_000, retry: false });
  const sources = useQuery({ queryKey: ['talent', 'resume-source', 'by-version', resumeIds], queryFn: () => talentApi.resumeVersionSources(resumeIds), enabled: resumeIds.length > 0, staleTime: 60_000, retry: false });
  const factByResumeId = new Map(!accessDenied(facts.error) ? facts.data?.map(summary => [summary.resumeVersionId, summary.facts] as const) : []);
  const omittedResumeIds = new Set(facts.isSuccess ? resumeIds.filter(id => !factByResumeId.has(id)) : []);
  const sourceByResumeId = new Map(!accessDenied(sources.error) ? sources.data?.map(value => [value.resumeVersionId, value.source] as const) : []);
  const omittedSourceIds = new Set(sources.isSuccess ? resumeIds.filter(id => !sourceByResumeId.has(id)) : []);
  const summaryFor = (item: RecruitmentApplication) => {
    return { facts: factByResumeId.get(item.resumeVersionId) ?? undefined, loading: facts.isPending, source: sourceByResumeId.get(item.resumeVersionId), sourceLoading: sources.isPending, sourceDenied: accessDenied(sources.error) || omittedSourceIds.has(item.resumeVersionId), forbidden: accessDenied(facts.error) || omittedResumeIds.has(item.resumeVersionId), unparsed: factByResumeId.has(item.resumeVersionId) && factByResumeId.get(item.resumeVersionId) === null };
  };
  const candidates = useQuery({ queryKey: ['talent', 'candidates', 'onboarded', candidateIds], queryFn: () => talentApi.candidateBatch(candidateIds), enabled: candidateIds.length > 0, staleTime: 60_000, retry: false });
  const candidateById = new Map(!accessDenied(candidates.error) ? candidates.data?.map(candidate => [candidate.id, candidate] as const) : []);
  const omittedCandidateIds = new Set(candidates.isSuccess ? candidateIds.filter(id => !candidateById.has(id)) : []);
  const deniedCandidateIds = accessDenied(candidates.error) ? new Set(candidateIds) : omittedCandidateIds;
  const failedDetails = sources.isError || omittedSourceIds.size > 0 || (sources.isSuccess && sources.data.some(value => value.source === null)) ? [sources] : [];
  const candidateDetailsUnavailable = candidates.isError || omittedCandidateIds.size > 0;
  const factDetailsUnavailable = facts.isError || omittedResumeIds.size > 0;
  const retryingDetails = candidates.isFetching || facts.isFetching || failedDetails.some(result => result.isFetching);
  const selectIds = (ids: string[]) => { if (ids.length > 1000) { void message.warning('一次最多选择 1000 条记录'); return; } setSelectedIds(ids); };
  const [pendingKeyword, setPendingKeyword] = useState('');
  // 条件按钮不经过搜索栏提交，必须自行带上已输入但未提交的关键词，否则用户看得见的输入会被静默丢弃。
  const submittedTerms = mergeSearchTerms([query, ...params.getAll('conditions'), pendingKeyword]);
  const applyFilters = (next: TalentFilterChoices, text = submittedTerms[0] ?? '', conditions = submittedTerms.slice(1)) => {
    setCursors([]); setSelectedIds([]);
    setParams(previous => {
    const value = new URLSearchParams(previous);
    for (const key of ['education', 'experience', 'age', 'gender'] as const) { if (next[key] && next[key] !== '不限') value.set(`onboarded_${key}`, next[key]); else value.delete(`onboarded_${key}`); }
    value.delete('conditions'); conditions.forEach(condition => value.append('conditions', condition));
    if (text.trim()) value.set('q', text.trim()); else value.delete('q');
    return value;
    }, { replace: true });
  };
  const nextCursor = applications.data?.nextCursor;
  const columns = [
    { title: '#', width: 48, render: (_: unknown, _value: RecruitmentApplication, index: number) => cursors.length * pageSize + index + 1 },
    { title: '候选人', className: 'talent-identity-cell', width: 180, render: (_: unknown, value: RecruitmentApplication) => <Space orientation="vertical" size={0}><Button type="link" className="table-link" title={candidateById.get(value.candidateId)?.fullName ?? (candidates.isPending ? '读取候选人…' : deniedCandidateIds.has(value.candidateId) ? '候选人需授权' : '候选人暂不可用')} onClick={event => openDetails(value.candidateId, event.currentTarget)}><span className="candidate-name">{candidateById.get(value.candidateId)?.fullName ?? (candidates.isPending ? '读取候选人…' : deniedCandidateIds.has(value.candidateId) ? '候选人需授权' : '候选人暂不可用')}</span><CandidateGenderIcon gender={summaryFor(value).forbidden ? undefined : summaryFor(value).facts?.snapshot?.gender} /></Button><small className="talent-contact">{candidateById.get(value.candidateId)?.primaryPhone ?? candidateById.get(value.candidateId)?.primaryEmail ?? (deniedCandidateIds.has(value.candidateId) ? '联系方式需授权' : '联系方式未提供')}</small></Space> },
    { title: '年龄', className: 'talent-age-cell', width: 60, render: (_: unknown, value: RecruitmentApplication) => { const summary = summaryFor(value); const age = summary.facts?.snapshot?.age; return age == null ? (summary.loading ? '加载中…' : '—') : `${age} 岁`; } },
    { title: '求职岗位', width: 130, render: (_: unknown, value: RecruitmentApplication) => <JobName jdId={value.jdId} demandId={value.demandId} /> },
    { title: '工作年限', width: 76, render: (_: unknown, value: RecruitmentApplication) => { const facts = summaryFor(value).facts; return facts ? workYears(facts) : summaryFor(value).loading ? '加载中…' : '—'; } },
    { title: '学历', width: 64, render: (_: unknown, value: RecruitmentApplication) => { const facts = summaryFor(value).facts; return facts ? educationSummary(facts) : summaryFor(value).loading ? '加载中…' : '—'; } },
    { title: '招聘流程', width: 142, render: (_: unknown, value: RecruitmentApplication) => <small>招聘负责人：<UserName id={value.ownerUserId} /></small> },
    { title: '入职状态', width: 178, render: (_: unknown, value: RecruitmentApplication) => <Space orientation="vertical" size={2}><Tag color={value.onboardingStatus === 'IN_PROGRESS' ? 'gold' : 'green'}>{value.onboardingStatus === 'IN_PROGRESS' ? '入职中' : '已入职'}</Tag><small>{value.onboardingStatus === 'IN_PROGRESS' ? `已历时 ${formatDuration(value.onboardingElapsedSeconds)}` : `历时 ${formatDuration(value.onboardingElapsedSeconds)}`}</small>{value.actualOnboardedAt ? <small title="实际入职时间">实际：{formatDateTime(value.actualOnboardedAt)}</small> : null}</Space> },
    { title: '核心技能', className: 'talent-skills-cell', render: (_: unknown, value: RecruitmentApplication) => { const skills = summaryFor(value).facts?.skills; return skills?.length ? <CandidateSkills skills={skills}/> : summaryFor(value).loading ? '加载中…' : '—'; } },
    { title: '来源 / 归属人', className: 'talent-source-cell', width: 110, render: (_: unknown, value: RecruitmentApplication) => <Space orientation="vertical" size={0}><span>{summaryFor(value).source?.sourceChannelName ?? (summaryFor(value).sourceLoading ? '读取来源…' : summaryFor(value).sourceDenied ? '来源需授权' : '来源未提供')}</span><small>{candidateById.has(value.candidateId) ? <UserName id={candidateById.get(value.candidateId)?.ownerUserId} /> : '归属人暂不可用'}</small></Space> },
    { title: '入职操作', className: 'table-action-cell', width: 150, render: (_: unknown, value: RecruitmentApplication) => <OnboardingFlowActions application={value} onChanged={async () => { await Promise.all([applications.refetch(), queryClient.invalidateQueries({ queryKey: ['talent-search'] }), queryClient.invalidateQueries({ queryKey: ['talent', 'pool-statuses'] })]); }} /> },
    { title: '操作', className: 'table-action-cell', width: 112, render: (_: unknown, value: RecruitmentApplication) => <Button type="link" size="small" onClick={event => openDetails(value.candidateId, event.currentTarget)}>查看简历</Button> },
  ];
  return <div className="workspace-page onboarded-page pagination-workspace">
    <section className="ledger-heading"><Typography.Title level={1}>入职流程</Typography.Title></section>
    <TalentFilters value={filters} onApply={applyFilters} />
    {applications.error ? <Alert occurrence={applications.error} type="error" showIcon title={errorText(applications.error)} description="未加载的数据不代表没有入职流程记录。" action={<Button loading={applications.isFetching} onClick={() => void applications.refetch()}>重试</Button>} /> : null}
    <Card className="talent-ledger-card paged-panel">
      <div className="talent-toolbar shared-library-toolbar">
        <div className="talent-bulk-actions">
          <JdSelector prioritizeCurrentOwner {...(jdId ? { value: jdId } : {})} placeholder="求职岗位（选择 JD）" onChange={job => setParams(previous => { const next = new URLSearchParams(previous); if (job) next.set('jd', job.id); else next.delete('jd'); return next; }, { replace: true })} />
        </div>
        <MultiConditionSearch label="入职流程关键词搜索" query={query} conditions={params.getAll('conditions')} onDraftChange={setPendingKeyword} onSearch={(text,conditions)=>applyFilters(filters,text,conditions)} />
      </div>
      {failedDetails.length > 0 || candidateDetailsUnavailable || factDetailsUnavailable ? <Alert type="warning" showIcon title="部分候选人资料暂不可用，已授权信息仍可查看" description={omittedCandidateIds.size > 0 || omittedResumeIds.size > 0 ? '部分候选人或简历版本不存在，或当前账号无权查看，可在权限恢复后重试。' : undefined} action={<Button loading={retryingDetails} disabled={retryingDetails} onClick={() => void Promise.all([...failedDetails.map(result => result.refetch({ cancelRefetch: false })), ...(candidateDetailsUnavailable ? [candidates.refetch({ cancelRefetch: false })] : []), ...(factDetailsUnavailable ? [facts.refetch({ cancelRefetch: false })] : [])])}>重试候选人资料</Button>} /> : null}
      <Table<RecruitmentApplication> className="candidate-centered-table" tableLayout="fixed" rowKey="id" rowSelection={{ getTitleCheckboxProps: () => ({ 'aria-label': '全选本页入职流程记录', disabled: applications.isFetching || !items.length }), getCheckboxProps: () => ({ disabled: applications.isFetching }), columnWidth: 40, selectedRowKeys: selectedIds, preserveSelectedRowKeys: true, onChange: keys => selectIds(keys.map(String)) }} loading={applications.isPending} columns={columns} dataSource={items} pagination={false} locale={{ emptyText: <Empty description="当前没有入职流程记录" /> }} scroll={{x:1430}} onHeaderRow={() => scrollableTableHeader('入职流程记录表，可水平滚动查看完整内容')} />
      <CompactPagination label="入职流程分页" page={cursors.length+1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursors([]);setSelectedIds([]);}} loading={applications.isFetching} hasPrevious={cursors.length>0} hasNext={Boolean(applications.data?.hasMore&&nextCursor)} onPrevious={()=>setCursors(value=>value.slice(0,-1))} onNext={()=>{if(nextCursor)setCursors(value=>[...value,nextCursor]);}} onPageChange={target=>setCursors(value=>value.slice(0,target-1))} maxPage={cursors.length+1} {...countPaginationProps(total)} />
    </Card>
    {candidateId ? <Suspense fallback={<Space><span role="status">正在加载简历详情…</span><Button onClick={() => setCandidateId(undefined)}>取消加载详情</Button></Space>}><CandidateDialog compact candidateId={candidateId} open motionFrom={undefined} canDownload={canDownloadResume(actor)} onClose={() => setCandidateId(undefined)} /></Suspense> : null}
  </div>;
}

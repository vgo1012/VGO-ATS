import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { jobLabel } from '../demand/jobLabel';
import { CompactPagination } from '../components/CompactPagination';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { FileTextOutlined, ReloadOutlined } from '@ant-design/icons';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Checkbox, Col, Collapse, Descriptions, Empty, Grid, Input, Progress, Row, Segmented, Select, Space, Table, Tag } from 'antd';
import { WorkspaceDialog as Modal } from '../components/WorkspaceDialog';
import { detailOrigin, type DetailOrigin } from '../components/detailMotion';
import { useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { MatchSummary } from '../matching/MatchSummary';
import { MatchRunJob } from '../matching/MatchRunJob';
import { MatchJobWorkspace, ScreeningResultsLoading } from '../matching/MatchJobWorkspace';
import { ScreeningReturnDialog, type ScreeningReturnTarget } from '../matching/ScreeningReturnDialog';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { canDownloadResume } from '../auth/permissions';
import { CandidateDialog } from '../talent/CandidateDetails';
import { demandApi, type JdSummary } from '../demand/api';
import { WeightProfiles } from '../matching/WeightProfiles';
import { screeningSettings } from '../matching/screeningSettings';
import { ScreeningJobHeader } from '../matching/ScreeningJobHeader';
import { useScreeningJobs } from '../matching/useScreeningJobs';
import { ScreeningMetrics, type ScreeningFilter } from '../matching/ScreeningMetrics';
import { assessmentPresentation, criterionPresentation, mustTierPresentation, screeningGrade, screeningGradeLabels } from '../matching/screeningGrade';
import '../matching/screeningWorkspace.css';
import { PROFESSIONAL_DIMENSIONS, isPersonalSelectable } from '../demand/scoringRules';
import { selectScreeningCandidates } from '../matching/selectScreeningCandidates';
import { talentApi } from '../talent/api';
import { matchingApi, type MatchResult, type MatchRun, type JobMatchResult, type ScreeningSummary } from '../matching/api';
import { recruitmentApi } from '../recruitment/api';
import { CandidateActionDialog, type CandidateActionTarget } from '../recruitment/CandidateActionDialog';
import { selectMatchResults } from '../matching/selectMatchResults';
import { talentHandoff } from '../matching/talentHandoff';
import { taskApi, taskPollingInterval } from '../tasks/task';

import { displayEnum } from '../display/labels';

const criterionLabels = { AGE: '年龄', GENDER: '性别', EDUCATION: '学历', CERTIFICATE: '证书', LANGUAGE: '语言' } as const;

function errorMessage(error: unknown) {
  return error instanceof ApiProblemError ? error.problem.detail : error instanceof Error ? error.message : '操作失败，请稍后重试。';
}

function accessDenied(error: unknown) {
  return error instanceof ApiProblemError && [401, 403].includes(error.problem.status);
}

export function MatchingWorkspacePage() {
  const { actor } = useSession();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const navigate=useNavigate();
  const location=useLocation();
  const handoff=talentHandoff(location.state);
  const [searchParams, setSearchParams] = useSearchParams();
  const routeScoreJob=searchParams.get('scoreJob')??undefined;
  const recoveryVersionId = routeScoreJob;
  const firstJobs = useScreeningJobs('', !handoff.jdId && !searchParams.has('run') && !routeScoreJob);
  const recoveredVersion=useQuery({queryKey:['demand','jd-version',recoveryVersionId],queryFn:()=>demandApi.jdVersion(recoveryVersionId!),enabled:Boolean(recoveryVersionId),retry:false});
  const parentJdId=recoveryVersionId?recoveredVersion.data?.jdId:handoff.jdId;
  const parentJd=useQuery({queryKey:['demand','jd',parentJdId],queryFn:()=>demandApi.jd(parentJdId!),enabled:Boolean(parentJdId&&(!recoveryVersionId||!accessDenied(recoveredVersion.error))),retry:false});
  const recoveryDenied=accessDenied(recoveredVersion.error)||accessDenied(parentJd.error);
  const jd=recoveryDenied?undefined:parentJdId?parentJd.data:!searchParams.has('run')&&!routeScoreJob?firstJobs.data?.pages[0]?.items[0]:undefined;
  const [panel, setPanel] = useState<'preferences'>();
  const [versionRetryFor, setVersionRetryFor] = useState<string>();
  const defaults = useQuery({ queryKey: ['matching', 'defaults'], queryFn: demandApi.scoringDefaults });
  const versionQueryKey = ['demand', 'jd-versions-by-id', jd?.id];
  const versions = useQuery({ queryKey: versionQueryKey, queryFn: () => demandApi.jdVersions(jd!.id), enabled: Boolean(jd&&!recoveryVersionId), retry: false });
  const recoveryError = recoveredVersion.error ?? parentJd.error;
  const versionsError = recoveryVersionId ? recoveryError : versions.error;
  const versionsDenied = recoveryVersionId ? recoveryDenied : accessDenied(versionsError);
  const selectedVersion = versionsDenied ? undefined : recoveryVersionId
    ? parentJd.data ? recoveredVersion.data : undefined
    : versions.data?.items.find(item => item.versionNo === jd?.currentVersionNo);
  const jdVersionId = selectedVersion?.id;
  const admitted=useQuery({queryKey:['matching','screening-candidates','admitted'],queryFn:()=>talentApi.screeningCandidates(),retry:false});
  const active = useQuery({
    queryKey: ['matching', 'active-jd', jdVersionId],
    queryFn: () => demandApi.activeScoringForJd(jdVersionId!),
    enabled: Boolean(jdVersionId),
  });
  const profileQueryKey = ['matching', 'weight-profiles', { mineOnly: true }] as const;
  const profiles = useInfiniteQuery({ queryKey: profileQueryKey, initialPageParam: undefined as string | undefined, queryFn: ({ pageParam }) => demandApi.weightProfiles(true, pageParam), getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined, retry:false });
  const config = active.data?.scoringConfiguration;
  const [scheme, setScheme] = useState<'company' | 'personal'>('company');
  const [selectedProfileVersion, setSelectedProfileVersion] = useState<string>();
  const [selectedCriteria, setSelectedCriteria] = useState<string[]>([]);
  const personalProfiles = (accessDenied(profiles.error) ? [] : profiles.data?.pages.flatMap(page => page.items) ?? []).filter(item => item.currentVersion.id !== defaults.data?.weightProfile.currentVersion.id);
  const effectiveProfileVersion = scheme === 'company' ? defaults.data?.weightProfile.currentVersion.id : selectedProfileVersion ?? personalProfiles[0]?.currentVersion.id;
  const effectiveCriteria = scheme === 'company' ? [] : selectedCriteria.filter(id => config?.criteria.some(item => item.id === id && isPersonalSelectable(item)));
  const currentProfile = scheme === 'company' ? defaults.data?.weightProfile : personalProfiles.find(item => item.currentVersion.id === effectiveProfileVersion);
  const currentWeights = currentProfile?.currentVersion;
  const weightFields = { skills: 'skillsWeight', roleExperience: 'roleWeight', relatedYears: 'yearsWeight', responsibilityMatch: 'responsibilityWeight', industryAndProjects: 'industryProjectWeight' } as const;
  const dimensionFields = { skills: 'skillsEnabled', roleExperience: 'roleEnabled', relatedYears: 'yearsEnabled', responsibilityMatch: 'responsibilityEnabled', industryAndProjects: 'industryProjectEnabled' } as const;
  const enabledWeight = PROFESSIONAL_DIMENSIONS.reduce((sum, [key, , fallback]) => sum + (config?.version[dimensionFields[key]] ? currentWeights?.[weightFields[key]] ?? fallback : 0), 0);
  const sortMode = screeningSettings(currentProfile).defaultSortMode;
  const basisSelection = currentProfile && config ? {
    profile: currentProfile,
    planVersionId: config.version.id,
    selectedCriterionIds: effectiveCriteria,
    sortMode,
  } : undefined;
  const runId = searchParams.get('run') || undefined;
  const setRunId = (id:string|undefined, preserveJob=true, stateJd=jd) => setSearchParams(current => { const next=new URLSearchParams(current);if(id)next.set('run',id);else next.delete('run');next.delete('scoreJob');next.delete('basis');return next; }, {state:preserveJob?{...(stateJd?{jdId:stateJd.id}:{})}:null});
  const selectedScoreJob=searchParams.get('scoreJob')||(runId ? undefined : jdVersionId);
  const selectedBasis=searchParams.get('basis')||undefined;
  const [resultCursors, setResultCursors] = useState<string[]>([]);
  const [resultPageSize, setResultPageSize] = useState(20);
  const resultCursor = resultCursors.at(-1);
  const preferCriteria = config?.criteria.filter(isPersonalSelectable) ?? [];
  const run = useQuery({
    queryKey: ['matching', 'run', runId], queryFn: () => matchingApi.run(runId!), enabled: Boolean(runId), retry: false,
    refetchInterval: (query) => !accessDenied(query.state.error) && query.state.data && ['QUEUED', 'RUNNING'].includes(query.state.data.status) ? 2_000 : false,
  });
  const runDenied = accessDenied(run.error);
  const visibleRun = runDenied ? undefined : run.data;
  const taskId = visibleRun?.taskId;
  const task = useQuery({ queryKey: ['tasks', taskId], queryFn: () => taskApi.get(taskId!), enabled: Boolean(taskId), retry: false, refetchInterval: (query) => accessDenied(query.state.error) ? false : taskPollingInterval(query.state.data) });
  const taskDenied = accessDenied(task.error);
  const visibleTask = taskDenied ? undefined : task.data;
  const [retryingTaskId, setRetryingTaskId] = useState<string>();
  const retryingTask = Boolean(taskId && retryingTaskId === taskId);
  const taskBusy = task.isFetching || retryingTask;
  const results = useQuery({ queryKey: ['matching', 'results', runId, resultCursor, resultPageSize], queryFn: () => matchingApi.results(runId!, resultCursor, resultPageSize), enabled: Boolean(runId && visibleRun && ['SUCCEEDED', 'PARTIAL'].includes(visibleRun.status)), retry: false });
  const resultsDenied = accessDenied(results.error);
  const visibleResults = runDenied || resultsDenied ? undefined : results.data;
  const [retryingRunId, setRetryingRunId] = useState<string>();
  const retryingResults = Boolean(runId && retryingRunId === runId);
  const resultsBusy = run.isFetching || results.isFetching || retryingResults;
  const retryResults = async () => {
    setRetryingRunId(runId);
    try {
      const refreshed = await run.refetch({ cancelRefetch: false });
      if (!refreshed.isError && refreshed.data && ['SUCCEEDED', 'PARTIAL'].includes(refreshed.data.status)) {
        await results.refetch({ cancelRefetch: false });
      }
    } finally {
      setRetryingRunId(undefined);
    }
  };
  const runAction = useMutation({
    mutationFn: (action: 'cancel' | 'retry') => {
      if (accessDenied(queryClient.getQueryState(['matching', 'run', runId])?.error)) throw new Error('暂无匹配进度的查看权限，请重新读取后再操作');
      if (accessDenied(queryClient.getQueryState(['tasks', taskId])?.error)) throw new Error('任务进度访问状态已变化，请重新读取后再操作');
      return action === 'cancel' ? matchingApi.cancel(runId!) : matchingApi.retry(runId!);
    },
    onSuccess: async (_, action) => {
      message.success(action === 'cancel' ? '已提交取消请求，正在更新任务状态' : '失败项已提交重试');
      setResultCursors([]);
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['matching', 'run', runId] }), queryClient.invalidateQueries({ queryKey: ['matching', 'results', runId] }), queryClient.invalidateQueries({ queryKey: ['tasks', visibleRun?.taskId] })]);
    },
  });
  const createRun = useMutation({
    mutationFn: async () => {
      if (scheme !== 'personal') throw new Error('公司默认直接使用已有评分，无需重新计算');
      if (!jdVersionId || active.data?.jdVersionId !== jdVersionId) throw new Error('岗位版本尚未读取完成，请重试');
      if (!config) throw new Error('正式评分配置尚未激活');
      const weightProfileVersionId = effectiveProfileVersion;
      if (enabledWeight <= 0) throw new Error('启用维度的比例合计为 0，请调整个人比例');
      if (!weightProfileVersionId) throw new Error('评分权重尚未读取完成，请重试');
      const selected = await selectScreeningCandidates(jdVersionId, '');
      if (!selected.length) throw new Error('当前岗位暂无已加入智能筛选的简历');
      const admittedContexts = await talentApi.screeningCandidates([...new Set(selected.map(item => item.candidateId))]);
      if (selected.some(item => !admittedContexts.some(admitted => admitted.jdVersionId === jdVersionId && admitted.admissionStatus === 'ADMITTED' && admitted.candidateId === item.candidateId && admitted.resumeVersionId === item.resumeVersionId))) {
        throw new Error('当前岗位的简历准入状态已变化，请刷新后重新评分');
      }
      const latestProfiles = queryClient.getQueryState<typeof profiles.data>(profileQueryKey);
      const profileStillOwned = !accessDenied(latestProfiles?.error) && Boolean(latestProfiles?.data?.pages.some(page => page.items.some(item => item.profile.ownerUserId === actor?.userId && item.currentVersion.id === weightProfileVersionId)));
      if (!profileStillOwned) throw new Error('个人方案访问状态已变化，请重新读取后再评分');
      const resumeVersionIds=[...new Set(selected.map(item=>item.resumeVersionId))];
      // 候选人预读期间可能撤权或移除版本，提交前读取当前查询状态。
      const latestParent = parentJdId
        ? queryClient.getQueryState<Awaited<ReturnType<typeof demandApi.jd>>>(['demand', 'jd', parentJdId])
        : undefined;
      let versionAccessible: boolean;
      if (recoveryVersionId) {
        const latestVersion = queryClient.getQueryState<Awaited<ReturnType<typeof demandApi.jdVersion>>>(['demand', 'jd-version', recoveryVersionId]);
        versionAccessible = !accessDenied(latestVersion?.error) && latestVersion?.data?.id === jdVersionId && latestVersion.data.jdId === latestParent?.data?.id;
      } else {
        const latestVersions = queryClient.getQueryState<Awaited<ReturnType<typeof demandApi.jdVersions>>>(versionQueryKey);
        versionAccessible = !accessDenied(latestVersions?.error) && Boolean(latestVersions?.data?.items.some(item => item.id === jdVersionId));
      }
      if (!versionAccessible || parentJdId && (accessDenied(latestParent?.error) || latestParent?.data?.id !== parentJdId)) {
        throw new Error('岗位版本访问状态已变化，请重新读取后再评分');
      }
      return matchingApi.createRun({ jdVersionId, resumeVersionIds, weightProfileVersionId, selectedCriterionIds: effectiveCriteria, sortMode });
    },
    onSuccess: (value) => {
      queryClient.setQueryData(['matching', 'run', value.id], value);
      if (jdVersionId && basisSelection) queryClient.setQueryData(['matching', 'scheme-basis', jdVersionId, basisSelection], value.id);
      void queryClient.invalidateQueries({ predicate: query => query.queryKey[0] === 'matching' && query.queryKey[1] !== 'scheme-basis' });
      setPanel(undefined);
      setResultCursors([]);
      setRunId(value.id);
      message.success('正在计算候选人匹配分');
    },
  });

  const runStatus = visibleRun ? <Alert type={visibleRun.status === 'SUCCEEDED' ? 'success' : visibleRun.status === 'PARTIAL' ? 'warning' : visibleRun.status === 'FAILED' ? 'error' : 'info'} showIcon title={`匹配进度：${displayEnum(visibleRun.status)}`} description={<Space orientation="vertical" style={{ width: '100%' }}>{visibleTask ? <Progress aria-label="候选人匹配进度" percent={visibleTask.progressPercent} /> : null}<span>成功 {visibleRun.succeededCount} / 失败 {visibleRun.failedCount} / 共 {visibleRun.totalCount}</span><Space>{visibleTask && ['QUEUED', 'RUNNING'].includes(visibleTask.status) ? <Button size="small" loading={runAction.isPending} disabled={runAction.isPending} onClick={() => runAction.mutate('cancel')}>取消</Button> : null}{['FAILED', 'PARTIAL'].includes(visibleRun.status) && visibleTask?.retryable ? <Button size="small" loading={runAction.isPending} disabled={runAction.isPending} onClick={() => runAction.mutate('retry')}>仅重试失败项</Button> : null}</Space></Space>} /> : null;

  const showRunStatus=Boolean(visibleRun&&['QUEUED','RUNNING','FAILED','CANCELLED','PARTIAL'].includes(visibleRun.status));

  return (
    <div className="workspace-page matching-workspace-page pagination-workspace">
      <ScreeningJobHeader
        jd={jd} version={selectedVersion}
        loading={Boolean(!jd && (recoveryVersionId ? recoveredVersion.isPending || Boolean(recoveredVersion.data && parentJd.isPending) : parentJdId ? parentJd.isPending : firstJobs.isFetching) || jd && !recoveryVersionId && versions.isPending)}
        disabled={createRun.isPending}
        onChange={value => { const key=['demand','jd',value.id];if(!queryClient.getQueryState(key))queryClient.setQueryData(key,value);setRunId(undefined,true,value); setResultCursors([]); setSelectedCriteria([]); setScheme('company'); setSelectedProfileVersion(undefined); setPanel(undefined); }}
        schemeControls={<>
          <Segmented<'company' | 'personal'> aria-label="评分方案" value={scheme} disabled={createRun.isPending} options={[{label:'公司默认',value:'company'},{label:'个人方案',value:'personal'}]} onChange={value => {
            setScheme(value);
            createRun.reset();
            if (value === 'company' && (runId || selectedBasis)) { setRunId(undefined); setResultCursors([]); }
          }} />
          <Select id="screening-profile" aria-label="选择个人方案" className="screening-profile-select" placeholder="请先维护个人方案" value={scheme === 'company' ? 'company' : currentProfile ? effectiveProfileVersion ?? null : null} loading={profiles.isPending || profiles.isFetchingNextPage} disabled={scheme === 'company' || createRun.isPending || profiles.isPending} options={scheme === 'company' ? [{label:'公司默认方案',value:'company'}] : personalProfiles.map(item => ({label:item.profile.name,value:item.currentVersion.id}))} onChange={setSelectedProfileVersion} popupRender={menu=><>{menu}{profiles.hasNextPage?<Button type="text" block loading={profiles.isFetchingNextPage} onMouseDown={event=>event.preventDefault()} onClick={()=>void profiles.fetchNextPage()}>加载更多方案</Button>:null}</>} />
          <Button disabled={createRun.isPending} onClick={() => setPanel('preferences')}>维护个人方案</Button>
        </>}
        onRescore={() => createRun.mutate()}
        rescoreLoading={createRun.isPending}
        rescoreDisabled={scheme === 'company' || !jdVersionId || !config || !currentProfile || enabledWeight <= 0 || createRun.isPending || ['QUEUED','RUNNING'].includes(visibleRun?.status ?? '')}
        weights={PROFESSIONAL_DIMENSIONS.map(([key, label, fallback]) => ({ label, value: !config || !currentWeights ? '—' : config.version[dimensionFields[key]] && enabledWeight ? `${((currentWeights[weightFields[key]] ?? fallback) / enabledWeight * 100).toFixed(0)}%` : '未启用' }))}
      />
      {firstJobs.error && !jd && !runId && !selectedScoreJob ? <Alert type="error" title="岗位列表读取失败" action={<Button onClick={() => void firstJobs.refetch()}>重试岗位列表</Button>} /> : null}

      {admitted.error?<Alert occurrence={admitted.error} type={admitted.error instanceof ApiProblemError&&admitted.error.problem.status===403?'info':'error'} showIcon title={admitted.error instanceof ApiProblemError&&admitted.error.problem.status===403?'暂无智能筛选查看权限':'智能筛选准入范围读取失败'} description="不会展示未确认加入的默认评分。" action={<Button onClick={()=>void admitted.refetch()}>重试准入范围</Button>}/>:admitted.data?.length===0?<Alert type="info" showIcon title="智能筛选暂无简历" description="请先到“我的简历”勾选同一已评分岗位的简历并点击“智能筛选”，无需再次选择岗位。" action={<Button onClick={()=>navigate('/talents')}>前往我的简历</Button>}/>:null}

      {defaults.error ? <Alert occurrence={defaults.error} type="error" showIcon title="公司默认权重读取失败" action={<Button onClick={() => void defaults.refetch()}>重试默认权重</Button>} /> : null}
      {profiles.error && scheme === 'personal' ? <Alert type="error" showIcon title="个人方案读取失败" action={<Button onClick={() => void profiles.refetch()}>重试个人方案</Button>} /> : null}
      {active.error ? <Alert occurrence={active.error} type="error" showIcon title="评分配置读取失败" action={<Button onClick={() => void active.refetch()}>重试评分配置</Button>} /> : null}
      {versionsError || versionRetryFor !== undefined && versionRetryFor === (jd?.id ?? recoveryVersionId) ? <Alert type={versionsDenied ? 'info' : 'warning'} showIcon title={versionsDenied ? '暂无岗位版本的查看权限' : '岗位版本读取失败'} action={<Button aria-label="重试岗位版本" aria-busy={versions.isFetching || recoveredVersion.isFetching || parentJd.isFetching} loading={versions.isFetching || recoveredVersion.isFetching || parentJd.isFetching} disabled={versions.isFetching || recoveredVersion.isFetching || parentJd.isFetching} onClick={() => {
        setVersionRetryFor(jd?.id ?? recoveryVersionId);
        const retry = recoveryVersionId
          ? recoveredVersion.error || !recoveredVersion.data
            ? recoveredVersion.refetch({ cancelRefetch: false })
            : parentJd.refetch({ cancelRefetch: false })
          : versions.refetch({ cancelRefetch: false });
        void retry.finally(() => setVersionRetryFor(undefined));
      }}>重试岗位版本</Button>} /> : null}

      {createRun.error ? <Alert occurrence={createRun.error} type="error" showIcon title={errorMessage(createRun.error)} /> : null}
      {parentJd.error&&!recoveryVersionId?<Alert occurrence={parentJd.error} type="error" title="关联岗位读取失败，请重新选择岗位" action={<Button onClick={()=>void parentJd.refetch()}>重试岗位</Button>}/>:null}
      {!runId ? <MatchJobWorkspace loading={Boolean((!jd && (parentJd.isFetching || recoveredVersion.isFetching || firstJobs.isFetching)) || (jd && !recoveryVersionId && versions.isPending) || (jdVersionId && active.isPending) || defaults.isPending || admitted.isPending)} basisSelection={basisSelection} schemeLabel={scheme === 'company' ? '公司默认' : '个人方案'} jdVersionId={jdVersionId} selectedJob={recoveryDenied?undefined:selectedScoreJob} basisRunId={recoveryDenied?undefined:selectedBasis} renderResults={(items,basisRun,summary)=><MatchResultsTable key={basisRun.id} items={items.map(item=>item.result)} run={basisRun} job={undefined} sources={items} summary={summary}/>} /> : null}

      <Modal size="wide" title="维护个人方案" open={panel === 'preferences'} footer={null} onCancel={() => setPanel(undefined)}>
        <WeightProfiles onSaved={profile => { setSelectedProfileVersion(profile.currentVersion.id); setScheme('personal'); setPanel(undefined); }} />
        {preferCriteria.length ? <Collapse ghost items={[{key:'criteria',label:'公司可选加分项',children:<Checkbox.Group value={selectedCriteria} onChange={values => setSelectedCriteria(values.map(String))}><Space orientation="vertical">{preferCriteria.map(item => <Checkbox key={item.id} value={item.id}>{criterionLabels[item.criterionCode]} · +{item.bonusPoints} · {item.reason}</Checkbox>)}</Space></Checkbox.Group>}]} /> : null}
      </Modal>
      {runId ? <section className="matching-personal-results">
            <Space orientation="vertical" size={16} style={{ width: '100%' }}>
              {showRunStatus?runStatus:null}
              {visibleResults && visibleRun ? <>
                <MatchResultsTable key={visibleRun.id} items={visibleResults.items} run={visibleRun} job={jd} />
              </> : !showRunStatus && !run.error && !results.error && (run.isPending || results.isPending) ? <ScreeningResultsLoading/> : null}
              {!resultsDenied && visibleRun && ['SUCCEEDED', 'PARTIAL'].includes(visibleRun.status) ? <CompactPagination label="匹配结果分页" page={resultCursors.length+1} pageSize={resultPageSize} onPageSizeChange={size=>{setResultPageSize(size);setResultCursors([]);}} loading={resultsBusy} hasPrevious={resultCursors.length>0} hasNext={Boolean(visibleResults?.hasMore&&visibleResults.nextCursor&&!run.error&&!results.error)} onPrevious={()=>setResultCursors(current=>current.slice(0,-1))} onNext={()=>{const next=visibleResults?.nextCursor;if(next)setResultCursors(current=>[...current,next]);}} onPageChange={target=>setResultCursors(current=>current.slice(0,target-1))} maxPage={resultCursors.length+1} previousLabel="上一页结果" nextLabel="下一页结果" note={`本次成功 ${visibleRun.succeededCount} / 共 ${visibleRun.totalCount}`} total={visibleRun.succeededCount} /> : null}
              {runAction.error ? <Alert occurrence={runAction.error} type="error" showIcon title={errorMessage(runAction.error)} /> : null}
              {task.error || retryingTask ? <Alert type={taskDenied ? 'info' : 'warning'} showIcon title={taskDenied ? '暂无任务进度的查看权限' : '任务进度暂不可用'} action={<Button aria-label="重试任务进度" aria-busy={taskBusy} loading={taskBusy} disabled={taskBusy} onClick={() => {
                setRetryingTaskId(taskId);
                void task.refetch({ cancelRefetch: false }).finally(() => setRetryingTaskId(current => current === taskId ? undefined : current));
              }}>重试任务进度</Button>} /> : null}
              {run.error || results.error || retryingResults ? <Alert type={runDenied || resultsDenied ? 'info' : 'warning'} showIcon title={runDenied || resultsDenied ? '暂无匹配进度或结果的查看权限' : '匹配进度或结果读取失败'} action={<Button aria-label="重试匹配结果" aria-busy={resultsBusy} loading={resultsBusy} disabled={resultsBusy} onClick={() => void retryResults()}>重试匹配结果</Button>} /> : null}
            </Space>
          </section> : null}
    </div>
  );
}

const dimensionLabels: Record<string, string> = { CORE_SKILLS: '核心技能', ROLE_EXPERIENCE: '岗位经历', RELATED_YEARS: '相关年限', RESPONSIBILITY_MATCH: '职责匹配', INDUSTRY_AND_PROJECTS: '行业与项目' };
const dimensionOrder = ['CORE_SKILLS', 'ROLE_EXPERIENCE', 'RELATED_YEARS', 'RESPONSIBILITY_MATCH', 'INDUSTRY_AND_PROJECTS'] as const;

function normalizedWeight(item: MatchResult, code: string) {
  const enabled = item.details.dimensions.filter(dimension => dimension.enabled);
  const total = enabled.reduce((sum, dimension) => sum + dimension.configuredWeight, 0);
  return total ? (enabled.find(dimension => dimension.code === code)?.configuredWeight ?? 0) / total * 100 : 0;
}

function DimensionContribution({ item, code }: { item: MatchResult; code: typeof dimensionOrder[number] }) {
  const dimension = item.details.dimensions.find((value) => value.code === code);
  if (!dimension?.enabled) return <span className="matching-score-unknown" title={dimension?.explanation}>旧版未评·请重评</span>;
  if (dimension.status === 'UNKNOWN') return <span className="matching-score-unknown" title={dimension.explanation}>信息不足</span>;
  const maximum = normalizedWeight(item, code);
  const contribution = dimension.score * maximum / 100;
  const filled = Math.round(dimension.score / 20);
  return <span className={`matching-score-blocks matching-score-${dimensionOrder.indexOf(code) + 1}`} role="img" aria-label={`${dimensionLabels[code]}贡献 ${contribution.toFixed(1)} 分，满分 ${maximum.toFixed(1)} 分`} title={dimension.explanation}>
    <span>{Array.from({ length: 5 }, (_, index) => <i className={index < filled ? 'filled' : ''} key={index} />)}</span>
    <small>{contribution.toFixed(1)}/{maximum.toFixed(1)}</small>
  </span>;
}

function MatchResultsTable({ items, run, job, sources, summary }: { items: MatchResult[]; run: MatchRun; job: JdSummary | undefined; sources?:JobMatchResult[]; summary?:ScreeningSummary|null|undefined }) {
  const { actor } = useSession();
  const queryClient = useQueryClient();
  const [candidateId, setCandidateId] = useState<string>();
  const [candidateOpen, setCandidateOpen] = useState(false);
  const [candidateOrigin, setCandidateOrigin] = useState<DetailOrigin>();
  const [evidenceTarget, setEvidenceTarget] = useState<MatchResult>();
  const [evidenceOpen, setEvidenceOpen] = useState(false);
  const screens = Grid.useBreakpoint();
  const fitColumns = Boolean(screens.xl);
  const [evidenceOrigin, setEvidenceOrigin] = useState<DetailOrigin>();
  const [action,setAction]=useState<{mode:'advance'|'share';targets:CandidateActionTarget[]}>();
  const [selected,setSelected]=useState<MatchResult[]>([]);
  const [completedCandidateIds,setCompletedCandidateIds]=useState<Set<string>>(()=>new Set());
  const selectionRevision=useRef(0);
  const changeSelection=(value:MatchResult[]|((current:MatchResult[])=>MatchResult[]))=>{selectionRevision.current++;setSelected(value);};
  const completeCandidates=(ids:string[])=>{
    if(!ids.length)return;
    setCompletedCandidateIds(current=>new Set([...current,...ids]));
    changeSelection(current=>current.filter(item=>!ids.includes(item.candidateId)));
  };
  const activeItems=items.filter(item=>!completedCandidateIds.has(item.candidateId));
  const [gradeFilter,setGradeFilter]=useState<ScreeningFilter>('all');
  const [search,setSearch]=useState('');
  const [selectingAll,setSelectingAll]=useState(false);
  const [selectionError,setSelectionError]=useState<string>();
  const [returnTargets,setReturnTargets]=useState<ScreeningReturnTarget[]>();
  const admissions=useQuery({queryKey:['matching','screening-candidates','admitted'],queryFn:()=>talentApi.screeningCandidates(),retry:false});
  const poolStates=useQuery({queryKey:['talent','pool-statuses',activeItems.map(item=>item.candidateId)],queryFn:()=>talentApi.poolStatuses(activeItems.map(item=>item.candidateId)),enabled:activeItems.length>0,retry:false});
  const candidateNames=new Map((poolStates.data??[]).map(state=>[state.candidateId,state.candidateName]));
  const candidateName=(candidateId:string)=>candidateNames.get(candidateId)??(poolStates.isPending?'加载候选人…':'候选人信息暂不可用');
  const searchTerm=search.trim().toLocaleLowerCase();
  const visibleItems=activeItems.filter(item=>(gradeFilter==='all'||screeningGrade(item)===gradeFilter)&&(!searchTerm||[
    candidateNames.get(item.candidateId)??'',
    ...item.details.dimensions.flatMap(dimension=>[dimension.explanation,...dimension.highlights]),
  ].join(' ').toLocaleLowerCase().includes(searchTerm)));
  const canShare=(candidateId:string)=>poolStates.data?.some(state=>state.candidateId===candidateId&&state.canScreenOut)===true;
  const targets=(rows:MatchResult[])=>rows.map(item=>({candidateId:item.candidateId,resumeVersionId:item.resumeVersionId}));
  const selectAll=async()=>{
    const revision=++selectionRevision.current;
    setSelectingAll(true);setSelectionError(undefined);
    try {
      const rows=await selectMatchResults(run.id,sources?async cursor=>{const page=await matchingApi.jobResults(run.jdVersionId,run.id,cursor);return {...page,items:page.items.map(item=>item.result)};}:undefined);
      if(selectionRevision.current===revision)setSelected(rows.filter(item=>!completedCandidateIds.has(item.candidateId)));
    } catch(error) {
      if(selectionRevision.current===revision)setSelectionError(errorMessage(error));
    } finally {setSelectingAll(false);}
  };
  const canManageScreening = Boolean(actor?.superAdmin || actor?.permissions.includes('matching.run'));
  const returnTarget=(item:MatchResult):ScreeningReturnTarget|undefined=>{
    if(!canManageScreening||admissions.isError)return;
    const admission=admissions.data?.find(value=>value.candidateId===item.candidateId&&value.resumeVersionId===item.resumeVersionId&&value.jdVersionId===run.jdVersionId&&value.admissionStatus==='ADMITTED');
    if(!admission?.admissionId||admission.admissionVersion==null)return;
    return {candidateId:item.candidateId,admissionId:admission.admissionId,expectedVersion:admission.admissionVersion};
  };
  const openReturn=(rows:MatchResult[])=>{
    const targets=rows.map(returnTarget);
    if(targets.some(item=>!item)){setSelectionError('部分简历已不在智能筛选中，请刷新列表后重新选择');return;}
    setReturnTargets(targets.filter((item):item is ScreeningReturnTarget=>Boolean(item)));
  };
  const returnTitle=admissions.isPending?'正在核对智能筛选状态':admissions.isError?'智能筛选状态读取失败，请重试':'仅未进入招聘流程且仍在智能筛选中的简历可以退回';
  const processingAccess = useQuery({queryKey:['recruitment','jd-processing-access',run.jdVersionId,actor?.userId],queryFn:()=>recruitmentApi.jdProcessingAccess(run.jdVersionId),enabled:Boolean(actor?.superAdmin || actor?.permissions.includes('recruitment.manage')),retry:false});
  const checksProcessingAccess=Boolean(actor?.superAdmin || actor?.permissions.includes('recruitment.manage'));
  const canAdvance = processingAccess.data?.processingAllowed === true;
  const isOwnedAdmission=(item:MatchResult)=>admissions.data?.some(value=>value.candidateId===item.candidateId&&value.resumeVersionId===item.resumeVersionId&&value.jdVersionId===run.jdVersionId&&value.admissionStatus==='ADMITTED'&&value.ownedByCurrentActor===true)===true;
  const canAdvanceItem=(item:MatchResult)=>canAdvance||isOwnedAdmission(item);
  const advanceTitle=processingAccess.isPending?'正在核对当前岗位处理权限':processingAccess.error?'岗位处理权限读取失败':canAdvance?undefined:'当前岗位不在你的处理范围内';
  const advanceTitleFor=(rows:MatchResult[])=>rows.every(isOwnedAdmission)?undefined:advanceTitle;
  const shareTitle=(candidateId:string)=>poolStates.isPending?'正在核对候选人共享库状态':poolStates.error?'候选人共享库状态读取失败':canShare(candidateId)?undefined:'当前候选人不可转入共享库，可能已有流程、已处理或不属于你';
  const refresh = useMutation({
    mutationFn: async () => {
      await Promise.all([
        ['matching','jobs',run.jdVersionId],
        ['matching','scheme-basis',run.jdVersionId],
        ['matching','job-results',run.jdVersionId],
        ['matching','job-results-summary',run.jdVersionId],
        ['matching','run',run.id],
        ['matching','results',run.id],
        ['matching','screening-candidates','admitted'],
        ['talent','pool-statuses',activeItems.map(item=>item.candidateId)],
        ['recruitment','jd-processing-access',run.jdVersionId,actor?.userId],
      ].map(queryKey=>queryClient.invalidateQueries({queryKey},{throwOnError:true})));
    },
  });

  return <>
    <ScreeningMetrics items={activeItems} summary={summary} value={gradeFilter} onChange={value=>{setGradeFilter(value);changeSelection([]);}} />
    <section className="matching-result-ledger" aria-label="匹配结果与人工决策">
      <div className="screening-result-toolbar">
        <Space wrap className="candidate-bulk-actions" role="group" aria-label="匹配结果批量操作"><span>已选 {selected.length} 人</span><Button loading={selectingAll} onClick={()=>{if(gradeFilter!=='all'||searchTerm)changeSelection(visibleItems);else void selectAll();}}>{gradeFilter!=='all'||searchTerm?'全选本页筛选结果':sources?'全选此依据全部候选人':'全选本次全部结果'}</Button><Button disabled={!selected.length||selectingAll} onClick={()=>changeSelection([])}>清空选择</Button>{checksProcessingAccess?<Button type="primary" aria-busy={processingAccess.isPending} disabled={!selected.length||selectingAll||selected.some(item=>!canAdvanceItem(item))} title={advanceTitleFor(selected)} onClick={()=>setAction({mode:'advance',targets:targets(selected)})}>批量推荐到招聘流程</Button>:null}<Button disabled={!selected.length||selectingAll} onClick={()=>setAction({mode:'share',targets:targets(selected)})}>批量转入共享库</Button>
        {canManageScreening?<>
          <Button disabled={!selected.length||selectingAll||selected.some(item=>!returnTarget(item))} title={returnTitle} onClick={()=>openReturn(selected)}>批量退回我的简历</Button>
          {admissions.error?<Button onClick={()=>void admissions.refetch()}>重试筛选状态</Button>:null}
        </>:null}</Space>
        <Button aria-label="刷新" icon={<ReloadOutlined aria-hidden="true"/>} aria-busy={refresh.isPending} loading={refresh.isPending} disabled={selectingAll} onClick={()=>{changeSelection([]);refresh.mutate();}}>刷新</Button>
        <div className="screening-result-filter">
          <Input allowClear aria-label="搜索本页候选人" placeholder="搜索本页姓名、技能、匹配关键词" value={search} onChange={event=>{setSearch(event.target.value);changeSelection([]);}} />
          <Button type="link" disabled={gradeFilter==='all'&&!search} onClick={()=>{setGradeFilter('all');setSearch('');changeSelection([]);}}>清除筛选</Button>
        </div>
      </div>
      {selectionError?<Alert occurrence={selectionError} type="error" title={selectionError}/>:null}
      {refresh.error?<Alert occurrence={refresh.error} type="error" title="智能筛选刷新失败" description={errorMessage(refresh.error)}/>:null}
      <Table<MatchResult> className="matching-score-table candidate-centered-table" tableLayout="fixed" rowSelection={{columnWidth:fitColumns?'3%':40,selectedRowKeys:selected.map(item=>item.resultId),preserveSelectedRowKeys:true,onChange:keys=>{const all=new Map([...selected,...activeItems].map(item=>[item.resultId,item]));if(keys.length>1000){setSelectionError('最多选择 1000 人，请分批操作');return;}changeSelection(keys.flatMap(key=>{const item=all.get(String(key));return item?[item]:[];}));}}} onHeaderRow={() => scrollableTableHeader(fitColumns ? "匹配结果表头" : "匹配结果表头，可横向滚动")} rowKey="resultId" pagination={false} dataSource={visibleItems} locale={{emptyText: '本页没有符合条件的候选人，请调整或清除筛选'}} columns={[
        { title: '#', width: fitColumns ? '3%' : 48, render: (_: unknown, item: MatchResult, index: number) => item.stableRank ?? index + 1 },
        { title: '候选人', width: fitColumns ? '8%' : 150, render: (_: unknown, item: MatchResult) => <span className="matching-person"><b>{candidateName(item.candidateId)}</b></span> },
        ...dimensionOrder.map((code) => ({ title: dimensionLabels[code], className: 'matching-dimension-cell', width: fitColumns ? '6%' : 112, render: (_: unknown, item: MatchResult) => <DimensionContribution item={item} code={code} /> })),
        { title: '综合参考分', width: fitColumns ? '8%' : 104, render: (_: unknown, item: MatchResult) => <span className="matching-total"><b>{item.referenceScore.toFixed(2)}</b><small>专业 {item.professionalScore.toFixed(2)} · +{item.bonusScore}</small></span> },
        { title: '必须项', width: fitColumns ? '7%' : 124, render: (_: unknown, item: MatchResult) => { const state = mustTierPresentation(item.mustTier); return <Tag color={state.color}>{state.label}</Tag>; } },
        { title: '分级', key: 'grade', width: fitColumns ? '6%' : 96, render: (_, item) => {
          const grade = screeningGrade(item);
          const colors = { strong: 'orange', consider: 'blue', reject: 'default', missing: 'green' } as const;
          return <Tag color={colors[grade]}>{screeningGradeLabels[grade]}</Tag>;
        } },
        { title: 'AI 评估', width: fitColumns ? '8%' : 156, render: (_: unknown, item: MatchResult) => { const assessment = assessmentPresentation(item); return <button type="button" className="matching-evidence-trigger matching-reliability" onClick={event => { setEvidenceOrigin(detailOrigin(event.currentTarget)); setEvidenceTarget(item); setEvidenceOpen(true); }} aria-label="查看候选人的评分详情"><b>{assessment.label}</b><small>查看五维评分与 AI 总结</small></button>; } },
        { title: '匹配说明', width: fitColumns ? '14%' : 300, render: (_: unknown, item: MatchResult) => <MatchSummary text={item.details.aiAssessment?.summary || item.details.dimensions.flatMap((dimension) => dimension.highlights).slice(0, 2).join('；') || item.details.dimensions.find((dimension) => dimension.enabled)?.explanation || '暂无 AI 总结'}/> },
        { title: '操作', className: 'table-action-cell', ...(!fitColumns && screens.md ? { fixed: 'right' as const } : {}), width: fitColumns ? '20%' : 268, render: (_: unknown, item: MatchResult) => <div className="matching-row-actions">
          <Button size="small" icon={<FileTextOutlined />} onClick={event => { setCandidateOrigin(detailOrigin(event.currentTarget)); setCandidateId(item.candidateId); setCandidateOpen(true); }}>查看简历</Button>
          {checksProcessingAccess?<Button size="small" type="primary" aria-busy={processingAccess.isPending} disabled={!canAdvanceItem(item)} title={advanceTitleFor([item])} onClick={()=>setAction({mode:'advance',targets:targets([item])})}>推荐到流程</Button>:null}
          <Button size="small" aria-busy={poolStates.isPending} disabled={!canShare(item.candidateId)} title={shareTitle(item.candidateId)} onClick={()=>setAction({mode:'share',targets:targets([item])})}>转入共享库</Button>
          {canManageScreening?<Button size="small" disabled={!returnTarget(item)} title={returnTitle} onClick={()=>openReturn([item])}>退回我的简历</Button>:null}
        </div> },
      ]} scroll={fitColumns ? {} : { x: 1846 }} />
    </section>

    <Modal size="wide" title={<>{evidenceTarget ? candidateName(evidenceTarget.candidateId) : '候选人'} · 评分详情</>} open={evidenceOpen} motionFrom={evidenceOrigin} onCancel={() => setEvidenceOpen(false)} afterOpenChange={open => { if (!open) setEvidenceTarget(undefined); }} footer={null} destroyOnHidden>
      <p className="dialog-description">{(job ? jobLabel(job.title, job.code) : undefined) ?? <MatchRunJob jdVersionId={run.jdVersionId}/>} · 总分 {evidenceTarget?.referenceScore.toFixed(2)} · 专业分 {evidenceTarget?.professionalScore.toFixed(2)} · 附加分 +{evidenceTarget?.bonusScore}。</p>
      {evidenceTarget ? <MatchScorePanel item={evidenceTarget} /> : null}
    </Modal>

    {candidateId?<CandidateDialog compact key={candidateId} candidateId={candidateId} open={candidateOpen} motionFrom={candidateOrigin} canDownload={canDownloadResume(actor)} onClose={()=>setCandidateOpen(false)}/>:null}

    {action?<CandidateActionDialog open mode={action.mode} candidates={action.targets} jdVersionId={run.jdVersionId} allowOwnedResumeRecommendation={action.mode==='advance'&&action.targets.every(target=>activeItems.some(item=>item.candidateId===target.candidateId&&isOwnedAdmission(item)))} onClose={()=>setAction(undefined)} onCompleted={completeCandidates}/>:null}
    {returnTargets?<ScreeningReturnDialog targets={returnTargets} onClose={()=>setReturnTargets(undefined)} onCompleted={completeCandidates}/>:null}
  </>;
}

function MatchScorePanel({ item }: { item: MatchResult }) {
  return <section className="matching-evidence-panel">
    <div className="matching-evidence-metrics">
      <article><span>总分</span><b>{item.referenceScore.toFixed(2)}</b></article>
      <article><span>专业分</span><b>{item.professionalScore.toFixed(2)}</b></article>
      <article><span>附加分</span><b>+{item.bonusScore}</b></article>
    </div>
    <Card size="small" title="AI 总结"><p>{item.details.aiAssessment?.summary || '历史评分未保存 AI 总结，请重新评分。'}</p></Card>
    <Row gutter={[10, 10]}>{dimensionOrder.map((code) => {
      const dimension = item.details.dimensions.find((value) => value.code === code);
      return <Col xs={24} md={12} xl={8} key={code}><Card size="small" className="matching-evidence-dimension" title={dimensionLabels[code]} extra={<Tag color={!dimension?.enabled ? 'default' : dimension.status === 'KNOWN' ? 'blue' : 'gold'}>{!dimension?.enabled ? '旧版未评·请重评' : dimension.status === 'KNOWN' ? `${dimension.score.toFixed(1)} 分` : '信息不足'}</Tag>}>
        <p>{dimension?.explanation ?? '历史版本未评估该维度，请重新评分'}</p>
        <div className="matching-evidence-highlights">{dimension?.highlights.map((value) => <Tag key={value}>{value}</Tag>)}</div>
      </Card></Col>;
    })}</Row>
    <Card size="small" title="附加要求事实" className="matching-criteria-evidence">
      {item.details.criteria.length ? <Descriptions size="small" column={1} items={item.details.criteria.map((criterion) => { const state = criterionPresentation(criterion); return { key: criterion.definitionId, label: `${criterionLabels[criterion.code as keyof typeof criterionLabels] ?? criterion.code} · ${displayEnum(criterion.level)}`, children: <Space wrap><Tag color={state.color}>{state.label}</Tag><span>实际：{criterion.actual.join('、') || '信息不足'} · {criterion.level === 'DISPLAY_ONLY' ? '仅供参考，不参与满足判断' : `要求：${criterion.expected.join('、') || '未配置'}`} · {criterion.reason}</span></Space> }; })} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次没有附加要求" />}
    </Card>
  </section>;
}

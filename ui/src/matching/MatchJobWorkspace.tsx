import { CompactPagination } from '../components/CompactPagination';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { hashKey, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Empty, Skeleton } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiProblemError } from '../api/problem';
import { matchingApi, type JobMatchResult, type MatchRun, type ScreeningSummary } from './api';
import { selectScoringBasis, type ScoringBasisSelection } from './selectScoringBasis';

type CachedJobs=Awaited<ReturnType<typeof matchingApi.jobs>>&{accessRevoked?:true};

export function MatchJobWorkspace({loading=false,basisSelection,schemeLabel,jdVersionId,selectedJob,basisRunId,renderResults}:{loading?:boolean;basisSelection?:ScoringBasisSelection|undefined;schemeLabel:string;jdVersionId?:string|undefined;selectedJob?:string|undefined;basisRunId?:string|undefined;renderResults:(items:JobMatchResult[],run:MatchRun,summary:ScreeningSummary|null)=>ReactNode}) {
  const client=useQueryClient();
  const previousSnapshot=useRef<{job:string|undefined;value:string|undefined}>({job:undefined,value:undefined});
  const activeJob=selectedJob??jdVersionId;
  const jobsKey=['matching','jobs',activeJob];
  const jobs=useQuery<CachedJobs>({queryKey:jobsKey,queryFn:async({signal})=>{
    try{
      return await matchingApi.jobs(activeJob,undefined,signal);
    }catch(error){
      if(!signal.aborted&&error instanceof ApiProblemError&&[401,403].includes(error.problem.status))client.setQueryData<CachedJobs>(jobsKey,current=>current?{...current,accessRevoked:true}:current);
      throw error;
    }
  },enabled:Boolean(activeJob),refetchInterval:query=>query.state.data?.items.some(item=>item.activeRunCount>0)?3_000:false});
  const jobsDenied=jobs.error instanceof ApiProblemError&&[401,403].includes(jobs.error.problem.status);
  const jobsAccessBlocked=jobsDenied||jobs.data?.accessRevoked===true;
  // 汇总包含新增运行、成功/失败进度和活跃数；相同快照无需重新读取依据和结果。
  const snapshot=jobs.data ? JSON.stringify(jobs.data.items.find(item=>item.jdVersionId===activeJob)??null) : undefined;
  useEffect(()=>{
    const previous=previousSnapshot.current;
    previousSnapshot.current={job:activeJob,value:snapshot};
    if(!activeJob||snapshot===undefined||previous.job!==activeJob||previous.value===undefined||previous.value===snapshot)return;
    void client.invalidateQueries({queryKey:['matching','scheme-basis',activeJob]});
    void client.invalidateQueries({queryKey:['matching','job-results',activeJob]});
    void client.invalidateQueries({queryKey:['matching','job-results-summary',activeJob]});
  },[activeJob,client,snapshot]);
  const retryJobs=async()=>{
    const result=await jobs.refetch({cancelRefetch:false});
    if(result.isError)return;
    await Promise.all([
      client.invalidateQueries({queryKey:['matching','scheme-basis',activeJob]}),
      client.invalidateQueries({queryKey:['matching','job-results',activeJob]}),
      client.invalidateQueries({queryKey:['matching','job-results-summary',activeJob]}),
    ]);
  };
  if(loading)return <ScreeningResultsLoading/>;
  if(!activeJob)return <Empty description="请先选择招聘岗位"/>;
  return <>
    {jobs.error?<Alert occurrence={jobs.error} type={jobsDenied?'info':'error'} showIcon title={jobsDenied?'暂无岗位评分的查看权限':'岗位评分读取失败'} action={<Button aria-label="重试岗位评分" loading={jobs.isFetching} disabled={jobs.isFetching} onClick={()=>void retryJobs()}>重试岗位评分</Button>}/>:null}
    {jobs.isPending?<ScreeningResultsLoading/>:null}
    {!jobsAccessBlocked&&jobs.data?<JobResults basisSelection={basisSelection} schemeLabel={schemeLabel} key={activeJob} jdVersionId={activeJob} basisRunId={basisRunId} renderResults={renderResults}/>:null}
  </>;
}

function JobResults({basisSelection,schemeLabel,jdVersionId,basisRunId,renderResults}:{basisSelection?:ScoringBasisSelection|undefined;schemeLabel:string;jdVersionId:string;basisRunId?:string|undefined;renderResults:(items:JobMatchResult[],run:MatchRun,summary:ScreeningSummary|null)=>ReactNode}) {
  const automatic=Boolean(basisSelection)&&!basisRunId;
  const selectionKey=['matching','scheme-basis',jdVersionId,basisSelection];
  const selectionHash=hashKey(selectionKey);
  const [retryingSelection,setRetryingSelection]=useState<string>();
  // 相同评分快照可复用判断依据；重连时仍需重新核对读取权限。
  const selectedBasis=useQuery({queryKey:selectionKey,queryFn:({signal})=>selectScoringBasis(jdVersionId,basisSelection!,signal),enabled:automatic,retry:false,staleTime:Infinity,refetchOnReconnect:'always'});
  const selectionError=automatic?selectedBasis.error:null;
  const denied=selectionError instanceof ApiProblemError && [401,403].includes(selectionError.problem.status);
  const selected=basisRunId??(automatic&&!denied?selectedBasis.data:undefined);
  return <>
    {selectionError || (automatic && retryingSelection===selectionHash)?<Alert type={denied?'info':'warning'} showIcon title={denied?'暂无判断依据的查看权限':'判断依据读取失败'} action={<Button aria-label="重试判断依据" aria-busy={selectedBasis.isFetching} loading={selectedBasis.isFetching} disabled={selectedBasis.isFetching} onClick={()=>{
      setRetryingSelection(selectionHash);
      void selectedBasis.refetch({cancelRefetch:false}).finally(()=>setRetryingSelection(current=>current===selectionHash?undefined:current));
    }}>重试判断依据</Button>}/>:null}
    {selected?<BasisResults key={`${jdVersionId}:${selected}`} jdVersionId={jdVersionId} basisRunId={selected} renderResults={renderResults}/>:(automatic && selectedBasis.isPending)?<ScreeningResultsLoading/>:!selectionError?<Empty description={`暂无${schemeLabel}的评分结果`}/>:null}
  </>;
}

function BasisResults({jdVersionId,basisRunId,renderResults}:{jdVersionId:string;basisRunId:string;renderResults:(items:JobMatchResult[],run:MatchRun,summary:ScreeningSummary|null)=>ReactNode}) {
  const [cursors,setCursors]=useState<string[]>([]);
  const [pageSize,setPageSize]=useState(20);
  const [retrying,setRetrying]=useState(false);
  const run=useQuery({queryKey:['matching','run',basisRunId],queryFn:()=>matchingApi.run(basisRunId),retry:false});
  const results=useQuery({queryKey:['matching','job-results',jdVersionId,basisRunId,cursors.at(-1),pageSize],queryFn:()=>matchingApi.jobResults(jdVersionId,basisRunId,cursors.at(-1),pageSize),retry:false});
  const summary=useQuery({queryKey:['matching','job-results-summary',jdVersionId,basisRunId],queryFn:()=>matchingApi.jobResultsSummary(jdVersionId,basisRunId),retry:false});
  const denied=[run.error,results.error,summary.error].some(error=>error instanceof ApiProblemError && [401,403].includes(error.problem.status));
  const fetching=run.isFetching||results.isFetching;
  return <div className="screening-basis-results" aria-busy={fetching}>
    {run.error||results.error||denied||retrying?<Alert type={denied?'info':'warning'} showIcon title={denied?'暂无岗位结果的查看权限':'岗位结果读取失败'} action={<Button aria-label="重试岗位结果" aria-busy={fetching} loading={fetching} disabled={fetching} onClick={()=>{
      setRetrying(true);
      void Promise.all([run.refetch({cancelRefetch:false}),results.refetch({cancelRefetch:false}),summary.refetch({cancelRefetch:false})]).finally(()=>setRetrying(false));
    }}>重试岗位结果</Button>}/>:null}
    {summary.error&&!denied?<Alert occurrence={summary.error} type="warning" title="岗位统计读取失败" action={<Button onClick={()=>void summary.refetch()} loading={summary.isFetching}>重试统计</Button>}/>:null}
    {run.isPending||results.isPending?<ScreeningResultsLoading/>:null}
    {!denied&&run.data&&results.data?renderResults(results.data.items,run.data,summary.isError?null:summary.data??null):null}
    {!denied && run.data && results.data ? <CompactPagination label="岗位结果分页" page={cursors.length+1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursors([]);}} loading={fetching} hasPrevious={cursors.length>0} hasNext={Boolean(results.data?.hasMore&&results.data.nextCursor&&!run.error&&!results.error)} onPrevious={()=>setCursors(current=>current.slice(0,-1))} onNext={()=>{const next=results.data?.nextCursor;if(next)setCursors(current=>[...current,next]);}} onPageChange={target=>setCursors(current=>current.slice(0,target-1))} maxPage={cursors.length+1} previousLabel="上一页结果" nextLabel="下一页结果" note="同一依据下候选人去重" {...(summary.data&&!summary.isError?{total:summary.data.total}:{totalStatus:summary.isError?'error' as const:'pending' as const,onRetryTotal:()=>void summary.refetch()})} /> : null}
  </div>;
}

export function ScreeningResultsLoading() {
  return <div className="screening-results-loading" role="status" aria-label="正在加载智能筛选">
    <div aria-hidden="true"><Skeleton active={false} title={{width:'28%'}} paragraph={{rows:6}} /></div>
  </div>;
}

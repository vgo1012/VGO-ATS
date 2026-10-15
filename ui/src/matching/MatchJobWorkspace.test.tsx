import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { matchingApi, type MatchJob, type MatchRun } from './api';
import { MatchJobWorkspace } from './MatchJobWorkspace';
import type { ScoringBasisSelection } from './selectScoringBasis';

const job=(overrides:Partial<MatchJob>={}):MatchJob=>({jdVersionId:'jd-a',latestRunId:'run-a',lastRunAt:'2026-09-11T00:00:00Z',runCount:1,evaluationCount:10,succeededCount:1,failedCount:0,candidateCount:10,scoredCandidateCount:1,activeRunCount:1,...overrides});
const page=(value:MatchJob)=>({items:[value],hasMore:false,nextCursor:null});
const run:MatchRun={id:'run-a',taskId:'task-a',jdVersionId:'jd-a',jdFactSnapshotId:'fact',planVersionId:'plan',weightProfileVersionId:'weight',personalPreferenceVersionId:null,selectionSnapshotId:'selection',sortMode:'REFERENCE_SCORE',status:'RUNNING',totalCount:10,succeededCount:1,failedCount:0,errorCode:null,version:1,createdAt:'2026-09-11T00:00:00Z',startedAt:null,finishedAt:null,updatedAt:'2026-09-11T00:00:00Z'};
const selection:ScoringBasisSelection={planVersionId:'plan',selectedCriterionIds:[],sortMode:'REFERENCE_SCORE',profile:{profile:{id:'profile',ownerUserId:'hr',name:'个人',status:'ACTIVE',currentVersionNo:1,version:1,updatedAt:''},currentVersion:{id:'weight',versionNo:1,skillsWeight:100,roleWeight:0,yearsWeight:0,responsibilityWeight:0,industryProjectWeight:0}}};
const denied=(status:number)=>new ApiProblemError({status,code:'ACCESS_DENIED',title:'拒绝访问',detail:'岗位权限已撤销',recoverable:false});
const clients:QueryClient[]=[];
afterEach(()=>{cleanup();clients.forEach(client=>client.clear());clients.length=0;vi.restoreAllMocks();});

function mount(){
  const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:60_000}}});
  clients.push(client);
  vi.spyOn(matchingApi,'run').mockResolvedValue(run);
  const summary=vi.spyOn(matchingApi,'jobResultsSummary').mockResolvedValue({total:41,strong:12,consider:24,reject:4,missing:1});
  const results=vi.spyOn(matchingApi,'jobResults').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(matchingApi,'bases').mockResolvedValue({items:[{basisRunId:'run-a',jdVersionId:'jd-a',basisKey:'basis',planVersionId:'plan',weightProfileVersionId:'weight',sortMode:'REFERENCE_SCORE',lastRunAt:'',runCount:1,candidateCount:1,scoredCandidateCount:1,skillsWeight:100,roleWeight:0,yearsWeight:0,responsibilityWeight:0,industryProjectWeight:0,selectedCriterionIds:[],personalScreeningSettings:null}],hasMore:false,nextCursor:null});
  const content=(jd='jd-a',basis:string|undefined='run-a',automatic=false)=><QueryClientProvider client={client}><MatchJobWorkspace selectedJob={jd} {...(automatic?{basisSelection:selection}:{basisRunId:basis})} schemeLabel="公司默认" renderResults={()=> <span>结果已加载</span>}/></QueryClientProvider>;
  const view=render(content());
  const poll=async()=>{await act(async()=>{await client.refetchQueries({queryKey:['matching','jobs'],type:'active'});});};
  return {client,results,summary,poll,view,content};
}

it('相同岗位快照连续轮询不重取结果，运行中新增成功结果仍刷新',async()=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {results,poll}=mount();
  await screen.findByText('结果已加载');
  await poll();await poll();
  expect(load).toHaveBeenCalledTimes(3);
  expect(results).toHaveBeenCalledTimes(1);
  load.mockResolvedValue(page(job({succeededCount:2,scoredCandidateCount:2})));
  await poll();
  await waitFor(()=>expect(results).toHaveBeenCalledTimes(2));
  await poll();
  expect(results).toHaveBeenCalledTimes(2);
});

it.each(['explicit','automatic'] as const)('父岗位拒绝后隐藏缓存的 %s basis 结果，慢重试期间保持隐藏并在成功后恢复',async mode=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {client,view,content}=mount();
  if(mode==='automatic')view.rerender(content('jd-a',undefined,true));
  await screen.findByText('结果已加载');
  load.mockRejectedValue(denied(403));
  await act(()=>client.invalidateQueries({queryKey:['matching','jobs','jd-a']}));
  await screen.findByText('暂无岗位评分的查看权限');
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();
  let reject!:(error:unknown)=>void;
  load.mockImplementation(()=>new Promise((_,failed)=>{reject=failed;}));
  fireEvent.click(screen.getByRole('button',{name:'重试岗位评分'}));
  await waitFor(()=>expect(load).toHaveBeenCalledTimes(3));
  expect(screen.getByRole('button',{name:/重试岗位评分/})).toBeDisabled();
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();
  await act(()=>{reject(new ApiProblemError({status:503,code:'SERVICE_UNAVAILABLE',title:'暂不可用',detail:'稍后重试',recoverable:true}));return Promise.resolve();});
  await screen.findByText('岗位评分读取失败');
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();
  load.mockResolvedValue(page(job()));
  fireEvent.click(screen.getByRole('button',{name:'重试岗位评分'}));
  await screen.findByText('结果已加载');
});

it('父岗位临时失败时保留已授权的显式结果',async()=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {client}=mount();
  await screen.findByText('结果已加载');
  load.mockRejectedValue(new ApiProblemError({status:503,code:'SERVICE_UNAVAILABLE',title:'暂不可用',detail:'稍后重试',recoverable:true}));
  await act(()=>client.invalidateQueries({queryKey:['matching','jobs','jd-a']}));
  expect(screen.getByText('结果已加载')).toBeInTheDocument();
});

it('父岗位拒绝后再遇临时失败，使用同一查询缓存重挂时仍不得显示旧结果',async()=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {client,view,content}=mount();
  await screen.findByText('结果已加载');
  load.mockRejectedValue(denied(403));
  await act(()=>client.invalidateQueries({queryKey:['matching','jobs','jd-a']}));
  await screen.findByText('暂无岗位评分的查看权限');
  load.mockRejectedValue(new ApiProblemError({status:503,code:'SERVICE_UNAVAILABLE',title:'暂不可用',detail:'稍后重试',recoverable:true}));
  fireEvent.click(screen.getByRole('button',{name:'重试岗位评分'}));
  await screen.findByText('岗位评分读取失败');
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();

  view.unmount();
  let resolve!:(value:ReturnType<typeof page>)=>void;
  load.mockImplementation(()=>new Promise(done=>{resolve=done;}));
  render(content());
  await waitFor(()=>expect(screen.queryByText('结果已加载')).not.toBeInTheDocument());
  await act(()=>{resolve(page(job()));return Promise.resolve();});
  await screen.findByText('结果已加载');
});

it('首次父岗位查询完成前不挂载缓存的子结果',async()=>{
  let resolve!:(value:ReturnType<typeof page>)=>void;
  vi.spyOn(matchingApi,'jobs').mockImplementation(()=>new Promise(done=>{resolve=done;}));
  const {results}=mount();
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();
  expect(results).not.toHaveBeenCalled();
  await act(()=>{resolve(page(job()));return Promise.resolve();});
  await screen.findByText('结果已加载');
  expect(results).toHaveBeenCalledTimes(1);
});

it('失败进度、终态和新运行变化均刷新，终态后的相同数据不重复刷新',async()=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {results,poll}=mount();
  await screen.findByText('结果已加载');
  for(const [index,change] of [{failedCount:1},{failedCount:1,activeRunCount:0},{failedCount:1,activeRunCount:1,runCount:2,latestRunId:'run-b'}].entries()){
    load.mockResolvedValue(page(job(change)));
    await poll();
    await waitFor(()=>expect(results).toHaveBeenCalledTimes(index+2));
    await poll();
    expect(results).toHaveBeenCalledTimes(index+2);
  }
});

it('切换岗位不将旧岗位快照当作变化，切换依据读取新结果',async()=>{
  vi.spyOn(matchingApi,'jobs').mockImplementation(id=>Promise.resolve(page(job({jdVersionId:id!,latestRunId:id==='jd-a'?'run-a':'run-b'}))));
  const {client,results,view,content,poll}=mount();
  await screen.findByText('结果已加载');
  const invalidate=vi.spyOn(client,'invalidateQueries');
  view.rerender(content('jd-b','run-b'));
  await waitFor(()=>expect(results).toHaveBeenCalledWith('jd-b','run-b',undefined,20));
  await poll();
  expect(invalidate).not.toHaveBeenCalled();
  view.rerender(content('jd-b','run-c'));
  await waitFor(()=>expect(results).toHaveBeenCalledWith('jd-b','run-c',undefined,20));
  expect(invalidate).not.toHaveBeenCalled();
});

it('轮询失败后人工重试即使快照未变化也能刷新结果',async()=>{
  const load=vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job()));
  const {results,poll}=mount();
  await screen.findByText('结果已加载');
  load.mockRejectedValueOnce(new Error('offline'));
  await poll();
  fireEvent.click(await screen.findByRole('button',{name:'重试岗位评分'}));
  await waitFor(()=>expect(results).toHaveBeenCalledTimes(2));
});


it('翻页保留岗位总数和总页数，汇总失败不退回当前页数量且可重试',async()=>{
  vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job({activeRunCount:0})));
  const {client,results,summary}=mount();
  results.mockResolvedValue({items:[],hasMore:true,nextCursor:'page-two'});
  await screen.findByText('条/共41条记录');
  await act(()=>client.invalidateQueries({queryKey:['matching','job-results']}));
  fireEvent.click(screen.getByRole('button',{name:'下一页结果'}));
  await waitFor(()=>expect(results).toHaveBeenLastCalledWith('jd-a','run-a','page-two',20));
  expect(screen.getByText('条/共41条记录')).toBeVisible();
  expect(screen.getByText('共3页')).toBeVisible();
  expect(summary).toHaveBeenCalledTimes(1);
  summary.mockRejectedValueOnce(new Error('统计暂不可用'));
  await act(()=>client.invalidateQueries({queryKey:['matching','job-results-summary']}));
  await screen.findByText('条/统计失败');
  expect(screen.queryByText('条/共41条记录')).not.toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button',{name:'重试统计'})[0]!);
  await screen.findByText('条/共41条记录');
});

it('统计接口拒绝访问时也隐藏缓存结果和总数',async()=>{
  vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job({activeRunCount:0})));
  const {client,summary}=mount();
  await screen.findByText('条/共41条记录');
  summary.mockRejectedValue(denied(403));
  await act(()=>client.invalidateQueries({queryKey:['matching','job-results-summary']}));
  await screen.findByText('暂无岗位结果的查看权限');
  expect(screen.queryByText('结果已加载')).not.toBeInTheDocument();
  expect(screen.queryByText('条/共41条记录')).not.toBeInTheDocument();
});

it('调整每页条数从首页重新请求且复用完整岗位汇总',async()=>{
  vi.spyOn(matchingApi,'jobs').mockResolvedValue(page(job({activeRunCount:0})));
  const {client,results,summary}=mount();
  results.mockResolvedValue({items:[],hasMore:true,nextCursor:'page-two'});
  await screen.findByText('条/共41条记录');
  await act(()=>client.invalidateQueries({queryKey:['matching','job-results']}));
  fireEvent.click(screen.getByRole('button',{name:'下一页结果'}));
  await waitFor(()=>expect(results).toHaveBeenLastCalledWith('jd-a','run-a','page-two',20));
  const input=screen.getByRole('spinbutton',{name:'每页条数'});
  fireEvent.change(input,{target:{value:'30'}});
  fireEvent.blur(input);
  await waitFor(()=>expect(results).toHaveBeenLastCalledWith('jd-a','run-a',undefined,30));
  expect(screen.getByRole('spinbutton',{name:'页码'})).toHaveValue(1);
  expect(screen.getByText('共2页')).toBeVisible();
  expect(summary).toHaveBeenCalledTimes(1);
});

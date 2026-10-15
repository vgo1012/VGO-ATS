import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { CandidateLibrary } from './CandidateLibrary';
import { talentApi, type Candidate } from './api';

vi.mock('../auth/session',()=>({useSession:()=>({actor:null})}));

vi.mock('../demand/JdSelector',()=>({JdSelector:()=> <span>目标岗位选择</span>}));
vi.mock('../recruitment/CandidateActionDialog',()=>({CandidateActionDialog:({open,mode,candidates,jdVersionId}:{open:boolean;mode:string;candidates:Array<{candidateId:string;resumeVersionId?:string}>;jdVersionId?:string})=>open?<div role="dialog" aria-label="公共批量操作" data-jd-version-id={jdVersionId??''}>{mode}:{candidates.map(row=>`${row.candidateId}${row.resumeVersionId?`/${row.resumeVersionId}`:''}`).join(',')}</div>:null}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string})=><span>{id}</span>}));

const renderLibrary=(items:Candidate[])=>render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={items} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} page={1} hasMore={false} onNext={()=>undefined} onPrevious={()=>undefined}/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
const emptySummaries=(items:Candidate[])=>items.map(person=>({candidateId:person.id,resumeVersion:null,source:null,facts:null,factsStatus:'NO_RESUME' as const}));

const shareSummary=(candidateId:string,version:string)=>({candidateId,resumeVersion:{id:version,resumeId:`resume-${candidateId}`,intakeId:`intake-${candidateId}`,versionNo:1,originalFileName:`${version}.pdf`,mediaType:'application/pdf',sizeBytes:100,contentSha256:'hash',status:'ACTIVE',createdAt:'2026-09-01T00:00:00Z'},source:null,facts:null,factsStatus:'NOT_READY' as const});

function shareSelectionView(initialVersions:Record<string,string|undefined>) {
  const people=['a','b'].map(id=>({id,fullName:`候选人${id}`,ownerUserId:'hr',version:1} as Candidate));
  const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});
  for(const person of people){
    client.setQueryData(['talent','candidate-resume-summaries',[person.id]],initialVersions[person.id]?[shareSummary(person.id,initialVersions[person.id]!)]:emptySummaries([person]));
    client.setQueryData(['talent','pool-statuses',[person.id]],[{candidateId:person.id,resumeVersionId:'pool-new-version',stages:[]}]);
  }
  const View=({index=0,query=''}:{index?:number;query?:string})=><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={[people[index]!]} page={index+1} loading={false} query={query} onQuery={()=>undefined} onOpen={()=>undefined} hasMore onNext={()=>undefined} onPrevious={()=>undefined}/>;
  const wrapper=({children}:{children:React.ReactNode})=><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider></App></ConfigProvider>;
  const view=render(<View/>,{wrapper});
  return {client,view,View};
}

it('numbers candidates across pages and resets on the first page',()=>{
  const {view,View}=shareSelectionView({a:'a-v1',b:'b-v1'});
  expect(screen.getByRole('columnheader',{name:'#'})).toBeVisible();
  expect(screen.getByRole('cell',{name:'1'})).toBeVisible();
  view.rerender(<View index={1}/>);
  expect(screen.getByRole('cell',{name:'21'})).toBeVisible();
  view.rerender(<View/>);
  expect(screen.getByRole('cell',{name:'1'})).toBeVisible();
});

it('freezes displayed versions across pages instead of adopting newer background summaries',async()=>{
  const {client,view,View}=shareSelectionView({a:'a-v1',b:'b-v1'});
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  await act(()=>{client.setQueryData(['talent','candidate-resume-summaries',['a']],[shareSummary('a','a-v2')]);return Promise.resolve();});
  view.rerender(<View index={1}/>);
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'筛选结束入共享库'}));
  expect(screen.getByRole('dialog',{name:'公共批量操作'})).toHaveTextContent('share:a/a-v1,b/b-v1');
});

it('does not fill an unreviewed missing version until the candidate is selected again',async()=>{
  const {client}=shareSelectionView({a:undefined});
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  await act(()=>{client.setQueryData(['talent','candidate-resume-summaries',['a']],[shareSummary('a','a-v2')]);return Promise.resolve();});
  fireEvent.click(screen.getByRole('button',{name:'筛选结束入共享库'}));
  expect(await screen.findByText('勾选时简历版本尚未读取，请等待摘要加载完成后重新勾选。')).toBeVisible();
  expect(screen.queryByRole('dialog',{name:'公共批量操作'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'筛选结束入共享库'}));
  expect(screen.getByRole('dialog',{name:'公共批量操作'})).toHaveTextContent('share:a/a-v2');
});

it.each(['clear','filter'] as const)('discards frozen share versions after %s',async mode=>{
  const {client,view,View}=shareSelectionView({a:'a-v1'});
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  await act(()=>{client.setQueryData(['talent','candidate-resume-summaries',['a']],[shareSummary('a','a-v2')]);return Promise.resolve();});
  if(mode==='clear')fireEvent.click(screen.getByRole('button',{name:'清空选择'}));
  else view.rerender(<View query="新的筛选"/>);
  expect(screen.getByRole('checkbox',{name:'全选本页候选人'})).not.toBeChecked();
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'筛选结束入共享库'}));
  expect(screen.getByRole('dialog',{name:'公共批量操作'})).toHaveTextContent('share:a/a-v2');
});

afterEach(()=>{cleanup();vi.restoreAllMocks();});

it.each([
  {jdId:'jd-a', scoredJobs:['a','b'], expectedJob:'a'},
  {jdId:'jd-a', scoredJobs:['b'], expectedJob:undefined},
  {jdId:undefined, scoredJobs:['a','b'], expectedJob:undefined},
])('respects the selected job when screening $scoredJobs with filter $jdId', async ({jdId,scoredJobs,expectedJob})=>{
  const person={id:'person',fullName:'多岗位候选人',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries([person]));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:person.id,resumeVersionId:'resume',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:['a','b'].map(job=>({jdId:`jd-${job}`,jdVersionId:`version-${job}`,title:`岗位${job}`,versionNo:1,resumeVersionId:'resume',status:'PENDING',stage:null,applicationId:null,matchRunId:null}))}]);
  vi.spyOn(talentApi,'screeningCandidates').mockResolvedValue(scoredJobs.map(job=>({candidateId:person.id,resumeVersionId:'resume',jdVersionId:`version-${job}`,defaultMatchRunId:`run-${job}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null})));
  const admit=vi.spyOn(talentApi,'admitScreening').mockResolvedValue({requestedCount:1,admittedCount:1,alreadyAdmittedCount:0,items:[]});
  const Location=()=>{const location=useLocation();return <output aria-label="当前地址">{location.pathname+location.search}</output>;};
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/talents']}><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={[person]} {...(jdId?{jdId}:{})} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} page={1} hasMore={false} onNext={()=>undefined} onPrevious={()=>undefined}/><Location/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'智能筛选'}));
  if(expectedJob){
    await waitFor(()=>expect(admit).toHaveBeenCalledWith([{candidateId:'person',resumeVersionId:'resume',jdVersionId:`version-${expectedJob}`,defaultMatchRunId:`run-${expectedJob}`} ]));
    await waitFor(()=>expect(screen.getByLabelText('当前地址')).toHaveTextContent(`/matching?scoreJob=version-${expectedJob}`));
  }else{
    expect(await screen.findByText(jdId?'所选简历尚未完成公司默认评分，请刷新后重试':'所选简历关联了多个已评分岗位，无法确定唯一岗位')).toBeInTheDocument();
    expect(admit).not.toHaveBeenCalled();
    expect(screen.getByLabelText('当前地址')).toHaveTextContent('/talents');
    expect(screen.getByRole('checkbox',{name:'全选本页候选人'})).toBeChecked();
  }
});

it('keeps an empty incomplete scan resumable without claiming no candidates exist',()=>{
  const next=vi.fn();
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={[]} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} page={1} hasMore scanIncomplete onNext={next} onPrevious={()=>undefined}/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  expect(screen.getByText('仍有候选人待检索，请继续检索')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'继续检索'}));
  expect(next).toHaveBeenCalledOnce();
  expect(screen.queryByText('暂无候选人，请先上传简历并确认入库')).not.toBeInTheDocument();
});

it('marks identity conflicts beside the candidate without adding a separate conflict view',async()=>{
  const people=[{id:'a',fullName:'需核验候选人',ownerUserId:'hr',version:1},{id:'b',fullName:'正常候选人',ownerUserId:'hr',version:1}] as Candidate[];
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries(people));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue(people.map(person=>({candidateId:person.id,resumeVersionId:null,libraryEligible:false,screenedOut:false,canScreenOut:false,stages:[],openIdentityConflictCount:person.id==='a'?2:0})));
  renderLibrary(people);
  expect(await screen.findByText('身份待核验 · 2 项 · 查看原因')).toBeInTheDocument();
  expect(screen.getAllByText(/身份待核验/)).toHaveLength(1);
  expect(screen.queryByRole('tab',{name:/冲突/})).not.toBeInTheDocument();
});

it('omits redundant process states and delegates sharing with the displayed resume version',async()=>{
  const items=[{id:'a',fullName:'待筛选候选人',ownerUserId:'hr',version:3},{id:'b',fullName:'面试候选人',ownerUserId:'peer',version:1}] as Candidate[];
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(items.map(person=>({...emptySummaries([person])[0]!,resumeVersion:{id:`displayed-${person.id}`,createdAt:'2026-09-01T00:00:00Z'} as NonNullable<Awaited<ReturnType<typeof talentApi.candidateResumeSummaries>>[number]['resumeVersion']>})));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:'a',resumeVersionId:'v-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]},{candidateId:'b',resumeVersionId:'v-b',libraryEligible:false,screenedOut:false,canScreenOut:false,stages:['IN_PROCESS']}]);
  const screenOut=vi.spyOn(talentApi,'screenOut');
  renderLibrary(items);
  expect(screen.queryByRole('columnheader',{name:'处理状态'})).not.toBeInTheDocument();
  expect(screen.queryByText('待归属人筛选')).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader',{name:'AI / 简历复核'})).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader',{name:'序号'})).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader',{name:'所在地'})).not.toBeInTheDocument();
  expect(screen.queryByRole('columnheader',{name:'最近公司'})).not.toBeInTheDocument();
  await screen.findAllByText('摘要暂不可用');
  fireEvent.click(screen.getAllByRole('checkbox')[1]!);
  fireEvent.click(screen.getByRole('button',{name:'筛选结束入共享库'}));
  expect(screenOut).not.toHaveBeenCalled();
  expect(screen.getByRole('dialog',{name:'公共批量操作'})).toHaveTextContent('share:a/displayed-a');
});

it('opens direct recommendation with the current resume bound job without asking for another job',async()=>{
  const person={id:'a',fullName:'已绑定岗位候选人',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries([person]));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:'a',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:'jd',jdVersionId:'jd-version',title:'已绑定岗位',versionNo:1,resumeVersionId:'resume-a',status:'AI_SCREENED',stage:null,applicationId:null,matchRunId:'run'}]}]);
  renderLibrary([person]);
  await screen.findAllByText('已绑定岗位');
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'直接推荐'}));
  const dialog=await screen.findByRole('dialog',{name:'公共批量操作'});
  expect(dialog).toHaveAttribute('data-jd-version-id','jd-version');
  expect(dialog).toHaveTextContent('advance:a/resume-a');
});

it('archives an ended flow instead of reusing its JD as the current job',async()=>{
  const person={id:'a',fullName:'重新进入流程的候选人',ownerUserId:'hr',version:1} as Candidate;
  const openCandidate=vi.fn();
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries([person]));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:'a',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[
    {jdId:'old-jd',jdVersionId:'old-version',title:'旧岗位',versionNo:1,resumeVersionId:'resume-a',status:'FINISHED',stage:'TERMINATED',applicationId:'old-application',matchRunId:null},
    {jdId:'new-jd',jdVersionId:'new-version',title:'新岗位',versionNo:1,resumeVersionId:'resume-a',status:'PENDING',stage:null,applicationId:null,matchRunId:'new-run'},
  ]}]);
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={[person]} loading={false} query="" onQuery={()=>undefined} onOpen={openCandidate} page={1} hasMore={false} onNext={()=>undefined} onPrevious={()=>undefined}/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);

  expect(await screen.findByText('新岗位',{selector:'td'})).toBeVisible();
  expect(screen.queryByText('旧岗位',{selector:'td'})).not.toBeInTheDocument();
  expect(screen.queryByText('已有招聘流程')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'详情与版本'}));
  expect(openCandidate).toHaveBeenCalledWith('a');
  expect(screen.queryByRole('button',{name:'查看招聘活动'})).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'直接推荐'}));
  expect(await screen.findByRole('dialog',{name:'公共批量操作'})).toHaveAttribute('data-jd-version-id','new-version');
});

it('shows an ended current-resume JD as historical association without recommending it',async()=>{
  const person={id:'a',fullName:'已取消流程候选人',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue([shareSummary(person.id,'resume-a')]);
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:'a',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:['TERMINATED'],jobContexts:[
    {jdId:'jd',jdVersionId:'jd-version',jdCode:'JD-000001',title:'AI工程师',versionNo:1,resumeVersionId:'resume-a',status:'FINISHED',stage:'TERMINATED',applicationId:'old-application',matchRunId:null},
    {jdId:'other',jdVersionId:'other-version',title:'旧简历岗位',versionNo:1,resumeVersionId:'old-resume',status:'FINISHED',stage:'TERMINATED',applicationId:'older-application',matchRunId:null},
  ]}]);
  renderLibrary([person]);
  expect(await screen.findByText('历史关联：AI工程师 · JD-000001',{selector:'td'})).toBeVisible();
  expect(screen.queryByText('未关联 JD',{selector:'td'})).not.toBeInTheDocument();
  expect(screen.queryByText(/旧简历岗位/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'直接推荐'}));
  expect(await screen.findByText('所选简历仅有已结束流程的历史关联岗位，请先确认本次应聘岗位')).toBeVisible();
  expect(screen.queryByRole('dialog',{name:'公共批量操作'})).not.toBeInTheDocument();
});

it('does not use an old-resume history as the current job',async()=>{
  const person={id:'a',fullName:'历史范围测试候选人',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries([person]));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{candidateId:'a',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:false,stages:[],jobContexts:[{jdId:'jd',jdVersionId:'version',title:'历史岗位',versionNo:1,resumeVersionId:'old-resume',status:'FINISHED',stage:'TERMINATED',applicationId:'old-application',matchRunId:null}]}]);
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={[person]} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} page={1} hasMore={false} onNext={()=>undefined} onPrevious={()=>undefined}/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  expect(await screen.findByText('未关联 JD',{selector:'td'})).toBeVisible();
  expect(screen.queryByText(/历史关联：/)).not.toBeInTheDocument();
});

it('rejects direct recommendation across different bound jobs instead of asking for a replacement job',async()=>{
  const people=[{id:'a',fullName:'岗位一候选人',ownerUserId:'hr',version:1},{id:'b',fullName:'岗位二候选人',ownerUserId:'hr',version:1}] as Candidate[];
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries(people));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue(people.map(person=>({candidateId:person.id,resumeVersionId:`resume-${person.id}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:`jd-${person.id}`,jdVersionId:`jd-version-${person.id}`,title:`岗位 ${person.id}`,versionNo:1,resumeVersionId:`resume-${person.id}`,status:'PENDING',stage:null,applicationId:null,matchRunId:null}]})));
  renderLibrary(people);
  await screen.findAllByText('岗位 a');
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'直接推荐'}));
  expect(await screen.findByText('所选简历绑定的岗位不一致，请调整勾选范围')).toBeInTheDocument();
  expect(screen.queryByRole('dialog',{name:'公共批量操作'})).not.toBeInTheDocument();
});

it('keeps selections across pages and admits them with their uniquely scored job before navigation',async()=>{
  const a={id:'a',fullName:'第一页',ownerUserId:'hr',version:1} as Candidate;
  const b={id:'b',fullName:'第二页',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockImplementation(ids=>Promise.resolve(emptySummaries(ids.map(id=>id==='a'?a:b))));
  vi.spyOn(talentApi,'poolStatuses').mockImplementation(ids=>Promise.resolve(ids.map(candidateId=>({candidateId,resumeVersionId:`resume-${candidateId}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:'jd',jdVersionId:'jd-version',title:'已评分岗位',versionNo:1,resumeVersionId:`resume-${candidateId}`,status:'PENDING',stage:null,applicationId:null,matchRunId:`run-${candidateId}`}]}))));
  vi.spyOn(talentApi,'screeningCandidates').mockImplementation(ids=>Promise.resolve((ids??[]).map(candidateId=>({candidateId,resumeVersionId:`resume-${candidateId}`,jdVersionId:'jd-version',defaultMatchRunId:`run-${candidateId}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null}))));
  const admit=vi.spyOn(talentApi,'admitScreening').mockResolvedValue({requestedCount:2,admittedCount:2,alreadyAdmittedCount:0,items:[]});
  const Location=()=>{const location=useLocation();return <output>{JSON.stringify({search:location.search,state:location.state as unknown})}</output>;};
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  client.setQueryData(['talent','candidates'],[]);
  client.setQueryData(['talent-search','pool'],[]);
  const View=({items,page}:{items:Candidate[];page:number})=><MemoryRouter><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={items} page={page} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} hasMore onNext={()=>undefined} onPrevious={()=>undefined}/><Location/></MemoryRouter>;
  const wrapper=({children}:{children:React.ReactNode})=><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}>{children}</QueryClientProvider></App></ConfigProvider>;
  const view=render(<View items={[a]} page={1}/>,{wrapper});
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  view.rerender(<View items={[b]} page={2}/>);
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  expect(screen.getByRole('checkbox',{name:'全选本页候选人'})).toBeChecked();
  expect(screen.getByRole('button',{name:'清空选择'})).toBeEnabled();
  expect(screen.getByRole('button',{name:'智能筛选'}).compareDocumentPosition(screen.getByRole('button',{name:'直接推荐'})) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'智能筛选'}));
  await waitFor(()=>expect(admit).toHaveBeenCalledWith([
    {candidateId:'a',resumeVersionId:'resume-a',jdVersionId:'jd-version',defaultMatchRunId:'run-a'},
    {candidateId:'b',resumeVersionId:'resume-b',jdVersionId:'jd-version',defaultMatchRunId:'run-b'},
  ]));
  expect(await screen.findByText('{"search":"?scoreJob=jd-version","state":{"jdId":"jd"}}')).toBeInTheDocument();
  expect(client.getQueryState(['talent','candidates'])?.isInvalidated).toBe(true);
  expect(client.getQueryState(['talent-search','pool'])?.isInvalidated).toBe(true);
  expect(screen.getByRole('checkbox',{name:'全选本页候选人'})).not.toBeChecked();
},15_000);

it('admits a mixed-job selection with each original JD and opens the screening workspace',async()=>{
  const items=[{id:'a',fullName:'岗位一候选人',ownerUserId:'hr',version:1},{id:'b',fullName:'岗位二候选人',ownerUserId:'hr',version:1}] as Candidate[];
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries(items));
  vi.spyOn(talentApi,'poolStatuses').mockImplementation(ids=>Promise.resolve(ids.map(candidateId=>({candidateId,resumeVersionId:`resume-${candidateId}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:`jd-${candidateId}`,jdVersionId:`jd-version-${candidateId}`,title:`岗位 ${candidateId}`,versionNo:1,resumeVersionId:`resume-${candidateId}`,status:'PENDING',stage:null,applicationId:null,matchRunId:`run-${candidateId}`}]}))));
  vi.spyOn(talentApi,'screeningCandidates').mockImplementation(ids=>Promise.resolve((ids??[]).map(candidateId=>({candidateId,resumeVersionId:`resume-${candidateId}`,jdVersionId:`jd-version-${candidateId}`,defaultMatchRunId:`run-${candidateId}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null}))));
  const admit=vi.spyOn(talentApi,'admitScreening').mockRejectedValueOnce(new Error('加入暂时失败')).mockResolvedValue({requestedCount:2,admittedCount:2,alreadyAdmittedCount:0,items:[]});
  const Location=()=>{const location=useLocation();return <output>{JSON.stringify({pathname:location.pathname,search:location.search,state:location.state as unknown})}</output>;};
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/talents']}><CandidateLibrary pageSize={20} onPageSizeChange={vi.fn()} items={items} loading={false} query="" onQuery={()=>undefined} onOpen={()=>undefined} page={1} hasMore={false} onNext={()=>undefined} onPrevious={()=>undefined}/><Location/></MemoryRouter></QueryClientProvider></App></ConfigProvider>);
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  fireEvent.click(screen.getByRole('button',{name:'智能筛选'}));
  expect(await screen.findByText('加入暂时失败')).toBeInTheDocument();
  for(const checkbox of screen.getAllByRole('checkbox'))expect(checkbox).toBeChecked();
  expect(screen.getByText('{"pathname":"/talents","search":"","state":null}')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'重试智能筛选'}));
  expect(await screen.findByText('{"pathname":"/matching","search":"","state":null}')).toBeInTheDocument();
  expect(admit).toHaveBeenCalledTimes(2);
  expect(admit).toHaveBeenLastCalledWith([
    {candidateId:'a',resumeVersionId:'resume-a',jdVersionId:'jd-version-a',defaultMatchRunId:'run-a'},
    {candidateId:'b',resumeVersionId:'resume-b',jdVersionId:'jd-version-b',defaultMatchRunId:'run-b'},
  ]);
  for(const checkbox of screen.getAllByRole('checkbox'))expect(checkbox).not.toBeChecked();
},15_000);

describe('CandidateLibrary requests',()=>{
  it('loads a page through one summary request without per-candidate resume requests',async()=>{
    const people:Candidate[]=Array.from({length:20},(_,index)=>({id:`candidate-${index}`,fullName:`候选人${index}`,primaryEmail:null,primaryPhone:null,ownerUserId:'owner',ownerDepartmentId:'department',status:'ACTIVE',version:1,createdAt:'2026-09-08T00:00:00Z',updatedAt:'2026-09-08T00:00:00Z'}));
    const summaries=vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries(people));
    const versions=vi.spyOn(talentApi,'resumeVersions'); const sources=vi.spyOn(talentApi,'resumeSource'); const facts=vi.spyOn(talentApi,'resumeFacts');
    vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([]);
    renderLibrary(people);
    await waitFor(()=>expect(summaries).toHaveBeenCalledTimes(1));
    expect(summaries).toHaveBeenCalledWith(people.map(person=>person.id));
    expect(versions).not.toHaveBeenCalled();expect(sources).not.toHaveBeenCalled();expect(facts).not.toHaveBeenCalled();
  });
});

it('shows associated JD titles for the current resume and never substitutes resume intentions',async()=>{
  const people=[{id:'a',fullName:'关联候选人',ownerUserId:'hr',version:1},{id:'b',fullName:'未关联候选人',ownerUserId:'hr',version:1}] as Candidate[];
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue(emptySummaries(people));
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue(people.map(person=>({candidateId:person.id,resumeVersionId:'current',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:person.id==='a'?[{jdId:'jd-a',jdVersionId:'jv-a',title:'上传关联的岗位',versionNo:1,resumeVersionId:'current',status:'PENDING',stage:null,applicationId:null,matchRunId:null}]:[]})));
  renderLibrary(people);
  // 只核对岗位单元格文本，避免异步等待时重复计算整张 AntD 表格的可访问名称。
  expect(await screen.findByText('上传关联的岗位',{selector:'td'})).toBeVisible();
  expect(screen.getByText('未关联 JD',{selector:'td'})).toBeVisible();
});

it('keeps separate same-title JD codes visible from the existing batch pool response', async () => {
  const person = { id: 'person', fullName: '同名岗位候选人', ownerUserId: 'hr', version: 1 } as Candidate;
  vi.spyOn(talentApi, 'candidateResumeSummaries').mockResolvedValue(emptySummaries([person]));
  const states = vi.spyOn(talentApi, 'poolStatuses').mockResolvedValue([{ candidateId: person.id, resumeVersionId: 'resume', libraryEligible: true, screenedOut: false, canScreenOut: true, stages: [], jobContexts: ['000001', '000002'].map(code => ({ jdId: code, jdCode: `JD-${code}`, jdVersionId: `version-${code}`, title: 'IDC工程师', versionNo: 1, resumeVersionId: 'resume', status: 'PENDING', stage: null, applicationId: null, matchRunId: null })) }]);
  renderLibrary([person]);
  expect((await screen.findAllByText('IDC工程师 · JD-000001、IDC工程师 · JD-000002')).length).toBeGreaterThan(0);
  expect(states).toHaveBeenCalledOnce();
});

it('keeps legacy overdue records selectable without displaying timeout warnings or task actions',async()=>{
  const person={id:'legacy',fullName:'历史未处理候选人',ownerUserId:'hr',version:1} as Candidate;
  vi.spyOn(talentApi,'candidateResumeSummaries').mockResolvedValue([shareSummary(person.id,'resume-legacy')]);
  vi.spyOn(talentApi,'poolStatuses').mockResolvedValue([{
    candidateId:person.id,resumeVersionId:'resume-legacy',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[],
    lifecycle:{phase:'PENDING_DISPOSAL',warningLevel:'RED',enteredAt:'2026-08-01T00:00:00Z',elapsedWorkdays:20,nextTransitionAt:null,shareDueAt:'2026-08-05T17:00:00Z',pauseReason:'CALENDAR_MISSING',sharedAt:null,shareReason:null,version:'1',cycleId:'old-cycle'},
  }]);
  renderLibrary([person]);
  expect(await screen.findByRole('button',{name:person.fullName})).toBeVisible();
  await waitFor(()=>expect(screen.getByText('待解析')).toBeVisible());
  expect(screen.queryByText(/红色预警|黄色预警|暂停流转/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'查看招聘活动'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox',{name:'全选本页候选人'}));
  expect(screen.getByRole('button',{name:'筛选结束入共享库'})).toBeEnabled();
});

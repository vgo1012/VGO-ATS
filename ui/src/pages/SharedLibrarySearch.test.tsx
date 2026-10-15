import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {App,ConfigProvider} from 'antd';
import {fireEvent,render,screen,waitFor,cleanup,within} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {afterEach,expect,it,vi} from 'vitest';
import {TalentSearchPanel} from './TalentSearchPanel';
import {searchApi} from '../search/api';
import {talentApi} from '../talent/api';
import type {CandidatePoolStatus} from '../talent/api';
import type * as SearchApiModule from '../search/api';
import type {TalentSearchResult} from '../search/api';
import type * as BusinessReferences from '../display/BusinessReferences';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr',superAdmin:false,permissions:['talent.read','recruitment.manage']}})}));
vi.mock('../search/api',async original=>({...await original<typeof SearchApiModule>(),searchApi:{search:vi.fn(),count:vi.fn().mockResolvedValue(0)}}));
vi.mock('../demand/api',()=>({demandApi:{jds:vi.fn().mockResolvedValue({items:[],hasMore:false,nextCursor:null})}}));
vi.mock('../talent/api',()=>({talentApi:{candidateResumeSummaries:vi.fn().mockResolvedValue([]),poolStatuses:vi.fn().mockResolvedValue([]),candidateBatch:vi.fn().mockResolvedValue([])}}));
vi.mock('../talent/SharedCandidateTransferDialog',()=>({SharedCandidateTransferDialog:({open,candidates}:{open:boolean;candidates:Array<{candidateId:string;resumeVersionId:string;candidateVersion:number}>})=>open?<div role="dialog" aria-label="转入我的简历">{candidates.map(value=>`${value.candidateId}:${value.resumeVersionId}:${value.candidateVersion}`).join(',')}</div>:null}));
vi.mock('../display/BusinessReferences',async original=>({...await original<typeof BusinessReferences>(),UserName:({id}:{id?:string})=><>{id??'—'}</>}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

const candidate:TalentSearchResult={candidateId:'candidate-1',resumeVersionId:'resume-1',factSnapshotId:'facts-1',fullName:'真实候选人',email:null,phone:null,currentRole:null,city:null,age:null,gender:null,jobIntention:null,sourceChannel:null,factUpdatedAt:'2026-09-19T00:00:00Z',skills:[],employers:[],roles:[],projects:[],certificates:[],languages:[],degrees:[],industries:[],tags:[],workYears:null,engagementLock:null,conditionMatches:[],applicationSummaries:[],interviewSummaries:[],clientSubmissionSummaries:[]};
it('keeps jobs and finished applications from older resume versions visible in the shared row',async()=>{
  vi.mocked(searchApi.search).mockResolvedValue({items:[candidate],hasMore:false,nextCursor:null});
  const state:CandidatePoolStatus={candidateId:candidate.candidateId,resumeVersionId:'resume-1',libraryEligible:true,screenedOut:true,canScreenOut:false,stages:[],jobContexts:[{jdId:'old-jd',jdVersionId:'old-jdv',title:'历史运维岗位',versionNo:1,resumeVersionId:'older-resume',status:'FINISHED',stage:'REJECTED',applicationId:'older-application',matchRunId:null}]};
  vi.mocked(talentApi.poolStatuses).mockResolvedValueOnce([state]);
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  expect(await screen.findByText('历史运维岗位',{exact:true})).toBeVisible();
  expect(screen.getByRole('columnheader',{name:'历史关联岗位'})).toBeVisible();
  expect(screen.getByRole('button',{name:'历史运维岗位 · 历史归档'})).toBeVisible();
  expect(screen.getByText('· 历史简历版本')).toBeVisible();
  expect(screen.queryByText('未关联 JD')).not.toBeInTheDocument();
  expect(screen.queryByText('暂无招聘流程')).not.toBeInTheDocument();
});
it('searches keywords directly in the shared library without opening a dialog',async()=>{
  vi.mocked(searchApi.search).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  // landmark 与输入框按 e2e 契约共用同一无障碍名称，必须按 role 取用以避免歧义匹配。
  fireEvent.change(screen.getByRole('textbox',{name:'共享简历关键词搜索'}),{target:{value:'Linux 服务器'}});
  fireEvent.click(screen.getByRole('button',{name:'搜索'}));
  await waitFor(()=>expect(searchApi.search).toHaveBeenLastCalledWith(expect.objectContaining({query:'Linux 服务器'}),undefined,'SHARED',20));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('applies the reference button filters with keywords and resets the conditions', async () => {
 vi.mocked(searchApi.search).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 fireEvent.change(screen.getByRole('textbox',{name:'共享简历关键词搜索'}),{target:{value:'Java'}});
 // 在语义分组内查找控件，避免每次点击都计算整页按钮的可访问名称与样式。
 const filters=within(screen.getByRole('form',{name:'简历筛选条件'}));
 fireEvent.click(within(filters.getByRole('group',{name:'学历要求'})).getByRole('button',{name:'本科'}));
 fireEvent.click(within(filters.getByRole('group',{name:'经验要求'})).getByRole('button',{name:'1-3 年'}));
 fireEvent.click(within(filters.getByRole('group',{name:'年龄要求'})).getByRole('button',{name:'25-30'}));
 fireEvent.click(within(filters.getByRole('group',{name:'性别'})).getByRole('button',{name:'女'}));
 await waitFor(()=>expect(searchApi.search).toHaveBeenLastCalledWith(expect.objectContaining({query:'Java',degrees:['本科'],minWorkYears:1,maxWorkYears:3,minAge:25,maxAge:29,gender:'FEMALE'}),undefined,'SHARED',20));
 expect(filters.queryByRole('button',{name:/^筛\s*选$/})).not.toBeInTheDocument();
 fireEvent.click(filters.getByRole('button',{name:/重\s*置/}));
 await waitFor(()=>expect(searchApi.search).toHaveBeenLastCalledWith(expect.objectContaining({query:'Java',degrees:[]}),undefined,'SHARED',20));
 for(const text of ['可解释人才搜索','编辑搜索条件','保存模板','智能筛选','直接推荐','筛选结束入共享库'])expect(screen.queryByText(text)).not.toBeInTheDocument();
},15_000);

it('automatically skips an empty intermediate scan page until a shared candidate is found',async()=>{
 vi.mocked(searchApi.search).mockImplementation((_criteria,cursor)=>Promise.resolve(cursor==='next-page'
   ?{items:[candidate],hasMore:false,nextCursor:null,scanIncomplete:false}
   :{items:[],hasMore:true,nextCursor:'next-page',scanIncomplete:true}));
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 expect(await screen.findByText('真实候选人')).toBeInTheDocument();
 expect(vi.mocked(searchApi.search).mock.calls.map(([,cursor])=>cursor)).toEqual([undefined,'next-page']);
 expect(screen.queryByText('仍有候选人待检索，请继续检索')).not.toBeInTheDocument();
});

it('stops automatic scanning when the backend repeats a cursor',async()=>{
 vi.mocked(searchApi.search).mockResolvedValue({items:[],hasMore:true,nextCursor:'repeated',scanIncomplete:true});
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 expect(await screen.findByText('共享简历检索游标异常')).toBeInTheDocument();
 expect(vi.mocked(searchApi.search).mock.calls.map(([,cursor])=>cursor)).toEqual([undefined,'repeated']);
 expect(screen.getByRole('button',{name:'继续检索'})).toBeDisabled();
});

it('cancels an obsolete count when search changes and never shows its late total',async()=>{
 vi.mocked(searchApi.search).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 let finish:((value:number)=>void)|undefined;
 let oldSignal:AbortSignal|undefined;
 vi.mocked(searchApi.count).mockImplementationOnce((_criteria,_scope,_ids,signal)=>{oldSignal=signal;return new Promise<number>(resolve=>{finish=resolve;});}).mockResolvedValue(7);
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 expect(await screen.findByText('条/统计中…')).toBeVisible();
 fireEvent.change(screen.getByRole('textbox',{name:'共享简历关键词搜索'}),{target:{value:'新的条件'}});
 fireEvent.click(screen.getByRole('button',{name:'搜索'}));
 expect(await screen.findByText('条/共7条记录')).toBeVisible();
 expect(oldSignal?.aborted).toBe(true);
 finish?.(999);
 await waitFor(()=>expect(screen.queryByText('条/共999条记录')).not.toBeInTheDocument());
});

it('changes page size after paging and resets the cursor and selection',async()=>{
 vi.mocked(searchApi.search).mockImplementation((_criteria,cursor)=>Promise.resolve({items:[candidate],hasMore:!cursor,nextCursor:cursor?null:'second'}));
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 const footer=within(screen.getByRole('navigation',{name:'共享简历库分页'}));
 await waitFor(()=>expect(footer.getByRole('button',{name:'下一页'})).toBeEnabled());
 fireEvent.click(footer.getByRole('button',{name:'下一页'}));
 await waitFor(()=>expect(searchApi.search).toHaveBeenLastCalledWith(expect.anything(),'second','SHARED',20));
 await waitFor(()=>expect(footer.getByRole('spinbutton',{name:'每页条数'})).toBeEnabled());
 fireEvent.click(screen.getByRole('checkbox',{name:'选择真实候选人'}));
 const size=footer.getByRole('spinbutton',{name:'每页条数'});
 fireEvent.change(size,{target:{value:'30'}});
 fireEvent.blur(size);
 await waitFor(()=>expect(searchApi.search).toHaveBeenLastCalledWith(expect.anything(),undefined,'SHARED',30));
 expect(footer.getByRole('spinbutton',{name:'页码'})).toHaveValue(1);
 expect(await screen.findByRole('checkbox',{name:'选择真实候选人'})).not.toBeChecked();
});

it('uses the row action to open a standalone scored transfer instead of recording contact',async()=>{
  vi.mocked(searchApi.search).mockResolvedValue({items:[candidate],hasMore:false,nextCursor:null});
 vi.mocked(talentApi.candidateBatch).mockResolvedValue([{id:'candidate-1',fullName:'真实候选人',primaryEmail:null,primaryPhone:null,ownerUserId:'old-owner',ownerDepartmentId:'old-dept',status:'ACTIVE',version:7,createdAt:'2026-09-01T00:00:00Z',updatedAt:'2026-09-20T00:00:00Z'}]);
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 const row=(await screen.findByText('真实候选人',{}, {timeout:5000})).closest('tr')!;
 const rowTransfer=within(row).getByRole('button',{name:'转入我的简历'});
 await waitFor(()=>expect(rowTransfer).toBeEnabled());
 fireEvent.click(rowTransfer);
 expect(await screen.findByRole('dialog',{name:'转入我的简历'})).toHaveTextContent('candidate-1:resume-1:7');
 expect(screen.queryByText('记录有效联系')).not.toBeInTheDocument();
});

it('opens one scored transfer for selected shared resumes with frozen versions',async()=>{
 vi.mocked(searchApi.search).mockResolvedValue({items:[candidate],hasMore:false,nextCursor:null});
 vi.mocked(talentApi.candidateBatch).mockResolvedValue([{id:'candidate-1',fullName:'真实候选人',primaryEmail:null,primaryPhone:null,ownerUserId:'old-owner',ownerDepartmentId:'old-dept',status:'ACTIVE',version:7,createdAt:'2026-09-01T00:00:00Z',updatedAt:'2026-09-20T00:00:00Z'}]);
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><TalentSearchPanel onOpenCandidate={()=>undefined}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 fireEvent.click(await screen.findByRole('checkbox',{name:'选择真实候选人'}));
 const reclaim=await screen.findByRole('button',{name:'转入我的简历（1）'});
  await waitFor(()=>expect(reclaim).toBeEnabled());
  fireEvent.click(reclaim);
 expect(await screen.findByRole('dialog',{name:'转入我的简历'})).toHaveTextContent('candidate-1:resume-1:7');
});

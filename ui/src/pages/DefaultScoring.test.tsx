import { StrictMode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { demandApi, type MatchPlanDetails, type WeightProfileDetails } from '../demand/api';
import { talentApi, type Candidate } from '../talent/api';
import { matchingApi } from '../matching/api';
import { recruitmentApi } from '../recruitment/api';
import { candidateActionsApi } from '../recruitment/candidateActionsApi';
import { MatchingWorkspacePage } from './MatchingWorkspacePage';
import { ScoringGovernancePage } from './ScoringGovernancePage';
import { ApiProblemError } from '../api/problem';

const session=vi.hoisted(()=>({actor:{userId:'hr',permissions:['matching.run'],superAdmin:false}}));
vi.mock('../auth/session',()=>({useSession:()=>session}));
vi.mock('../recruitment/api',()=>({recruitmentApi:{jdProcessingAccess:vi.fn()}}));
vi.mock('../recruitment/candidateActionsApi',()=>({candidateActionsApi:{readiness:vi.fn(),advance:vi.fn()}}));
vi.mock('../demand/api', () => ({ demandApi: { scoringDefaults:vi.fn(), scoringConfigs:vi.fn(), cloneScoringConfig:vi.fn(), activateScoringConfig:vi.fn(), jdVersions:vi.fn(), activeScoringForJd:vi.fn(), weightProfiles:vi.fn(), personalPreference:vi.fn(),jdFacts:vi.fn(),jd:vi.fn(),jds:vi.fn() } }));
vi.mock('../talent/api', () => ({ talentApi: { candidate:vi.fn(),candidates:vi.fn(),resumeVersions:vi.fn(),poolStatuses:vi.fn(),screeningCandidates:vi.fn(),screenOut:vi.fn(),returnScreening:vi.fn() } }));
vi.mock('../matching/api', () => ({ matchingApi: { screeningCandidates:vi.fn(),createRun:vi.fn(),jobs:vi.fn(),bases:vi.fn(),jobResults:vi.fn(),run:vi.fn(),results:vi.fn() } }));
vi.mock('../tasks/task',()=>({taskApi:{get:vi.fn().mockResolvedValue({status:'SUCCEEDED',progressPercent:100})},taskPollingInterval:()=>false}));
vi.mock('../matching/WeightProfiles',()=>({WeightProfiles:()=>null}));
vi.mock('../display/BusinessReferences',()=>({useUserNames:()=>({people:new Map(),loading:false}),UserName:()=> <span>本人</span>,CandidateName:()=> <span>历史候选人</span>,ResumeVersionName:()=> <span>历史简历版本</span>,TechnicalDetails:()=>null}));
const config = {plan:{name:'公司默认五维评分'},version:{id:'default-plan',versionNo:1,status:'ACTIVE',skillsEnabled:true,roleEnabled:true,yearsEnabled:true,responsibilityEnabled:true,industryProjectEnabled:true},criterionPlan:{planMaxBonusPoints:0},criteria:[]} as unknown as MatchPlanDetails;
const profile = {profile:{id:'default-profile',name:'公司默认权重',ownerUserId:null},currentVersion:{id:'default-weights',versionNo:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20}} as WeightProfileDetails;
function mount(element:React.ReactNode){return render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>{element}</QueryClientProvider></App></ConfigProvider></MemoryRouter>);}
function mountMatchingWithJd(){
 vi.mocked(demandApi.jd).mockResolvedValue({id:'jd',demandId:null,title:'来源岗位',currentVersionNo:1} as Awaited<ReturnType<typeof demandApi.jd>>);
 const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
 return {...render(<MemoryRouter initialEntries={[{pathname:'/matching',state:{candidateIds:['candidate'],jdId:'jd'}}]}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><MatchingWorkspacePage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>),client};
}
function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(done=>{resolve=done;});return {promise,resolve};}
afterEach(cleanup);
beforeEach(()=>{
 vi.clearAllMocks();
 vi.mocked(demandApi.jdFacts).mockResolvedValue({facts:{title:'来源岗位'}} as Awaited<ReturnType<typeof demandApi.jdFacts>>);
 vi.mocked(matchingApi.screeningCandidates).mockResolvedValue({items:[{candidateId:'candidate',resumeVersionId:'resume'}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof matchingApi.screeningCandidates>>);
 vi.mocked(demandApi.jds).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 session.actor={userId:'hr',permissions:['matching.run'],superAdmin:false};
 vi.mocked(recruitmentApi.jdProcessingAccess).mockResolvedValue({processingAllowed:false});
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue({
   jdId:'jd',jdVersionId:'jd-version',jdTitle:'来源岗位',demandId:null,recruitmentType:'INTERNAL',
   headcount:2,occupiedHeadcount:1,remainingHeadcount:1,canProcess:true,
   activeWorkflowId:'workflow',activeWorkflow:null,draftWorkflows:[],standardSteps:[],
 });
 vi.mocked(candidateActionsApi.advance).mockResolvedValue({items:[{candidateId:'candidate',resumeVersionId:'resume',status:'SUCCEEDED',applicationId:'application'}]});
 vi.mocked(matchingApi.jobs).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 vi.mocked(matchingApi.bases).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 vi.mocked(matchingApi.jobResults).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 vi.mocked(demandApi.scoringConfigs).mockResolvedValue({items:[config],hasMore:false,nextCursor:null});
 vi.mocked(demandApi.scoringDefaults).mockResolvedValue({scoringConfiguration:config,weightProfile:profile});
 vi.mocked(demandApi.weightProfiles).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
 vi.mocked(demandApi.jdVersions).mockResolvedValue({items:[{id:'jd-version',jdId:'jd',versionNo:1}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof demandApi.jdVersions>>);
 vi.mocked(demandApi.personalPreference).mockResolvedValue(null);
 vi.mocked(demandApi.activeScoringForJd).mockResolvedValue({jdVersionId:'jd-version',scoringConfigurationActive:true,workflowTemplateActive:false,readyForApplication:false,scoringConfiguration:config});
 vi.mocked(talentApi.poolStatuses).mockResolvedValue([]);
 vi.mocked(talentApi.candidate).mockResolvedValue({id:'candidate',fullName:'批量候选人',ownerUserId:'hr',version:4} as Candidate);
 vi.mocked(talentApi.screenOut).mockResolvedValue({candidateId:'candidate',resumeVersionId:'resume',libraryEligible:true,screenedOut:true,canScreenOut:false,stages:[]});
 vi.mocked(talentApi.returnScreening).mockResolvedValue({requestedCount:1,returnedCount:1,alreadyReturnedCount:0});
 vi.mocked(talentApi.candidates).mockResolvedValue({items:[{id:'candidate',fullName:'合成候选人',ownerUserId:'hr'} as Candidate],hasMore:false,nextCursor:null});
 vi.mocked(talentApi.resumeVersions).mockResolvedValue({items:[{id:'resume'}],hasMore:false,nextCursor:null} as Awaited<ReturnType<typeof talentApi.resumeVersions>>);
 vi.mocked(talentApi.screeningCandidates).mockImplementation(ids=>Promise.resolve([{candidateId:ids?.[0]??'candidate',resumeVersionId:'resume',jdVersionId:'jd-version',defaultMatchRunId:'default-run',defaultResult:{professionalScore:73,bonusScore:0,referenceScore:73,mustTier:'PASS'},admissionStatus:'ADMITTED',admissionId:'admission',admissionVersion:'0',admittedAt:'2026-09-10T00:00:00Z'}]));
 vi.mocked(matchingApi.createRun).mockRejectedValue(new Error('测试只检查提交参数'));
});

it('loads another personal weight page only when the user requests more', async()=>{
 const first={...profile,profile:{...profile.profile,id:'personal-1',ownerUserId:'hr',name:'第一页方案'},currentVersion:{...profile.currentVersion,id:'personal-v1'}};
 const second={...profile,profile:{...profile.profile,id:'personal-2',ownerUserId:'hr',name:'下一页方案'},currentVersion:{...profile.currentVersion,id:'personal-v2'}};
 vi.mocked(demandApi.weightProfiles).mockImplementation((_mineOnly,cursor)=>Promise.resolve(cursor==='next'?{items:[second],hasMore:false,nextCursor:null}:{items:[first],hasMore:true,nextCursor:'next'}));
 mountMatchingWithJd();
 await waitFor(()=>expect(demandApi.weightProfiles).toHaveBeenCalledExactlyOnceWith(true,undefined));
 fireEvent.click(await screen.findByText('个人方案'));
 const selector=await screen.findByRole('combobox',{name:'选择个人方案'});
 fireEvent.mouseDown(selector);
 await screen.findAllByText('第一页方案');
 fireEvent.click(await screen.findByRole('button',{name:'加载更多方案'}));
 await screen.findByText('下一页方案');
 expect(demandApi.weightProfiles).toHaveBeenLastCalledWith(true,'next');
 expect(demandApi.weightProfiles).toHaveBeenCalledTimes(2);
},15_000);

it('clears a selected personal weight after access is revoked',async()=>{
 const personal={...profile,profile:{...profile.profile,id:'personal',ownerUserId:'hr',name:'本人方案'},currentVersion:{...profile.currentVersion,id:'personal-version'}};
 vi.mocked(demandApi.weightProfiles).mockResolvedValue({items:[personal],hasMore:false,nextCursor:null});
 const {client}=mountMatchingWithJd();
 fireEvent.click(await screen.findByText('个人方案'));
 expect(await screen.findByText('本人方案')).toBeInTheDocument();
 vi.mocked(demandApi.weightProfiles).mockRejectedValue(new ApiProblemError({status:403,title:'禁止访问',detail:'范围已撤销',code:'FORBIDDEN',recoverable:false}));
 await act(()=>client.invalidateQueries({queryKey:['matching','weight-profiles']}));
 await screen.findByText('个人方案读取失败');
 await waitFor(()=>expect(screen.queryByText('本人方案')).not.toBeInTheDocument());
 expect(screen.queryByText('personal-version')).not.toBeInTheDocument();
},15_000);
it('offers a V2 draft while keeping V1 active until publication',async()=>{
 mount(<ScoringGovernancePage/>);
 expect(await screen.findByRole('button', {name:'创建 V2 草稿'})).toBeInTheDocument();
 expect(screen.getByText('全部 JD 的新匹配')).toBeInTheDocument();
 expect(demandApi.activateScoringConfig).not.toHaveBeenCalled();
 expect(screen.queryByRole('button',{name:'关联岗位'})).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'新建评分方案'})).not.toBeInTheDocument();
});
it('uses existing company scores without offering another computation',async()=>{
 mountMatchingWithJd();
 await waitFor(()=>expect(demandApi.activeScoringForJd).toHaveBeenCalled());
 const run=screen.getByRole('button',{name:'重新评分'});
 expect(run).toBeDisabled();
 fireEvent.click(run);
 expect(matchingApi.createRun).not.toHaveBeenCalled();
 expect(matchingApi.screeningCandidates).not.toHaveBeenCalled();
});

function enablePersonalProfile() {
 vi.mocked(demandApi.weightProfiles).mockResolvedValue({items:[{...profile,profile:{...profile.profile,id:'personal',ownerUserId:'hr'},currentVersion:{...profile.currentVersion,id:'personal-weights'}}],hasMore:false,nextCursor:null});
}
function choosePersonal() { fireEvent.click(screen.getByRole('radio',{name:'个人方案'})); }

it('rechecks admission before personal rescoring and rejects a merely ready default result',async()=>{
 enablePersonalProfile();
 vi.mocked(talentApi.screeningCandidates).mockImplementation(ids=>Promise.resolve([{candidateId:ids?.[0]??'candidate',resumeVersionId:'resume',jdVersionId:'jd-version',defaultMatchRunId:'default-run',defaultResult:{professionalScore:73,bonusScore:0,referenceScore:73,mustTier:'PASS'},admissionStatus:ids?'READY':'ADMITTED',admissionId:ids?null:'admission',admissionVersion:ids?null:'0',admittedAt:ids?null:'2026-09-10T00:00:00Z'}]));
 mountMatchingWithJd();
 choosePersonal();
 const run=await screen.findByRole('button',{name:'重新评分'},{timeout:5000});
 await waitFor(()=>expect(run).toBeEnabled());fireEvent.click(run);
 await waitFor(() => expect(screen.getAllByRole('alert').some(alert => alert.textContent?.includes('当前岗位的简历准入状态已变化，请刷新后重新评分'))).toBe(true));
 expect(matchingApi.createRun).not.toHaveBeenCalled();
},15000);

it('does not replace a selected resume with another admitted version of the same candidate',async()=>{
 enablePersonalProfile();
 vi.mocked(demandApi.jdFacts).mockResolvedValue({facts:{title:'来源岗位'}} as Awaited<ReturnType<typeof demandApi.jdFacts>>);
 mountMatchingWithJd();
 choosePersonal();
 await waitFor(()=>expect(screen.getByRole('button',{name:'重新评分'})).toBeEnabled(),{timeout:5000});
 vi.mocked(talentApi.screeningCandidates).mockResolvedValue([{candidateId:'candidate',resumeVersionId:'different-resume',jdVersionId:'jd-version',defaultMatchRunId:'default-run',defaultResult:{professionalScore:73,bonusScore:0,referenceScore:73,mustTier:'PASS'},admissionStatus:'ADMITTED',admissionId:'different-admission',admissionVersion:'0',admittedAt:'2026-09-10T00:00:00Z'}]);
 const run=await screen.findByRole('button',{name:'重新评分'});
 await waitFor(()=>expect(run).toBeEnabled());
 fireEvent.click(run);
 await waitFor(() => expect(screen.getAllByRole('alert').some(alert => alert.textContent?.includes('当前岗位的简历准入状态已变化，请刷新后重新评分'))).toBe(true));
 expect(matchingApi.createRun).not.toHaveBeenCalled();
},15000);


function mountSavedRun(){
 const run={id:'saved-run',taskId:'task',jdVersionId:'jd-version',status:'SUCCEEDED',totalCount:1,succeededCount:1,failedCount:0} as Awaited<ReturnType<typeof matchingApi.run>>;
 vi.mocked(matchingApi.run).mockResolvedValue(run);
 vi.mocked(matchingApi.results).mockResolvedValue({items:[{resultId:'result',baseEvaluationId:'base',candidateId:'candidate',resumeVersionId:'resume',stableRank:1,referenceScore:73,professionalScore:73,bonusScore:0,mustTier:'PASS',details:{jdFactSnapshotId:'jd-fact',resumeFactSnapshotId:'resume-fact',candidateId:'candidate',resumeVersionId:'resume',professionalScore:73,bonusScore:0,referenceScore:73,mustTier:'PASS',dimensions:[],criteria:[],selectedBonusCap:0}}],hasMore:false,nextCursor:null});
 vi.mocked(demandApi.jdFacts).mockResolvedValue({facts:{title:'历史岗位'}} as Awaited<ReturnType<typeof demandApi.jdFacts>>);
 const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
 render(<StrictMode><MemoryRouter initialEntries={['/matching?run=saved-run']}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><MatchingWorkspacePage/></QueryClientProvider></App></ConfigProvider></MemoryRouter></StrictMode>);
 return client;
}

it('手动刷新当前冻结结果和候选人状态，保留搜索、清空勾选且不重新评分或刷新其他岗位',async()=>{
 vi.mocked(talentApi.poolStatuses).mockResolvedValue([{candidateId:'candidate',candidateName:'批量候选人',resumeVersionId:'resume',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}] as Awaited<ReturnType<typeof talentApi.poolStatuses>>);
 const client=mountSavedRun();
 await screen.findByText('73.00');
 await screen.findByText('批量候选人');
 const original=client.getQueryData<Awaited<ReturnType<typeof matchingApi.results>>>(['matching','results','saved-run',undefined,20])!;
 client.setQueryData(['matching','job-results','other-job','other-basis'],{items:[]});
 fireEvent.change(screen.getByRole('textbox',{name:'搜索本页候选人'}),{target:{value:'批量'}});
 fireEvent.click(screen.getByRole('button',{name:'全选本页筛选结果'}));
 expect(screen.getByText('已选 1 人')).toBeInTheDocument();
 const pending=deferred<Awaited<ReturnType<typeof matchingApi.results>>>();
 const previousResults=vi.mocked(matchingApi.results).mock.calls.length;
 const previousRun=vi.mocked(matchingApi.run).mock.calls.length;
 const previousPool=vi.mocked(talentApi.poolStatuses).mock.calls.length;
 vi.mocked(matchingApi.results).mockReturnValueOnce(pending.promise);
 const button=screen.getByRole('button',{name:/刷新/});
 fireEvent.click(button);fireEvent.click(button);
 await waitFor(()=>expect(matchingApi.results).toHaveBeenCalledTimes(previousResults+1));
 expect(button).toHaveClass('ant-btn-loading');
 expect(screen.getByText('已选 0 人')).toBeInTheDocument();
 await act(()=>{pending.resolve({...original,items:original.items.map(item=>({...item,referenceScore:81,professionalScore:81}))});return Promise.resolve();});
 expect(await screen.findByText('81.00')).toBeInTheDocument();
 expect(screen.getByRole('textbox',{name:'搜索本页候选人'})).toHaveValue('批量');
 await waitFor(()=>expect(button).not.toHaveClass('ant-btn-loading'));
 expect(matchingApi.run).toHaveBeenCalledTimes(previousRun+1);
 expect(talentApi.poolStatuses).toHaveBeenCalledTimes(previousPool+1);
 expect(client.getQueryState(['matching','job-results','other-job','other-basis'])?.isInvalidated).toBe(false);
 expect(matchingApi.createRun).not.toHaveBeenCalled();
});

it.each([503,403])('刷新结果失败 %s 时可恢复，拒绝访问时隐藏缓存结果',async status=>{
 mountSavedRun();
 await screen.findByText('73.00');
 vi.mocked(matchingApi.results).mockRejectedValueOnce(new ApiProblemError({status,title:'读取失败',detail:'当前结果暂不可读取',code:'REFRESH_FAILED',recoverable:status===503}));
 fireEvent.click(screen.getByRole('button',{name:/刷新/}));
 if(status===403){
   await screen.findByText('暂无匹配进度或结果的查看权限');
   expect(screen.queryByText('73.00')).not.toBeInTheDocument();
 }else{
   await screen.findByText('智能筛选刷新失败');
   expect(screen.getByText('73.00')).toBeInTheDocument();
   await waitFor(()=>expect(screen.getByRole('button',{name:/刷新/})).not.toHaveClass('ant-btn-loading'));
   fireEvent.click(screen.getByRole('button',{name:/刷新/}));
   await waitFor(()=>expect(screen.queryByText('智能筛选刷新失败')).not.toBeInTheDocument());
 }
 expect(matchingApi.createRun).not.toHaveBeenCalled();
});
it('restores the selected frozen score from the URL without choosing a job again',async()=>{
 vi.mocked(talentApi.poolStatuses).mockResolvedValue([{candidateId:'candidate',candidateName:'批量候选人',resumeVersionId:'resume',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}] as Awaited<ReturnType<typeof talentApi.poolStatuses>>);
 mountSavedRun();
 expect(await screen.findByText('73.00')).toBeInTheDocument();
 expect(matchingApi.run).toHaveBeenCalledWith('saved-run');
 expect(matchingApi.results).toHaveBeenCalledWith('saved-run',undefined,20);
 expect(await screen.findByText('批量候选人')).toBeInTheDocument();
 expect(screen.queryByText('结果已冻结')).not.toBeInTheDocument();
});
it('removes a successfully shared candidate from the restored screening result immediately',async()=>{
 vi.mocked(talentApi.poolStatuses).mockResolvedValue([{candidateId:'candidate',candidateName:'批量候选人',resumeVersionId:'resume',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}] as Awaited<ReturnType<typeof talentApi.poolStatuses>>);
 mountSavedRun();
 await screen.findByText('73.00');
 const share=screen.getByText('转入共享库').closest('button')!;
 await waitFor(()=>expect(share).toBeEnabled());
 fireEvent.click(share);
 fireEvent.click(await screen.findByRole('button',{name:'确认转共享 1 人'}));
 await waitFor(()=>expect(talentApi.screenOut).toHaveBeenCalled());
 await waitFor(()=>expect(screen.queryByText('73.00')).not.toBeInTheDocument());
 expect(screen.queryByText(/^本页显示/)).not.toBeInTheDocument();
 expect(screen.getByRole('button',{name:'清除筛选'})).toBeDisabled();
});
it('removes a candidate immediately after recommendation succeeds',async()=>{
 session.actor={userId:'hr',permissions:['matching.run','recruitment.manage'],superAdmin:false};
 vi.mocked(recruitmentApi.jdProcessingAccess).mockResolvedValue({processingAllowed:true});
 mountSavedRun();
 await screen.findByText('73.00');
 const advance=screen.getByText('推荐到流程').closest('button')!;
 await waitFor(()=>expect(advance).toBeEnabled());
 fireEvent.click(advance);
 const confirm=await screen.findByRole('button',{name:'确认推荐 1 人'});
 await waitFor(()=>expect(confirm).toBeEnabled());
 fireEvent.click(confirm);
 await waitFor(()=>expect(candidateActionsApi.advance).toHaveBeenCalled());
 await waitFor(()=>expect(screen.queryByText('73.00')).not.toBeInTheDocument());
});
it('lets the current resume owner recommend from the original result row without JD processing ownership',async()=>{
 session.actor={userId:'hr',permissions:['matching.run','recruitment.manage'],superAdmin:false};
 vi.mocked(talentApi.screeningCandidates).mockResolvedValue([{candidateId:'candidate',resumeVersionId:'resume',jdVersionId:'jd-version',defaultMatchRunId:'default-run',defaultResult:{professionalScore:73,bonusScore:0,referenceScore:73,mustTier:'PASS'},admissionStatus:'ADMITTED',admissionId:'admission',admissionVersion:'0',admittedAt:'2026-09-10T00:00:00Z',ownedByCurrentActor:true}]);
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue({jdId:'jd',jdVersionId:'jd-version',jdTitle:'来源岗位',demandId:null,recruitmentType:'INTERNAL',headcount:2,occupiedHeadcount:1,remainingHeadcount:1,canProcess:false,activeWorkflowId:'workflow',activeWorkflow:null,draftWorkflows:[],standardSteps:[]});
 mountSavedRun();
 const advance=(await screen.findByText('推荐到流程')).closest('button')!;
 await waitFor(()=>expect(advance).toBeEnabled());
 fireEvent.click(advance);
 const confirm=await screen.findByRole('button',{name:'确认推荐 1 人'});
 await waitFor(()=>expect(confirm).toBeEnabled());
 fireEvent.click(confirm);
 await waitFor(()=>expect(candidateActionsApi.advance).toHaveBeenCalled());
});
it('removes a candidate immediately after returning it to personal resumes',async()=>{
 mountSavedRun();
 await screen.findByText('73.00');
 const returnButton=screen.getByText('退回我的简历').closest('button')!;
 await waitFor(()=>expect(returnButton).toBeEnabled());
 fireEvent.click(returnButton);
 fireEvent.click(await screen.findByRole('button',{name:'确认退回'}));
 await waitFor(()=>expect(talentApi.returnScreening).toHaveBeenCalled());
 await waitFor(()=>expect(screen.queryByText('73.00')).not.toBeInTheDocument());
});
it('opens return for a currently admitted exact resume and explains retained history',async()=>{
 mountSavedRun();
 await screen.findByText('73.00');
 const returnButton=screen.getByText('退回我的简历').closest('button')!;
 await waitFor(()=>expect(returnButton).toBeEnabled());
 fireEvent.click(returnButton);
 const notice=await screen.findByText(/^已有评分和操作记录会保留/);
 expect(notice.closest('[role="dialog"]')).toHaveAccessibleName('退回我的简历');
});
it('does not offer an actionable return for a score that has left screening',async()=>{
 vi.mocked(talentApi.screeningCandidates).mockResolvedValue([]);
 mountSavedRun();
 await screen.findByText('73.00');
 expect(screen.getByText('退回我的简历').closest('button')).toBeDisabled();
 expect(screen.queryByText('结果已冻结')).not.toBeInTheDocument();
});
it('removes scoring chrome and feedback while keeping candidate actions',async()=>{
 mountSavedRun();
 await screen.findByText('73.00');
 for(const label of ['反馈判断','证据型评分台','结果已冻结','本页评分概览','评分记录','刷新评分'])expect(screen.queryByText(label)).not.toBeInTheDocument();
 expect(screen.getByText('查看简历')).toBeInTheDocument();
 expect(screen.getByLabelText('查看候选人的评分详情')).toBeInTheDocument();
});

it('does not let administrator visibility advance another HR frozen result',async()=>{
 session.actor={userId:'admin',permissions:['recruitment.manage'],superAdmin:true};
 mountSavedRun();
 await screen.findByText('73.00');
 await waitFor(()=>expect(recruitmentApi.jdProcessingAccess).toHaveBeenCalledWith('jd-version'));
 expect(screen.getByText('推荐到流程').closest('button')).toBeDisabled();
});

it('keeps result actions visible while their server checks are loading',async()=>{
 session.actor={userId:'hr',permissions:['matching.run','recruitment.manage'],superAdmin:false};
 const access=deferred<{processingAllowed:boolean}>();
 const pool=deferred<Awaited<ReturnType<typeof talentApi.poolStatuses>>>();
 vi.mocked(recruitmentApi.jdProcessingAccess).mockReturnValue(access.promise);
 vi.mocked(talentApi.poolStatuses).mockReturnValue(pool.promise);
 mountSavedRun();
 await screen.findByText('73.00');
 const advance=screen.getByText('推荐到流程').closest('button')!;
 const share=screen.getByText('转入共享库').closest('button')!;
 expect(advance).toBeDisabled();
 expect(share).toBeDisabled();
 access.resolve({processingAllowed:true});
 pool.resolve([{candidateId:'candidate',resumeVersionId:'resume',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}]);
 await waitFor(()=>expect(advance).toBeEnabled());
 await waitFor(()=>expect(share).toBeEnabled());
});

it('lets the owner or explicit collaborator process a restored result without reselecting its JD',async()=>{
 session.actor={userId:'collaborator',permissions:['recruitment.manage'],superAdmin:false};
 vi.mocked(recruitmentApi.jdProcessingAccess).mockResolvedValue({processingAllowed:true});
 mountSavedRun();
 await waitFor(()=>expect(recruitmentApi.jdProcessingAccess).toHaveBeenCalledWith('jd-version'),{timeout:5000});
 expect(await screen.findByText('推荐到流程',{}, {timeout:5000})).toBeInTheDocument();
});

it('人才池带入候选人及岗位后无需重新逐人选择',async()=>{
 enablePersonalProfile();
 vi.mocked(demandApi.jd).mockResolvedValue({id:'jd',demandId:'demand',title:'关联岗位',currentVersionNo:1} as Awaited<ReturnType<typeof demandApi.jd>>);
 render(<MemoryRouter initialEntries={[{pathname:'/matching',state:{candidateIds:['candidate'],jdId:'jd'}}]}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MatchingWorkspacePage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 expect(screen.queryByText('本次筛选候选人 · 1 人')).not.toBeInTheDocument();
 choosePersonal();
 const run=await screen.findByRole('button',{name:'重新评分'});
 await waitFor(()=>expect(run).toBeEnabled());
 fireEvent.click(run);
 await waitFor(()=>expect(matchingApi.createRun).toHaveBeenCalledWith(expect.objectContaining({resumeVersionIds:['resume'],jdVersionId:'jd-version'})));
});

it('未选岗位时禁用个人评分，选岗后可按个人方案评分',async()=>{
 enablePersonalProfile();
 vi.mocked(demandApi.scoringDefaults).mockRejectedValueOnce(new Error('暂不可用'));
 render(<MemoryRouter initialEntries={[{pathname:'/matching',state:{candidateIds:['candidate']}}]}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MatchingWorkspacePage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 const run=screen.getByRole('button',{name:'重新评分'});
 expect(run).toBeDisabled();
 fireEvent.click(await screen.findByText('重试默认权重'));
 vi.mocked(demandApi.jds).mockResolvedValue({items:[{id:'jd',title:'来源岗位',currentVersionNo:1} as Awaited<ReturnType<typeof demandApi.jd>>],hasMore:false,nextCursor:null});
 fireEvent.click(screen.getByLabelText('选择招聘岗位'));
 fireEvent.change(screen.getByLabelText('搜索招聘岗位'),{target:{value:'来源'}});
 fireEvent.click((await screen.findByText('来源岗位')).closest('button')!);
 choosePersonal();
 await waitFor(()=>expect(run).toBeEnabled());
 fireEvent.click(run);
 await waitFor(()=>expect(matchingApi.createRun).toHaveBeenCalledWith(expect.objectContaining({resumeVersionIds:['resume']})));
});

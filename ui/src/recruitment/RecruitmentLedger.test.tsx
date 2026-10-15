import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { cleanup, configure, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { talentApi, type Candidate, type ResumeFactBundle } from '../talent/api';
import type { RecruitmentApplication } from './api';
import { Applications } from './RecruitmentLedger';

vi.mock('./LedgerActionDialog',()=>({LedgerActionDialog:({roundNo}:{roundNo?:number})=><div role="dialog">选中轮次 {roundNo}</div>}));
vi.mock('../demand/JdSelector',()=>({JdSelector:()=>null}));
vi.mock('./ClientFilter',()=>({ClientFilter:()=>null}));
vi.mock('../display/BusinessReferences',()=>({JobName:()=> '岗位',UserName:()=> '负责人',PeopleSelect:()=>null,ResumeVersionName:({versionId}:{versionId:string})=>versionId}));
vi.mock('../talent/ResumeReviewPanel',()=>({ResumeReviewPanel:({bundle}:{bundle:ResumeFactBundle})=><span>{bundle.simpleFacts[0]?.nameValue}</span>}));

const person:Candidate={id:'candidate',fullName:'历史简历候选人',primaryEmail:null,primaryPhone:'12345',ownerUserId:'owner',ownerDepartmentId:'department',status:'ACTIVE',version:1,createdAt:'2026-09-01T00:00:00Z',updatedAt:'2026-09-01T00:00:00Z'};
const application=(id:string,version:string):RecruitmentApplication=>({id,candidateId:'candidate',resumeVersionId:version,jdId:'jd',jdVersionId:'jd-version',demandId:'demand',workflowTemplateVersionId:'workflow',matchPlanVersionId:'plan',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',outcome:null,ownerUserId:'owner',ownerDepartmentId:'department',terminalAt:null,version:1,createId:'owner',createTime:'2026-09-01T00:00:00Z',updateId:'owner',updateTime:'2026-09-01T00:00:00Z'});
const summary=(version:string):ResumeFactBundle=>({snapshot:{resumeVersionId:version,gender:'FEMALE',sourceType:'AI_PARSE'} as ResumeFactBundle['snapshot'],workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[{id:'unknown',factType:'UNKNOWN',nameValue:'时间待核实'}] as ResumeFactBundle['simpleFacts']});
configure({asyncUtilTimeout:5_000});
vi.setConfig({testTimeout:15_000});
const clients:QueryClient[]=[];
afterEach(()=>{cleanup();clients.forEach(client=>client.clear());clients.length=0;vi.restoreAllMocks();});

function mount(items:RecruitmentApplication[]){
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});clients.push(client);
  const onFilters=vi.fn();
  const props={loading:false,error:null,filters:{},onFilters,onOpen:vi.fn(),page:1,hasPrevious:false,hasNext:false,onPrevious:vi.fn(),onNext:vi.fn()};
  const content=(rows:RecruitmentApplication[])=><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><Applications pageSize={20} onPageSizeChange={vi.fn()} {...props} items={rows}/></QueryClientProvider></ConfigProvider>;
  const view=render(content(items));
  return {client,view,content,onFilters};
}

it('按候选人姓名提交自定义搜索条件',()=>{
  const {onFilters}=mount([]);
  fireEvent.change(screen.getByRole('searchbox',{name:'搜索候选人姓名'}),{target:{value:'  林雨桐  '}});
  fireEvent.click(screen.getByRole('button',{name:/^搜\s*索$/}));
  expect(onFilters).toHaveBeenCalledWith({q:'林雨桐'});
});

it('整页批量获取候选人与申请历史版本摘要，只有打开复核才获取完整事实',async()=>{
  const people=vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([person]);
  const summaries=vi.spyOn(talentApi,'resumeVersionFactSummaries').mockResolvedValue([{resumeVersionId:'historical',facts:summary('historical')},{resumeVersionId:'newer',facts:{...summary('newer'),simpleFacts:[]}}]);
  const single=vi.spyOn(talentApi,'candidate');
  const facts=vi.spyOn(talentApi,'resumeFacts').mockResolvedValue({...summary('historical'),simpleFacts:[{id:'unknown',factType:'UNKNOWN',nameValue:'完整结构化核对信息'}] as ResumeFactBundle['simpleFacts']});
  const {client}=mount([application('a','historical'),application('b','newer')]);
  await screen.findByRole('button',{name:'复核 1 项'});
  expect(people).toHaveBeenCalledExactlyOnceWith(['candidate']);
  expect(summaries).toHaveBeenCalledExactlyOnceWith(['historical','newer']);
  expect(screen.getByText('简历无待复核')).toBeInTheDocument();
  expect(screen.queryByText(/黑名单需授权|重试黑名单|读取标签/)).not.toBeInTheDocument();
  expect(document.querySelector('.ant-tag')?.textContent??'').not.toContain('黑名单');
  expect(single).not.toHaveBeenCalled();expect(facts).not.toHaveBeenCalled();
  expect(client.getQueryData(['talent','resume-facts','historical'])).toBeUndefined();
  fireEvent.click(screen.getByRole('button',{name:'复核 1 项'}));
  expect(await screen.findByText('完整结构化核对信息')).toBeInTheDocument();
  expect(facts).toHaveBeenCalledExactlyOnceWith('historical');
});

it('翻页重新按本页版本读取，不把无权限或未解析摘要显示为无待复核',async()=>{
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([person]);
  const summaries=vi.spyOn(talentApi,'resumeVersionFactSummaries').mockResolvedValue([{resumeVersionId:'old',facts:summary('old')}]);
  const {view,content}=mount([application('a','old')]);
  await screen.findByRole('button',{name:'复核 1 项'});
  summaries.mockResolvedValue([{resumeVersionId:'unparsed',facts:null}]);
  view.rerender(content([application('b','forbidden'),application('c','unparsed')]));
  await waitFor(()=>expect(screen.getAllByText('复核信息暂不可用')).toHaveLength(2));
  expect(summaries).toHaveBeenLastCalledWith(['forbidden','unparsed']);
  expect(screen.queryByText('简历无待复核')).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'复核 1 项'})).not.toBeInTheDocument();
});

it('摘要失败可以批量重试，完整事实失败在弹窗中独立重试',async()=>{
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([person]);
  vi.spyOn(talentApi,'resumeVersionFactSummaries').mockRejectedValueOnce(new Error('offline')).mockResolvedValue([{resumeVersionId:'old',facts:summary('old')}]);
  const facts=vi.spyOn(talentApi,'resumeFacts').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({...summary('old'),simpleFacts:[{id:'unknown',factType:'UNKNOWN',nameValue:'重试后的核对信息'}] as ResumeFactBundle['simpleFacts']});
  mount([application('a','old')]);
  fireEvent.click(await screen.findByRole('button',{name:'重试复核信息'}));
  fireEvent.click(await screen.findByRole('button',{name:'复核 1 项'}));
  fireEvent.click(await screen.findByRole('button',{name:'重试完整复核信息'}));
  expect(await screen.findByText('重试后的核对信息')).toBeInTheDocument();
  expect(facts).toHaveBeenCalledTimes(2);
});

it('空列表不发出候选人或版本摘要请求',()=>{
  const people=vi.spyOn(talentApi,'candidateBatch');
  const summaries=vi.spyOn(talentApi,'resumeVersionFactSummaries');
  mount([]);
  expect(people).not.toHaveBeenCalled();expect(summaries).not.toHaveBeenCalled();
});

it('在当前阶段与负责人之间逐行显示招聘状态',()=>{
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([person]);
  vi.spyOn(talentApi,'resumeVersionFactSummaries').mockResolvedValue([]);
  mount([
    application('active','active'),
    {...application('onboarding','onboarding'),currentStage:'OFFER',onboardingStatus:'IN_PROGRESS'},
    {...application('onboarded','onboarded'),currentStage:'ONBOARDED'},
    {...application('cancelled','cancelled'),currentStage:'TERMINATED',terminalAt:'2026-09-22T08:00:00Z'},
  ]);

  const headers=screen.getAllByRole('columnheader').map(header=>header.textContent);
  expect(headers.indexOf('当前阶段')+1).toBe(headers.indexOf('状态'));
  expect(headers.indexOf('状态')+1).toBe(headers.indexOf('负责人'));
  expect(screen.getAllByRole('cell',{name:'进行中'})).toHaveLength(1);
  expect(screen.getAllByRole('cell',{name:'已完成'})).toHaveLength(2);
  expect(screen.getAllByRole('cell',{name:'已取消'})).toHaveLength(1);
});

it('轮次显示六个可点击入口并将所选数字传给面试表单',()=>{
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([person]);
  vi.spyOn(talentApi,'resumeVersionFactSummaries').mockResolvedValue([]);
  mount([{...application('a','old'),currentStage:'IN_PROCESS',currentStepCode:'INTERVIEW_ROUND_1',currentStepRoundNo:2,activeInterviewRoundNos:[2]}]);
  expect(screen.getByRole('button',{name:'第 2 轮面试'})).toHaveAttribute('data-active','true');
  for(const round of [1,2,3,4,5,6])expect(screen.getByRole('button',{name:`第 ${round} 轮面试`})).toBeEnabled();
  fireEvent.click(screen.getByRole('button',{name:'第 1 轮面试'}));
  expect(screen.getByRole('dialog')).toHaveTextContent('选中轮次 1');
});

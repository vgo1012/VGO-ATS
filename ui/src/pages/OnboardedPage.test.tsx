import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { recruitmentApi } from '../recruitment/api';
import { talentApi } from '../talent/api';
import { OnboardedPage } from './OnboardedPage';

vi.mock('../demand/api',()=>({demandApi:{jds:vi.fn().mockResolvedValue({items:[],hasMore:false,nextCursor:null})}}));
vi.mock('../demand/JdSelector',()=>({JdSelector:()=>null}));
// 保留一个条件按钮，用于验证即时筛选与关键词输入的协同；其余用例不依赖筛选组件细节。
vi.mock('../talent/TalentFilters',()=>({TalentFilters:({onApply}:{onApply:(filters:never)=>void})=><button onClick={()=>onApply({education:'本科',experience:'不限',age:'不限'} as never)}>本科</button>}));
vi.mock('../talent/api',()=>({talentApi:{candidateBatch:vi.fn().mockResolvedValue([{id:'candidate-1',ownerUserId:'owner-1'}]),resumeVersionFactSummaries:vi.fn().mockResolvedValue([{resumeVersionId:'resume-1',facts:{snapshot:{},educations:[{degreeName:'本科'}],workExperiences:[],skills:[]}}]),resumeVersionSources:vi.fn().mockResolvedValue([{resumeVersionId:'resume-1',source:{sourceChannelName:'内推'}}]),resumeVersions:vi.fn().mockResolvedValue({items:[{id:'resume-1',createdAt:'2026-09-01T00:00:00Z'}]})}}));
vi.mock('../recruitment/api', () => ({ recruitmentApi: { applications: vi.fn(), countApplications: vi.fn() } }));
vi.mock('../display/BusinessReferences', () => ({ JobName: ({jdId}:{jdId:string}) => <>{`岗位-${jdId}`}</>, UserName: ({id}:{id:string}) => <>{`负责人-${id}`}</> }));
vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr-a',permissions:['recruitment.manage']}})}));
vi.mock('../talent/CandidateDetails',()=>({CandidateDialog:({candidateId,onClose,compact}:{candidateId?:string;onClose:()=>void;compact:boolean})=>candidateId?<div role="dialog" aria-label="简历详情">{candidateId}{compact?'统一展示':''}<button onClick={onClose}>关闭详情</button></div>:null}));

const application=(index:number, resumeVersionId=`resume-${index}`)=>({ id:`app-${index}`, candidateId:`candidate-${index}`, demandId:`demand-${index}`, jdId:`jd-${index}`, jdVersionId:`jdv-${index}`, resumeVersionId, workflowTemplateVersionId:'workflow-1', matchPlanVersionId:'plan-1', recruitmentType:'INTERNAL', currentStage:'OFFER', outcome:'PASS', ownerUserId:'owner-1', ownerDepartmentId:'department-1', terminalAt:null, onboardingStatus:'IN_PROGRESS', onboardingStartedAt:'2026-09-01T00:00:00Z', actualOnboardedAt:null, onboardingElapsedSeconds:90000, version:1, createId:'owner-1', createTime:'2026-09-01T00:00:00Z', updateId:'owner-1', updateTime:'2026-09-02T00:00:00Z' });
const fact=(label='本科')=>({snapshot:{},educations:[{degreeName:label}],workExperiences:[],skills:[]});

// 该套件此前缺少清理，上一条用例的 DOM 与仍在挂载的组件会带入下一条，导致重复元素与请求计数虚高。
afterEach(cleanup);

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(talentApi.candidateBatch).mockResolvedValue([{id:'candidate-1',fullName:'候选人-candidate-1',ownerUserId:'owner-1'}] as never);
  vi.mocked(talentApi.resumeVersionFactSummaries).mockResolvedValue([{resumeVersionId:'resume-1',facts:fact()}] as never);
  vi.mocked(talentApi.resumeVersionSources).mockImplementation(ids=>Promise.resolve(ids.map(resumeVersionId=>({resumeVersionId,source:{sourceChannelName:'内推'}})) as never));
  vi.mocked(recruitmentApi.applications).mockResolvedValue({ items: [application(1)], nextCursor:null, hasMore:false } as never);
  vi.mocked(recruitmentApi.countApplications).mockResolvedValue(1);
});

it('requests the onboarded server page and opens application details locally without inventing an onboarding time', async () => {
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><OnboardedPage /></QueryClientProvider></ConfigProvider></MemoryRouter>);
  const table = within(screen.getByRole('table'));
  await table.findByText('候选人-candidate-1');
  expect(table.queryByText(/黑名单/)).not.toBeInTheDocument();
  await table.findByRole('cell', { name: '本科' });
  expect(screen.getByText('内推')).toBeInTheDocument();
  expect(recruitmentApi.applications).toHaveBeenCalledWith({ stage: 'ONBOARDED', onboarding: 'true', cursor: undefined, limit: 20 });
  expect(table.getByRole('columnheader', { name: '入职状态' })).toBeInTheDocument();
  expect(table.getByRole('columnheader', { name: '入职操作' })).toBeInTheDocument();
  expect(table.getByText('入职中')).toBeInTheDocument();
  expect(table.getByText('已历时 1天1小时')).toBeInTheDocument();
  expect(table.getByRole('button', { name: '确认实际入职' })).toBeInTheDocument();
  expect(table.getByRole('button', { name: '退回共享简历库' })).toBeInTheDocument();
  fireEvent.click(table.getByRole('button', { name: '查看简历' }));
  expect(await screen.findByRole('dialog', { name: '简历详情' })).toHaveTextContent('candidate-1统一展示');
  expect(screen.queryByRole('link', { name: '查看简历' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '关闭详情' }));
  fireEvent.click(table.getByRole('button', { name: '候选人-candidate-1' }));
  expect(screen.getByRole('dialog', { name: '简历详情' })).toHaveTextContent('candidate-1统一展示');
  expect(screen.queryByText(/2026-09-02/)).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('条/共1条记录')).toBeInTheDocument());
});

it('keeps an entered keyword when applying the filters without submitting the search', async () => {
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><OnboardedPage /></QueryClientProvider></ConfigProvider></MemoryRouter>);
  await within(screen.getByRole('table')).findByText('候选人-candidate-1');

  // 只输入不提交，再点条件按钮：关键词必须一并生效，否则搜索框显示着它而结果并未按它过滤。
  fireEvent.change(screen.getByRole('textbox',{name:'入职流程关键词搜索'}),{target:{value:'Java'}});
  fireEvent.click(screen.getByRole('button',{name:'本科'}));

  await waitFor(()=>expect(recruitmentApi.applications).toHaveBeenLastCalledWith(expect.objectContaining({q:'Java',degrees:'本科'})));
});

it('loads a 20-row page with three batch requests bound to historical versions', async () => {
  const items=Array.from({length:20},(_,index)=>application(index+1,`historical-resume-${index+1}`));
  vi.mocked(recruitmentApi.applications).mockResolvedValue({items,nextCursor:null,hasMore:false} as never);
  vi.mocked(recruitmentApi.countApplications).mockResolvedValue(20);
  vi.mocked(talentApi.candidateBatch).mockResolvedValue(items.map(item=>({id:item.candidateId,fullName:`批量姓名-${item.candidateId}`,ownerUserId:'owner-1'})) as never);
  vi.mocked(talentApi.resumeVersionFactSummaries).mockResolvedValue(items.map(item=>({resumeVersionId:item.resumeVersionId,facts:fact()})) as never);
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><OnboardedPage /></QueryClientProvider></ConfigProvider></MemoryRouter>);
  await screen.findByText('条/共20条记录');
  expect(talentApi.candidateBatch).toHaveBeenCalledTimes(1);
  expect(await screen.findByText('批量姓名-candidate-20')).toBeInTheDocument();
  expect(talentApi.candidateBatch).toHaveBeenCalledWith(items.map(item=>item.candidateId));
  expect(talentApi.resumeVersionFactSummaries).toHaveBeenCalledTimes(1);
  expect(talentApi.resumeVersionFactSummaries).toHaveBeenCalledWith(items.map(item=>item.resumeVersionId));
  expect(talentApi.resumeVersionSources).toHaveBeenCalledTimes(1);
  expect(talentApi.resumeVersionSources).toHaveBeenCalledWith(items.map(item=>item.resumeVersionId));

});

it('keeps authorized historical facts visible while reporting omitted candidate and resume access', async () => {
  const items=[application(1,'historical-authorized'),application(2,'historical-unparsed'),application(3,'historical-omitted')];
  vi.mocked(recruitmentApi.applications).mockResolvedValue({items,nextCursor:null,hasMore:false} as never);
  vi.mocked(talentApi.candidateBatch).mockResolvedValue([{id:'candidate-1',fullName:'已授权甲',ownerUserId:'owner-1',primaryPhone:'13800000001'},{id:'candidate-2',fullName:'已授权乙',ownerUserId:'owner-1'}] as never);
  vi.mocked(talentApi.resumeVersionFactSummaries).mockResolvedValue([{resumeVersionId:'historical-authorized',facts:fact('历史版本学历')},{resumeVersionId:'historical-unparsed',facts:null}] as never);
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><OnboardedPage /></QueryClientProvider></ConfigProvider></MemoryRouter>);
  expect(await screen.findByText('历史版本学历')).toBeInTheDocument();
  expect(screen.getAllByText('联系方式需授权')).toHaveLength(1);
  expect(screen.getByText('部分候选人或简历版本不存在，或当前账号无权查看，可在权限恢复后重试。')).toBeInTheDocument();
  expect(talentApi.resumeVersionFactSummaries).toHaveBeenCalledWith(['historical-authorized','historical-unparsed','historical-omitted']);
});

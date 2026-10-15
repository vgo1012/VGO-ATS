import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateDetails } from './CandidateDetails';

const mocks=vi.hoisted(()=>({facts:vi.fn(),correct:vi.fn()}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:{superAdmin:true,permissions:[]}})}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=><>合法归属人</>,TechnicalDetails:()=>null}));
vi.mock('../submission/api',()=>({submissionApi:{restrictions:vi.fn().mockResolvedValue([])}}));
vi.mock('./CompanyBlacklistPanel',()=>({CompanyBlacklistPanel:()=>null}));
vi.mock('./ResumeReviewPanel',()=>({ResumeReviewPanel:()=>null}));
vi.mock('../tasks/task',()=>({taskApi:{latestResume:vi.fn().mockResolvedValue(null)},taskPollingInterval:()=>false}));
vi.mock('./api',()=>({talentApi:{
  candidate:vi.fn().mockResolvedValue({id:'candidate',fullName:'基础姓名',primaryPhone:'13811110000',primaryEmail:'base@example.invalid',ownerUserId:'owner'}),
  resumeVersions:vi.fn().mockResolvedValue({items:[{id:'resume',versionNo:1,originalFileName:'resume.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null}),
  resumeSource:vi.fn().mockResolvedValue({sourceChannelName:'合法来源',receivedAt:'2026-09-01T00:00:00Z'}),
  resumeFacts:mocks.facts,correctFacts:mocks.correct,
}}));
const facts={snapshot:{id:'snapshot-old',resumeVersionId:'resume',snapshotVersion:1,fullName:'事实敏感姓名',phone:'13899990000',email:'facts@example.invalid',currentRole:'机密岗位'},skills:[{id:'skill',skillName:'专属事实技能'}],workExperiences:[{id:'work',employer:'敏感履历单位',roleName:'工程师',descriptionText:'内部履历描述',responsibilitiesJson:'[]',achievementsJson:'[]'}],projectExperiences:[],educations:[],simpleFacts:[]};
beforeEach(()=>{mocks.facts.mockResolvedValue(facts);});
afterEach(()=>{cleanup();vi.clearAllMocks();});
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDetails candidateId="candidate" canDownload={false}/></QueryClientProvider></App></ConfigProvider>);return client;}
const denied=(status:number)=>new ApiProblemError({title:'读取失败',detail:'该资料暂不可读取',status,code:status===503?'SERVICE_UNAVAILABLE':'ACCESS_DENIED',recoverable:status===503});

it.each([401,403,503])('facts %s hides only unauthorized content and recovers',async status=>{
  const client=mount();
  await screen.findByText('专属事实技能');await screen.findByText(/敏感履历单位/);
  expect(screen.queryByRole('tab',{name:/证据与可靠性/})).not.toBeInTheDocument();
  mocks.facts.mockRejectedValue(denied(status));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-facts','resume']}));
  if(status!==503){
    await waitFor(()=>expect(screen.queryByText('专属事实技能')).not.toBeInTheDocument());
    expect(screen.queryByText(/敏感履历单位/)).not.toBeInTheDocument();
    for(const text of ['基础姓名','13811110000','base@example.invalid','暂无该简历事实的查看权限'])expect(screen.getByText(text)).toBeInTheDocument();
  }else expect(screen.getByText('专属事实技能')).toBeInTheDocument();
  expect(screen.getByText('合法来源')).toBeInTheDocument();
  mocks.facts.mockResolvedValue({...facts,snapshot:{...facts.snapshot,phone:'13888880000'}});
  fireEvent.click(await screen.findByRole('button',{name:'重试解析事实'}));
  await screen.findByText('13888880000');
},15_000);

it('removes an open correction after permission loss and requires reopening with fresh facts',async()=>{
  const client=mount();await screen.findByText('事实敏感姓名');
  fireEvent.click(screen.getByRole('button',{name:'HR 更正'}));
  await screen.findByRole('region',{name:'HR 更正'});
  fireEvent.change(screen.getByLabelText('更正原因'),{target:{value:'尚未提交的旧草稿'}});
  mocks.facts.mockRejectedValue(denied(403));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-facts','resume']}));
  await waitFor(()=>expect(screen.queryByRole('region',{name:'HR 更正'})).not.toBeInTheDocument());
  expect(screen.queryByRole('button',{name:'保存更正'})).not.toBeInTheDocument();expect(mocks.correct).not.toHaveBeenCalled();
  mocks.facts.mockResolvedValue({...facts,snapshot:{...facts.snapshot,id:'snapshot-new',fullName:'恢复后的事实姓名'}});
  fireEvent.click(screen.getByRole('button',{name:'重试解析事实'}));
  await screen.findByText('恢复后的事实姓名');
  expect(screen.queryByRole('region',{name:'HR 更正'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'HR 更正'}));
  expect(screen.getByLabelText('姓名')).toHaveValue('恢复后的事实姓名');
  expect(screen.getByLabelText('更正原因')).toHaveValue('人工校正');
},15_000);

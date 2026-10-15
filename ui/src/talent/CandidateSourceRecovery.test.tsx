import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateDetails } from './CandidateDetails';

const sourceQuery=vi.hoisted(()=>vi.fn());
vi.mock('../auth/session',()=>({useSession:()=>({actor:{superAdmin:true,permissions:[]}})}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string})=><>{id==='source-old'?'旧来源招聘人':id==='source-new'?'新来源招聘人':'合法归属人'}</>,TechnicalDetails:()=>null}));
vi.mock('../submission/api',()=>({submissionApi:{restrictions:vi.fn().mockResolvedValue([])}}));
vi.mock('./CompanyBlacklistPanel',()=>({CompanyBlacklistPanel:()=>null}));
vi.mock('./ResumeReviewPanel',()=>({ResumeReviewPanel:()=>null}));
vi.mock('../tasks/task',()=>({taskApi:{latestResume:vi.fn().mockResolvedValue(null)},taskPollingInterval:()=>false}));
vi.mock('./api',()=>({talentApi:{
  candidate:vi.fn().mockResolvedValue({id:'candidate',fullName:'合法基础姓名',ownerUserId:'owner'}),
  resumeVersions:vi.fn().mockResolvedValue({items:[{id:'resume',versionNo:1,originalFileName:'授权简历.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null}),
  resumeSource:sourceQuery,
  resumeFacts:vi.fn().mockResolvedValue({snapshot:{id:'snapshot',resumeVersionId:'resume',fullName:'授权事实姓名',phone:'13811110000'},skills:[{id:'skill',skillName:'授权技能'}],workExperiences:[],projectExperiences:[],educations:[],simpleFacts:[]}),
}}));
const original={sourceChannelName:'独特旧渠道',sourceRecruiterUserId:'source-old',receivedAt:'2026-08-12T09:20:00Z'};
const restored={sourceChannelName:'恢复新渠道',sourceRecruiterUserId:'source-new',receivedAt:'2026-08-13T10:30:00Z'};
beforeEach(()=>{sourceQuery.mockResolvedValue(original);});
afterEach(()=>{cleanup();vi.clearAllMocks();});
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDetails candidateId="candidate" canDownload={false}/></QueryClientProvider></App></ConfigProvider>);return client;}
const problem=(status:number)=>new ApiProblemError({title:'来源失败',detail:'简历来源暂不可访问',status,code:status===503?'SERVICE_UNAVAILABLE':'ACCESS_DENIED',recoverable:status===503});

it.each([401,403,503])('source %s hides denied metadata only and recovers new values',async status=>{
  const client=mount();await screen.findByText('独特旧渠道');await screen.findByText('授权技能');
  expect(screen.getByText('旧来源招聘人')).toBeInTheDocument();expect(screen.getByText(/2026.*08.*12.*17:20/)).toBeInTheDocument();
  sourceQuery.mockRejectedValue(problem(status));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-source','candidate','resume']}));
  if(status!==503){
    await waitFor(()=>expect(screen.queryByText('独特旧渠道')).not.toBeInTheDocument());
    expect(screen.queryByText('旧来源招聘人')).not.toBeInTheDocument();
    expect(screen.queryByText(/2026.*08.*12.*17:20/)).not.toBeInTheDocument();
    expect(screen.queryByText('时间未记录')).not.toBeInTheDocument();
    await screen.findByText('暂无该简历来源的查看权限');
  }else{
    expect(screen.getByText('独特旧渠道')).toBeInTheDocument();expect(screen.getByText('旧来源招聘人')).toBeInTheDocument();expect(screen.getByText(/2026.*08.*12.*17:20/)).toBeInTheDocument();
  }
  for(const text of ['授权技能','授权事实姓名','13811110000','授权简历.pdf'])expect(screen.getByText(text)).toBeInTheDocument();
  sourceQuery.mockResolvedValue(restored);
  fireEvent.click(await screen.findByRole('button',{name:'重试简历来源'}));
  await screen.findByText('恢复新渠道');await screen.findByText('新来源招聘人');await screen.findByText(/2026.*08.*13.*18:30/);
},15_000);

it('shares a pending source recovery across repeated clicks',async()=>{
  const client=mount();await screen.findByText('独特旧渠道');
  sourceQuery.mockRejectedValue(problem(503));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-source','candidate','resume']}));
  let resolve!:(value:typeof restored)=>void;
  sourceQuery.mockImplementation(()=>new Promise(done=>{resolve=done;}));
  const before=sourceQuery.mock.calls.length;
  const retry=await screen.findByRole('button',{name:'重试简历来源'});
  fireEvent.click(retry);fireEvent.click(retry);
  await waitFor(()=>expect(retry).toBeDisabled());expect(sourceQuery).toHaveBeenCalledTimes(before+1);
  await act(()=>{resolve(restored);return Promise.resolve();});
  await screen.findByText('恢复新渠道');
},15_000);

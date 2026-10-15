import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateDetails } from './CandidateDetails';
import { talentApi } from './api';

const versionsQuery=vi.hoisted(()=>vi.fn());
vi.mock('../auth/session',()=>({useSession:()=>({actor:{superAdmin:true,permissions:[]}})}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=><>授权归属人</>,TechnicalDetails:()=>null}));
vi.mock('../submission/api',()=>({submissionApi:{restrictions:vi.fn().mockResolvedValue([])}}));
vi.mock('./CompanyBlacklistPanel',()=>({CompanyBlacklistPanel:()=>null}));
vi.mock('./ResumeReviewPanel',()=>({ResumeReviewPanel:()=>null}));
vi.mock('../tasks/task',()=>({taskApi:{latestResume:vi.fn().mockResolvedValue(null)},taskPollingInterval:()=>false}));
vi.mock('./api',()=>({talentApi:{
  candidate:vi.fn().mockResolvedValue({id:'candidate',fullName:'授权基础姓名',ownerUserId:'owner'}),
  resumeVersions:versionsQuery,
  resumeSource:vi.fn().mockResolvedValue({sourceChannelName:'独立授权来源',receivedAt:'2026-09-01T00:00:00Z'}),
  resumeFacts:vi.fn().mockResolvedValue({snapshot:{id:'snapshot',resumeVersionId:'resume',fullName:'授权事实姓名',phone:'13811110000'},skills:[{id:'skill',skillName:'独立授权技能'}],workExperiences:[],projectExperiences:[],educations:[],simpleFacts:[]}),
}}));
const original={items:[{id:'resume',versionNo:2,originalFileName:'旧版本二.pdf',createdAt:'2026-09-02T00:00:00Z'},{id:'resume-old',versionNo:1,originalFileName:'旧版本一.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null};
const restored={items:[{id:'resume',versionNo:2,originalFileName:'恢复的新文件.pdf',createdAt:'2026-09-02T00:00:00Z'},{id:'resume-old',versionNo:1,originalFileName:'恢复的已选文件.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null};
beforeEach(()=>{versionsQuery.mockResolvedValue(original);});
afterEach(()=>{cleanup();vi.clearAllMocks();});
function mount(canDownload=true){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDetails candidateId="candidate" canDownload={canDownload}/></QueryClientProvider></App></ConfigProvider>);return client;}
const problem=(status:number)=>new ApiProblemError({title:'版本读取失败',detail:'简历版本列表暂不可访问',status,code:status===503?'SERVICE_UNAVAILABLE':'ACCESS_DENIED',recoverable:status===503});

it('exposes version selection and its active state without download permission',async()=>{
  mount(false);
  const latest=await screen.findByRole('button',{name:'查看简历 V2',pressed:true});
  const previous=screen.getByRole('button',{name:'查看简历 V1',pressed:false});
  expect(screen.queryByRole('button',{name:'受控下载'})).not.toBeInTheDocument();
  fireEvent.click(previous);
  await waitFor(()=>expect(previous).toHaveAttribute('aria-pressed','true'));
  expect(latest).toHaveAttribute('aria-pressed','false');
  expect(talentApi.resumeSource).toHaveBeenCalledWith('candidate','resume-old');
  fireEvent.click(latest);
  await waitFor(()=>expect(latest).toHaveAttribute('aria-pressed','true'));
  expect(previous).toHaveAttribute('aria-pressed','false');
},15_000);

it.each([401,403,503])('versions %s hides denied filenames and downloads without removing independently authorized details',async status=>{
  const client=mount();await screen.findByText('旧版本二.pdf');await screen.findByText('旧版本一.pdf');await screen.findByText('独立授权技能');
  expect(screen.getAllByRole('button',{name:'受控下载'})).toHaveLength(2);
  fireEvent.click(screen.getByText('旧版本一.pdf'));
  await waitFor(()=>expect(talentApi.resumeSource).toHaveBeenCalledWith('candidate','resume-old'));
  expect(screen.getByText('旧版本一.pdf').closest('tr')).toHaveClass('ant-table-row-selected');
  const firstVersionReads=vi.mocked(talentApi.resumeSource).mock.calls.filter(([,version])=>version==='resume').length;
  versionsQuery.mockRejectedValue(problem(status));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-versions','candidate']}));
  if(status!==503){
    await waitFor(()=>expect(screen.queryByText('旧版本二.pdf')).not.toBeInTheDocument());
    expect(screen.queryByText('旧版本一.pdf')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('button',{name:'受控下载'})).toHaveLength(0);
    await screen.findByText('暂无该候选人简历版本列表的查看权限');
  }else{
    expect(screen.getByText('旧版本二.pdf')).toBeInTheDocument();expect(screen.getByText('旧版本一.pdf')).toBeInTheDocument();
    expect(screen.getAllByRole('button',{name:'受控下载'})).toHaveLength(2);
  }
  for(const text of ['独立授权来源','独立授权技能','授权事实姓名','13811110000'])expect(screen.getByText(text)).toBeInTheDocument();
  versionsQuery.mockResolvedValue(restored);
  fireEvent.click(await screen.findByRole('button',{name:'重试简历版本'}));
  await screen.findByText('恢复的新文件.pdf');
  expect(screen.queryByText('旧版本二.pdf')).not.toBeInTheDocument();expect(screen.queryByText('旧版本一.pdf')).not.toBeInTheDocument();
  expect(screen.getAllByRole('button',{name:'受控下载'})).toHaveLength(2);
  expect(screen.getByText('恢复的已选文件.pdf').closest('tr')).toHaveClass('ant-table-row-selected');
  expect(vi.mocked(talentApi.resumeSource).mock.calls.filter(([,version])=>version==='resume')).toHaveLength(firstVersionReads);
},15_000);

it('shares one slow version recovery and disables its retry button',async()=>{
  const client=mount();await screen.findByText('旧版本二.pdf');
  versionsQuery.mockRejectedValue(problem(503));
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-versions','candidate']}));
  let resolve!:(value:typeof restored)=>void;
  versionsQuery.mockImplementation(()=>new Promise(done=>{resolve=done;}));
  const before=versionsQuery.mock.calls.length;
  const retry=await screen.findByRole('button',{name:'重试简历版本'});
  fireEvent.click(retry);fireEvent.click(retry);
  await waitFor(()=>expect(retry).toBeDisabled());expect(versionsQuery).toHaveBeenCalledTimes(before+1);
  await act(()=>{resolve(restored);return Promise.resolve();});
  await screen.findByText('恢复的新文件.pdf');
},15_000);

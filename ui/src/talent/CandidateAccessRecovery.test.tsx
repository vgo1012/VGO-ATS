import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateDialog } from './CandidateDetails';

const mocks=vi.hoisted(()=>({candidate:vi.fn(),versions:vi.fn(),source:vi.fn(),facts:vi.fn(),latest:vi.fn(),task:vi.fn(),correct:vi.fn()}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:{superAdmin:true,permissions:[]}})}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=><>档案归属人</>,TechnicalDetails:()=>null}));
vi.mock('../submission/api',()=>({submissionApi:{restrictions:vi.fn().mockResolvedValue([])}}));
vi.mock('./CompanyBlacklistPanel',()=>({CompanyBlacklistPanel:()=>null}));
vi.mock('./ResumeReviewPanel',()=>({ResumeReviewPanel:()=>null}));
vi.mock('../tasks/task',()=>({taskApi:{latestResume:mocks.latest,get:mocks.task},taskPollingInterval:()=>false}));
vi.mock('./api',()=>({talentApi:{candidate:mocks.candidate,resumeVersions:mocks.versions,resumeSource:mocks.source,resumeFacts:mocks.facts,correctFacts:mocks.correct}}));
const candidate={id:'candidate',fullName:'缓存档案姓名',primaryPhone:'13811110000',ownerUserId:'owner'};
beforeEach(()=>{
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  mocks.candidate.mockResolvedValue(candidate);
  mocks.versions.mockResolvedValue({items:[{id:'resume',versionNo:1,originalFileName:'缓存简历文件.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null});
  mocks.source.mockResolvedValue({sourceChannelName:'缓存来源渠道',receivedAt:'2026-09-01T00:00:00Z'});
  mocks.facts.mockResolvedValue({snapshot:{id:'snapshot',resumeVersionId:'resume',fullName:'事实姓名',phone:'13899990000'},skills:[{id:'skill',skillName:'缓存事实技能'}],workExperiences:[],projectExperiences:[],educations:[],simpleFacts:[]});
  mocks.latest.mockResolvedValue({taskId:'task'});mocks.task.mockResolvedValue({taskId:'task',status:'FAILED',progressPercent:0,attemptCount:1,maxAttempts:3,retryable:false});
});
afterEach(()=>{cleanup();vi.clearAllMocks();});
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDialog candidateId="candidate" open motionFrom={undefined} canDownload onClose={()=>undefined}/></QueryClientProvider></App></ConfigProvider>);return client;}
const error=(status:number)=>new ApiProblemError({title:'档案读取失败',detail:'当前候选人档案暂不可访问',status,code:status===503?'SERVICE_UNAVAILABLE':'ACCESS_DENIED',recoverable:status===503});

it.each([401,403,503])('candidate refresh %s respects access and allows explicit recovery',async status=>{
  const client=mount();
  await screen.findByRole('dialog',{name:'缓存档案姓名'});await screen.findByText('缓存事实技能');
  await waitFor(()=>expect(client.isFetching()).toBe(0));
  mocks.candidate.mockRejectedValue(error(status));
  await act(()=>client.invalidateQueries({queryKey:['talent','candidate','candidate']}));
  if(status!==503){
    await screen.findByRole('dialog',{name:'候选人详情'});
    expect(screen.queryByRole('region',{name:'关键资料'})).not.toBeInTheDocument();
    for(const text of ['缓存档案姓名','13899990000','缓存来源渠道','缓存简历文件.pdf','缓存事实技能'])expect(screen.queryByText(text)).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'受控下载'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'HR 更正'})).not.toBeInTheDocument();
    const counts=[mocks.versions,mocks.source,mocks.facts,mocks.latest,mocks.task].map(mock=>mock.mock.calls.length);
    await act(()=>Promise.all([['talent','resume-versions'],['talent','resume-source'],['talent','resume-facts'],['tasks']].map(queryKey=>client.invalidateQueries({queryKey}))));
    expect([mocks.versions,mocks.source,mocks.facts,mocks.latest,mocks.task].map(mock=>mock.mock.calls.length)).toEqual(counts);
  }else{
    expect(screen.getByRole('dialog',{name:'缓存档案姓名'})).toBeInTheDocument();
    expect(screen.getByText('13899990000')).toBeInTheDocument();
  }
  mocks.candidate.mockResolvedValue({...candidate,fullName:'恢复后的档案姓名'});
  fireEvent.click(await screen.findByRole('button',{name:'重试候选人详情'}));
  await screen.findByRole('dialog',{name:'恢复后的档案姓名'});
  await screen.findByRole('region',{name:'关键资料'});
},15_000);

it('closes correction on candidate denial and does not reopen it automatically',async()=>{
  const client=mount();await screen.findByRole('dialog',{name:'缓存档案姓名'});await screen.findByText('事实姓名');
  fireEvent.click(screen.getByRole('button',{name:'HR 更正'}));
  await screen.findByRole('region',{name:'HR 更正'});
  mocks.candidate.mockRejectedValue(error(403));
  await act(()=>client.invalidateQueries({queryKey:['talent','candidate','candidate']}));
  await waitFor(()=>expect(screen.queryByRole('region',{name:'HR 更正'})).not.toBeInTheDocument());
  expect(screen.queryByRole('button',{name:'保存更正'})).not.toBeInTheDocument();expect(mocks.correct).not.toHaveBeenCalled();
  mocks.candidate.mockResolvedValue({...candidate,fullName:'恢复后的档案姓名'});
  fireEvent.click(await screen.findByRole('button',{name:'重试候选人详情'}));
  await screen.findByRole('dialog',{name:'恢复后的档案姓名'});
  expect(screen.queryByRole('region',{name:'HR 更正'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'HR 更正'})).toBeEnabled();
},15_000);

it('stops child reads while closed and refreshes invalidated data when reopened',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const View=({open}:{open:boolean})=><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDialog candidateId="candidate" open={open} motionFrom={undefined} canDownload onClose={()=>undefined}/></QueryClientProvider></App></ConfigProvider>;
  const view=render(<View open/>);
  await screen.findByRole('dialog',{name:'缓存档案姓名'});
  await screen.findByText('缓存事实技能');
  await waitFor(()=>expect(client.isFetching()).toBe(0));
  view.rerender(<View open={false}/>);
  await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  const reads=[mocks.versions,mocks.source,mocks.facts,mocks.latest,mocks.task];
  const counts=reads.map(mock=>mock.mock.calls.length);
  await act(()=>Promise.all([['talent'],['tasks']].map(queryKey=>client.invalidateQueries({queryKey}))));
  expect(reads.map(mock=>mock.mock.calls.length)).toEqual(counts);
  expect(client.getQueryData(['talent','resume-source','candidate','resume'])).toBeDefined();
  view.rerender(<View open/>);
  await waitFor(()=>{for(const [index,read] of reads.entries())expect(read.mock.calls.length).toBeGreaterThan(counts[index]!);});
},15_000);

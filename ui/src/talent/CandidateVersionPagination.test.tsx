import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateDetails } from './CandidateDetails';

const mocks=vi.hoisted(()=>({versions:vi.fn(),source:vi.fn()}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:{superAdmin:true,permissions:[]}})}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=><>授权归属人</>,TechnicalDetails:()=>null}));
vi.mock('../submission/api',()=>({submissionApi:{restrictions:vi.fn().mockResolvedValue([])}}));
vi.mock('./CompanyBlacklistPanel',()=>({CompanyBlacklistPanel:()=>null}));
vi.mock('./ResumeReviewPanel',()=>({ResumeReviewPanel:()=>null}));
vi.mock('../tasks/task',()=>({taskApi:{latestResume:vi.fn().mockResolvedValue(null)},taskPollingInterval:()=>false}));
vi.mock('./api',()=>({talentApi:{
  candidate:vi.fn().mockResolvedValue({id:'candidate',fullName:'授权候选人',ownerUserId:'owner'}),resumeVersions:mocks.versions,resumeSource:mocks.source,
  resumeFacts:vi.fn().mockResolvedValue({snapshot:{id:'snapshot',resumeVersionId:'first',fullName:'授权事实姓名'},skills:[{id:'skill',skillName:'独立授权技能'}],workExperiences:[],projectExperiences:[],educations:[],simpleFacts:[]}),
}}));
const first={items:[{id:'first',versionNo:21,originalFileName:'首批版本.pdf',createdAt:'2026-09-02T00:00:00Z'}],hasMore:true,nextCursor:'older'};
const second={items:[{id:'second',versionNo:1,originalFileName:'历史版本.pdf',createdAt:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null};
beforeEach(()=>{mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>Promise.resolve(cursor?second:first));mocks.source.mockResolvedValue({sourceChannelName:'独立授权来源',receivedAt:'2026-09-01T00:00:00Z'});});
afterEach(()=>{cleanup();vi.clearAllMocks();});
function mount(client=new QueryClient({defaultOptions:{queries:{retry:false}}})){render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><CandidateDetails candidateId="candidate" canDownload/></QueryClientProvider></App></ConfigProvider>);}
const failure=(status:number)=>new ApiProblemError({title:'版本读取失败',detail:'版本追加读取失败',status,code:status===403?'ACCESS_DENIED':'SERVICE_UNAVAILABLE',recoverable:status===503});

it('keeps paginated history separate from the shared single-page cache',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const key=['talent','resume-versions','candidate'];
  const cached={...first,items:[{...first.items[0],originalFileName:'普通查询缓存.pdf'}]};
  client.setQueryData(key,cached);
  mount(client);
  await screen.findByText('首批版本.pdf');
  expect(screen.queryByText('普通查询缓存.pdf')).not.toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button',{name:'加载更多简历版本'}));
  await screen.findByText('历史版本.pdf');
  expect(client.getQueryData(key)).toEqual(cached);
},15_000);

it('loads older versions with a bounded cursor and keeps selection until the user chooses the older file',async()=>{
  mount();await screen.findByText('首批版本.pdf');await screen.findByText('独立授权技能');
  fireEvent.click(await screen.findByRole('button',{name:'加载更多简历版本'}));
  await screen.findByText('历史版本.pdf');
  expect(mocks.versions).toHaveBeenCalledWith('candidate','older',20);
  expect(screen.getByText('首批版本.pdf').closest('tr')).toHaveClass('ant-table-row-selected');
  fireEvent.click(screen.getByText('历史版本.pdf'));
  await waitFor(()=>expect(mocks.source).toHaveBeenCalledWith('candidate','second'));
  expect(screen.getByText('历史版本.pdf').closest('tr')).toHaveClass('ant-table-row-selected');
  expect(screen.queryByRole('button',{name:'加载更多简历版本'})).not.toBeInTheDocument();
},15_000);

it('retries only the failed append after 503 while keeping first-page content and selection',async()=>{
  mount();await screen.findByText('首批版本.pdf');
  mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>cursor?Promise.reject(failure(503)):Promise.resolve(first));
  fireEvent.click(await screen.findByRole('button',{name:'加载更多简历版本'}));
  const retry=await screen.findByRole('button',{name:'重试简历版本'});
  expect(screen.getByText('首批版本.pdf')).toBeInTheDocument();
  const firstReads=mocks.versions.mock.calls.filter(([,cursor])=>!cursor).length;
  mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>Promise.resolve(cursor?second:first));
  fireEvent.click(retry);await screen.findByText('历史版本.pdf');
  expect(mocks.versions.mock.calls.filter(([,cursor])=>!cursor)).toHaveLength(firstReads);
  expect(screen.getByText('首批版本.pdf').closest('tr')).toHaveClass('ant-table-row-selected');
},15_000);

it('hides every cached file after an append is forbidden and recovers the successful pages explicitly',async()=>{
  mount();await screen.findByText('首批版本.pdf');await screen.findByText('独立授权技能');
  mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>cursor?Promise.reject(failure(403)):Promise.resolve(first));
  fireEvent.click(await screen.findByRole('button',{name:'加载更多简历版本'}));
  await screen.findByText('暂无该候选人简历版本列表的查看权限');
  expect(screen.queryByText('首批版本.pdf')).not.toBeInTheDocument();expect(screen.queryAllByRole('button',{name:'受控下载'})).toHaveLength(0);
  expect(screen.getByText('独立授权技能')).toBeInTheDocument();expect(screen.getByText('独立授权来源')).toBeInTheDocument();
  const firstReads=mocks.versions.mock.calls.filter(([,cursor])=>!cursor).length;
  mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>Promise.resolve(cursor?second:{...first,items:[{...first.items[0],originalFileName:'恢复首批.pdf'}]}));
  fireEvent.click(await screen.findByRole('button',{name:'重试简历版本'}));
  await screen.findByText('恢复首批.pdf');
  expect(mocks.versions.mock.calls.filter(([,cursor])=>!cursor)).toHaveLength(firstReads+1);
  expect(screen.queryByText('首批版本.pdf')).not.toBeInTheDocument();
},15_000);

it('disables repeated load-more clicks while one older page is pending',async()=>{
  mount();await screen.findByText('首批版本.pdf');
  let resolve!:(value:typeof second)=>void;
  mocks.versions.mockImplementation((_candidate:string,cursor?:string)=>cursor?new Promise(done=>{resolve=done;}):Promise.resolve(first));
  const more=await screen.findByRole('button',{name:'加载更多简历版本'});
  fireEvent.click(more);fireEvent.click(more);
  await waitFor(()=>expect(more).toBeDisabled());expect(mocks.versions.mock.calls.filter(([,cursor])=>cursor==='older')).toHaveLength(1);
  await act(()=>{resolve(second);return Promise.resolve();});await screen.findByText('历史版本.pdf');
},15_000);

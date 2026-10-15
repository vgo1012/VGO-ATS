import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { OnboardedPage } from './OnboardedPage';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr-a'}})}));
const mocks=vi.hoisted(()=>({candidate:vi.fn(),facts:vi.fn(),source:vi.fn()}));
vi.mock('../talent/api',()=>({talentApi:{candidateBatch:mocks.candidate,resumeVersionFactSummaries:mocks.facts,resumeVersionSources:mocks.source}}));
vi.mock('../demand/JdSelector',()=>({JdSelector:()=>null}));
vi.mock('../display/BusinessReferences',()=>({JobName:()=><>授权的岗位引用</>,UserName:({id}:{id:string})=><>{id==='candidate-owner'?'候选归属专员':'招聘负责人'}</>}));
vi.mock('../talent/CandidateDetails',()=>({CandidateDialog:({candidateId}:{candidateId?:string})=>candidateId?<div role="dialog">授权的简历详情</div>:null}));
vi.mock('../recruitment/api',()=>({recruitmentApi:{applications:vi.fn().mockResolvedValue({items:[{id:'app',candidateId:'candidate',resumeVersionId:'resume',jdId:'jd',demandId:'demand',ownerUserId:'recruiter'}],hasMore:false,nextCursor:null}),countApplications:vi.fn().mockResolvedValue(1)}}));

const candidate={id:'candidate',fullName:'授权的候选引用',ownerUserId:'candidate-owner',primaryPhone:'13812349876'};
const facts={snapshot:{age:31,gender:'FEMALE',createdAt:'2026-09-01T00:00:00Z'},educations:[{degreeName:'硕士'}],workExperiences:[],skills:[{id:'permission-skill',skillName:'权限测试技能'}]};
const source={sourceChannelName:'专属内推渠道'};
beforeEach(()=>{mocks.candidate.mockResolvedValue([candidate]);mocks.facts.mockResolvedValue([{resumeVersionId:'resume',facts}]);mocks.source.mockResolvedValue([{resumeVersionId:'resume',source}]);});
afterEach(()=>{cleanup();vi.clearAllMocks();});

const cases=(['candidate','facts','source'] as const).flatMap(kind=>[401,403,503].map(status=>({kind,status})));
it('removes a source omitted by a successful refresh and retries a missing source record',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><OnboardedPage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  await screen.findByText('专属内推渠道');
  mocks.source.mockResolvedValue([]);
  await act(()=>client.invalidateQueries({queryKey:['talent','resume-source','by-version',['resume']]}));
  await waitFor(()=>expect(screen.queryByText('专属内推渠道')).not.toBeInTheDocument());
  expect(screen.getByText('来源需授权')).toBeInTheDocument();
  expect(screen.getByText('13812349876')).toBeInTheDocument();
  mocks.source.mockResolvedValue([{resumeVersionId:'resume',source:null}]);
  fireEvent.click(screen.getByRole('button',{name:'重试候选人资料'}));
  await screen.findByText('来源未提供');
  await waitFor(()=>expect(screen.getByRole('button',{name:'重试候选人资料'})).toBeEnabled());
  mocks.source.mockResolvedValue([{resumeVersionId:'resume',source}]);
  fireEvent.click(screen.getByRole('button',{name:'重试候选人资料'}));
  await screen.findByText('专属内推渠道');
  await waitFor(()=>expect(screen.queryByRole('button',{name:'重试候选人资料'})).not.toBeInTheDocument());
},15_000);

it.each(cases)('$kind refresh $status protects only that source and can recover',async({kind,status})=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><OnboardedPage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  const data={candidate:['授权的候选引用','13812349876','候选归属专员'],facts:['31 岁','硕士','权限测试技能'],source:['专属内推渠道']};
  for(const text of Object.values(data).flat())await screen.findByText(text);
  mocks[kind].mockRejectedValue(new ApiProblemError({title:'资料读取失败',detail:'当前资料暂不可访问',status,code:status===503?'SERVICE_UNAVAILABLE':'ACCESS_DENIED',recoverable:status===503}));
  const key=kind==='candidate'?['talent','candidates','onboarded',['candidate']]:kind==='facts'?['talent','candidate-resume-summaries','by-version',['resume']]:['talent','resume-source','by-version',['resume']];
  await act(()=>client.invalidateQueries({queryKey:key}));
  for(const other of ['candidate','facts','source'] as const){
    for(const text of data[other]){
      if(status!==503&&other===kind)await waitFor(()=>expect(screen.queryByText(text)).not.toBeInTheDocument());
      else expect(screen.getByText(text)).toBeInTheDocument();
    }
  }
  expect(screen.getByText('授权的岗位引用')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'查看简历'}));
  expect(await screen.findByRole('dialog')).toHaveTextContent('授权的简历详情');
  mocks[kind].mockResolvedValue(kind==='candidate'?[candidate]:kind==='facts'?[{resumeVersionId:'resume',facts}]:[{resumeVersionId:'resume',source}]);
  fireEvent.click(screen.getByRole('button',{name:'重试候选人资料'}));
  for(const text of data[kind])await screen.findByText(text);
  await waitFor(()=>expect(screen.queryByRole('button',{name:'重试候选人资料'})).not.toBeInTheDocument());
},15_000);

it('disables repeated recovery while candidate details are loading',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><OnboardedPage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  await screen.findByText('13812349876');
  mocks.candidate.mockRejectedValue(new ApiProblemError({title:'无权读取',detail:'候选人资料需授权',status:403,code:'ACCESS_DENIED',recoverable:false}));
  await act(()=>client.invalidateQueries({queryKey:['talent','candidates','onboarded',['candidate']]}));
  let resolve!:(value:(typeof candidate)[])=>void;
  mocks.candidate.mockImplementation(()=>new Promise(done=>{resolve=done;}));
  const before=mocks.candidate.mock.calls.length;
  const retry=await screen.findByRole('button',{name:'重试候选人资料'});
  fireEvent.click(retry);fireEvent.click(retry);
  await waitFor(()=>expect(retry).toBeDisabled());
  expect(mocks.candidate).toHaveBeenCalledTimes(before+1);
  await act(()=>{resolve([candidate]);return Promise.resolve();});
  await screen.findByText('13812349876');
  await waitFor(()=>expect(screen.queryByRole('button',{name:'重试候选人资料'})).not.toBeInTheDocument());
},15_000);

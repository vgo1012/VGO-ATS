import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { RecruitmentWorkspacePage } from './RecruitmentWorkspacePage';
import { reportingApi } from '../reporting/api';
import { recruitmentApi } from '../recruitment/api';

const { actor } = vi.hoisted(() => ({ actor: { userId: 'admin', superAdmin: true, roles: [] as string[], permissions: [] } }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor }) }));
vi.mock('../talent/api', () => ({ talentApi: { candidates: vi.fn().mockResolvedValue({items:[]}) } }));
vi.mock('../demand/api', () => ({ demandApi: { jds: vi.fn().mockResolvedValue({items:[]}) } }));
vi.mock('../interview/api', () => ({ interviewApi: { list: vi.fn().mockResolvedValue({items:[]}) } }));
vi.mock('../recruitment/api', () => ({ recruitmentApi: { applications: vi.fn().mockResolvedValue({items:[]}), countApplications: vi.fn().mockResolvedValue(0) } }));
vi.mock('../reporting/api', () => ({ reportingApi: { report: vi.fn().mockResolvedValue({points:[{eventType:'RESUME_INGESTED',eventCount:100}],days:[]}) } }));
vi.mock('../recruitment/CreateApplicationDialog', () => ({ CreateApplicationModal: () => null }));
vi.mock('../components/DateRangeFilter',()=>({DateRangeFilter:()=>null}));
vi.mock('../recruitment/RecruitmentLedger',()=>({Applications:()=> <span>招聘台账</span>}));

const clients:QueryClient[]=[];
afterEach(()=>{cleanup();clients.forEach(client=>client.clear());clients.length=0;vi.clearAllMocks();});
function mount(){
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});clients.push(client);
  render(<ConfigProvider><QueryClientProvider client={client}><MemoryRouter><RecruitmentWorkspacePage /></MemoryRouter></QueryClientProvider></ConfigProvider>);
  return client;
}

it('招聘台账不请求工作台待办或趋势',async()=>{
  mount();
  expect(await screen.findByText('招聘台账')).toBeInTheDocument();
  await waitFor(()=>expect(recruitmentApi.applications).toHaveBeenCalled());
  expect(reportingApi.report).not.toHaveBeenCalled();
});

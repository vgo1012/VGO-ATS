import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { InterviewWorkspacePage } from './InterviewWorkspacePage';
import { interviewApi, type Interview, type InterviewTask } from '../interview/api';
import { ApiProblemError } from '../api/problem';
import { followUpPlansApi, type FollowUpPlan } from '../recruitment/followUpPlansApi';
import type * as BusinessReferences from '../display/BusinessReferences';

const state = vi.hoisted(() => ({ actor: { superAdmin: false, permissions: ['interview.schedule', 'recruitment.manage'] } }));
vi.mock('../auth/session', () => ({ useSession: () => state }));
vi.mock('../interview/api', () => ({ interviewApi: { list: vi.fn(), myTasks: vi.fn(), invitedJobs:vi.fn(), materials:vi.fn() } }));
vi.mock('../recruitment/followUpPlansApi',()=>({followUpPlansApi:{list:vi.fn().mockResolvedValue({items:[],hasMore:false,nextCursor:null})}}));
vi.mock('../demand/api',()=>({demandApi:{clients:vi.fn().mockResolvedValue({items:[],hasMore:false,nextCursor:null})}}));
vi.mock('../demand/JdSelector', () => ({ JdSelector: () => null }));
vi.mock('../interview/InterviewMaterials', () => ({ InterviewReference: ({field}:{field:string}) => field==='candidateName'?'候选人':'岗位', InterviewMaterials: () => null }));
vi.mock('../display/BusinessReferences', async (importOriginal) => ({ ...await importOriginal<typeof BusinessReferences>(), CandidateName: ({id}:{id:string}) => id, JobName: () => '岗位', UserName: () => 'HR' }));

function renderPage(tasks?: InterviewTask[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (tasks) client.setQueryData(['interview-tasks'], tasks);
  render(<MemoryRouter><ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><InterviewWorkspacePage /></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  return client;
}

describe('schedule page role boundaries', () => {
  it.each([403,503])('handles cached interview rows after refresh returns %s',async(status)=>{
    state.actor.permissions=['recruitment.manage'];
    vi.mocked(interviewApi.list).mockResolvedValueOnce({items:[{id:'cached',candidateName:'候选人',jdTitle:'岗位',clientName:'缓存甲方',status:'SCHEDULED',interviewType:'EXTERNAL_CLIENT',scheduledStartAt:new Date().toISOString(),scheduledEndAt:new Date(Date.now()+60000).toISOString()} as Interview],hasMore:true,nextCursor:'next'});
    const client=renderPage();
    expect(await screen.findByText('候选人')).toBeInTheDocument();
    vi.mocked(interviewApi.list).mockRejectedValue(new ApiProblemError({title:'读取失败',detail:'重新核对权限',status,code:'READ_FAILED',recoverable:status===503}));
    await act(()=>client.invalidateQueries({queryKey:['interviews']}));
    expect(await screen.findByRole('button',{name:'重试日程'})).toBeInTheDocument();
    if(status===403){
      expect(screen.queryByText('候选人')).not.toBeInTheDocument();
      expect(screen.queryByRole('button',{name:'加载更多面试'})).not.toBeInTheDocument();
    }else expect(screen.getByText('候选人')).toBeInTheDocument();
  });
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.mocked(followUpPlansApi.list).mockReset().mockResolvedValue({items:[],hasMore:false,nextCursor:null});
    vi.mocked(interviewApi.list).mockResolvedValue({ items: [], hasMore: false, nextCursor: null });
    vi.mocked(interviewApi.myTasks).mockResolvedValue([]);
    vi.mocked(interviewApi.invitedJobs).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  });

  it('renders list and task summaries without requesting full interview materials', async () => {
    state.actor.permissions = ['recruitment.manage'];
    vi.mocked(interviewApi.list).mockResolvedValue({items:[{
      id:'summary',candidateName:'摘要候选人',jdTitle:'历史岗位',jdCode:'JD-001',
      status:'SCHEDULED',interviewType:'INTERNAL',scheduledStartAt:new Date().toISOString(),
      scheduledEndAt:new Date(Date.now()+60000).toISOString(),roundNo:1,
    } as Interview],hasMore:false,nextCursor:null});
    vi.mocked(interviewApi.myTasks).mockResolvedValue([{interviewId:'other-week',candidateName:'任务候选人',
      stepCode:'INTERVIEW_ROUND_1',feedbackDueAt:'2026-09-10T01:00:00Z',submitted:false} as InterviewTask]);
    renderPage();
    expect(await screen.findByText('摘要候选人')).toBeInTheDocument();
    expect(screen.getByText(/历史岗位.*JD-001/)).toBeInTheDocument();
    expect(await screen.findByText(/任务候选人/)).toBeInTheDocument();
    expect(interviewApi.materials).not.toHaveBeenCalled();
  });

  it('does not infer first round from a reusable step code in tasks without round data', () => {
    state.actor.permissions = ['recruitment.manage'];
    const tasks = [{ interviewId: 'sixth', candidateName: '候选人', stepCode: 'INTERVIEW_ROUND_1', feedbackDueAt: '2026-09-10T01:00:00Z', submitted: false }] as InterviewTask[];
    vi.mocked(interviewApi.myTasks).mockResolvedValue(tasks);
    renderPage(tasks);
    expect(screen.getByRole('button', { name: /面试.*候选人.*待面评/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /一面.*候选人/ })).not.toBeInTheDocument();
  });

  it('shows assigned feedback work without recruitment progress', async () => {
    state.actor.permissions = ['interview.schedule', 'recruitment.manage'];
    renderPage();
    expect(await screen.findByText('暂无待跟进面评')).toBeInTheDocument();
    expect(await screen.findByRole('group', { name: '日程颜色说明' })).toHaveTextContent('甲方面试 · 待进行');
    expect(interviewApi.myTasks).toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: '待反馈' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '招聘进展' })).not.toBeInTheDocument();
    expect(screen.queryByText('我的面评')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新建面试/ })).toBeInTheDocument();
  });

  it('does not expose feedback tasks to a legacy interviewer account', () => {
    state.actor.permissions = ['interview.read', 'interview.feedback.submit-own'];
    renderPage();
    expect(interviewApi.myTasks).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: '待反馈' })).toBeInTheDocument();
    expect(screen.getByText('当前账号不能处理招聘面评')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /新建面试/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '招聘进展' })).not.toBeInTheDocument();
  });

  it('shows the feedback failure without restoring recruitment progress', async () => {
    state.actor.permissions = ['recruitment.manage'];
    vi.mocked(interviewApi.myTasks).mockRejectedValue(new Error('面评服务暂不可用'));
    renderPage();
    expect(await screen.findByText('面评服务暂不可用')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '招聘进展' })).not.toBeInTheDocument();
  });

  it('loads later appointments and retries the failed cursor without losing the first page', async () => {
    state.actor.permissions = ['recruitment.manage'];
    const plan = (id:string):FollowUpPlan => ({id,applicationId:'a',candidateId:id,jdId:'j',jdVersionId:'v',recruitmentType:'INTERNAL',clientId:null,clientName:null,ownerUserId:'hr',startsAt:new Date().toISOString(),endsAt:new Date(Date.now()+60000).toISOString(),channel:'PHONE',note:id,status:'SCHEDULED',version:'0',createTime:new Date().toISOString(),updateTime:new Date().toISOString()});
    vi.mocked(followUpPlansApi.list)
      .mockResolvedValueOnce({items:[plan('首批预约')],hasMore:true,nextCursor:'next-plans'})
      .mockRejectedValueOnce(new Error('下一页暂不可用'))
      .mockResolvedValueOnce({items:[plan('后页预约')],hasMore:false,nextCursor:null});
    renderPage();
    expect(await screen.findByText('首批预约')).toBeInTheDocument();
    expect(screen.getByText('已显示 1 条沟通预约，该周还有更多预约')).toBeInTheDocument();
    expect(screen.queryByText('暂无安排')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'加载更多沟通预约'}));
    fireEvent.click(await screen.findByRole('button',{name:'重试加载沟通预约'}));
    expect(await screen.findByText('后页预约')).toBeInTheDocument();
    expect(screen.getByText('首批预约')).toBeInTheDocument();
    expect(vi.mocked(followUpPlansApi.list).mock.calls.map(([filters])=>filters?.cursor)).toEqual([undefined,'next-plans','next-plans']);
    expect(screen.queryByRole('button',{name:'加载更多沟通预约'})).not.toBeInTheDocument();
  },15_000);

  it('starts the new week without carrying the appointment cursor or pagination into list view', async () => {
    state.actor.permissions = ['recruitment.manage'];
    vi.mocked(followUpPlansApi.list).mockResolvedValueOnce({items:[],hasMore:true,nextCursor:'old-week'});
    renderPage();
    // 先等待异步列表就绪，避免全页角色查询阻塞加载状态的提交。
    await screen.findByText('已显示 0 条沟通预约，该周还有更多预约');
    expect(await screen.findByRole('button',{name:'加载更多沟通预约'})).toBeInTheDocument();
    const first=vi.mocked(followUpPlansApi.list).mock.calls[0]![0];
    fireEvent.click(screen.getByRole('button',{name:'上一周'}));
    await waitFor(()=>expect(followUpPlansApi.list).toHaveBeenCalledTimes(2));
    expect(vi.mocked(followUpPlansApi.list).mock.calls[1]![0]).toMatchObject({cursor:undefined});
    expect(vi.mocked(followUpPlansApi.list).mock.calls[1]![0]?.from).not.toBe(first?.from);
    fireEvent.click(screen.getByRole('button',{name:'自定义'}));
    expect(screen.queryByRole('button',{name:'加载更多沟通预约'})).not.toBeInTheDocument();
  },15_000);
});

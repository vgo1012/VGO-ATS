import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { FollowUpPlanHistory } from './FollowUpPlanHistory';
import { followUpPlansApi, type FollowUpPlanEvent } from './followUpPlansApi';
import { recruitmentApi } from './api';

vi.mock('./followUpPlansApi',()=>({followUpPlansApi:{history:vi.fn()}}));
vi.mock('./api',()=>({recruitmentApi:{communications:vi.fn()}}));
vi.mock('../display/BusinessReferences',()=>({UserName:()=> <span>负责 HR</span>}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

const event:FollowUpPlanEvent={id:'history-1',planId:'plan-1',action:'COMPLETED',reason:'候选人确认可参加下一轮',actorUserId:'hr-1',occurredAt:'2026-09-09T03:00:00Z',before:null,after:{startsAt:'2026-09-09T02:00:00Z',endsAt:'2026-09-09T02:30:00Z',channel:'PHONE',note:'确认意向',status:'COMPLETED',resultReason:'候选人确认可参加下一轮'}};

function setup(){
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><FollowUpPlanHistory applicationId="app-1"/></QueryClientProvider></ConfigProvider>);
  return client;
}

it('双读取失败不能冒充空记录，独立重试恢复后才显示真实空态',async()=>{
  vi.mocked(recruitmentApi.communications).mockRejectedValueOnce(new Error('实际沟通暂不可用')).mockResolvedValue([]);
  vi.mocked(followUpPlansApi.history).mockRejectedValueOnce(new Error('预约历史暂不可用')).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const client=setup();
  expect(await screen.findByRole('button',{name:'重试预约历史'})).toBeInTheDocument();
  expect(screen.queryByText('尚无沟通记录')).not.toBeInTheDocument();
  expect(screen.getByText('沟通记录暂不可用，请重试。')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'重试预约历史'}));
  await waitFor(()=>expect(screen.queryByRole('button',{name:'重试预约历史'})).not.toBeInTheDocument());
  expect(recruitmentApi.communications).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('尚无沟通记录')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'重试实际沟通'}));
  expect(await screen.findByText('尚无沟通记录')).toBeInTheDocument();
  client.clear();
});

it('另一路仍在加载或失败时保留已读取的历史',async()=>{
  let rejectCommunication!:(reason:Error)=>void;
  vi.mocked(recruitmentApi.communications).mockReturnValue(new Promise((_,reject)=>{rejectCommunication=reject;}));
  vi.mocked(followUpPlansApi.history).mockResolvedValue({items:[event],hasMore:false,nextCursor:null});
  const client=setup();
  expect(await screen.findByText(/候选人确认可参加下一轮/)).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('加载沟通记录');
  await act(()=>{rejectCommunication(new Error('实际沟通暂不可用'));return Promise.resolve();});
  expect(await screen.findByText('部分沟通记录未能读取，已显示可用记录。')).toBeInTheDocument();
  expect(screen.getByText(/候选人确认可参加下一轮/)).toBeInTheDocument();
  client.clear();
});

it('下一页失败后重试失败游标且保留已加载内容',async()=>{
  vi.mocked(recruitmentApi.communications).mockResolvedValue([]);
  vi.mocked(followUpPlansApi.history).mockResolvedValueOnce({items:[event],hasMore:true,nextCursor:'next'})
    .mockRejectedValueOnce(new Error('后页不可用'))
    .mockResolvedValueOnce({items:[{...event,id:'older',reason:'更早预约'}],hasMore:false,nextCursor:null});
  const client=setup();
  fireEvent.click(await screen.findByRole('button',{name:'加载更多沟通记录'}));
  fireEvent.click(await screen.findByRole('button',{name:'重试预约历史'}));
  expect(await screen.findByText(/更早预约/)).toBeInTheDocument();
  expect(screen.getByText(/候选人确认可参加下一轮/)).toBeInTheDocument();
  expect(vi.mocked(followUpPlansApi.history).mock.calls.map(call=>call[1])).toEqual([undefined,'next','next']);
  client.clear();
});

it('按服务端游标加载历史且没有新增记录入口',async()=>{
  vi.mocked(recruitmentApi.communications).mockResolvedValue([{id:'communication-1',applicationId:'app-1',channel:'WECHAT',summary:'已确认面试意向',result:'CONTINUE',nextPlan:'安排下一轮',communicatedAt:'2026-09-09T04:00:00Z',recordedByUserId:'hr-1'}]);
  vi.mocked(followUpPlansApi.history).mockResolvedValueOnce({items:[event],hasMore:true,nextCursor:'next'}).mockResolvedValueOnce({items:[{...event,id:'history-2',action:'CREATED',reason:'此前约定电话'}],hasMore:false,nextCursor:null});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider><QueryClientProvider client={client}><FollowUpPlanHistory applicationId="app-1"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText(/候选人确认可参加下一轮/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/新增|记录沟通/})).not.toBeInTheDocument();
  expect(screen.getByText(/已确认面试意向/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'加载更多沟通记录'}));
  expect(await screen.findByText(/此前约定电话/)).toBeInTheDocument();
  expect(followUpPlansApi.history).toHaveBeenLastCalledWith('app-1','next');
  client.clear();
});

it('权限失效后不继续展示已缓存的预约历史',async()=>{
  vi.mocked(recruitmentApi.communications).mockResolvedValue([]);
  vi.mocked(followUpPlansApi.history).mockResolvedValue({items:[event],hasMore:false,nextCursor:null});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider><QueryClientProvider client={client}><FollowUpPlanHistory applicationId="app-1"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText(/候选人确认可参加下一轮/)).toBeInTheDocument();
  vi.mocked(followUpPlansApi.history).mockRejectedValue(new ApiProblemError({status:403,title:'不可访问',detail:'读取权限已失效',code:'FORBIDDEN',recoverable:false}));
  await client.refetchQueries({queryKey:['follow-up-plan-history','app-1']});
  await waitFor(()=>expect(screen.queryByText(/候选人确认可参加下一轮/)).not.toBeInTheDocument());
  expect(await screen.findByRole('alert')).toHaveTextContent('读取权限已失效');
  client.clear();
});

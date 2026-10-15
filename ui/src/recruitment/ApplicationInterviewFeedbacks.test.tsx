import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ApiProblemError } from '../api/problem';
import { afterEach, expect, it, vi } from 'vitest';
import { interviewApi, type FeedbackView, type Interview } from '../interview/api';
import { ApplicationInterviewFeedbacks } from './ApplicationInterviewFeedbacks';

vi.mock('../interview/api',()=>({interviewApi:{allForApplication:vi.fn(),feedbackBatchAll:vi.fn()}}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string})=><span>{id}</span>}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

const interview=(id:string,roundNo:number,start:string)=>({id,roundNo,interviewType:'EXTERNAL_CLIENT',stepCode:`INTERVIEW_ROUND_${roundNo}`,scheduledStartAt:start,status:'COMPLETED'} as Interview);
const feedback=(interviewId:string,author:string,comment:string,status:'DRAFT'|'SUBMITTED'='SUBMITTED',verdict:'PASS'|'FAIL'='PASS')=>({feedback:{interviewId,authorUserId:author,status},currentVersion:{verdict,shortComment:comment,createdAt:'2026-09-10T03:00:00Z'}} as FeedbackView);

it('共享上下文展示草稿和作者来源，但不将草稿结论当成正式结果',async()=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([interview('round-1',1,'2026-09-08T03:00:00Z')]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['round-1'],items:[{...feedback('round-1','历史面试官','共享草稿文字','DRAFT','FAIL'),currentVersion:{...feedback('round-1','作者','共享草稿文字').currentVersion,verdict:'FAIL',externalFeedbackSource:'历史电话记录'}}]});
  render(<ConfigProvider><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ApplicationInterviewFeedbacks sharedLibrary applicationId="shared" recruitmentType="INTERNAL"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText('共享草稿文字')).toBeInTheDocument();
  expect(screen.getByText('历史面试官')).toBeInTheDocument();
  expect(screen.getByText('历史电话记录').parentElement?.parentElement).toHaveTextContent('来源：历史电话记录');
  expect(screen.getByText('含未提交草稿')).toBeInTheDocument();
  expect(screen.queryByText('不通过',{exact:true})).not.toBeInTheDocument();
});

it.each([401,403,503])('流程面试列表刷新%s时按权限保留或隐藏轮次缓存并支持恢复',async(status)=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([interview('round-1',1,'2026-09-08T03:00:00Z')]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['round-1'],items:[feedback('round-1','面试官','缓存的正式评价')]});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><ApplicationInterviewFeedbacks applicationId="application-1" recruitmentType="EXTERNAL"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText('缓存的正式评价')).toBeInTheDocument();
  vi.mocked(interviewApi.allForApplication).mockRejectedValue(new ApiProblemError({status,title:'读取失败',detail:'流程面试读取失败',code:'READ_FAILED',recoverable:status===503}));
  await act(()=>client.invalidateQueries({queryKey:['application-interviews','application-1']}));
  const retry=await screen.findByRole('button',{name:'重试流程记录'});
  if(status===503)expect(screen.getByText('缓存的正式评价')).toBeInTheDocument();
  else{
    expect(screen.queryByText('缓存的正式评价')).not.toBeInTheDocument();
    expect(screen.queryByText(/甲方面试 · 第 1 轮/)).not.toBeInTheDocument();
    expect(screen.queryByText('当前流程尚无面试与面评')).not.toBeInTheDocument();
    await act(()=>client.invalidateQueries({queryKey:['application-interview-feedback','application-1']}));
    expect(interviewApi.feedbackBatchAll).toHaveBeenCalledTimes(1);
  }
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([interview('round-2',2,'2026-09-09T03:00:00Z')]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['round-2'],items:[feedback('round-2','面试官','恢复后可见评价')]});
  fireEvent.click(retry);
  expect(await screen.findByText('恢复后可见评价')).toBeInTheDocument();
  expect(screen.queryByText('缓存的正式评价')).not.toBeInTheDocument();
  client.clear();
});

it('在任意入口完整展示所有面试轮次的正式面评并隐藏旧草稿和版本信息',async()=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([
    interview('round-1',1,'2026-09-08T03:00:00Z'),
    interview('round-3',3,'2026-09-10T03:00:00Z'),
  ]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['round-1','round-3'],items:[feedback('round-1','一面面试官','一面通过'),feedback('round-1','旧用户','不可见旧草稿','DRAFT'),feedback('round-3','三面面试官','三面未通过','SUBMITTED','FAIL')]});
  render(<ConfigProvider><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
    <ApplicationInterviewFeedbacks applicationId="application-1" recruitmentType="EXTERNAL"/>
  </QueryClientProvider></ConfigProvider>);

  expect(await screen.findByText('一面通过')).toBeInTheDocument();
  const table=screen.getByRole('table');
  expect(within(table).getAllByRole('columnheader').map(cell=>cell.textContent)).toEqual(['步骤','面试时间','状态','本轮结果','面评']);
  expect(within(table).getAllByRole('row')).toHaveLength(3);
  expect(within(table).getAllByText('已完成')).toHaveLength(2);
  expect(within(table).queryByText('导入完成')).not.toBeInTheDocument();
  expect(within(table).queryByText(/第 2 轮/)).not.toBeInTheDocument();
  const failedRow=within(table).getByText('三面未通过').closest('tr');
  expect(failedRow).toHaveTextContent('甲方面试 · 第 3 轮');
  expect(failedRow).toHaveTextContent('不通过');
  expect(screen.getByText(/甲方面试 · 第 1 轮/)).toBeInTheDocument();
  expect(screen.getByText(/甲方面试 · 第 3 轮/)).toBeInTheDocument();
  expect(screen.queryByText('不可见旧草稿')).not.toBeInTheDocument();
  expect(within(table).getByText('通过',{exact:true})).toBeInTheDocument();
  expect(within(table).getByText('不通过',{exact:true})).toBeInTheDocument();
  for(const text of ['一面面试官','三面面试官','内部面评'])expect(within(table).queryByText(text,{exact:true})).not.toBeInTheDocument();
  expect(screen.queryByText(/第 \d+ 版/)).not.toBeInTheDocument();
  expect(interviewApi.feedbackBatchAll).toHaveBeenCalledTimes(1);
});

it('长面评默认只显示摘要，查看更多能阅读完整换行内容',async()=>{
  const comment='技术判断很清晰\n'+ '连续英文路径'.repeat(12);
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([interview('round-1',1,'2026-09-08T03:00:00Z')]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['round-1'],items:[feedback('round-1','面试官',comment)]});
  render(<ConfigProvider><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>
    <ApplicationInterviewFeedbacks applicationId="application-1" recruitmentType="EXTERNAL"/>
  </QueryClientProvider></ConfigProvider>);
  const more=await screen.findByRole('button',{name:'查看更多面评'});
  expect(within(screen.getByRole('table')).queryByText(comment)).not.toBeInTheDocument();
  expect(more.parentElement).toHaveTextContent('技术判断很清晰 连续英文路径');
  fireEvent.click(more);
  expect(await screen.findByRole('dialog',{name:'完整面评'})).toHaveTextContent(comment.replace('\n',' '));
  fireEvent.click(screen.getByRole('button',{name:'Close'}));
  await waitFor(()=>expect(screen.queryByRole('dialog',{name:'完整面评'})).not.toBeInTheDocument());
  expect(screen.getByRole('button',{name:'查看更多面评'})).toBeInTheDocument();
});

it('保留空轮并在批量授权撤回后隐藏旧面评，支持重新读取',async()=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([interview('a',1,'2026-09-08'),interview('b',2,'2026-09-09')]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['a','b'],items:[feedback('a','作者','随后撤权的评论')]});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><ApplicationInterviewFeedbacks applicationId="app" recruitmentType="INTERNAL"/></QueryClientProvider></ConfigProvider>);
  await screen.findByText('随后撤权的评论');
  expect(screen.getByText('本轮暂无面评')).toBeInTheDocument();
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['b'],items:[]});
  await act(()=>client.invalidateQueries({queryKey:['application-interview-feedback','app']}));
  await waitFor(()=>expect(screen.queryByText('随后撤权的评论')).not.toBeInTheDocument());
  expect(screen.getByText('本轮面评需授权或已不可用')).toBeInTheDocument();
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['a','b'],items:[feedback('a','作者','恢复后的评论')]});
  fireEvent.click(screen.getByRole('button',{name:'重试本轮面评'}));
  await screen.findByText('恢复后的评论');
});

it('流程记录逐条绑定面评，撤权不显示缓存且读取失败可以重试',async()=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([
    {...interview('a',1,'2026-09-08'),workflowStepId:'step-a'},
    {...interview('b',2,'2026-09-09'),workflowStepId:'step-b'},
  ]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['a','b'],items:[feedback('a','作者','一面具体意见'),feedback('b','作者','二面具体意见'),feedback('a','作者','未提交草稿','DRAFT')]});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  render(<ConfigProvider theme={{token:{motion:false}}}><QueryClientProvider client={client}><ApplicationInterviewFeedbacks applicationId="app" recruitmentType="INTERNAL"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText('一面具体意见')).toBeInTheDocument();
  expect(screen.getByText('二面具体意见')).toBeInTheDocument();
  expect(screen.queryByText('未提交草稿')).not.toBeInTheDocument();
  vi.mocked(interviewApi.feedbackBatchAll).mockRejectedValue(new ApiProblemError({status:403,title:'无权读取',detail:'面评权限已撤回',code:'DENIED',recoverable:false}));
  await act(()=>client.invalidateQueries({queryKey:['application-interview-feedback','app']}));
  expect(await screen.findByText('面评权限已撤回')).toBeInTheDocument();
  expect(screen.queryByText('一面具体意见')).not.toBeInTheDocument();
  expect(screen.queryByText('通过',{exact:true})).not.toBeInTheDocument();
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['a'],items:[feedback('a','作者','恢复后的一面意见')]});
  fireEvent.click(screen.getByRole('button',{name:'重试本批面评'}));
  expect(await screen.findByText('恢复后的一面意见')).toBeInTheDocument();
  client.clear();
});

it.each(['', '   ', '\n'])('面评正文为空时正文显示本轮暂无面评，结果列仍展示结论（%j）',async(comment)=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([{...interview('a',1,'2026-09-08T03:00:00Z'),initialComment:'排期备注'}]);
  vi.mocked(interviewApi.feedbackBatchAll).mockResolvedValue({visibleInterviewIds:['a'],items:[feedback('a','评价人姓名',comment)]});
  render(<ConfigProvider><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ApplicationInterviewFeedbacks applicationId="app" recruitmentType="INTERNAL"/></QueryClientProvider></ConfigProvider>);
  expect(await screen.findByText('本轮暂无面评')).toBeInTheDocument();
  expect(screen.getByText('通过',{exact:true})).toBeInTheDocument();
  for(const text of ['评价人姓名','内部面评','排期备注'])expect(screen.queryByText(text,{exact:true})).not.toBeInTheDocument();
});

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { interviewApi, type FeedbackView, type InterviewDetails } from '../interview/api';
import { recruitmentApi, type ApplicationDetails } from './api';
import { LedgerInterviewRoundForm } from './LedgerInterviewRoundForm';

const state=vi.hoisted(()=>({actor:{userId:'hr',permissions:['recruitment.manage','interview.schedule'],superAdmin:false} as SessionActor}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:state.actor})}));
vi.mock('../display/BusinessReferences',()=>({UserName:({id}:{id:string})=><span>{id}</span>}));
vi.mock('../interview/InterviewMaterials',()=>({InterviewMaterials:()=>null,InterviewReference:()=>null}));
vi.mock('../components/ScheduleDateTimePicker',()=>({ScheduleDateTimePicker:({value,onChange,id}:{value?:string;onChange:(value:string)=>void;id:string})=><input id={id} value={value??''} onChange={event=>onChange(event.target.value)}/>}));
const app={application:{id:'app',ownerUserId:'hr',terminalAt:null},collaboratorUserIds:[],workflowSteps:[{id:'step',stepCode:'INTERVIEW_ROUND_1',status:'IN_PROGRESS',version:1}]} as unknown as ApplicationDetails;
const value={interview:{id:'i',applicationId:'app',workflowStepId:'step',stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'INTERNAL',status:'SCHEDULED',scheduledStartAt:'2099-09-15T06:00:00Z',scheduledEndAt:'2099-09-15T06:30:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',initialComment:'历史排期备注',version:1},participants:[{userId:'interviewer'}]} as InterviewDetails;
const saved={feedback:{id:'f',interviewId:'i',authorUserId:'hr',status:'SUBMITTED'},currentVersion:{verdict:'PASS',shortComment:'技术表达清晰',changeReason:'核对实际面试记录'}} as FeedbackView;
const onChanged=vi.fn().mockResolvedValue(undefined);
function mount(details=value){return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><LedgerInterviewRoundForm value={details} origin={{action:'INTERVIEW',operationId:'operation'}} onClose={vi.fn()} onChanged={onChanged}/></QueryClientProvider></App></ConfigProvider>);}
beforeEach(()=>{
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  vi.spyOn(recruitmentApi,'application').mockResolvedValue(app);
  vi.spyOn(recruitmentApi,'completeStep').mockResolvedValue(app);
  vi.spyOn(interviewApi,'feedbackAll').mockResolvedValue([]);
  vi.spyOn(interviewApi,'feedbackByAuthor').mockResolvedValue(null);
  vi.spyOn(interviewApi,'feedback').mockResolvedValue(saved);
  vi.spyOn(interviewApi,'get').mockResolvedValue({...value,interview:{...value.interview,status:'COMPLETED'}});
  vi.spyOn(interviewApi,'cancel').mockResolvedValue(value);
});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.clearAllMocks();});
it('submits inline formal feedback and completes the exact workflow step without a second dialog',async()=>{
  mount();
  expect(await screen.findByLabelText('面评')).toHaveValue('历史排期备注');
  expect(screen.getAllByRole('dialog')).toHaveLength(1);
  expect(screen.queryByLabelText('提交依据')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'正式提交面评'}));
  await waitFor(()=>expect(recruitmentApi.completeStep).toHaveBeenCalledWith('app',app.workflowSteps[0],'PASSED',expect.any(String),undefined,undefined,{action:'INTERVIEW',operationId:'operation'}));
  expect(interviewApi.feedback).toHaveBeenCalledWith('i',expect.objectContaining({status:'SUBMITTED',shortComment:'历史排期备注',changeReason:'正式提交面评'}),expect.any(String),{action:'INTERVIEW',operationId:'operation'});
});
it('shows the saved schedule read-only without a second save action',async()=>{
  mount();await screen.findByLabelText('面评');
  expect(screen.queryByRole('button',{name:/保\s*存/})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'改期'})).not.toBeInTheDocument();
  for(const label of ['开始时间','结束时间','时区'])expect(screen.getByLabelText(label)).toHaveAttribute('readonly');
  expect(screen.getByLabelText('开始时间')).toHaveValue('2099-09-15 14:00');
  expect(screen.getByLabelText('结束时间')).toHaveValue('2099-09-15 14:30');
});
it('retries uncertain cancellation with its original request and blocks feedback until confirmed',async()=>{
  vi.mocked(interviewApi.cancel).mockRejectedValueOnce(new TypeError('响应丢失'));
  mount();await screen.findByLabelText('面评');
  fireEvent.click(screen.getByRole('button',{name:'取消本轮面试'}));
  fireEvent.click(await screen.findByRole('button',{name:/OK|确\s*定/}));
  await screen.findByText('上次取消结果尚未确认，请重试。');
  expect(screen.getByRole('button',{name:'正式提交面评'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'重试取消'}));
  await waitFor(()=>expect(interviewApi.cancel).toHaveBeenCalledTimes(2));
  expect(vi.mocked(interviewApi.cancel).mock.calls[1]).toEqual(vi.mocked(interviewApi.cancel).mock.calls[0]);
});
it('shows completed feedback read-only and recovers an unfinished step without posting feedback again',async()=>{
  vi.mocked(interviewApi.feedbackAll).mockResolvedValue([saved]);
  mount({...value,interview:{...value.interview,status:'COMPLETED'}});
  expect(await screen.findByLabelText('面评')).toHaveAttribute('readonly');
  expect(screen.getByLabelText('面评')).toHaveValue('技术表达清晰');
  expect(screen.queryByText(/提交依据/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'正式提交面评'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'改期'})).not.toBeInTheDocument();
  fireEvent.click(await screen.findByRole('button',{name:'重试同步本轮结果'}));
  await waitFor(()=>expect(recruitmentApi.completeStep).toHaveBeenCalledTimes(1));
  expect(interviewApi.feedback).not.toHaveBeenCalled();
});
it('blocks processing when the latest application belongs to another HR',async()=>{
  vi.mocked(recruitmentApi.application).mockResolvedValue({...app,application:{...app.application,ownerUserId:'other'}});
  mount();await screen.findByText('你可以查看此招聘流程；只有归属 HR 或已添加的流程协作人可以处理。');
  expect(screen.queryByRole('button',{name:'正式提交面评'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'改期'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/保\s*存/})).not.toBeInTheDocument();
});

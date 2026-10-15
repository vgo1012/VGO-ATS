import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { interviewApi, type InterviewDetails } from '../interview/api';
import { submissionApi } from '../submission/api';
import { recruitmentApi, type ApplicationDetails, type WorkflowStep } from './api';
import { LedgerInterviewDialog } from './LedgerInterviewDialog';
import { refreshRecruitmentActivity } from './activityApi';

const state=vi.hoisted(()=>({actor:{userId:'hr',permissions:['recruitment.manage','interview.schedule'],superAdmin:false} as SessionActor}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:state.actor})}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> <span>李明</span>,JobName:()=> <span>工程师</span>,PeopleSelect:({value,onChange}:{value?:string[];onChange:(value:string[])=>void})=><input aria-label="实际面试官" value={value?.join(',')??''} onChange={event=>onChange(event.target.value?[event.target.value]:[])}/>,TechnicalDetails:()=>null,UserName:()=>null}));
vi.mock('../components/ScheduleDateTimePicker',()=>({ScheduleDateTimePicker:({value,onChange,id}:{value?:string;onChange:(value:string)=>void;id:string})=><input id={id} value={value??''} onChange={event=>onChange(event.target.value)}/>}));
const base={application:{id:'app',candidateId:'person',jdId:'jd',ownerUserId:'hr',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',terminalAt:null,version:1},collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[]} as unknown as ApplicationDetails;
const step={id:'step',stepCode:'INTERVIEW_ROUND_1',status:'IN_PROGRESS',sequenceNo:1,roundNo:1} as WorkflowStep;
const onChanged=vi.fn(()=>Promise.resolve());
const onClose=vi.fn();
function mount(value=base,roundNo?:number,client=new QueryClient({defaultOptions:{queries:{retry:false}}})){return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><LedgerInterviewDialog {...(roundNo?{roundNo}:{})} value={value} onChanged={onChanged} onClose={onClose}/></QueryClientProvider></App></ConfigProvider>);}
async function fill(){
  await screen.findByRole('button',{name:/保\s*存/});
  fireEvent.change(screen.getByLabelText('开始时间'),{target:{value:'2099-09-15T14:00'}});
  fireEvent.change(screen.getByLabelText('结束时间'),{target:{value:'2099-09-15T14:30'}});
  fireEvent.change(screen.getByRole('textbox',{name:'实际面试官'}),{target:{value:'interviewer'}});
}
beforeEach(()=>{
  onChanged.mockImplementation(()=>Promise.resolve());
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  vi.spyOn(recruitmentApi,'application').mockResolvedValue(base);
  vi.spyOn(recruitmentApi,'nextAction').mockResolvedValue({...base,workflowSteps:[step]});
  vi.spyOn(interviewApi,'allForApplication').mockResolvedValue([]);
  vi.spyOn(interviewApi,'list').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(interviewApi,'create').mockResolvedValue({interview:{id:'i'}} as InterviewDetails);
  vi.spyOn(submissionApi,'list').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
});
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.clearAllMocks();});
it('opens the actual scheduling fields on one surface and cancelling creates no step',async()=>{
  mount();await fill();
  expect(screen.getAllByRole('dialog')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button',{name:'Cancel'}));
  expect(recruitmentApi.nextAction).not.toHaveBeenCalled();expect(interviewApi.create).not.toHaveBeenCalled();
  expect(interviewApi.list).not.toHaveBeenCalled();expect(interviewApi.allForApplication).toHaveBeenCalledWith('app');
});
it('retains the created step and idempotency key when scheduling fails',async()=>{
  vi.mocked(interviewApi.create).mockRejectedValueOnce(new Error('网络中断'));
  mount();await fill();
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalledTimes(1));
  await waitFor(()=>expect(screen.getByRole('button',{name:/保\s*存/})).toBeEnabled());
  expect(screen.getByRole('textbox',{name:'实际面试官'})).toHaveValue('interviewer');
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalledTimes(2));
  expect(recruitmentApi.nextAction).toHaveBeenCalledTimes(1);
  const [first,second]=vi.mocked(interviewApi.create).mock.calls;
  expect(first?.[0]).toMatchObject({workflowStepId:'step',reason:'',interviewType:'INTERNAL'});
  expect(first?.[0]).not.toHaveProperty('initialComment');
  expect(second).toEqual(first);
  expect(first?.[2]).toMatchObject({action:'INTERVIEW'});
  expect(vi.mocked(recruitmentApi.nextAction).mock.calls[0]?.[3]).toEqual(first?.[2]);
});
it('reuses a current unscheduled interview step without choosing another next action',async()=>{
  const value={...base,application:{...base.application,currentStage:'IN_PROCESS' as const},workflowSteps:[step]};
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  mount(value);await fill();fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalledTimes(1));
  expect(recruitmentApi.nextAction).not.toHaveBeenCalled();
  expect(interviewApi.list).not.toHaveBeenCalled();
  expect(interviewApi.allForApplication).toHaveBeenCalledWith('app');
});
it('allows an external label without a client recommendation or internal participants',async()=>{
  mount();await fill();
  fireEvent.click(screen.getByRole('radio',{name:'外部面试'}));
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalled());
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0]).toMatchObject({interviewType:'EXTERNAL',participantUserIds:[]});
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0]).not.toHaveProperty('initialComment');
  expect(submissionApi.list).not.toHaveBeenCalled();
});
it('opens a directly selected first round before any schedule exists',async()=>{
  mount(base,1);await fill();
  expect(screen.getByRole('button',{name:/保\s*存/})).toBeEnabled();
});
it('requires a real first-round schedule before jumping to round three',async()=>{
  mount(base,3);
  expect(await screen.findByText('请先安排第 1 轮面试，再选择后续轮次。')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/保\s*存/})).not.toBeInTheDocument();
});
it('schedules round three while round one is still awaiting feedback',async()=>{
  const value={...base,workflowSteps:[step]};
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([{id:'first',stepCode:'INTERVIEW_ROUND_1',roundNo:1,status:'SCHEDULED'}] as InterviewDetails['interview'][]);
  vi.mocked(recruitmentApi.nextAction).mockResolvedValue({...value,workflowSteps:[step,{...step,id:'third',roundNo:3,sequenceNo:2}]});
  mount(value,3);await fill();
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalled());
  expect(recruitmentApi.nextAction).toHaveBeenCalledWith(value.application,'INTERVIEW_ROUND_1','',expect.objectContaining({action:'INTERVIEW'}),3);
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0].workflowStepId).toBe('third');
});
it('recreates a cancelled first-round step even when round three has been arranged',async()=>{
  const value={...base,workflowSteps:[{...step,status:'WITHDRAWN' as const},{...step,id:'third',roundNo:3}]};
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([{id:'first',workflowStepId:'step',stepCode:'INTERVIEW_ROUND_1',roundNo:1,status:'CANCELLED'},{id:'third',workflowStepId:'third',stepCode:'INTERVIEW_ROUND_1',roundNo:3,status:'SCHEDULED'}] as InterviewDetails['interview'][]);
  vi.mocked(recruitmentApi.nextAction).mockResolvedValue({...value,workflowSteps:[...value.workflowSteps,{...step,id:'replacement'}]});
  mount(value,1);await fill();
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalled());
  expect(recruitmentApi.nextAction).toHaveBeenCalledWith(value.application,'INTERVIEW_ROUND_1','',expect.anything(),1);
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0].workflowStepId).toBe('replacement');
});
it('reuses the replacement first-round step rather than the withdrawn history',async()=>{
  const value={...base,workflowSteps:[{...step,status:'WITHDRAWN' as const},{...step,id:'replacement',sequenceNo:2}]};
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  mount(value,1);await fill();fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalled());
  expect(recruitmentApi.nextAction).not.toHaveBeenCalled();
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0].workflowStepId).toBe('replacement');
});
it('preserves legacy client AI scheduling with its bound recommendation and no new type override',async()=>{
  const value={...base,application:{...base.application,recruitmentType:'EXTERNAL' as const,clientId:'client'},workflowSteps:[{...step,stepCode:'CLIENT_AI_INTERVIEW',clientSubmissionId:'submission'}]};
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  vi.mocked(submissionApi.list).mockResolvedValue({items:[{...value.application,id:'submission',applicationId:'app',deliveryStatus:'SENT',sentAt:'2026-09-01T00:00:00Z'}] as unknown as Awaited<ReturnType<typeof submissionApi.list>>['items'],hasMore:false,nextCursor:null});
  mount(value);
  const save=await screen.findByRole('button',{name:/保\s*存/});
  await waitFor(()=>expect(save).toBeEnabled());
  expect(screen.queryByRole('radio',{name:'外部面试'})).not.toBeInTheDocument();
  fireEvent.click(save);
  await waitFor(()=>expect(interviewApi.create).toHaveBeenCalled());
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0]).toMatchObject({workflowStepId:'step',clientSubmissionId:'submission',participantUserIds:[]});
  expect(vi.mocked(interviewApi.create).mock.calls[0]?.[0]).not.toHaveProperty('interviewType');
});
it('keeps the original dialog after scheduling and leaves history in the activity reader',async()=>{
  let value=base;
  const scheduled={id:'i',applicationId:'app',workflowStepId:'step',stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'INTERNAL',status:'SCHEDULED',scheduledStartAt:'2099-09-15T06:00:00Z',scheduledEndAt:'2099-09-15T06:30:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',createdAt:'2099-09-14T00:00:00Z'} as InterviewDetails['interview'];
  const details={interview:scheduled,participants:[{userId:'interviewer'}]} as InterviewDetails;
  vi.mocked(recruitmentApi.application).mockImplementation(()=>Promise.resolve(value));
  vi.mocked(interviewApi.create).mockImplementation(()=>{value={...base,application:{...base.application,currentStage:'IN_PROCESS'},workflowSteps:[step]};return Promise.resolve(details);});
  vi.mocked(interviewApi.allForApplication).mockImplementation(()=>Promise.resolve(value.workflowSteps.length?[scheduled]:[]));
  vi.spyOn(interviewApi,'get').mockResolvedValue(details);
  vi.spyOn(interviewApi,'feedbackAll').mockResolvedValue([]);
  vi.spyOn(interviewApi,'feedbackByAuthor').mockResolvedValue(null);
  vi.spyOn(interviewApi,'historyPage').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(interviewApi,'materials').mockResolvedValue({} as Awaited<ReturnType<typeof interviewApi.materials>>);
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const invalidate=vi.spyOn(client,'invalidateQueries');
  onChanged.mockImplementation(()=>refreshRecruitmentActivity(client,'app'));
  mount(base,undefined,client);await fill();
  const dialog=screen.getByRole('dialog');
  fireEvent.click(screen.getByRole('button',{name:/保\s*存/}));
  await waitFor(()=>expect(interviewApi.feedbackByAuthor).toHaveBeenCalled());
  await screen.findByRole('button',{name:'正式提交面评'});
  expect(screen.getByRole('dialog')).toBe(dialog);
  expect(onChanged).toHaveBeenCalledTimes(1);
  for(const key of ['application','application-interviews','interview','interviews','interview-tasks'])expect(invalidate.mock.calls.filter(([filters])=>filters?.queryKey?.[0]===key)).toHaveLength(1);
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByLabelText('面评')).toHaveValue('');
  expect(screen.queryByRole('button',{name:'填写面评'})).not.toBeInTheDocument();
  expect(screen.queryByText('等待实际面试官提交面评')).not.toBeInTheDocument();
  expect(screen.queryByText('本轮面评',{exact:true})).not.toBeInTheDocument();
  expect(screen.queryByText('排期变更历史')).not.toBeInTheDocument();
  expect(interviewApi.historyPage).not.toHaveBeenCalled();
});

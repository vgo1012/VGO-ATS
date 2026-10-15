import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { FollowUpPlanDialog } from './FollowUpPlanDialog';
import { followUpPlansApi, type FollowUpPlan } from './followUpPlansApi';
import { recruitmentApi, type ApplicationDetails, type RecruitmentApplication } from './api';
import { ApiProblemError } from '../api/problem';

vi.mock('../components/ScheduleDateTimePicker',()=>({ScheduleDateTimePicker:({value,onChange,id}:{value:string;onChange:(value:string)=>void;id:string})=><input id={id} value={value} onChange={event=>onChange(event.target.value)}/>}));
const plan:FollowUpPlan={id:'plan',applicationId:'app',candidateId:'candidate',jdId:'jd',jdVersionId:'jd-v1',recruitmentType:'INTERNAL',clientId:null,clientName:null,ownerUserId:'owner',startsAt:'2030-09-12T02:00:00Z',endsAt:'2030-09-12T02:30:00Z',channel:'PHONE',note:'原预约',status:'SCHEDULED',version:'1',createTime:'2026-09-12T00:00:00Z',updateTime:'2026-09-12T00:00:00Z'};
afterEach(()=>{cleanup();vi.restoreAllMocks();});
function mount(saved=vi.fn(()=>Promise.resolve()),action:'schedule'|'cancel'|'complete'='schedule',client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})){
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><FollowUpPlanDialog plan={plan} action={action} onClose={()=>undefined} onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  return saved;
}

it.each(['FOLLOW_UP_STEP_CHANGED','FOLLOW_UP_PLAN_CHANGED'])('prepares a new completion only after checking a rolled-back %s conflict',async code=>{
  const details={workflowSteps:[{id:'step',version:4,sequenceNo:1,status:'IN_PROGRESS',stepCode:'CANDIDATE_COMMUNICATION'}]} as ApplicationDetails;
  const read=vi.spyOn(recruitmentApi,'application').mockResolvedValue(details);
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,version:'3'}],hasMore:false,nextCursor:null});
  const finish=vi.spyOn(followUpPlansApi,'finish').mockRejectedValueOnce(new ApiProblemError({title:'冲突',detail:'步骤版本已变化',status:409,code,recoverable:true})).mockResolvedValue({...plan,status:'COMPLETED'});
  const saved=mount(undefined,'complete');
  await screen.findByText('同步当前候选人沟通步骤');
  fireEvent.mouseDown(screen.getByLabelText('本次沟通结果'));
  fireEvent.click(await screen.findByText('沟通完成，继续推进'));
  fireEvent.change(screen.getByLabelText('实际沟通情况（选填）'),{target:{value:'保留原沟通草稿'}});
  fireEvent.click(screen.getByRole('button',{name:'记录完成'}));
  await screen.findByText('步骤版本已变化');
  read.mockResolvedValue({...details,workflowSteps:details.workflowSteps.map(step=>({...step,version:5}))});
  fireEvent.click(screen.getByRole('button',{name:'重新核对当前步骤'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'记录完成'})).toBeEnabled());
  expect(screen.getByLabelText('实际沟通情况（选填）')).toHaveValue('保留原沟通草稿');
  fireEvent.click(screen.getByRole('button',{name:'记录完成'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledWith('CONTINUE'));
  expect(finish.mock.calls[1]?.[0].version).toBe('3');
  expect(finish.mock.calls[1]?.[3]?.workflowStepVersion).toBe('5');
  expect(finish.mock.calls[1]?.[4]).not.toBe(finish.mock.calls[0]?.[4]);
});

it.each(['changed','unreadable','key-reused','server-error'])('freezes completion body and key for recovery when the current workflow is %s',async mode=>{
  const application={workflowSteps:[{id:'communication-1',version:4,sequenceNo:1,status:'IN_PROGRESS',stepCode:'CANDIDATE_COMMUNICATION'}]} as ApplicationDetails;
  const read=vi.spyOn(recruitmentApi,'application').mockResolvedValue(application);
  const failure=mode==='key-reused'||mode==='server-error'?new ApiProblemError({title:'完成失败',detail:'完成响应丢失',status:mode==='key-reused'?409:503,code:mode==='key-reused'?'IDEMPOTENCY_KEY_REUSED':'SERVICE_UNAVAILABLE',recoverable:true}):new Error('完成响应丢失');
  const finish=vi.spyOn(followUpPlansApi,'finish').mockRejectedValueOnce(failure).mockResolvedValue({...plan,status:'COMPLETED'});
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  const saved=mount(undefined,'complete',client);
  await screen.findByText('同步当前候选人沟通步骤');
  fireEvent.mouseDown(screen.getByLabelText('本次沟通结果'));
  fireEvent.click(await screen.findByText('沟通完成，继续推进'));
  fireEvent.change(screen.getByLabelText('实际沟通情况（选填）'),{target:{value:'沟通确认'}});
  fireEvent.change(screen.getByLabelText('下一步计划（选填）'),{target:{value:'安排面试'}});
  fireEvent.click(screen.getByRole('button',{name:'记录完成'}));
  await screen.findByText('完成响应丢失');
  expect(finish.mock.calls[0]).toHaveLength(5);
  expect(finish.mock.calls[0]?.[4]).toEqual(expect.any(String));
  expect(finish.mock.calls[0]?.[3]).toEqual({result:'CONTINUE',nextPlan:'安排面试',workflowStepId:'communication-1',workflowStepVersion:'4'});
  expect(screen.getByLabelText('实际沟通情况（选填）')).toBeDisabled();
  expect(screen.getByLabelText('本次沟通结果')).toBeDisabled();
  expect(screen.getByLabelText('下一步计划（选填）')).toBeDisabled();
  if(mode==='unreadable')read.mockRejectedValue(new Error('新步骤不可读'));
  else read.mockResolvedValue({...application,workflowSteps:[]});
  await client.refetchQueries({queryKey:['application','app']});
  fireEvent.click(screen.getByRole('button',{name:'重试记录完成'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledWith('CONTINUE'));
  expect(finish.mock.calls[1]).toEqual(finish.mock.calls[0]);
  expect(read).toHaveBeenCalledTimes(2);
});

it.each(['候选人另有安排',''])('recovers cancellation response loss with submitted reason %s',async reason=>{
  const finish=vi.spyOn(followUpPlansApi,'finish').mockRejectedValue(new Error('取消响应丢失'));
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,status:'CANCELLED',version:'2',resultReason:reason||'HR 取消沟通预约'}],hasMore:false,nextCursor:null});
  const saved=mount(undefined,'cancel');
  fireEvent.change(screen.getByLabelText('取消原因（选填）'),{target:{value:reason}});
  fireEvent.click(screen.getByRole('button',{name:'确认取消'}));
  await screen.findByText('取消响应丢失');
  expect(screen.getByRole('button',{name:'确认取消'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(finish).toHaveBeenCalledOnce();
});

it.each([
  {status:'CANCELLED',resultReason:'其他HR取消',edit:false},
  {status:'COMPLETED',resultReason:'已完成沟通',edit:false},
  {status:'CANCELLED',resultReason:'原取消原因',edit:true},
] as const)('preserves cancellation draft without claiming success for $status edited=$edit',async({status,resultReason,edit})=>{
  const finish=vi.spyOn(followUpPlansApi,'finish').mockRejectedValue(new Error('取消响应丢失'));
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,status,resultReason,version:'2'}],hasMore:false,nextCursor:null});
  const saved=mount(undefined,'cancel');
  fireEvent.change(screen.getByLabelText('取消原因（选填）'),{target:{value:'原取消原因'}});
  fireEvent.click(screen.getByRole('button',{name:'确认取消'}));
  await screen.findByText('取消响应丢失');
  if(edit)fireEvent.change(screen.getByLabelText('取消原因（选填）'),{target:{value:'新取消草稿'}});
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByRole('region',{name:'当前预约'});
  expect(screen.getByText(resultReason)).toBeInTheDocument();
  expect(screen.getByLabelText('取消原因（选填）')).toHaveValue(edit?'新取消草稿':'原取消原因');
  expect(screen.queryByRole('button',{name:'按最新版本重试'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'确认取消'})).toBeDisabled();
  expect(saved).not.toHaveBeenCalled();expect(finish).toHaveBeenCalledOnce();
});

it('requires explicit cancellation of the latest scheduled version after a failed read',async()=>{
  const latest={...plan,version:'3',note:'其他HR刚刚改期'};
  const finish=vi.spyOn(followUpPlansApi,'finish').mockRejectedValueOnce(new Error('取消失败 503')).mockResolvedValue({...latest,status:'CANCELLED'});
  const read=vi.spyOn(followUpPlansApi,'list').mockRejectedValueOnce(new Error('读取失败 403')).mockResolvedValue({items:[latest],hasMore:false,nextCursor:null});
  const saved=mount(undefined,'cancel');
  fireEvent.change(screen.getByLabelText('取消原因（选填）'),{target:{value:'我的取消原因'}});
  fireEvent.click(screen.getByRole('button',{name:'确认取消'}));
  await screen.findByText('取消失败 503');
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('读取失败 403');
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('其他HR刚刚改期');
  expect(saved).not.toHaveBeenCalled();expect(finish).toHaveBeenCalledOnce();
  fireEvent.click(screen.getByRole('button',{name:'按最新版本重试'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(finish.mock.calls[1]).toEqual([{...plan,version:'3'},'cancel','我的取消原因']);
  expect(read).toHaveBeenCalledTimes(2);
});

it('recovers a lost reschedule response by checking the saved plan without another write',async()=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValue(new Error('改期响应丢失'));
  const read=vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,version:'2',note:'已约定新事项',startsAt:'2030-09-12T10:00:00+08:00',endsAt:'2030-09-12T10:30:00+08:00'}],hasMore:false,nextCursor:null});
  const saved=mount();
  fireEvent.change(screen.getByLabelText('沟通事项（选填）'),{target:{value:'已约定新事项'}});
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  expect(screen.getByRole('button',{name:'保存到日程'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(read).toHaveBeenCalledWith({applicationId:'app'});
  expect(patch).toHaveBeenCalledOnce();
});

it('preserves the draft on conflict and writes the current version only after explicit retry',async()=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValueOnce(new Error('改期响应丢失')).mockResolvedValue({...plan,version:'3'});
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,version:'2',note:'另一位HR的新事项'}],hasMore:false,nextCursor:null});
  const saved=mount();
  fireEvent.change(screen.getByLabelText('沟通事项（选填）'),{target:{value:'我的改期草稿'}});
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('另一位HR的新事项');
  expect(screen.getByLabelText('沟通事项（选填）')).toHaveValue('我的改期草稿');
  expect(patch).toHaveBeenCalledOnce();expect(saved).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button',{name:'按最新版本重试'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(patch.mock.calls[1]?.[1]).toMatchObject({note:'我的改期草稿',version:'2'});
});

it('does not discard edits made after the failed submission even when the prior write was saved',async()=>{
  vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValue(new Error('改期响应丢失'));
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,version:'2',note:'已提交事项'}],hasMore:false,nextCursor:null});
  const saved=mount();
  fireEvent.change(screen.getByLabelText('沟通事项（选填）'),{target:{value:'已提交事项'}});
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  fireEvent.change(screen.getByLabelText('沟通事项（选填）'),{target:{value:'后来修改的草稿'}});
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('当前预约已变化，填写内容已保留');
  expect(saved).not.toHaveBeenCalled();
  expect(screen.getByLabelText('沟通事项（选填）')).toHaveValue('后来修改的草稿');
});

it.each(['COMPLETED','CANCELLED'] as const)('blocks further rescheduling when the current plan is %s',async status=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValue(new Error('改期响应丢失'));
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,status,version:'2'}],hasMore:false,nextCursor:null});
  const saved=mount();
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('当前预约已结束，不能继续改期');
  expect(screen.queryByRole('button',{name:'按最新版本重试'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'保存到日程'})).toBeDisabled();
  expect(patch).toHaveBeenCalledOnce();expect(saved).not.toHaveBeenCalled();
});

it('keeps the draft and permits another read when checking the current plan fails',async()=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValue(new Error('改期响应丢失'));
  const read=vi.spyOn(followUpPlansApi,'list').mockRejectedValueOnce(new Error('预约读取失败')).mockResolvedValue({items:[{...plan,version:'2'}],hasMore:false,nextCursor:null});
  const saved=mount();
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await screen.findByText('预约读取失败');
  expect(screen.getByLabelText('沟通事项（选填）')).toHaveValue(plan.note);
  expect(screen.getByRole('button',{name:'保存到日程'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(read).toHaveBeenCalledTimes(2);expect(patch).toHaveBeenCalledOnce();
});

it('does not repeat a saved update when refreshing the parent fails',async()=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockResolvedValue({...plan,version:'2'});
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[{...plan,version:'2'}],hasMore:false,nextCursor:null});
  const saved=vi.fn(()=>Promise.resolve()).mockRejectedValueOnce(new Error('日程刷新失败'));
  mount(saved);
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('日程刷新失败');
  expect(screen.getByRole('button',{name:'保存到日程'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'核对最新预约'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledTimes(2));
  expect(patch).toHaveBeenCalledOnce();
});

it('prevents duplicate checks and writes while current-plan verification is pending',async()=>{
  const patch=vi.spyOn(followUpPlansApi,'reschedule').mockRejectedValue(new Error('改期响应丢失'));
  let resolveRead!:(value:Awaited<ReturnType<typeof followUpPlansApi.list>>)=>void;
  const read=vi.spyOn(followUpPlansApi,'list').mockImplementation(()=>new Promise(resolve=>{resolveRead=resolve;}));
  const saved=mount();
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('改期响应丢失');
  const check=screen.getByRole('button',{name:'核对最新预约'});
  fireEvent.click(check);fireEvent.click(check);
  await waitFor(()=>expect(check).toBeDisabled());
  expect(read).toHaveBeenCalledOnce();
  expect(screen.getByRole('button',{name:/保存到日程/})).toBeDisabled();
  resolveRead({items:[{...plan,version:'2'}],hasMore:false,nextCursor:null});
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(patch).toHaveBeenCalledOnce();
});

it('keeps ordinary creation retry enabled with unchanged inputs and idempotency key',async()=>{
  const create=vi.spyOn(followUpPlansApi,'create').mockRejectedValueOnce(new Error('创建响应丢失')).mockResolvedValue(plan);
  const saved=vi.fn(()=>Promise.resolve());
  const application={id:'app',version:1} as RecruitmentApplication;
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><FollowUpPlanDialog application={application} onClose={()=>undefined} onSaved={saved}/></QueryClientProvider></App></ConfigProvider>);
  fireEvent.change(screen.getByLabelText('开始时间（北京时间）'),{target:{value:'2030-09-12T10:00'}});
  fireEvent.change(screen.getByLabelText('沟通事项（选填）'),{target:{value:'创建时的沟通事项'}});
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await screen.findByText('创建响应丢失');
  expect(screen.getByRole('button',{name:'保存到日程'})).toBeEnabled();
  expect(screen.queryByRole('button',{name:'核对最新预约'})).not.toBeInTheDocument();
  expect(screen.getByLabelText('开始时间（北京时间）')).toHaveValue('2030-09-12T10:00');
  expect(screen.getByLabelText('沟通事项（选填）')).toHaveValue('创建时的沟通事项');
  fireEvent.click(screen.getByRole('button',{name:'保存到日程'}));
  await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(create).toHaveBeenCalledTimes(2);
  expect(create.mock.calls[1]).toEqual(create.mock.calls[0]);
  expect(create.mock.calls[0]?.[2]).toEqual(expect.any(String));
});

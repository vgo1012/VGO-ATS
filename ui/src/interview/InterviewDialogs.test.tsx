import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { ApiProblemError } from '../api/problem';
import { recruitmentApi, type ApplicationDetails } from '../recruitment/api';
import { InterviewContent } from './InterviewDialogs';
import { interviewApi, type FeedbackView, type InterviewDetails } from './api';

const state=vi.hoisted(()=>({actor:{userId:'hr',permissions:['recruitment.manage'],superAdmin:false} as SessionActor,status:'SUBMITTED',verdict:'PASS'}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:state.actor})}));
vi.mock('./api',()=>({interviewApi:{feedbackPage:vi.fn(),historyPage:vi.fn(),get:vi.fn()}}));
vi.mock('../recruitment/api',()=>({recruitmentApi:{application:vi.fn(),completeStep:vi.fn()}}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:({id}:{id:string})=><span>候选人姓名 {id}</span>,PeopleSelect:()=>null,TechnicalDetails:()=>null,UserName:({id}:{id:string})=><span>用户姓名 {id}</span>}));
vi.mock('./InterviewMaterials',()=>({InterviewMaterials:()=>null,InterviewReference:()=>null}));
vi.mock('./useInterviewMaterials',()=>({useInterviewMaterials:()=>({})}));
vi.mock('./InterviewFeedbackModal',()=>({InterviewFeedbackModal:({onSaved}:{onSaved:(value:FeedbackView)=>Promise<unknown>})=><button onClick={()=>void onSaved({feedback:{status:state.status},currentVersion:{verdict:state.verdict}} as FeedbackView)}>提交测试面评</button>}));
vi.mock('../recruitment/ContinueRecruitmentDialog',()=>({ContinueRecruitmentDialog:({applicationId}:{applicationId:string})=><div>衔接 {applicationId}</div>}));
vi.mock('../recruitment/ApplicationInterviewFeedbacks',()=>({ApplicationInterviewFeedbacks:()=> <div>流程记录</div>}));

const step={id:'step',status:'IN_PROGRESS',version:2,clientSubmissionId:null};
const details={application:{id:'app',ownerUserId:'hr',terminalAt:null},collaboratorUserIds:[],workflowSteps:[step]} as unknown as ApplicationDetails;
const interview={interview:{id:'interview',applicationId:'app',workflowStepId:'step',status:'SCHEDULED',interviewType:'INTERNAL',scheduledStartAt:'2026-09-09T02:00:00Z',scheduledEndAt:'2026-09-09T02:30:00Z'},participants:[{userId:'actual-interviewer'}]} as InterviewDetails;
const failedCalendar:InterviewDetails['externalCalendar']={mode:'WECOM_MEETING',activeType:null,status:'FAILED',title:'招聘面试',description:null,adminUserId:'wecom-user-1',inviteeUserIds:['wecom-user-1'],reminderMinutes:5,waitingRoomEnabled:false,allowEnterBeforeHost:true,enterMuteEnabled:true,externalObjectId:null,meetingCode:null,meetingLink:null,remoteStatus:null,lastErrorCode:'WECOM_60020',lastErrorDetail:'服务器出口 IP 未加入企业微信可信 IP',attemptCount:1,lastSyncedAt:null};
function mount(client=new QueryClient({defaultOptions:{queries:{retry:false}}})){return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><InterviewContent value={interview} loading={false} error={null} onChanged={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);}
function mountValue(value:InterviewDetails){return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><InterviewContent value={value} loading={false} error={null} onChanged={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);}

describe('schedule interview continuation',()=>{
  it.each([401,403])('hides cached facts and unmounts an open action after access fails with %s',async(status)=>{
    const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const onChanged=vi.fn(()=>Promise.resolve());
    const content=(error:unknown)=><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><InterviewContent value={interview} loading={false} error={error} onChanged={onChanged}/></QueryClientProvider></App></ConfigProvider>;
    const view=render(content(null));
    await screen.findByText('流程记录');
    fireEvent.click(await screen.findByRole('button',{name:'填写面评'}));
    expect(screen.getByRole('button',{name:'提交测试面评'})).toBeInTheDocument();
    view.rerender(content(new ApiProblemError({title:'权限已失效',detail:'不能再访问此面试',status,code:'FORBIDDEN',recoverable:false})));
    expect(screen.queryByText('流程记录')).not.toBeInTheDocument();
    expect(screen.queryByText('排期变更历史')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'提交测试面评'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'重试面试详情'}));
    expect(onChanged).toHaveBeenCalledOnce();
    view.rerender(content(null));
    expect(await screen.findByText('流程记录')).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'提交测试面评'})).not.toBeInTheDocument();
  });

  it('keeps cached facts available for a transient server error',async()=>{
    render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><InterviewContent value={interview} loading={false} error={new ApiProblemError({title:'暂不可用',detail:'稍后重试',status:503,code:'UNAVAILABLE',recoverable:true})} onChanged={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);
    expect(await screen.findByText('流程记录')).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'重试面试详情'})).toBeInTheDocument();
  });
  beforeEach(()=>{
    cleanup();vi.clearAllMocks();state.actor={userId:'hr',permissions:['recruitment.manage'],superAdmin:false} as SessionActor;state.status='SUBMITTED';state.verdict='PASS';
    vi.mocked(recruitmentApi.application).mockResolvedValue(details);
    vi.mocked(recruitmentApi.completeStep).mockResolvedValue(details);
    vi.mocked(interviewApi.get).mockResolvedValue({interview:{...interview.interview,status:'COMPLETED'},participants:interview.participants});
    vi.mocked(interviewApi.feedbackPage).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
    vi.mocked(interviewApi.historyPage).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  });
  it.each(['ONLINE','ONSITE'] as const)('deduplicates interview facts while preserving the %s location',async(meetingMode)=>{
    mountValue({...interview,interview:{...interview.interview,stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'EXTERNAL',meetingMode,locationText:'上海办公室 302'}});
    expect(await screen.findByText('外部面试 · 第 1 轮')).toBeInTheDocument();
    expect(screen.queryByText('面试类型',{exact:true})).not.toBeInTheDocument();
    expect(screen.queryByText('方式',{exact:true})).not.toBeInTheDocument();
    expect(screen.getAllByText('方式 / 地点')).toHaveLength(1);
    if(meetingMode==='ONLINE')expect(screen.getAllByText('线上面试',{exact:true})).toHaveLength(1);
    else expect(screen.getByText('上海办公室 302')).toBeInTheDocument();
  });
  it('retries failed recruitment details before showing shared actions',async()=>{
    vi.mocked(recruitmentApi.application).mockRejectedValueOnce(new Error('暂时不可用'));
    mount();
    fireEvent.click(await screen.findByRole('button',{name:'重试招聘操作'}));
    expect(await screen.findByRole('button',{name:'第 6 轮面试'})).toBeInTheDocument();
  });
  it('does not render an external meeting link with an unsafe protocol',()=>{
    mountValue({...interview,externalCalendar:{...failedCalendar,status:'SYNCED',meetingLink:'javascript:alert(1)'}});
    expect(screen.queryByRole('link',{name:'打开会议'})).not.toBeInTheDocument();
    cleanup();
    mountValue({...interview,externalCalendar:{...failedCalendar,status:'SYNCED',meetingLink:'https://meeting.example.test/join'}});
    expect(screen.getByRole('link',{name:'打开会议'})).toHaveAttribute('href','https://meeting.example.test/join');
  });
  it('keeps an invited interviewer read-only when recruitment access is denied',async()=>{
    state.actor={...state.actor,userId:'invited-interviewer',permissions:['recruitment.manage','interview.schedule']};
    vi.mocked(recruitmentApi.application).mockRejectedValue(new ApiProblemError({title:'无权访问',detail:'不能处理此招聘活动',status:403,code:'PERMISSION_DENIED',recoverable:false}));
    mountValue({...interview,participants:[{interviewId:'interview',userId:'invited-interviewer',participantRole:'INTERVIEWER',responseStatus:'ACCEPTED',createdAt:'2026-09-09T01:00:00Z'}],externalCalendar:failedCalendar});
    expect(await screen.findByText('仅可查看本次面试')).toBeInTheDocument();
    expect(screen.getByText('流程记录')).toBeInTheDocument();
    expect(screen.queryByText('招聘操作读取失败')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'重试招聘操作'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:/^取\s*消$/})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'第 6 轮面试'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'重试企业微信同步'})).not.toBeInTheDocument();
  });
  it('shows shared recruitment actions even when this interview is completed',async()=>{
    mountValue({...interview,interview:{...interview.interview,status:'COMPLETED'}});
    expect(await screen.findByRole('button',{name:'第 6 轮面试'})).toBeInTheDocument();
    for(const name of ['待定','面试','放弃','入职','黑名单'])expect(screen.getByRole('button',{name})).toBeInTheDocument();
  });
  it('hides shared recruitment actions for an unrelated recruitment manager',async()=>{
    state.actor={...state.actor,userId:'not-owner'};mount();
    await waitFor(()=>expect(recruitmentApi.application).toHaveBeenCalled());
    expect(screen.queryByRole('button',{name:'第 6 轮面试'})).not.toBeInTheDocument();
  });
  it('allows the application owner to fill feedback recorded for another interviewer',async()=>{
    mount();
    expect(await screen.findByRole('button',{name:'填写面评'})).toBeInTheDocument();
    expect(screen.getByText('实际面试官（记录）')).toBeInTheDocument();
  });
  it('removes the reschedule action while keeping interview cancellation available',async()=>{
    state.actor={...state.actor,permissions:['recruitment.manage','interview.schedule']};mount();
    expect(await screen.findByRole('button',{name:/^取\s*消$/})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'改期'})).not.toBeInTheDocument();
  });
  it('allows the application owner to fill feedback for an external label without client-feedback permission',async()=>{
    mountValue({...interview,interview:{...interview.interview,interviewType:'EXTERNAL'},participants:[]});
    expect(await screen.findByRole('button',{name:'填写面评'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'代录甲方反馈'})).not.toBeInTheDocument();
  });
  it('does not allow an unrelated user with the legacy feedback permission',async()=>{
    state.actor={...state.actor,userId:'not-owner',permissions:['interview.feedback.submit-own']};mount();
    await waitFor(()=>expect(recruitmentApi.application).not.toHaveBeenCalled());
    expect(screen.queryByRole('button',{name:'填写面评'})).not.toBeInTheDocument();
    await waitFor(()=>expect(interviewApi.feedbackPage).toHaveBeenCalled());
  });
  it('continues formal passing feedback for the application owner',async()=>{
    const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const invalidate=vi.spyOn(client,'invalidateQueries');
    mount(client);await waitFor(()=>expect(recruitmentApi.application).toHaveBeenCalledWith('app'));
    fireEvent.click(await screen.findByRole('button',{name:'填写面评'}));
    fireEvent.click(screen.getByRole('button',{name:'提交测试面评'}));
    await waitFor(()=>expect(recruitmentApi.completeStep).toHaveBeenCalledWith('app',step,'PASSED',expect.stringContaining('通过'),undefined));
    expect(await screen.findByText('衔接 app')).toBeInTheDocument();
    expect(invalidate).toHaveBeenCalledWith({queryKey:['application','app']});
    expect(invalidate).toHaveBeenCalledWith({queryKey:['applications']});
    expect(invalidate).toHaveBeenCalledWith({queryKey:['application-interviews','app']});
    expect(invalidate).toHaveBeenCalledWith({queryKey:['application-interview-feedback','app']});
  });
  it('does not continue a legacy draft response',async()=>{
    state.status='DRAFT';state.verdict='PASS';mount();await waitFor(()=>expect(recruitmentApi.application).toHaveBeenCalled());
    fireEvent.click(await screen.findByRole('button',{name:'填写面评'}));fireEvent.click(screen.getByRole('button',{name:'提交测试面评'}));
    await waitFor(()=>expect(screen.queryByRole('button',{name:'提交测试面评'})).not.toBeInTheDocument());
    expect(recruitmentApi.completeStep).not.toHaveBeenCalled();expect(screen.queryByText('衔接 app')).not.toBeInTheDocument();
  });
  it('defines a failed round directly from the formal feedback',async()=>{
    state.status='SUBMITTED';state.verdict='FAIL';mount();await waitFor(()=>expect(recruitmentApi.application).toHaveBeenCalled());
    fireEvent.click(await screen.findByRole('button',{name:'填写面评'}));fireEvent.click(screen.getByRole('button',{name:'提交测试面评'}));
    await waitFor(()=>expect(recruitmentApi.completeStep).toHaveBeenCalledWith('app',step,'FAILED',expect.stringContaining('不通过'),undefined));
    expect(await screen.findByText('衔接 app')).toBeInTheDocument();
  });
  it('does not fetch application details for a user with only the legacy feedback permission',()=>{
    state.actor={...state.actor,permissions:['interview.feedback.submit-own']};mount();
    expect(screen.queryByRole('button',{name:'填写面评'})).not.toBeInTheDocument();
    expect(recruitmentApi.application).not.toHaveBeenCalled();expect(interviewApi.get).not.toHaveBeenCalled();expect(screen.queryByText('衔接 app')).not.toBeInTheDocument();
  });
  it('offers recovery to collaborators but not unrelated managers',async()=>{
    vi.mocked(interviewApi.feedbackPage).mockResolvedValue({items:[{feedback:{status:'SUBMITTED',authorUserId:'hr'},currentVersion:{verdict:'PASS'}} as FeedbackView],hasMore:false,nextCursor:null});
    vi.mocked(recruitmentApi.application).mockResolvedValue({...details,application:{...details.application,ownerUserId:'another'},collaboratorUserIds:['hr']});
    const view=mountValue({interview:{...interview.interview,status:'COMPLETED'},participants:interview.participants});fireEvent.click(await screen.findByRole('button',{name:'按正式面评继续招聘跟进'}));expect(await screen.findByText('衔接 app')).toBeInTheDocument();view.unmount();
    vi.mocked(recruitmentApi.application).mockResolvedValue({...details,application:{...details.application,ownerUserId:'another'}});mount();
    await screen.findByText('流程记录');expect(screen.queryByRole('button',{name:'按正式面评继续招聘跟进'})).not.toBeInTheDocument();
  });
  it('keeps a completed external interview read-only while allowing recruitment continuation',async()=>{
    state.actor={userId:'hr',permissions:['recruitment.manage','interview.schedule','interview.external-feedback.record'],superAdmin:false} as SessionActor;
    vi.mocked(interviewApi.feedbackPage).mockResolvedValue({items:[{feedback:{status:'SUBMITTED',authorUserId:'hr'},currentVersion:{verdict:'PASS'}} as FeedbackView],hasMore:false,nextCursor:null});
    mountValue({interview:{...interview.interview,status:'COMPLETED',interviewType:'EXTERNAL_CLIENT'},participants:[]});

    await screen.findByText('流程记录');
    expect(screen.queryByRole('button',{name:'代录甲方反馈'})).not.toBeInTheDocument();
    expect(screen.queryByText('面试通知')).not.toBeInTheDocument();
    expect(screen.getByText('甲方面试已完成 · 记录只读')).toBeInTheDocument();
    expect(await screen.findByRole('button',{name:'按正式面评继续招聘跟进'})).toBeInTheDocument();
  });

  it('shows a failed enterprise meeting projection without hiding the local schedule',async()=>{
    state.actor={...state.actor,permissions:['recruitment.manage','interview.schedule']};
    mountValue({...interview,externalCalendar:failedCalendar});
    expect(await screen.findByText('企业微信会议')).toBeInTheDocument();
    expect(screen.getByText('会议：').closest('.interview-conference-row')).toHaveTextContent('失败企业微信会议');
    expect(screen.queryByText('企业微信联动')).not.toBeInTheDocument();
    expect(screen.getByText('企业微信日程同步失败，本项目排期已保存')).toBeInTheDocument();
    expect(await screen.findByRole('button',{name:'重试企业微信同步'})).toBeInTheDocument();
    expect(screen.getByText('待进行')).toBeInTheDocument();
  });
});

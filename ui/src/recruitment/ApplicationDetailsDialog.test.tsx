import { interviewApi } from '../interview/api';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { App,Card,ConfigProvider } from 'antd';
import { cleanup,render,screen } from '@testing-library/react';
import { afterEach,beforeEach,expect,it,vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { recruitmentApi,type ApplicationDetails } from './api';
import { ApplicationContent } from './ApplicationDetailsDialog';

const actor={userId:'hr',superAdmin:true} as SessionActor;
vi.mock('../auth/session',()=>({useSession:()=>({actor})}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> '候选人',JobName:()=> '岗位',PeopleSelect:()=>null,ResumeVersionName:()=> '简历版本',TechnicalDetails:()=>null,UserName:()=> '招聘专员'}));
vi.mock('../interview/InterviewMaterials',()=>({InterviewMaterials:({id,scope}:{id:string;scope?:string})=><button data-testid="materials">{scope} 材料 {id}</button>}));
vi.mock('./ApplicationInterviewFeedbacks',()=>({ApplicationInterviewFeedbacks:({children}:{children?:ReactNode})=> <Card title="流程记录">正式面评记录{children}</Card>}));
vi.mock('./SharedApplicationHistory',()=>({SharedApplicationHistory:()=>null}));

const details={
  application:{id:'app',candidateId:'candidate',jdId:'jd',demandId:'demand',jdVersionId:'jd-version',resumeVersionId:'resume',workflowTemplateVersionId:'workflow',matchPlanVersionId:'plan',recruitmentType:'INTERNAL',currentStage:'INTERVIEW',outcome:null,ownerUserId:'hr',terminalAt:null},
  collaboratorUserIds:[],decisions:[],offerStatusRecords:[],elapsedSeconds:3600,
  workflowSteps:[{id:'interview',stepCode:'INTERVIEW_ROUND_1',status:'IN_PROGRESS',sequenceNo:1,resultReason:null}],
} as unknown as ApplicationDetails;

function mount(readOnly?:boolean,operationsOnly=false,sharedLibrary=false){
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><ApplicationContent value={details} onChanged={()=>Promise.resolve()} {...(readOnly===undefined?{}:{readOnly})} operationsOnly={operationsOnly} sharedLibrary={sharedLibrary}/></QueryClientProvider></App></ConfigProvider>);
}

beforeEach(()=>{vi.spyOn(interviewApi,'allForApplication').mockResolvedValue([]);vi.spyOn(recruitmentApi,'communications').mockResolvedValue([]);});
afterEach(()=>{cleanup();vi.restoreAllMocks();});

it.each([false,true])('非归属用户按实际入口展示查看范围提示，共享=%s',sharedLibrary=>{
  const original={...actor};
  Object.assign(actor,{userId:'shared-reader',superAdmin:false,permissions:[],roles:[]});
  try {
    mount(false,false,sharedLibrary);
    expect(screen.getByText(sharedLibrary?'当前为共享查看':'当前为管理查看')).toBeInTheDocument();
    expect(screen.queryByText(sharedLibrary?'当前为管理查看':'当前为共享查看')).not.toBeInTheDocument();
    expect(screen.getByText(sharedLibrary?'此候选人已进入共享简历库，所有系统内登录用户均可完整查看相关资料和流程记录；修改资料、推进流程仍遵守原有权限规则。':'你可以查看此招聘流程；只有归属 HR 或已添加的流程协作人可以处理。')).toBeInTheDocument();
  } finally {Object.assign(actor,original);}
});

it('只读详情保留流程和面评记录并隐藏全部处理入口',()=>{
  mount(true);
  expect(screen.getByText('正式面评记录')).toBeInTheDocument();
  expect(screen.getByText('责任与协作')).toBeInTheDocument();
  for(const name of ['管理员跳过','调整协作人','转交负责人'])expect(screen.queryByRole('button',{name})).not.toBeInTheDocument();
});

it('其他入口默认保留原有处理操作',()=>{
  mount();
  expect(screen.getByRole('button',{name:'管理员跳过'})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'调整协作人'})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'转交负责人'})).toBeInTheDocument();
});

it('已终止的 Application 只保留历史查看且不能重新开启',()=>{
  const original=details.application;
  details.application={...original,currentStage:'TERMINATED',terminalAt:'2026-09-22T08:00:00Z'};
  try {
    mount();
    expect(screen.queryByRole('button',{name:'重新开启'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'调整协作人'})).not.toBeInTheDocument();
    expect(screen.getByText('正式面评记录')).toBeInTheDocument();
  } finally {details.application=original;}
});

it('流程管理只展示操作区并复用原有处理入口',()=>{
  mount(false,true);
  expect(screen.getByRole('button',{name:'管理员跳过'})).toBeInTheDocument();
  expect(screen.getByRole('button',{name:'调整协作人'})).toBeInTheDocument();
  expect(screen.queryByText('流程记录')).not.toBeInTheDocument();
  expect(screen.queryByText('招聘资料')).not.toBeInTheDocument();
});

it('沟通情况在只读详情的流程记录中展示',async()=>{
  vi.mocked(recruitmentApi.communications).mockResolvedValue([{id:'note',applicationId:'app',summary:'约定下周再沟通',channel:'OTHER',result:'FOLLOW_UP',nextPlan:'',communicatedAt:'2026-09-14T10:00:00Z',recordedByUserId:'hr'}]);
  mount(true);
  const note=await screen.findByText('约定下周再沟通');
  expect(note.closest('.ant-card')).toHaveTextContent('流程记录');
  expect(screen.queryByRole('button',{name:'保存记录'})).not.toBeInTheDocument();
});

it('多轮面试只显示一个材料入口并优先使用当前步骤的授权面试记录',async()=>{
  vi.mocked(interviewApi.allForApplication).mockResolvedValue([
    {id:'other',workflowStepId:'other-step',stepCode:'INTERVIEW_ROUND_1',roundNo:1,scheduledStartAt:'2099-09-14T10:00:00Z'},
    {id:'current',workflowStepId:'interview',stepCode:'INTERVIEW_ROUND_1',roundNo:2,scheduledStartAt:'2026-09-14T10:00:00Z'},
  ] as Awaited<ReturnType<typeof interviewApi.allForApplication>>);
  mount(true);
  expect(await screen.findByTestId('materials')).toHaveTextContent('application 材料 current');
  expect(screen.queryByRole('combobox',{name:'材料对应面试轮次'})).not.toBeInTheDocument();
});

it('未排期的直接推荐仍提供简历和 JD 入口',async()=>{
  mount(true);
  expect(await screen.findByRole('button',{name:'查看简历与 JD'})).toBeInTheDocument();
  expect(screen.queryByText('暂无面试材料')).not.toBeInTheDocument();
});

it('外部招聘详情不显示客户推荐状态或功能',async()=>{
  const original=details.application;
  details.application={...original,recruitmentType:'EXTERNAL',currentStage:'IN_PROCESS',currentStepCode:'CLIENT_SUBMISSION'};
  const steps=details.workflowSteps;
  details.workflowSteps=[{...steps[0]!,stepCode:'CLIENT_SUBMISSION'}];
  try {
    mount(true);
    expect(await screen.findByRole('button',{name:'查看简历与 JD'})).toBeInTheDocument();
    expect(screen.queryByText('客户推荐与反馈')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'推荐给客户'})).not.toBeInTheDocument();
    expect(screen.queryByText(/推荐给客户/)).not.toBeInTheDocument();
  } finally { details.application=original;details.workflowSteps=steps; }
});

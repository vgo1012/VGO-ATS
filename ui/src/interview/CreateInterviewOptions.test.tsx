import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { recruitmentApi, type ApplicationDetails, type RecruitmentApplication, type WorkflowStep } from '../recruitment/api';
import { talentApi } from '../talent/api';
import { demandApi } from '../demand/api';
import { CreateInterviewModal } from './InterviewDialogs';
import { wecomCollaborationApi } from './api';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'current-user',displayName:'当前用户'}})}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> <span>候选人</span>,PeopleSelect:({value}:{value?:string[]})=><input aria-label="实际面试官" readOnly value={value?.join(',')??''}/>,TechnicalDetails:()=>null,UserName:()=>null}));
afterEach(()=>{cleanup();vi.restoreAllMocks();});
const mount=(application?:ApplicationDetails,workflowStep?:WorkflowStep,open=true)=>render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CreateInterviewModal open={open} {...(application?{application}:{})} {...(workflowStep?{workflowStep}:{})} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>);

it('loads a 100-application page through one deduplicated candidate request',async()=>{
  const items=Array.from({length:100},(_,index)=>({id:`app-${index}`,candidateId:`person-${index%50}`,jdId:'job',terminalAt:null} as RecruitmentApplication));
  vi.spyOn(recruitmentApi,'applications').mockResolvedValue({items,hasMore:false,nextCursor:null});
  vi.spyOn(demandApi,'jds').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  const batch=vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([]);
  const single=vi.spyOn(talentApi,'candidate');
  mount();
  await waitFor(()=>expect(batch).toHaveBeenCalledTimes(1));
  expect(batch).toHaveBeenCalledWith(Array.from({length:50},(_,index)=>`person-${index}`));
  expect(single).not.toHaveBeenCalled();
  fireEvent.mouseDown(screen.getByRole('combobox',{name:'候选人 / 应聘岗位'}));
  expect(await screen.findAllByText('候选人信息暂不可用 · 岗位名称暂不可用')).not.toHaveLength(0);
});

it('does not load hidden option sources when scheduling an existing application',async()=>{
  const apps=vi.spyOn(recruitmentApi,'applications');
  const batch=vi.spyOn(talentApi,'candidateBatch');
  const jobs=vi.spyOn(demandApi,'jds');
  const versions=vi.spyOn(demandApi,'jdVersions');
  const application={application:{id:'app',candidateId:'person',jdVersionId:'jd-version',recruitmentType:'INTERNAL'},workflowSteps:[]} as unknown as ApplicationDetails;
  await act(()=>{mount(application,{id:'step',stepCode:'INTERVIEW_ROUND_1'} as WorkflowStep);return Promise.resolve();});
  expect(screen.queryByRole('combobox',{name:'候选人 / 应聘岗位'})).not.toBeInTheDocument();
  expect(apps).not.toHaveBeenCalled();
  expect(batch).not.toHaveBeenCalled();
  expect(jobs).not.toHaveBeenCalled();
  expect(versions).not.toHaveBeenCalled();
});

it('does not request option sources while closed',async()=>{
  const apps=vi.spyOn(recruitmentApi,'applications');
  const batch=vi.spyOn(talentApi,'candidateBatch');
  const jobs=vi.spyOn(demandApi,'jds');
  await act(()=>{mount(undefined,undefined,false);return Promise.resolve();});
  expect(apps).not.toHaveBeenCalled();
  expect(batch).not.toHaveBeenCalled();
  expect(jobs).not.toHaveBeenCalled();
});

it('loads enterprise members and defaults calendar titles to the selected job and candidate',async()=>{
  const application={application:{id:'app',candidateId:'person',jdVersionId:'jd-version',recruitmentType:'INTERNAL'},workflowSteps:[]} as unknown as ApplicationDetails;
  const candidate=vi.spyOn(talentApi,'candidate').mockResolvedValue({id:'person',fullName:'示例甲'} as Awaited<ReturnType<typeof talentApi.candidate>>);
  const job=vi.spyOn(demandApi,'jdVersion').mockResolvedValue({id:'jd-version',title:'IDC运维工程师（华北数据中心）'} as Awaited<ReturnType<typeof demandApi.jdVersion>>);
  const members=vi.spyOn(wecomCollaborationApi,'members').mockResolvedValue([
    {userId:'wecom-user-1',name:'测试成员',position:'面试官',linkedUserAccountId:'local-user'},
  ]);
  mount(application,{id:'step',stepCode:'INTERVIEW_ROUND_1'} as WorkflowStep);
  await waitFor(()=>expect(screen.getByLabelText('实际面试官')).toHaveValue('current-user'));
  expect(screen.getByRole('radio',{name:'直接填写面评'})).toBeChecked();
  expect(screen.queryByRole('radio',{name:'仅本项目'})).not.toBeInTheDocument();
  expect(members).not.toHaveBeenCalled();
  await act(()=>new Promise(resolve=>setTimeout(resolve,400)));
  fireEvent.click(await screen.findByRole('radio',{name:'企业微信会议'}));
  await waitFor(()=>{
    expect(members).toHaveBeenCalledOnce();
    expect(candidate).toHaveBeenCalledOnce();
    expect(job).toHaveBeenCalledOnce();
  },{timeout:5000});
  await waitFor(()=>expect(screen.getByRole('textbox',{name:'会议标题'})).toHaveValue('IDC运维工程师（华北数据… · 示例甲'),{timeout:5000});
  fireEvent.click(screen.getByRole('radio',{name:'企业微信日程'}));
  await waitFor(()=>expect(screen.getByRole('textbox',{name:'日程标题'})).toHaveValue('IDC运维工程师（华北数据中心） · 示例甲'));
  fireEvent.change(screen.getByRole('textbox',{name:'日程标题'}),{target:{value:'自定义会议'}});
  fireEvent.click(screen.getByRole('radio',{name:'企业微信会议'}));
  expect(await screen.findByRole('textbox',{name:'会议标题'})).toHaveValue('自定义会议');
  expect(screen.queryByRole('combobox',{name:/企业微信发起人/})).not.toBeInTheDocument();
  expect(screen.queryByText(/会议由本应用创建|会议管理员使用当前 HR|日程管理员使用当前 HR|实际面试官自动加入/)).not.toBeInTheDocument();
  expect(screen.queryByLabelText('面评')).not.toBeInTheDocument();
  expect(screen.getByLabelText('参会说明（可选）')).toBeInTheDocument();
  expect(screen.queryByText('更多设置')).not.toBeInTheDocument();
  expect(screen.getByRole('combobox',{name:/额外企业微信参与人/})).toBeInTheDocument();
  expect(screen.getByText('开启等候室')).toBeInTheDocument();
},30000);

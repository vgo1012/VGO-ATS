import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import type * as Antd from 'antd';
import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { demandApi } from '../demand/api';
import { interviewApi, type InterviewDetails } from '../interview/api';
import { submissionApi } from '../submission/api';
import { talentApi, type Candidate } from '../talent/api';
import { recruitmentApi, type ApplicationDetails, type WorkflowStep } from './api';
import { activityRiskApi, type CompanyRestriction } from './activityApi';
import { followUpPlansApi, type FollowUpPlan } from './followUpPlansApi';
import { Applications } from './RecruitmentLedger';

const state=vi.hoisted(()=>({actor:{userId:'hr',permissions:['recruitment.manage','interview.schedule','submission.manage'],superAdmin:false} as SessionActor}));
vi.mock('antd',async()=>{const actual=await vi.importActual<typeof Antd>('antd');return {...actual,DatePicker:Object.assign(()=>null,{RangePicker:()=>null})};});
vi.mock('../auth/session',()=>({useSession:()=>({actor:state.actor})}));
vi.mock('../demand/JdSelector',()=>({JdSelector:()=>null}));
vi.mock('./ClientFilter',()=>({ClientFilter:()=>null}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> '李明',JobName:()=> '工程师',PeopleSelect:()=>null,TechnicalDetails:()=>null,UserName:()=> '张 HR',ResumeVersionName:()=>null}));
vi.mock('../components/ScheduleDateTimePicker',()=>({ScheduleDateTimePicker:({value,onChange,id}:{value?:string;onChange:(value:string)=>void;id:string})=><input id={id} value={value??''} onChange={event=>onChange(event.target.value)}/>}));
const base={application:{id:'app',candidateId:'person',jdId:'jd',resumeVersionId:'resume',ownerUserId:'hr',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',terminalAt:null,version:1},collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[]} as unknown as ApplicationDetails;
configure({asyncUtilTimeout:5_000});
const clients:QueryClient[]=[];
function mount(value=base){
  vi.mocked(recruitmentApi.application).mockResolvedValue(value);
  const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});clients.push(client);
  client.setQueryData(['application',value.application.id],value);
  client.setQueryData(['follow-up-plans',value.application.id,{status:'SCHEDULED'}],{pages:[{items:[],hasMore:false,nextCursor:null}],pageParams:[undefined]});
  return render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><Applications items={[value.application]} loading={false} error={null} filters={{}} onFilters={vi.fn()} onOpen={vi.fn()} page={1} pageSize={20} onPageSizeChange={vi.fn()} hasPrevious={false} hasNext={false} onPrevious={vi.fn()} onNext={vi.fn()}/></QueryClientProvider></App></ConfigProvider>);
}
async function open(name:RegExp){fireEvent.click(screen.getByRole('button',{name}));return screen.findByRole('dialog');}
async function select(label:string,text:string){fireEvent.mouseDown(screen.getByRole('combobox',{name:label}));fireEvent.click(await screen.findByText(text,{selector:'.ant-select-item-option-content'}));}
beforeEach(()=>{
  Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()});
  Object.defineProperty(HTMLElement.prototype,'scrollIntoView',{configurable:true,value:vi.fn()});
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([{id:'person',fullName:'李明'} as Candidate]);
  vi.spyOn(talentApi,'resumeVersionFactSummaries').mockResolvedValue([]);
  vi.spyOn(recruitmentApi,'application').mockResolvedValue(base);
  vi.spyOn(recruitmentApi,'nextAction').mockResolvedValue(base);
  vi.spyOn(recruitmentApi,'communicate').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.communicate>>);
  vi.spyOn(recruitmentApi,'decide').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.decide>>);
  vi.spyOn(recruitmentApi,'addOffer').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.addOffer>>);
  vi.spyOn(recruitmentApi,'onboard').mockResolvedValue(undefined);
  vi.spyOn(recruitmentApi,'hire').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.hire>>);
  vi.spyOn(interviewApi,'allForApplication').mockResolvedValue([]);
  vi.spyOn(interviewApi,'list').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(interviewApi,'create').mockResolvedValue({} as InterviewDetails);
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(followUpPlansApi,'create').mockResolvedValue({} as FollowUpPlan);
  vi.spyOn(submissionApi,'list').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(demandApi,'clients').mockResolvedValue({items:[{id:'a',name:'公司 A'},{id:'b',name:'公司 B'}] as Awaited<ReturnType<typeof demandApi.clients>>['items'],hasMore:false,nextCursor:null});
  vi.spyOn(activityRiskApi,'restrictions').mockResolvedValue([]);
  vi.spyOn(activityRiskApi,'create').mockResolvedValue({} as CompanyRestriction);
});
afterEach(()=>{cleanup();clients.forEach(client=>client.clear());clients.length=0;vi.restoreAllMocks();});
it.each([
  [/^待\s*定$/,'待定','保存记录'],[/^面\s*试$/,'面试',/保\s*存/],[/^放\s*弃$/,'放弃','确认放弃'],[/^入\s*职$/,'确认录用','确认录用'],[/^黑名单$/,'黑名单',/保\s*存/],
] as const)('列表 %s 直接打开对应操作，取消不会写入',async(button,title,save)=>{
  mount();await open(button);
  await screen.findByRole('button',{name:save});
  const dialog=screen.getByRole('dialog');
  expect(dialog.querySelector('.ant-modal-title')).toHaveTextContent(title);
  expect(screen.getAllByRole('dialog')).toHaveLength(1);
  fireEvent.click(within(dialog).getByRole('button',{name:'Cancel'}));
  for(const mutation of [recruitmentApi.nextAction,recruitmentApi.communicate,recruitmentApi.decide,recruitmentApi.onboard,recruitmentApi.hire,recruitmentApi.addOffer,interviewApi.create,followUpPlansApi.create,activityRiskApi.create])expect(mutation).not.toHaveBeenCalled();
});
it('待定记录保存评论和操作来源，不创建面试或退回现有步骤',async()=>{
  mount();await open(/^待\s*定$/);await screen.findByRole('button',{name:'保存记录'});
  fireEvent.change(screen.getByLabelText('沟通情况'),{target:{value:'下周再联系'}});
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  await waitFor(()=>expect(recruitmentApi.communicate).toHaveBeenCalledWith('app',expect.objectContaining({summary:'下周再联系',result:'FOLLOW_UP'}),expect.any(String),expect.objectContaining({action:'PENDING'})));
  expect(recruitmentApi.nextAction).not.toHaveBeenCalled();expect(interviewApi.create).not.toHaveBeenCalled();
});
it('当前沟通步骤的待定记录要求填写沟通情况，不完成原步骤',async()=>{
  const step={id:'communication-step',stepCode:'CANDIDATE_COMMUNICATION',status:'IN_PROGRESS',sequenceNo:1,version:3,defaultFollowUpChannel:'PHONE'} as WorkflowStep;
  const complete=vi.spyOn(recruitmentApi,'completeStep').mockResolvedValue(base);
  mount({...base,application:{...base.application,currentStage:'IN_PROCESS'},workflowSteps:[step]});
  await open(/^待\s*定$/);
  const comment=await screen.findByLabelText('沟通情况');
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  await waitFor(()=>expect(comment.closest('.ant-form-item')).toHaveClass('ant-form-item-has-error'));
  expect(recruitmentApi.communicate).not.toHaveBeenCalled();
  fireEvent.change(comment,{target:{value:'稍后再次联系'}});
  fireEvent.click(screen.getByRole('button',{name:'保存记录'}));
  await waitFor(()=>expect(recruitmentApi.communicate).toHaveBeenCalledWith('app',expect.objectContaining({summary:'稍后再次联系',result:'FOLLOW_UP'}),expect.any(String),expect.objectContaining({action:'PENDING'})));
  expect(complete).not.toHaveBeenCalled();
  expect(recruitmentApi.nextAction).not.toHaveBeenCalled();
});
it.each([['公司淘汰','REJECT'],['候选人退出','WITHDRAW'],['其他','REJECT']])('放弃将 %s 与原因准确提交',async(label,decision)=>{
  mount();await open(/^放\s*弃$/);await screen.findByLabelText('原因评论');await select('放弃类型',label);
  fireEvent.click(screen.getByRole('button',{name:'确认放弃'}));
  await waitFor(()=>expect(screen.getByLabelText('原因评论')).toHaveAttribute('aria-invalid','true'));expect(recruitmentApi.decide).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('原因评论'),{target:{value:'双方确认不再继续'}});fireEvent.click(screen.getByRole('button',{name:'确认放弃'}));
  await waitFor(()=>expect(recruitmentApi.decide).toHaveBeenCalledWith('app',decision,'双方确认不再继续',undefined,expect.objectContaining({action:'ENDED'})));
});
it('确认录用合并必填原因与 Offer 进展并一次进入入职流程',async()=>{
  mount({...base,application:{...base.application,currentStage:'OFFER'},offerStatusRecords:[{status:'ISSUED',occurredAt:'2026-01-01T00:00:00Z'}] as ApplicationDetails['offerStatusRecords']});
  await open(/^入\s*职$/);const confirm=await screen.findByRole('button',{name:'确认录用'});
  fireEvent.click(confirm);
  await waitFor(()=>expect(screen.getByLabelText('原因评论')).toHaveAttribute('aria-invalid','true'));
  expect(recruitmentApi.hire).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('原因评论'),{target:{value:'已完成录用确认'}});
  fireEvent.click(confirm);
  await waitFor(()=>expect(recruitmentApi.hire).toHaveBeenCalledWith('app',{offerStatus:'ISSUED',reason:'已完成录用确认'},expect.objectContaining({action:'ONBOARDED'})));
  expect(recruitmentApi.decide).not.toHaveBeenCalled();
});
it('黑名单单选公司并直接保存原因和来源，无需证据引用',async()=>{
  mount();await open(/^黑名单$/);
  const company=await screen.findByLabelText('公司标签');
  const dialog=within(company.closest('[role="dialog"]') as HTMLElement);
  const save=dialog.getByRole('button',{name:/保\s*存/});
  expect(save).toBeDisabled();expect(activityRiskApi.restrictions).not.toHaveBeenCalled();
  fireEvent.mouseDown(company);fireEvent.click(await screen.findByText('公司 A',{selector:'.ant-select-item-option-content'}));
  await dialog.findByText('记录【公司 A】黑名单标签并结束当前招聘流程；简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。');
  await waitFor(()=>expect(activityRiskApi.restrictions).toHaveBeenCalledWith('person','a'));
  fireEvent.mouseDown(company);fireEvent.click(await screen.findByText('公司 B',{selector:'.ant-select-item-option-content'}));
  await dialog.findByText('记录【公司 B】黑名单标签并结束当前招聘流程；简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。');
  await waitFor(()=>expect(activityRiskApi.restrictions).toHaveBeenCalledWith('person','b'));
  expect(dialog.queryByText('记录【公司 A】黑名单标签并结束当前招聘流程；简历在流程结束后进入共享简历库并保留标签，不进入个人待筛选，招聘推进仍受黑名单限制。')).not.toBeInTheDocument();
  fireEvent.change(dialog.getByLabelText('原因评论'),{target:{value:'已核验公司限制'}});
  expect(dialog.queryByLabelText('证据引用')).not.toBeInTheDocument();
  await waitFor(()=>expect(save).toBeEnabled());fireEvent.click(save);
  await waitFor(()=>expect(activityRiskApi.create).toHaveBeenCalledWith('app',{clientId:'b',reasonDetail:'已核验公司限制',sourceChannel:'MANUAL'},expect.objectContaining({action:'BLACKLIST'})));
  expect(recruitmentApi.decide).not.toHaveBeenCalled();
});

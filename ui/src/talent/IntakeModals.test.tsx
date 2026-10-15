import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { confirmCurrentIntake, talentApi, type IdentityConflict, type ResumeIntake } from './api';
import { CandidateIdentityConflicts } from './CandidateIdentityConflicts';
import { ConfirmIntakeModal, ConflictResolutionModal } from './IntakeModals';

vi.mock('./api', async original => ({ ...await original(), confirmCurrentIntake: vi.fn(), talentApi: { resolveConflict: vi.fn(), intake:vi.fn(), candidate:vi.fn(), candidateConflicts:vi.fn() } }));
vi.mock('../display/BusinessReferences', () => ({ CandidateName: () => '候选人' }));

afterEach(()=>{cleanup();vi.clearAllMocks();});
const intake=(id:string,fields:Partial<ResumeIntake>):ResumeIntake=>({id,batchId:'batch',sourceChannelId:'source',sourceRecruiterUserId:'hr',defaultOwnerUserId:'hr',ownerUserId:'hr',ownerDepartmentId:'dep',handledByUserId:null,originalFileName:`${id}.pdf`,mediaType:'application/pdf',sizeBytes:1,contentSha256:id,scanStatus:'CLEAN',status:'IDENTITY_REVIEW_REQUIRED',importStatus:'PENDING',processingStatus:'SUCCEEDED',failureCode:null,candidateId:null,resumeVersionId:null,version:1,createdAt:'2026-09-09T00:00:00Z',...fields});
const conflict:IdentityConflict={id:'conflict',intakeId:'incoming',candidateIdA:'person',candidateIdB:null,conflictType:'CONTACT_NAME_MISMATCH',status:'OPEN',resolution:null,chosenCandidateId:null,reason:null,createdAt:'2026-09-09T00:00:00Z'};
const hiddenConflict:IdentityConflict={...conflict,candidateIdA:null,conflictType:'CANDIDATE_ACCESS_REQUIRED'};
beforeEach(()=>{
  vi.mocked(talentApi.intake).mockResolvedValue(intake('incoming',{parsedFullName:'本次姓名',parsedPhone:'13800000000',parsedEmail:'incoming@example.com'}));
  vi.mocked(talentApi.candidate).mockResolvedValue({id:'person',fullName:'已有姓名',primaryPhone:'13900000000',primaryEmail:'existing@example.com',ownerUserId:'hr',ownerDepartmentId:'dep',status:'ACTIVE',version:1,createdAt:'',updatedAt:''});
  vi.mocked(confirmCurrentIntake).mockResolvedValue(intake('saved',{}));
  vi.mocked(talentApi.resolveConflict).mockResolvedValue(conflict);
});
function mount(content:React.ReactNode,client=new QueryClient({defaultOptions:{queries:{retry:false}}})){
  const view=render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}>{content}</QueryClientProvider></App></ConfigProvider>);
  return {client,...view};
}

it('clears parsed contact fields when switching to another identity exception',async()=>{
  const first=intake('first',{parsedFullName:'张三',parsedEmail:'first@example.com',parsedPhone:'13800000000'});
  const second=intake('second',{parsedFullName:'李四',parsedEmail:null,parsedPhone:null});
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  const content=(value:ResumeIntake)=><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}><ConfirmIntakeModal value={value} onClose={()=>undefined} onSaved={()=>Promise.resolve()}/></QueryClientProvider></App></ConfigProvider>;
  const view=render(content(first));
  expect(await screen.findByLabelText('邮箱')).toHaveValue('first@example.com');
  view.rerender(content(second));
  await waitFor(()=>expect(screen.getByLabelText('候选人姓名')).toHaveValue('李四'));
  expect(screen.getByLabelText('邮箱')).toHaveValue('');
  expect(screen.getByLabelText('手机号')).toHaveValue('');
});
it.each(['intake','conflict'] as const)('focuses invalid %s fields without sending a write', async kind => {
  const client = new QueryClient();
  const onSaved = vi.fn().mockResolvedValue(undefined);
  render(<ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={client}>
    {kind === 'intake' ? <ConfirmIntakeModal value={intake('invalid',{})} onClose={vi.fn()} onSaved={onSaved}/> :
      <ConflictResolutionModal value={conflict} onClose={vi.fn()} onSaved={onSaved}/>}
  </QueryClientProvider></App></ConfigProvider>);
  const field = await screen.findByLabelText(kind === 'intake' ? '候选人姓名' : '处理原因');
  const scroll = vi.fn();
  field.scrollIntoView = scroll;
  const submit = screen.getByRole('button',{name:kind === 'intake' ? '确认异常身份' : '确认处理'});
  await waitFor(()=>expect(submit).toBeEnabled());
  submit.focus();
  fireEvent.click(submit);
  await waitFor(() => expect(field).toHaveAttribute('aria-invalid','true'));
  await waitFor(() => expect(field).toHaveFocus());
  expect(scroll).toHaveBeenCalledWith({block:'nearest'});
  expect(confirmCurrentIntake).not.toHaveBeenCalled();
  expect(talentApi.resolveConflict).not.toHaveBeenCalled();
});

it('explains missing contacts and permits deliberate confirmation without invented contact information',async()=>{
  mount(<ConfirmIntakeModal value={intake('no-contact',{parsedFullName:'李四',failureCode:'IDENTITY_REVIEW_REQUIRED'})} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  expect(await screen.findByText('未识别到手机号和邮箱，无法自动核对身份')).toBeVisible();
  fireEvent.click(screen.getByRole('button',{name:'确认异常身份'}));
  await waitFor(()=>expect(confirmCurrentIntake).toHaveBeenCalledWith(expect.objectContaining({id:'no-contact'}),{fullName:'李四',phone:'',email:''}));
});

it('labels a prepared intake confirmation without implying an identity exception',async()=>{
  mount(<ConfirmIntakeModal value={intake('ready',{status:'READY_FOR_CONFIRMATION',parsedFullName:'李四',parsedPhone:'13800000000'})} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  expect(await screen.findByRole('dialog',{name:'确认入库 · ready.pdf'})).toBeVisible();
  expect(screen.getByRole('button',{name:'确认入库'})).toBeEnabled();
  expect(screen.queryByText('核对异常身份')).not.toBeInTheDocument();
});

it('does not submit a known conflict through ordinary identity confirmation when details are unavailable',async()=>{
  mount(<ConfirmIntakeModal value={intake('conflicted',{parsedFullName:'李四',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'})} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  expect(await screen.findByText('身份信息与已有候选人存在冲突，具体差异待读取')).toBeVisible();
  expect(screen.getByRole('button',{name:'确认异常身份'})).toBeDisabled();
  expect(confirmCurrentIntake).not.toHaveBeenCalled();
});

it('keeps the reason and incoming/existing identity comparison next to the resolution choices',async()=>{
  mount(<ConflictResolutionModal value={{...conflict,candidateIdB:'person-b'}} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  expect(await screen.findByText('联系方式匹配已有档案，但姓名不一致')).toBeVisible();
  expect(await screen.findByText('incoming@example.com')).toBeVisible();
  expect(screen.getAllByText('existing@example.com')).toHaveLength(2);
  expect(screen.getByRole('columnheader',{name:'本次简历'})).toBeVisible();
  expect(screen.getByRole('columnheader',{name:'已有档案 2'})).toBeVisible();
  expect(screen.getByText(/关联已有候选人：/)).toBeVisible();
  expect(screen.getByText(/创建独立候选人：/)).toBeVisible();
  expect(screen.getByText(/合并候选人：/)).toBeVisible();
  expect(talentApi.intake).toHaveBeenCalledWith('incoming');
  expect(talentApi.candidate).toHaveBeenCalledWith('person-b');
});

it('does not fetch hidden candidate identities or offer a resolution in a direct conflict dialog',async()=>{
  mount(<ConflictResolutionModal value={hiddenConflict} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  expect(await screen.findByText('联系方式与已有档案一致，疑似同一人多份简历')).toBeVisible();
  expect(screen.queryByLabelText('处理方式')).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'确认处理'})).toBeDisabled();
  expect(talentApi.intake).not.toHaveBeenCalled();
  expect(talentApi.candidate).not.toHaveBeenCalled();
  expect(talentApi.resolveConflict).not.toHaveBeenCalled();
});

it('shows administrator handoff without a candidate lookup in the candidate conflict list',async()=>{
  vi.mocked(talentApi.candidateConflicts).mockResolvedValue({items:[hiddenConflict],hasMore:false,nextCursor:null});
  mount(<CandidateIdentityConflicts candidate={{id:'incoming',fullName:'本次姓名',primaryPhone:null,primaryEmail:null,ownerUserId:'hr',ownerDepartmentId:'dep',status:'ACTIVE',version:1,createdAt:'',updatedAt:''}} onClose={vi.fn()}/>);
  expect(await screen.findByText('联系方式与已有档案一致，疑似同一人多份简历')).toBeVisible();
  expect(screen.queryByRole('button',{name:'核验并处理此项'})).not.toBeInTheDocument();
  expect(talentApi.intake).not.toHaveBeenCalled();
  expect(talentApi.candidate).not.toHaveBeenCalled();
});

it('hides cached identity details and candidate labels after a permission denial',async()=>{
  const {client}=mount(<ConflictResolutionModal value={conflict} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  await screen.findByText('existing@example.com');
  await waitFor(()=>expect(client.isFetching()).toBe(0));
  vi.mocked(talentApi.candidate).mockRejectedValueOnce(new ApiProblemError({title:'读取失败',detail:'访问被拒绝',status:403,code:'ACCESS_DENIED',recoverable:false}));
  await act(()=>client.invalidateQueries({queryKey:['talent','candidate','person']}));
  await screen.findByText('无权读取部分身份信息，请联系有权限的负责人核验。');
  expect(screen.queryByText('existing@example.com')).not.toBeInTheDocument();
  expect(screen.queryByText(/已有姓名/)).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'确认处理'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'重试读取身份信息'}));
  await screen.findByText('existing@example.com');
  await waitFor(()=>expect(screen.getByRole('button',{name:'确认处理'})).toBeEnabled());
});

it('refreshes cached identities on reopening even when the application cache is still fresh',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:30_000}}});
  const content=<ConflictResolutionModal value={conflict} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>;
  const first=mount(content,client);
  await screen.findByText('existing@example.com');
  await waitFor(()=>expect(client.isFetching()).toBe(0));
  first.unmount();
  vi.mocked(talentApi.candidate).mockRejectedValueOnce(new ApiProblemError({title:'读取失败',detail:'访问被拒绝',status:403,code:'ACCESS_DENIED',recoverable:false}));
  mount(content,client);
  expect(screen.getByRole('button',{name:'确认处理'})).toBeDisabled();
  await screen.findByText('无权读取部分身份信息，请联系有权限的负责人核验。');
  expect(screen.queryByText('existing@example.com')).not.toBeInTheDocument();
  expect(screen.queryByText(/已有姓名/)).not.toBeInTheDocument();
  expect(talentApi.candidate).toHaveBeenCalledTimes(2);
});

it('submits an independent identity without the previously selected existing candidate',async()=>{
  mount(<ConflictResolutionModal value={conflict} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  await screen.findByText('existing@example.com');
  fireEvent.mouseDown(screen.getByLabelText('处理方式'));
  fireEvent.click(await screen.findByText('创建独立候选人',{selector:'.ant-select-item-option-content'}));
  fireEvent.change(screen.getByLabelText('处理原因'),{target:{value:'已核实为不同人员'}});
  fireEvent.click(screen.getByRole('button',{name:'确认处理'}));
  await waitFor(()=>expect(talentApi.resolveConflict).toHaveBeenCalledWith('conflict',{resolution:'CREATE_SEPARATE',reason:'已核实为不同人员'}));
});

it.each([403,503])('blocks blind conflict decisions after detail read failure %s and permits retry',async status=>{
  vi.mocked(talentApi.candidate).mockRejectedValueOnce(new ApiProblemError({title:'读取失败',detail:'读取失败',status,code:'ACCESS_DENIED',recoverable:true}));
  mount(<ConflictResolutionModal value={conflict} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)}/>);
  const submit=await screen.findByRole('button',{name:'确认处理'});
  expect(submit).toBeDisabled();
  const retry=await screen.findByRole('button',{name:'重试读取身份信息'});
  expect(screen.queryByText('existing@example.com')).not.toBeInTheDocument();
  expect(screen.getByText(status===403?'无权读取部分身份信息，请联系有权限的负责人核验。':'身份信息读取失败，请重试后再处理。')).toBeVisible();
  fireEvent.change(screen.getByLabelText('处理原因'),{target:{value:'已电话核实'}});
  fireEvent.click(submit);
  expect(talentApi.resolveConflict).not.toHaveBeenCalled();
  fireEvent.click(retry);
  await screen.findByText('existing@example.com');
  await waitFor(()=>expect(submit).toBeEnabled());
  expect(screen.getByLabelText('处理原因')).toHaveValue('已电话核实');
});

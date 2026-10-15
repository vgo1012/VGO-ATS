import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { demandApi } from '../demand/api';
import { talentApi, type IdentityConflict, type IntakeBatch, type ResumeIntake } from '../talent/api';
import { shouldPollIntakes } from '../talent/intakePolling';

const session=vi.hoisted(()=>({permissions:['talent.manage'] as string[]}));
vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr-1',displayName:'HR',permissions:session.permissions,superAdmin:false}})}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:()=> <span>张三</span>,UserName:({id}:{id:string})=> <span data-user-id={id}>招聘专员</span>}));
vi.mock('../talent/IntakeModals',()=>({ConfirmIntakeModal:({value}:{value?:ResumeIntake})=>value?<div role="dialog" aria-label="确认收件弹窗" data-status={value.status} data-failure-code={value.failureCode}>确认收件弹窗</div>:null,ConflictResolutionModal:()=>null}));
vi.mock('../demand/JdSelector',()=>({JdSelector:({onChange}:{onChange:(value:unknown)=>void})=><button onClick={()=>onChange({id:'jd-1',title:'Java 工程师',currentVersionNo:2})}>选择测试 JD</button>}));
import { IntakeValidationPage } from './IntakeValidationPage';

const base:ResumeIntake={id:'intake-1',batchId:'batch-1',sourceChannelId:'source-1',sourceRecruiterUserId:'hr-1',defaultOwnerUserId:'hr-1',ownerUserId:'hr-1',ownerDepartmentId:'dep-1',handledByUserId:null,originalFileName:'candidate.pdf',mediaType:'application/pdf',sizeBytes:1024,contentSha256:'sha',scanStatus:'CLEAN',status:'PENDING_IMPORT',importStatus:'PENDING',failureCode:null,candidateId:null,resumeVersionId:null,version:1,createdAt:'2026-09-09T01:00:00Z'};
const batch: IntakeBatch={id:'batch-1',jdVersionId:'jd-v1',sourceChannelId:'source-1',sourceRecruiterUserId:'hr-1',defaultOwnerUserId:'hr-1',ownerDepartmentId:'dep-1',status:'COMPLETED',totalCount:1,succeededCount:1,failedCount:0,completedCount:1,processingCount:0,parsingCount:0,parsedCount:1,attentionCount:0,ignoredCount:0,createdAt:base.createdAt};
const resolvedConflict:IdentityConflict={id:'conflict-resolved',intakeId:base.id,candidateIdA:'candidate-1',candidateIdB:null,conflictType:'NAME_MATCH_CONTACT_MISMATCH',status:'RESOLVED',resolution:'USE_EXISTING',chosenCandidateId:'candidate-1',reason:'已核实身份',createdAt:base.createdAt};
const conflictReview:ResumeIntake={...base,jdVersionId:'jd-v1',status:'IDENTITY_REVIEW_REQUIRED',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW',parsedFullName:'测试候选人',parsedPhone:'13800000000'};

function renderItems(items:ResumeIntake[],initialEntry='/?batchId=batch-1'){
  vi.mocked(talentApi.intakes).mockResolvedValue({items,hasMore:false,nextCursor:null});
  return render(<MemoryRouter initialEntries={[initialEntry]}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><IntakeValidationPage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
}
function renderPage(item:ResumeIntake){return renderItems([item]);}

beforeEach(()=>{
  session.permissions=['talent.manage'];
  vi.spyOn(talentApi,'intakes');
  vi.spyOn(talentApi,'batches').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'batch').mockResolvedValue(batch);
  vi.spyOn(talentApi,'countIntakes').mockResolvedValue(1);
  vi.spyOn(talentApi,'candidateBatch').mockResolvedValue([]);
  vi.spyOn(talentApi,'channels').mockResolvedValue({items:[{id:'source-1',code:'MANUAL',name:'手工上传',channelType:'MANUAL',status:'ACTIVE',version:1,updatedAt:'2026-09-09T01:00:00Z'}],hasMore:false,nextCursor:null});
  vi.spyOn(talentApi,'conflicts').mockResolvedValue({items:[],hasMore:false,nextCursor:null});
  vi.spyOn(demandApi,'jdVersions').mockResolvedValue({items:[{id:'jd-v1',jdId:'jd-1',versionNo:1,title:'Java 工程师',rawText:'',status:'SUPERSEDED',sourceType:'MANUAL',createId:'hr-1',createTime:'2026-09-01T00:00:00Z'},{id:'jd-v2',jdId:'jd-1',versionNo:2,title:'Java 工程师',rawText:'',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-1',createTime:'2026-09-02T00:00:00Z'}],hasMore:false,nextCursor:null});
});
afterEach(()=>{cleanup();vi.restoreAllMocks();});

it('进入解析校验默认按待入库查询明细与数量',async()=>{
  renderPage(base);
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenCalledWith('PENDING',undefined,{batchId:'batch-1'},20));
  expect(talentApi.countIntakes).toHaveBeenCalledWith('PENDING',{batchId:'batch-1'},expect.any(AbortSignal));
  expect(screen.getByRole('combobox',{name:'入库状态'}).closest('.ant-form-item')).toHaveTextContent('待入库');
});

it('只有查看权限的收件列表仍提供原始简历入口，不开放删除和入库操作',async()=>{
  session.permissions=['talent.read'];
  renderPage(base);
  expect(await screen.findByRole('button',{name:'查看原始简历'})).toBeEnabled();
  expect(screen.queryByRole('button',{name:'删除'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'关联 JD'})).not.toBeInTheDocument();
});

it('上传任务分组展示批次归属人而不是上传人',async()=>{
  vi.mocked(talentApi.batches).mockResolvedValue({items:[{...batch,defaultOwnerUserId:'owner-2'}],hasMore:false,nextCursor:null});
  renderItems([], '/');
  const task=await within(await screen.findByRole('list',{name:'上传任务'})).findByRole('button');
  expect(task).toHaveTextContent('归属：招聘专员');
  expect(within(task).getByText('招聘专员')).toHaveAttribute('data-user-id','owner-2');
});

it.each(['IMPORTED','PROCESSING','ALL'])('保留显式入库筛选 %s',async(status)=>{
  renderItems([], `/?batchId=batch-1&importStatus=${status}`);
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenCalledWith(status==='ALL'?undefined:status,undefined,{batchId:'batch-1'},20));
  expect(talentApi.countIntakes).toHaveBeenCalledWith(status==='ALL'?undefined:status,{batchId:'batch-1'},expect.any(AbortSignal));
});

it('清空入库状态可查询全部，重置恢复待入库',async()=>{
  renderPage(base);
  fireEvent.click(await screen.findByRole('button',{name:'清空入库状态'}));
  fireEvent.click(screen.getByRole('button',{name:/查\s*询/}));
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith(undefined,undefined,{batchId:'batch-1'},20));
  expect(talentApi.countIntakes).toHaveBeenLastCalledWith(undefined,{batchId:'batch-1'},expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('button',{name:/重\s*置/}));
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING',undefined,{batchId:'batch-1'},20));
});

describe('IntakeValidationPage import processing',()=>{
  it('为收件筛选提供可读的清空操作',async()=>{
    renderItems([], '/?batchId=batch-1&importStatus=PENDING');
    const keyword=screen.getByRole('textbox',{name:'关键词'});
    fireEvent.change(keyword,{target:{value:'临时查询'}});
    fireEvent.click(screen.getByRole('button',{name:'清空关键词'}));
    expect(keyword).toHaveValue('');
    const name=screen.getByRole('textbox',{name:'姓名'});
    fireEvent.change(name,{target:{value:'临时姓名'}});
    fireEvent.click(screen.getByRole('button',{name:'清空姓名'}));
    expect(name).toHaveValue('');
    expect(await screen.findByRole('button',{name:'清空入库状态'})).toBeInTheDocument();
  });

  it('explains missing contact details before opening identity review',async()=>{
    const review={...base,jdVersionId:'jd-v1',status:'IDENTITY_REVIEW_REQUIRED',processingStatus:'SUCCEEDED',failureCode:'IDENTITY_REVIEW_REQUIRED',parsedFullName:'测试候选人',parsedEmail:null,parsedPhone:null} as ResumeIntake;
    const reparse=vi.spyOn(talentApi,'reparse').mockResolvedValue({...review,status:'PARSING',importStatus:'PROCESSING',version:2});
    renderPage(review);
    expect(await screen.findByText(/未识别到.*手机号.*邮箱/)).toBeVisible();
    await waitFor(()=>expect(screen.getByRole('button',{name:'核对异常身份'})).toBeEnabled());
    fireEvent.click(screen.getByRole('button',{name:'重新解析'}));
    await waitFor(()=>expect(reparse).toHaveBeenCalledExactlyOnceWith(review));
  });

  it('shows a prepared intake as confirmable without labeling its identity abnormal',async()=>{
    renderPage({...base,jdVersionId:'jd-v1',status:'READY_FOR_CONFIRMATION',processingStatus:'SUCCEEDED',parsedFullName:'测试候选人',parsedPhone:'13800000000'});
    expect(await screen.findByText('待确认入库',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.getByRole('button',{name:'确认入库'})).toBeVisible();
    expect(screen.queryByRole('button',{name:'核对异常身份'})).not.toBeInTheDocument();
  });

  it('keeps a prepared intake with an unresolved conflict in identity review',async()=>{
    renderPage({...base,jdVersionId:'jd-v1',status:'READY_FOR_CONFIRMATION',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'});
    expect(await screen.findByText('待核对身份',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.getByRole('button',{name:'刷新核对状态'})).toBeVisible();
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('shows the specific identity conflict instead of a generic pending message',async()=>{
    vi.mocked(talentApi.conflicts).mockResolvedValue({items:[{id:'conflict-1',intakeId:base.id,candidateIdA:'candidate-1',candidateIdB:null,conflictType:'NAME_MATCH_CONTACT_MISMATCH',status:'OPEN',resolution:null,chosenCandidateId:null,reason:null,createdAt:base.createdAt}],hasMore:false,nextCursor:null});
    renderPage({...base,jdVersionId:'jd-v1',status:'IDENTITY_REVIEW_REQUIRED',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW',parsedFullName:'测试候选人',parsedPhone:'13800000000'});
    expect(await screen.findByText(/姓名相同.*联系方式.*未匹配/)).toBeVisible();
    expect(screen.getByRole('button',{name:'处理身份冲突'})).toBeEnabled();
    expect(screen.queryByRole('button',{name:'重新解析'})).not.toBeInTheDocument();
  });

  it('does not route an unresolved conflict into ordinary identity confirmation when details are absent',async()=>{
    renderPage({...base,jdVersionId:'jd-v1',status:'IDENTITY_REVIEW_REQUIRED',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW',parsedFullName:'测试候选人',parsedPhone:'13800000000'});
    await screen.findByText('candidate.pdf');
    await waitFor(()=>expect(talentApi.conflicts).toHaveBeenCalledWith([base.id],'RESOLVED'));
    expect(screen.queryByRole('button',{name:'核对异常身份'})).not.toBeInTheDocument();
    expect(screen.getByRole('button',{name:'刷新核对状态'})).toBeVisible();
  });

  it('restores confirmation for a historical intake with a complete resolved decision',async()=>{
    vi.mocked(talentApi.conflicts).mockImplementation((_ids,status)=>Promise.resolve({items:status==='RESOLVED'?[resolvedConflict]:[],hasMore:false,nextCursor:null}));
    renderPage(conflictReview);
    await waitFor(()=>expect(talentApi.conflicts).toHaveBeenCalledWith([base.id],'RESOLVED'));
    await waitFor(()=>expect(screen.getByRole('button',{name:'确认入库'})).toBeEnabled(),{timeout:10000});
    const confirm=screen.getByRole('button',{name:'确认入库'});
    expect(talentApi.conflicts).toHaveBeenCalledWith([base.id],'RESOLVED');
    fireEvent.click(confirm);
    expect(screen.getByRole('dialog',{name:'确认收件弹窗'})).toHaveAttribute('data-status','READY_FOR_CONFIRMATION');
    expect(screen.getByRole('dialog',{name:'确认收件弹窗'})).not.toHaveAttribute('data-failure-code','IDENTITY_CONFLICT_REQUIRES_REVIEW');
  });

  it.each(['error','partial'] as const)('blocks historical confirmation when resolved decisions are %s',async(state)=>{
    vi.mocked(talentApi.conflicts).mockImplementation((_ids,status)=>{
      if(status!=='RESOLVED')return Promise.resolve({items:[],hasMore:false,nextCursor:null});
      if(state==='error')return Promise.reject(new Error('无权读取已解决决定'));
      return Promise.resolve({items:[resolvedConflict],hasMore:true,nextCursor:'more'});
    });
    renderPage(conflictReview);
    await screen.findByText(state==='error'?'已解决身份决定暂不可用':'已解决身份决定结果未完整加载');
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog',{name:'确认收件弹窗'})).not.toBeInTheDocument();
  });

  it.each(['error','partial'] as const)('blocks historical confirmation when open conflicts are %s',async(state)=>{
    vi.mocked(talentApi.conflicts).mockImplementation((_ids,status)=>{
      if(status==='RESOLVED')return Promise.resolve({items:[resolvedConflict],hasMore:false,nextCursor:null});
      if(state==='error')return Promise.reject(new Error('无权读取未解决冲突'));
      return Promise.resolve({items:[],hasMore:true,nextCursor:'more'});
    });
    renderPage(conflictReview);
    await screen.findByText(state==='error'?'身份冲突状态暂不可用':'身份冲突结果未完整加载');
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('does not confirm from a resolved row without an explicit valid decision',async()=>{
    vi.mocked(talentApi.conflicts).mockImplementation((_ids,status)=>Promise.resolve({items:status==='RESOLVED'?[{...resolvedConflict,resolution:'USE_EXISTING',chosenCandidateId:null}]:[],hasMore:false,nextCursor:null}));
    renderPage(conflictReview);
    await waitFor(()=>expect(talentApi.conflicts).toHaveBeenCalledWith([base.id],'RESOLVED'));
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('lets an open conflict take precedence over a resolved decision',async()=>{
    vi.mocked(talentApi.conflicts).mockImplementation((_ids,status)=>Promise.resolve({items:status==='RESOLVED'?[resolvedConflict]:[{...resolvedConflict,id:'conflict-open',status:'OPEN',resolution:null,chosenCandidateId:null,reason:null}],hasMore:false,nextCursor:null}));
    renderPage(conflictReview);
    await waitFor(()=>expect(talentApi.conflicts).toHaveBeenCalledWith([base.id],'RESOLVED'));
    await waitFor(()=>expect(screen.getByRole('button',{name:'处理身份冲突'})).toBeVisible(),{timeout:10000});
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('does not use a resolved decision to recover a completed intake',async()=>{
    renderPage({...conflictReview,status:'COMPLETED',importStatus:'IMPORTED'});
    await screen.findByText('candidate.pdf');
    expect(talentApi.conflicts).not.toHaveBeenCalledWith([base.id],'RESOLVED');
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('removes identity review explanations after import is complete',async()=>{
    renderPage({...base,jdVersionId:'jd-v1',status:'COMPLETED',importStatus:'IMPORTED',scoreStatus:'SUCCEEDED',failureCode:null,parsedFullName:'测试候选人',parsedPhone:null,parsedEmail:null});
    expect(await screen.findByText('已入库',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.queryByText(/未识别到|待核对身份/)).not.toBeInTheDocument();
  });

  it('polls visible pages with unfinished imports or automatic scores',()=>{
    expect(shouldPollIntakes([{...base,status:'PARSING',importStatus:'PROCESSING'}],'visible')).toBe(3000);
    expect(shouldPollIntakes([{...base,status:'CONFIRMED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'QUEUED'}],'visible')).toBe(3000);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'WAITING_CONFIG'}],'visible')).toBe(3000);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'NOT_APPLICABLE'}],'visible')).toBe(3000);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'PROCESSING',jdVersionId:'jd-v1',scoreStatus:'FAILED_FINAL'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'PROCESSING',scoreStatus:'NOT_APPLICABLE'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'IMPORTED',scoreStatus:'WAITING_JD'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'FAILED_RETRYABLE',importStatus:'PENDING'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'IMPORTED'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'SUCCEEDED'}],'visible')).toBe(false);
    expect(shouldPollIntakes([{...base,status:'PARSING',importStatus:'PROCESSING'}],'hidden')).toBe(false);
  });
  it('explains automatic scoring for a linked JD and allows a pending import',async()=>{
    const importIntake=vi.spyOn(talentApi,'importIntake').mockResolvedValue({...base,status:'PARSING',importStatus:'PROCESSING'});
    vi.spyOn(talentApi,'intake').mockResolvedValue({...base,jdVersionId:'jd-v1'});
    renderPage({...base,jdVersionId:'jd-v1',jdTitle:'Java 后端工程师'});
    expect(await screen.findByText('Java 后端工程师')).toBeVisible();
    expect(screen.queryByText('点击“入库”后，AI 自动解析简历并加入待筛选库。')).not.toBeInTheDocument();
    expect(screen.getByText('等待启动解析，可重试导入')).toBeVisible();
    expect(importIntake).not.toHaveBeenCalled();
    await vi.waitFor(()=>expect(screen.getByRole('button',{name:'重试导入'})).toBeEnabled());
    fireEvent.click(screen.getByRole('button',{name:'重试导入'}));
    await vi.waitFor(()=>expect(importIntake).toHaveBeenCalledWith(expect.objectContaining({version:1})));
    expect(screen.queryByLabelText('候选人姓名')).not.toBeInTheDocument();
  },30_000);

  it('explains an unlinked JD and exposes the recovery action',async()=>{
    renderPage({...base,status:'IDENTITY_REVIEW_REQUIRED',jdVersionId:null});
    expect(await screen.findByText('未关联 JD')).toBeVisible();
    expect(screen.getByText('历史收件缺少 JD，请核对后继续入库')).toBeVisible();
    expect(screen.getByText('待关联 JD',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.getAllByLabelText('关联 JD')[0]).toBeVisible();
  },30_000);

  it('removes manual import selection and confirmation',async()=>{
    renderPage({...base,jdVersionId:'jd-v1',status:'COMPLETED',importStatus:'IMPORTED'});
    await screen.findByText('candidate.pdf');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'批量入库'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:/^入\s*库$/})).not.toBeInTheDocument();
  });

  it.each(['loading','error','partial'] as const)('blocks pending recovery while conflict information is %s',async(state)=>{
    if(state==='loading')vi.mocked(talentApi.conflicts).mockReturnValue(new Promise(()=>{}));
    if(state==='error')vi.mocked(talentApi.conflicts).mockRejectedValue(new Error('冲突读取失败'));
    if(state==='partial')vi.mocked(talentApi.conflicts).mockResolvedValue({items:[],hasMore:true,nextCursor:'more'});
    renderPage({...base,jdVersionId:'jd-v1'});
    await screen.findByText('candidate.pdf');
    if(state==='error')await screen.findByText('身份冲突状态暂不可用');
    if(state==='partial')await screen.findByText('身份冲突结果未完整加载');
    expect(screen.getByRole('button',{name:'重试导入'})).toBeDisabled();
  },30_000);

  it('links a completed historical intake and explains that scoring will continue',async()=>{
    const linkBatchJd=vi.spyOn(talentApi,'linkBatchJd').mockResolvedValue({...batch,jdVersionId:'jd-v2'});
    renderPage({...base,status:'COMPLETED',importStatus:'PROCESSING',jdVersionId:null,candidateId:'candidate-1',resumeVersionId:'resume-1'});
    expect(await screen.findByText('简历解析已完成，关联后将自动评分')).toBeVisible();
    fireEvent.click(screen.getAllByLabelText('关联 JD')[0]!);
    fireEvent.click(await screen.findByRole('button',{name:'选择测试 JD'}));
    await vi.waitFor(()=>expect(screen.getByRole('button',{name:'确认关联'})).toBeEnabled());
    fireEvent.click(screen.getByRole('button',{name:'确认关联'}));
    await vi.waitFor(()=>expect(linkBatchJd).toHaveBeenCalledExactlyOnceWith('batch-1','jd-v2'));
  },30_000);

  it('requires a fresh JD selection for the next batch after a successful association',async()=>{
    const linkBatchJd=vi.spyOn(talentApi,'linkBatchJd').mockResolvedValue({} as Awaited<ReturnType<typeof talentApi.linkBatchJd>>);
    renderItems([{...base,jdVersionId:null},{...base,id:'intake-2',batchId:'batch-2',originalFileName:'second.pdf',jdVersionId:null}]);
    const first=await screen.findByText('candidate.pdf');
    fireEvent.click(within(first.closest('tr')!).getByLabelText('关联 JD'));
    fireEvent.click(await screen.findByRole('button',{name:'选择测试 JD'}));
    await vi.waitFor(()=>expect(screen.getByRole('button',{name:'确认关联'})).toBeEnabled(),{timeout:10000});
    fireEvent.click(screen.getByRole('button',{name:'确认关联'}));
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(linkBatchJd).toHaveBeenCalledExactlyOnceWith('batch-1','jd-v2');
    fireEvent.click(within(screen.getByText('second.pdf').closest('tr')!).getByLabelText('关联 JD'));
    await screen.findByRole('dialog');
    expect(screen.getByRole('button',{name:'确认关联'})).toBeDisabled();
    expect(screen.queryByText('V2 · Java 工程师')).not.toBeInTheDocument();
    expect(linkBatchJd).toHaveBeenCalledTimes(1);
  },30_000);

  it('requires a fresh JD selection after cancelling and reopening the same batch',async()=>{
    const linkBatchJd=vi.spyOn(talentApi,'linkBatchJd');
    renderPage({...base,jdVersionId:null});
    await screen.findByText('candidate.pdf');
    fireEvent.click(screen.getAllByLabelText('关联 JD')[0]!);
    fireEvent.click(await screen.findByRole('button',{name:'选择测试 JD'}));
    await vi.waitFor(()=>expect(screen.getByRole('button',{name:'确认关联'})).toBeEnabled(),{timeout:10000});
    fireEvent.click(screen.getByRole('button',{name:/^取\s*消$/}));
    await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getAllByLabelText('关联 JD')[0]!);
    await screen.findByRole('dialog');
    expect(screen.getByRole('button',{name:'确认关联'})).toBeDisabled();
    expect(linkBatchJd).not.toHaveBeenCalled();
  },30_000);

  it('shows the real import failure with its reason and retry action',async()=>{
    const retry=vi.spyOn(talentApi,'retry').mockResolvedValue({...base,status:'PARSING',importStatus:'PROCESSING'});
    renderPage({...base,status:'FAILED_RETRYABLE',failureCode:'AI_PROVIDER_TIMEOUT',jdVersionId:'jd-v1'});
    expect(await screen.findByText('导入失败',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.queryByText('待入库',{selector:'.ant-tag'})).not.toBeInTheDocument();
    expect(screen.getByText('失败原因：暂无法解释该错误，请联系管理员核查')).toBeVisible();
    expect(screen.getByText('可尝试重试导入；若提示不可重试，请联系管理员核查。')).toBeVisible();
    expect(screen.getByText('AI_PROVIDER_TIMEOUT')).toBeInTheDocument();
    expect(screen.getByText('导入失败，详见处理状态')).toBeVisible();
    await vi.waitFor(()=>expect(screen.getByRole('button',{name:'重试导入'})).toBeEnabled());
    fireEvent.click(screen.getByRole('button',{name:'重试导入'}));
    await vi.waitFor(()=>expect(retry).toHaveBeenCalledWith(expect.objectContaining({status:'FAILED_RETRYABLE',version:1})));
    expect(screen.queryByText('安全检查通过')).not.toBeInTheDocument();
  },30_000);

  it('explains a data scope failure before asking an administrator to review access',async()=>{
    renderPage({...base,status:'FAILED_RETRYABLE',processingStatus:'FAILED_FINAL',failureCode:'DATA_SCOPE_FORBIDDEN',jdVersionId:'jd-v1'});
    expect(await screen.findByText('失败原因：当前处理账号无权访问相关业务数据')).toBeVisible();
    expect(screen.getByText('请管理员核对关联 JD、候选人归属及处理权限；权限调整后再重试。')).toBeVisible();
    expect(screen.queryByText('可尝试重试导入；若提示不可重试，请联系管理员核查。')).not.toBeInTheDocument();
    expect(screen.getByText('DATA_SCOPE_FORBIDDEN')).toBeInTheDocument();
  });

  it('routes a hidden existing candidate to an administrator without offering conflict resolution',async()=>{
    vi.mocked(talentApi.conflicts).mockResolvedValue({items:[{id:'conflict-hidden',intakeId:base.id,candidateIdA:null,candidateIdB:null,conflictType:'CANDIDATE_ACCESS_REQUIRED',status:'OPEN',resolution:null,chosenCandidateId:null,reason:null,createdAt:base.createdAt}],hasMore:false,nextCursor:null});
    renderPage({...base,jdVersionId:'jd-v1',status:'IDENTITY_REVIEW_REQUIRED',failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'});
    expect(await screen.findByText('联系方式与已有档案一致，疑似同一人多份简历')).toBeVisible();
    expect(screen.getByText(/联系已有档案负责人核实候选人归属和后续沟通/)).toBeVisible();
    expect(screen.getByText('联系负责人核验')).toBeVisible();
    expect(screen.queryByRole('button',{name:'处理身份冲突'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'核对异常身份'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'确认入库'})).not.toBeInTheDocument();
  });

  it('shows the automatic retry wait without claiming the system omitted a failure reason',async()=>{
    renderPage({...base,status:'PARSING',importStatus:'PROCESSING',processingStatus:'FAILED_RETRYABLE',failureCode:null,jdVersionId:'jd-v1'});
    expect(await screen.findByText('等待自动重试',{selector:'.ant-tag'})).toBeVisible();
    expect(screen.getByText('等待自动重试，完成后自动评分')).toBeVisible();
    expect(screen.getByText('上次解析尝试未完成，系统将自动重试。')).toBeVisible();
    expect(screen.queryByText('失败原因：系统未提供具体原因')).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'重试导入'})).not.toBeInTheDocument();
  });

  it.each([
    ['PENDING_IMPORT','PENDING','待入库','等待启动解析，可重试导入'],
    ['PARSING','PROCESSING','解析中','正在解析，完成后自动评分'],
    ['IDENTITY_REVIEW_REQUIRED','PENDING','待核对身份','等待身份核对，暂未评分'],
    ['COMPLETED','IMPORTED','已入库','评分失败，可重试'],
    ['CONFIRMED','IMPORTED','已入库','评分失败，可重试'],
  ] as const)('keeps source, import and score states visible for %s',async(status,importStatus,label,scoreLabel)=>{
    renderPage({...base,status,importStatus,duplicateContent:true,jdVersionId:'jd-v1',jdTitle:'产品经理',scoreStatus:importStatus==='IMPORTED'?'FAILED_RETRYABLE':'NOT_APPLICABLE',candidateId:importStatus==='IMPORTED'?'candidate-1':null});
    expect(await screen.findByText('同内容重复收件')).toBeVisible();
    expect(screen.queryByText('请核对历史来源，避免重复联系或推荐')).not.toBeInTheDocument();
    expect(screen.getByText(label,{selector:'.ant-tag'})).toBeVisible();
    expect(screen.getByText(scoreLabel)).toBeVisible();
    if(status==='PENDING_IMPORT')await vi.waitFor(()=>expect(screen.getByRole('button',{name:'重试导入'})).toBeEnabled());
    if(status==='IDENTITY_REVIEW_REQUIRED')expect(screen.getByRole('button',{name:'核对异常身份'})).toBeVisible();
    expect(screen.queryByRole('button',{name:/^入\s*库$/})).not.toBeInTheDocument();
    if(importStatus==='PROCESSING')expect(screen.getByRole('button',{name:/^删\s*除$/})).toBeDisabled();
    else expect(screen.getByRole('button',{name:/^删\s*除$/})).toBeEnabled();
    if(importStatus==='IMPORTED')expect(screen.getByRole('button',{name:'重试评分'})).toBeVisible();
    else expect(screen.queryByRole('button',{name:'重试评分'})).not.toBeInTheDocument();
    expect(screen.queryByText(/已合并|已归并|已按文件内容复用|无需重复解析/)).not.toBeInTheDocument();
  },30_000);

  it('retries a failed automatic score without reimporting the resume',async()=>{
    const item:ResumeIntake={...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',jdTitle:'产品经理',scoreStatus:'FAILED_RETRYABLE',scoreRunId:'run-1',failureCode:'AI_PROVIDER_TIMEOUT',candidateId:'candidate-1',resumeVersionId:'resume-1'};
    const retryScore=vi.spyOn(talentApi,'retryScore').mockResolvedValue({...item,scoreStatus:'QUEUED',version:2});
    renderPage(item);
    expect(await screen.findByText('失败原因：暂无法解释该错误，请联系管理员核查')).toBeVisible();
    const row=screen.getByText('candidate.pdf').closest('tr')!;
    const cells=row.querySelectorAll('td');
    expect(within(cells[1]!).queryByText(/失败原因/)).not.toBeInTheDocument();
    expect(within(cells[2]!).getByText('失败原因：暂无法解释该错误，请联系管理员核查')).toBeVisible();
    expect(screen.getByText('可点击“重试评分”；若仍失败，请联系管理员排查。')).toBeVisible();
    fireEvent.click(screen.getByRole('button',{name:'重试评分'}));
    await vi.waitFor(()=>expect(retryScore).toHaveBeenCalledExactlyOnceWith(item));
  },30_000);

  it('keeps score recalculation in More and confirms its temporary visibility change',async()=>{
    session.permissions=['talent.manage','matching.run'];
    const item:ResumeIntake={...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'SUCCEEDED',scoreRunId:'run-1',candidateId:'candidate-1',resumeVersionId:'resume-1'};
    const recalculate=vi.spyOn(talentApi,'recalculateScore').mockResolvedValue({...item,scoreStatus:'QUEUED',scoreRunId:'run-2',version:2});
    renderPage(item);

    await screen.findByText('candidate.pdf');
    expect(screen.getByRole('button',{name:'更多操作'})).toBeVisible();
    expect(screen.queryByRole('button',{name:'更新岗位评分'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'更多操作'}));
    fireEvent.click(await screen.findByRole('menuitem',{name:'更新岗位评分'}));
    const dialog=screen.getByRole('dialog',{name:'更新岗位评分'});
    expect(within(dialog).getByText(/更新期间.*默认评分暂不可用/)).toBeInTheDocument();
    expect(recalculate).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button',{name:'更新评分'}));
    await waitFor(()=>expect(recalculate).toHaveBeenCalledExactlyOnceWith(item));
  },30_000);

  it('does not offer score recalculation without matching permission',async()=>{
    renderPage({...base,status:'COMPLETED',importStatus:'IMPORTED',jdVersionId:'jd-v1',scoreStatus:'SUCCEEDED',scoreRunId:'run-1',candidateId:'candidate-1',resumeVersionId:'resume-1'});
    await screen.findByText('candidate.pdf');
    expect(screen.queryByRole('button',{name:'更多操作'})).not.toBeInTheDocument();
  });

  it('deletes an imported duplicate receipt only after confirmation and keeps the resume history',async()=>{
    const item:ResumeIntake={...base,status:'COMPLETED',importStatus:'IMPORTED',duplicateContent:true,candidateId:'candidate-1',resumeVersionId:'resume-1'};
    const ignore=vi.spyOn(talentApi,'ignore').mockResolvedValue({...item,status:'IGNORED',version:2});
    renderPage(item);
    await screen.findByText('candidate.pdf');
    fireEvent.click(screen.getByRole('button',{name:/^删\s*除$/}));
    expect(ignore).not.toHaveBeenCalled();
    await waitFor(()=>expect(screen.getByText('仅移出收件列表，已入库的候选人档案、简历版本和来源历史仍保留。')).toBeVisible(),{timeout:10000});
    vi.mocked(talentApi.intakes).mockResolvedValue({items:[],hasMore:false,nextCursor:null});
    fireEvent.click(screen.getByRole('button',{name:'确认删除'}));
    await vi.waitFor(()=>expect(ignore).toHaveBeenCalledExactlyOnceWith(item));
    await vi.waitFor(()=>expect(screen.queryByText('candidate.pdf')).not.toBeInTheDocument());
  },30_000);
});


it.each([
  ['MODEL_JSON_INVALID','解析结果格式不正确，未能读取简历信息'],
  ['MALWARE_SCANNER_UNAVAILABLE','文件安全扫描服务暂不可用'],
  [null,'系统未提供具体原因'],
] as const)('shows import failure diagnostics for code %s while keeping retry',async(failureCode,reason)=>{
  renderPage({...base,status:'FAILED_RETRYABLE',processingStatus:'FAILED_FINAL',failureCode,jdVersionId:'jd-v1'});
  expect(await screen.findByText('candidate.pdf')).toBeVisible();
  expect(screen.getByText('导入失败',{selector:'.ant-tag'})).toBeVisible();
  expect(screen.getByText(`失败原因：${reason}`)).toBeVisible();
  expect(screen.getByText('可尝试重试导入；若提示不可重试，请联系管理员核查。')).toBeVisible();
  if(failureCode)expect(screen.getByText(failureCode)).toBeInTheDocument();
  else expect(screen.queryByText('查看错误代码')).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'重试导入'})).toBeVisible();
});

it.each([
  ['QUARANTINED','已隔离','MALWARE_OR_SIGNATURE_REJECTED','安全检查发现风险，文件已隔离'],
  ['REJECTED','已拒绝',null,'系统未提供具体原因'],
] as const)('shows %s rather than pending for a blocked receipt without JD',async(status,label,failureCode,reason)=>{
  renderPage({...base,status,jdVersionId:null,failureCode});
  expect(await screen.findByText(label,{selector:'.ant-tag'})).toBeVisible();
  expect(screen.queryByText('待关联 JD',{selector:'.ant-tag'})).not.toBeInTheDocument();
  expect(screen.getByText(`失败原因：${reason}`)).toBeVisible();
  if(failureCode)expect(screen.getByText(failureCode)).toBeInTheDocument();
});

it.each([
  ['QUARANTINED','文件已隔离，无法评分'],
  ['REJECTED','收件已拒绝，无法评分'],
] as const)('explains why a linked %s receipt cannot be scored',async(status,scoreLabel)=>{
  renderPage({...base,status,jdVersionId:'jd-v1'});
  expect(await screen.findByText(scoreLabel)).toBeVisible();
});
it('clears stale failure details while processing succeeds',async()=>{
  renderPage({...base,status:'COMPLETED',importStatus:'IMPORTED',processingStatus:'SUCCEEDED',failureCode:'MODEL_JSON_INVALID',candidateId:'candidate-1'});
  await screen.findByText('candidate.pdf');
  expect(screen.queryByText(/失败原因/)).not.toBeInTheDocument();
});

it('shows receipt information without candidate lookups or upload entry',async()=>{
  renderPage({...base,candidateId:'candidate-1'});
  const filename=await screen.findByText('candidate.pdf');
  expect(screen.getByRole('columnheader',{name:'收件文件'})).toBeVisible();
  expect(filename).toHaveAttribute('tabindex','0');
  expect(screen.getByText('2026/09/09 09:00')).toBeVisible();
  expect(screen.queryByText(/KB|等待识别身份|候选人：/)).not.toBeInTheDocument();
  expect(screen.queryByText('异常详情')).not.toBeInTheDocument();
  expect(screen.queryByText('同内容重复收件')).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'批量上传简历'})).not.toBeInTheDocument();
  expect(talentApi.candidateBatch).not.toHaveBeenCalled();
});


it('shows the server total rather than the current page length and refreshes it after filtering', async () => {
  vi.mocked(talentApi.countIntakes).mockResolvedValue(121);
  renderPage(base);
  expect(await screen.findByText('条/共121条记录')).toBeVisible();
  expect(screen.getByText('共7页')).toBeVisible();
  vi.mocked(talentApi.countIntakes).mockResolvedValue(0);
  fireEvent.change(screen.getByPlaceholderText('已识别候选人姓名'), { target: { value: '没有匹配' } });
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  expect(await screen.findByText('条/共0条记录')).toBeVisible();
  expect(talentApi.countIntakes).toHaveBeenLastCalledWith('PENDING', { name: '没有匹配', batchId: 'batch-1' }, expect.any(AbortSignal));
});

it('lists same-content receipts through a combinable server-side issue filter', async () => {
  renderPage({...base, duplicateContent: true});
  await screen.findByText('candidate.pdf');
  fireEvent.mouseDown(screen.getByLabelText('问题状态'));
  fireEvent.click(await screen.findByText('同内容重复收件', {selector:'.ant-select-item-option-content'}));
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  await waitFor(() => expect(talentApi.intakes).toHaveBeenLastCalledWith(
    'PENDING', undefined, {duplicateContent:true,batchId:'batch-1'}, 20,
  ));
  expect(talentApi.countIntakes).toHaveBeenLastCalledWith(
    'PENDING', {duplicateContent:true,batchId:'batch-1'}, expect.any(AbortSignal),
  );
});

it('lists all problematic receipts and identity reviews through server-side issue filters',async()=>{
  renderPage({...base,status:'IDENTITY_REVIEW_REQUIRED'});
  await screen.findByText('candidate.pdf');
  for(const [label,problemStatus] of [['全部问题','PROBLEM'],['待核对身份','IDENTITY_REVIEW']] as const){
    fireEvent.mouseDown(screen.getByLabelText('问题状态'));
    fireEvent.click(await screen.findByText(label,{selector:'.ant-select-item-option-content'}));
    fireEvent.click(screen.getByRole('button',{name:/查\s*询/}));
    await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith(
      'PENDING',undefined,{problemStatus,batchId:'batch-1'},20,
    ));
  }
});

it('keeps receipts visible when the total fails and allows retrying the count', async () => {
  vi.mocked(talentApi.countIntakes).mockRejectedValueOnce(new Error('统计暂不可用')).mockResolvedValue(42);
  renderPage(base);
  expect(await screen.findByText('candidate.pdf')).toBeVisible();
  fireEvent.click(await screen.findByRole('button', { name: '重试统计' }));
  expect(await screen.findByText('条/共42条记录')).toBeVisible();
});

it('修改入库每页条数会重置游标并保留当前筛选', async () => {
  vi.mocked(talentApi.intakes).mockImplementation((_status, cursor) => Promise.resolve({ items: [base], hasMore: !cursor, nextCursor: cursor ? null : 'next' }));
  render(<MemoryRouter initialEntries={['/?batchId=batch-1&name=张三']}><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IntakeValidationPage/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
  await screen.findByText('candidate.pdf');
  await waitFor(() => expect(screen.getByRole('button', { name: '下一页' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '上一页' })).toBeEnabled());
  const size = screen.getByRole('spinbutton', { name: '每页条数' });
  fireEvent.change(size, { target: { value: '30' } }); fireEvent.blur(size);
  await waitFor(() => expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING', undefined, { name: '张三', batchId: 'batch-1' }, 30));
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
},30_000);

it('按上传任务查看收件，保留服务端筛选与分页边界',async()=>{
  const second={...batch,id:'batch-2',totalCount:2,completedCount:0,processingCount:1,parsingCount:1,parsedCount:0,attentionCount:1};
  vi.mocked(talentApi.batches).mockResolvedValue({items:[batch,second],hasMore:false,nextCursor:null});
  renderItems([], '/?name=张三');
  vi.mocked(talentApi.intakes).mockImplementation((_status,_cursor,filters)=>Promise.resolve({items:[base,{...base,id:'intake-2',batchId:'batch-2',originalFileName:'second.pdf'}].filter(item=>!filters?.batchId||item.batchId===filters.batchId),hasMore:false,nextCursor:null}));
  const group=await screen.findByRole('list',{name:'上传任务'});
  const tasks=await within(group).findAllByRole('button');
  expect(tasks).toHaveLength(2);
  expect(screen.queryByRole('columnheader',{name:'收件文件'})).not.toBeInTheDocument();
  expect(screen.queryByRole('textbox',{name:'关键词'})).not.toBeInTheDocument();
  expect(talentApi.intakes).not.toHaveBeenCalled();
  expect(talentApi.countIntakes).not.toHaveBeenCalled();
  fireEvent.click(tasks[1]!);
  expect(screen.queryByRole('list',{name:'上传任务'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'返回任务分组'})).toBeVisible();
  expect(screen.getByRole('textbox',{name:'关键词'})).toBeVisible();
  expect(screen.getByRole('textbox',{name:'姓名'})).toHaveValue('');
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING',undefined,{batchId:'batch-2'},20));
  expect(await screen.findByText('second.pdf')).toBeVisible();
  fireEvent.change(screen.getByRole('textbox',{name:'姓名'}),{target:{value:'张三'}});
  fireEvent.click(screen.getByRole('button',{name:/查\s*询/}));
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING',undefined,{name:'张三',batchId:'batch-2'},20));
  expect(screen.queryByText('candidate.pdf')).not.toBeInTheDocument();
  expect(talentApi.countIntakes).toHaveBeenLastCalledWith('PENDING',{name:'张三',batchId:'batch-2'},expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('button',{name:/重\s*置/}));
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING',undefined,{batchId:'batch-2'},20));
  expect(screen.queryByRole('list',{name:'上传任务'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'返回任务分组'}));
  expect(await screen.findByRole('list',{name:'上传任务'})).toBeVisible();
  expect(screen.queryByRole('columnheader',{name:'收件文件'})).not.toBeInTheDocument();
  expect(screen.queryByRole('textbox',{name:'关键词'})).not.toBeInTheDocument();
  fireEvent.click(within(screen.getByRole('list',{name:'上传任务'})).getAllByRole('button')[0]!);
  await waitFor(()=>expect(talentApi.intakes).toHaveBeenLastCalledWith('PENDING',undefined,{batchId:'batch-1'},20));
  expect(screen.getByRole('textbox',{name:'姓名'})).toHaveValue('');
},30_000);

it('在任务卡和详情用互斥的当前状态计数说明 50 份收件',async()=>{
  const grouped={...batch,totalCount:50,succeededCount:50,completedCount:37,processingCount:1,parsingCount:0,parsedCount:43,attentionCount:11,ignoredCount:1};
  vi.mocked(talentApi.batches).mockResolvedValue({items:[grouped],hasMore:false,nextCursor:null});
  vi.mocked(talentApi.batch).mockResolvedValue(grouped);
  renderItems([], '/');
  const task=await within(await screen.findByRole('list',{name:'上传任务'})).findByRole('button');
  expect(within(task).getByText('首次上传成功 50 · 首次上传失败 0')).toBeVisible();
  expect(within(task).getByText('已入库 37 · 处理中 1 · 需处理 11 · 已忽略 1')).toBeVisible();
  expect(within(task).queryByText(/解析成功 43\/50|可能与解析成功重叠/)).not.toBeInTheDocument();
  fireEvent.click(task);
  expect(await screen.findByText(/首次上传成功 50 · 首次上传失败 0 · 已入库 37 · 处理中 1 · 需处理 11 · 已忽略 1/)).toBeVisible();
});

it('恢复后的解析成功不被错误归入首次上传成功数',async()=>{
  vi.mocked(talentApi.batch).mockResolvedValue({...batch,totalCount:1,succeededCount:0,failedCount:1,parsedCount:1});
  renderItems([]);
  expect(await screen.findByText(/首次上传成功 0 · 首次上传失败 1 · 已入库 1 · 处理中 0 · 需处理 0 · 已忽略 0/)).toBeVisible();
  expect(screen.queryByText(/其中解析成功/)).not.toBeInTheDocument();
});

it('通过任务通知直达解析明细时不展示分组列表',async()=>{
  renderPage(base);
  expect(screen.queryByRole('list',{name:'上传任务'})).not.toBeInTheDocument();
  expect(await screen.findByText('candidate.pdf')).toBeVisible();
  expect(screen.getByRole('button',{name:'返回任务分组'})).toBeVisible();
  expect(talentApi.intakes).toHaveBeenCalledWith('PENDING',undefined,{batchId:'batch-1'},20);
  expect(talentApi.batches).not.toHaveBeenCalled();
});

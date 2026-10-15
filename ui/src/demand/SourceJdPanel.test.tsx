import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SourceJdPanel } from './SourceJdPanel';
import { demandApi, type PublicJdSource, type JdFacts, type JdSummary } from './api';
import { ApiProblemError } from '../api/problem';

vi.mock('./api', () => ({ demandApi: { jds: vi.fn(), countJds: vi.fn(), publicJds: vi.fn(), countPublicJds: vi.fn(), publicJd: vi.fn(), jdVersions: vi.fn(), jdSources: vi.fn(), createSourceJd: vi.fn(), updateJd: vi.fn(), deleteJd: vi.fn(), parseJd: vi.fn(), jdFacts: vi.fn() } }));
vi.mock('../display/BusinessReferences',()=>({PeopleSelect:({value,onChange,...props}:{value?:string;onChange?:(value:string)=>void})=><input {...props} value={value??''} onChange={event=>onChange?.(event.target.value)}/>,useUserNames:()=>({people:new Map([['hr-1','示例招聘专员']]),loading:false})}));
const jd: JdSummary = { id:'jd-1', code:'JD-000001', title:'示例运维岗位', workRegionName:'北京', onboardedHeadcount:0, distinction:'北京 · 云平台', clientId:null, clientName:null, status:'ACTIVE', currentVersionNo:1, contentShared:false, appliedFromJdVersionId:null, ownerUserId:'hr-1', ownerDepartmentId:'dept-1', recruitmentType:'INTERNAL', headcount:3, occupiedHeadcount:0, remainingHeadcount:3, recruitmentCycleDays:45, version:'1', createTime:'2026-09-07T10:00:00Z' };
const version = { id:'v1', jdId:jd.id, versionNo:1, title:jd.title, rawText:'Linux 和 GPU 维护', sourceType:'MANUAL' as const, status:'PUBLISHED' as const, createId:'hr-1', createTime:jd.createTime };
const departmentSource: PublicJdSource = { id:jd.id, code:jd.code!, title:jd.title, workRegionName:jd.workRegionName!, currentVersionNo:1, currentVersionId:version.id, rawText:version.rawText, ownerUserId:'source-owner', ownerUserName:'来源所属人', ownerDepartmentId:'outside-department', ownerDepartmentName:'来源部门', createTime:jd.createTime };
const factsSnapshot = (skill: string): JdFacts => ({
  factSnapshotId: skill, jdVersionId: version.id, snapshotVersion: 1, sourceType: 'AI_PARSE', schemaVersion: '1', createdAt: jd.createTime,
  facts: { title: jd.title, summary: null,
    professional: { coreSkills: [{ id: skill, name: skill, required: false, aliases: [] }], roleExperiences: [], minimumYears: null, preferredYears: null, responsibilities: [], industryTags: [], projectTags: [] },
    controlledConditions: { ageMin: null, ageMax: null, sex: null, educations: [], credentials: [], languages: [] },
    fulfillmentConditions: { locations: [], availability: null, salaryMin: null, salaryMax: null, salaryPeriod: null },
  },
});
const department={id:'dept-1',code:'OPS',name:'运维部',parentDepartmentId:null,status:'ACTIVE' as const};
const supportDepartment={id:'dept-2',code:'PLATFORM',name:'平台部',parentDepartmentId:null,status:'ACTIVE' as const};
function setup(client = new QueryClient({ defaultOptions:{ queries:{ retry:false } } }), canAdmin=false, clients: Parameters<typeof SourceJdPanel>[0]['clients']=[], canReassignOwner=canAdmin) { return render(<ConfigProvider><App><QueryClientProvider client={client}><SourceJdPanel canAdmin={canAdmin} canReassignOwner={canReassignOwner} actorUserId="hr-1" actorHomeDepartmentId="dept-1" actorSupportDepartmentIds={[supportDepartment.id]} clients={clients} departments={[department,supportDepartment,{...department,id:'dept-unassigned',name:'无支持关系部门'}]} /></QueryClientProvider></App></ConfigProvider>); }
async function selectRequiredOwnershipAndType(type: '内部招聘' | '外部招聘' = '内部招聘') {
  await selectCity('北京市');
  fireEvent.mouseDown(screen.getByLabelText('JD 归属部门'));
  fireEvent.click(await screen.findByText(department.name,{selector:'.ant-select-item-option-content'}));
  fireEvent.mouseDown(screen.getByLabelText('招聘类型'));
  fireEvent.click(await screen.findByText(type,{selector:'.ant-select-item-option-content'}));
  if (type === '外部招聘') await screen.findByLabelText('客户');
}
async function selectCity(name: string, province = name) {
  fireEvent.mouseDown(screen.getByLabelText('工作地区'));
  fireEvent.change(screen.getByLabelText('工作地区'), { target: { value: name.slice(0, -1) } });
  fireEvent.click(await screen.findByText(`${name} · ${province}`, { selector: '.ant-select-item-option-content' }));
}
describe('JD library', () => {
  it('requires a region and limits new job names to the automatic dictionary boundary', async () => {
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(screen.getByLabelText('岗位名称'),{target:{value:'岗'.repeat(81)}});
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请输入工作地区')).toBeInTheDocument();
    expect(await screen.findByText('岗位名称不能超过 80 字')).toBeInTheDocument();
    expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  });

  it.each(['JD-000001', 'JD-01002003001'])('shows legacy or new code %s in mail examples and allows an independent same-title demand', async code => {
    vi.mocked(demandApi.jds).mockResolvedValue({items:[{...jd,code}],nextCursor:null,hasMore:false});
    setup();
    expect(await screen.findByText(code)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: jd.title }));
    expect(await screen.findByText(`【${code}】${jd.title}—张三`)).toBeInTheDocument();
    expect(screen.getByText(`【${code}】张三.pdf`)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    fireEvent.click(screen.getByRole('button', { name: /新增 JD/ }));
    expect(screen.queryByText(/两位部门码＋三位岗位码＋三位地区码＋至少三位流水号/)).not.toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText('岗位名称'), { target: { value: jd.title } });
    await selectCity('北京市');
    expect(await screen.findByText('公共 JD 列表已有同名同地区 JD，可先从公共 JD 列表应用')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '保存并解析' })).toBeEnabled();
  },30_000);

  it('warns when more same-name sources exist beyond the first hint page', async () => {
    vi.mocked(demandApi.publicJds).mockResolvedValue({items:[],nextCursor:'more',hasMore:true});
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(screen.getByLabelText('岗位名称'),{target:{value:jd.title}});
    expect(await screen.findByText('还有更多同名来源，请到公共 JD 列表核对；仍可独立新建。')).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'保存并解析'})).toBeEnabled();
  });

  afterEach(() => vi.mocked(demandApi.jdFacts).mockReset());
  it.each([false, true])('replaces pre-completion facts and ignores late reads (pending: %s)', async (pending) => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let finishOld!: (value: JdFacts) => void;
    const oldRead = new Promise<JdFacts>((resolve) => { finishOld = resolve; });
    vi.mocked(demandApi.jds).mockResolvedValue({ items: [{ ...jd, currentParseStatus: 'RUNNING' }], nextCursor: null, hasMore: false });
    vi.mocked(demandApi.jdFacts).mockReturnValueOnce(pending ? oldRead : Promise.resolve(factsSnapshot('旧技能'))).mockResolvedValue(factsSnapshot('最终技能'));
    setup(client);
    fireEvent.click(await screen.findByRole('button', { name: jd.title }, { timeout: 10_000 }));
    await waitFor(() => expect(demandApi.jdFacts).toHaveBeenCalledOnce());
    if (!pending) expect(await screen.findByText('旧技能')).toBeInTheDocument();
    act(() => { client.setQueryData(['demand', 'source-library', '', undefined, 20], { items: [{ ...jd, currentParseStatus: 'SUCCEEDED' }], nextCursor: null, hasMore: false }); });
    expect(await screen.findByText('最终技能')).toBeInTheDocument();
    await act(async () => { finishOld(factsSnapshot('旧技能')); await oldRead; });
    expect(screen.queryByText('旧技能')).not.toBeInTheDocument();
    expect(screen.getByText('最终技能')).toBeInTheDocument();
    expect(demandApi.jdFacts).toHaveBeenCalledTimes(2);
    expect(demandApi.parseJd).not.toHaveBeenCalled();
  },15_000);
  it('refreshes missing facts when the list reports parse completion before the facts poll', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const absent = new ApiProblemError({ title: '尚无事实', status: 404, detail: '解析尚未完成', code: 'JD_FACTS_NOT_FOUND', recoverable: true });
    vi.mocked(demandApi.jds).mockResolvedValue({ items: [{ ...jd, currentParseStatus: 'RUNNING' }], nextCursor: null, hasMore: false });
    vi.mocked(demandApi.jdFacts).mockRejectedValue(absent);
    setup(client);
    fireEvent.click(await screen.findByRole('button', { name: jd.title }, { timeout: 10_000 }));
    await waitFor(() => expect(client.getQueryState(['demand', 'jd-facts', version.id])?.status).toBe('error'));
    expect(screen.getByText('系统正在解析')).toBeInTheDocument();
    vi.mocked(demandApi.jdFacts).mockRejectedValue(new ApiProblemError({ title: '读取失败', status: 503, detail: '完成后的事实读取失败，请重试', code: 'UNAVAILABLE', recoverable: true }));
    act(() => { client.setQueryData(['demand', 'source-library', '', undefined, 20], { items: [{ ...jd, currentParseStatus: 'SUCCEEDED' }], nextCursor: null, hasMore: false }); });
    expect(await screen.findByText('完成后的事实读取失败，请重试')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试读取' })).toBeInTheDocument();
    expect(demandApi.jdFacts).toHaveBeenCalledTimes(2);
    expect(demandApi.parseJd).not.toHaveBeenCalled();
  },15_000);
  afterEach(cleanup);
  beforeEach(() => { Object.defineProperty(HTMLElement.prototype,'scrollTo',{configurable:true,value:vi.fn()}); });
  beforeEach(() => { Object.defineProperty(HTMLElement.prototype,'scrollIntoView',{configurable:true,value:vi.fn()}); vi.clearAllMocks(); vi.mocked(demandApi.jds).mockResolvedValue({items:[jd],nextCursor:null,hasMore:false}); vi.mocked(demandApi.publicJds).mockResolvedValue({items:[{...departmentSource,rawText:null}],nextCursor:null,hasMore:false}); vi.mocked(demandApi.countPublicJds).mockResolvedValue(1); vi.mocked(demandApi.publicJd).mockResolvedValue(departmentSource); vi.mocked(demandApi.countJds).mockResolvedValue(1); vi.mocked(demandApi.jdVersions).mockResolvedValue({items:[version],nextCursor:null,hasMore:false}); vi.mocked(demandApi.jdSources).mockResolvedValue({items:[{id:'source',jdVersionId:version.id,sourceType:'MANUAL',sourceLabel:'手动录入',mailSourceId:null,ownerUserId:'hr-1',createTime:jd.createTime}],nextCursor:null,hasMore:false}); vi.mocked(demandApi.jdFacts).mockResolvedValue(factsSnapshot('默认技能')); vi.mocked(demandApi.parseJd).mockResolvedValue({taskId:'task'} as never); });
  it('shows fields that distinguish same-title JDs and lets the owner manage it', async () => { setup(); expect(await screen.findByText('北京市')).toBeInTheDocument(); expect(screen.getByText('内部招聘')).toBeInTheDocument(); expect(screen.getByText('示例招聘专员')).toBeInTheDocument(); expect(screen.getByRole('button',{name:/更新/})).toBeInTheDocument(); });
  it('does not offer JD creation to a read-only viewer', async()=>{ render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SourceJdPanel canAdmin={false} canCreate={false} actorUserId="viewer" actorHomeDepartmentId="dept-1" clients={[]} departments={[]} /></QueryClientProvider></App></ConfigProvider>); expect(await screen.findByText('北京市')).toBeInTheDocument(); expect(screen.queryByRole('button',{name:/新增 JD/})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'公共 JD 列表'}));
    fireEvent.click(await screen.findByRole('button',{name:'查看原文'}));
    expect(await screen.findByText(version.rawText)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'返回公共 JD 列表'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'应用此 JD'})).not.toBeInTheDocument();
  });
  it('shows public sources without a home department but explains why apply is unavailable', async()=>{
    render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SourceJdPanel canAdmin={false} actorUserId="hr-1" clients={[]} departments={[department]} /></QueryClientProvider></App></ConfigProvider>);
    fireEvent.click(screen.getByRole('button',{name:'公共 JD 列表'}));
    expect(await screen.findByText('当前账号未设置所属部门，无法应用公共 JD。请联系管理员设置后重试。')).toBeInTheDocument();
    expect(await screen.findByRole('button',{name:'查看原文'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'应用'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'查看原文'}));
    expect(await screen.findByText(version.rawText)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'返回公共 JD 列表'})).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'应用此 JD'})).not.toBeInTheDocument();
    expect(screen.getByText('当前账号未设置所属部门，无法应用公共 JD。请联系管理员设置后重试。')).toBeInTheDocument();
  });
  it('opens the separate department list and applies the current source version with an independent HC', async()=>{
    vi.mocked(demandApi.createSourceJd).mockResolvedValue(version); setup();
    fireEvent.click(screen.getByRole('button',{name:'公共 JD 列表'}));
    expect(await screen.findByRole('dialog',{name:'公共 JD 列表'})).toBeInTheDocument();
    expect(demandApi.publicJds).toHaveBeenCalledWith('',undefined,20);
    fireEvent.click(await screen.findByRole('button',{name:'查看原文'}));
    expect(await screen.findByText(version.rawText)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'应用此 JD'}));
    expect(await screen.findByRole('dialog',{name:'应用公共 JD'})).toBeInTheDocument();
    expect(screen.getByLabelText('岗位名称')).toHaveValue(jd.title);
    expect(screen.getByLabelText('岗位名称')).toBeDisabled();
    expect(screen.getByLabelText('JD 归属部门')).toBeEnabled();
    expect(screen.getByLabelText('JD 原文')).toHaveAttribute('readonly');
    expect(screen.queryByRole('checkbox',{name:'公开到公共 JD 列表'})).not.toBeInTheDocument();
    expect(screen.getByLabelText('JD 原文')).toHaveValue(version.rawText);
    expect(screen.getByLabelText('招聘人数（HC）')).toHaveValue('');
    expect(screen.getByLabelText('招聘周期（天）')).toHaveValue('');
    expect(screen.getByLabelText('招聘类型')).toHaveValue('');
    expect(screen.queryByLabelText('客户')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请填写招聘人数（HC）')).toBeInTheDocument();
    expect(screen.getByText('请填写招聘周期')).toBeInTheDocument();
    expect(demandApi.createSourceJd).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'2'}});
    fireEvent.change(screen.getByLabelText('招聘周期（天）'),{target:{value:'45'}});
    fireEvent.mouseDown(screen.getByLabelText('招聘类型'));
    fireEvent.click(await screen.findByText('内部招聘',{selector:'.ant-select-item-option-content'}));
    fireEvent.mouseDown(screen.getByLabelText('JD 归属部门'));
    expect(screen.queryByText('无支持关系部门',{selector:'.ant-select-item-option-content'})).not.toBeInTheDocument();
    fireEvent.click(await screen.findByText(supportDepartment.name,{selector:'.ant-select-item-option-content'}));
    await selectCity('杭州市','浙江省');
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await waitFor(()=>expect(demandApi.createSourceJd).toHaveBeenCalledWith(expect.objectContaining({ appliedFromJdVersionId:version.id, ownerDepartmentId:'dept-2', workRegionName:'杭州市', title:jd.title, headcount:2, recruitmentCycleDays:45 })));
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).not.toHaveProperty('ownerUserId');
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).not.toHaveProperty('contentShared');
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).toMatchObject({clientId:null,recruitmentType:'INTERNAL'});
  },30_000);
  it.each([
    ['JD_SOURCE_CHANGED', '查看最新来源并重新应用', '公共 JD 详情'],
    ['JD_SOURCE_NOT_FOUND', '返回公共 JD 列表重新选择', '公共 JD 列表'],
  ])('keeps an applied draft and recovers from %s', async(code, action, destination)=>{
    vi.mocked(demandApi.createSourceJd).mockRejectedValue(new ApiProblemError({ title:'来源不可用', status:409, detail:'来源 JD 已变化或取消公开，请重新选择', code, recoverable:true }));
    setup(); fireEvent.click(screen.getByRole('button',{name:'公共 JD 列表'}));
    fireEvent.click(await screen.findByRole('button',{name:'查看原文'}));
    fireEvent.click(await screen.findByRole('button',{name:'应用此 JD'},{timeout:10_000}));
    expect(await screen.findByRole('dialog',{name:'应用公共 JD'})).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'2'}});
    fireEvent.change(screen.getByLabelText('招聘周期（天）'),{target:{value:'45'}});
    fireEvent.mouseDown(screen.getByLabelText('招聘类型'));
    fireEvent.click(await screen.findByText('内部招聘',{selector:'.ant-select-item-option-content'}));
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await waitFor(()=>expect(demandApi.createSourceJd).toHaveBeenCalled());
    expect(await screen.findByRole('button',{name:action})).toBeInTheDocument();
    expect(screen.getByLabelText('招聘人数（HC）')).toHaveValue('2');
    fireEvent.click(screen.getByRole('button',{name:action}));
    expect(await screen.findByRole('dialog',{name:destination})).toBeInTheDocument();
  },30_000);
  it('creates without a distinction and starts parsing without a business code', async () => {
    vi.mocked(demandApi.createSourceJd).mockResolvedValue(version); setup(undefined, true); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    expect(screen.queryByLabelText('岗位类型')).not.toBeInTheDocument();
    expect(screen.getByLabelText('工作地区')).toBeInTheDocument();
    await selectRequiredOwnershipAndType();
    fireEvent.change(await screen.findByLabelText('JD 所属人'),{target:{value:'hr-1'}});
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}});
    fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(screen.getByLabelText('招聘周期（天）')).toHaveValue('45'); await waitFor(()=>expect(demandApi.createSourceJd).toHaveBeenCalledWith(expect.objectContaining({workRegionName:'北京市',title:jd.title,rawText:version.rawText,headcount:3,recruitmentCycleDays:45})));
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).not.toHaveProperty('distinction');
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).not.toHaveProperty('jobTypeCode');
    expect(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0]).not.toHaveProperty('workRegionCode');
    expect(Object.hasOwn(vi.mocked(demandApi.createSourceJd).mock.calls[0]![0], 'code')).toBe(false); await waitFor(()=>expect(demandApi.parseJd).toHaveBeenCalledWith(version.id));
  },15_000);
  it('does not offer update or delete for a JD owned by another user', async()=>{
    vi.mocked(demandApi.jds).mockResolvedValue({items:[{...jd,ownerUserId:'admin'}],nextCursor:null,hasMore:false}); setup();
    expect(await screen.findByText('可直接使用')).toBeInTheDocument(); expect(screen.queryByRole('button',{name:/更新/})).not.toBeInTheDocument(); expect(screen.queryByRole('button',{name:/删除/})).not.toBeInTheDocument();
  },15_000);
  it('removes an owned JD after explicit confirmation', async()=>{
    vi.mocked(demandApi.deleteJd).mockResolvedValue(undefined); setup(); await screen.findByText('北京市'); fireEvent.click(await screen.findByRole('button',{name:/删\s*除/},{timeout:10_000})); await screen.findByText('删除这份 JD？'); fireEvent.click(screen.getAllByRole('button',{name:/删\s*除/}).at(-1)!);
    await waitFor(()=>expect(demandApi.deleteJd).toHaveBeenCalledWith(jd,expect.anything()));
  },15_000);
  it('shows JD usage conflicts, retains the JD and allows deletion after usage is cleared', async()=>{
    const failure = new ApiProblemError({title:'JD 正在使用',status:409,code:'JD_IN_USE',detail:'该 JD 仍关联我的简历 1 份、智能筛选 2 份、招聘流程 1 份，请先处理关联简历。',recoverable:true});
    vi.mocked(demandApi.deleteJd).mockRejectedValueOnce(failure).mockResolvedValueOnce(undefined);
    setup();
    fireEvent.click(await screen.findByRole('button',{name:/删\s*除/},{timeout:10_000}));
    expect(await screen.findByText('关联简历仍在我的简历、智能筛选或招聘流程中时不可删除。删除后不再出现在日常选择中，历史招聘记录仍保留。')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button',{name:/删\s*除/}).at(-1)!);
    expect(await screen.findByText(failure.problem.detail)).toBeInTheDocument();
    expect(screen.getByRole('button',{name:jd.title})).toBeInTheDocument();
    expect(demandApi.deleteJd).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('JD 已删除，历史招聘记录仍可查看')).not.toBeInTheDocument();
    await waitFor(()=>expect(screen.getAllByRole('button',{name:/删\s*除/})).toHaveLength(1));
    vi.mocked(demandApi.jds).mockResolvedValue({items:[],nextCursor:null,hasMore:false});
    fireEvent.click(screen.getByRole('button',{name:/删\s*除/}));
    await screen.findByText('删除这份 JD？');
    fireEvent.click(screen.getAllByRole('button',{name:/删\s*除/}).at(-1)!);
    expect(await screen.findByText('JD 已删除，历史招聘记录仍可查看')).toBeInTheDocument();
    await waitFor(()=>expect(screen.queryByRole('button',{name:jd.title})).not.toBeInTheDocument());
    expect(screen.queryByText(failure.problem.detail)).not.toBeInTheDocument();
    expect(demandApi.deleteJd).toHaveBeenCalledTimes(2);
  },15_000);
  it('keeps entered text when saving fails', async()=>{
    vi.mocked(demandApi.createSourceJd).mockRejectedValue(new Error('offline')); setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    await selectRequiredOwnershipAndType();
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:'同名岗位'}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:'原文不会丢失'}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('填写内容已保留，可以修改后重试。')).toBeInTheDocument(); expect(screen.getByLabelText('岗位名称')).toHaveValue('同名岗位'); expect(screen.getByLabelText('招聘周期（天）')).toHaveValue('45'); expect(screen.getByLabelText('JD 原文')).toHaveValue('原文不会丢失');
  },15_000);
  it('shows one error per failed save and allows retry with the entered values', async()=>{
    const failure=new ApiProblemError({title:'Conflict',status:409,code:'JD_DUPLICATE',detail:'相同 JD 正在保存，请重试',recoverable:true});
    vi.mocked(demandApi.createSourceJd).mockRejectedValueOnce(failure).mockResolvedValueOnce(version);
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    await selectRequiredOwnershipAndType();
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}});
    fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}});
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await screen.findByText('填写内容已保留，可以修改后重试。');
    expect(screen.getAllByText(failure.problem.detail)).toHaveLength(1);
    expect(demandApi.parseJd).not.toHaveBeenCalled();
    expect(screen.getByLabelText('JD 原文')).toHaveValue(version.rawText);
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await waitFor(()=>expect(demandApi.parseJd).toHaveBeenCalledWith(version.id));
    expect(demandApi.createSourceJd).toHaveBeenCalledTimes(2);
  },30_000);
  it('removes the distinction field and resets region before editing', async()=>{
    setup(); await screen.findByText('北京市'); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    expect(screen.queryByLabelText('区分说明')).not.toBeInTheDocument();
    await selectCity('杭州市','浙江省');
    fireEvent.click(screen.getByRole('button',{name:'Close'}));
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    await waitFor(() => expect(within(screen.getByRole('dialog',{name:'更新 JD'})).getByText('北京市')).toBeInTheDocument());
  },15_000);
  it('updates a legacy JD without requiring or erasing its distinction', async()=>{
    vi.mocked(demandApi.updateJd).mockResolvedValue(version); setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    await screen.findByLabelText('工作地区');
    await selectCity('杭州市','浙江省');
    fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(jd,expect.objectContaining({workRegionName:'杭州市'})));
    expect(vi.mocked(demandApi.updateJd).mock.calls[0]![1]).not.toHaveProperty('distinction');
  },15_000);
  it('lets a system administrator update the JD owner and department', async()=>{
    vi.mocked(demandApi.updateJd).mockResolvedValue(version); setup(undefined,true,[],true);
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    const owner=await screen.findByLabelText('JD 所属人'); expect(owner).toHaveValue('hr-1');
    fireEvent.change(owner,{target:{value:'hr-2'}});
    fireEvent.mouseDown(screen.getByLabelText('JD 归属部门')); fireEvent.click(await screen.findByText(supportDepartment.name,{selector:'.ant-select-item-option-content'}));
    fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(jd,expect.objectContaining({ownerUserId:'hr-2',ownerDepartmentId:'dept-2'})));
  },15_000);
  it('does not expose owner reassignment to a non-super administrator', async()=>{
    setup(undefined,true,[],false); fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    expect(await screen.findByLabelText('JD 归属部门')).toBeEnabled();
    expect(screen.queryByLabelText('JD 所属人')).not.toBeInTheDocument();
  },15_000);
  it('lets only the owner or super administrator change public sharing', async()=>{
    vi.mocked(demandApi.updateJd).mockResolvedValue(version);
    setup();
    expect(await screen.findByText('未公开')).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button',{name:/更新/}));
    fireEvent.click(await screen.findByRole('checkbox',{name:'公开到公共 JD 列表'}));
    fireEvent.click(await screen.findByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(jd,expect.objectContaining({contentShared:true})));
  },15_000);
  it('omits sharing when an HR director edits another owner JD', async()=>{
    vi.mocked(demandApi.jds).mockResolvedValue({items:[{...jd,ownerUserId:'other',contentShared:true}],nextCursor:null,hasMore:false});
    vi.mocked(demandApi.updateJd).mockResolvedValue(version);
    setup(undefined,true,[],false);
    fireEvent.click(await screen.findByRole('button',{name:/更新/}));
    expect(await screen.findByRole('button',{name:'保存修改'})).toBeInTheDocument();
    expect(screen.queryByRole('checkbox',{name:'公开到公共 JD 列表'})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalled());
    expect(vi.mocked(demandApi.updateJd).mock.calls[0]![1]).not.toHaveProperty('contentShared');
  },15_000);
  it('does not let an HR director publish a JD created for another owner', async()=>{
    setup(undefined,true,[],false);
    fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(await screen.findByLabelText('JD 所属人'),{target:{value:'other'}});
    expect(screen.queryByRole('checkbox',{name:'公开到公共 JD 列表'})).not.toBeInTheDocument();
  });
  it.each([false, true])('keeps applied content read-only and allows personal department and city changes (super admin: %s)', async(superAdmin)=>{
    const applied = {...jd, appliedFromJdVersionId:version.id};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[applied],nextCursor:null,hasMore:false});
    vi.mocked(demandApi.updateJd).mockResolvedValue(version);
    setup(undefined,superAdmin);
    fireEvent.click(await screen.findByRole('button',{name:/更新/}));
    expect(await screen.findByLabelText('岗位名称')).toBeDisabled();
    expect(screen.getByLabelText('JD 原文')).toHaveAttribute('readonly');
    expect(screen.queryByRole('checkbox',{name:'公开到公共 JD 列表'})).not.toBeInTheDocument();
    expect(screen.getByLabelText('JD 归属部门')).toBeEnabled();
    fireEvent.mouseDown(screen.getByLabelText('JD 归属部门'));
    if (superAdmin) expect(screen.getByText('无支持关系部门',{selector:'.ant-select-item-option-content'})).toBeInTheDocument();
    else expect(screen.queryByText('无支持关系部门',{selector:'.ant-select-item-option-content'})).not.toBeInTheDocument();
    fireEvent.click(await screen.findByText(supportDepartment.name,{selector:'.ant-select-item-option-content'}));
    await selectCity('杭州市','浙江省');
    fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(applied,expect.objectContaining({title:jd.title,rawText:version.rawText,ownerDepartmentId:'dept-2',workRegionName:'杭州市'})));
    expect(vi.mocked(demandApi.updateJd).mock.calls[0]![1]).not.toHaveProperty('contentShared');
  },30_000);
  it('preserves historical region and department while updating only applied JD configuration', async()=>{
    const legacy = {...jd,workRegionName:'历史驻场地区',ownerDepartmentId:'dept-unassigned',appliedFromJdVersionId:version.id};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[legacy],nextCursor:null,hasMore:false});
    vi.mocked(demandApi.updateJd).mockResolvedValue(version);
    setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/}));
    const departmentField = await screen.findByLabelText('JD 归属部门');
    const editor = departmentField.closest<HTMLElement>('[role=dialog]')!;
    expect(editor).toBeVisible();
    expect(within(editor).getByText('更新 JD')).toBeInTheDocument();
    expect(within(editor).getByText('无支持关系部门')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'5'}});
    fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(legacy,expect.objectContaining({workRegionName:'历史驻场地区',ownerDepartmentId:'dept-unassigned',headcount:5})));
  },15_000);
  it('shows current source ownership from public fields and explicit missing ownership', async()=>{
    const known = {...departmentSource,ownerUserId:'source-owner',ownerUserName:'来源所属人',ownerDepartmentId:'outside-department',ownerDepartmentName:'跨部门来源'};
    const missing = {...departmentSource,id:'missing-source',ownerUserId:null,ownerUserName:null,ownerDepartmentId:null,ownerDepartmentName:null};
    vi.mocked(demandApi.publicJds).mockResolvedValue({items:[known,missing],nextCursor:null,hasMore:false});
    vi.mocked(demandApi.publicJd).mockResolvedValue(known);
    setup(); fireEvent.click(screen.getByRole('button',{name:'公共 JD 列表'}));
    await screen.findByRole('dialog',{name:'公共 JD 列表'});
    expect(await screen.findByText('来源所属人')).toBeInTheDocument();
    expect(screen.getByText('跨部门来源')).toBeInTheDocument();
    expect(screen.getByText('未分配所属人')).toBeInTheDocument();
    expect(screen.getByText('未分配部门')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button',{name:'查看原文'})[0]!);
    await waitFor(()=>expect(demandApi.publicJd).toHaveBeenCalled());
    expect(await screen.findByRole('dialog',{name:'公共 JD 详情'})).toHaveTextContent('来源所属人');
    expect(screen.getByRole('dialog',{name:'公共 JD 详情'})).toHaveTextContent('跨部门来源');
  });
  it('reports a parse start failure accurately', async()=>{
    vi.mocked(demandApi.createSourceJd).mockResolvedValue(version); vi.mocked(demandApi.parseJd).mockRejectedValueOnce(new Error('parse unavailable')); setup();
    fireEvent.click(screen.getByRole('button',{name:/新增 JD/})); await selectRequiredOwnershipAndType(); fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('JD 已保存，但自动解析未启动；可在详情中重试。')).toBeInTheDocument(); expect(screen.queryByText(/系统正在解析/)).not.toBeInTheDocument();
  },15_000);
  it('offers retry from failed JD details', async()=>{
    vi.mocked(demandApi.jds).mockResolvedValue({items:[{...jd,currentParseStatus:'FAILED'}],nextCursor:null,hasMore:false}); setup();
    fireEvent.click(await screen.findByRole('button',{name:jd.title},{timeout:10_000})); expect(await screen.findByRole('button',{name:'重新解析'},{timeout:10_000})).toBeInTheDocument();
    expect(await screen.findByRole('group',{name:'JD 来源'})).toBeInTheDocument();
  },15_000);
  it('requires HC before saving', async()=>{
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}});
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请填写招聘人数（HC）')).toBeInTheDocument();
    expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },30_000);
  it('requires department and an explicit recruitment type for a new JD', async()=>{
    render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SourceJdPanel canAdmin={false} actorUserId="hr-1" clients={[]} departments={[department]}/></QueryClientProvider></App></ConfigProvider>); fireEvent.click(screen.getByRole('button',{name:/新增 JD/})); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请选择 JD 归属部门')).toBeInTheDocument();
    expect(screen.getByText('请选择招聘类型')).toBeInTheDocument();
    expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },15_000);
  it('defaults a new JD to the current actor home department', async()=>{
    vi.mocked(demandApi.createSourceJd).mockResolvedValue(version); setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/})); await selectCity('北京市'); fireEvent.mouseDown(await screen.findByLabelText('招聘类型')); fireEvent.click(await screen.findByText('内部招聘',{selector:'.ant-select-item-option-content'})); fireEvent.change(screen.getByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'})); await waitFor(()=>expect(demandApi.createSourceJd).toHaveBeenCalledWith(expect.objectContaining({ownerDepartmentId:'dept-1'})));
  },15_000);
  it('rejects a home department that is not in the current directory', async()=>{
    render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SourceJdPanel canAdmin={false} actorUserId="hr-1" actorHomeDepartmentId="removed-dept" clients={[]} departments={[department]}/></QueryClientProvider></App></ConfigProvider>); fireEvent.click(screen.getByRole('button',{name:/新增 JD/})); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请选择部门库中的有效部门')).toBeInTheDocument(); expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },15_000);
  it('shows department loading failure inside the editor and retries it', async()=>{
    const retry=vi.fn().mockResolvedValue(undefined);
    render(<ConfigProvider><App><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><SourceJdPanel canAdmin={false} actorUserId="hr-1" clients={[]} departments={[]} departmentUnavailable onRetryDepartments={retry}/></QueryClientProvider></App></ConfigProvider>);
    fireEvent.click(screen.getByRole('button',{name:/新增 JD/})); expect(await screen.findByText('保存 JD 前请重新读取部门库。')).toBeInTheDocument(); fireEvent.click(screen.getByRole('button',{name:'重试部门'})); expect(retry).toHaveBeenCalledOnce();
  },15_000);
  it('requires a client for external recruitment', async()=>{
    const client={id:'client-1',code:'C1',name:'示例客户',status:'ACTIVE' as const,ownerUserId:'hr-1',ownerDepartmentId:'dept-1',version:1,updatedAt:jd.createTime};
    setup(undefined,false,[client]); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.mouseDown(await screen.findByLabelText('招聘类型')); fireEvent.click(await screen.findByText('外部招聘',{selector:'.ant-select-item-option-content'})); await screen.findByLabelText('客户');
    fireEvent.click(screen.getByRole('button',{name:'保存并解析'})); expect(await screen.findByText('外部招聘必须选择客户')).toBeInTheDocument(); expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },15_000);
  it('includes all ownership fields for external recruitment', async()=>{
    const client={id:'client-1',code:'C1',name:'示例客户',status:'ACTIVE' as const,ownerUserId:'hr-1',ownerDepartmentId:'dept-1',version:1,updatedAt:jd.createTime};
    vi.mocked(demandApi.createSourceJd).mockResolvedValue(version); setup(undefined,false,[client]); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.mouseDown(await screen.findByLabelText('招聘类型')); fireEvent.click(await screen.findByText('外部招聘',{selector:'.ant-select-item-option-content'})); await screen.findByLabelText('客户');
    await selectCity('北京市'); fireEvent.mouseDown(screen.getByLabelText('客户')); fireEvent.click(await screen.findByText(client.name,{selector:'.ant-select-item-option-content'})); fireEvent.change(screen.getByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await waitFor(()=>expect(demandApi.createSourceJd).toHaveBeenCalledWith(expect.objectContaining({ownerDepartmentId:'dept-1',recruitmentType:'EXTERNAL',clientId:'client-1'})));
  },15_000);
  it('clears the existing client when an edited JD switches to internal recruitment', async()=>{
    const client={id:'client-1',code:'C1',name:'示例客户',status:'ACTIVE' as const,ownerUserId:'hr-1',ownerDepartmentId:'dept-1',version:1,updatedAt:jd.createTime};
    const external={...jd,recruitmentType:'EXTERNAL' as const,clientId:client.id,clientName:client.name};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[external],nextCursor:null,hasMore:false}); vi.mocked(demandApi.updateJd).mockResolvedValue(version); setup(undefined,false,[client]);
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000})); expect(await screen.findByLabelText('JD 归属部门')).toBeEnabled(); fireEvent.mouseDown(screen.getByLabelText('JD 归属部门')); fireEvent.click(await screen.findByText(supportDepartment.name,{selector:'.ant-select-item-option-content'})); await screen.findByLabelText('客户'); fireEvent.mouseDown(screen.getByLabelText('招聘类型')); fireEvent.click(await screen.findByText('内部招聘',{selector:'.ant-select-item-option-content'})); await waitFor(()=>expect(screen.queryByLabelText('客户')).not.toBeInTheDocument()); fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(external,expect.objectContaining({recruitmentType:'INTERNAL',clientId:null})));
    expect(vi.mocked(demandApi.updateJd).mock.calls[0]![1]).toHaveProperty('ownerDepartmentId','dept-2');
  },30_000);
  it('requires a recruitment cycle when a new JD cycle is cleared', async()=>{
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    const cycle=await screen.findByLabelText('招聘周期（天）'); expect(cycle).toHaveValue('45'); fireEvent.change(cycle,{target:{value:''}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('请填写招聘周期')).toBeInTheDocument(); expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },15_000);
  it('rejects a fractional HC before saving', async()=>{
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}}); fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'1.5'}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    expect(await screen.findByText('HC 必须为 1～10000 的整数')).toBeInTheDocument();
    expect(demandApi.createSourceJd).not.toHaveBeenCalled();
  },15_000);
  it('rejects HC above 10000 before saving', async()=>{
    setup(); fireEvent.click(screen.getByRole('button',{name:/新增 JD/}));
    fireEvent.change(await screen.findByLabelText('岗位名称'),{target:{value:jd.title}}); fireEvent.change(screen.getByLabelText('JD 原文'),{target:{value:version.rawText}}); const headcount=screen.getByLabelText('招聘人数（HC）'); fireEvent.change(headcount,{target:{value:'10001'}}); fireEvent.click(screen.getByRole('button',{name:'保存并解析'}));
    await waitFor(()=>expect(headcount).toHaveAttribute('aria-invalid','true')); expect(demandApi.createSourceJd).not.toHaveBeenCalled(); expect(screen.getByRole('dialog')).toBeInTheDocument();
  },15_000);
  it('does not let an edit reduce HC below occupied positions', async()=>{
    const occupied={...jd,headcount:3,occupiedHeadcount:2,remainingHeadcount:1};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[occupied],nextCursor:null,hasMore:false}); setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    expect(await screen.findByLabelText('招聘人数（HC）')).toHaveValue('3');
    fireEvent.change(screen.getByLabelText('招聘人数（HC）'),{target:{value:'1'}}); fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    expect(await screen.findByText('不能少于已占用的 2 个名额')).toBeInTheDocument(); expect(demandApi.updateJd).not.toHaveBeenCalled();
  },15_000);
  it('requires legacy JDs without a cycle to be completed before updating', async()=>{
    const legacy={...jd,recruitmentCycleDays:null}; vi.mocked(demandApi.jds).mockResolvedValue({items:[legacy],nextCursor:null,hasMore:false}); setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000})); expect(await screen.findByLabelText('招聘周期（天）')).toHaveValue(''); fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    expect(await screen.findByText('请填写招聘周期')).toBeInTheDocument(); expect(demandApi.updateJd).not.toHaveBeenCalled();
  },15_000);
  it('retains the entered HC after a server capacity conflict', async()=>{
    const occupied={...jd,headcount:3,occupiedHeadcount:2,remainingHeadcount:1};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[occupied],nextCursor:null,hasMore:false}); vi.mocked(demandApi.updateJd).mockRejectedValueOnce(new ApiProblemError({title:'HC 冲突',status:409,code:'JD_HEADCOUNT_CONFLICT',detail:'名额已被并发占用',recoverable:true})); setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000}));
    fireEvent.change(await screen.findByLabelText('招聘人数（HC）'),{target:{value:'2'}}); fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    expect(await screen.findByText('名额已被并发占用')).toBeInTheDocument(); expect(screen.getByLabelText('招聘人数（HC）')).toHaveValue('2'); expect(screen.getByLabelText('岗位名称')).toHaveValue(jd.title);
  },15_000);
  it('updates HC without reparsing when the server keeps the current JD version', async()=>{
    const editable={...jd,headcount:2,occupiedHeadcount:1,remainingHeadcount:1,recruitmentCycleDays:30};
    vi.mocked(demandApi.jds).mockResolvedValue({items:[editable],nextCursor:null,hasMore:false}); vi.mocked(demandApi.updateJd).mockResolvedValue(version); setup();
    fireEvent.click(await screen.findByRole('button',{name:/更新/},{timeout:10_000})); expect(await screen.findByLabelText('招聘周期（天）')).toHaveValue('30'); fireEvent.change(await screen.findByLabelText('招聘人数（HC）'),{target:{value:'3'}}); fireEvent.click(screen.getByRole('button',{name:'保存修改'}));
    await waitFor(()=>expect(demandApi.updateJd).toHaveBeenCalledWith(editable,expect.objectContaining({headcount:3,recruitmentCycleDays:30}))); await waitFor(()=>expect(screen.queryByRole('dialog',{name:'更新 JD'})).not.toBeInTheDocument()); expect(demandApi.parseJd).not.toHaveBeenCalled();
  },15_000);

it('修改每页条数时重置 JD 库游标并使用新的请求数量', async () => {
  vi.mocked(demandApi.countJds).mockResolvedValue(21);
  vi.mocked(demandApi.jds).mockImplementation((_query, cursor) => Promise.resolve({ items: [jd], hasMore: !cursor, nextCursor: cursor ? null : 'next' }));
  setup();
  const pagination = screen.getByRole('navigation', { name: 'JD 库分页' });
  await waitFor(() => {
    expect(pagination).toHaveAttribute('aria-busy', 'false');
    expect(screen.getByRole('button', { name: '下一页' })).toBeEnabled();
  }, { timeout: 10_000 });
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() => {
    expect(pagination).toHaveAttribute('aria-busy', 'false');
    expect(screen.getByRole('button', { name: '上一页' })).toBeEnabled();
  }, { timeout: 10_000 });
  const size = screen.getByRole('spinbutton', { name: '每页条数' });
  fireEvent.change(size, { target: { value: '30' } }); fireEvent.blur(size);
  await waitFor(() => expect(demandApi.jds).toHaveBeenLastCalledWith('', undefined, 30), { timeout: 10_000 });
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
});

it('按当前搜索条件显示 JD 总数', async () => {
  vi.mocked(demandApi.countJds).mockResolvedValue(41);
  setup();
  expect(await screen.findByText('条/共41条记录')).toBeVisible();
  expect(screen.getByText('共3页')).toBeVisible();
  expect(screen.getByRole('button', { name: '搜索 JD' })).toBeVisible();
  fireEvent.change(screen.getByRole('searchbox', { name: '搜索 JD 库' }), { target: { value: '运维' } });
  fireEvent.keyDown(screen.getByRole('searchbox', { name: '搜索 JD 库' }), { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(demandApi.countJds).toHaveBeenLastCalledWith('运维', expect.any(AbortSignal)));
  fireEvent.change(screen.getByRole('searchbox', { name: '搜索 JD 库' }), { target: { value: '产品' } });
  fireEvent.click(screen.getByRole('button', { name: '搜索 JD' }));
  await waitFor(() => expect(demandApi.countJds).toHaveBeenLastCalledWith('产品', expect.any(AbortSignal)));
});

it('为 JD 库和公共 JD 搜索提供可读的清空按钮', async () => {
  setup();
  const librarySearch = screen.getByRole('searchbox', { name: '搜索 JD 库' });
  fireEvent.change(librarySearch, { target: { value: '临时查询' } });
  fireEvent.click(screen.getByRole('button', { name: '清空搜索' }));
  expect(librarySearch).toHaveValue('');

  fireEvent.click(screen.getByRole('button', { name: '公共 JD 列表' }));
  const departmentSearch = await screen.findByRole('searchbox', { name: '搜索公共 JD' });
  fireEvent.change(departmentSearch, { target: { value: '临时查询' } });
  fireEvent.click(screen.getByRole('button', { name: '清空搜索' }));
  expect(departmentSearch).toHaveValue('');
});
});

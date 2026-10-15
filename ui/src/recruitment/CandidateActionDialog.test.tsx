import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CandidateActionDialog } from './CandidateActionDialog';
import { candidateActionsApi, type RecruitmentReadiness } from './candidateActionsApi';

vi.mock('../auth/session',()=>({useSession:()=>({actor:{userId:'hr'}})}));
vi.mock('../display/BusinessReferences',()=>({CandidateName:({id}:{id:string})=><span>{id}</span>}));
vi.mock('./candidateActionsApi',()=>({candidateActionsApi:{readiness:vi.fn(),activateStandard:vi.fn(),advance:vi.fn()}}));
vi.mock('./ApplicationDetailsDialog',()=>({ApplicationDetailsDialog:({applicationId,onClose}:{applicationId?:string;onClose:()=>void})=>applicationId?<div role="dialog" aria-label="招聘活动详情">{applicationId}<button onClick={onClose}>关闭详情</button></div>:null}));
const ready:RecruitmentReadiness={headcount:2,occupiedHeadcount:1,remainingHeadcount:1,jdId:'jd',jdVersionId:'jv',jdTitle:'运维岗位',demandId:'demand',recruitmentType:'INTERNAL',canProcess:true,canManageWorkflow:true,activeWorkflowId:'workflow',activeWorkflow:{template:{id:'workflow',jdVersionId:'jv',versionNo:1,recruitmentType:'INTERNAL',status:'ACTIVE',version:1,activatedAt:null},steps:[{id:'communication',templateVersionId:'workflow',stepCode:'CANDIDATE_COMMUNICATION',sequenceNo:1,candidateOptional:false,defaultFollowUpChannel:'PHONE'},{id:'interview',templateVersionId:'workflow',stepCode:'INTERVIEW_ROUND_1',sequenceNo:2,candidateOptional:true,defaultFollowUpChannel:null}]},draftWorkflows:[],standardSteps:['CANDIDATE_COMMUNICATION','INTERVIEW_ROUND_1','DECISION','OFFER']};
let queryClient:QueryClient;
function mount(onCompleted=vi.fn(),count=1){
 queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
 render(<MemoryRouter><ConfigProvider theme={{token:{motion:false}}}><App><QueryClientProvider client={queryClient}><CandidateActionDialog open mode="advance" jdVersionId="jv" candidates={Array.from({length:count},(_,i)=>({candidateId:`candidate${i}`,resumeVersionId:`resume${i}`}))} onClose={vi.fn()} onCompleted={onCompleted}/></QueryClientProvider></App></ConfigProvider></MemoryRouter>);
 return onCompleted;
}
afterEach(cleanup);
beforeEach(()=>{vi.resetAllMocks();vi.mocked(candidateActionsApi.readiness).mockResolvedValue(ready);vi.mocked(candidateActionsApi.advance).mockImplementation((_,items)=>Promise.resolve({items:items.map(item=>({...item,status:'SUCCEEDED',applicationId:'app'}))}));});
it('推荐处理中不提供无必要的暂停操作',async()=>{
 vi.mocked(candidateActionsApi.advance).mockImplementation(()=>new Promise(()=>{}));
 mount(undefined,2);
 const submit=await screen.findByRole('button',{name:'确认推荐 2 人'});
 await waitFor(()=>expect(submit).toBeEnabled());
 fireEvent.click(submit);
 expect(await screen.findByRole('progressbar',{name:'候选人批量处理进度'})).toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'处理完当前请求后暂停'})).not.toBeInTheDocument();
 expect(screen.queryByText('正在暂停')).not.toBeInTheDocument();
});
it('one remaining HC still permits recommending multiple candidates because recommendations do not occupy HC',async()=>{
 mount(undefined,2);
 const submit=await screen.findByRole('button',{name:'确认推荐 2 人'});
 await screen.findByText('已占用 1 / 2 · 剩余 1'); expect(submit).toBeEnabled();
});
it.each([
 [{...ready,headcount:null,remainingHeadcount:null},'HC 待填写'],
 [{...ready,occupiedHeadcount:2,remainingHeadcount:0},'HC 已满'],
])('blocks recommendations when capacity is unavailable (%s)',async(readiness,label)=>{
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue(readiness); mount();
 expect(await screen.findByText(label,{exact:false})).toBeInTheDocument(); expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
});
it('refreshes a full HC state and restores the recommendation action after capacity changes',async()=>{
 vi.mocked(candidateActionsApi.readiness).mockResolvedValueOnce({...ready,occupiedHeadcount:2,remainingHeadcount:0}).mockResolvedValueOnce(ready);
 mount(); expect(await screen.findByText(/HC 已满/)).toBeInTheDocument(); expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
 fireEvent.click(screen.getByRole('button',{name:'刷新名额'}));
 await waitFor(()=>expect(candidateActionsApi.readiness).toHaveBeenCalledTimes(2)); expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeEnabled();
});
it('在推荐弹窗内启用标准流程，然后直接推荐，无需AI结果',async()=>{
 vi.mocked(candidateActionsApi.readiness).mockResolvedValueOnce({...ready,activeWorkflowId:null,activeWorkflow:null}).mockResolvedValue(ready);
 vi.mocked(candidateActionsApi.activateStandard).mockResolvedValue(ready);
 const completed=mount();
 const activate=await screen.findByRole('button',{name:'使用并激活标准流程'});
 expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
 fireEvent.click(activate);
 await waitFor(()=>expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'确认推荐 1 人'}));
 await waitFor(()=>expect(completed).toHaveBeenCalledWith(['candidate0']));
 expect(candidateActionsApi.advance).toHaveBeenCalledWith('jv',[{candidateId:'candidate0',resumeVersionId:'resume0'}],expect.any(String),expect.any(String));
 fireEvent.click(await screen.findByRole('button',{name:'查看招聘流程'}));
 expect(await screen.findByRole('dialog',{name:'招聘活动详情'})).toHaveTextContent('app');
 expect(screen.queryByRole('link',{name:'查看招聘流程'})).not.toBeInTheDocument();
});
it('批量推荐为每个成功候选人就地打开对应招聘流程',async()=>{
 vi.mocked(candidateActionsApi.advance).mockResolvedValue({items:[{candidateId:'candidate0',resumeVersionId:'resume0',status:'SUCCEEDED',applicationId:'app0'},{candidateId:'candidate1',resumeVersionId:'resume1',status:'ALREADY_EXISTS',applicationId:'app1'}]});
 mount(undefined,2);
 await waitFor(()=>expect(screen.getByRole('button',{name:'确认推荐 2 人'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'确认推荐 2 人'}));
 const buttons=await screen.findAllByRole('button',{name:'查看招聘流程'});
 expect(buttons).toHaveLength(2);
 fireEvent.click(buttons[1]!);
 expect(await screen.findByRole('dialog',{name:'招聘活动详情'})).toHaveTextContent('app1');
 fireEvent.click(screen.getByRole('button',{name:'关闭详情'}));
 fireEvent.click(buttons[0]!);
 expect(await screen.findByRole('dialog',{name:'招聘活动详情'})).toHaveTextContent('app0');
});
it('全部失败显示失败进度且不宣称成功项已保存',async()=>{
 vi.mocked(candidateActionsApi.advance).mockResolvedValue({items:[{candidateId:'candidate0',resumeVersionId:'resume0',status:'FAILED',message:'请重试此候选人'}]});
 mount();
 await waitFor(()=>expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'确认推荐 1 人'}));
 expect(await screen.findByText('候选人未处理成功')).toBeInTheDocument();
 expect(screen.getByRole('progressbar',{name:'候选人批量处理进度'})).toHaveClass('ant-progress-status-exception');
 expect(screen.queryByText(/成功项已保存/)).not.toBeInTheDocument();
 expect(screen.getByText('已完成 0 人 · 失败 1 人 · 尚未处理 0 人')).toBeInTheDocument();
});
it('部分失败保留已成功项，仅重试失败候选人',async()=>{
 vi.mocked(candidateActionsApi.advance).mockResolvedValueOnce({items:[{candidateId:'candidate0',resumeVersionId:'resume0',status:'SUCCEEDED',applicationId:'app0'},{candidateId:'candidate1',resumeVersionId:'resume1',status:'FAILED',message:'请先解除已核实的限制'}]}).mockResolvedValueOnce({items:[{candidateId:'candidate1',resumeVersionId:'resume1',status:'SUCCEEDED',applicationId:'app1'}]});
 const completed=mount(undefined,2);
 await waitFor(()=>expect(screen.getByRole('button',{name:'确认推荐 2 人'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'确认推荐 2 人'}));
 expect(await screen.findByText('部分候选人未处理成功')).toBeInTheDocument();
 expect(screen.getByRole('progressbar',{name:'候选人批量处理进度'})).toHaveClass('ant-progress-status-exception');
 await waitFor(()=>expect(completed).toHaveBeenCalledWith(['candidate0']));
 fireEvent.click(screen.getByRole('button',{name:'继续处理未完成项'}));
 await waitFor(()=>expect(candidateActionsApi.advance).toHaveBeenCalledTimes(2));
 expect(vi.mocked(candidateActionsApi.advance).mock.calls[1]?.[1]).toEqual([{candidateId:'candidate1',resumeVersionId:'resume1'}]);
 expect(vi.mocked(candidateActionsApi.advance).mock.calls[1]?.[3]).toEqual(vi.mocked(candidateActionsApi.advance).mock.calls[0]?.[3]);
 await waitFor(()=>expect(screen.getByRole('progressbar',{name:'候选人批量处理进度'})).toHaveClass('ant-progress-status-success'));
 expect(screen.queryByText('部分候选人未处理成功')).not.toBeInTheDocument();
});
it('推荐时显示岗位绑定的外部招聘及甲方公司，不预选沟通或面试轮次',async()=>{
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue({...ready,recruitmentType:'EXTERNAL',clientId:'client-exact-version',clientName:'甲方示例公司'});
 mount();
 expect(await screen.findByText('外部招聘')).toBeInTheDocument();
 expect(screen.getByText('甲方公司：甲方示例公司')).toBeInTheDocument();
 expect(screen.queryByText('推荐后待 HR 跟进')).not.toBeInTheDocument();
 expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
 expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
 expect(screen.queryByText('一面')).not.toBeInTheDocument();
});
it('内部招聘显示明确标识，权限不足时不能推荐',async()=>{
 mount();
 expect(await screen.findByText('内部招聘')).toBeInTheDocument();
 cleanup();
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue({...ready,canProcess:false});
 mount();
 expect(await screen.findByText('此岗位由其他 HR 负责')).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
});
it('岗位资格读取失败时关闭推荐入口并提供重试',async()=>{
 vi.mocked(candidateActionsApi.readiness).mockRejectedValue(new Error('连接中断'));
 mount();
 expect(await screen.findByText('无法核对招聘流程')).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
 expect(screen.getByRole('button',{name:'重试流程检查'})).toBeInTheDocument();
});
it('资格重读被拒绝后隐藏缓存岗位和流程激活入口，并可显式重试恢复',async()=>{
 const inactive={...ready,jdTitle:'外部架构师',recruitmentType:'EXTERNAL' as const,clientId:'client',clientName:'甲方公司',activeWorkflowId:null,activeWorkflow:null};
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue(inactive);
 mount();
 expect(await screen.findByText('推荐岗位：外部架构师')).toBeInTheDocument();
 expect(screen.getByText('甲方公司：甲方公司')).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'使用并激活标准流程'})).toBeEnabled();
 let rejectRefetch!:(error:unknown)=>void;
 vi.mocked(candidateActionsApi.readiness).mockImplementation(()=>new Promise((_,reject)=>{rejectRefetch=reject;}));
 act(()=>{void queryClient.invalidateQueries({queryKey:['recruitment','readiness','jv','hr']});});
 await screen.findByText('正在核对岗位权限与招聘流程…');
 expect(screen.queryByText('推荐岗位：外部架构师')).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'使用并激活标准流程'})).not.toBeInTheDocument();
 await act(()=>{rejectRefetch(new ApiProblemError({status:403,code:'ACCESS_DENIED',title:'拒绝访问',detail:'岗位权限已撤销',recoverable:false}));return Promise.resolve();});
 await screen.findByText('无法核对招聘流程');
 expect(screen.queryByText('推荐岗位：外部架构师')).not.toBeInTheDocument();
 expect(screen.queryByText('甲方公司：甲方公司')).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'使用并激活标准流程'})).not.toBeInTheDocument();
 vi.mocked(candidateActionsApi.readiness).mockResolvedValue(inactive);
 fireEvent.click(screen.getByRole('button',{name:'重试流程检查'}));
 expect(await screen.findByText('推荐岗位：外部架构师')).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'使用并激活标准流程'})).toBeEnabled();
});

it('候选人列表只使用上一页和下一页，并保留跨页候选人',async()=>{
 mount(undefined,21);
 await screen.findByText('推荐岗位：运维岗位');
 expect(screen.getByText('candidate0')).toBeInTheDocument();
 expect(screen.queryByText('candidate20')).not.toBeInTheDocument();
 expect(screen.getByRole('button',{name:'上一页'})).toBeDisabled();
 expect(screen.queryByRole('spinbutton',{name:'页码'})).not.toBeInTheDocument();
 fireEvent.click(screen.getByRole('button',{name:'下一页'}));
 expect(screen.getByText('candidate20')).toBeInTheDocument();
 expect(screen.queryByText('candidate0')).not.toBeInTheDocument();
 expect(screen.getByRole('button',{name:'下一页'})).toBeDisabled();
 fireEvent.click(screen.getByRole('button',{name:'上一页'}));
 expect(screen.getByText('candidate0')).toBeInTheDocument();
});

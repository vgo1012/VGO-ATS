import { jobLabel } from '../demand/jobLabel';
import { hasJdCapacity } from '../demand/headcount';
import { JdHeadcount } from '../demand/JdHeadcount';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Space, Tag } from 'antd';
import { demandApi, type WorkflowDetails } from '../demand/api';
import { candidateActionsApi, type RecruitmentReadiness } from './candidateActionsApi';
import { actionError } from './candidateActions';

/** Configuration is reviewed in the same decision dialog; no silent workflow changes. */
export function RecruitmentPreparation({value,onChanged,compact=false}:{value:RecruitmentReadiness;onChanged:()=>Promise<unknown>;compact?:boolean}) {
  const qc=useQueryClient();
  const activate=useMutation({mutationFn:async(draft?:WorkflowDetails)=>{if(draft)await demandApi.activateWorkflow(draft);else await candidateActionsApi.activateStandard(value.jdVersionId,'HR 核对标准流程并启用，用于候选人推荐');},onSuccess:async()=>{await onChanged();await qc.invalidateQueries({queryKey:['matching','active-jd',value.jdVersionId]});}});
  if(!value.canProcess)return <Alert type="warning" showIcon title="此岗位由其他 HR 负责" description="仅负责人或明确添加的协作人可以推荐候选人。"/>;
  return <Space orientation="vertical" style={{width:'100%'}}>
    {!compact?<JdHeadcount value={value} />:null}
    {!compact||!hasJdCapacity(value)?<Space wrap><Button href={`/recruitment/setup?query=${encodeURIComponent(value.jdCode ?? value.jdTitle)}`}>去 JD 编辑</Button><Button onClick={()=>void onChanged()}>刷新名额</Button></Space>:null}
    <Space wrap>
      {compact?<b>推荐岗位：{jobLabel(value.jdTitle, value.jdCode)}</b>:null}
      <Tag color={value.recruitmentType==='EXTERNAL'?'blue':'default'}>{value.recruitmentType==='EXTERNAL'?'外部招聘':value.recruitmentType==='INTERNAL'?'内部招聘':'招聘类型待确认'}</Tag>
      {value.recruitmentType==='EXTERNAL'?<span>甲方公司：{value.clientName??'当前岗位版本未提供公司信息'}</span>:null}
    </Space>
    {compact?<JdHeadcount value={value} compact={hasJdCapacity(value)} />:null}
    {!compact?<p>招聘类型{value.recruitmentType==='EXTERNAL'?'和甲方公司':''}取自当前岗位版本。</p>:null}
    {value.activeWorkflowId?(compact?null:<Alert type="success" showIcon title="推荐后待 HR 跟进" description="无需先运行 AI 筛选。每次跟进后再选择下一步，可安排沟通、增加面试轮次或记录人工决定。"/>):!value.canManageWorkflow?<Alert type="info" showIcon title="招聘流程尚未就绪" description="请联系岗位负责人启用流程，然后重新检查。" action={<Button onClick={()=>void onChanged()}>重新检查</Button>}/>:<>
    {activate.error?<Alert occurrence={activate.error} type="error" showIcon title="流程启用失败" description={actionError(activate.error)}/>:null}
    {value.draftWorkflows.length?<>
      <Alert type="info" showIcon title="已有待启用流程" description="启用岗位流程后即可推荐。每位候选人的实际步骤由 HR 后续逐次决定。"/>
      {value.draftWorkflows.map(draft=><Button key={draft.template.id} loading={activate.isPending} disabled={activate.isPending} onClick={()=>activate.mutate(draft)}>激活流程 V{draft.template.versionNo}</Button>)}
    </>:<Alert type="info" showIcon title="此岗位尚未启用招聘流程" description={<><p>先启用岗位流程，再推荐候选人进入待 HR 跟进。沟通和面试不在此预先安排。</p><Button type="primary" loading={activate.isPending} onClick={()=>activate.mutate(undefined)}>使用并激活标准流程</Button></>}/>}
    </>}
  </Space>;
}

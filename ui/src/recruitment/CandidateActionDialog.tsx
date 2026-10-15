import { hasJdCapacity } from '../demand/headcount';
import { createIdempotencyKey } from '../api/idempotency';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Progress, Space, Table, Tag } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useSession } from '../auth/session';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { CandidateName } from '../display/BusinessReferences';
import { JdSelector } from '../demand/JdSelector';
import { demandApi, type JdSummary } from '../demand/api';
import { candidateActionsApi, type CandidateActionOutcome, type CandidateActionTarget } from './candidateActionsApi';
import { actionError, executeCandidateActions, summarizeCandidateActions } from './candidateActions';
import { RecruitmentPreparation } from './RecruitmentPreparation';
import { LazyApplicationDetailsDialog } from './LazyApplicationDetailsDialog';
import './candidate-action-dialog.css';

export type { CandidateActionTarget } from './candidateActionsApi';
interface Props {
  open:boolean;mode:'advance'|'share';candidates:CandidateActionTarget[];jdVersionId?:string|undefined;
  allowOwnedResumeRecommendation?:boolean;
  onClose:()=>void;onCompleted:(successIds:string[])=>void;
}
export function CandidateActionDialog(props:Props) {
  return props.open?<ActionDialog {...props}/>:null;
}
function ActionDialog({mode,candidates,jdVersionId:fixedJdVersionId,allowOwnedResumeRecommendation=false,onClose,onCompleted}:Props) {
  const [targets]=useState(candidates);
  const [page,setPage]=useState(1);
  const [operationKey]=useState(()=>createIdempotencyKey());
  const [jd,setJd]=useState<JdSummary>();
  const [reason,setReason]=useState(mode==='advance'?'HR 已人工查看简历，确认推荐到招聘流程':'当前岗位不继续推进，结束本次筛选并转入共享简历库');
  const [results,setResults]=useState<CandidateActionOutcome[]>([]);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState<string>();
  const [applicationId,setApplicationId]=useState<string>();
  const stop=useRef(false);
  const running=useRef(false);
  const qc=useQueryClient();
  const {actor}=useSession();
  const versions=useQuery({queryKey:['demand','jd-versions-by-id',jd?.id],queryFn:()=>demandApi.jdVersions(jd!.id),enabled:Boolean(jd&&!fixedJdVersionId)});
  const jdVersionId=fixedJdVersionId??versions.data?.items.find(item=>item.versionNo===jd?.currentVersionNo)?.id;
  const readiness=useQuery({queryKey:['recruitment','readiness',jdVersionId,actor?.userId],queryFn:()=>candidateActionsApi.readiness(jdVersionId!),enabled:Boolean(mode==='advance'&&jdVersionId),retry:false});
  const visibleReadiness=!readiness.error&&!readiness.isFetching?readiness.data:undefined;
  const {succeeded,failed,completedIds,byCandidate}=useMemo(()=>summarizeCandidateActions(results),[results]);
  const remaining=targets.filter(item=>!completedIds.has(item.candidateId));
  const ready=mode==='share'||Boolean((visibleReadiness?.canProcess||allowOwnedResumeRecommendation)&&visibleReadiness?.activeWorkflowId&&hasJdCapacity(visibleReadiness));
  useEffect(()=>{
    if(!busy)return;
    const leaving=(event:BeforeUnloadEvent)=>{event.preventDefault();};
    window.addEventListener('beforeunload',leaving);
    return()=>window.removeEventListener('beforeunload',leaving);
  },[busy]);
  useEffect(()=>()=>{stop.current=true;},[]);
  const execute=async()=>{
    if(running.current||!ready||!reason.trim()||reason.length>500)return;
    running.current=true;stop.current=false;setBusy(true);setError(undefined);
    const retained=succeeded;
    setResults(retained);
    try {
      const done=await executeCandidateActions({mode,candidates:remaining,jdVersionId,reason:reason.trim(),operationKey,stopped:()=>stop.current,onProgress:progress=>setResults([...retained,...progress])});
      const successIds=done.filter(item=>item.status!=='FAILED').map(item=>item.candidateId);
      onCompleted(successIds);
      await Promise.all([qc.invalidateQueries({queryKey:['talent']}),qc.invalidateQueries({queryKey:['talent-search']}),qc.invalidateQueries({queryKey:['recruitment']}),qc.invalidateQueries({queryKey:['applications']}),qc.invalidateQueries({queryKey:['matching']})]);
    }catch(failure){setError(actionError(failure));}
    finally{running.current=false;setBusy(false);}
  };
  return <WorkspaceDialog open className="candidate-action-dialog" size="form" title={mode==='advance'?'推荐候选人到招聘流程':'结束筛选并转入共享简历库'} onCancel={()=>{if(!busy)onClose();}} closable={!busy} keyboard={!busy} mask={{closable:!busy}} footer={<Space wrap>
    {!busy?<Button onClick={onClose}>{succeeded.length?'完成':'取消'}</Button>:null}
    {remaining.length?<Button type="primary" loading={busy} disabled={!ready||busy||!reason.trim()||reason.length>500} onClick={()=>void execute()}>{results.length?'继续处理未完成项':mode==='advance'?`确认推荐 ${targets.length} 人`:`确认转共享 ${targets.length} 人`}</Button>:null}
    {!busy&&mode==='advance'&&targets.length===1&&succeeded[0]?.applicationId?<Button type="primary" onClick={()=>setApplicationId(succeeded[0]!.applicationId)}>查看招聘流程</Button>:null}
  </Space>}>
    <div className="candidate-action-content">
    {mode==='share'?<p>已选 {targets.length} 人。仅处理你有权结束筛选、且无进行中招聘流程的候选人。历史评分保留。</p>:null}
    {mode==='advance'?<>
      {!fixedJdVersionId?<JdSelector includeSourceJds disabled={busy||results.length>0} {...(jd?{value:jd.id}:{})} onChange={setJd} placeholder="选择推荐岗位"/>:null}
      {versions.error?<Alert occurrence={versions.error} type="error" title="岗位版本读取失败" action={<Button onClick={()=>void versions.refetch()}>重试</Button>}/>:null}
      {readiness.isFetching?<p role="status">正在核对岗位权限与招聘流程…</p>:null}
      {readiness.error?<Alert occurrence={readiness.error} type="error" showIcon title="无法核对招聘流程" description={actionError(readiness.error)} action={<Button onClick={()=>void readiness.refetch()}>重试流程检查</Button>}/>:null}
      {visibleReadiness?<RecruitmentPreparation compact value={visibleReadiness} onChanged={()=>readiness.refetch()}/>:null}
    </>:null}
    <div><label htmlFor="candidate-action-reason">{mode==='advance'?'人工推荐依据':'结束筛选原因'}</label>
    <Input.TextArea id="candidate-action-reason" value={reason} onChange={event=>setReason(event.target.value)} rows={2} maxLength={500} showCount disabled={busy||results.length>0}/></div>
    {error?<Alert occurrence={error} type="error" showIcon title={error}/>:null}
    {busy||results.length?<>
      <Progress status={failed.length?'exception':busy?'active':remaining.length?'normal':'success'} percent={Math.round(results.length/targets.length*100)} aria-label="候选人批量处理进度"/>
      <p role="status">已完成 {succeeded.length} 人 · 失败 {failed.length} 人 · 尚未处理 {targets.length-results.length} 人</p>
      {failed.length?<Alert type="warning" showIcon title={succeeded.length?'部分候选人未处理成功':'候选人未处理成功'} description={succeeded.length?'成功项已保存。请查看各项原因，修正后仅重试未完成项；重复推荐会回到已有流程。':'本次未完成任何候选人的处理。请查看各项原因，修正后重试；重复推荐会回到已有流程。'}/>:null}
    </>:null}
    <Table size="small" rowKey="candidateId" pagination={false} dataSource={targets.slice((page-1)*20,page*20)} scroll={{x:500}} columns={[
      {title:'候选人',width:150,render:(_,item:CandidateActionTarget)=><CandidateName id={item.candidateId}/>},
      {title:'处理结果',render:(_,item:CandidateActionTarget)=>{const result=byCandidate.get(item.candidateId);return result?<Space wrap><Tag color={result.status==='FAILED'?'red':'green'}>{result.status==='FAILED'?'未完成':result.status==='ALREADY_EXISTS'?'已处理':'成功'}</Tag><span>{result.message??(result.status==='FAILED'?'请重试':'已保存')}</span>{mode==='advance'&&targets.length>1&&result.applicationId?<Button size="small" type="link" onClick={()=>setApplicationId(result.applicationId)}>查看招聘流程</Button>:null}</Space>:<span>待处理</span>;}},
    ]}/>
    <nav className="candidate-action-pagination" aria-label="候选人分页"><Button disabled={page===1} onClick={()=>setPage(page-1)}>上一页</Button><Button disabled={page*20>=targets.length} onClick={()=>setPage(page+1)}>下一页</Button></nav>
    </div>
    <LazyApplicationDetailsDialog applicationId={applicationId} onClose={()=>setApplicationId(undefined)}/>
  </WorkspaceDialog>;
}

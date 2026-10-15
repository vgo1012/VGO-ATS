import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { jobLabel } from '../demand/jobLabel';
import { intakeIdentityReview, requiresAdminIdentityReview } from '../talent/intakeIdentityReview';
import { CompactPagination } from '../components/CompactPagination';
import { useAppFeedback } from '../components/useAppFeedback';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { CloseCircleFilled } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, DatePicker, Dropdown, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { dateRangeParams } from '../components/dateRange';
import { demandApi, type CursorPage, type JdSummary } from '../demand/api';
import { JdSelector } from '../demand/JdSelector';
import { UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';
import { importCurrentIntake, talentApi, type IdentityConflict, type IntakeBatch, type ResumeIntake } from '../talent/api';
import { ConfirmIntakeModal, ConflictResolutionModal } from '../talent/IntakeModals';
import { IntakeFailure } from '../talent/IntakeFailure';
import { IntakeOriginalResume } from '../talent/IntakeOriginalResume';
import { shouldPollIntakes } from '../talent/intakePolling';
import '../styles/settings-layout.css';

type Filters={query?:string;name?:string;from?:string;to?:string;duplicateContent?:boolean;problemStatus?:string;batchId?:string};
type FilterForm={query:string|undefined;name:string|undefined;status:string|undefined;issue:string|undefined;range:[Dayjs,Dayjs]|undefined};
const errorMessage=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';
const importStatuses=['PENDING','PROCESSING','IMPORTED'] as const;
const importStatusText:Record<ResumeIntake['importStatus'],string>={PENDING:'待入库',PROCESSING:'解析中',IMPORTED:'已入库'};
const importStatusColor:Record<ResumeIntake['importStatus'],string>={PENDING:'gold',PROCESSING:'blue',IMPORTED:'green'};
const legacyImportStatus:Record<string,ResumeIntake['importStatus']>={PENDING_IMPORT:'PENDING',IDENTITY_REVIEW_REQUIRED:'PENDING',READY_FOR_CONFIRMATION:'PENDING',FAILED_RETRYABLE:'PENDING',QUARANTINED:'PENDING',REJECTED:'PENDING',PARSING:'PROCESSING',COMPLETED:'IMPORTED',CONFIRMED:'IMPORTED'};
const scoreText=(value:ResumeIntake)=>{
  if(value.status==='PARSING'&&value.processingStatus==='FAILED_RETRYABLE')return '等待自动重试，完成后自动评分';
  const label=({QUEUED:'等待评分',WAITING_JD:'等待 JD 解析',WAITING_CONFIG:'等待评分配置',RUNNING:'正在评分',SUCCEEDED:'评分完成',FAILED_RETRYABLE:'评分失败，可重试',FAILED_FINAL:'评分失败'} as Record<string,string>)[value.scoreStatus??''];
  if(label)return label;
  if(value.status==='IDENTITY_REVIEW_REQUIRED'||value.status==='READY_FOR_CONFIRMATION')return '等待身份核对，暂未评分';
  if(value.status==='FAILED_RETRYABLE')return '导入失败，详见处理状态';
  if(value.status==='QUARANTINED')return '文件已隔离，无法评分';
  if(value.status==='REJECTED')return '收件已拒绝，无法评分';
  if(value.importStatus==='PENDING')return '等待启动解析，可重试导入';
  if(value.importStatus==='PROCESSING')return '正在解析，完成后自动评分';
  return '已入库，等待启动评分';
};
const cursorTrail=(value:string|null):Array<string|null>=>{if(!value)return [];try{const parsed:unknown=JSON.parse(value);if(!Array.isArray(parsed))return [];const result:Array<string|null>=[];for(const raw of parsed){const item:unknown=raw;if(item!==null&&typeof item!=='string')return [];result.push(item);}return result;}catch{return [];}};
const batchCountText=(batch:IntakeBatch)=>`${!['COMPLETED','COMPLETED_WITH_ERRORS'].includes(batch.status)?'上传中 · ':''}已入库 ${batch.completedCount} · 处理中 ${batch.processingCount} · 需处理 ${batch.attentionCount} · 已忽略 ${batch.ignoredCount}`;
const hasExplicitResolution=(conflict:IdentityConflict)=>conflict.status==='RESOLVED'&&Boolean(conflict.reason?.trim())&&(
  (conflict.resolution==='CREATE_SEPARATE'&&!conflict.chosenCandidateId)||
  ((conflict.resolution==='USE_EXISTING'||conflict.resolution==='MERGE')&&Boolean(conflict.chosenCandidateId))
);

function LinkIntakeBatchJdModal({value,onClose,onSaved}:{value?:ResumeIntake;onClose:()=>void;onSaved:()=>Promise<void>}){
  const {message}=useAppFeedback(); const [jd,setJd]=useState<JdSummary>(); const [jdVersionId,setJdVersionId]=useState<string>();
  const versions=useQuery({queryKey:['demand','jd-versions-by-id',jd?.id],queryFn:()=>demandApi.jdVersions(jd!.id),enabled:Boolean(value&&jd)});
  const selectedJdVersionId=jdVersionId??versions.data?.items.find(item=>item.versionNo===jd?.currentVersionNo)?.id;
  const link=useMutation({mutationFn:()=>talentApi.linkBatchJd(value!.batchId,selectedJdVersionId!),onSuccess:async()=>{message.success('JD 已关联，后续处理将自动继续');await onSaved();setJd(undefined);setJdVersionId(undefined);onClose();},onError:error=>message.error(errorMessage(error))});
  const close=()=>{if(link.isPending)return;setJd(undefined);setJdVersionId(undefined);onClose();};
  return <Modal centered width={560} rootClassName="workspace-dialog-root" className="workspace-dialog" transitionName="workspace-dialog-motion" maskTransitionName="workspace-mask-motion" title="关联 JD" open={Boolean(value)} okText="确认关联" cancelText="取消" confirmLoading={link.isPending} okButtonProps={{disabled:!selectedJdVersionId||versions.isFetching}} onOk={()=>link.mutate()} onCancel={close} destroyOnHidden>
    <Space orientation="vertical" size="middle" style={{width:'100%'}}>
      <Typography.Text>请选择这批历史收件实际对应的岗位与版本。</Typography.Text>
      <JdSelector {...(jd?{value:jd.id}:{})} onChange={selected=>{setJd(selected);setJdVersionId(undefined);}} placeholder="搜索并选择 JD" disabled={link.isPending}/>
      <Select aria-label="关联的 JD 版本" placeholder="选择 JD 版本" value={selectedJdVersionId} loading={versions.isFetching} disabled={!jd||link.isPending} onChange={setJdVersionId} options={(versions.data?.items??[]).map(item=>({value:item.id,label:`V${item.versionNo} · ${item.title}`}))}/>
      {versions.error?<Alert occurrence={versions.error} type="error" showIcon title="JD 版本加载失败" description={errorMessage(versions.error)} action={<Button size="small" onClick={()=>void versions.refetch()}>重试</Button>}/>:null}
      <Alert type="warning" showIcon title="关联后不可改绑" description="同一批次的全部收件将使用该 JD 版本；已完成解析的收件会自动继续评分。"/>
    </Space>
  </Modal>;
}

export function IntakeValidationPage({embedded=false}:{embedded?:boolean}){
  const {actor}=useSession(); const {message}=useAppFeedback(); const queryClient=useQueryClient(); const [params,setSearchParams]=useSearchParams(); const [form]=Form.useForm<FilterForm>();
  const setParams=useCallback((next:URLSearchParams,options?:{replace?:boolean})=>setSearchParams(current=>{
    const merged=new URLSearchParams(current);
    for(const key of ['query','name','status','importStatus','issue','from','to','batchId','cursor','page','trail']){
      const value=next.get(key);if(value===null)merged.delete(key);else merged.set(key,value);
    }
    return merged;
  },options),[setSearchParams]);
  const [pageSize,setPageSize]=useState(20);
  const [batchCursor,setBatchCursor]=useState<string>(); const [batchTrail,setBatchTrail]=useState<Array<string|undefined>>([]);
  const canManage=Boolean(actor?.superAdmin||actor?.permissions.includes('talent.manage'));
  const canRunMatching=Boolean(actor?.superAdmin||actor?.permissions.includes('matching.run'));
  const cursor=params.get('cursor')??undefined; const page=Math.max(1,Number(params.get('page')??1)||1); const legacyStatus=params.get('status')??undefined; const selectedImportStatus=params.get('importStatus')??(legacyStatus?legacyImportStatus[legacyStatus]:undefined)??'PENDING'; const importStatus=selectedImportStatus==='ALL'?undefined:selectedImportStatus; const issue=params.get('issue')??undefined; const query=params.get('query')??undefined; const name=params.get('name')??undefined; const from=params.get('from')??undefined; const to=params.get('to')??undefined; const batchId=params.get('batchId')??undefined;
  const problemStatus=issue&&['PROBLEM','IDENTITY_REVIEW','FAILED','MISSING_JD'].includes(issue)?issue:undefined;
  const filters:Filters={...(query?{query}:{}),...(name?{name}:{}),...(from?{from}:{}),...(to?{to}:{}),...(issue==='DUPLICATE_CONTENT'?{duplicateContent:true}:{}),...(problemStatus?{problemStatus}:{}),...(batchId?{batchId}:{})};
  const trail=cursorTrail(params.get('trail')); const [confirming,setConfirming]=useState<ResumeIntake>(); const [resolving,setResolving]=useState<IdentityConflict>(); const [linking,setLinking]=useState<ResumeIntake>(); const [recalculating,setRecalculating]=useState<ResumeIntake>();
  // 仅供弹窗展示；提交时 confirmCurrentIntake 会重新读取服务端收件与版本。
  const confirmResolvedIntake=(value:ResumeIntake)=>setConfirming({...value,status:'READY_FOR_CONFIRMATION',failureCode:null});
  const intakes=useQuery({queryKey:['talent','intakes',importStatus,cursor,filters,pageSize],queryFn:()=>talentApi.intakes(importStatus,cursor,filters,pageSize),enabled:Boolean(batchId)&&!legacyStatus,refetchInterval:query=>shouldPollIntakes(query.state.data?.items,document.visibilityState),refetchIntervalInBackground:false}); const channels=useQuery({queryKey:['talent','channels'],queryFn:talentApi.channels});
  const batches=useQuery({queryKey:['talent','intake-batches',batchCursor],queryFn:()=>talentApi.batches(batchCursor),enabled:!batchId,retry:false,refetchInterval:query=>document.visibilityState==='visible'&&query.state.data?.items.some(value=>value.parsingCount>0)?3000:false});
  const selectedBatch=useQuery({queryKey:['talent','intake-batch',batchId],queryFn:()=>talentApi.batch(batchId!),enabled:Boolean(batchId),retry:false,refetchInterval:query=>document.visibilityState==='visible'&&query.state.data?.parsingCount?3000:false});
  const total=useQuery({queryKey:['talent','intake-count',actor,importStatus,filters],queryFn:({signal})=>talentApi.countIntakes(importStatus,filters,signal),enabled:Boolean(batchId)&&!legacyStatus,retry:false,refetchInterval:()=>shouldPollIntakes(intakes.data?.items,document.visibilityState),refetchIntervalInBackground:false});
  const items=intakes.data?.items??[]; const intakeIds=items.map(item=>item.id);
  const reviewIntakeIds=items.filter(item=>(item.status==='IDENTITY_REVIEW_REQUIRED'||item.status==='READY_FOR_CONFIRMATION')&&item.failureCode==='IDENTITY_CONFLICT_REQUIRES_REVIEW').map(item=>item.id);
  const conflicts=useQuery({queryKey:['talent','conflicts','OPEN',intakeIds],queryFn:()=>talentApi.conflicts(intakeIds),enabled:Boolean(batchId)&&canManage&&intakeIds.length>0});
  const resolvedConflicts=useQuery({queryKey:['talent','conflicts','RESOLVED',reviewIntakeIds],queryFn:()=>talentApi.conflicts(reviewIntakeIds,'RESOLVED'),enabled:Boolean(batchId)&&canManage&&reviewIntakeIds.length>0});
  const conflictByIntake=new Map((conflicts.data?.items??[]).filter(conflict=>conflict.status==='OPEN').map(conflict=>[conflict.intakeId,conflict]));
  const conflictStateIncomplete=conflicts.isPending||conflicts.isFetching||Boolean(conflicts.error)||!conflicts.data||conflicts.data.hasMore;
  const resolvedStateIncomplete=resolvedConflicts.isPending||resolvedConflicts.isFetching||Boolean(resolvedConflicts.error)||!resolvedConflicts.data||resolvedConflicts.data.hasMore;
  const recoverableIdentityReviewIds=new Set<string>();
  if(!conflictStateIncomplete&&!resolvedStateIncomplete){
    for(const id of reviewIntakeIds){
      const decisions=resolvedConflicts.data.items.filter(conflict=>conflict.intakeId===id);
      if(!conflictByIntake.has(id)&&decisions.length>0&&decisions.every(hasExplicitResolution))recoverableIdentityReviewIds.add(id);
    }
  }
  const refresh=async()=>{await Promise.all([queryClient.invalidateQueries({queryKey:['talent','intakes']}),queryClient.invalidateQueries({queryKey:['talent','intake-count']}),queryClient.invalidateQueries({queryKey:['talent','intake-batches']}),queryClient.invalidateQueries({queryKey:['talent','intake-batch']}),queryClient.invalidateQueries({queryKey:['talent','conflicts']}),queryClient.invalidateQueries({queryKey:['talent','candidates']}),queryClient.invalidateQueries({queryKey:['talent','pool-statuses']})]);};
  const action=useMutation({
    mutationFn:({item,type}:{item:ResumeIntake;type:'ignore'|'retry'|'reparse'|'retryScore'|'recalculateScore'|'importIntake'})=>type==='importIntake'?importCurrentIntake(item).then(result=>result.intake):talentApi[type](item),
    onSuccess:async(_,{item,type})=>{
      if(type==='ignore'){
        queryClient.setQueriesData<CursorPage<ResumeIntake>>({queryKey:['talent','intakes']},current=>current?{...current,items:current.items.filter(value=>value.id!==item.id)}:current);
      }
      message.success(type==='ignore'?'收件已删除并移出列表':type==='importIntake'?'已开始解析并入库':type==='retryScore'?'已重新提交自动评分':type==='recalculateScore'?'已提交岗位评分更新':type==='reparse'?'已重新提交解析，请稍后查看结果':'已重新提交入库处理');await refresh();
    },
    onError:error=>{message.error(errorMessage(error));},
  });
  const selectionLocked=action.isPending;
  const submit=(values:FilterForm)=>{const next=new URLSearchParams();if(batchId)next.set('batchId',batchId);if(values.query?.trim())next.set('query',values.query.trim());if(values.name?.trim())next.set('name',values.name.trim());next.set('importStatus',values.status??'ALL');if(values.issue)next.set('issue',values.issue);if(values.range){const range=dateRangeParams(values.range);next.set('from',range.from);next.set('to',range.to);}setParams(next);};
  const reset=()=>{form.setFieldsValue({query:undefined,name:undefined,status:'PENDING',issue:undefined,range:undefined});const next=new URLSearchParams();if(batchId)next.set('batchId',batchId);setParams(next);};
  const selectBatch=(id?:string)=>{const next=new URLSearchParams(params);if(id)next.set('batchId',id);else next.delete('batchId');for(const key of ['query','name','status','importStatus','issue','from','to','cursor','page','trail'])next.delete(key);setParams(next);};
  const move=(nextCursor:string)=>{const next=new URLSearchParams(params);next.set('trail',JSON.stringify([...trail,cursor??null]));next.set('cursor',nextCursor);next.set('page',String(page+1));setParams(next);};
  const previous=()=>{const prior=trail.at(-1);const next=new URLSearchParams(params);if(prior)next.set('cursor',prior);else next.delete('cursor');const remaining=trail.slice(0,-1);if(remaining.length)next.set('trail',JSON.stringify(remaining));else next.delete('trail');next.set('page',String(Math.max(1,page-1)));setParams(next);};
  const goToPage=(targetPage:number)=>{const prior=trail[targetPage-1];const next=new URLSearchParams(params);if(prior)next.set('cursor',prior);else next.delete('cursor');const remaining=trail.slice(0,targetPage-1);if(remaining.length)next.set('trail',JSON.stringify(remaining));else next.delete('trail');next.set('page',String(targetPage));setParams(next);};
  const initialRange=from&&to?[dayjs(from).tz('Asia/Shanghai'),dayjs(to).subtract(1,'millisecond').tz('Asia/Shanghai')] as [Dayjs,Dayjs]:undefined;
  useEffect(()=>{
    const legacy=params.get('status');if(!legacy)return;
    const next=new URLSearchParams(params);for(const key of ['status','cursor','page','trail'])next.delete(key);
    if(legacyImportStatus[legacy])next.set('importStatus',legacyImportStatus[legacy]);
    setParams(next,{replace:true});
  },[params,setParams]);
  useEffect(()=>{form.setFieldsValue({query,name,status:selectedImportStatus,issue,range:from&&to?[dayjs(from).tz('Asia/Shanghai'),dayjs(to).subtract(1,'millisecond').tz('Asia/Shanghai')]:undefined});},[form,from,selectedImportStatus,issue,name,query,to]);
  return <div className="workspace-page talent-workspace-page settings-list-page intake-validation-page">
    {!embedded?<section className="ledger-heading talent-heading"><Typography.Title level={1}>解析校验</Typography.Title></section>:null}
    {!batchId?<Card className="intake-batch-card" title="上传任务分组">
      {batches.error?<Alert occurrence={batches.error} type="error" showIcon title="上传任务加载失败" action={<Button onClick={()=>void batches.refetch()}>重试</Button>}/>:null}
      <Typography.Text type="secondary">选择一个任务，查看这批简历的解析结果与待处理事项。</Typography.Text>
      <div className="intake-batch-list" role="list" aria-label="上传任务">
        {(batches.data?.items??[]).map(batch=><div key={batch.id} role="listitem"><button className="intake-batch-item" type="button" aria-pressed={batch.id===batchId} onClick={()=>selectBatch(batch.id)}>
          <strong>任务 #{batch.id.slice(-6)} · {formatDateTime(batch.createdAt)}</strong><span>{channels.data?.items.find(value=>value.id===batch.sourceChannelId)?.name??'来源暂不可用'} · 共 {batch.totalCount} 份</span>
          <span>归属：<UserName id={batch.defaultOwnerUserId}/></span>
          <span>首次上传成功 {batch.succeededCount} · 首次上传失败 {batch.failedCount}</span><span>{batchCountText(batch)}</span><span className="intake-batch-link">查看解析校验 →</span>
        </button></div>)}
        {!batches.isPending&&!batches.data?.items.length?<Typography.Text type="secondary">暂无上传任务。</Typography.Text>:null}
      </div>
      <div className="intake-batch-pagination"><Button disabled={!batchTrail.length} onClick={()=>{setBatchCursor(batchTrail.at(-1));setBatchTrail(batchTrail.slice(0,-1));}}>上一组</Button><span>第 {batchTrail.length+1} 组</span><Button disabled={!batches.data?.hasMore||!batches.data.nextCursor} onClick={()=>{setBatchTrail([...batchTrail,batchCursor]);setBatchCursor(batches.data!.nextCursor!);}}>下一组</Button></div>
    </Card>:<div className="intake-detail-heading">
      <Button onClick={()=>selectBatch()}>返回任务分组</Button>
      <div>
        <Typography.Title level={4}>任务 #{batchId.slice(-6)} 的解析校验</Typography.Title>
        {selectedBatch.error?<Alert occurrence={selectedBatch.error} type="warning" showIcon title="当前任务信息暂不可用" action={<Button onClick={()=>void selectedBatch.refetch()}>重试</Button>}/>:null}
        {selectedBatch.data?<Typography.Text type="secondary">{formatDateTime(selectedBatch.data.createdAt)} 上传 · {selectedBatch.data.totalCount} 份 · 首次上传成功 {selectedBatch.data.succeededCount} · 首次上传失败 {selectedBatch.data.failedCount} · {batchCountText(selectedBatch.data)}</Typography.Text>:null}
      </div>
    </div>}
    {batchId?<>
    <Card className="intake-filter-card">
      <Form className="intake-filters" form={form} layout="inline" initialValues={{query:filters.query,name:filters.name,status:selectedImportStatus,issue,range:initialRange}} onFinish={submit}>
        <Form.Item name="query" label="关键词"><Input allowClear={{clearIcon:<CloseCircleFilled aria-label="清空关键词"/>}} placeholder="文件名或收件关键词"/></Form.Item><Form.Item name="name" label="姓名"><Input allowClear={{clearIcon:<CloseCircleFilled aria-label="清空姓名"/>}} placeholder="已识别候选人姓名"/></Form.Item><Form.Item name="status" label="入库状态"><Select allowClear={{label:'清空入库状态'}} options={[{value:'ALL',label:'全部状态'},...importStatuses.map(value=>({value,label:importStatusText[value]}))]}/></Form.Item><Form.Item name="issue" label="问题状态"><Select allowClear={{label:'清空问题状态'}} options={[{value:'PROBLEM',label:'全部问题'},{value:'IDENTITY_REVIEW',label:'待核对身份'},{value:'DUPLICATE_CONTENT',label:'同内容重复收件'},{value:'FAILED',label:'导入或评分失败'},{value:'MISSING_JD',label:'待关联 JD'}]}/></Form.Item><Form.Item name="range" label="收件日期"><DatePicker.RangePicker allowClear/></Form.Item><Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={reset}>重置</Button></Space></Form.Item>
      </Form>
    </Card>
    {intakes.error?<Alert occurrence={intakes.error} type="error" showIcon title={errorMessage(intakes.error)} description="筛选条件已保留，可直接重试。" action={<Button loading={intakes.isFetching} onClick={()=>void intakes.refetch()}>重试</Button>}/>:null}
    {channels.error?<Alert occurrence={channels.error} type="error" showIcon title={errorMessage(channels.error)} description="来源名称暂不可用，其余收件信息仍可处理。" action={<Button loading={channels.isFetching} onClick={()=>void channels.refetch()}>重试来源渠道</Button>}/>:null}
    {conflicts.error?<Alert occurrence={conflicts.error} type="warning" showIcon title="身份冲突状态暂不可用" description="请重试后再处理可能存在身份冲突的收件。" action={<Button loading={conflicts.isFetching} onClick={()=>void conflicts.refetch()}>重试冲突状态</Button>}/>:null}
    {conflicts.data?.hasMore?<Alert type="warning" showIcon title="身份冲突结果未完整加载" description="部分冲突尚未返回，请重新查询或联系管理员核验，未标注不代表没有冲突。"/>:null}
    {resolvedConflicts.error?<Alert occurrence={resolvedConflicts.error} type="warning" showIcon title="已解决身份决定暂不可用" description="无法确认历史身份冲突已处理，请重试或联系管理员核验。" action={<Button loading={resolvedConflicts.isFetching} onClick={()=>void resolvedConflicts.refetch()}>重试身份决定</Button>}/>:null}
    {resolvedConflicts.data?.hasMore?<Alert type="warning" showIcon title="已解决身份决定结果未完整加载" description="当前页仍有身份决定未返回，确认入口已暂时阻断，请缩小筛选范围后重试。"/>:null}
    <Card className="paged-panel" title="当前任务的解析收件" {...(embedded?{style:{minHeight:0}}:{})}>
      <Table<ResumeIntake> rowKey="id" loading={intakes.isPending} pagination={false} dataSource={items} onHeaderRow={() => scrollableTableHeader('简历导入任务表头，可横向滚动')} scroll={{x:1100}} locale={{emptyText:'当前条件下暂无导入任务'}} columns={[
        {title:'收件文件',width:260,render:(_:unknown,value)=><div className="intake-file"><Tooltip title={value.originalFileName} trigger={['hover','focus']}><strong className="intake-file-name" tabIndex={0}>{value.originalFileName}</strong></Tooltip><Typography.Text type="secondary" className="intake-file-time">{formatDateTime(value.createdAt)}</Typography.Text>{value.duplicateContent?<Tag color="gold">同内容重复收件</Tag>:null}</div>},
        {title:'关联 JD / 评分',width:185,render:(_:unknown,value)=>value.jdVersionId?<Space orientation="vertical" size={0}><span>{jobLabel(value.jdTitle??'岗位名称暂不可用', value.jdCode)}</span><small>{scoreText(value)}</small></Space>:<Space orientation="vertical" size={0}><span>未关联 JD</span><small>{value.status==='COMPLETED'?'简历解析已完成，关联后将自动评分':'历史收件缺少 JD，请核对后继续入库'}</small></Space>},
        {title:'处理状态 / 原因',width:235,render:(_:unknown,value)=>{
          const identityReview=value.status==='IDENTITY_REVIEW_REQUIRED'||value.status==='READY_FOR_CONFIRMATION';
          const resolvedDecisionReady=recoverableIdentityReviewIds.has(value.id);
          const failed=['FAILED_RETRYABLE','QUARANTINED','REJECTED'].includes(value.status);
          const autoRetry=value.status==='PARSING'&&value.processingStatus==='FAILED_RETRYABLE';
          const explanation=resolvedDecisionReady?{reason:'身份冲突已人工处理',advice:'请核对本次简历与候选人姓名、联系方式后确认入库。'}:identityReview?intakeIdentityReview(value,conflictByIntake.get(value.id)):null;
          return <Space orientation="vertical" size={4} style={{overflowWrap:'anywhere'}}>
            {failed?<Tag color="red">{value.status==='FAILED_RETRYABLE'?'导入失败':displayEnum(value.status)}</Tag>:autoRetry?<Tag color="blue">等待自动重试</Tag>:value.jdVersionId?<Tag color={identityReview?'gold':importStatusColor[value.importStatus]}>{identityReview?(resolvedDecisionReady||value.status==='READY_FOR_CONFIRMATION'&&!value.failureCode?'待确认入库':'待核对身份'):importStatusText[value.importStatus]}</Tag>:<Tag color="gold">待关联 JD</Tag>}
            {explanation?<><span>{explanation.reason}</span><Typography.Text type="secondary">{explanation.advice}</Typography.Text></>:null}
            <IntakeFailure value={value}/>
          </Space>;
        }},
        {title:'来源 / 归属 / 处理人',width:200,render:(_:unknown,value)=><small>{channels.data?.items.find(channel=>channel.id===value.sourceChannelId)?.name??'来源暂不可用'}<br/>归属：<UserName id={value.ownerUserId}/><br/>处理：{value.handledByUserId?<UserName id={value.handledByUserId}/>:'尚未处理'}</small>},
        {title:'操作', className: 'table-action-cell',width:220,render:(_:unknown,value)=>{const conflict=conflictByIntake.get(value.id);const identityReview=value.status==='IDENTITY_REVIEW_REQUIRED'||value.status==='READY_FOR_CONFIRMATION';const canReparse=value.status==='IDENTITY_REVIEW_REQUIRED'&&value.failureCode==='IDENTITY_REVIEW_REQUIRED'&&value.processingStatus==='SUCCEEDED'&&value.scanStatus==='CLEAN'&&!value.candidateId&&!value.resumeVersionId;const blocked=value.status==='QUARANTINED'||value.status==='REJECTED';const processing=value.importStatus==='PROCESSING';return <Space wrap><IntakeOriginalResume value={value}/>{canManage&&(conflict&&requiresAdminIdentityReview(conflict)?<Typography.Text type="secondary">联系负责人核验</Typography.Text>:!value.jdVersionId?<Button size="small" type="primary" disabled={selectionLocked} aria-label="关联 JD" onClick={()=>setLinking(value)}>关联 JD</Button>:conflict?<Button size="small" danger disabled={selectionLocked} onClick={()=>setResolving(conflict)}>处理身份冲突</Button>:identityReview&&value.failureCode==='IDENTITY_CONFLICT_REQUIRES_REVIEW'?recoverableIdentityReviewIds.has(value.id)?<Button size="small" type="primary" disabled={selectionLocked||intakes.isFetching} onClick={()=>confirmResolvedIntake(value)}>确认入库</Button>:<Button size="small" disabled={selectionLocked||conflicts.isFetching||resolvedConflicts.isFetching||intakes.isFetching} onClick={()=>void refresh()}>刷新核对状态</Button>:identityReview?<Button size="small" type="primary" disabled={selectionLocked||conflictStateIncomplete} onClick={()=>setConfirming(value)}>{value.status==='READY_FOR_CONFIRMATION'&&!value.failureCode?'确认入库':'核对异常身份'}</Button>:value.status==='FAILED_RETRYABLE'?<Button size="small" disabled={selectionLocked} onClick={()=>action.mutate({item:value,type:'retry'})}>重试导入</Button>:value.status==='PENDING_IMPORT'&&!blocked?<Button size="small" disabled={selectionLocked||conflictStateIncomplete||value.scanStatus!=='CLEAN'} onClick={()=>action.mutate({item:value,type:'importIntake'})}>重试导入</Button>:null)}{canManage&&canReparse&&!conflict?<Button size="small" disabled={selectionLocked||conflictStateIncomplete} onClick={()=>action.mutate({item:value,type:'reparse'})}>重新解析</Button>:null}{canManage&&value.scoreStatus==='FAILED_RETRYABLE'?<Button size="small" disabled={selectionLocked} onClick={()=>action.mutate({item:value,type:'retryScore'})}>重试评分</Button>:null}{canManage&&canRunMatching&&value.status==='COMPLETED'&&value.scoreStatus==='SUCCEEDED'&&Boolean(value.scoreRunId&&value.jdVersionId&&value.resumeVersionId&&value.candidateId)?<Dropdown trigger={['click']} menu={{items:[{key:'recalculate',label:'更新岗位评分'}],onClick:()=>setRecalculating(value)}}><Button size="small" aria-label="更多操作" disabled={selectionLocked}>更多</Button></Dropdown>:null}{canManage&&value.status!=='IGNORED'?<Popconfirm title="确认删除此收件？" description="仅移出收件列表，已入库的候选人档案、简历版本和来源历史仍保留。" okText="确认删除" cancelText="取消" okButtonProps={{danger:true}} disabled={selectionLocked||processing} onConfirm={()=>action.mutateAsync({item:value,type:'ignore'})}><Button size="small" danger disabled={selectionLocked||processing} title={processing?'解析中，请完成后再删除':undefined}>删除</Button></Popconfirm>:null}</Space>;}},
      ]}/>
      <CompactPagination label="导入任务分页" page={page} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);const next=new URLSearchParams(params);for(const key of ['cursor','page','trail'])next.delete(key);setParams(next);}} loading={intakes.isFetching}
        {...(total.data===undefined?{totalStatus:total.isError?'error' as const:'pending' as const}:{total:total.data})} onRetryTotal={()=>void total.refetch()}
        hasPrevious={page > 1 && trail.length > 0} hasNext={Boolean(intakes.data?.hasMore && intakes.data.nextCursor)}
        onPrevious={previous} onNext={()=>{if(intakes.data?.nextCursor)move(intakes.data.nextCursor);}}
        {...(trail.length===page-1?{maxPage:page,onPageChange:(targetPage:number)=>{if(targetPage<page)goToPage(targetPage);}}:{})} />
    </Card>
    </>:null}
    <Modal centered width={560} rootClassName="workspace-dialog-root" className="workspace-dialog" transitionName="workspace-dialog-motion" maskTransitionName="workspace-mask-motion" title="更新岗位评分" open={Boolean(recalculating)} okText="更新评分" okButtonProps={{loading:action.isPending}} onCancel={()=>setRecalculating(undefined)} onOk={()=>{if(recalculating)void action.mutateAsync({item:recalculating,type:'recalculateScore'}).then(()=>setRecalculating(undefined)).catch(()=>undefined);}}>
      <p>仅在这份简历重新解析完成且事实已变化后更新关联岗位的公司默认评分。更新期间，这条收件的默认评分暂不可用；原评分记录保留。</p>
    </Modal>
    <ConfirmIntakeModal {...(confirming?{value:confirming}:{})} onClose={()=>setConfirming(undefined)} onSaved={refresh}/><ConflictResolutionModal {...(resolving?{value:resolving}:{})} onClose={()=>setResolving(undefined)} onSaved={refresh}/><LinkIntakeBatchJdModal {...(linking?{value:linking}:{})} onClose={()=>setLinking(undefined)} onSaved={refresh}/>
  </div>;
}

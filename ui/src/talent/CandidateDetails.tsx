import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { useAppFeedback } from '../components/useAppFeedback';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { factDate, formatResumeDate } from './resumeDates';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Descriptions, Empty, Progress, Skeleton, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { FileDownloadButton } from '../components/FileDownloadButton';
import type { DetailOrigin } from '../components/detailMotion';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { canCorrectResumeFacts } from '../auth/permissions';
import { taskApi, taskPollingInterval } from '../tasks/task';
import { talentApi, type ResumeFactBundle } from './api';
import { CandidateBlacklistDetails } from './CandidateBlacklistDetails';
import { CandidateInterviewMaterials } from './CandidateInterviewMaterials';
import { CandidateRecruitmentHistoryPanel } from './CandidateRecruitmentHistory';
import '../recruitment/ApplicationDetailsDialog.css';
import './candidate-details.css';
import { CompanyBlacklistPanel } from './CompanyBlacklistPanel';
import { ResumeReviewPanel } from './ResumeReviewPanel';
import { ResumeFactCorrectionForm } from './ResumeFactCorrectionForm';
import { commandFromFacts, refreshCorrectedFacts, type ResumeFactCorrectionValues } from './resumeFactCorrection';

import { TechnicalDetails, UserName } from '../display/BusinessReferences';
import { displayEnum, formatDateTime } from '../display/labels';

const accessDenied=(error:unknown):error is ApiProblemError=>error instanceof ApiProblemError&&[401,403].includes(error.problem.status);

const errorMessage=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:'操作失败，请稍后重试。';

export function CandidateDialog({candidateId,open,motionFrom,canDownload,onClose,compact=false,sharedLibrary=false}:{sharedLibrary?:boolean;compact?:boolean;candidateId?:string;open:boolean;motionFrom:DetailOrigin|undefined;canDownload:boolean;onClose:()=>void}) {
  const candidate=useQuery({queryKey:['talent','candidate',candidateId],queryFn:()=>talentApi.candidate(candidateId!),enabled:Boolean(candidateId)&&open});
  const [busy,setBusy]=useState(false);
  // 关闭后仍传递 active=false，避免 Modal 冻结子内容而保留查询的启用状态。
  return <WorkspaceDialog forceRender size="wide" title={accessDenied(candidate.error)?'候选人详情':candidate.data?.fullName??'候选人详情'} open={open} motionFrom={motionFrom} onCancel={onClose} footer={null} confirmLoading={busy}>
    {candidateId?<CandidateDetails key={candidateId} candidateId={candidateId} active={open} compact={compact} sharedLibrary={sharedLibrary} canDownload={canDownload} onBusyChange={setBusy}/>:null}
  </WorkspaceDialog>;
}

function jsonList(value:string):string[]{try{const parsed=JSON.parse(value) as unknown;return Array.isArray(parsed)?parsed.map(String):[];}catch{return [];}}
export function CandidateDetails({candidateId,active=true,canDownload,onBusyChange,compact=false,sharedLibrary=false}:{sharedLibrary?:boolean;compact?:boolean;candidateId:string;active?:boolean;canDownload:boolean;onBusyChange?:(busy:boolean)=>void}){
  const canCorrect=canCorrectResumeFacts(useSession().actor);
  const queryClient=useQueryClient();const {message}=useAppFeedback();const [selectedVersionId,setSelectedVersionId]=useState<string>();const [submittedTask,setSubmittedTask]=useState<{versionId:string;taskId:string}>();const [correctionOpen,setCorrectionOpen]=useState(false);const [correctionBase,setCorrectionBase]=useState<ResumeFactBundle>();
  const candidate=useQuery({queryKey:['talent','candidate',candidateId],queryFn:()=>talentApi.candidate(candidateId),enabled:Boolean(candidateId)&&active});
  const candidateDenied=accessDenied(candidate.error);
  const visibleCandidate=candidateDenied?undefined:candidate.data;
  const detailActive=active&&!candidateDenied;
  const versions=useInfiniteQuery({queryKey:['talent','resume-versions',candidateId,'history'],queryFn:({pageParam})=>talentApi.resumeVersions(candidateId,pageParam,20),initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,enabled:Boolean(candidateId)&&detailActive,retry:false});
  const versionsDenied=accessDenied(versions.error);
  const versionItems=versions.data?.pages.flatMap(page=>page.items)??[];
  const currentVersionId=selectedVersionId??versionItems[0]?.id;
  const latestTask=useQuery({queryKey:['tasks','resume-latest',currentVersionId],queryFn:()=>taskApi.latestResume(currentVersionId!).then(value=>value??null),enabled:Boolean(currentVersionId)&&detailActive,retry:false});
  const taskId=submittedTask?.versionId===currentVersionId?submittedTask?.taskId:latestTask.data?.taskId;
  const source=useQuery({queryKey:['talent','resume-source',candidateId,currentVersionId],queryFn:()=>talentApi.resumeSource(candidateId,currentVersionId!),enabled:Boolean(currentVersionId)&&detailActive&&(!compact||sharedLibrary),staleTime:60_000,retry:false});
  const sourceDenied=accessDenied(source.error);
  const visibleSource=sourceDenied?undefined:source.data;
  const task=useQuery({queryKey:['tasks',taskId],queryFn:()=>taskApi.get(taskId!),enabled:Boolean(taskId)&&detailActive,refetchInterval:query=>taskPollingInterval(query.state.data)});
  const facts=useQuery({queryKey:['talent','resume-facts',currentVersionId],queryFn:()=>talentApi.resumeFacts(currentVersionId!),enabled:Boolean(currentVersionId)&&detailActive,retry:false,refetchInterval:detailActive?taskPollingInterval(task.data):false});
  const factsDenied=accessDenied(facts.error);
  const visibleFacts=factsDenied?undefined:facts.data;
  if(correctionOpen&&(candidateDenied||factsDenied)){setCorrectionOpen(false);setCorrectionBase(undefined);}
  const parse=useMutation({mutationFn:()=>talentApi.parseResume(currentVersionId!),onSuccess:value=>{setSubmittedTask({versionId:value.resumeVersionId??currentVersionId!,taskId:value.taskId});void queryClient.invalidateQueries({queryKey:['tasks','resume-latest',currentVersionId]});message.success('简历解析任务已提交');}});
  const controlTask=async(action:'cancel'|'retry')=>{if(!taskId)return;try{await taskApi[action](taskId);await queryClient.invalidateQueries({queryKey:['tasks',taskId]});message.success(action==='cancel'?'已请求取消任务':'任务已重新排队');}catch(error){message.error(errorMessage(error));}};
  const correct=useMutation({mutationFn:(values:ResumeFactCorrectionValues)=>{if(!correctionBase)throw new Error('请重新打开更正');const candidateError=queryClient.getQueryState(['talent','candidate',candidateId])?.error;if(accessDenied(candidateError))throw candidateError;const error=queryClient.getQueryState(['talent','resume-facts',correctionBase.snapshot.resumeVersionId])?.error;if(accessDenied(error))throw error;return talentApi.correctFacts({resumeVersionId:correctionBase.snapshot.resumeVersionId,baseFactSnapshotId:correctionBase.snapshot.id,reason:values.reason,facts:commandFromFacts(correctionBase,values)});},onSuccess:async value=>{await refreshCorrectedFacts(queryClient,candidateId,correctionBase!.snapshot.resumeVersionId);setCorrectionOpen(false);message.success(`更正已保存（第 ${value.snapshotVersion} 版）`);}});
  const openCorrection=()=>{const value=visibleFacts;if(!value||candidateDenied)return;correct.reset();setCorrectionBase(value);setCorrectionOpen(true);};
  const activeTask=task.data&&['QUEUED','RUNNING','CANCELLING'].includes(task.data.status);
  const submitting=useRef(false);
  const contentRef=useRef<HTMLDivElement>(null);
  useEffect(()=>{if(detailActive&&task.data?.status==='SUCCEEDED'&&currentVersionId){void queryClient.invalidateQueries({queryKey:['talent','resume-facts',currentVersionId]});void queryClient.invalidateQueries({queryKey:['talent','candidate-resume-summaries']});}},[task.data?.status,currentVersionId,queryClient,detailActive]);
  useEffect(()=>{onBusyChange?.(correct.isPending);return()=>onBusyChange?.(false);},[correct.isPending,onBusyChange]);
  useEffect(()=>{if(active){contentRef.current?.querySelector<HTMLElement>('[data-detail-focus]')?.focus({preventScroll:true});contentRef.current?.closest('.ant-modal-body')?.scrollTo({top:0});}},[active,correctionOpen]);
  return <div ref={contentRef} className={`candidate-detail-content${compact?' candidate-detail-compact':''}`}>
    {candidate.error?<Alert occurrence={candidate.error} type={candidateDenied?"info":"error"} showIcon title={candidateDenied?"暂无该候选人的查看权限":errorMessage(candidate.error)} action={<Button loading={candidate.isFetching} disabled={candidate.isFetching} onClick={()=>void candidate.refetch({cancelRefetch:false})}>重试候选人详情</Button>}/>:null}
    {correctionOpen&&canCorrect&&!candidateDenied?<section aria-label="HR 更正"><div className="candidate-editor-heading"><Button icon={<ArrowLeftOutlined/>} disabled={correct.isPending} onClick={()=>setCorrectionOpen(false)}>返回候选人详情</Button><Typography.Title level={4} tabIndex={-1} data-detail-focus>HR 更正</Typography.Title></div>
{correct.error?<Alert occurrence={correct.error} type="error" showIcon title={errorMessage(correct.error)} action={correct.error instanceof ApiProblemError&&correct.error.problem.status===409?<Button loading={facts.isFetching} disabled={facts.isFetching||correct.isPending} onClick={()=>void facts.refetch().then(result=>{if(result.error){message.error(errorMessage(result.error));}else if(result.data){setCorrectionBase(result.data);correct.reset();}})}>放弃草稿并载入最新事实</Button>:null}/>:null}<Alert type="info" showIcon title="核对并修改资料后保存。系统保留原始简历及更正记录。"/>
      {correctionBase?<ResumeFactCorrectionForm key={correctionBase.snapshot.id} bundle={correctionBase} busy={correct.isPending} onCancel={()=>setCorrectionOpen(false)} onSubmit={values=>{if(submitting.current||candidateDenied||factsDenied)return;submitting.current=true;correct.mutate(values,{onSettled:()=>{submitting.current=false;}});}}/>:null}
    </section>:<><span className="sr-only" tabIndex={-1} data-detail-focus>候选人详情</span>
{visibleCandidate?<Space orientation="vertical" size={compact?12:18} style={{width:'100%'}}>
      <Card className="candidate-detail-summary" size="small" role="region" aria-label="关键资料" title="关键资料" extra={<Space wrap>{compact?<CandidateInterviewMaterials candidateId={candidateId} active={active} resumeVersionId={currentVersionId}/>:null}{canCorrect?<Button onClick={openCorrection} disabled={!visibleFacts||factsDenied}>HR 更正</Button>:null}</Space>}><div className={compact?"candidate-summary-grid":undefined}><Descriptions size="small" column={{xs:1,md:3}} items={[{key:'name',label:'姓名',children:(visibleFacts?visibleFacts.snapshot.fullName:visibleCandidate.fullName)??'未录入'},{key:'email',label:'邮箱',children:(visibleFacts?visibleFacts.snapshot.email:visibleCandidate.primaryEmail)??'未录入'},{key:'phone',label:'手机号',children:(visibleFacts?visibleFacts.snapshot.phone:visibleCandidate.primaryPhone)??'未录入'},{key:'age',label:'年龄',children:visibleFacts?.snapshot.age!=null?`${visibleFacts.snapshot.age} 岁`:'未录入'},{key:'gender',label:'性别',children:displayEnum(visibleFacts?.snapshot.gender,'未录入')},{key:'owner',label:'归属人',children:<UserName id={visibleCandidate.ownerUserId}/>}]}/>{compact?<CandidateBlacklistDetails candidateId={candidateId} active={active} sharedLibrary={sharedLibrary}/>:null}</div></Card>
      {!compact||sharedLibrary?<>
      {!compact?<CandidateBlacklistDetails candidateId={candidateId} active={active} sharedLibrary={sharedLibrary}/>:null}
      <CompanyBlacklistPanel candidateId={candidateId} active={active} sharedLibrary={sharedLibrary}/>
      <CandidateRecruitmentHistoryPanel candidateId={candidateId} active={active} sharedLibrary={sharedLibrary}/>
      <Typography.Title level={4}>简历版本</Typography.Title>{versions.error?<Alert occurrence={versions.error} type={versionsDenied?'info':'error'} showIcon title={versionsDenied?'暂无该候选人简历版本列表的查看权限':errorMessage(versions.error)} action={<Button loading={versions.isFetching} disabled={versions.isFetching} onClick={()=>void (versions.isFetchNextPageError&&!versionsDenied?versions.fetchNextPage({cancelRefetch:false}):versions.refetch({cancelRefetch:false}))}>重试简历版本</Button>}/>:null}
      {currentVersionId?<>
        {source.error?<Alert occurrence={source.error} type={sourceDenied?'info':'warning'} showIcon title={sourceDenied?'暂无该简历来源的查看权限':'简历来源读取失败'} action={<Button loading={source.isFetching} disabled={source.isFetching} onClick={()=>void source.refetch({cancelRefetch:false})}>重试简历来源</Button>}/>:null}
        <Descriptions size="small" column={{xs:1,md:3}} items={[{key:'source',label:'当前版本来源',children:visibleSource?.sourceChannelName??(source.isPending?'读取来源…':'来源暂不可用')},{key:'recruiter',label:'来源招聘人',children:visibleSource?<UserName id={visibleSource.sourceRecruiterUserId}/>:'—'},{key:'received',label:'收件时间',children:visibleSource?formatDateTime(visibleSource.receivedAt):'—'}]}/>
      </>:null}
      {!versionsDenied?<Table onHeaderRow={() => scrollableTableHeader("简历版本表头，可横向滚动")} rowKey="id" loading={versions.isPending} pagination={false} dataSource={versionItems} rowClassName={value=>value.id===currentVersionId?'ant-table-row-selected':''} onRow={value=>({onClick:()=>setSelectedVersionId(value.id)})} columns={[{title:'版本',dataIndex:'versionNo',render:(v:number,value:{id:string;originalFileName:string})=><Button size="small" type={value.id===currentVersionId?'primary':'default'} aria-label={`查看简历 V${v}`} aria-pressed={value.id===currentVersionId} onClick={event=>{event.stopPropagation();setSelectedVersionId(value.id);}}>V{v}</Button>},{title:'文件',dataIndex:'originalFileName'},{title:'时间',dataIndex:'createdAt',render:(v:string)=>formatDateTime(v)},{title:'操作', className: 'table-action-cell',render:(_:unknown,v:{id:string;originalFileName:string})=><Space>{canDownload?<FileDownloadButton size="small" download={()=>talentApi.downloadResume(v.id,v.originalFileName)}>受控下载</FileDownloadButton>:<Tag>无下载权限</Tag>}</Space>}]} scroll={{x:620}}/>:null}
      {!versionsDenied&&versions.data?<Space wrap><Typography.Text type="secondary" role="status">已加载 {versionItems.length} 个版本</Typography.Text>{versions.hasNextPage&&!versions.error?<Button aria-label="加载更多简历版本" loading={versions.isFetching} disabled={versions.isFetching} onClick={()=>void versions.fetchNextPage({cancelRefetch:false})}>加载更多简历版本</Button>:null}</Space>:null}
      </>:null}
      {compact&&!sharedLibrary&&versions.error?<Alert occurrence={versions.error} type="error" title="简历资料读取失败" action={<Button onClick={()=>void versions.refetch()}>重试简历资料</Button>}/>:null}
      {currentVersionId?<Card className="candidate-detail-analysis" title="AI 事实解析" extra={factsDenied?null:<Space><Button type="primary" loading={parse.isPending} disabled={Boolean(activeTask)||latestTask.isPending||Boolean(latestTask.error)||Boolean(taskId&&(task.isPending||task.error))} onClick={()=>parse.mutate()}>{visibleFacts?'重新解析':'开始解析'}</Button></Space>}>
        {latestTask.error?<Alert occurrence={latestTask.error} type="warning" showIcon title="暂时无法恢复解析任务状态" description={errorMessage(latestTask.error)} action={<Button loading={latestTask.isFetching} onClick={()=>void latestTask.refetch()}>重新读取任务</Button>}/>:null}
        {task.error?<Alert occurrence={task.error} type="warning" showIcon title="暂时无法读取解析任务状态" description={errorMessage(task.error)} action={<Button loading={task.isFetching} onClick={()=>void task.refetch()}>重新读取任务</Button>}/>:null}
        {parse.error?<Alert occurrence={parse.error} type="error" showIcon title={errorMessage(parse.error)}/>:null}
        {task.data?<Alert className="modal-alert" type={task.data.status==='SUCCEEDED'?'success':task.data.status==='FAILED'?'error':'info'} showIcon title={`解析任务：${displayEnum(task.data.status)}`} description={<Space orientation="vertical"><Progress aria-label="简历解析进度" percent={task.data.progressPercent} size="small"/><span>{task.data.errorDetail??`第 ${task.data.attemptCount}/${task.data.maxAttempts} 次尝试`}</span><Space>{activeTask&&task.data.status!=='CANCELLING'?<Button size="small" onClick={()=>void controlTask('cancel')}>取消</Button>:null}{task.data.status==='FAILED'&&task.data.retryable?<Button size="small" onClick={()=>void controlTask('retry')}>重试</Button>:null}</Space></Space>}/>:null}
        {factsDenied?<Alert type="info" showIcon title="暂无该简历事实的查看权限" description="基础档案和来源可查看；如需查看履历与原文，请联系负责人确认授权范围。" action={<Button loading={facts.isFetching} disabled={facts.isFetching} onClick={()=>void facts.refetch({cancelRefetch:false})}>重试解析事实</Button>}/>:<>
          {facts.error&&!(facts.error instanceof ApiProblemError&&facts.error.problem.status===404)?<Alert occurrence={facts.error} type="error" showIcon title={errorMessage(facts.error)} action={<Button loading={facts.isFetching} disabled={facts.isFetching} onClick={()=>void facts.refetch({cancelRefetch:false})}>重试解析事实</Button>}/>:null}
          {visibleFacts?<ResumeFactsPanel bundle={visibleFacts}/>:facts.isPending?<Skeleton active={false} paragraph={{rows:4}}/>:!facts.error||facts.error instanceof ApiProblemError&&facts.error.problem.status===404?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前版本尚无有效事实快照；解析失败不会生成伪事实"/>:null}
        </>}
      </Card>:null}
      <TechnicalDetails items={[["候选人",candidateId],["简历版本",currentVersionId],["事实快照",visibleFacts?.snapshot.id]]}/>
    </Space>:candidate.error?null:<div className="candidate-detail-loading" role="status"><p>正在读取候选人详情…</p><Skeleton active={false} paragraph={{rows:4}}/></div>}
    </>}
  </div>;
}

export function ResumeFactsPanel({bundle}:{bundle:ResumeFactBundle}){
  const simple=(type:string)=>bundle.simpleFacts.filter(item=>item.factType===type);
  return <Space orientation="vertical" style={{width:'100%'}}>
    <ResumeReviewPanel bundle={bundle}/>
    <Tabs className="fact-tabs" renderTabBar={(props, DefaultTabBar)=><DefaultTabBar {...props} mobile/>} items={[
    {key:'professional',label:'专业事实',children:<Space orientation="vertical" style={{width:'100%'}}><Alert type="success" showIcon title="这些是匹配输入事实，不含 AI 分数或推荐结论。"/><Descriptions bordered size="small" column={1} items={[{key:'role',label:'当前岗位',children:bundle.snapshot.currentRole??'未识别'},{key:'skills',label:'核心技能',children:<Space wrap>{bundle.skills.map(item=><Tag key={item.id}>{item.skillName}{item.yearsValue!=null?` · ${item.yearsValue}年`:''}</Tag>)}</Space>},{key:'work',label:'岗位经历',children:bundle.workExperiences.length?bundle.workExperiences.map(item=><section className="resume-experience" key={item.id}><b>{item.employer??'公司未识别'} · {item.roleName??'岗位未识别'}</b><small>{formatResumeDate(factDate(item,'start'))} — {formatResumeDate(factDate(item,'end'))}</small>{item.descriptionText?<p>{item.descriptionText}</p>:null}{jsonList(item.responsibilitiesJson).length?<ul>{jsonList(item.responsibilitiesJson).map((text,index)=><li key={index}>{text}</li>)}</ul>:null}{jsonList(item.achievementsJson).length?<p>成果：{jsonList(item.achievementsJson).join('；')}</p>:null}</section>):'未识别'},{key:'project',label:'项目经历',children:bundle.projectExperiences.length?bundle.projectExperiences.map(item=><section className="resume-experience" key={item.id}><b>{item.projectName??'项目名称未识别'} · {item.roleName??'角色未识别'}</b><small>{formatResumeDate(factDate(item,'start'))} — {formatResumeDate(factDate(item,'end'))}</small><p>{item.descriptionText}</p><Space wrap>{jsonList(item.technologiesJson).map(text=><Tag key={text}>{text}</Tag>)}</Space>{jsonList(item.achievementsJson).length?<p>成果：{jsonList(item.achievementsJson).join('；')}</p>:null}</section>):'项目经历待补充'},{key:'responsibility',label:'职责事实',children:<Space wrap>{simple('RESPONSIBILITY_TAG').map(value=><Tag key={value.id}>{value.nameValue}</Tag>)}</Space>} ]}/></Space>},
    {key:'controlled',label:'受控条件',children:<Descriptions bordered size="small" column={1} items={[{key:'age',label:'年龄',children:bundle.snapshot.age!=null?<Space wrap>{bundle.snapshot.age} 岁{bundle.snapshot.ageAsOf?<Typography.Text type="secondary">（截至 {bundle.snapshot.ageAsOf}）</Typography.Text>:null}</Space>:'未识别'},{key:'birthDate',label:'出生信息',children:bundle.snapshot.birthDate??'未识别'},{key:'gender',label:'性别',children:displayEnum(bundle.snapshot.gender,'未识别')},{key:'education',label:'学历',children:bundle.educations.length?bundle.educations.map(v=>`${v.school??''} ${displayEnum(v.degreeName,'')} ${v.major??''}`).join('；'):'未识别'},{key:'credentials',label:'证书',children:simple('CREDENTIAL').map(v=>v.nameValue).filter(Boolean).join('；')||'未识别'},{key:'languages',label:'语言',children:simple('LANGUAGE').map(v=>`${v.nameValue??''}${v.levelValue?` · ${v.levelValue}`:''}`).join('；')||'未识别'}]}/>},
    {key:'fulfillment',label:'履约与意向',children:<Descriptions bordered size="small" column={1} items={[{key:'city',label:'地点',children:bundle.snapshot.city??'未识别'},{key:'intention',label:'求职意向',children:bundle.snapshot.jobIntention??'未识别'}]}/>},
  ]}/></Space>;
}

import { CandidateIdentityConflicts } from './CandidateIdentityConflicts';
import { jobLabel } from '../demand/jobLabel';
import { MultiConditionSearch } from './MultiConditionSearch';
import { CompactPagination, type CompactPaginationProps } from '../components/CompactPagination';
import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Empty, Space, Table, Tag } from 'antd';
import { CloudUploadOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CandidateActionDialog, type CandidateActionTarget } from '../recruitment/CandidateActionDialog';
import { MATCH_CANDIDATE_LIMIT } from '../matching/selectionCandidates';
import { JdSelector } from '../demand/JdSelector';
import { UserName } from '../display/BusinessReferences';
import { formatDateTime } from '../display/labels';
import { talentApi, type Candidate, type ResumeFactBundle } from './api';
import { CandidateSkills } from './CandidateSkills';
import { CandidateBusinessRiskTags } from './CandidateBusinessRiskTags';
import { CandidateGenderIcon } from './CandidateGenderIcon';
import { educationSummary, workYears } from './summary';
import type { SearchCriteria } from '../search/api';
import { resumeReviewFlags } from './resumeReview';
import { selectScreeningContexts, ScreeningSelectionError } from './screeningAdmissionSelection';
import { ScreeningSelectionIssues } from './ScreeningSelectionIssues';

export function CandidateLibrary({countProps,pagesKnown=true,items,loading,query,onQuery,onOpen,page,pageSize,onPageSizeChange,hasMore,scanIncomplete=false,onNext,onPrevious,onPageChange,criteria,onUpload,onOpenIntakes,jdId,onJdChange,emptyDescription}:{
  pagesKnown?:boolean;
  countProps?:Pick<CompactPaginationProps,'total'|'totalStatus'|'onRetryTotal'>;
  jdId?:string;onJdChange?:(id:string|undefined)=>void;onUpload?:()=>void;onOpenIntakes?:()=>void;criteria?:SearchCriteria;items:Candidate[];loading:boolean;query:string;onQuery:(query:string,conditions:string[])=>void;onOpen:(id:string)=>void;emptyDescription?:string;
  pageSize:number;onPageSizeChange:(size:number)=>void;
  page:number;hasMore:boolean;scanIncomplete?:boolean;onNext:()=>void;onPrevious:()=>void;onPageChange?:(page:number)=>void;
}) {
  const qc=useQueryClient();const {message}=App.useApp();
  const navigate=useNavigate();
  const [identityCandidate,setIdentityCandidate]=useState<Candidate>();
  const [selectedIds,setSelectedIds]=useState<string[]>([]);
  const [selectedVersions,setSelectedVersions]=useState<Record<string,string|undefined>>({});
  const [shareError,setShareError]=useState<string>();
  const selectionScope=JSON.stringify([query,criteria,jdId]);
  const [previousSelectionScope,setPreviousSelectionScope]=useState(selectionScope);
  if(previousSelectionScope!==selectionScope){setPreviousSelectionScope(selectionScope);setSelectedIds([]);setSelectedVersions({});setShareError(undefined);}
  const [action,setAction]=useState<{mode:'advance'|'share';targets:CandidateActionTarget[];jdVersionId?:string}>();
  const screening=useMutation({
    mutationFn:async()=>{
      const selectedCandidateIds=[...selectedIds];
      const [contexts,statuses]=await Promise.all([talentApi.screeningCandidates(selectedCandidateIds),talentApi.poolStatuses(selectedCandidateIds)]);
      const selected=selectScreeningContexts(selectedCandidateIds,contexts,statuses,jdId);
      const singleJob=new Set(selected.map(value=>value.context.jdVersionId)).size===1;
      if(singleJob&&new Set(selected.map(value=>value.jdId)).size!==1)throw new Error('所选简历的岗位信息不一致，请刷新后重试');
      const result=await talentApi.admitScreening(selected.map(({context})=>({candidateId:context.candidateId,resumeVersionId:context.resumeVersionId,jdVersionId:context.jdVersionId,defaultMatchRunId:context.defaultMatchRunId})));
      return {result,job:singleJob?selected[0]:undefined};
    },
    onSuccess:async({result,job})=>{
      setSelectedIds([]);
      setSelectedVersions({});
      await Promise.all([qc.invalidateQueries({queryKey:['talent']}),qc.invalidateQueries({queryKey:['talent-search']}),qc.invalidateQueries({queryKey:['matching']})]);
      void message.success(`已将 ${result.requestedCount} 份简历加入智能筛选`);
      if(job)void navigate(`/matching?scoreJob=${encodeURIComponent(job.context.jdVersionId)}`,{state:{jdId:job.jdId}});
      else void navigate('/matching');
    },
  });
  const recommendation=useMutation({
    mutationFn:async()=>{
      const selectedCandidateIds=[...selectedIds];
      const statuses=await talentApi.poolStatuses(selectedCandidateIds);
      const statusByCandidate=new Map(statuses.map(value=>[value.candidateId,value]));
      const targets=selectedCandidateIds.map(candidateId=>{
        const state=statusByCandidate.get(candidateId);
        if(!state?.resumeVersionId)throw new Error('所选候选人的当前简历不可用，请刷新后重试');
        const contexts=(state.jobContexts??[]).filter(context=>context.status!=='FINISHED'&&context.resumeVersionId===state.resumeVersionId&&(!jdId||context.jdId===jdId));
        const versions=[...new Set(contexts.map(context=>context.jdVersionId))];
        if(!versions.length){
          const historical=state.jobContexts?.some(context=>context.status==='FINISHED'&&context.resumeVersionId===state.resumeVersionId&&(!jdId||context.jdId===jdId));
          throw new Error(historical?'所选简历仅有已结束流程的历史关联岗位，请先确认本次应聘岗位':jdId?'所选简历未绑定当前筛选岗位，请调整勾选范围':'所选简历尚未绑定岗位，请刷新后重试');
        }
        if(versions.length!==1)throw new Error('所选简历关联了多个岗位，无法确定唯一推荐岗位');
        return {candidateId,resumeVersionId:state.resumeVersionId,jdVersionId:versions[0]!};
      });
      const versions=[...new Set(targets.map(target=>target.jdVersionId))];
      if(versions.length!==1)throw new Error('所选简历绑定的岗位不一致，请调整勾选范围');
      return {jdVersionId:versions[0]!,targets:targets.map(({candidateId,resumeVersionId})=>({candidateId,resumeVersionId}))};
    },
    onSuccess:({jdVersionId,targets})=>setAction({mode:'advance',jdVersionId,targets}),
  });
  const actionPending=screening.isPending||recommendation.isPending;
  const screeningIssues=screening.error instanceof ScreeningSelectionError&&screening.error.jdId===jdId?screening.error.issues.filter(issue=>selectedIds.includes(issue.candidateId)):[];
  const completed=async(ids:string[])=>{setSelectedIds(previous=>previous.filter(id=>!ids.includes(id)));setSelectedVersions(previous=>Object.fromEntries(Object.entries(previous).filter(([id])=>!ids.includes(id))));await Promise.all([qc.invalidateQueries({queryKey:['talent']}),qc.invalidateQueries({queryKey:['talent-search']})]);};
  const openShare=()=>{
    if(selectedIds.some(id=>!selectedVersions[id])){setShareError('勾选时简历版本尚未读取，请等待摘要加载完成后重新勾选。');return;}
    setShareError(undefined);setAction({mode:'share',targets:selectedIds.map(candidateId=>({candidateId,resumeVersionId:selectedVersions[candidateId]!}))});
  };
  const candidateIds=items.map(person=>person.id);
  const states=useQuery({queryKey:['talent','pool-statuses',candidateIds],queryFn:()=>talentApi.poolStatuses(candidateIds),enabled:items.length>0});
  const byCandidate=new Map(states.data?.map(value=>[value.candidateId,value]));
  const summaries=useQuery({queryKey:['talent','candidate-resume-summaries',candidateIds],queryFn:()=>talentApi.candidateResumeSummaries(candidateIds),enabled:items.length>0,staleTime:60_000,retry:false});
  const summaryByCandidate=new Map(summaries.data?.map(value=>[value.candidateId,value]));
  const summary=(index:number)=>summaryByCandidate.get(items[index]?.id??'');
  const denied=(index:number)=>summary(index)?.factsStatus==='FORBIDDEN';
  const factText=(index:number,read:(bundle:ResumeFactBundle)=>string)=>{const value=summary(index);return value?.facts?read(value.facts):denied(index)?'—':summaries.isPending?'加载中…':'—';};
  const unavailable=summaries.isError;
  const jobTitle=(person:Candidate)=>{
    const state=byCandidate.get(person.id);
    const contexts=state?.jobContexts?.filter(context=>context.resumeVersionId===state.resumeVersionId&&(!jdId||context.jdId===jdId))??[];
    const titles=[...new Set(contexts.filter(context=>context.status!=='FINISHED').map(context=>jobLabel(context.title, context.jdCode)))].join('、');
    // 流程结束不等于解除收件岗位关联；仅有历史岗位时明确标示，避免误报未关联。
    const historical=[...new Set(contexts.filter(context=>context.status==='FINISHED').map(context=>jobLabel(context.title, context.jdCode)))].join('、');
    return titles||(historical?`历史关联：${historical}`:states.isPending?'加载中…':states.isError?'岗位暂不可用':'未关联 JD');
  };
  return <Card className="talent-ledger-card paged-panel">
    <div className="talent-toolbar">
    <div className="talent-bulk-actions">
      <JdSelector {...(jdId?{value:jdId}:{})} disabled={actionPending} onChange={value=>onJdChange?.(value?.id)} placeholder="求职岗位（选择 JD）"/>
      <Button disabled={!selectedIds.length||actionPending} onClick={()=>{setSelectedIds([]);setSelectedVersions({});setShareError(undefined);}}>清空选择</Button>
      <Button loading={screening.isPending} disabled={!selectedIds.length||actionPending} onClick={()=>screening.mutate()}>{screening.isError&&selectedIds.length>0?'重试智能筛选':'智能筛选'}</Button>
      <Button loading={recommendation.isPending} disabled={!selectedIds.length||actionPending} onClick={()=>recommendation.mutate()}>直接推荐</Button>
      <Button disabled={!selectedIds.length||actionPending} onClick={()=>openShare()}>筛选结束入共享库</Button>
    </div>
    {onUpload?<div className="talent-intake-actions"><Button className="talent-upload-button" aria-label="批量上传" type="primary" icon={<CloudUploadOutlined/>} onClick={onUpload}>批量上传</Button>{onOpenIntakes?<Button onClick={onOpenIntakes}>解析校验</Button>:null}</div>:null}
    <MultiConditionSearch disabled={actionPending} query={query} conditions={criteria?.conditions ?? []} label="搜索候选人" onSearch={onQuery}/>
    </div>
    {screening.error?<Alert occurrence={screening.error} type="error" showIcon title={screening.error instanceof Error?screening.error.message:'加入智能筛选失败，请稍后重试'} description="原有勾选已保留；只有从共享简历库选择 JD 且 AI 评分成功后转入的简历，才能进入对应岗位的智能筛选。"/>:null}
    {screeningIssues.length>0?<ScreeningSelectionIssues issues={screeningIssues} items={items} onOpen={onOpen}/>:null}
    {recommendation.error?<Alert occurrence={recommendation.error} type="error" showIcon title={recommendation.error instanceof Error?recommendation.error.message:'推荐岗位读取失败，请稍后重试'} description="没有创建招聘流程，原有候选人选择已保留。" action={<Button size="small" onClick={()=>recommendation.mutate()}>重试直接推荐</Button>}/>:null}
    {states.error?<Alert occurrence={states.error} type="error" showIcon title="关联岗位暂不可用" action={<Button onClick={()=>void states.refetch()}>重试岗位</Button>}/>:null}
    {unavailable?<Alert type="warning" showIcon title="部分简历摘要暂不可用，基础档案仍可查看" action={<Button size="small" onClick={()=>void summaries.refetch()}>重试摘要</Button>}/>:null}
    {shareError?<Alert occurrence={shareError} type="warning" showIcon title={shareError}/>:null}
    <p className="talent-mobile-hint">向右滑动查看来源、更新状态和操作</p>
    <Table<Candidate> className="candidate-centered-table" tableLayout="fixed" onHeaderRow={()=>scrollableTableHeader('简历库表头，可横向滚动')} rowKey="id" rowSelection={{getTitleCheckboxProps:()=>({'aria-label':'全选本页候选人',disabled:loading||!items.length||actionPending}),columnWidth:40,selectedRowKeys:selectedIds,preserveSelectedRowKeys:true,getCheckboxProps:person=>({'aria-label':`选择${person.fullName}`,disabled:loading||actionPending}),onChange:keys=>{
      if(keys.length>MATCH_CANDIDATE_LIMIT){void message.warning(`一次最多选择 ${MATCH_CANDIDATE_LIMIT} 人`);return;}
      const nextIds=keys.map(String);
      // 冻结勾选时实际展示的简历版本；后台状态更新不能替换 HR 已核对的版本。
      setSelectedVersions(Object.fromEntries(nextIds.map(id=>[id,selectedIds.includes(id)?selectedVersions[id]:summaryByCandidate.get(id)?.resumeVersion?.id])));
      setSelectedIds(nextIds);setShareError(undefined);
    }}} pagination={false} loading={loading} dataSource={items} scroll={{x:1320}} locale={{emptyText:<Empty description={scanIncomplete?'仍有候选人待检索，请继续检索':query||criteria?'没有匹配的候选人，请调整搜索词或筛选条件':emptyDescription??'暂无候选人，请先上传简历并确认入库'}/>}} columns={[
      {title:'#',width:48,render:(_:unknown,_value:Candidate,index:number)=>(page-1)*pageSize+index+1},
      {title:'候选人',className:'talent-identity-cell',width:180,render:(_,person,index)=><Space orientation="vertical" size={0}><Button type="link" className="table-link" title={(summary(index)?.facts?.snapshot.sourceType==='HUMAN_CORRECTION'?summary(index)?.facts?.snapshot.fullName:null)??person.fullName} onClick={()=>onOpen(person.id)}><span className="candidate-name">{(summary(index)?.facts?.snapshot.sourceType==='HUMAN_CORRECTION'?summary(index)?.facts?.snapshot.fullName:null)??person.fullName}</span><CandidateGenderIcon gender={denied(index)?undefined:summary(index)?.facts?.snapshot?.gender}/></Button>{(byCandidate.get(person.id)?.openIdentityConflictCount??0)>0?<Button type="link" size="small" onClick={()=>setIdentityCandidate(person)}>身份待核验 · {byCandidate.get(person.id)?.openIdentityConflictCount} 项 · 查看原因</Button>:null}<small className="talent-contact">{(summary(index)?.facts?.snapshot.sourceType==='HUMAN_CORRECTION'?summary(index)?.facts?.snapshot.phone??summary(index)?.facts?.snapshot.email:person.primaryPhone??person.primaryEmail)??'联系方式未录入'}</small><div className="talent-mobile-summary"><b>{jobTitle(person)}</b><span>{factText(index,bundle=>[workYears(bundle),educationSummary(bundle)].join(' · '))}</span><span>{summary(index)?.facts?.skills.slice(0,3).map(skill=>skill.skillName).join(' · ')}</span></div></Space>},
      {title:'年龄',className:'talent-age-cell',align:'center',width:60,render:(_,__,index)=>factText(index,bundle=>bundle.snapshot.age==null?'—':`${bundle.snapshot.age} 岁`)},
      {title:'求职岗位',align:'center',width:130,render:(_,person)=>jobTitle(person)},
      {title:'工作年限',width:76,render:(_,__,index)=><span title="按简历中起止时间完整的工作经历合并计算，重叠经历不重复累计">{factText(index,workYears)}</span>},
      {title:'学历',width:64,render:(_,__,index)=>factText(index,educationSummary)},
      {title:'风险标签',width:205,render:(_,person)=><><CandidateBusinessRiskTags state={byCandidate.get(person.id)} loading={states.isPending} failed={states.isError}/>{screeningIssues.some(issue=>issue.candidateId===person.id)?<Tag color="red" title={screeningIssues.find(issue=>issue.candidateId===person.id)?.reason}>智能筛选待处理</Tag>:null}</>},
      {title:'核心技能',className:'talent-skills-cell',render:(_,__,index)=>{const skills=summary(index)?.facts?.skills;return skills?.length?<CandidateSkills skills={skills}/>:factText(index,()=> '技能待补充');}},
      {title:'来源 / 归属人',className:'talent-source-cell',width:110,render:(_,person,index)=><Space orientation="vertical" size={0}><span>{summary(index)?.source?.sourceChannelName??(summaries.isPending?'读取来源…':'来源未提供')}</span><small><UserName id={person.ownerUserId}/></small></Space>},
      {title:'简历更新 / 流转',className:'talent-updated-cell',width:118,render:(_,person,index)=>{const value=summary(index);return <Space orientation="vertical" size={4}><small>{formatDateTime(value?.resumeVersion?.createdAt??person.updatedAt??person.createdAt)}</small>{value?.facts?null:<Tag>{denied(index)?'摘要需授权':summaries.isPending?'读取简历中':!value?.resumeVersion?'暂无简历':value.factsStatus==='NOT_READY'?'待解析':'摘要暂不可用'}</Tag>}</Space>}},
      {title:'操作', className: 'table-action-cell',width:128,render:(_,person,index)=>{const bundle=summary(index)?.facts;const flags=bundle?resumeReviewFlags(bundle):[];return <Space className="table-action-stack" orientation="vertical" size={6}><Button type="link" size="small" onClick={()=>onOpen(person.id)}>详情与版本</Button>{bundle?(flags.length?<Button type="link" size="small" title={[...new Set(flags.map(flag=>flag.label))].join('、')} onClick={()=>onOpen(person.id)}>复核 {flags.length} 项</Button>:<small>简历无待复核</small>):<small>{factText(index,()=> '')}</small>}</Space>;}},
    ]}/>
    <CompactPagination pagesKnown={pagesKnown} label="简历库分页" page={page} pageSize={pageSize} onPageSizeChange={onPageSizeChange} loading={loading} hasPrevious={page>1} hasNext={hasMore} onPrevious={onPrevious} onNext={onNext} nextLabel={scanIncomplete?'继续检索':'下一页'} {...(onPageChange?{onPageChange,maxPage:page}:{})} {...(!countProps&&page===1&&!hasMore&&!scanIncomplete&&!loading?{total:items.length}:{})} {...countProps} />
    {identityCandidate?<CandidateIdentityConflicts candidate={identityCandidate} onClose={()=>setIdentityCandidate(undefined)}/>:null}
    <CandidateActionDialog open={Boolean(action)} mode={action?.mode??'advance'} candidates={action?.targets??[]} jdVersionId={action?.jdVersionId} onClose={()=>setAction(undefined)} onCompleted={ids=>void completed(ids)}/>
  </Card>;
}

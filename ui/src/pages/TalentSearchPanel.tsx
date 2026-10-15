import { countPaginationProps } from '../components/countPaginationProps';
import { jobLabel } from '../demand/jobLabel';
import { MultiConditionSearch } from '../talent/MultiConditionSearch';
import { mergeSearchTerms } from '../talent/searchTerms';
import { CompactPagination } from '../components/CompactPagination';
import { scrollableTableHeader } from '../components/scrollableTableHeader';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { LockOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { App, Button, Card, Empty, Space, Table, Tag } from 'antd';
import { useLayoutEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { searchApi, type TalentSearchResult } from '../search/api';
import { LazyApplicationDetailsDialog } from '../recruitment/LazyApplicationDetailsDialog';
import { CandidateRecruitmentProgress } from '../talent/CandidateRecruitmentProgress';
import { CandidateSkills } from '../talent/CandidateSkills';
import { CandidateGenderIcon } from '../talent/CandidateGenderIcon';
import { TalentFilters } from '../talent/TalentFilters';
import { talentFilterCriteria, talentFiltersFromUrl, type TalentFilterChoices } from '../talent/talentFilterCriteria';

import { JdSelector } from '../demand/JdSelector';
import { UserName } from '../display/BusinessReferences';
import { talentApi } from '../talent/api';
import { educationSummary, highestEducation } from '../talent/summary';
import { displayEnum, formatDateTime } from '../display/labels';
import { SharedCandidateTransferDialog, type SharedCandidateTransferTarget } from '../talent/SharedCandidateTransferDialog';

const errorText=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';

export function TalentSearchPanel({onOpenCandidate}:{onOpenCandidate:(candidateId:string)=>void}){
  const [applicationId,setApplicationId]=useState<string>();
  const lastApplicationId=useRef<string | undefined>(undefined);
  const workspace=useRef<HTMLDivElement>(null);
  useLayoutEffect(()=>{
    if(applicationId){lastApplicationId.current=applicationId;return;}
    const previous=lastApplicationId.current;
    if(!previous)return;
    workspace.current?.querySelector<HTMLButtonElement>(`[data-application-id="${CSS.escape(previous)}"]`)?.focus({preventScroll:true});
    lastApplicationId.current=undefined;
  },[applicationId]);
  const [params,setParams]=useSearchParams();
  const jdId=params.get('shared_jd')??undefined;
  const filters=talentFiltersFromUrl(params,'shared');
  const criteria={...talentFilterCriteria(filters),...(jdId?{jdId}:{}),...(params.get('q')?{query:params.get('q')!}:{}),...(params.has('conditions')?{conditions:params.getAll('conditions')}:{})};
  const [pageSize,setPageSize]=useState(20);
  const [cursor,setCursor]=useState<string>();
  const [cursorStack,setCursorStack]=useState<Array<string|undefined>>([]);
  const [selectedIds,setSelectedIds]=useState<string[]>([]);
  const [transferTargets,setTransferTargets]=useState<SharedCandidateTransferTarget[]>();
  const {actor}=useSession();const {message}=App.useApp();
  const result=useQuery({queryKey:['talent-search',criteria,cursor,pageSize],queryFn:async()=>{
    let next=cursor;const seen=new Set<string>();
    for(;;){
      const page=await searchApi.search(criteria,next,'SHARED',pageSize);
      const continuation=page.items.length===0&&page.scanIncomplete&&page.hasMore?page.nextCursor:null;
      if(!continuation)return {...page,invalidContinuation:false};
      if(continuation===next||seen.has(continuation))return {...page,invalidContinuation:true};
      seen.add(continuation);next=continuation;
    }
  }});
  const total=useQuery({queryKey:['talent-search','count',actor,criteria],queryFn:({signal})=>searchApi.count(criteria,'SHARED',[],signal),retry:false});
  const invalidContinuation=Boolean(result.data?.invalidContinuation);
  const candidateIds=result.data?.items.map(item=>item.candidateId)??[];
  const summaries=useQuery({queryKey:['talent','candidate-resume-summaries',candidateIds],queryFn:()=>talentApi.candidateResumeSummaries(candidateIds),enabled:candidateIds.length>0,staleTime:60_000,retry:false});
  const summaryByCandidate=new Map(summaries.data?.map(value=>[value.candidateId,value]));
  const states=useQuery({queryKey:['talent','pool-statuses',candidateIds],queryFn:()=>talentApi.poolStatuses(candidateIds),enabled:candidateIds.length>0});
  const stateByCandidate=new Map(states.data?.map(value=>[value.candidateId,value]));
  const candidates=useQuery({queryKey:['talent','candidate-batch',candidateIds],queryFn:()=>talentApi.candidateBatch(candidateIds),enabled:candidateIds.length>0,staleTime:60_000,retry:false});
  const candidateById=new Map(candidates.data?.map(value=>[value.id,value]));
  const selectionScope=JSON.stringify(criteria);
  const [previousScope,setPreviousScope]=useState(selectionScope);
  if(previousScope!==selectionScope){setPreviousScope(selectionScope);setSelectedIds([]);setCursor(undefined);setCursorStack([]);}
  const selectIds=(ids:string[])=>{if(ids.length>1000){void message.warning('一次最多选择 1000 人');return;}setSelectedIds(ids);};
  const [pendingKeyword,setPendingKeyword]=useState('');
  const openTransfer=(ids:string[])=>{const targets=ids.map(candidateId=>{
    const item=result.data?.items.find(value=>value.candidateId===candidateId);
    const person=candidateById.get(candidateId);
    if(!item||!person)throw new Error('候选人版本尚未读取完成，请刷新后重试');
    return {candidateId,resumeVersionId:item.resumeVersionId,candidateVersion:person.version};
  });setTransferTargets(targets);};
  // 条件按钮不经过搜索栏提交，必须自行带上已输入但未提交的关键词，否则用户看得见的输入会被静默丢弃。
  const submittedTerms=mergeSearchTerms([criteria.query??'',...(criteria.conditions??[]),pendingKeyword]);
  const applyFilters=(next:TalentFilterChoices,query=submittedTerms[0]??'',conditions=submittedTerms.slice(1))=>{
    const nextParams=new URLSearchParams(params);
    for(const key of Array.from(nextParams.keys()))if(key!=='candidate'&&key!=='shared_jd')nextParams.delete(key);
    for(const [key,value] of Object.entries(next))if(value&&value!=='不限')nextParams.set(`shared_${key}`,value);
    if(query.trim())nextParams.set('q',query.trim());
    conditions.forEach(value=>nextParams.append('conditions',value));
    setParams(nextParams,{replace:true});setCursor(undefined);setCursorStack([]);setSelectedIds([]);
  };
  const columns=[
    {title:'#',width:48,render:(_:unknown,_value:TalentSearchResult,index:number)=>cursorStack.length*pageSize+index+1},
    {title:'候选人',className:'talent-identity-cell',width:180,render:(_:unknown,v:TalentSearchResult)=><Space orientation="vertical" size={0}><Button type="link" className="table-link" title={v.fullName??'未识别姓名'} onClick={()=>onOpenCandidate(v.candidateId)}><span className="candidate-name">{v.fullName??'未识别姓名'}</span><CandidateGenderIcon gender={v.gender}/></Button><small className="talent-contact">{v.phone??v.email??'联系方式未录入'}</small></Space>},
    {title:'年龄',className:'talent-age-cell',width:60,render:(_:unknown,v:TalentSearchResult)=>{const summary=summaryByCandidate.get(v.candidateId);const age=summary?.facts?.snapshot?.age;return age==null?(summaries.isPending?'加载中…':'—'):`${age} 岁`;}},
    {title:'历史关联岗位',width:130,render:(_:unknown,v:TalentSearchResult)=>{const state=stateByCandidate.get(v.candidateId);const titles=state?.jobContexts?.filter(context=>!jdId||context.jdId===jdId).map(context=>jobLabel(context.title, context.jdCode))??[];return [...new Set(titles)].join('、')||(states.isPending?'加载中…':states.isError?'岗位暂不可用':'未关联 JD');}},
    {title:'工作年限',width:76,render:(_:unknown,v:TalentSearchResult)=>v.workYears==null?'年限待补充':`${v.workYears} 年`},
    {title:'学历',width:64,render:(_:unknown,v:TalentSearchResult)=>{const summary=summaryByCandidate.get(v.candidateId);return summary?.facts?educationSummary(summary.facts):summary?.factsStatus==='FORBIDDEN'?'—':highestEducation(v.degrees);}},
    {title:'招聘流程',width:205,render:(_:unknown,v:TalentSearchResult)=><Space orientation="vertical" size={4}><CandidateRecruitmentProgress state={stateByCandidate.get(v.candidateId)} loading={states.isPending} failed={states.isError} onOpen={setApplicationId}/>{v.engagementLock?<><Tag icon={<LockOutlined/>} color={v.engagementLock.canContact?'green':'gold'}>{v.engagementLock.contactName} 跟进中</Tag><small>{formatDateTime(v.engagementLock.expiresAt)} 释放</small>{v.engagementLock.collaborationStatus?<small>协作：{displayEnum(v.engagementLock.collaborationStatus)}</small>:null}</>:null}{v.conditionMatches.length?<details><summary>筛选命中依据</summary>{v.conditionMatches.map((match,index)=><div key={index}>{match.matched?'命中':'未命中'}：{match.expected}</div>)}</details>:null}</Space>},
    {title:'核心技能',className:'talent-skills-cell',render:(_:unknown,v:TalentSearchResult)=>v.skills.length?<CandidateSkills skills={v.skills.map((skill,index)=>({id:String(index),skillName:skill}))}/>:'技能待补充'},
    {title:'来源 / 归属人',className:'talent-source-cell',width:110,render:(_:unknown,v:TalentSearchResult)=><Space orientation="vertical" size={0}><span>{summaryByCandidate.get(v.candidateId)?.source?.sourceChannelName??(summaries.isPending?'读取来源…':'来源未提供')}</span><small>{candidateById.has(v.candidateId)?<UserName id={candidateById.get(v.candidateId)?.ownerUserId}/>:candidates.isPending?'加载姓名…':'归属人暂不可用'}</small></Space>},
    {title:'简历更新 / 解析',className:'talent-updated-cell',width:118,render:(_:unknown,v:TalentSearchResult)=>{const summary=summaryByCandidate.get(v.candidateId);return <Space orientation="vertical" size={4}><small>{formatDateTime(summary?.resumeVersion?.createdAt??v.factUpdatedAt)}</small>{summary?.facts?null:<Tag>{summary?.factsStatus==='FORBIDDEN'?'摘要需授权':summaries.isPending?'读取简历中':summary?.factsStatus==='NO_RESUME'?'暂无简历':summary?.factsStatus==='NOT_READY'?'待解析':'摘要暂不可用'}</Tag>}</Space>}},
    {title:'操作', className: 'table-action-cell',width:145,render:(_:unknown,v:TalentSearchResult)=><div className="shared-library-row-actions"><Button size="small" type="primary" disabled={!candidateById.has(v.candidateId)} onClick={()=>openTransfer([v.candidateId])}>转入我的简历</Button><Button size="small" onClick={()=>onOpenCandidate(v.candidateId)}>查看简历</Button></div>}];
  return <Space ref={workspace} className="talent-search-workspace" orientation="vertical" size={10} style={{width:'100%'}}>
    <TalentFilters value={filters} onApply={next=>applyFilters(next)} />
    <Card className="talent-ledger-card paged-panel">
    <div className="talent-toolbar shared-library-toolbar">
      <div className="talent-bulk-actions">
        <JdSelector prioritizeCurrentOwner {...(jdId?{value:jdId}:{})} placeholder="求职岗位（选择 JD）" onChange={job=>{setParams(previous=>{const next=new URLSearchParams(previous);if(job)next.set('shared_jd',job.id);else next.delete('shared_jd');return next;},{replace:true});}}/>
        <Button type="primary" disabled={!selectedIds.length||candidates.isPending||selectedIds.some(id=>!candidateById.has(id))} onClick={()=>openTransfer(selectedIds)}>转入我的简历{selectedIds.length?`（${selectedIds.length}）`:''}</Button>
        <Button disabled={!selectedIds.length} onClick={()=>setSelectedIds([])}>清空选择</Button>
      </div>
      <MultiConditionSearch label="共享简历关键词搜索" query={criteria.query??''} conditions={criteria.conditions??[]} onDraftChange={setPendingKeyword} onSearch={(query,conditions)=>applyFilters(filters,query,conditions)}/>
    </div>
    {summaries.error?<Alert occurrence={summaries.error} type="warning" showIcon title="部分简历摘要暂不可用，基础信息仍可查看" action={<Button onClick={()=>void summaries.refetch()}>重试摘要</Button>}/>:null}
    {states.error?<Alert occurrence={states.error} type="warning" showIcon title="求职岗位暂不可用" action={<Button onClick={()=>void states.refetch()}>重试岗位</Button>}/>:null}
    {invalidContinuation?<Alert type="error" showIcon title="共享简历检索游标异常" description="系统已停止自动续查，请重新搜索或调整筛选条件。"/>:null}
    {result.error?<Alert occurrence={result.error} type="error" showIcon title={errorText(result.error)}/>:null}
    <Table<TalentSearchResult> className="candidate-centered-table" tableLayout="fixed" rowSelection={{getTitleCheckboxProps:()=>({'aria-label':'全选本页候选人',disabled:result.isFetching||!candidateIds.length}),getCheckboxProps:person=>({'aria-label':`选择${person.fullName??'候选人'}`,disabled:result.isFetching}),columnWidth:40,selectedRowKeys:selectedIds,preserveSelectedRowKeys:true,onChange:keys=>selectIds(keys.map(String))}} onHeaderRow={()=>scrollableTableHeader('共享简历结果表头，可横向滚动')} rowKey="candidateId" loading={result.isPending} pagination={false} dataSource={result.data?.items??[]} columns={columns} scroll={{x:1328}} locale={{emptyText:<Empty description={result.data?.scanIncomplete?'仍有候选人待检索，请继续检索':'暂无共享候选人，请调整搜索词或筛选条件'}/>}}/><CompactPagination pagesKnown={false} label="共享简历库分页" page={cursorStack.length+1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursor(undefined);setCursorStack([]);setSelectedIds([]);}} loading={result.isFetching} hasPrevious={cursorStack.length>0} hasNext={Boolean(result.data?.hasMore&&result.data.nextCursor&&!invalidContinuation)} onPrevious={()=>{setSelectedIds([]);setCursor(cursorStack.at(-1));setCursorStack(values=>values.slice(0,-1));}} onNext={()=>{setSelectedIds([]);setCursorStack(values=>[...values,cursor]);setCursor(result.data!.nextCursor!);}} onPageChange={target=>{setSelectedIds([]);setCursor(cursorStack[target-1]);setCursorStack(values=>values.slice(0,target-1));}} maxPage={cursorStack.length+1} nextLabel={result.data?.scanIncomplete?'继续检索':'下一页'} {...countPaginationProps(total)} /></Card>
    <LazyApplicationDetailsDialog sharedLibrary applicationId={applicationId} onClose={()=>setApplicationId(undefined)}/>
    <SharedCandidateTransferDialog open={Boolean(transferTargets)} candidates={transferTargets??[]} onClose={()=>setTransferTargets(undefined)} onCompleted={()=>setSelectedIds([])}/>
  </Space>;
}

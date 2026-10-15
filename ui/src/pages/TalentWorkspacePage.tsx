import { countPaginationProps } from '../components/countPaginationProps';
import { FeedbackAlert as Alert } from '../components/FeedbackAlert';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Spin } from 'antd';
import { lazy, Suspense, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiProblemError } from '../api/problem';
import { canDownloadResume } from '../auth/permissions';
import { useSession } from '../auth/session';
import { CandidateDialog } from '../talent/CandidateDetails';
import { CandidateLibrary } from '../talent/CandidateLibrary';
import { UploadDrawer } from '../talent/UploadDrawer';
import { talentApi } from '../talent/api';
import { watchParsingBatch } from '../talent/batchParseWatch';
import { emptyCriteria, searchApi } from '../search/api';
import { talentFilterCriteria, talentFiltersFromUrl, type TalentFilterChoices } from '../talent/talentFilterCriteria';
import { TalentFilters } from '../talent/TalentFilters';
import { detailOrigin, type DetailOrigin } from '../components/detailMotion';

const IntakeValidationPage=lazy(()=>import('./IntakeValidationPage').then(module=>({default:module.IntakeValidationPage})));

const errorMessage=(error:unknown)=>error instanceof ApiProblemError?error.problem.detail:error instanceof Error?error.message:'操作失败，请稍后重试。';

export function TalentWorkspacePage(){
  const {actor}=useSession();
  const queryClient=useQueryClient();
  const canUpload=Boolean(actor?.superAdmin||actor?.permissions.includes('talent.manage'));
  const [uploadOpen,setUploadOpen]=useState(false);
  const channels=useQuery({queryKey:['talent','channels'],queryFn:talentApi.channels,enabled:canUpload&&uploadOpen});
  const [searchParams,setSearchParams]=useSearchParams();
  const intakeOpen=canUpload&&searchParams.get('intake')==='1';
  const setIntakeOpen=(open:boolean,batchId?:string)=>{const next=new URLSearchParams(searchParams);if(open)next.set('intake','1');else next.delete('intake');for(const key of ['query','name','status','importStatus','issue','from','to','cursor','page','trail'])next.delete(key);if(batchId)next.set('batchId',batchId);else next.delete('batchId');setSearchParams(next,{replace:!open});};
  const closeIntakes=()=>{setUploadOpen(false);setIntakeOpen(false);void Promise.all([queryClient.invalidateQueries({queryKey:['talent']}),queryClient.invalidateQueries({queryKey:['talent-search']})]);};
  const candidateQuery=searchParams.get('pool_q')??''; const [cursor,setCursor]=useState<string>(); const [previousCursors,setPreviousCursors]=useState<Array<string|undefined>>([]);
  const [pageSize,setPageSize]=useState(20);
  const choices=talentFiltersFromUrl(searchParams,'pool');
  const jdId=searchParams.get('pool_jd')??undefined;
  const criteria={...talentFilterCriteria(talentFiltersFromUrl(searchParams,'pool')),...(jdId?{jdId}:{}),...(searchParams.has('pool_conditions')?{conditions:searchParams.getAll('pool_conditions')}:{})};
  const filtering=JSON.stringify(criteria)!==JSON.stringify(emptyCriteria());
  const scope=JSON.stringify([candidateQuery,criteria,pageSize]);
  const [previousScope,setPreviousScope]=useState(scope);
  if(previousScope!==scope){setPreviousScope(scope);setCursor(undefined);setPreviousCursors([]);}
  const candidates=useQuery({queryKey:['talent','candidates',candidateQuery,cursor,criteria,pageSize],queryFn:async()=>filtering||candidateQuery.trim()?searchApi.personalCandidates({...criteria,query:candidateQuery},cursor,pageSize):{...await talentApi.candidates('',cursor,pageSize,true),scanIncomplete:false}});
  const total=useQuery({queryKey:['talent','candidates','count',actor,candidateQuery,criteria],queryFn:({signal})=>filtering||candidateQuery.trim()?searchApi.count({...criteria,query:candidateQuery},'PERSONAL_POOL',[],signal):talentApi.countCandidates('',true,undefined,signal),retry:false});
  const applyFilters=(filters:TalentFilterChoices)=>{const next=new URLSearchParams(searchParams);for(const field of ['education','experience','age','gender'] as const){const value=filters[field];if(value&&value!=='不限')next.set(`pool_${field}`,value);else next.delete(`pool_${field}`);}setSearchParams(next,{replace:true});setCursor(undefined);setPreviousCursors([]);};
  const candidateId=searchParams.get('candidate')??undefined; const [drawerCandidateId,setDrawerCandidateId]=useState(candidateId); const [candidateOrigin,setCandidateOrigin]=useState<DetailOrigin>();
  if(candidateId&&candidateId!==drawerCandidateId)setDrawerCandidateId(candidateId);
  const openCandidate=(id:string)=>{setCandidateOrigin(detailOrigin(document.activeElement));const next=new URLSearchParams(searchParams);next.set('candidate',id);setSearchParams(next);};
  const closeCandidate=()=>{const next=new URLSearchParams(searchParams);next.delete('candidate');setSearchParams(next);};
  const searchCandidates=(query:string,conditions:string[])=>{const next=new URLSearchParams(searchParams);next.delete('pool_conditions');conditions.forEach(value=>next.append('pool_conditions',value));if(query)next.set('pool_q',query);else next.delete('pool_q');setSearchParams(next,{replace:true});setCursor(undefined);setPreviousCursors([]);};
  const changeJd=(id:string|undefined)=>{const next=new URLSearchParams(searchParams);if(id)next.set('pool_jd',id);else next.delete('pool_jd');setSearchParams(next,{replace:true});setCursor(undefined);setPreviousCursors([]);};
  return <div className="workspace-page talent-workspace-page">
    {candidates.error?<Alert occurrence={candidates.error} type="error" showIcon title={errorMessage(candidates.error)} description="人才池未能完整加载，请重试。" action={<Button loading={candidates.isFetching} onClick={()=>void candidates.refetch()}>重试</Button>}/>:null}
    <TalentFilters value={choices} onApply={applyFilters}/>
    <CandidateLibrary pageSize={pageSize} onPageSizeChange={setPageSize} pagesKnown={!filtering&&!candidateQuery.trim()} countProps={countPaginationProps(total)} {...(jdId?{jdId}:{})} onJdChange={changeJd} {...(canUpload?{onUpload:()=>setUploadOpen(true),onOpenIntakes:()=>setIntakeOpen(true)}:{})} {...(filtering?{criteria}:{})} items={candidates.data?.items??[]} loading={candidates.isPending} query={candidateQuery} onQuery={searchCandidates} onOpen={openCandidate} page={previousCursors.length+1} scanIncomplete={Boolean(candidates.data?.scanIncomplete)} hasMore={Boolean(candidates.data?.hasMore&&candidates.data.nextCursor)} onNext={()=>{setPreviousCursors([...previousCursors,cursor]);setCursor(candidates.data?.nextCursor??undefined);}} onPageChange={target=>{setCursor(previousCursors[target-1]);setPreviousCursors(previousCursors.slice(0,target-1));}} onPrevious={()=>{setCursor(previousCursors.at(-1));setPreviousCursors(previousCursors.slice(0,-1));}}/>
    {canUpload&&uploadOpen&&channels.error?<Alert occurrence={channels.error} type="error" title="上传来源加载失败" action={<Button onClick={()=>void channels.refetch()}>重试</Button>}/>:null}
    {canUpload?<UploadDrawer open={uploadOpen&&!intakeOpen&&!channels.error} channels={channels.data?.items??[]} onClose={()=>setUploadOpen(false)} onReview={batchId=>{setUploadOpen(false);setIntakeOpen(true,batchId);}} onSaved={async(_hasFailures,batchId,completed)=>{
        await Promise.all([queryClient.invalidateQueries({queryKey:['talent','intakes']}),queryClient.invalidateQueries({queryKey:['talent','intake-count']}),queryClient.invalidateQueries({queryKey:['talent','intake-batches']})]);
        if(completed&&batchId&&actor?.userId)watchParsingBatch(actor.userId,batchId);
      }}/>:null}
    {canUpload?<Drawer title="解析校验" placement="right" size="min(94vw,1180px)" className="intake-drawer" open={intakeOpen} onClose={closeIntakes} destroyOnHidden>{intakeOpen?<Suspense fallback={<Spin description="正在加载解析校验"/>}><IntakeValidationPage embedded/></Suspense>:null}</Drawer>:null}
    <CandidateDialog compact key={drawerCandidateId} {...(drawerCandidateId?{candidateId:drawerCandidateId}:{})} open={Boolean(candidateId)} motionFrom={candidateOrigin} canDownload={canDownloadResume(actor)} onClose={closeCandidate}/>
  </div>;
}

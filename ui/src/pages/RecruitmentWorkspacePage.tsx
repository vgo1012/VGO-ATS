import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Typography } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { businessWeek, dateRangeParams } from '../components/dateRange';
import { WorkspaceDialogGroup } from '../components/WorkspaceDialog';
import { countPaginationProps } from '../components/countPaginationProps';
import { recruitmentApi } from '../recruitment/api';
import { ApplicationDrawer } from '../recruitment/ApplicationDetailsDialog';
import { Applications } from '../recruitment/RecruitmentLedger';
export { Applications } from '../recruitment/RecruitmentLedger';
export { ActionModal, RiskFactModal } from '../recruitment/ApplicationDetailsDialog';

export function RecruitmentWorkspacePage(){
  const [pageSize,setPageSize]=useState(20);
  const [searchParams,setSearchParams]=useSearchParams();
  const selectedId=searchParams.get('application')??undefined;
  const setSelectedId=(id:string|undefined)=>setSearchParams(previous=>{const next=new URLSearchParams(previous);if(id)next.set('application',id);else next.delete('application');return next;});
  const filterKeys=['q','jdId','clientId','lifecycle','from','to','stage']; const filters:Record<string,string|undefined>=Object.fromEntries(filterKeys.map(key=>[key,searchParams.get(key)??undefined])); const setFilters=(value:Record<string,string|undefined>)=>setSearchParams(previous=>{const next=new URLSearchParams(previous);['jdVersionId','workflowStepCode','recruitmentType','ownerUserId','interviewType'].forEach(key=>next.delete(key));filterKeys.forEach(key=>{if(value[key])next.set(key,value[key]);else next.delete(key);});return next;}); const [paging,setPaging]=useState<{key:string;cursors:string[]}>({key:'',cursors:[]}); const qc=useQueryClient();
  if(!filters.from&&!filters.to)Object.assign(filters,dateRangeParams(businessWeek()));
  filters.stage=['ONBOARDED','TERMINATED'].includes(filters.stage??'')?filters.stage:undefined;
  filters.lifecycle=filters.stage||['ARCHIVED','ALL'].includes(filters.lifecycle??'')?'ALL':'ACTIVE';
  const filterKey=JSON.stringify([filters,pageSize]);const cursors=paging.key===filterKey?paging.cursors:[];
  const setCursors=(value:string[])=>setPaging({key:filterKey,cursors:value});
  const cursor=cursors.at(-1); const apps=useQuery({queryKey:['applications',filters,cursor,pageSize],queryFn:()=>recruitmentApi.applications({...filters,cursor,limit:pageSize})});
  const total=useQuery({queryKey:['applications','count',filters],queryFn:({signal})=>recruitmentApi.countApplications(filters,signal),retry:false});
  const detail=useQuery({queryKey:['application',selectedId],queryFn:()=>recruitmentApi.application(selectedId!),enabled:Boolean(selectedId)});
  const refresh=async()=>{await Promise.all([qc.invalidateQueries({queryKey:['applications']}),qc.invalidateQueries({queryKey:['application']}),qc.invalidateQueries({queryKey:['company-blacklist']}),qc.invalidateQueries({queryKey:['company-blacklist-affected']})]);};
  const nextCursor=apps.data?.nextCursor;
  return <div className="workspace-page pipeline-workspace pagination-workspace">
    <section className="ledger-heading"><div><Typography.Title level={1}>招聘流程</Typography.Title></div></section>
    <Applications countProps={countPaginationProps(total)} items={apps.data?.items??[]} loading={apps.isPending} error={apps.error} filters={filters} onFilters={value=>{setFilters(value);setCursors([]);}} onOpen={setSelectedId} page={cursors.length+1} pageSize={pageSize} onPageSizeChange={size=>{setPageSize(size);setCursors([]);}} hasPrevious={cursors.length>0} hasNext={Boolean(apps.data?.hasMore&&nextCursor)} onPrevious={()=>setCursors(cursors.slice(0,-1))} onNext={()=>{if(nextCursor)setCursors([...cursors,nextCursor]);}}/>
    <WorkspaceDialogGroup>
    <ApplicationDrawer value={detail.data} loading={detail.isPending} error={detail.error} open={Boolean(selectedId)} onClose={()=>setSelectedId(undefined)} onChanged={refresh} readOnly/>
    </WorkspaceDialogGroup>
  </div>;
}

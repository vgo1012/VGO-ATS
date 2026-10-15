import { useState } from 'react';
import { useInfiniteQuery, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Empty, Space, Table, Tag } from 'antd';
import { WorkspaceDialog } from '../components/WorkspaceDialog';
import { ConflictResolutionModal } from './IntakeModals';
import { talentApi, type Candidate, type IdentityConflict } from './api';
import { identityConflictReview, requiresAdminIdentityReview } from './intakeIdentityReview';

const reasons:Record<string,string>={
  STRONG_IDENTITY_MISMATCH:'手机号、邮箱或所选档案指向不同候选人',
  CONTACT_NAME_MISMATCH:'联系方式匹配已有档案，但姓名不一致',
  NAME_MATCH_CONTACT_MISMATCH:'姓名相同，但联系方式未匹配已有档案',
};

function ConflictEvidence({conflict,onResolve}:{conflict:IdentityConflict;onResolve:()=>void}){
  const restricted=requiresAdminIdentityReview(conflict);
  const intake=useQuery({queryKey:['talent','intake',conflict.intakeId],queryFn:()=>talentApi.intake(conflict.intakeId),enabled:!restricted,retry:false});
  const ids=restricted?[]:[...new Set([conflict.candidateIdA,conflict.candidateIdB].filter((id):id is string=>Boolean(id)))];
  const candidates=useQueries({queries:ids.map(id=>({queryKey:['talent','candidate',id],queryFn:()=>talentApi.candidate(id),retry:false}))});
  const pending=intake.isPending||candidates.some(result=>result.isPending);
  const failed=intake.isError||candidates.some(result=>result.isError);
  const fields=[{key:'name',label:'姓名'},{key:'phone',label:'手机号'},{key:'email',label:'邮箱'}] as const;
  const rows=[{key:'intake',label:'本次简历',name:intake.data?.parsedFullName,phone:intake.data?.parsedPhone,email:intake.data?.parsedEmail},
    ...candidates.map((result,index)=>({key:ids[index]!,label:`已有档案 ${index+1}`,name:result.data?.fullName,phone:result.data?.primaryPhone,email:result.data?.primaryEmail}))];
  if(restricted){const review=identityConflictReview(conflict);return <section style={{marginBottom:24,overflowWrap:'anywhere'}}><h3>{review.reason}</h3><p>{review.advice}</p></section>;}
  return <section style={{marginBottom:24,overflowWrap:'anywhere'}}>
    <h3>{reasons[conflict.conflictType]??'身份信息存在冲突，请核对姓名与联系方式'}</h3>
    <p>来源文件：{intake.data?.originalFileName??(intake.isPending?'读取中…':'暂不可查看')}</p>
    {failed?<p role="status">部分身份信息暂不可查看，可能无访问权限或读取失败。<Button onClick={()=>{void intake.refetch();candidates.forEach(result=>void result.refetch());}}>重试读取</Button></p>:null}
    <Table pagination={false} size="small" rowKey="key" loading={pending} dataSource={fields} tableLayout="fixed" columns={[
      {title:'核对字段',dataIndex:'label',width:85},
      ...rows.map(row=>({title:row.label,key:row.key,render:(_:unknown,field:typeof fields[number])=>{
        const value=row[field.key];
        return <span style={{overflowWrap:'anywhere'}}>{value||'未提供 / 不可查看'}</span>;
      }})),
      {title:'核对提示',width:100,render:(_:unknown,field:typeof fields[number])=>{
        const values=rows.map(row=>row[field.key]?.trim()).filter(Boolean);
        return pending||failed?'待读取':new Set(values).size>1?<Tag color="gold">值不一致</Tag>:values.length<rows.length?<Tag>信息缺失</Tag>:<span>相同</span>;
      }},
    ]}/>
    <Button style={{marginTop:12}} disabled={pending||failed} onClick={onResolve}>核验并处理此项</Button>
  </section>;
}

export function CandidateIdentityConflicts({candidate,onClose}:{candidate:Candidate;onClose:()=>void}){
  const qc=useQueryClient();
  const [open,setOpen]=useState(true);
  const [resolution,setResolution]=useState<IdentityConflict>();
  const conflicts=useInfiniteQuery({queryKey:['talent','candidate-identity-conflicts',candidate.id],initialPageParam:undefined as string|undefined,
    queryFn:({pageParam})=>talentApi.candidateConflicts(candidate.id,pageParam),getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,retry:false});
  const items=conflicts.data?.pages.flatMap(page=>page.items)??[];
  return <WorkspaceDialog open={open} title={`身份核验 · ${candidate.fullName}`} onCancel={()=>setOpen(false)} afterClose={onClose} footer={<Button onClick={()=>setOpen(false)}>关闭</Button>} size="form">
    <p>逐项对照本次简历与已有档案。差异不代表哪一方填写错误，请核实后决定关联或保留独立档案。</p>
    {conflicts.isPending?<p role="status">正在读取冲突详情…</p>:null}
    {conflicts.isError?<p role="status">冲突详情读取失败。<Button onClick={()=>void (conflicts.isFetchNextPageError?conflicts.fetchNextPage():conflicts.refetch())}>重试</Button></p>:null}
    {!conflicts.isPending&&!conflicts.isError&&!items.length?<Empty description="当前没有可查看的待处理冲突"/>:null}
    {items.map(conflict=><ConflictEvidence key={conflict.id} conflict={conflict} onResolve={()=>setResolution(conflict)}/>)}
    {conflicts.hasNextPage?<Space><Button loading={conflicts.isFetchingNextPage} onClick={()=>void conflicts.fetchNextPage()}>查看更多冲突</Button></Space>:null}
    <ConflictResolutionModal {...(resolution?{value:resolution}:{})} onClose={()=>setResolution(undefined)} onSaved={()=>qc.invalidateQueries({queryKey:['talent']})}/>
  </WorkspaceDialog>;
}

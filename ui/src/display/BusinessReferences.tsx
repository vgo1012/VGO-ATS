import { jobLabel } from '../demand/jobLabel';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Button, Select, Typography } from 'antd';
import type { SelectProps } from 'antd';
import { useDeferredValue, useState } from 'react';
import { ApiProblemError } from '../api/problem';
import { useSession } from '../auth/session';
import { demandApi } from '../demand/api';
import { directoryApi } from '../identity/directory';
import { talentApi } from '../talent/api';

// 刷新失败时 Query 仍保留旧数据；权限拒绝后不能继续展示这些引用。
const accessDenied = (error: unknown) => error instanceof ApiProblemError && [401, 403].includes(error.problem.status);

function usePeople(ids: string[]) {
  const { actor } = useSession();
  const references = [...new Set(ids.filter(id => id !== actor?.userId))].sort();
  const users = useQuery({queryKey:['identity','directory-references',actor?.userId,references], queryFn:async()=>{
    const pages=[];
    for(let start=0;start<references.length;start+=100)pages.push(directoryApi.resolve(references.slice(start,start+100)));
    return (await Promise.all(pages)).flat();
  }, enabled:Boolean(actor && references.length), staleTime:60_000, retry:false});
  const people = new Map((accessDenied(users.error) ? [] : users.data ?? []).map(person => [person.id, person.displayName]));
  if (actor) people.set(actor.userId, actor.displayName);
  return {people, loading:references.length > 0 && users.isPending, users};
}

// eslint-disable-next-line react-refresh/only-export-components -- 引用解析 hook 与展示组件共享同一份鉴权缓存。
export function useUserNames(ids: string[]) {
  const { people, loading } = usePeople(ids);
  return { people, loading };
}

export function UserName({id}:{id:string|null|undefined}) {
  const {people,loading} = useUserNames(id ? [id] : []);
  return <>{!id ? '未分配' : people.get(id) ?? (loading ? '加载姓名…' : '成员姓名暂不可用')}</>;
}

export function PeopleSelect(props: SelectProps) {
  const {actor} = useSession();
  const [search,setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const selected = (Array.isArray(props.value)?props.value:[props.value]).filter((value):value is string=>typeof value==='string');
  const {people,loading,users:references} = usePeople(selected);
  const users = useInfiniteQuery({queryKey:['identity','directory-search',actor?.userId,deferredSearch],initialPageParam:undefined as string|undefined,queryFn:({pageParam})=>directoryApi.users(deferredSearch,pageParam),getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,enabled:Boolean(actor),staleTime:60_000,retry:false});
  const options = new Map((accessDenied(users.error)?[]:users.data?.pages.flatMap(page=>page.items)??[]).map(person=>[person.id,{value:person.id,label:person.displayName,disabled:false}]));
  for(const value of selected) if(!options.has(value)) options.set(value,{value,label:people.get(value)??'成员姓名暂不可用',disabled:!accessDenied(references.error)&&references.data?.find(person=>person.id===value)?.status==='DISABLED'});
  return <Select placeholder="按姓名搜索成员" {...props} showSearch={{filterOption:false,onSearch:setSearch}} loading={loading||users.isFetching} options={[...options.values()]} notFoundContent={users.isError?<Button type="link" onClick={()=>void users.refetch()}>目录加载失败，点击重试</Button>:users.isFetching?'加载成员…':'没有匹配的在职成员'} onPopupScroll={event=>{props.onPopupScroll?.(event);const node=event.currentTarget;if(node.scrollHeight-node.scrollTop-node.clientHeight<32&&users.hasNextPage&&!users.isFetchingNextPage)void users.fetchNextPage();}}/>;
}

export function CandidateName({id}:{id:string}) {
  const value=useQuery({queryKey:['talent','candidate',id],queryFn:()=>talentApi.candidate(id),enabled:Boolean(id),staleTime:60_000,retry:false});
  return <>{(!accessDenied(value.error)&&value.data?.fullName) || (value.isPending?'加载候选人…':'候选人信息暂不可用')}</>;
}

export function JobName({jdId,jdVersionId}:{jdId:string;demandId?:string;jdVersionId?:string|undefined}) {
  const version=useQuery({queryKey:['demand','jd-version',jdVersionId],queryFn:()=>demandApi.jdVersion(jdVersionId!),enabled:Boolean(jdVersionId),staleTime:60_000,retry:false});
  const versions=useQuery({queryKey:['demand','jd-versions-by-jd',jdId],queryFn:()=>demandApi.jdVersions(jdId),enabled:Boolean(jdId)&&!jdVersionId,staleTime:60_000,retry:false});
  const job=jdVersionId?(accessDenied(version.error)?undefined:version.data):(accessDenied(versions.error)?undefined:versions.data?.items[0]);
  const pending=jdVersionId?version.isPending:versions.isPending;
  return <>{job?.title ? jobLabel(job.title,job.jdCode) : (pending?'加载岗位…':'岗位名称暂不可用')}</>;
}

export function ResumeVersionName({versionId}:{candidateId:string;versionId:string}) {
  const versions=useQuery({queryKey:['talent','resume-version-reference',versionId],queryFn:()=>talentApi.resumeVersionReferences([versionId]),enabled:Boolean(versionId),staleTime:60_000,retry:false});
  const version=accessDenied(versions.error)?undefined:versions.data?.find(item=>item.id===versionId);
  return <>{version?`第 ${version.versionNo} 版 · ${version.originalFileName}`:versions.isPending?'加载简历版本…':'简历版本信息暂不可用'}</>;
}

export function TechnicalDetails({items}:{items:Array<[string,string|null|undefined]>}) {
  return <details className="technical-details"><summary>追溯编号</summary><dl>{items.filter(([,value])=>value).map(([label,value])=><div key={label}><dt>{label}</dt><dd><Typography.Text copyable>{value}</Typography.Text></dd></div>)}</dl></details>;
}

import { useInfiniteQuery } from '@tanstack/react-query';
import { Button, Col, Select } from 'antd';
import { useDeferredValue, useState } from 'react';
import { interviewApi, type InterviewJobOption } from './api';

type Filters=Record<string,string|number|undefined>;
export function InvitedJobFilters({value,onChange}:{value:Filters;onChange:(value:Filters)=>void}) {
  const [search,setSearch]=useState('');
  const [selected,setSelected]=useState<InterviewJobOption>();
  const query=useDeferredValue(search);
  const jdId=typeof value.jdId==='string'?value.jdId:undefined;
  const jobs=useInfiniteQuery({queryKey:['interview-job-options',query],
    queryFn:({pageParam})=>interviewApi.invitedJobs({query,...(pageParam?{cursor:pageParam}:{})}),
    initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,retry:false});
  const versions=useInfiniteQuery({queryKey:['interview-job-versions',jdId],enabled:Boolean(jdId),
    queryFn:({pageParam})=>interviewApi.invitedJobs({...(jdId?{jdId}:{}),...(pageParam?{cursor:pageParam}:{})}),
    initialPageParam:undefined as string|undefined,getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,retry:false});
  const loaded=jobs.data?.pages.flatMap(page=>page.items)??[];
  const options=[...new Map([...(selected&&selected.jdId===jdId?[selected]:[]),...loaded].map(item=>[item.jdId,item])).values()];
  return <>
    <Col xs={24} md={6}><Select showSearch allowClear filterOption={false} value={jdId??null}
      aria-label="搜索受邀岗位" placeholder="搜索受邀岗位" searchValue={search} onSearch={setSearch}
      loading={jobs.isFetching} options={options.map(item=>({value:item.jdId,label:item.title}))}
      onChange={(id:string|undefined)=>{setSelected(options.find(item=>item.jdId===id));setSearch('');onChange({...value,jdId:id,jdVersionId:undefined});}}
      notFoundContent={jobs.error?<Button onClick={()=>void jobs.refetch()}>岗位读取失败，重试</Button>:jobs.isPending?'正在读取…':'暂无匹配的受邀岗位'}
      popupRender={menu=><>{menu}{jobs.hasNextPage?<Button block loading={jobs.isFetchingNextPage} onClick={()=>void jobs.fetchNextPage()}>加载更多受邀岗位</Button>:null}{jobs.isFetchNextPageError?<Button block onClick={()=>void jobs.fetchNextPage()}>重试更多岗位</Button>:null}</>}/></Col>
    <Col xs={24} md={6}><Select allowClear disabled={!jdId} value={value.jdVersionId??null}
      aria-label="岗位版本" placeholder="全部受邀岗位版本" loading={versions.isFetching}
      options={(versions.data?.pages.flatMap(page=>page.items)??[]).map(item=>({value:item.jdVersionId,label:`第 ${item.jdVersionNo} 版 · ${item.title}`}))}
      onChange={jdVersionId=>onChange({...value,jdVersionId})}
      notFoundContent={versions.error?<Button onClick={()=>void versions.refetch()}>版本读取失败，重试</Button>:versions.isPending?'正在读取…':'暂无受邀版本'}
      popupRender={menu=><>{menu}{versions.hasNextPage?<Button block loading={versions.isFetchingNextPage} onClick={()=>void versions.fetchNextPage()}>加载更多受邀版本</Button>:null}{versions.isFetchNextPageError?<Button block onClick={()=>void versions.fetchNextPage()}>重试更多版本</Button>:null}</>}/></Col>
  </>;
}

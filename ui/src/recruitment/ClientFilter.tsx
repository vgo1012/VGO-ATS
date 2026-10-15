import { useInfiniteQuery } from '@tanstack/react-query';
import { Button, Select } from 'antd';
import { demandApi } from '../demand/api';

/** The directory is paged; filtering applications remains a server operation. */
export function ClientFilter({value,onChange,disabled=false}:{value:string|undefined;onChange:(id:string|undefined)=>void;disabled?:boolean}) {
  const clients=useInfiniteQuery({queryKey:['demand','client-filter'],initialPageParam:undefined as string|undefined,queryFn:({pageParam})=>demandApi.clients(undefined,pageParam),getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined});
  return <Select allowClear showSearch optionFilterProp="label" aria-label="甲方公司" placeholder="全部甲方公司" value={value??null} disabled={disabled} onChange={onChange} loading={clients.isFetching} options={clients.data?.pages.flatMap(page=>page.items).map(client=>({value:client.id,label:client.name}))??[]}
    popupRender={menu=><>{menu}{clients.error?<Button block onClick={()=>void clients.refetch()}>重新加载甲方公司</Button>:clients.hasNextPage?<Button block loading={clients.isFetchingNextPage} onClick={()=>void clients.fetchNextPage()}>加载更多公司</Button>:null}</>}/>;
}

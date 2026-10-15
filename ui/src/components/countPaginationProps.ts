import type { UseQueryResult } from '@tanstack/react-query';
import type { CompactPaginationProps } from './CompactPagination';
export function countPaginationProps(query:UseQueryResult<number>):Pick<CompactPaginationProps,'total'|'totalStatus'|'onRetryTotal'>{
  if(query.isFetching||query.isPending)return {totalStatus:'pending'};
  if(query.isError)return {totalStatus:'error',onRetryTotal:()=>{void query.refetch();}};
  return {total:query.data};
}

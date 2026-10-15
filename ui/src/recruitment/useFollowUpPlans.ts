import { useInfiniteQuery } from '@tanstack/react-query';
import { followUpPlansApi } from './followUpPlansApi';

export function useFollowUpPlans(applicationId?:string,filters:Record<string,string|number|undefined>={}) {
  const activeFilters={...filters,status:'SCHEDULED'};
  return useInfiniteQuery({
    queryKey:['follow-up-plans',applicationId,activeFilters],
    queryFn:({pageParam})=>followUpPlansApi.list({...activeFilters,applicationId,cursor:pageParam}),
    initialPageParam:undefined as string|undefined,
    getNextPageParam:page=>page.hasMore?page.nextCursor??undefined:undefined,
  });
}

import { useInfiniteQuery } from '@tanstack/react-query';
import { ApiProblemError } from '../api/problem';
import { demandApi } from '../demand/api';

export function useScreeningJobs(search: string, enabled = true) {
  const jobs = useInfiniteQuery({
    queryKey: ['demand', 'jd-selector', search],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => demandApi.jds(search, pageParam),
    getNextPageParam: page => page.hasMore ? page.nextCursor ?? undefined : undefined,
    staleTime: 15_000,
    retry: false,
    enabled,
  });
  const denied = jobs.error instanceof ApiProblemError && [401, 403].includes(jobs.error.problem.status);
  return { ...jobs, data: denied ? undefined : jobs.data, hasNextPage: !denied && jobs.hasNextPage };
}

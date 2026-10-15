import { useQueries, useQuery } from '@tanstack/react-query';
import { ApiProblemError } from '../api/problem';
import { talentApi, type IdentityConflict } from './api';
import { requiresAdminIdentityReview } from './intakeIdentityReview';

export function useIdentityConflictDetails(conflict?:IdentityConflict){
  const restricted=Boolean(conflict&&requiresAdminIdentityReview(conflict));
  const intake=useQuery({queryKey:['talent','intake',conflict?.intakeId],queryFn:()=>talentApi.intake(conflict!.intakeId),enabled:Boolean(conflict)&&!restricted,staleTime:0,retry:false});
  const ids=restricted?[]:[...new Set([conflict?.candidateIdA,conflict?.candidateIdB].filter((id):id is string=>Boolean(id)))];
  const candidates=useQueries({queries:ids.map(id=>({queryKey:['talent','candidate',id],queryFn:()=>talentApi.candidate(id),staleTime:0,retry:false}))});
  const pending=intake.isPending||intake.isFetching||candidates.some(query=>query.isPending||query.isFetching);
  const failed=intake.isError||candidates.some(query=>query.isError);
  const denied=[intake.error,...candidates.map(query=>query.error)].some(error=>error instanceof ApiProblemError&&(error.problem.status===401||error.problem.status===403));
  const ready=Boolean(conflict)&&!restricted&&!pending&&!failed&&Boolean(intake.data)&&ids.length>0&&candidates.every(query=>Boolean(query.data));
  const retry=()=>{if(restricted)return;void intake.refetch();candidates.forEach(query=>void query.refetch());};
  return {intake,candidates,ids,pending,failed,denied,ready,restricted,retry};
}

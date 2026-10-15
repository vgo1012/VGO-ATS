import { jobLabel } from '../demand/jobLabel';
import { useQuery } from '@tanstack/react-query';
import { demandApi } from '../demand/api';

export function MatchRunJob({jdVersionId}:{jdVersionId:string}) {
  const facts=useQuery({queryKey:['demand','jd-version',jdVersionId],queryFn:()=>demandApi.jdVersion(jdVersionId),staleTime:60_000,retry:false});
  return <>{(facts.data ? jobLabel(facts.data.title, facts.data.jdCode) : undefined)??(facts.isPending?'加载岗位…':'岗位名称暂不可用')}</>;
}

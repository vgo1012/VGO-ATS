import { matchingApi, type MatchResult, type MatchResultPage } from './api';
import { MATCH_CANDIDATE_LIMIT } from './selectionCandidates';

export async function selectMatchResults(runId:string,loadPage:(cursor?:string)=>Promise<MatchResultPage>=(cursor)=>matchingApi.results(runId,cursor)):Promise<MatchResult[]> {
  const items=new Map<string,MatchResult>();
  const visited=new Set<string>();
  let cursor:string|undefined;
  for(;;) {
    const page=await loadPage(cursor);
    page.items.forEach(item=>items.set(item.resultId,item));
    if(items.size>MATCH_CANDIDATE_LIMIT)throw new Error(`最多选择 ${MATCH_CANDIDATE_LIMIT} 人，请分批操作，原有选择未改变`);
    if(!page.hasMore)return [...items.values()];
    if(!page.nextCursor||visited.has(page.nextCursor))throw new Error('结果分页发生变化，请重试，原有选择未改变');
    visited.add(page.nextCursor);cursor=page.nextCursor;
  }
}

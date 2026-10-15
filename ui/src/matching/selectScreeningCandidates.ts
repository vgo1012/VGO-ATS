import { matchingApi, type ScreeningSelectionIdentity } from './api';
import { MATCH_CANDIDATE_LIMIT } from './selectionCandidates';

/** 全选只遍历当前岗位已准入的搜索结果，失败时不替换已有选择。 */
export async function selectScreeningCandidates(jdVersionId: string, query: string): Promise<ScreeningSelectionIdentity[]> {
  const selected = new Map<string, ScreeningSelectionIdentity>();
  const cursors = new Set<string>();
  let cursor: string | undefined;
  for (;;) {
    const page = await matchingApi.screeningCandidates(jdVersionId, query, cursor, 100);
    for (const item of page.items) selected.set(item.resumeVersionId, {candidateId:item.candidateId,resumeVersionId:item.resumeVersionId});
    if (selected.size > MATCH_CANDIDATE_LIMIT || (selected.size === MATCH_CANDIDATE_LIMIT && page.hasMore)) {
      throw new Error(`每次最多选择 ${MATCH_CANDIDATE_LIMIT} 份简历，请缩小搜索范围或分批选择。原有选择已保留。`);
    }
    if (!page.hasMore) return [...selected.values()];
    if (!page.nextCursor || cursors.has(page.nextCursor)) throw new Error('候选人分页发生变化，请重试。原有选择已保留。');
    cursor = page.nextCursor;
    cursors.add(cursor);
  }
}

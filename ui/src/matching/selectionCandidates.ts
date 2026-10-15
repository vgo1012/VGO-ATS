import { emptyCriteria, searchApi, type SearchCriteria } from '../search/api';
import { talentApi, type Candidate } from '../talent/api';

// Matches the backend's per-run ceiling. Explicit bulk selection is bounded;
// ordinary browsing still requests only one server page at a time.
export const MATCH_CANDIDATE_LIMIT = 1000;

export async function selectMatchingCandidates(query: string, poolOnly = false, criteria?: SearchCriteria): Promise<Candidate[]> {
  const selected = new Map<string, Candidate>();
  const visited = new Set<string>();
  let cursor: string | undefined;
  for (;;) {
    const page = poolOnly && (criteria || query.trim()) ? await searchApi.personalCandidates({...emptyCriteria(),...criteria,query},cursor,100) : poolOnly ? await talentApi.candidates(query, cursor, 100, true) : await talentApi.candidates(query, cursor, 100);
    for (const person of page.items) selected.set(person.id, person);
    if (selected.size > MATCH_CANDIDATE_LIMIT || (selected.size === MATCH_CANDIDATE_LIMIT && page.hasMore)) {
      throw new Error(`匹配每次最多 ${MATCH_CANDIDATE_LIMIT} 人，请缩小搜索范围或分批选择。尚未替换原有选择。`);
    }
    if (!page.hasMore) return [...selected.values()];
    if (!page.nextCursor || visited.has(page.nextCursor)) throw new Error('候选人分页发生变化，请重新选择。尚未替换原有选择。');
    visited.add(page.nextCursor);
    cursor = page.nextCursor;
  }
}

export async function selectedResumeVersions(candidateIds: string[]): Promise<string[]> {
  const versions: string[] = new Array<string>(candidateIds.length);
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(6, candidateIds.length) }, async () => {
    while (next < candidateIds.length) {
      const index = next++;
      const result = await talentApi.resumeVersions(candidateIds[index]!);
      const latest = [...result.items].sort((a, b) => b.versionNo - a.versionNo)[0];
      if (!latest) throw new Error('所选候选人中有档案尚无简历，请先上传简历或移除后重试。');
      versions[index] = latest.id;
    }
  }));
  return versions;
}

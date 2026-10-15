import type { WeightProfileDetails } from '../demand/api';
import { matchingApi, type MatchBasis, type MatchSortMode } from './api';
import { screeningSettings } from './screeningSettings';

export interface ScoringBasisSelection {
  profile: WeightProfileDetails;
  planVersionId: string;
  selectedCriterionIds: string[];
  sortMode: MatchSortMode;
}

const canonical = (value: unknown): string => {
  if (Array.isArray(value)) return JSON.stringify(value.map(canonical).sort());
  if (value && typeof value === 'object') return JSON.stringify(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)]));
  return JSON.stringify(value);
};

export function matchesScoringBasis(basis: MatchBasis, selection: ScoringBasisSelection) {
  const weights = selection.profile.currentVersion;
  // 后端按实际权重和条件归组，代表版本 ID 可能来自等价方案。
  return basis.planVersionId === selection.planVersionId
    && basis.sortMode === selection.sortMode
    && (['skillsWeight', 'roleWeight', 'yearsWeight', 'responsibilityWeight', 'industryProjectWeight'] as const).every(key => basis[key] === weights[key])
    && canonical(basis.selectedCriterionIds ?? []) === canonical(selection.selectedCriterionIds)
    && canonical(basis.personalScreeningSettings?.criteria ?? []) === canonical(screeningSettings(selection.profile).criteria);
}

export async function selectScoringBasis(jdVersionId: string, selection: ScoringBasisSelection, signal: AbortSignal) {
  let cursor: string | undefined;
  const seen = new Set<string>();
  do {
    signal.throwIfAborted();
    const page = await matchingApi.bases(jdVersionId, cursor, signal);
    signal.throwIfAborted();
    const match = page.items.find(basis => basis.scoredCandidateCount > 0 && matchesScoringBasis(basis, selection));
    if (match) return match.basisRunId;
    if (!page.hasMore || !page.nextCursor) return null;
    if (seen.has(page.nextCursor)) throw new Error('评分记录分页异常，请刷新重试');
    cursor = page.nextCursor;
    seen.add(cursor);
  } while (cursor);
  return null;
}

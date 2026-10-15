import type { ControlledCriterion, PersonalScreeningSettings, WeightProfileDetails } from '../demand/api';

export const SCREENING_SORT_OPTIONS = [
  { value: 'REFERENCE_SCORE', label: '综合参考分排序' },
  { value: 'PROFESSIONAL_SCORE', label: '专业适配分排序' },
  { value: 'BONUS_SCORE', label: '附加分排序' },
];

export function screeningSettings(profile?: WeightProfileDetails): PersonalScreeningSettings {
  const raw = profile?.currentVersion.screeningSettingsJson;
  if (raw) {
    try { const parsed = JSON.parse(raw) as PersonalScreeningSettings; if (Array.isArray(parsed.criteria) && SCREENING_SORT_OPTIONS.some(item => item.value === parsed.defaultSortMode)) return parsed; } catch { /* Legacy profiles have no settings. */ }
  }
  return { criteria: [], defaultSortMode: 'REFERENCE_SCORE' };
}

export function personalCriteria(profile: WeightProfileDetails): ControlledCriterion[] {
  return screeningSettings(profile).criteria.map((item, index) => ({ ...item, id: `${profile.currentVersion.id}:${item.code}`, planVersionId: profile.currentVersion.id, criterionCode: item.code, sequenceNo: index + 1 }));
}

import type { CriterionResult, MatchResult } from './api';

export const screeningGradeLabels = {
  strong: '强推荐',
  consider: '可考虑',
  reject: '不推荐',
  missing: '信息缺失',
} as const;

const requiredDimensions = ['CORE_SKILLS', 'ROLE_EXPERIENCE', 'RELATED_YEARS', 'RESPONSIBILITY_MATCH', 'INDUSTRY_AND_PROJECTS'] as const;
const aiProfessionalAlgorithmVersions = new Set(['ai-professional-v1', 'ai-professional-v2']);

/** 仅对当前页展示辅助分级；未知信息不等同于不推荐。 */
export function screeningGrade(item: MatchResult) {
  const known = requiredDimensions.filter(code => item.details.dimensions.some(dimension => dimension.code === code && dimension.enabled && dimension.status === 'KNOWN'));
  if (known.length !== requiredDimensions.length) return 'missing';
  if (item.referenceScore >= 85) return 'strong';
  return item.referenceScore >= 70 ? 'consider' : 'reject';
}

export function assessmentPresentation(item: MatchResult) {
  return aiProfessionalAlgorithmVersions.has(item.details.algorithmVersion ?? '') && item.details.aiAssessment
    ? { label: 'AI 评分', color: 'blue' as const }
    : { label: '历史算法评分', color: 'default' as const };
}

export function mustTierPresentation(value: MatchResult['mustTier'] | undefined) {
  if (value === 'PASS') return { label: '已核对：满足', color: 'green' as const };
  if (value === 'FAIL') return { label: '已核对：不满足', color: 'red' as const };
  return { label: '待核实', color: 'gold' as const };
}

export function criterionPresentation(criterion: CriterionResult) {
  if (criterion.level === 'DISPLAY_ONLY') {
    return criterion.status === 'PASS'
      ? { label: '已记录', color: 'blue' as const }
      : { label: '待核实', color: 'gold' as const };
  }
  if (criterion.level === 'PREFER') {
    if (!criterion.selected) return { label: '本次未启用', color: 'default' as const };
    if (criterion.status === 'PASS') return { label: `已加分 +${criterion.awardedBonus}`, color: 'green' as const };
    return criterion.status === 'UNKNOWN'
      ? { label: '待核实 · +0', color: 'gold' as const }
      : { label: '未满足 · +0', color: 'default' as const };
  }
  return criterion.status === 'PASS'
    ? { label: '满足', color: 'green' as const }
    : criterion.status === 'FAIL'
      ? { label: '不满足', color: 'red' as const }
      : { label: '待核实', color: 'gold' as const };
}

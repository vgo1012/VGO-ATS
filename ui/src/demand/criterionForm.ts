import type { ControlledCriterion, CriterionCode, CriterionLevel } from './api';
import { CONTROLLED_FACTS } from './scoringRules';

export interface SimpleCriterion {
  code: CriterionCode;
  enabled: boolean;
  level: CriterionLevel;
  min: number | null;
  max: number | null;
  values: string[];
  bonusPoints: number;
  selectableByHr: boolean;
  reason: string;
  original?: ControlledCriterion;
  originalValues?: string[];
  originalMin?: number | null;
  originalMax?: number | null;
}

function leaves(value: unknown): string[] {
  if (value == null) return [];
  if (typeof value === 'object') return Object.values(value).flatMap(leaves);
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' ? [String(value)] : [];
}

export function criteriaToForm(existing: ControlledCriterion[] = []): SimpleCriterion[] {
  return CONTROLLED_FACTS.map(([code]) => {
    const criterion = existing.find(item => item.criterionCode === code);
    let values: string[] = [];
    if (criterion) {
      try { values = leaves(JSON.parse(criterion.expectedValueJson)); }
      catch { values = []; }
    }
    const number = (index: number) => values[index] !== undefined && Number.isFinite(Number(values[index])) ? Number(values[index]) : null;
    const result: SimpleCriterion = { code, enabled: Boolean(criterion), level: criterion?.level ?? 'MUST',
      min: criterion?.operator === 'LTE' ? null : number(0),
      max: criterion?.operator === 'BETWEEN' ? number(1) : criterion?.operator === 'LTE' || criterion?.operator === 'EQ' ? number(0) : null,
      values, bonusPoints: criterion?.bonusPoints || 5, selectableByHr: criterion?.selectableByHr ?? true,
      reason: criterion?.reason ?? '' };
    if (criterion) {
      result.original = criterion;
      result.originalValues = [...values];
      result.originalMin = result.min;
      result.originalMax = result.max;
    }
    return result;
  });
}

export function criterionRequest(value: SimpleCriterion) {
  let operator = 'IN';
  let expected: Record<string, unknown> = { values: value.values.map(item => item.trim()).filter(Boolean) };
  const unchanged = value.original && JSON.stringify(value.values) === JSON.stringify(value.originalValues)
    && value.min === value.originalMin && value.max === value.originalMax;
  if (value.code === 'AGE') {
    if (value.min !== null && value.max !== null) { operator = 'BETWEEN'; expected = { min: value.min, max: value.max }; }
    else if (value.min !== null) { operator = 'GTE'; expected = { min: value.min }; }
    else { operator = 'LTE'; expected = value.max === null ? {} : { max: value.max }; }
    if (!unchanged && value.level !== 'DISPLAY_ONLY' && value.min === null && value.max === null) throw new Error('请填写年龄下限或上限');
    if (value.min !== null && value.max !== null && value.min > value.max) throw new Error('年龄下限不能大于上限');
  } else if (!unchanged && value.level !== 'DISPLAY_ONLY' && !(expected.values as string[]).length) {
    throw new Error(`请填写${CONTROLLED_FACTS.find(([code]) => code === value.code)?.[1]}要求`);
  }
  return { code: value.code, level: value.level, operator: unchanged ? value.original!.operator : operator,
    expectedValueJson: unchanged ? value.original!.expectedValueJson : JSON.stringify(expected),
    bonusPoints: value.level === 'PREFER' ? value.bonusPoints : 0,
    selectableByHr: value.level === 'PREFER' && value.selectableByHr,
    reason: value.reason || '按招聘岗位要求核对明确事实', evidencePolicy: value.original?.evidencePolicy ?? 'EXPLICIT_FACT_ONLY' };
}

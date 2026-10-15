import { expect, it } from 'vitest';
import type { CriterionResult, MatchResult } from './api';
import { assessmentPresentation, criterionPresentation, mustTierPresentation, screeningGrade } from './screeningGrade';

const result = (score: number, mustTier = 'PASS', status = 'KNOWN') => ({ referenceScore: score, mustTier, details: { dimensions: ['CORE_SKILLS', 'ROLE_EXPERIENCE', 'RELATED_YEARS', 'RESPONSIBILITY_MATCH', 'INDUSTRY_AND_PROJECTS'].map(code => ({ code, enabled: true, status })) } }) as MatchResult;
it('keeps the four rating buckets disjoint at thresholds and prioritizes known failures and missing evidence', () => {
  expect([85, 84.9, 70, 69.9].map(score => screeningGrade(result(score)))).toEqual(['strong', 'consider', 'consider', 'reject']);
  expect(screeningGrade(result(99, 'FAIL'))).toBe('strong');
  expect(screeningGrade(result(99, 'UNKNOWN'))).toBe('strong');
  expect(screeningGrade(result(99, 'PASS', 'UNKNOWN'))).toBe('missing');
  expect(screeningGrade({ ...result(99), details: { ...result(99).details, dimensions: [] } })).toBe('missing');
});

it('keeps MUST checks separate and identifies compact AI assessments', () => {
  expect(mustTierPresentation('FAIL').label).toBe('已核对：不满足');
  const aiAssessment = { assessmentId:'a',provider:'p',model:'m',promptVersion:'v',schemaVersion:'v2',evaluatedOn:'2026-09-21',defaultProfessionalScore:90,summary:'匹配' };
  const aiResult = (algorithmVersion: 'ai-professional-v1' | 'ai-professional-v2') => ({ ...result(90), details: { ...result(90).details, algorithmVersion, aiAssessment } }) as MatchResult;
  expect(assessmentPresentation(aiResult('ai-professional-v1')).label).toBe('AI 评分');
  expect(assessmentPresentation(aiResult('ai-professional-v2')).label).toBe('AI 评分');
  expect(assessmentPresentation({ ...aiResult('ai-professional-v2'), details: { ...aiResult('ai-professional-v2').details, algorithmVersion: 'deterministic-v5-five-dimensions' } }).label).toBe('历史算法评分');
  expect(assessmentPresentation(result(90)).label).toBe('历史算法评分');
});

it('uses level-specific wording for controlled conditions', () => {
  const base: Omit<CriterionResult, 'level' | 'status'> = { definitionId: 'c', code: 'LANGUAGE', selected: true, configuredBonus: 10, awardedBonus: 0, reason: '', actual: [], expected: [] };
  expect(criterionPresentation({ ...base, level: 'MUST', status: 'UNKNOWN' }).label).toBe('待核实');
  expect(criterionPresentation({ ...base, level: 'PREFER', status: 'UNKNOWN' }).label).toBe('待核实 · +0');
  expect(criterionPresentation({ ...base, level: 'DISPLAY_ONLY', status: 'PASS' }).label).toBe('已记录');
  expect(criterionPresentation({ ...base, level: 'DISPLAY_ONLY', status: 'FAIL' }).label).toBe('待核实');
});

it('keeps unknown, disabled and missing dimensions out of known-zero conclusions', () => {
  const item = result(0);
  expect(screeningGrade(item)).toBe('reject');
  item.details.dimensions[2]!.enabled = false;
  expect(screeningGrade(item)).toBe('missing');
  item.details.dimensions.pop();
  expect(screeningGrade(result(0, 'PASS', 'UNKNOWN'))).toBe('missing');
});

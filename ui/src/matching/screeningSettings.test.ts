import { expect, it } from 'vitest';
import type { WeightProfileDetails } from '../demand/api';
import { personalCriteria, screeningSettings } from './screeningSettings';

it('restores a saved mandatory requirement and preferred sort from a frozen profile', () => {
  const settings = { criteria: [{ code: 'EDUCATION', level: 'MUST', operator: 'IN', expectedValueJson: '{"values":["本科","硕士"]}', bonusPoints: 0, selectableByHr: false, reason: '岗位要求', evidencePolicy: 'EXPLICIT_FACT_ONLY' }], defaultSortMode: 'BONUS_SCORE' };
  const profile = { currentVersion: { id: 'weight-v2', screeningSettingsJson: JSON.stringify(settings) } } as WeightProfileDetails;
  expect(screeningSettings(profile)).toEqual(settings);
  expect(personalCriteria(profile)).toEqual([expect.objectContaining({ criterionCode: 'EDUCATION', level: 'MUST', planVersionId: 'weight-v2' })]);
});
it('keeps legacy profiles usable without adding invented bonus criteria', () => {
  expect(screeningSettings()).toEqual({ criteria: [], defaultSortMode: 'REFERENCE_SCORE' });
});

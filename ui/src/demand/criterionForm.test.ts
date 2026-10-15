import { describe, expect, it } from 'vitest';
import { criteriaToForm, criterionRequest } from './criterionForm';

describe('simple business requirements', () => {
  it('preserves historical operator and JSON semantics when only presentation or bonus changes', () => {
    const existing = { id: 'legacy', planVersionId: 'plan', criterionCode: 'CERTIFICATE' as const, level: 'PREFER' as const,
      operator: 'CONTAINS', expectedValueJson: '{"certificate":"PMP"}', bonusPoints: 5, reason: '业务要求', evidencePolicy: 'EXPLICIT_FACT_ONLY', selectableByHr: true, sequenceNo: 1 };
    const value = criteriaToForm([existing]).find(item => item.code === 'CERTIFICATE')!;
    expect(criterionRequest({ ...value, bonusPoints: 10 })).toMatchObject({ operator: 'CONTAINS', expectedValueJson: existing.expectedValueJson, bonusPoints: 10 });
    expect(criterionRequest({ ...value, values: ['CPA'] })).toMatchObject({ operator: 'IN', expectedValueJson: '{"values":["CPA"]}' });
  });
  it('turns one-sided and bounded ages into deterministic rules in the right order', () => {
    const age = criteriaToForm()[0]!;
    expect(criterionRequest({ ...age, min: 22, max: 35 })).toMatchObject({ operator: 'BETWEEN', expectedValueJson: '{"min":22,"max":35}', bonusPoints: 0 });
    expect(criterionRequest({ ...age, max: 45 })).toMatchObject({ operator: 'LTE', expectedValueJson: '{"max":45}' });
    expect(() => criterionRequest({ ...age, min: 45, max: 22 })).toThrow('下限');
    expect(() => criterionRequest(age)).toThrow('请填写');
  });
  it('does not allow a must condition to become optional and separates bonus from base score', () => {
    const education = criteriaToForm()[2]!;
    expect(criterionRequest({ ...education, values: ['本科'], selectableByHr: true })).toMatchObject({ selectableByHr: false, bonusPoints: 0 });
    expect(criterionRequest({ ...education, level: 'PREFER', values: ['本科'], bonusPoints: 10 })).toMatchObject({ selectableByHr: true, bonusPoints: 10 });
  });
  it('serializes custom certificate bonus and must rules without mixing their semantics', () => {
    const certificate = criteriaToForm().find(item => item.code === 'CERTIFICATE')!;
    expect(criterionRequest({ ...certificate, level: 'PREFER', values: ['PMP'], bonusPoints: 20 })).toMatchObject({
      code: 'CERTIFICATE', operator: 'IN', expectedValueJson: '{"values":["PMP"]}',
      bonusPoints: 20, selectableByHr: true,
    });
    expect(criterionRequest({ ...certificate, level: 'MUST', values: ['PMP'], bonusPoints: 20, selectableByHr: true })).toMatchObject({
      code: 'CERTIFICATE', operator: 'IN', expectedValueJson: '{"values":["PMP"]}',
      bonusPoints: 0, selectableByHr: false,
    });
  });
});

import { describe, expect, it } from 'vitest';
import { CONTROLLED_FACTS, PROFESSIONAL_DIMENSIONS, dualActivationReady, isPersonalSelectable, professionalWeightTotal } from './scoringRules';

describe('评分业务边界', () => {
  it('固定五项专业维度且默认权重合计 100', () => {
    expect(PROFESSIONAL_DIMENSIONS.map((item) => item[1])).toEqual(['核心技能', '岗位经历', '相关年限', '职责匹配', '行业与项目']);
    expect(PROFESSIONAL_DIMENSIONS.map((item) => item[2])).toEqual([20, 20, 20, 20, 20]);
    expect(professionalWeightTotal({ skills: 20, roleExperience: 20, relatedYears: 20, responsibilityMatch: 20, industryAndProjects: 20 })).toBe(100);
  });

  it('学历证书语言只属于五类受控事实，不属于专业维度', () => {
    expect(CONTROLLED_FACTS.map((item) => item[1])).toEqual(['年龄', '性别', '学历', '证书', '语言']);
    expect(PROFESSIONAL_DIMENSIONS.flat().join(',')).not.toContain('学历');
  });

  it('个人只能选择管理员开放的 PREFER，MUST 永远不可选', () => {
    const base = { id: 'criterion', planVersionId: 'plan', criterionCode: 'LANGUAGE' as const, operator: 'IN', expectedValueJson: '{}', bonusPoints: 10, reason: '业务原因', evidencePolicy: 'EXPLICIT_FACT_ONLY', sequenceNo: 1 };
    expect(isPersonalSelectable({ ...base, level: 'PREFER', selectableByHr: true })).toBe(true);
    expect(isPersonalSelectable({ ...base, level: 'MUST', selectableByHr: true })).toBe(false);
  });

  it('流程与评分必须同时激活', () => {
    expect(dualActivationReady(true, true)).toBe(true);
    expect(dualActivationReady(true, false)).toBe(false);
    expect(dualActivationReady(false, true)).toBe(false);
  });
});

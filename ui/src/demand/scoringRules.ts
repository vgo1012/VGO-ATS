import type { ControlledCriterion, CriterionCode, ProfessionalWeights } from './api';

export const PROFESSIONAL_DIMENSIONS = [
  ['skills', '核心技能', 20],
  ['roleExperience', '岗位经历', 20],
  ['relatedYears', '相关年限', 20],
  ['responsibilityMatch', '职责匹配', 20],
  ['industryAndProjects', '行业与项目', 20],
] as const;

export const CONTROLLED_FACTS: Array<[CriterionCode, string]> = [
  ['AGE', '年龄'], ['GENDER', '性别'], ['EDUCATION', '学历'], ['CERTIFICATE', '证书'], ['LANGUAGE', '语言'],
];

export function professionalWeightTotal(weights: ProfessionalWeights): number {
  return PROFESSIONAL_DIMENSIONS.reduce((total, [key]) => total + weights[key], 0);
}

export function isPersonalSelectable(criterion: ControlledCriterion): boolean {
  return criterion.level === 'PREFER' && criterion.selectableByHr;
}

export function dualActivationReady(workflowActive: boolean, scoringActive: boolean): boolean {
  return workflowActive && scoringActive;
}

import { describe, expect, it } from 'vitest';
import { canManageMonthlyReports, hasManagementScope, roleLabel } from './accessPolicy';

const actor = (roles: string[], superAdmin = false) => ({ roles, superAdmin });

describe('role presentation and management scope', () => {
  it.each([
    [actor(['RECRUITER']), false, '招聘专员'],
    [actor(['RECRUITMENT_MANAGER']), true, '部门管理'],
    [actor(['HR_DIRECTOR']), true, '人力负责人'],
    [actor([], true), true, '系统管理员'],
  ])('maps %o to its role and scope', (value, management, label) => {
    expect(hasManagementScope(value)).toBe(management);
    expect(roleLabel(value)).toBe(label);
  });
});

it('limits monthly reports to HR directors with permission and super administrators', () => {
  expect(canManageMonthlyReports({ roles: ['HR_DIRECTOR'], permissions: ['reporting.monthly-report.manage'], superAdmin: false })).toBe(true);
  expect(canManageMonthlyReports({ roles: ['RECRUITER'], permissions: ['reporting.monthly-report.manage'], superAdmin: false })).toBe(false);
  expect(canManageMonthlyReports({ roles: ['HR_DIRECTOR'], permissions: [], superAdmin: false })).toBe(false);
  expect(canManageMonthlyReports({ roles: [], permissions: [], superAdmin: true })).toBe(true);
});

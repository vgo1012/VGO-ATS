import type { SessionActor } from './session';

export const PERMISSIONS = {
  demandRead: 'demand.read', demandManage: 'demand.manage', talentRead: 'talent.read', talentManage: 'talent.manage',
  matchingRun: 'matching.run', recruitmentManage: 'recruitment.manage', interviewReadAuthorized: 'interview.feedback.read-authorized',
  interviewSubmitOwn: 'interview.feedback.submit-own', interviewSchedule: 'interview.schedule', reportingReadOwn: 'reporting.read-own',
  monthlyReportManage: 'reporting.monthly-report.manage',
  riskRead: 'risk.read', riskManage: 'risk.manage',
} as const;

export const ROLES = {
  recruiter: 'RECRUITER', recruitmentManager: 'RECRUITMENT_MANAGER', hrDirector: 'HR_DIRECTOR',
  interviewer: 'INTERVIEWER', superAdmin: 'SUPER_ADMIN',
} as const;

type AccessActor = Pick<SessionActor, 'roles' | 'superAdmin'> | null | undefined;

export function hasManagementScope(actor: AccessActor): boolean {
  return Boolean(actor?.superAdmin || actor?.roles?.some(role => role === ROLES.recruitmentManager || role === ROLES.hrDirector));
}

export function canManageMonthlyReports(actor: (AccessActor & { permissions?: string[] }) | null | undefined): boolean {
  return Boolean(actor?.superAdmin || (actor?.roles?.includes(ROLES.hrDirector) && actor.permissions?.includes(PERMISSIONS.monthlyReportManage)));
}

export function roleLabel(actor: AccessActor): string {
  if (actor?.superAdmin) return '系统管理员';
  if (actor?.roles?.includes(ROLES.hrDirector)) return '人力负责人';
  if (actor?.roles?.includes(ROLES.recruitmentManager)) return '部门管理';
  if (actor?.roles?.includes(ROLES.recruiter)) return '招聘专员';
  if (actor?.roles?.includes(ROLES.interviewer)) return '面试官';
  return '招聘业务账号';
}

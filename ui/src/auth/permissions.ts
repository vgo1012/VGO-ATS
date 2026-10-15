import type { SessionActor } from './session';

export type Permission =
  | 'demand.read'
  | 'demand.manage'
  | 'talent.read'
  | 'talent.manage'
  | 'matching.run'
  | 'recruitment.manage'
  | 'interview.read'
  | 'interview.evaluate'
  | 'identity.organization.manage';

export interface Actor {
  userId: string;
  displayName: string;
  permissions: ReadonlySet<Permission>;
  superAdmin: boolean;
}

export function can(actor: Actor, permission: Permission): boolean {
  return actor.superAdmin || actor.permissions.has(permission);
}

// 登录用户可发起对象级下载授权；服务端按具体简历在授权和下载时再次校验。
export function canDownloadResume(actor: Pick<SessionActor, 'userId'> | null | undefined): boolean {
  return Boolean(actor);
}

export function canCorrectResumeFacts(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('talent.read'));
}

export function canManageInterviewFeedback(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('recruitment.manage'));
}

export function canScheduleInterviews(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('interview.schedule'));
}

export function canReadRecruitmentLedger(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('recruitment.manage'));
}

export function canReadRisk(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('risk.read'));
}

export function canManageRisk(actor: Pick<SessionActor, 'superAdmin' | 'permissions'> | null | undefined): boolean {
  return Boolean(actor?.superAdmin || actor?.permissions.includes('risk.manage'));
}

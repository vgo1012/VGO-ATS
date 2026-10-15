import type { SessionActor } from './session';
import { PERMISSIONS } from './accessPolicy';

type WorkspaceActor = (Pick<SessionActor, 'superAdmin' | 'permissions'> & Partial<Pick<SessionActor, 'roles'>>) | null | undefined;

const routePermissions: Record<string, string> = {
  '/overview': PERMISSIONS.reportingReadOwn,
  '/talents': PERMISSIONS.talentRead,
  '/onboarded': PERMISSIONS.recruitmentManage,
  '/matching': PERMISSIONS.matchingRun,
  '/recruitment': PERMISSIONS.recruitmentManage,
  '/recruitment/setup': PERMISSIONS.demandRead,
  '/schedule': PERMISSIONS.interviewReadAuthorized,
  '/shared-library': 'AUTHENTICATED',
  '/settings/preferences': PERMISSIONS.matchingRun,
  '/settings/intakes': PERMISSIONS.talentManage,
  '/admin/mail-resume-reviews': 'SUPER_ADMIN',
  '/admin/source-channels': 'SUPER_ADMIN',
  '/admin/work-calendars': 'SUPER_ADMIN',
  '/admin/task-center': 'SUPER_ADMIN',
  '/admin/clients': 'SUPER_ADMIN',
  '/admin/reports': PERMISSIONS.reportingReadOwn,
  '/recruitment-analysis': PERMISSIONS.reportingReadOwn,
  '/monthly-reports': PERMISSIONS.monthlyReportManage,
  '/admin/access': 'SUPER_ADMIN',
  '/admin/identity': 'SUPER_ADMIN',
  '/admin/scoring': 'SUPER_ADMIN',
};

function normalizedWorkspacePath(path: string): string | null {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return null;
  return path.split(/[?#]/)[0]!.replace(/\/$/, '');
}

export function isKnownWorkspacePath(path: string): boolean {
  const normalizedPath = normalizedWorkspacePath(path);
  return normalizedPath !== null && routePermissions[normalizedPath] !== undefined;
}

/** 页面入口与菜单共用能力判断；服务端继续执行对象级鉴权。 */
export function canAccessWorkspace(actor: WorkspaceActor, path: string): boolean {
  const normalizedPath = normalizedWorkspacePath(path);
  if (!actor || normalizedPath === null) return false;
  const permission = routePermissions[normalizedPath];
  if (!permission) return false;
  if (permission === 'AUTHENTICATED') return true;
  if (permission === 'SUPER_ADMIN') return actor.superAdmin;
  if (normalizedPath === '/monthly-reports') {
    return actor.superAdmin || Boolean(actor.roles?.includes('HR_DIRECTOR') && permission && actor.permissions.includes(permission));
  }
  return actor.superAdmin || actor.permissions.includes(permission);
}

export function defaultWorkspacePath(actor: WorkspaceActor): string {
  return ['/recruitment-analysis', '/schedule', '/talents', '/matching', '/recruitment', '/shared-library'].find(path => canAccessWorkspace(actor, path)) ?? '/';
}

export function loginDestination(actor: WorkspaceActor, requested?: string): string {
  const requestedPath = requested?.split(/[?#]/)[0]?.replace(/\/$/, '');
  return requested && requestedPath !== '/login' && requestedPath !== '/change-password' && canAccessWorkspace(actor, requested)
    ? requested
    : defaultWorkspacePath(actor);
}

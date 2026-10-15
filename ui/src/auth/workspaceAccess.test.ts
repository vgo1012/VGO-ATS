import { expect, it } from 'vitest';
import { canAccessWorkspace, defaultWorkspacePath, isKnownWorkspacePath, loginDestination } from './workspaceAccess';

const interviewer = { superAdmin: false, permissions: ['interview.feedback.read-authorized'] };
it('gives every signed-in user a shared-library entry without granting private workspaces', () => {
  const reader = { superAdmin: false, permissions: [] };
  expect(canAccessWorkspace(reader, '/shared-library')).toBe(true);
  expect(defaultWorkspacePath(reader)).toBe('/shared-library');
  expect(canAccessWorkspace(reader, '/talents')).toBe(false);
  expect(canAccessWorkspace(null, '/shared-library')).toBe(false);
});
it('shares recruitment analysis permission between route access and navigation', () => {
  expect(canAccessWorkspace(interviewer, '/recruitment-analysis')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['reporting.read-own'] }, '/recruitment-analysis')).toBe(true);
  for (const path of ['/recruitment-analysis/motions', '/recruitment-analysis/designs']) {
    expect(canAccessWorkspace({ superAdmin: false, permissions: ['reporting.read-own'] }, path)).toBe(false);
    expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, path)).toBe(false);
  }
});
it('restricts work calendar maintenance to super administrators', () => {
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['talent.manage'] }, '/admin/work-calendars')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/admin/work-calendars')).toBe(true);
});
it('removes cloud storage from workspaces and ignores its stale login destination', () => {
  const admin = { superAdmin: true, permissions: [] };
  expect(isKnownWorkspacePath('/admin/storage')).toBe(false);
  expect(canAccessWorkspace(admin, '/admin/storage')).toBe(false);
  expect(loginDestination(admin, '/admin/storage?tab=overview')).toBe(defaultWorkspacePath(admin));
});
it('keeps interviewer entry points within their granted workspace', () => {
  expect(defaultWorkspacePath(interviewer)).toBe('/schedule');
  for (const path of ['/overview', '/matching', '/talents', '/onboarded', '/recruitment/setup', '/settings/intakes', '/admin/source-channels', '/admin/clients', '/admin/storage', '/admin/identity', '/admin/scoring', '/admin/reports', '/settings/preferences']) {
    expect(canAccessWorkspace(interviewer, path)).toBe(false);
    expect(loginDestination(interviewer, path)).toBe('/schedule');
  }
  expect(canAccessWorkspace(interviewer, '/shared-library')).toBe(true);
  expect(loginDestination(interviewer, '/schedule?tab=mine')).toBe('/schedule?tab=mine');
});
it('preserves recruiter and administrator destinations without accepting external redirects', () => {
  const recruiter = { superAdmin: false, permissions: ['recruitment.manage', 'matching.run', 'reporting.read-own'] };
  expect(defaultWorkspacePath(recruiter)).toBe('/recruitment-analysis');
  expect(loginDestination(recruiter, '/matching')).toBe('/matching');
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['talent.read'] }, '/shared-library')).toBe(true);
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['talent.manage'] }, '/settings/intakes')).toBe(true);
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['talent.manage'] }, '/admin/mail-resume-reviews')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: false, permissions: ['talent.read'] }, '/admin/mail-resume-reviews')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/admin/scoring')).toBe(true);
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/admin/clients')).toBe(true);
  expect(canAccessWorkspace(recruiter, '/admin/clients?page=2')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/monthly-reports')).toBe(true);
  expect(canAccessWorkspace({ superAdmin: false, roles: ['HR_DIRECTOR'], permissions: ['reporting.monthly-report.manage'] }, '/monthly-reports')).toBe(true);
  expect(canAccessWorkspace({ superAdmin: false, roles: ['RECRUITER'], permissions: ['reporting.monthly-report.manage'] }, '/monthly-reports')).toBe(false);
  for (const path of ['https://example.invalid', '//example.invalid', '/\\example.invalid', '/login', '/change-password']) expect(loginDestination(interviewer, path)).toBe('/schedule');
});

it('allows only super administrators into mail resume review', () => {
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/admin/mail-resume-reviews')).toBe(true);
  expect(loginDestination({ superAdmin: false, permissions: ['talent.manage', 'talent.read'] }, '/admin/mail-resume-reviews')).toBe('/talents');
});

it('sends stale or unknown workspace links to an available landing page', () => {
  expect(canAccessWorkspace(interviewer, '/retired-workspace')).toBe(false);
  expect(canAccessWorkspace({ superAdmin: true, permissions: [] }, '/retired-workspace')).toBe(false);
  expect(loginDestination(interviewer, '/retired-workspace?tab=old')).toBe('/schedule');
});

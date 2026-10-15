import { describe, expect, it } from 'vitest';
import { can, canCorrectResumeFacts, canDownloadResume, canManageInterviewFeedback, type Actor } from './permissions';

describe('permission evaluation', () => {
  it('inherits shared-fact correction from fact read access', () => {
    expect(canCorrectResumeFacts({ superAdmin: false, permissions: ['talent.read'] })).toBe(true);
    expect(canCorrectResumeFacts({ superAdmin: false, permissions: ['talent.manage'] })).toBe(false);
    expect(canCorrectResumeFacts({ superAdmin: false, permissions: ['talent.fact.correct'] })).toBe(false);
    expect(canCorrectResumeFacts({ superAdmin: true, permissions: [] })).toBe(true);
    expect(canCorrectResumeFacts(null)).toBe(false);
  });
  it('lets every logged-in user request object-level resume download authorization', () => {
    expect(canDownloadResume({ userId: 'recruiter' })).toBe(true);
    expect(canDownloadResume({ userId: 'interviewer-without-talent-read' })).toBe(true);
    expect(canDownloadResume(null)).toBe(false);
    expect(canDownloadResume(undefined)).toBe(false);
  });
  it('reserves interview feedback work for recruitment managers', () => {
    expect(canManageInterviewFeedback({ superAdmin: false, permissions: ['recruitment.manage'] })).toBe(true);
    expect(canManageInterviewFeedback({ superAdmin: false, permissions: ['interview.feedback.submit-own'] })).toBe(false);
    expect(canManageInterviewFeedback({ superAdmin: true, permissions: [] })).toBe(true);
  });
  it('allows a super administrator to use every declared permission', () => {
    const actor: Actor = {
      userId: 'admin-1',
      displayName: '平台管理员',
      permissions: new Set(),
      superAdmin: true,
    };

    expect(can(actor, 'talent.read')).toBe(true);
    expect(can(actor, 'identity.organization.manage')).toBe(true);
  });
});

import { describe, expect, it } from 'vitest';
import { canProcessRecruitment } from './processingPermissions';

describe('canProcessRecruitment', () => {
  const admin = { userId: 'admin' };

  it('does not turn management visibility into processing authority', () => {
    expect(canProcessRecruitment(admin, { ownerUserId: '张国玉' })).toBe(false);
  });

  it('allows the owner and an explicit collaborator', () => {
    expect(canProcessRecruitment({ userId: '张国玉' }, { ownerUserId: '张国玉' })).toBe(true);
    expect(canProcessRecruitment(admin, { ownerUserId: '张国玉', collaboratorUserIds: ['admin'] })).toBe(true);
  });
});

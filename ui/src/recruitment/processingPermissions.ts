import type { SessionActor } from '../auth/session';

export interface RecruitmentOwnership {
  ownerUserId: string;
  collaboratorUserIds?: readonly string[];
}

/** 管理范围只扩大查看范围；处理动作必须来自负责人本人或显式协作人。 */
export function canProcessRecruitment(
  actor: Pick<SessionActor, 'userId'> | null | undefined,
  ownership: RecruitmentOwnership | null | undefined,
): boolean {
  if (!actor || !ownership) return false;
  return ownership.ownerUserId === actor.userId
    || Boolean(ownership.collaboratorUserIds?.includes(actor.userId));
}

export const recruitmentProcessingDeniedMessage = '你可以查看此招聘流程；只有归属 HR 或已添加的流程协作人可以处理。';

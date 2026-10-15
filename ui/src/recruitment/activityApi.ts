import { request } from '../api/http';
import { createIdempotencyKey } from '../api/idempotency';
import type { QueryClient } from '@tanstack/react-query';

export type LedgerAction = 'PENDING' | 'INTERVIEW' | 'ENDED' | 'ONBOARDED' | 'BLACKLIST';
export interface ActivityOrigin { action: LedgerAction; operationId: string }
export const ledgerActionLabels: Record<LedgerAction, string> = { PENDING: '待定', INTERVIEW: '面试', ENDED: '放弃', ONBOARDED: '入职', BLACKLIST: '黑名单' };
export const newActivityOrigin = (action: LedgerAction): ActivityOrigin => ({ action, operationId: createIdempotencyKey() });
export const activityHeaders = (origin?: ActivityOrigin): Record<string, string> => origin ? {
  'X-Recruitment-Action': origin.action, 'X-Recruitment-Operation': origin.operationId,
} : {};

export async function refreshRecruitmentActivity(client: QueryClient, applicationId: string) {
  await Promise.all([
    ['application', applicationId], ['applications'],
    ['follow-up-plans'], ['follow-up-plan-history', applicationId], ['application-communications', applicationId],
    ['interviews'], ['interview'], ['interview-history'], ['interview-tasks'], ['application-interviews', applicationId], ['application-interview-feedback', applicationId],
    ['application-activity', applicationId], ['candidate-blacklist-tags'], ['talent','pool-statuses'], ['demand'], ['recruitment', 'readiness'], ['candidate-restrictions'], ['submission-eligibility'],
  ].map(queryKey => client.invalidateQueries({ queryKey })));
}

export interface CompanyRestriction {
  id: string; clientId: string; candidateId: string; status: string; recordType: string;
  sourceApplicationId: string | null; reasonDetail: string; sourceChannel: string;
  evidenceReference: string | null; restrictionIntent: string; expiresAt: string | null;
  sourceOccurredAt: string; createTime: string;
}
export interface CompanyRestrictionInput { clientId: string; reasonDetail: string; sourceChannel: string }
export const activityRiskApi = {
  restrictions: (candidateId: string, clientId: string) => request<CompanyRestriction[]>(`/candidates/${candidateId}/client-restrictions?${new URLSearchParams({clientId,currentOnly:"true",limit:"1"})}`),
  create: (applicationId: string, body: CompanyRestrictionInput, origin: ActivityOrigin) => request<CompanyRestriction>(`/applications/${applicationId}/client-restrictions`, { method: 'POST', body, headers: activityHeaders(origin), idempotencyKey: origin.operationId }),
  correct: (id: string, body: { restrictionIntent: string; reasonDetail: string; sourceChannel: string; expiresAt?:string|null }, origin: ActivityOrigin) => request<CompanyRestriction>(`/client-restrictions/${id}/corrections`, { method: 'POST', body, headers: activityHeaders(origin), idempotencyKey: origin.operationId }),
  disable: (id: string, reason: string, origin: ActivityOrigin) => request<CompanyRestriction>(`/client-restrictions/${id}/disable`, { method: 'POST', body: { reason }, headers: activityHeaders(origin), idempotencyKey: origin.operationId }),
};

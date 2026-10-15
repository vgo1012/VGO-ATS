import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import type { WorkflowDetails } from '../demand/api';

export interface CandidateActionTarget { candidateId: string; resumeVersionId?: string | undefined }
export interface CandidateActionOutcome extends CandidateActionTarget {
  status: 'SUCCEEDED' | 'ALREADY_EXISTS' | 'FAILED';
  applicationId?: string | undefined;
  errorCode?: string | undefined;
  message?: string | undefined;
}
export interface RecruitmentReadiness {
  headcount?: number | null; occupiedHeadcount?: number; remainingHeadcount?: number | null;
  jdId: string; jdVersionId: string; jdTitle: string; jdCode?: string | null; demandId: string | null;
  recruitmentType: 'INTERNAL' | 'EXTERNAL' | null; canProcess: boolean;
  clientId?: string | null; clientName?: string | null;
  canManageWorkflow?: boolean;
  activeWorkflowId: string | null; activeWorkflow: WorkflowDetails | null; draftWorkflows: WorkflowDetails[]; standardSteps: string[];
}
export const candidateActionsApi = {
  readiness: (id: string) => request<RecruitmentReadiness>(`/jd-versions/${id}/recruitment-readiness`),
  activateStandard: (id: string, reason: string) => request<RecruitmentReadiness>(`/jd-versions/${id}/standard-workflow-activation`, { method: 'POST', body: { reason }, idempotencyKey: createIdempotencyKey() }),
  advance: (jdVersionId: string, items: Array<{candidateId: string; resumeVersionId: string}>, reason: string, key: string) => request<{items: CandidateActionOutcome[]}>('/applications/batch', {method:'POST',body:{jdVersionId,items,reason},idempotencyKey:key}),
};

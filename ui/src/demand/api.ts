import { createIdempotencyKey } from '../api/idempotency';
import { request } from '../api/http';
import { collectCount, type CountPage } from '../api/count';
import type { ParseTask } from '../talent/api';

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface Client {
  id: string;
  code: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  ownerUserId: string;
  ownerDepartmentId: string;
  version: number;
  updatedAt: string;
}

export interface DepartmentOption {
  id: string;
  code: string;
  name: string;
  parentDepartmentId: string | null;
  status: 'ACTIVE' | 'DISABLED';
}

export interface ClientProject {
  id: string;
  clientId: string;
  code: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
  updatedAt: string;
}

export interface ClientPosition {
  id: string;
  clientId: string;
  projectId: string | null;
  code: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
  updatedAt: string;
}

export interface RecruitmentDemand {
  id: string;
  code: string;
  title: string;
  clientId: string;
  projectId: string | null;
  positionId: string;
  recruitmentType: 'INTERNAL' | 'EXTERNAL';
  headcount: number;
  description: string | null;
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED';
  ownerUserId: string;
  ownerDepartmentId: string;
  version: number;
  updatedAt: string;
}

export interface JdSummary {
  jobTypeCode?: string | null;
  workRegionCode?: string | null;
  workRegionName?: string | null;
  recruitmentCycleDays?: number | null;
  headcount?: number | null;
  occupiedHeadcount?: number;
  onboardedHeadcount?: number;
  remainingHeadcount?: number | null;
  id: string;
  code?: string;
  /** Legacy association is returned only for historical records. */
  demandId?: string | null;
  demandCode?: string | null;
  title: string;
  distinction?: string | null;
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'DELETED';
  currentVersionNo: number;
  contentShared: boolean;
  appliedFromJdVersionId: string | null;
  clientId: string | null;
  clientName: string | null;
  ownerUserId: string;
  ownerDepartmentId: string;
  recruitmentType?: 'INTERNAL' | 'EXTERNAL';
  version?: string;
  currentParseStatus?: 'NOT_STARTED' | 'QUEUED' | 'RUNNING' | 'CANCELLING' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED';
  createTime: string;
}

export interface PublicJdSource {
  id: string;
  code: string | null;
  title: string;
  workRegionName: string | null;
  currentVersionNo: number;
  currentVersionId: string;
  rawText: string | null;
  ownerUserId: string | null;
  ownerUserName: string | null;
  ownerDepartmentId: string | null;
  ownerDepartmentName: string | null;
  createTime: string;
}

export interface JdVersion {
  jdCode?: string | null;
  id: string;
  jdId: string;
  versionNo: number;
  title: string;
  rawText: string;
  status: 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED';
  sourceType: 'MANUAL' | 'MAIL' | 'AI_PARSED';
  createId: string;
  createTime: string;
}

export interface JdSource {
  id: string;
  jdVersionId: string;
  sourceType: 'MANUAL' | 'MAIL';
  sourceLabel: string;
  mailSourceId: string | null;
  ownerUserId: string;
  createTime: string;
}

export interface JdFacts {
  factSnapshotId: string;
  jdVersionId: string;
  snapshotVersion: number;
  sourceType: 'AI_PARSE' | 'HUMAN_CORRECTION';
  schemaVersion: string;
  facts: {
    title: string | null;
    summary: string | null;
    professional: {
      coreSkills: Array<{ id: string; name: string; required: boolean; aliases: string[] }>;
      roleExperiences: Array<{ id: string; role: string; level: string | null }>;
      minimumYears: number | null;
      preferredYears: number | null;
      responsibilities: string[];
      industryTags: string[];
      projectTags: string[];
    };
    controlledConditions: {
      ageMin: number | null;
      ageMax: number | null;
      sex: 'ANY' | 'MALE' | 'FEMALE' | null;
      educations: string[];
      credentials: string[];
      languages: Array<{ id: string; name: string; level: string | null }>;
    };
    fulfillmentConditions: {
      locations: string[];
      availability: string | null;
      salaryMin: number | null;
      salaryMax: number | null;
      salaryPeriod: 'MONTH' | 'YEAR' | 'DAY' | 'HOUR' | null;
    };
  };
  createdAt: string;
}

export type WorkflowStepCode =
  | 'CANDIDATE_COMMUNICATION'
  | 'PRE_SUBMISSION_INTERNAL_INTERVIEW'
  | 'CLIENT_SUBMISSION'
  | 'CLIENT_AI_INTERVIEW'
  | 'INTERVIEW_ROUND_1'
  | 'INTERVIEW_ROUND_2'
  | 'INTERVIEW_ROUND_3'
  | 'DECISION'
  | 'OFFER';

export interface WorkflowStep {
  id: string;
  templateVersionId: string;
  stepCode: WorkflowStepCode;
  sequenceNo: number;
  candidateOptional: boolean;
  defaultFollowUpChannel: string | null;
}

export interface WorkflowDetails {
  template: {
    id: string;
    jdVersionId: string;
    versionNo: number;
    recruitmentType: 'INTERNAL' | 'EXTERNAL';
    status: 'DRAFT' | 'ACTIVE' | 'SUPERSEDED';
    version: number;
    activatedAt: string | null;
  };
  steps: WorkflowStep[];
}

export interface ProfessionalWeights {
  skills: number;
  roleExperience: number;
  relatedYears: number;
  responsibilityMatch: number;
  industryAndProjects: number;
}

export interface WeightProfileDetails {
  profile: {
    id: string;
    ownerUserId: string | null;
    name: string;
    status: 'ACTIVE' | 'DISABLED';
    currentVersionNo: number;
    version: number;
    updatedAt: string;
  };
  currentVersion: {
    id: string;
    versionNo: number;
    screeningSettingsJson?: string | null;
    skillsWeight: number;
    roleWeight: number;
    yearsWeight: number;
    responsibilityWeight: number;
    industryProjectWeight: number;
  };
}

export type ScreeningSortMode = 'REFERENCE_SCORE' | 'PROFESSIONAL_SCORE' | 'BONUS_SCORE';
export interface PersonalScreeningSettings {
  criteria: Array<{ code: CriterionCode; level: CriterionLevel; operator: string; expectedValueJson: string; bonusPoints: number; selectableByHr: boolean; reason: string; evidencePolicy: string }>;
  defaultSortMode: ScreeningSortMode;
}

export type CriterionCode = 'AGE' | 'GENDER' | 'EDUCATION' | 'CERTIFICATE' | 'LANGUAGE';
export type CriterionLevel = 'MUST' | 'PREFER' | 'DISPLAY_ONLY';

export interface ControlledCriterion {
  id: string;
  planVersionId: string;
  criterionCode: CriterionCode;
  level: CriterionLevel;
  operator: string;
  expectedValueJson: string;
  bonusPoints: number;
  reason: string;
  evidencePolicy: string;
  selectableByHr: boolean;
  sequenceNo: number;
}

export interface MatchPlanDetails {
  plan: { id: string; jdVersionId: string | null; name: string; currentVersionNo: number };
  version: {
    id: string;
    planId: string;
    jdVersionId: string | null;
    versionNo: number;
    status: 'DRAFT' | 'ACTIVE' | 'SUPERSEDED';
    skillsEnabled: boolean;
    roleEnabled: boolean;
    yearsEnabled: boolean;
    responsibilityEnabled: boolean;
    industryProjectEnabled: boolean;
    version: number;
    activatedAt: string | null;
  };
  criterionPlan: { id: string; planMaxBonusPoints: number };
  criteria: ControlledCriterion[];
}

export interface ActiveScoringConfiguration {
  jdVersionId: string;
  workflowTemplateActive: boolean;
  scoringConfigurationActive: boolean;
  readyForApplication: boolean;
  scoringConfiguration: MatchPlanDetails | null;
}

export interface PersonalPreference {
  id: string;
  ownerUserId: string;
  planVersionId: string;
  weightProfileVersionId: string;
  versionNo: number;
  selectedDefinitionIdsJson: string;
  createdAt: string;
}

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  const value = search.toString();
  return value ? `?${value}` : '';
}

const idempotency = () => createIdempotencyKey();

export const demandApi = {
  client: (id: string) => request<Client>(`/clients/${id}`),
  position: (id: string) => request<ClientPosition>(`/client-positions/${id}`),
  clients: (status?: string, cursor?: string, limit = 20) => request<CursorPage<Client>>(`/clients${query({ status, cursor, limit })}`),
  countClients: (status?: string, signal?: AbortSignal) => collectCount(
    () => request<CountPage>(`/clients/count${query({ status })}`, { signal: signal ?? null }), signal),
  departments: () => request<DepartmentOption[]>('/directory/departments'),
  createClient: (body: { name: string; code?: string }) =>
    request<Client>('/clients', { method: 'POST', body, idempotencyKey: idempotency() }),
  updateClient: (id: string, body: { name: string; version: number }) =>
    request<Client>(`/clients/${id}`, { method: 'PATCH', body }),
  disableClient: (client: Client) =>
    request<Client>(`/clients/${client.id}/disable`, { method: 'POST', body: { version: client.version, reason: 'HR 停用主数据' } }),
  projects: (clientId: string) =>
    request<CursorPage<ClientProject>>(`/clients/${clientId}/projects${query({ limit: 20 })}`),
  createProject: (clientId: string, body: { code: string; name: string }) =>
    request<ClientProject>(`/clients/${clientId}/projects`, { method: 'POST', body, idempotencyKey: idempotency() }),
  updateProject: (id: string, body: { name: string; version: number }) =>
    request<ClientProject>(`/client-projects/${id}`, { method: 'PATCH', body }),
  disableProject: (project: ClientProject) =>
    request<ClientProject>(`/client-projects/${project.id}/disable`, { method: 'POST', body: { version: project.version, reason: 'HR 停用主数据' } }),
  positions: (clientId?: string, projectId?: string, cursor?: string) =>
    request<CursorPage<ClientPosition>>(`/client-positions${query({ clientId, projectId, cursor, limit: 20 })}`),
  createPosition: (body: { clientId: string; projectId?: string; code: string; name: string }) =>
    request<ClientPosition>('/client-positions', { method: 'POST', body, idempotencyKey: idempotency() }),
  updatePosition: (id: string, body: { projectId?: string; name: string; version: number }) =>
    request<ClientPosition>(`/client-positions/${id}`, { method: 'PATCH', body }),
  disablePosition: (position: ClientPosition) =>
    request<ClientPosition>(`/client-positions/${position.id}/disable`, { method: 'POST', body: { version: position.version, reason: 'HR 停用主数据' } }),
  demands: (status?: string, cursor?: string) => request<CursorPage<RecruitmentDemand>>(`/demands${query({ status, cursor, limit: 20 })}`),
  createDemand: (body: Omit<RecruitmentDemand, 'id' | 'status' | 'version' | 'updatedAt' | 'ownerUserId' | 'ownerDepartmentId'> & { collaboratorUserIds?: string[] }) =>
    request<RecruitmentDemand>('/demands', { method: 'POST', body, idempotencyKey: idempotency() }),
  updateDemand: (id: string, body: Record<string, unknown>) =>
    request<RecruitmentDemand>(`/demands/${id}`, { method: 'PATCH', body }),
  transitionDemand: (demand: RecruitmentDemand, action: 'activate' | 'pause' | 'close') =>
    request<RecruitmentDemand>(`/demands/${demand.id}/${action}`, { method: 'POST', body: { version: demand.version, reason: `HR ${action} 招聘需求` } }),
  jds: (search?: string, cursor?: string, limit = 20) => request<CursorPage<JdSummary>>(`/jds${query({ query: search, cursor, limit })}`),
  publicJds: (search?: string, cursor?: string, limit = 20) => request<CursorPage<PublicJdSource>>(`/jds/public-sources${query({ query: search, cursor, limit })}`),
  countPublicJds: (search?: string, signal?: AbortSignal) => collectCount(
    cursor => request<CountPage>(`/jds/public-sources/count${query({ query: search, cursor })}`, { signal: signal ?? null }), signal),
  publicJd: (id: string) => request<PublicJdSource>(`/jds/public-sources/${id}`),
  countJds: (search?: string, signal?: AbortSignal) => collectCount(
    () => request<CountPage>(`/jds/count${query({ query: search })}`, { signal: signal ?? null }), signal),
  jd: (id: string) => request<JdSummary>(`/jds/${id}`),
  createSourceJd: (body: { workRegionName: string; title: string; rawText: string; headcount: number; recruitmentCycleDays: number; ownerDepartmentId: string; recruitmentType: 'INTERNAL' | 'EXTERNAL'; clientId?: string | null; distinction?: string; ownerUserId?: string; appliedFromJdVersionId?: string; contentShared?: boolean }) =>
    request<JdVersion>('/jds', { method: 'POST', body, idempotencyKey: idempotency() }),
  updateJd: (jd: JdSummary, body: { jobTypeCode?: string; workRegionCode?: string; workRegionName?: string; title: string; rawText: string; headcount: number; recruitmentCycleDays: number; ownerDepartmentId: string; ownerUserId?: string; recruitmentType: 'INTERNAL' | 'EXTERNAL'; clientId?: string | null; distinction?: string; contentShared?: boolean }) =>
    request<JdVersion>(`/jds/${jd.id}/versions`, { method: 'POST', body: { ...body, version: jd.version }, idempotencyKey: idempotency() }),
  deleteJd: (jd: JdSummary) => request<void>(`/jds/${jd.id}${query({ version: jd.version })}`, { method: 'DELETE' }),
  jdVersions: (jdId: string) => request<CursorPage<JdVersion>>(`/jds/${jdId}/versions?limit=20`),
  jdVersion: (jdVersionId: string) => request<JdVersion>(`/jd-versions/${jdVersionId}`),
  jdSources: (jdVersionId: string) => request<CursorPage<JdSource>>(`/jd-versions/${jdVersionId}/sources?limit=20`),
  jdVersionsForDemand: (demandId: string) =>
    request<CursorPage<JdVersion>>(`/demands/${demandId}/jd-versions${query({ limit: 20 })}`),
  createJdVersion: (demandId: string, body: { title: string; rawText: string; sourceType: 'MANUAL'; workRegionName?: string }) =>
    request<JdVersion>(`/demands/${demandId}/jd-versions`, { method: 'POST', body, idempotencyKey: idempotency() }),
  parseJd: (jdVersionId: string) =>
    request<ParseTask>(`/jd-versions/${jdVersionId}/parse`, { method: 'POST', idempotencyKey: idempotency() }),
  jdFacts: (jdVersionId: string) => request<JdFacts>(`/jd-versions/${jdVersionId}/facts`),
  workflows: (jdVersionId: string) =>
    request<CursorPage<WorkflowDetails>>(`/jd-versions/${jdVersionId}/workflow-template-versions${query({ limit: 20 })}`),
  createWorkflow: (jdVersionId: string, body: { recruitmentType: string; steps: Array<{ code: WorkflowStepCode; defaultFollowUpChannel?: string }>; version: number }) =>
    request<WorkflowDetails>(`/jd-versions/${jdVersionId}/workflow-template-versions`, { method: 'POST', body, idempotencyKey: idempotency() }),
  activateWorkflow: (workflow: WorkflowDetails) =>
    request<WorkflowDetails>(`/workflow-template-versions/${workflow.template.id}/activate`, { method: 'POST', body: { version: workflow.template.version, reason: 'HR 确认流程并激活' } }),
  activeScoring: (demandId: string) => request<ActiveScoringConfiguration>(`/demands/${demandId}/active-scoring-config`),
  activeScoringForJd: (jdVersionId: string) => request<ActiveScoringConfiguration>(`/jd-versions/${jdVersionId}/active-scoring-config`),
  scoringDefaults: () => request<{ scoringConfiguration: MatchPlanDetails; weightProfile: WeightProfileDetails }>('/scoring-defaults'),
  weightProfiles: (mineOnly = true, cursor?: string, limit = 20) => request<CursorPage<WeightProfileDetails>>(`/weight-profiles${query({ limit, cursor, mineOnly: String(mineOnly) })}`),
  countWeightProfiles: (mineOnly = true, signal?: AbortSignal) => collectCount(
    () => request<CountPage>(`/weight-profiles/count${query({ mineOnly: String(mineOnly) })}`, { signal: signal ?? null }), signal),
  createWeightProfile: (name: string, weights: ProfessionalWeights, settings?: PersonalScreeningSettings, operationKey = idempotency()) =>
    request<WeightProfileDetails>('/weight-profiles', { method: 'POST', body: { name, weights, ...settings }, idempotencyKey: operationKey }),
  updateWeightProfile: (profile: WeightProfileDetails, name: string, weights: ProfessionalWeights, settings?: PersonalScreeningSettings) =>
    request<WeightProfileDetails>(`/weight-profiles/${profile.profile.id}`, { method: 'PATCH', body: { name, weights, ...settings, version: profile.profile.version } }),
  scoringConfigs: (jdVersionId?: string) =>
    request<CursorPage<MatchPlanDetails>>(`/admin/jd-scoring-configs${query({ jdVersionId, limit: 20 })}`),
  createScoringConfig: (body: Record<string, unknown>, operationKey = idempotency()) =>
    request<MatchPlanDetails>('/admin/jd-scoring-configs', { method: 'POST', body, idempotencyKey: operationKey }),
  scoringBinding: (jdVersionId: string) => request<{ jdVersionId: string; planVersionId: string | null; version: string }>(`/admin/jd-versions/${jdVersionId}/scoring-plan`),
  bindScoringPlan: (jdVersionId: string, planVersionId: string | null, version: string) =>
    request(`/admin/jd-versions/${jdVersionId}/scoring-plan`, { method: 'PUT', body: { planVersionId, version } }),
  updateScoringConfig: (id: string, body: Record<string, unknown>, operationKey = idempotency()) =>
    request<MatchPlanDetails>(`/admin/jd-scoring-configs/${id}`, { method: 'PATCH', body, idempotencyKey: operationKey }),
  cloneScoringConfig: (id: string, operationKey = idempotency()) =>
    request<MatchPlanDetails>(`/admin/jd-scoring-configs/${id}/clone`, { method: 'POST', idempotencyKey: operationKey }),
  activateScoringConfig: (config: MatchPlanDetails) =>
    request<MatchPlanDetails>(`/admin/jd-scoring-configs/${config.version.id}/activate`, { method: 'POST', body: { version: config.version.version, reason: '超级管理员校验并激活' } }),
  personalPreference: (configId: string) => request<PersonalPreference | null>(`/me/jd-scoring-configs/${configId}/preferences`),
  savePersonalPreference: (configId: string, weightProfileVersionId: string, selectedDefinitionIds: string[]) =>
    request<PersonalPreference>(`/me/jd-scoring-configs/${configId}/preferences`, { method: 'PUT', body: { weightProfileVersionId, selectedDefinitionIds } }),
};

import { request } from '../api/http';

export interface AnalyticsFilters {
  from?: string;
  to?: string;
  departmentId?: string;
  jdId?: string;
  recruitmentType?: 'INTERNAL' | 'EXTERNAL';
}

export interface AnalyticsTrend {
  date: string;
  applications: number;
  offers: number;
  onboarded: number;
}

export interface AnalyticsChannel {
  id: string;
  name: string;
  resumes: number;
  applications: number;
  offers: number;
  onboarded: number;
}

export interface AnalyticsPortrait {
  dimension: 'AGE' | 'EDUCATION' | 'INTENDED_LOCATION';
  label: string;
  resumes: number;
  passed: number;
  rejected: number;
  pending: number;
  withdrawn: number;
}

export interface AnalyticsHrWorkload {
  ownerUserId: string;
  displayName: string;
  self: boolean;
  inactive: boolean;
  resumes: number;
  interview1: number;
  interview2: number;
  interview3Plus: number;
  applications: number;
  interviews: number;
  offers: number;
  onboarded: number;
  rejected: number;
}

/** 与 RecruitmentAnalyticsResponse 对齐，仅承接授权后的聚合，不返回候选人资料。 */
export interface RecruitmentAnalytics {
  from: string;
  to: string;
  asOf: string;
  demand: { activeJobs: number; headcount: number; unknownHeadcountJobs: number; occupied: number; openHeadcount: number };
  risk: { activeBlacklists: number };
  funnel: Array<{ key: string; label: string; count: number }>;
  trend: AnalyticsTrend[];
  channels: AnalyticsChannel[];
  portraits: AnalyticsPortrait[];
  efficiency: { completedCount: number; averageTotalHours: number | null; activeCount: number; averageActiveHours: number | null };
  departments: Array<{ id: string; name: string }>;
  hrWorkload?: AnalyticsHrWorkload[] | null;
  notes: string[];
}

export const analyticsKeys = {
  report: (filters: AnalyticsFilters) => ['reporting', 'recruitment-analysis', filters] as const,
};

export const analyticsApi = {
  report: (filters: AnalyticsFilters, signal?: AbortSignal) => {
    const query = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]: [string, unknown]) => { if (typeof value === 'string' && value) query.set(key, value); });
    return request<RecruitmentAnalytics>(`/reports/recruitment-analysis?${query}`, signal ? { signal } : {});
  },
};

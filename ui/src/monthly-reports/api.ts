import { downloadFile } from "../api/download";
import { request } from "../api/http";

export type MonthlyReportStatus =
  | "UPLOADING"
  | "VALIDATING"
  | "INVALID"
  | "READY"
  | "QUEUED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLING"
  | "CANCELLED"
  | "EXPIRED";

export interface MonthlyReportSummary {
  id: string;
  month: string;
  asOfDate: string;
  status: MonthlyReportStatus;
  version: string;
  createTime: string;
  createName: string;
  offerCount: number | null;
  totalScore: number | null;
  errorCode: string | null;
  errorDetail: string | null;
}

export interface MonthlyReportFile {
  kind: "OFFER" | "DEMAND" | "REPORT";
  fileName: string;
  sizeBytes: number;
}

export interface MonthlyReportDiagnostic {
  severity: "ERROR" | "WARNING" | "INFO";
  code: string;
  message: string;
  location?: string;
  source?: string;
  sheet?: string;
  row?: number;
  column?: string;
  count?: number;
}

export interface MonthlyReportDimensionMetric {
  dimension: string;
  key: string;
  group: string | null;
  count: number;
  score: number;
  averageScore: number;
  statusCounts: Record<string, number>;
  countShare: number | null;
  scoreShare: number | null;
}

export interface MonthlyReportSnapshot {
  ruleVersion: string;
  parserVersion: string;
  templateVersion: string;
  reportMonth: string;
  dataAsOf: string;
  scope: string;
  ready: boolean;
  offerSummary: {
    totalCount: number;
    scoredCount: number;
    totalScore: number;
    statusCounts: Record<string, number>;
    rejectedRate: number | null;
    nonRejectedRate: number | null;
    acceptedOrOnboardedRate: number | null;
    onboardedRate: number | null;
    dateFrom: string;
    dateTo: string;
    excludedCounts: Record<string, number>;
  };
  demandSummary: {
    approvedDemandCount: number;
    approvedHeadcount: number;
    monthAppliedDemandCount: number;
    monthAppliedHeadcount: number;
    monthExpectedDemandCount: number;
    monthExpectedHeadcount: number;
  };
  dimensions: MonthlyReportDimensionMetric[];
  analysis: Array<{
    ruleId: string;
    metricKey: string;
    dimension: string;
    value: unknown;
    text: string;
    recommendation: string | null;
  }>;
  diagnostics: MonthlyReportDiagnostic[];
}

export interface MonthlyReportDetail extends MonthlyReportSummary {
  taskId: string | null;
  progressPercent: number | null;
  retryable: boolean;
  remarks: string | null;
  files: MonthlyReportFile[];
  diagnostics: MonthlyReportDiagnostic[];
  snapshot: MonthlyReportSnapshot | null;
}

export interface MonthlyReportPage {
  items: MonthlyReportSummary[];
  nextCursor: string | null;
}

export const activeMonthlyReportStatuses = new Set<MonthlyReportStatus>([
  "UPLOADING",
  "VALIDATING",
  "QUEUED",
  "RUNNING",
  "CANCELLING",
]);

export const monthlyReportApi = {
  list: (month?: string, cursor?: string) => {
    const search = new URLSearchParams({ limit: "20" });
    if (month) search.set("month", month);
    if (cursor) search.set("cursor", cursor);
    return request<MonthlyReportPage>(`/monthly-reports?${search}`);
  },
  detail: (id: string, signal?: AbortSignal) =>
    request<MonthlyReportDetail>(
      `/monthly-reports/${id}`,
      signal ? { signal } : {},
    ),
  create: (
    values: {
      offerFile: File;
      demandFile: File;
      month: string;
      asOfDate: string;
    },
    idempotencyKey: string,
  ) => {
    const body = new FormData();
    body.append("offerFile", values.offerFile);
    body.append("demandFile", values.demandFile);
    body.append("month", values.month);
    body.append("asOfDate", values.asOfDate);
    return request<MonthlyReportDetail>("/monthly-reports", {
      method: "POST",
      body,
      idempotencyKey,
    });
  },
  generate: (
    id: string,
    body: {
      version: string;
      remarks: string | null;
      acknowledgeWarnings: boolean;
    },
    idempotencyKey: string,
  ) =>
    request<MonthlyReportDetail>(`/monthly-reports/${id}/generate`, {
      method: "POST",
      body,
      idempotencyKey,
    }),
  retry: (
    id: string,
    version: string,
    reason: string,
    idempotencyKey: string,
  ) =>
    request<MonthlyReportDetail>(`/monthly-reports/${id}/retry`, {
      method: "POST",
      body: { version, reason },
      idempotencyKey,
    }),
  cancel: (
    id: string,
    version: string,
    reason: string,
    idempotencyKey: string,
  ) =>
    request<MonthlyReportDetail>(`/monthly-reports/${id}/cancel`, {
      method: "POST",
      body: { version, reason },
      idempotencyKey,
    }),
  download: (id: string, fileName: string) =>
    downloadFile(`/monthly-reports/${id}/download`, fileName),
  delete: (id: string, version: string, reason: string) =>
    request<void>(`/monthly-reports/${id}`, {
      method: "DELETE",
      body: { version, reason },
    }),
};

export interface AsyncTaskResponse {
  "taskId"?: string | null;
  "resourceId"?: string | null;
  "resourceType"?: string | null;
  "taskType"?: string | null;
  "status"?: string | null;
  "attemptCount"?: number | null;
  "maxAttempts"?: number | null;
  "progressPercent"?: number | null;
  "errorCode"?: string | null;
  "errorDetail"?: string | null;
  "retryable"?: boolean | null;
  "resultResourceId"?: string | null;
  "version"?: string | null;
  "createTime"?: string | null;
  "startedAt"?: string | null;
  "finishedAt"?: string | null;
  "updateTime"?: string | null;
  "requestedByUserId"?: string | null;
  "ownerUserId"?: string | null;
}

export interface CursorPageAsyncTaskResponse {
  "items"?: Array<AsyncTaskResponse> | null;
  "nextCursor"?: string | null;
  "hasMore"?: boolean | null;
}

export interface ScheduledChannelResponse {
  "code"?: string | null;
  "lastTriggeredAt"?: string | null;
  "lastCompletedAt"?: string | null;
  "triggers"?: number | null;
  "coalesced"?: number | null;
  "rejected"?: number | null;
  "failed"?: number | null;
  "queued"?: number | null;
  "running"?: number | null;
  "recentScans"?: Array<ScheduledScanResponse> | null;
}

export interface ScheduledScanResponse {
  "runId"?: string | null;
  "state"?: string | null;
  "queuedAt"?: string | null;
  "startedAt"?: string | null;
  "finishedAt"?: string | null;
  "durationMillis"?: number | null;
  "errorCode"?: string | null;
}

export interface ScheduledTaskResponse {
  "code"?: string | null;
  "name"?: string | null;
  "module"?: string | null;
  "enabled"?: boolean | null;
  "scheduleType"?: string | null;
  "expression"?: string | null;
  "zone"?: string | null;
  "intervalMillis"?: number | null;
  "nextCronAt"?: string | null;
  "businessTypes"?: Array<string> | null;
  "channels"?: Array<ScheduledChannelResponse> | null;
  "idleBackoffMaxMillis"?: number | null;
  "persistentBacklogStatus"?: string | null;
  "persistentReadyCount"?: number | null;
  "oldestReadyAt"?: string | null;
}

export interface TaskCenterOverviewResponse {
  "instanceId"?: string | null;
  "observedSince"?: string | null;
  "sampledAt"?: string | null;
  "tasks"?: Array<ScheduledTaskResponse> | null;
}

import { request } from '../api/http';
import { normalizeDates } from '../api/dates';

export type TaskStatus = 'QUEUED' | 'RUNNING' | 'CANCELLING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export interface AsyncTask {
  taskId: string;
  resourceId: string;
  resourceType: string;
  taskType: string;
  status: TaskStatus;
  attemptCount: number;
  maxAttempts: number;
  progressPercent: number;
  errorCode: string | null;
  errorDetail: string | null;
  retryable: boolean;
  resultResourceId: string | null;
  version: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  updatedAt: string;
  requestedByUserId?: string | null;
  ownerUserId?: string | null;
}

export function taskPollingInterval(task?: Pick<AsyncTask, 'status'>): number | false {
  return task && ['QUEUED', 'RUNNING', 'CANCELLING'].includes(task.status) ? 2_000 : false;
}

export const taskApi = {
  latestResume: (resumeVersionId: string) => request<AsyncTask[]>(`/tasks?${new URLSearchParams({taskType:'RESUME_PARSE',resourceType:'RESUME_VERSION',resourceId:resumeVersionId,limit:'1'})}`)
    .then(items=>items.find(item=>item.resourceId===resumeVersionId&&item.resourceType==='RESUME_VERSION'&&item.taskType==='RESUME_PARSE'))
    .then(item=>item?normalizeDates(item):undefined),
  get: (taskId: string) => request<AsyncTask>(`/tasks/${taskId}`).then(normalizeDates),
  cancel: (taskId: string) => request<AsyncTask>(`/tasks/${taskId}/cancel`, { method: 'POST', body: { reason: 'HR 取消解析任务' } }),
  retry: (taskId: string) => request<AsyncTask>(`/tasks/${taskId}/retry-failed`, { method: 'POST', body: { reason: 'HR 重试失败任务' } }),
};

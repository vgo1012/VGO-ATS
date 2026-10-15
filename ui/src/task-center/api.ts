import { z } from 'zod';
import { request } from '../api/http';
import type { TaskCenterOverviewResponse, CursorPageAsyncTaskResponse } from '../api/taskCenter.generated';

const time = z.string().datetime({ offset: true });
const count = z.number().int().nonnegative();
const scanSchema = z.object({
  runId: z.string(), state: z.string(), queuedAt: time, startedAt: time.nullable(),
  finishedAt: time.nullable(), durationMillis: count.nullable(), errorCode: z.string().nullable(),
});
const channelSchema = z.object({
  code: z.string(), lastTriggeredAt: time.nullable(), lastCompletedAt: time.nullable(),
  triggers: count, coalesced: count, rejected: count, failed: count, queued: count, running: count,
  recentScans: z.array(scanSchema),
});
const scheduleSchema = z.object({
  code: z.string(), name: z.string(), module: z.string(), enabled: z.boolean(),
  scheduleType: z.enum(['CRON', 'FIXED_DELAY']), expression: z.string(), zone: z.string(),
  intervalMillis: count.nullable(), nextCronAt: time.nullable(), businessTypes: z.array(z.string()),
  channels: z.array(channelSchema),
  idleBackoffMaxMillis: count.optional(),
  persistentBacklogStatus: z.enum(['AVAILABLE', 'UNAVAILABLE', 'UNSUPPORTED']),
  persistentReadyCount: count.nullable(), oldestReadyAt: time.nullable(),
}).superRefine((task, context) => {
  if (task.persistentBacklogStatus !== 'AVAILABLE') {
    if (task.persistentReadyCount !== null || task.oldestReadyAt !== null) {
      context.addIssue({ code: 'custom', message: '不可用的积压统计不得携带数值' });
    }
    return;
  }
  if (task.persistentReadyCount === null) {
    context.addIssue({ code: 'custom', message: '可用的积压统计必须携带数量' });
  } else if ((task.persistentReadyCount === 0) !== (task.oldestReadyAt === null)) {
    context.addIssue({ code: 'custom', message: '积压数量与最早等待时间不一致' });
  }
});
export const overviewSchema = z.object({
  instanceId: z.string(), observedSince: time, sampledAt: time, tasks: z.array(scheduleSchema),
});
const businessTaskSchema = z.object({
  taskId: z.string(), taskType: z.string(), resourceId: z.string(), resultResourceId: z.string().nullable(), status: z.string(), progressPercent: count,
  attemptCount: count, maxAttempts: count, errorCode: z.string().nullable(), errorDetail: z.string().nullable(),
  createTime: time, startedAt: time.nullable(), finishedAt: time.nullable(),
});
export const historySchema = z.object({
  items: z.array(businessTaskSchema), hasMore: z.boolean(), nextCursor: z.string().nullable(),
}).refine(page => !page.hasMore || Boolean(page.nextCursor), '下一页游标缺失');

export type TaskCenterOverview = z.infer<typeof overviewSchema>;
export type Schedule = z.infer<typeof scheduleSchema>;
export type Scan = z.infer<typeof scanSchema>;
export type BusinessTask = z.infer<typeof businessTaskSchema>;

function parseResponse<Output>(schema: z.ZodType<Output>, value: unknown): Output {
  const parsed = schema.safeParse(value);
  if (!parsed.success) throw new Error('任务中心返回数据不完整，请刷新或联系管理员');
  return parsed.data;
}

export const taskCenterKeys = {
  overview: ['admin-task-center', 'overview'] as const,
  history: (code: string) => ['admin-task-center', 'history', code] as const,
};

export const taskCenterApi = {
  overview: (signal?: AbortSignal) => request<TaskCenterOverviewResponse>('/admin/task-center', { ...(signal ? { signal } : {}) })
    .then(value => parseResponse(overviewSchema, value)),
  history: (code: string, cursor: string, signal?: AbortSignal) => {
    const params = new URLSearchParams({ jobCode: code, limit: '20' });
    if (cursor) params.set('cursor', cursor);
    return request<CursorPageAsyncTaskResponse>(`/admin/task-center/history?${params}`, { ...(signal ? { signal } : {}) })
      .then(value => parseResponse(historySchema, value));
  },
};

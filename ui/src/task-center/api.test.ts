import { describe, expect, it } from 'vitest';
import { historySchema, overviewSchema, type Schedule } from './api';
import { backlogAge, scheduleState, taskTime } from './presentation';

const task: Schedule = {
  code: 'RESUME_PARSE', name: '简历解析任务轮询', module: '简历解析', enabled: true,
  scheduleType: 'FIXED_DELAY', expression: '2000', zone: 'Asia/Shanghai', intervalMillis: 2000,
  nextCronAt: null, businessTypes: ['RESUME_PARSE'], persistentBacklogStatus: 'AVAILABLE',
  persistentReadyCount: 0, oldestReadyAt: null, channels: [{
    code: 'RESUME_PARSE', lastTriggeredAt: null, lastCompletedAt: null, triggers: 0,
    coalesced: 0, rejected: 0, failed: 0, queued: 0, running: 0, recentScans: [],
  }],
};

describe('task center contract and status semantics', () => {
  it('accepts nullable observation times but rejects incomplete success payloads', () => {
    expect(overviewSchema.safeParse({ instanceId: 'one', observedSince: '2026-09-16T00:00:00Z', sampledAt: '2026-09-16T00:00:01Z', tasks: [task] }).success).toBe(true);
    expect(overviewSchema.safeParse({ instanceId: 'one', observedSince: '2026-09-16T00:00:00Z', sampledAt: '2026-09-16T00:00:01Z', tasks: [{ ...task, persistentReadyCount: 2, oldestReadyAt: null }] }).success).toBe(false);
    expect(overviewSchema.safeParse({ instanceId: 'one', observedSince: '2026-09-16T00:00:00Z', sampledAt: '2026-09-16T00:00:01Z', tasks: [{ ...task, persistentBacklogStatus: 'UNAVAILABLE', persistentReadyCount: 0 }] }).success).toBe(false);
    expect(overviewSchema.safeParse({ tasks: [] }).success).toBe(false);
    expect(historySchema.safeParse({ items: [], hasMore: true, nextCursor: null }).success).toBe(false);
  });

  it('does not present disabled, queued, or unobserved tasks as business success', () => {
    expect(scheduleState({ ...task, enabled: false }).label).toBe('未启用');
    expect(scheduleState(task).label).toBe('尚未观测到触发');
    const channel = task.channels[0]!;
    expect(scheduleState({ ...task, channels: [{ ...channel, queued: 1 }] }).label).toBe('已排队');
    expect(scheduleState({ ...task, channels: [{ ...channel, running: 1 }] }).label).toBe('扫描中');
    expect(scheduleState({ ...task, channels: [{ ...channel, lastTriggeredAt: '2026-09-16T00:00:00Z' }] }).label).toBe('轮次已完成');
  });

  it('uses Beijing time rather than the browser machine timezone', () => {
    expect(taskTime('2026-09-15T17:00:00Z')).toContain('01:00:00');
    expect(taskTime(null)).toBe('—');
  });

  it('formats the oldest ready age from the server sample time', () => {
    expect(backlogAge('2026-09-16T03:05:00Z', '2026-09-16T01:00:00Z')).toBe('2 小时 5 分钟');
    expect(backlogAge('2026-09-18T03:05:00Z', '2026-09-16T01:00:00Z')).toBe('2 天 2 小时');
    expect(backlogAge('2026-09-16T03:05:00Z', null)).toBe('—');
  });
});

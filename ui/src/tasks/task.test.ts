import { afterEach, describe, expect, it, vi } from 'vitest';
import { taskApi, taskPollingInterval } from './task';

describe('async task polling', () => {
  afterEach(()=>vi.restoreAllMocks());

  it('recovers only the task for the requested resume version and normalizes server timestamps', async()=>{
    const fetch=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify([{taskId:'task-1',resourceId:'resume-1',resourceType:'RESUME_VERSION',taskType:'RESUME_PARSE',status:'RUNNING',createTime:'2026-09-07T00:00:00Z'}]),{headers:{'content-type':'application/json'}}));
    await expect(taskApi.latestResume('resume-1')).resolves.toMatchObject({taskId:'task-1',status:'RUNNING',createdAt:'2026-09-07T00:00:00Z'});
    expect(fetch.mock.calls[0]?.[0]).toEqual(expect.stringContaining('resourceId=resume-1'));
    fetch.mockResolvedValue(new Response(JSON.stringify([{taskId:'other',resourceId:'resume-2',resourceType:'RESUME_VERSION',taskType:'RESUME_PARSE'}]),{headers:{'content-type':'application/json'}}));
    await expect(taskApi.latestResume('resume-1')).resolves.toBeUndefined();
  });
  it.each(['QUEUED', 'RUNNING', 'CANCELLING'] as const)('polls %s tasks', (status) => {
    expect(taskPollingInterval({ status })).toBe(2_000);
  });

  it.each(['SUCCEEDED', 'FAILED', 'CANCELLED'] as const)('stops polling %s tasks', (status) => {
    expect(taskPollingInterval({ status })).toBe(false);
  });

  it('does not poll before a task exists', () => {
    expect(taskPollingInterval()).toBe(false);
  });
});

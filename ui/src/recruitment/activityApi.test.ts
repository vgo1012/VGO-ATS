import { QueryClient } from '@tanstack/react-query';
import { expect, it } from 'vitest';
import { refreshRecruitmentActivity } from './activityApi';

it('招聘操作使列表、日程详情、面评历史和待反馈缓存同时失效', async () => {
  const client = new QueryClient();
  const keys = [['application', 'app'], ['applications'], ['interviews'], ['interview', 'interview-a'], ['interview-history', 'interview-a', 'feedback'], ['interview-tasks'], ['follow-up-plans'], ['application-interview-feedback', 'app']];
  for (const key of keys) client.setQueryData(key, { version: 1 });
  client.setQueryData(['application', 'other-app'], { version: 1 });
  await refreshRecruitmentActivity(client, 'app');
  for (const key of keys) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  expect(client.getQueryState(['application', 'other-app'])?.isInvalidated).toBe(false);
  client.clear();
});

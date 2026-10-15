import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('日程周视图与列表分页直接使用摘要，不逐场读取面试材料', async ({ page }) => {
  await reviewWorkspace(page);
  const interviews = Array.from({ length: 21 }, (_, index) => ({
    id: `summary-${index + 1}`, applicationId: `application-${index + 1}`, candidateId: `candidate-${index + 1}`,
    candidateName: `摘要候选人${index + 1}`, jdId: 'jd-a', jdVersionId: 'historical-jd-version',
    jdTitle: '历史版本岗位', jdCode: 'JD-HISTORY-007', workflowStepId: `step-${index + 1}`,
    stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'SCHEDULED',
    scheduledStartAt: '2026-09-07T02:00:00Z', scheduledEndAt: '2026-09-07T02:30:00Z',
    timezoneId: 'Asia/Shanghai', meetingMode: 'ONSITE', locationText: '会议室', nextResponsibleUserId: 'hr-a',
  }));
  const materialRequests: string[] = [];
  const cursors: (string | null)[] = [];
  page.on('request', request => {
    if (/\/interviews\/[^/]+\/materials(?:[/?]|$)/.test(request.url())) materialRequests.push(request.url());
  });
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/interviews') {
      const cursor = url.searchParams.get('cursor');
      cursors.push(cursor);
      return route.fulfill({ json: cursor
        ? { items: interviews.slice(20), hasMore: false, nextCursor: null }
        : { items: interviews.slice(0, 20), hasMore: true, nextCursor: 'summary-next-page' } });
    }
    if (path === '/my/interview-tasks') return route.fulfill({ json: [{
      ...interviews[0], interviewId: 'task-outside-current-page', candidateName: '独立待反馈候选人',
      feedbackDueAt: '2026-09-07T03:00:00Z', submitted: false, overdue: false,
    }] });
    return route.fallback();
  });

  await page.goto('/schedule');
  const calendar = page.getByRole('region', { name: '所选周面试日历' });
  await expect(calendar.locator('.interview-event')).toHaveCount(20);
  await expect(calendar.getByText('摘要候选人20', { exact: true })).toBeAttached();
  await expect(calendar.getByText('历史版本岗位 · JD-HISTORY-007', { exact: true })).toHaveCount(20);
  await expect(page.getByRole('region', { name: '待反馈' }).getByText('独立待反馈候选人 · 待面评', { exact: true })).toBeVisible();
  expect(cursors).toEqual([null]);
  expect(materialRequests).toEqual([]);

  await page.getByRole('button', { name: '加载更多面试', exact: true }).click();
  await expect(calendar.locator('.interview-event')).toHaveCount(21);
  await expect(calendar.getByText('摘要候选人21', { exact: true })).toBeAttached();
  expect(cursors).toEqual([null, 'summary-next-page']);

  await page.getByRole('button', { name: '自定义', exact: true }).click();
  const table = page.locator('.schedule-main table');
  await expect(table.getByRole('row')).toHaveCount(21);
  await expect(table.getByText('历史版本岗位 · JD-HISTORY-007', { exact: true })).toHaveCount(20);
  await page.getByRole('button', { name: '加载更多面试', exact: true }).click();
  await expect(table.getByRole('row')).toHaveCount(22);
  await expect(table.getByRole('button', { name: '摘要候选人21', exact: true })).toBeAttached();
  await expect(table.getByText('历史版本岗位 · JD-HISTORY-007', { exact: true })).toHaveCount(21);
  expect(cursors).toEqual([null, 'summary-next-page', null, 'summary-next-page']);
  expect(materialRequests).toEqual([]);

  await page.getByRole('button', { name: /^本\s*周$/ }).click();
  await expect(calendar.locator('.interview-event')).toHaveCount(21);
  expect(materialRequests).toEqual([]);
});

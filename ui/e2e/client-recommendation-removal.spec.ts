import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('旧客户推荐阶段不再展示功能，保留面试与决定入口', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const application = { id: 'app-legacy', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-a', recruitmentType: 'EXTERNAL', clientName: '示例公司', currentStage: 'IN_PROCESS', currentStepCode: 'CLIENT_SUBMISSION', currentStepStatus: 'IN_PROGRESS', ownerUserId: 'hr-a', terminalAt: null };
  const submissionReads: string[] = [];
  page.on('request', request => { if (/client-submissions|submission-eligibility/.test(request.url())) submissionReads.push(request.url()); });
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: [application], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/applications/app-legacy', route => route.fulfill({ json: { application, collaboratorUserIds: [], workflowSteps: [{ id: 'old-step', stepCode: 'CLIENT_SUBMISSION', sequenceNo: 2, status: 'IN_PROGRESS' }], decisions: [], offerStatusRecords: [], elapsedSeconds: 60 } }));
  await page.route('**/api/v1/interviews?**', route => route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } }));
  await page.goto('/recruitment');
  const stage = page.locator('.recruitment-stage');
  await expect(stage.getByRole('button', { name: '待定', exact: true })).toHaveClass(/selected/);
  await expect(stage).not.toContainText('推荐给客户');
  await page.getByRole('button', { name: '详情与版本', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '招聘活动详情', exact: true });
  await expect(detail.getByRole('button', { name: '查看简历与 JD' })).toBeVisible();
  await expect(detail.getByText(/客户推荐与反馈|推荐给客户/)).toHaveCount(0);
  expect(submissionReads).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('without-client-recommendation.png'), animations: 'disabled' });
});

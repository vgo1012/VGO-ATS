import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Locator, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const applicationId = 'single-window-app';
const candidateId = 'person-a';
const actorId = 'hr-a';
const now = '2026-09-08T03:00:00Z';

type Step = { id: string; stepCode: string; roundNo: number; status: string; version: number };

function application(steps: Step[]) {
  return {
    application: { id: applicationId, candidateId, demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-a', workflowTemplateVersionId: 'workflow-v1', matchPlanVersionId: 'plan-v1', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', outcome: null, ownerUserId: actorId, ownerDepartmentId: 'dept-a', terminalAt: null, version: 1, createTime: now, updateTime: now },
    collaboratorUserIds: [], workflowSteps: steps.map((step, index) => ({ ...step, applicationId, templateVersionId: 'workflow-v1', stepDefinitionId: `definition-${step.id}`, sequenceNo: index + 1, candidateOptional: false, defaultFollowUpChannel: null, ownerUserId: actorId, dueAt: null, startedAt: now, completedAt: ['PASSED', 'FAILED'].includes(step.status) ? now : null, resultReason: step.status === 'PASSED' ? '正式面评已提交，本轮结果：通过' : null, clientSubmissionId: null })), decisions: [], offerStatusRecords: [], elapsedSeconds: 0,
  };
}

function interview(id: string, step: Step, status: 'SCHEDULED' | 'COMPLETED' = 'SCHEDULED') {
  return { id, applicationId, candidateId, jdId: 'jd-a', jdVersionId: 'jd-v2', workflowTemplateVersionId: 'workflow-v1', workflowStepId: step.id, stepCode: step.stepCode, interviewType: 'INTERNAL', roundNo: step.roundNo, clientSubmissionId: null, status, scheduledStartAt: '2026-09-09T03:00:00Z', scheduledEndAt: '2026-09-09T03:30:00Z', timezoneId: 'Asia/Shanghai', meetingMode: 'ONLINE', locationText: null, meetingUrl: null, candidateNotificationChannel: 'NONE', candidateConfirmationStatus: 'PENDING', ownerUserId: actorId, ownerDepartmentId: 'dept-a', nextResponsibleUserId: actorId, version: 1, createdByUserId: actorId, createdAt: now, updatedByUserId: actorId, updatedAt: now };
}

async function fixture(page: Page) {
  await reviewWorkspace(page);
  let steps: Step[] = [{ id: 'step-1', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, status: 'PENDING', version: 1 }];
  const interviews: ReturnType<typeof interview>[] = [];
  const feedback = new Set<string>();
  const state = { historyReads: 0 };
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/directory/users') return route.fulfill({ json: { ...empty, items: [{ id: actorId, displayName: '陈佳', status: 'ACTIVE' }, { id: 'actual-interviewer', displayName: '王面试官', status: 'ACTIVE' }] } });
    if (path === '/applications') return route.fulfill({ json: { ...empty, items: [application(steps).application] } });
    if (path === `/applications/${applicationId}`) return route.fulfill({ json: application(steps) });
    if (path === `/applications/${applicationId}/intentions` || path === `/applications/${applicationId}/communications`) return route.fulfill({ json: [] });
    if (path === '/interviews' && request.method() === 'GET') {
      const workflowStepId = url.searchParams.get('workflowStepId');
      const status = url.searchParams.get('status');
      return route.fulfill({
        json: {
          ...empty,
          items: interviews.filter(value =>
            (!workflowStepId || value.workflowStepId === workflowStepId)
            && (!status || value.status === status),
          ),
        },
      });
    }
    if (path === '/interviews' && request.method() === 'POST') {
      const body = request.postDataJSON() as { workflowStepId: string };
      const step = steps.find(value => value.id === body.workflowStepId)!;
      const value = interview(`interview-${step.roundNo}`, step);
      interviews.push(value);
      return route.fulfill({ json: { interview: value, participants: [{ interviewId: value.id, userId: 'actual-interviewer', participantRole: 'INTERVIEWER', responseStatus: 'ACCEPTED', createdAt: now }] } });
    }
    const match = path.match(/^\/interviews\/(interview-\d+)(?:\/(.*))?$/);
    if (match) {
      const value = interviews.find(item => item.id === match[1])!;
      const operation = match[2];
      if (!operation && request.method() === 'GET') return route.fulfill({ json: { interview: value, participants: [{ interviewId: value.id, userId: 'actual-interviewer', participantRole: 'INTERVIEWER', responseStatus: 'ACCEPTED', createdAt: now }] } });
      if (operation === 'feedback' && request.method() === 'GET') return route.fulfill({ json: { ...empty, items: feedback.has(value.id) ? [{ feedback: { id: `feedback-${value.id}`, interviewId: value.id, authorUserId: actorId, feedbackKind: 'INTERNAL', status: 'SUBMITTED', currentVersionNo: 1, createdAt: now, updatedAt: now }, currentVersion: { id: `version-${value.id}`, feedbackId: `feedback-${value.id}`, versionNo: 1, verdict: 'PASS', shortComment: '通过', externalFeedbackSource: null, feedbackOccurredAt: now, changeReason: '面试结论', createdByUserId: actorId, createdAt: now } }] : [] } });
      if (operation === 'feedback' && request.method() === 'POST') {
        feedback.add(value.id); value.status = 'COMPLETED';
        return route.fulfill({ json: { feedback: { id: `feedback-${value.id}`, interviewId: value.id, authorUserId: actorId, feedbackKind: 'INTERNAL', status: 'SUBMITTED', currentVersionNo: 1, createdAt: now, updatedAt: now }, currentVersion: { id: `version-${value.id}`, feedbackId: `feedback-${value.id}`, versionNo: 1, verdict: 'PASS', shortComment: '通过', externalFeedbackSource: null, feedbackOccurredAt: now, changeReason: '面试结论', createdByUserId: actorId, createdAt: now } } });
      }
      if (operation === 'reschedule') return route.fulfill({ json: { interview: value, participants: [{ interviewId: value.id, userId: 'actual-interviewer', participantRole: 'INTERVIEWER', responseStatus: 'ACCEPTED', createdAt: now }] } });
      if (operation === 'schedule-history') { state.historyReads++; return route.fulfill({ json: empty }); }
      if (operation === 'materials') return route.fulfill({ json: { interviewId: value.id, candidateId, candidateName: '同窗候选人', jdId: 'jd-a', jdVersionId: 'jd-v2', jdVersionNo: 2, jdTitle: '高级前端工程师', jdRawText: '岗位材料', resumeVersionId: 'resume-a', resumeVersionNo: 1, resumeFileName: '简历.pdf', resumeMediaType: 'application/pdf', resumeSizeBytes: 1 } });
    }
    const complete = path.match(new RegExp(`^/applications/${applicationId}/workflow-steps/(step-\\d+)/complete$`));
    if (complete) { const step = steps.find(value => value.id === complete[1])!; step.status = 'PASSED'; step.version++; return route.fulfill({ json: application(steps) }); }
    if (path === `/applications/${applicationId}/next-actions`) {
      const round = steps.length + 1;
      const step = { id: `step-${round}`, stepCode: 'INTERVIEW_ROUND_1', roundNo: round, status: 'PENDING', version: 1 };
      steps = [...steps, step];
      return route.fulfill({ json: application(steps) });
    }
    if (path === `/applications/${applicationId}/activity-records`) return route.fulfill({ json: { ...empty, items: [] } });
    return route.fallback();
  });
  return state;
}

async function schedule(page: Page, dialog: Locator) {
  await dialog.getByLabel('实际面试官').click();
  await page.getByTitle('王面试官', { exact: true }).click();
  await dialog.getByLabel('实际面试官').press('Escape');
  await dialog.getByRole('button', { name: /^保\s*存$/ }).click();
}

async function submitFeedback(dialog: Locator) {
  await dialog.getByLabel('面评',{exact:true}).fill('面试结论');
  await dialog.getByRole('button', { name: '正式提交面评', exact: true }).click();
}

test('一二三面通过轮次入口连续排期和正式面评', async ({ page }, testInfo) => {
  const state = await fixture(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/recruitment');
  await page.getByRole('button', { name: '面试', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '面试', exact: true });
  await expect(dialog.getByLabel('开始时间', { exact: true })).toBeVisible();
  await dialog.evaluate(node => node.setAttribute('data-single-window', 'original'));
  const original = page.locator('[data-single-window="original"]');

  await schedule(page, dialog);
  await expect(original).toBeVisible();
  await expect(dialog.getByRole('button', { name: '正式提交面评', exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: /^改\s*期$/ })).toHaveCount(0);
  await submitFeedback(dialog);
  await expect(original).toBeVisible();
  await expect(dialog.getByText('面评已正式提交', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await page.getByRole('button', { name: '第 2 轮面试', exact: true }).click();
  await expect(dialog.getByLabel('开始时间', { exact: true })).toBeVisible();
  await schedule(page, dialog);
  await submitFeedback(dialog);
  await expect(dialog.getByText('面评已正式提交', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  await page.getByRole('button', { name: '第 3 轮面试', exact: true }).click();
  await expect(dialog.getByLabel('开始时间', { exact: true })).toBeVisible();
  await schedule(page, dialog);
  await expect(dialog.getByRole('button', { name: '正式提交面评', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(1);
  expect(state.historyReads).toBe(0);

  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`third-round-${width}.png`), fullPage: true });
  }
  await page.screenshot({ path: testInfo.outputPath('third-round.png'), fullPage: true });
  expect(errors).toEqual([]);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
});

test('只有旧面评权限的账号不能进入待反馈填写入口', async ({ page }) => {
  await fixture(page);
  let taskRequests=0;
  page.on('request',request=>{if(new URL(request.url()).pathname==='/api/v1/my/interview-tasks')taskRequests++;});
  await page.route('**/api/v1/me',route=>route.fulfill({json:{userId:'legacy-interviewer',displayName:'历史面试官',permissions:['interview.feedback.submit-own'],roles:['INTERVIEWER'],superAdmin:false}}));
  await page.goto('/schedule');
  await expect(page.getByRole('main')).toContainText('当前账号没有此页面的访问权限。');
  await expect(page.getByRole('button', { name: '填写面评', exact: true })).toHaveCount(0);
  expect(taskRequests).toBe(0);
});

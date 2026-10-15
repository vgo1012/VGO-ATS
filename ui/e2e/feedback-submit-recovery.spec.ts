import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const instant = '2026-09-07T01:30:00Z';

async function fixture(page: Page, rejection?: number) {
  await reviewWorkspace(page);
  const interview = { id: 'feedback-interview', applicationId: 'feedback-app', candidateId: 'person-a', candidateName: '面评恢复候选人', jdId: 'jd-a', jdVersionId: 'jd-v2', jdTitle: '面评恢复岗位',
    workflowStepId: 'feedback-step', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'SCHEDULED',
    scheduledStartAt: '2026-09-07T02:00:00Z', scheduledEndAt: '2026-09-07T03:00:00Z', timezoneId: 'Asia/Shanghai',
    meetingMode: 'ONSITE', locationText: '面评恢复会议室', candidateConfirmationStatus: 'PENDING', nextResponsibleUserId: 'hr-a', ownerUserId: 'hr-a', version: 1 };
  const step = { id: 'feedback-step', applicationId: 'feedback-app', templateVersionId: 'workflow-v1', stepDefinitionId: 'definition-1',
    stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, sequenceNo: 1, candidateOptional: false, status: 'IN_PROGRESS', ownerUserId: 'hr-a', version: 1 };
  const application = { id: 'feedback-app', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-a',
    workflowTemplateVersionId: 'workflow-v1', matchPlanVersionId: 'match-v1', recruitmentType: 'INTERNAL', progressiveFollowUp: true,
    currentStage: 'IN_PROCESS', outcome: null, ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', terminalAt: null,
    version: 1, createId: 'hr-a', createTime: instant, updateId: 'hr-a', updateTime: instant };
  const details = { application, collaboratorUserIds: [], workflowSteps: [step], decisions: [], offerStatusRecords: [], elapsedSeconds: 300 };
  const requests: Array<{ key: string; body: Record<string, unknown> }> = [];
  const saved = new Map<string, { feedback: Record<string, unknown>; currentVersion: Record<string, unknown> }>();
  const state = { wait: undefined as Promise<void> | undefined, completedSteps: 0 };
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    if (path === '/interview-feedback-batch') return route.fulfill({ json: { ...empty, visibleInterviewIds: [interview.id], items: [...saved.values()] } });
    if (path === '/interviews') return route.fulfill({ json: { ...empty, items: [interview] } });
    if (path === '/interviews/feedback-interview') return route.fulfill({ json: { interview,
      participants: [{ interviewId: interview.id, userId: 'hr-a', participantRole: 'INTERVIEWER', responseStatus: 'ACCEPTED' }] } });
    if (path === '/interviews/feedback-interview/materials') return route.fulfill({ json: { interviewId: interview.id, candidateId: 'person-a',
      candidateName: '面评恢复候选人', jdId: 'jd-a', jdVersionId: 'jd-v2', jdVersionNo: 2, jdTitle: '面评恢复岗位',
      jdRawText: '岗位要求', resumeVersionId: 'resume-a', resumeVersionNo: 1, resumeFileName: '测试简历.pdf', resumeMediaType: 'application/pdf', resumeSizeBytes: 100 } });
    if (path === '/applications/feedback-app') return route.fulfill({ json: details });
    if (['/applications/feedback-app/intentions', '/applications/feedback-app/communications',
      '/candidates/person-a/restriction-summaries', '/candidates/person-a/education-verification-records'].includes(path)) return route.fulfill({ json: [] });
    if (path === '/applications/feedback-app/workflow-steps/feedback-step/complete') {
      state.completedSteps++;
      step.status = 'PASSED'; application.currentStage = 'PATH_SELECTION';
      return route.fulfill({ json: details });
    }
    if (path !== '/interviews/feedback-interview/feedback') return route.fallback();
    if (request.method() === 'GET') return route.fulfill({ json: { ...empty, items: [...saved.values()] } });
    const body = request.postDataJSON() as Record<string, unknown>;
    const key = request.headers()['idempotency-key'] ?? '';
    requests.push({ key, body });
    expect(key).not.toBe('');
    if (rejection && requests.length === 1) return route.fulfill({ status: rejection, contentType: 'application/problem+json', json: {
      status: rejection, title: '面评校验未通过', detail: '请修正面评', code: 'VALIDATION_FAILED', recoverable: false,
    } });
    if (!saved.has(key)) saved.set(key, { feedback: { id: 'feedback-1', interviewId: interview.id, authorUserId: 'hr-a', status: 'SUBMITTED' },
      currentVersion: { ...body, id: 'feedback-version', versionNo: 1, createdAt: instant } });
    interview.status = 'COMPLETED';
    await state.wait;
    if (!rejection && requests.length === 1) return route.fulfill({ status: 200, contentType: 'application/json', body: '{"feedback":' });
    return route.fulfill({ json: saved.get(key) });
  });
  await page.goto('/schedule');
  await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /面评恢复候选人/ }).click();
  await page.getByRole('button', { name: '填写面评', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '填写面评', exact: true });
  await dialog.getByLabel('面评', { exact: true }).fill('本人已完成面试并确认通过');
  return { dialog, state, requests, saved };
}

for (const scenario of ['lost', '400', '422']) {
  test(`正式面评 ${scenario} 后正确确认或修正提交`, async ({ page }, testInfo) => {
    const width = testInfo.project.name === 'mobile' ? 390 : 1440;
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const api = await fixture(page, scenario === 'lost' ? undefined : Number(scenario));
    await expect(api.dialog.getByLabel('提交依据', { exact: true })).toHaveCount(0);
    const reason = api.dialog.getByLabel('面评', { exact: true });
    const submit = api.dialog.getByRole('button', { name: '正式提交面评', exact: true });
    await submit.click();
    if (scenario === 'lost') {
      await expect(api.dialog).toContainText('上次面评提交结果尚未确认，请重试确认后再修改。');
      await expect(reason).toBeDisabled();
      expect(api.saved.size).toBe(1);
    } else {
      await expect(page.getByText('请修正面评', { exact: true })).toBeVisible();
      await expect(reason).toBeEnabled();
      await reason.fill('修正后的面评');
      expect(api.saved.size).toBe(0);
    }
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.screenshot({ path: testInfo.outputPath(`feedback-submit-${scenario}-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    let release!: () => void;
    api.state.wait = new Promise<void>(resolve => { release = resolve; });
    await submit.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => api.requests.length).toBe(2);
      await expect(submit).toHaveClass(/ant-btn-loading/);
      await expect(reason).toBeDisabled();
      if (scenario === 'lost') {
        expect(api.requests[1]).toEqual(api.requests[0]);
        expect(api.requests[1]!.body.feedbackOccurredAt).toBeTruthy();
      } else {
        expect(api.requests[1]!.key).not.toBe(api.requests[0]!.key);
        expect(api.requests[1]!.body.shortComment).toBe('修正后的面评');
      }
      expect(api.requests[1]!.body.changeReason).toBe('正式提交面评');
      expect(api.saved.size).toBe(1);
    } finally { release(); }
    await expect(api.dialog).not.toBeVisible();
    await expect.poll(() => api.state.completedSteps).toBe(1);
    await expect(page.getByRole('dialog', { name: '继续招聘跟进', exact: true })).toBeVisible();
    expect(errors).toEqual([]);
  });
}

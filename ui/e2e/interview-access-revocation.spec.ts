import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const interview = {
  id: 'interview-access', applicationId: 'application-access', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2',
  candidateName: '权限测试候选人', jdTitle: '权限测试岗位',
  workflowStepId: 'step-a', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'SCHEDULED',
  scheduledStartAt: '2026-09-07T02:00:00Z', scheduledEndAt: '2026-09-07T03:00:00Z', timezoneId: 'Asia/Shanghai',
  meetingMode: 'ONSITE', locationText: '权限回归专用会议室', candidateConfirmationStatus: 'PENDING',
  nextResponsibleUserId: 'hr-a', ownerUserId: 'hr-a', version: 1,
};

async function fixture(page: Page, invited = false) {
  await reviewWorkspace(page);
  const state = { detailDenied: false, listStatus: 200, detailRequests: 0, listRequests: 0, writes: 0 };
  const historicalFeedback = {
    feedback: { id: 'feedback-a', interviewId: interview.id, authorUserId: 'another-user', status: 'SUBMITTED' },
    currentVersion: { verdict: 'PASS', shortComment: '需要撤销展示的历史面评', feedbackOccurredAt: '2026-09-07T01:00:00Z' },
  };
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const problem = (status: number) => route.fulfill({ status, contentType: 'application/problem+json', json: {
      status, code: status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE',
      detail: status === 403 ? '本次面试访问已被撤销' : '面试服务暂不可用', title: '面试读取失败',
      recoverable: status === 503,
    } });
    if (path.startsWith('/interviews/') && request.method() !== 'GET') {
      state.writes++;
      return problem(403);
    }
    if (path === '/interviews') {
      if (url.searchParams.has('applicationId')) return route.fulfill({ json: { ...empty, items: [interview] } });
      state.listRequests++;
      return state.listStatus === 200
        ? route.fulfill({ json: { items: [interview], hasMore: true, nextCursor: 'interview-page-2' } })
        : problem(state.listStatus);
    }
    if (path === '/interviews/interview-access') {
      state.detailRequests++;
      return state.detailDenied ? problem(403) : route.fulfill({ json: {
        interview, participants: [{ interviewId: interview.id, userId: invited ? 'invited-interviewer' : 'hr-a', participantRole: 'INTERVIEWER', responseStatus: 'ACCEPTED' }],
      } });
    }
    if (path === '/interviews/interview-access/materials') return route.fulfill({ json: {
      interviewId: interview.id, candidateId: 'person-a', candidateName: '权限测试候选人', jdId: 'jd-a', jdVersionId: 'jd-v2',
      jdVersionNo: 2, jdTitle: '权限测试岗位', jdRawText: '需要撤销展示的岗位材料', resumeVersionId: 'resume-a',
      resumeVersionNo: 1, resumeFileName: '缓存简历.docx', resumeMediaType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', resumeSizeBytes: 100,
    } });
    if (path === '/interview-feedback-batch') return route.fulfill({ json: { ...empty, visibleInterviewIds: [interview.id], items: [historicalFeedback] } });
    if (path === '/interviews/interview-access/feedback') return route.fulfill({ json: { ...empty, items: [historicalFeedback] } });
    if (path === '/interviews/interview-access/schedule-history') return route.fulfill({ json: { ...empty, items: [{
      id: 'history-a', interviewId: interview.id, actionType: 'CREATED', reason: '需要撤销展示的排期依据', operatedByUserId: 'hr-a', occurredAt: '2026-09-07T01:00:00Z',
    }] } });
    if (path === '/applications/application-access' && invited) return route.fulfill({ status: 403, contentType: 'application/problem+json', json: {
      status: 403, code: 'RECRUITMENT_PROCESSING_FORBIDDEN', detail: '无权处理此招聘活动', title: '无权访问', recoverable: false,
    } });
    if (path === '/applications/application-access') return route.fulfill({ json: {
      application: { id: 'application-access', ownerUserId: 'hr-a', terminalAt: null }, collaboratorUserIds: [], workflowSteps: [],
    } });
    return route.fallback();
  });
  if (invited) await page.route('**/api/v1/me', route => route.fulfill({ json: {
    userId: 'invited-interviewer', displayName: '受邀面试官', workEmail: 'interviewer@example.com',
    homeDepartmentId: 'dept-a', permissions: ['recruitment.manage', 'interview.schedule', 'interview.feedback.read-authorized'], roles: ['RECRUITER'], superAdmin: false,
  } }));
  return state;
}

test('受邀面试官可查看面试事实且不会看到他人的招聘与取消操作', async ({ page }) => {
  const state = await fixture(page, true);
  await page.goto('/schedule');
  await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /第 1 轮 10:00–11:00/ }).click();
  const detail = page.getByRole('dialog', { name: '面试事实与处理', exact: true });
  await expect(detail.getByText('仅可查看本次面试')).toBeVisible();
  await expect(detail.getByText('权限回归专用会议室', { exact: true })).toBeVisible();
  await expect(detail.getByText('招聘操作读取失败')).toHaveCount(0);
  await expect(detail.getByRole('button', { name: /^取\s*消$/ })).toHaveCount(0);
  await expect(detail.getByRole('button', { name: '重试招聘操作' })).toHaveCount(0);
  expect(state.writes).toBe(0);
});

async function reconnectStaleQueries(page: Page) {
  // Advance the public clock past staleTime, then exercise the browser reconnect lifecycle.
  await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
}

for (const opened of ['取消面试', '本次面试材料'] as const) {
  test(`面试详情被拒绝后隐藏缓存并关闭${opened}，恢复不重开旧弹窗`, async ({ page },testInfo) => {
    const state = await fixture(page);
    await page.goto('/schedule');
    await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /权限测试候选人/ }).click();
    const detail = page.getByRole('dialog', { name: '面试事实与处理', exact: true });
    await expect(detail.getByText('权限回归专用会议室', { exact: true })).toBeVisible();
    await expect(detail.getByText(/需要撤销展示的历史面评/)).toBeVisible();
    await expect(detail.getByText(/需要撤销展示的排期依据/)).toBeVisible();
    await detail.getByRole('button', { name: opened === '取消面试' ? /^取\s*消$/ : '查看本次面试材料', exact: true }).click();
    const nested = page.getByRole('dialog', { name: opened, exact: true });
    await expect(nested).toBeVisible();
    if (opened === '本次面试材料') await expect(nested.getByText('需要撤销展示的岗位材料')).toBeVisible();
    state.detailDenied = true;
    await reconnectStaleQueries(page);
    await expect.poll(() => state.detailRequests).toBeGreaterThan(1);
    await expect(nested).not.toBeVisible();
    const retry = detail.getByRole('button', { name: '重试面试详情', exact: true });
    await expect(retry).toBeVisible();
    await expect(detail.getByText('权限回归专用会议室', { exact: true })).toHaveCount(0);
    await expect(detail.getByText(/需要撤销展示的历史面评/)).toHaveCount(0);
    await expect(detail.getByText(/需要撤销展示的排期依据/)).toHaveCount(0);
    await expect(detail.getByRole('button', { name: '查看本次面试材料', exact: true })).toHaveCount(0);
    await expect(detail.getByRole('button', { name: /^(改\s*期|取\s*消|填写本人面评)$/ })).toHaveCount(0);
    await retry.focus();
    await page.screenshot({path:testInfo.outputPath('access-denied.png'),animations:'disabled'});
    expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
    state.detailDenied = false;
    await retry.focus();
    await page.keyboard.press('Enter');
    await expect(detail.getByText('权限回归专用会议室', { exact: true })).toBeVisible();
    await expect(detail.getByText(/需要撤销展示的历史面评/)).toBeVisible();
    await expect(nested).not.toBeVisible();
    expect(state.writes).toBe(0);
  });
}

for (const status of [403, 503]) {
  test(`面试列表刷新 ${status} 时${status === 403 ? '隐藏' : '保留'}已加载行`, async ({ page }) => {
    const state = await fixture(page);
    await page.goto('/schedule');
    const calendar = page.getByRole('region', { name: '所选周面试日历' });
    const card = calendar.getByRole('button', { name: /权限测试候选人/ });
    const load = page.getByRole('button', { name: '加载更多面试', exact: true });
    await expect(card).toBeVisible();
    await expect(load).toBeVisible();
    state.listStatus = status;
    await reconnectStaleQueries(page);
    await expect.poll(() => state.listRequests).toBeGreaterThan(1);
    const retry = page.getByRole('button', { name: '重试日程', exact: true });
    await expect(retry).toBeVisible();
    if (status === 403) {
      await expect(card).toHaveCount(0);
      await expect(load).toHaveCount(0);
    } else await expect(card).toBeVisible();
    state.listStatus = 200;
    await retry.click();
    await expect(card).toBeVisible();
    await expect(load).toBeVisible();
  });
}

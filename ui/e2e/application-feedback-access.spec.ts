import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const interview = {
  id: 'feedback-access', applicationId: 'feedback-app', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2',
  candidateName: '面评权限候选人', jdTitle: '面评权限测试岗位',
  workflowStepId: 'step-a', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'SCHEDULED',
  scheduledStartAt: '2026-09-07T02:00:00Z', scheduledEndAt: '2026-09-07T03:00:00Z', timezoneId: 'Asia/Shanghai',
  meetingMode: 'ONSITE', locationText: '详情持续可读会议室', candidateConfirmationStatus: 'PENDING',
  nextResponsibleUserId: 'hr-a', ownerUserId: 'hr-a', version: 1,
};
const historical = { ...interview, id: 'feedback-history', roundNo: 2, status: 'COMPLETED', scheduledStartAt: '2026-09-06T02:00:00Z', scheduledEndAt: '2026-09-06T03:00:00Z' };

async function fixture(page: Page, roundCount=2) {
  await reviewWorkspace(page);
  const rounds=[interview,historical,...Array.from({length:roundCount-2},(_,index)=>({...historical,id:`history-${index+3}`,roundNo:index+3}))];
  const state = { applicationListStatus: 200, applicationListRequests: 0, detailRequests: 0, feedbackRequests: 0, singleFeedbackIds:[] as string[] };
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/interviews') {
      if (!url.searchParams.has('applicationId')) return route.fulfill({ json: { ...empty, items: [interview] } });
      state.applicationListRequests++;
      const status = state.applicationListStatus;
      return status === 200 ? route.fulfill({ json: { ...empty, items: rounds } }) : route.fulfill({
        status, contentType: 'application/problem+json', json: {
          status, title: '流程记录读取失败', code: status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE',
          detail: status === 403 ? '当前招聘流程面评查看权限已撤销' : '当前招聘流程面评服务暂不可用', recoverable: status === 503,
        },
      });
    }
    if (path === '/interviews/feedback-access') {
      state.detailRequests++;
      return route.fulfill({ json: { interview, participants: [{ userId: 'hr-a', interviewId: interview.id }] } });
    }
    if (path === '/interviews/feedback-access/materials') return route.fulfill({ json: {
      interviewId: interview.id, candidateId: 'person-a', candidateName: '面评权限候选人', jdId: 'jd-a', jdVersionId: 'jd-v2',
      jdVersionNo: 2, jdTitle: '面评权限测试岗位', jdRawText: '岗位材料', resumeVersionId: 'resume-a',
      resumeVersionNo: 1, resumeFileName: '简历.docx', resumeMediaType: 'application/octet-stream', resumeSizeBytes: 100,
    } });
    if (path === '/interview-feedback-batch') {
      state.feedbackRequests++;
      const ids=url.searchParams.getAll('interviewIds');
      expect(ids).toEqual(rounds.map(value=>value.id).sort());
      return route.fulfill({ json: { ...empty,visibleInterviewIds:ids, items: rounds.map(value=>({
        feedback: { id:`comment-${value.id}`, interviewId:value.id, authorUserId: 'another-user', status: 'SUBMITTED' },
        currentVersion: { verdict: 'PASS', shortComment: value.id===interview.id?'当前轮次独有评论':value.id===historical.id?'历史轮次独有评论':`第${value.roundNo}轮评论`, feedbackOccurredAt: '2026-09-07T01:00:00Z' },
      })) } });
    }
    if (/\/interviews\/[^/]+\/feedback$/.test(path)) {state.singleFeedbackIds.push(path.split('/')[2]!);return route.fulfill({json:empty});}
    if (path === '/interviews/feedback-access/schedule-history') return route.fulfill({ json: empty });
    if (path === '/applications/feedback-app') return route.fulfill({ json: {
      application: { id: 'feedback-app', ownerUserId: 'hr-a', terminalAt: null }, collaboratorUserIds: [], workflowSteps: [],
    } });
    return route.fallback();
  });
  return state;
}

test('二十轮面试仅一次批量面评请求且保留全部历史',async({page},testInfo)=>{
  const state=await fixture(page,20);
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/面评权限候选人/}).click();
  const detail=page.getByRole('dialog',{name:'面试事实与处理',exact:true});
  const feedback=detail.locator('.ant-card').filter({has:page.getByText('流程记录',{exact:true})});
  await expect(feedback.getByText(/第20轮评论/)).toBeAttached();
  await expect(feedback.getByText(/当前轮次独有评论/)).toBeAttached();
  await expect(feedback.getByRole('row')).toHaveCount(21);
  expect(state.applicationListRequests).toBe(1);
  expect(state.feedbackRequests).toBe(1);
  // 详情自身保留当前轮面评读取；历史区域不再逐轮请求。
  expect(state.singleFeedbackIds).toEqual(['feedback-access']);
  for(const width of testInfo.project.name==='desktop'?[390,768,1280,1440,1920]:[393]){
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await detail.screenshot({path:testInfo.outputPath(`twenty-round-${width}.png`)});
  }
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await detail.screenshot({path:testInfo.outputPath('twenty-round-feedback.png')});
});

for (const status of [403, 503]) {
  test(`流程记录父列表刷新 ${status} 时${status === 403 ? '隐藏' : '保留'}缓存轮次，详情持续可读且重试恢复`, async ({ page }, testInfo) => {
    const state = await fixture(page);
    await page.goto('/schedule');
    await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /面评权限候选人/ }).click();
    const detail = page.getByRole('dialog', { name: '面试事实与处理', exact: true });
    const feedback = detail.locator('.ant-card').filter({ has: page.getByText('流程记录', { exact: true }) });
    await expect(feedback.getByText(/当前轮次独有评论/)).toBeVisible();
    await expect(feedback.getByText(/历史轮次独有评论/)).toBeVisible();
    await expect(feedback.getByRole('row')).toHaveCount(3);
    state.applicationListStatus = status;
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    await expect.poll(() => state.applicationListRequests).toBeGreaterThan(1);
    const retry = feedback.getByRole('button', { name: '重试流程记录', exact: true });
    await expect(retry).toBeVisible();
    await expect(detail.getByText('详情持续可读会议室', { exact: true })).toBeVisible();
    await expect(detail.getByRole('button', { name: '查看本次面试材料', exact: true })).toBeVisible();
    await expect(detail.getByRole('button', { name: '重试面试详情', exact: true })).toHaveCount(0);
    if (status === 403) {
      await expect(feedback.getByText(/当前轮次独有评论|历史轮次独有评论/)).toHaveCount(0);
      await expect(feedback.getByRole('row')).toHaveCount(0);
      for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
      expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
      await feedback.screenshot({ path: testInfo.outputPath('application-feedback-denied.png') });
    } else {
      await expect(feedback.getByText(/当前轮次独有评论/)).toBeVisible();
      await expect(feedback.getByText(/历史轮次独有评论/)).toBeVisible();
      await expect(feedback.getByRole('row')).toHaveCount(3);
    }
    state.applicationListStatus = 200;
    await retry.focus();
    await page.keyboard.press('Enter');
    await expect(feedback.getByText(/当前轮次独有评论/)).toBeVisible();
    await expect(feedback.getByText(/历史轮次独有评论/)).toBeVisible();
    await expect(retry).toHaveCount(0);
    await expect(feedback.getByRole('row')).toHaveCount(3);
    expect(state.detailRequests).toBeGreaterThan(1);
    expect(state.feedbackRequests).toBeGreaterThanOrEqual(2);
  });
}

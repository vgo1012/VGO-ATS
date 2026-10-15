import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('共享库向无额外权限的登录用户展示跨归属的事实、旧版本、岗位及面试记录', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const empty = { items: [], hasMore: false, nextCursor: null };
  const person = { id: 'person-a', fullName: '共享历史候选人', ownerUserId: 'another-hr', version: 1 };
  const interview = { id: 'old-interview', applicationId: 'old-app', candidateId: person.id, jdId: 'jd-a', jdVersionId: 'jd-v2', workflowStepId: 'step-old', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'COMPLETED', scheduledStartAt: '2026-09-01T02:00:00Z', scheduledEndAt: '2026-09-01T03:00:00Z', ownerUserId: 'another-hr' };
  const app = { id: 'old-app', candidateId: person.id, resumeVersionId: 'old-resume', jdId: 'jd-a', jdVersionId: 'jd-v2', ownerUserId: 'another-hr', recruitmentType: 'INTERNAL', currentStage: 'TERMINATED', outcome: 'REJECT', terminalAt: '2026-09-02T02:00:00Z' };
  const facts = { snapshot: { id: 'facts-new', resumeVersionId: 'new-resume', fullName: person.fullName, currentRole: '共享履历岗位', age: 29, city: '上海' }, skills: [{ id: 'linux', skillName: 'Linux 历史经验' }], workExperiences: [], projectExperiences: [], educations: [], simpleFacts: [] };
  const feedback = { feedbackId: 'feedback-old', authorUserId: 'another-interviewer', verdict: 'PASS', shortComment: '历史面评：故障排查经验扎实', feedbackOccurredAt: '2026-09-01T04:00:00Z' };
  const requests: string[] = [];
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    requests.push(path);
    if (path === '/me') return route.fulfill({ json: { userId: 'public-reader', displayName: '共享查看用户', roles: [], permissions: [], superAdmin: false } });
    if (path === '/directory/users/resolve') return route.fulfill({ json: [{id:'another-hr',displayName:'原归属招聘人'}, {id:'another-interviewer',displayName:'历史面试官'}, {id:'another-user',displayName:'草稿记录人'}] });
    if (path === '/jds/jd-a/versions') return route.fulfill({ status: 403, json: { status: 403, detail: '不能查看整个私有 JD 版本列表' } });
    if (path === '/jd-versions/jd-v2') return route.fulfill({ json: { id: 'jd-v2', jdId: 'jd-a', versionNo: 2, title: '共享绑定的历史岗位', rawText: '该冻结版本的岗位描述' } });
    if (path === '/talent-search') return route.fulfill({ json: { ...empty, items: [{ candidateId: person.id, resumeVersionId: 'new-resume', fullName: person.fullName, skills: [], degrees: [], conditionMatches: [] }] } });
    if (path === '/talent-search/count') return route.fulfill({ json: { count: 1, nextCursor: null, hasMore: false } });
    if (path === '/candidate-batch') return route.fulfill({ json: [person] });
    if (path === '/candidates/person-a') return route.fulfill({ json: person });
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: [{ candidateId: person.id, resumeVersionId: 'new-resume', libraryEligible: true, stages: [], jobContexts: [{ jdId: 'jd-a', jdVersionId: 'jd-v2', title: '历史运维岗位', versionNo: 2, resumeVersionId: 'old-resume', applicationId: app.id, stage: 'REJECTED', status: 'FINISHED' }] }] });
    if (path === '/candidates/person-a/resume-versions') return route.fulfill({ json: { ...empty, items: [{ id: 'new-resume', versionNo: 2, originalFileName: '最新简历.pdf' }, { id: 'old-resume', versionNo: 1, originalFileName: '历史简历.pdf' }] } });
    if (path.endsWith('/source')) return route.fulfill({ json: { sourceChannelName: '共享来源渠道', sourceRecruiterUserId: 'another-hr', receivedAt: '2026-09-01T00:00:00Z' } });
    if (path === '/resume-versions/new-resume/facts') return route.fulfill({ json: facts });
    if (path === '/resume-versions/old-resume/facts') return route.fulfill({ json: { ...facts, snapshot: { ...facts.snapshot, id: 'facts-old', resumeVersionId: 'old-resume', currentRole: '旧版履历岗位' } } });
    if (path === '/candidates/person-a/recruitment-history') return route.fulfill({ json: { items: [{ applicationId: app.id, jdId: app.jdId, jdVersionId: app.jdVersionId, currentStage: app.currentStage, originalOwnerUserId: app.ownerUserId, currentOwnerUserId: app.ownerUserId, createTime: '2026-09-01T00:00:00Z', terminalAt: app.terminalAt, stageEvents: [], ownershipEvents: [{ actionType: 'ASSIGN', fromUserId: null, toUserId: app.ownerUserId, operatedByUserId: app.ownerUserId, occurredAt: '2026-09-01T00:00:00Z' }], interviews: [{ ...interview, interviewId: interview.id, feedbacks: [feedback] }] }] } });
    if (path === '/applications/old-app') return route.fulfill({ json: { application: app, collaboratorUserIds: [], workflowSteps: [
      { id: 'step-undated', stepCode: 'CANDIDATE_COMMUNICATION', sequenceNo: 1, status: 'NOT_SELECTED', startedAt: null, completedAt: null, resultReason: '未发生流程步骤' },
      { id: 'step-earlier', stepCode: 'INTERVIEW_ROUND_1', sequenceNo: 2, status: 'PASSED', startedAt: '2026-09-01T00:00:00Z', completedAt: '2026-09-01T04:00:00Z', resultReason: '较早流程步骤' },
      { id: 'step-later', stepCode: 'DECISION', sequenceNo: 3, status: 'FAILED', startedAt: '2026-09-02T00:00:00Z', completedAt: '2026-09-02T02:00:00Z', resultReason: '较晚流程步骤' },
    ], decisions: [{id:'decision',decision:'REJECT',decidedAt:'2026-09-02T03:00:00Z',reason:'历史录用决定原因'}], offerStatusRecords: [{id:'offer',status:'TERMINATED',occurredAt:'2026-09-02T02:30:00Z',reason:'历史 Offer 终止原因'}], elapsedSeconds: 86400 } });
    if (path.startsWith('/applications/old-app/workflow-steps/') && path.endsWith('/follow-ups')) return route.fulfill({ json: [] });
    if (path === '/applications/old-app/communications') return route.fulfill({ json: [
      { id: 'communication-old', summary: '历史沟通：候选人希望调整入职日期', communicatedAt: '2026-09-02T00:00:00Z', recordedByUserId: 'another-hr' },
      { id: 'communication-new', summary: '较晚沟通记录', communicatedAt: '2026-09-02T01:00:00Z', recordedByUserId: 'another-hr' },
    ] });
    if (path === '/applications/old-app/intentions') return route.fulfill({ json: [{ id: 'intention', expectedSalaryMin: 18000, expectedSalaryMax: 25000, availability: '两周内', intentionLocations: '上海', reason: '历史薪酬沟通记录', recordedByUserId: 'another-hr' }] });
    if (path === '/candidates/person-a/restriction-summaries') return route.fulfill({ json: [{ clientName: '历史客户', reason: '共享标签完整原因', sourceChannel: 'MANUAL', occurredAt: '2026-09-01T00:00:00Z' }] });
    if (path === '/interviews') return route.fulfill({ json: { ...empty, items: [interview] } });
    if (path === '/interview-feedback-batch') return route.fulfill({ json: { ...empty, visibleInterviewIds: [interview.id], items: [{ feedback: { id: feedback.feedbackId, interviewId: interview.id, status: 'SUBMITTED', authorUserId: 'another-interviewer' }, currentVersion: feedback }, { feedback: { id: 'draft', interviewId: interview.id, status: 'DRAFT', authorUserId: 'another-user' }, currentVersion: { shortComment: '草稿面评也共享', verdict: 'FAIL' } }] } });
    if (path === '/interviews/old-interview/materials') return route.fulfill({ json: { candidateName: person.fullName, jdTitle: '历史运维岗位', resumeVersionId: 'old-resume', resumeVersionNo: 1 } });
    return route.fallback();
  });
  await page.goto('/shared-library');
  await expect(page.getByRole('columnheader',{name:'历史关联岗位',exact:true})).toBeVisible();
  await page.screenshot({path:testInfo.outputPath('shared-historical-job.png'),animations:'disabled'});
  const row = page.getByRole('row').filter({ has: page.getByRole('button', { name: person.fullName, exact: true }) });
  await expect(row.getByText('历史运维岗位', { exact: true })).toBeVisible();
  await expect(row.getByRole('button', { name: '历史运维岗位 · 历史归档' })).toBeVisible();
  await row.getByRole('button', { name: '查看简历', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: person.fullName, exact: true });
  await expect(dialog.getByText('共享来源渠道')).toBeVisible();
  await expect(dialog.getByRole('button', { name: '查看简历 V1' })).toBeVisible();
  await expect(dialog.getByText('Linux 历史经验')).toBeAttached();
  await expect(dialog.getByText('公司级黑名单与处置历史', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'HR 更正' })).toHaveCount(0);
  await expect(dialog.getByText('共享绑定的历史岗位')).toBeVisible();
  await expect(dialog.getByText('共享标签完整原因')).toBeVisible();
  await expect(dialog.getByText('暂无黑名单查看权限')).toHaveCount(0);
  await dialog.getByRole('region', { name: '历史招聘记录' }).getByRole('button').click();
  await expect(dialog.getByRole('region', { name: '历史招聘记录' }).getByText(/待确认|未分配/)).toHaveCount(0);
  await expect(dialog.getByText(feedback.shortComment, { exact: true })).toBeVisible();
  await dialog.screenshot({ path: testInfo.outputPath('shared-history.png') });
  await dialog.getByRole('button', { name: '查看该流程详情' }).click();
  const application = page.getByRole('dialog', { name: '招聘活动详情', exact: true });
  await expect(application.getByText('当前为共享查看',{exact:true})).toBeVisible();
  await expect(application.getByRole('region',{name:'招聘活动摘要'}).getByText('共享绑定的历史岗位')).toBeVisible();
  await expect(application.getByText('当前为管理查看',{exact:true})).toHaveCount(0);
  await expect(application.getByRole('table', { name: '流程记录' }).getByText(feedback.shortComment)).toBeVisible();
  await expect(application.getByText('草稿面评也共享')).toBeVisible();
  await expect(application.getByText('意向与薪酬记录', { exact: true })).toHaveCount(0);
  await expect(application.getByText('暂无意向与薪酬记录')).toHaveCount(0);
  await expect(application.getByText('历史录用决定原因')).toBeVisible();
  await expect(application.getByText('历史 Offer 终止原因')).toBeVisible();
  await expect(application.getByText('历史沟通：候选人希望调整入职日期')).toBeVisible();
  await expect(application.getByText('较晚沟通记录')).toBeVisible();
  await expect(application.getByText('未发生流程步骤')).toBeVisible();
  const historyItems = await application.locator('.ant-timeline-item-content').allTextContents();
  const indexOf = (content: string) => historyItems.findIndex(item => item.includes(content));
  expect(indexOf('较晚沟通记录')).toBeLessThan(indexOf('历史沟通：候选人希望调整入职日期'));
  expect(indexOf('较晚流程步骤')).toBeLessThan(indexOf('较早流程步骤'));
  expect(indexOf('较早流程步骤')).toBeLessThan(indexOf('未发生流程步骤'));
  expect(indexOf('历史录用决定原因')).toBeLessThan(indexOf('历史 Offer 终止原因'));
  await application.screenshot({ path: testInfo.outputPath('shared-interview-records.png') });
  expect(requests).toContain('/candidates/person-a/company-blacklist-records');
  expect(requests).toContain('/jd-versions/jd-v2');
  expect(requests).not.toContain('/jds/jd-a/versions');
  expect(requests).not.toContain('/applications/old-app/intentions');
});

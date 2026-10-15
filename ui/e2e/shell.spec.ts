import { emptyRecruitmentAnalysis } from './fixtures/recruitment-analysis';
import { rememberDialog } from './dialogFlow';
import AxeBuilder from '@axe-core/playwright';
import { reviewDialog } from './dialogReview';
import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const capture = async (page: Page, name: string) => {
  if (process.env.CAPTURE_VISUAL === '1') {
    await page.screenshot({ path: `test-results/review/${name}.png`, fullPage: true, animations: 'disabled' });
  }
};

const actor = {
  userId: '01JADMIN000000000000000000',
  displayName: '系统管理员',
  workEmail: 'admin@example.com',
  homeDepartmentId: '01JDEPARTMENT0000000000000',
  roles: ['SUPER_ADMIN'],
  permissions: [],
  superAdmin: true,
  mustChangePassword: false,
  version: '1',
};

test.beforeEach(async ({ page }, testInfo) => {
  // Keep unhandled supporting reads inside the synthetic workspace, away from the live proxy.
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path === '/reports/recruitment-analysis') return route.fulfill({json:emptyRecruitmentAnalysis});
    if(path === '/directory/departments') return route.fulfill({json:[]});
    if(path.endsWith('/company-blacklist-records'))return route.fulfill({json:{current:null,active:false,records:{items:[],hasMore:false,nextCursor:null}}});
    const lists=['/management/workbench','/candidate-pool-statuses','/candidate-resume-summaries','/resume-version-fact-summaries','/resume-version-references','/applications/stage-counts','/directory/users/resolve','/tasks'];
    return route.fulfill({json:lists.includes(path)?[]:{items:[],nextCursor:null,hasMore:false}});
  });
  if (process.env.CAPTURE_VISUAL === '1' && testInfo.project.name === 'desktop') {
    await page.setViewportSize({ width: 1800, height: 874 });
  }
  await page.route('**/api/v1/me', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(actor) });
  });
  await page.route('**/api/v1/jds?limit=20', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) });
  });
  await page.route('**/api/v1/weight-profiles?limit=20', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) });
  });
  for (const endpoint of ['source-channels?limit=20', 'intakes?limit=20', 'candidates?*', 'identity-conflicts?status=OPEN&limit=20']) {
    await page.route(`**/api/v1/${endpoint}`, async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) });
    });
  }
  await page.route('**/api/v1/admin/users?*',route=>route.fulfill({json:[]}));
  await page.route('**/api/v1/directory/users?*',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/directory/users/resolve?*',route=>route.fulfill({json:[]}));
  await page.route('**/api/v1/jds/*/versions?*',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidates/*/resume-versions/*/source',route=>route.fulfill({json:{sourceChannelName:'人工上传'}}));
  await page.route('**/api/v1/demands/*/jd-versions?*',route=>route.fulfill({json:{items:[],nextCursor:null,hasMore:false}}));
  await page.route('**/api/v1/candidates/*',route=>route.fulfill({json:{id:'01JCANDIDATE00000000000000',fullName:'测试候选人',ownerUserId:actor.userId}}));
  await page.route('**/api/v1/candidate-batch?*',route=>{
    const ids=new URL(route.request().url()).searchParams.getAll('candidateIds');
    return route.fulfill({json:['01JCANDIDATE00000000000000','01JEXTCANDIDATE00000000000'].filter(id=>ids.includes(id)).map(id=>({id,fullName:'测试候选人',ownerUserId:actor.userId}))});
  });
  await page.route('**/api/v1/candidates/*/resume-versions?*',route=>route.fulfill({json:{items:[],nextCursor:null,hasMore:false}}));
  await page.route('**/api/v1/talent-search?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/applications?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/workbench', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.route('**/api/v1/interviews?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/my/interview-tasks?limit=20', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
});

test('renders the production shell without horizontal overflow', async ({ page }, testInfo) => {
  await page.goto('/recruitment-analysis');
  await expect(page.getByRole('heading', { level: 2, name: '招聘流程转化', exact: true })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await capture(page, `final-recruitment-analysis-${testInfo.project.name}`);
  await capture(page, testInfo.project.name);
});

test('navigates between primary workspaces', async ({ page, isMobile }) => {
  await page.goto('/recruitment-analysis');
  const navigation = page.locator(isMobile ? '.mobile-bottom-nav' : '.desktop-nav');
  await expect(navigation.getByRole('button', { name: '招募管道', exact: true })).toBeVisible();
  if (isMobile) {
    await navigation.getByRole('button', { name: '招募管道', exact: true }).click();
  } else {
    await navigation.getByRole('button', { name: '招募管道', exact: true }).click();
  }
  await expect(page).toHaveURL(/\/recruitment$/);
  await expect(page.locator('.workspace-nav:visible').getByRole('button', { name: '招募管道', exact: true })).toHaveAttribute('aria-current', 'page');
});

test('opens the shared talent intake workspace without overflow', async ({ page }) => {
  await page.goto('/talents');
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('tab', { name: '待处置' })).toHaveCount(0);
  await capture(page, 'final-talents');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});

test('opens a read-only Application with immutable versions and no processing actions', async ({ page }) => {
  const applicationId='01JAPPLICATION0000000000000';
  const application={ id:applicationId,candidateId:'01JCANDIDATE00000000000000',demandId:'01JDEMAND0000000000000000',jdId:'01JJOB0000000000000000000',jdVersionId:'01JJDVERSION00000000000000',resumeVersionId:'01JRESUMEVER0000000000000',workflowTemplateVersionId:'01JWORKFLOWV0000000000000',matchPlanVersionId:'01JMATCHPLANV000000000000',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',outcome:null,ownerUserId:actor.userId,ownerDepartmentId:actor.homeDepartmentId,terminalAt:null,version:0,createId:actor.userId,createTime:'2026-09-02T00:00:00Z',updateId:actor.userId,updateTime:'2026-09-02T00:00:00Z' };
  await page.unroute('**/api/v1/applications?*');
  await page.route('**/api/v1/applications?*', route => route.fulfill({ status:200,contentType:'application/json',body:JSON.stringify({items:[application],nextCursor:null,hasMore:false}) }));
  await page.route(`**/api/v1/applications/${applicationId}`, route => route.fulfill({ status:200,contentType:'application/json',body:JSON.stringify({application,collaboratorUserIds:[],workflowSteps:[{id:'01JSTEP0000000000000000000',applicationId,templateVersionId:application.workflowTemplateVersionId,stepDefinitionId:'01JSTEPDEF000000000000000',stepCode:'CANDIDATE_COMMUNICATION',sequenceNo:1,candidateOptional:true,defaultFollowUpChannel:'PHONE',status:'AVAILABLE',ownerUserId:null,dueAt:null,startedAt:null,completedAt:null,resultReason:null,clientSubmissionId:null,version:0}],decisions:[],offerStatusRecords:[],elapsedSeconds:3600}) }));
  await page.route(`**/api/v1/applications/${applicationId}/communications`, route => route.fulfill({status:200,contentType:'application/json',body:'[]'}));
  await page.route(`**/api/v1/applications/${applicationId}/intentions`, route => route.fulfill({status:200,contentType:'application/json',body:'[]'}));

  await page.goto('/recruitment');
  await expect(page.getByRole('heading',{name:'招聘流程'})).toBeVisible();
  await capture(page, 'final-recruitment');
  await page.getByRole('button',{name:'测试候选人',exact:true}).click();
  const dialog = page.getByRole('dialog', { name: '招聘活动详情' });
  await expect(dialog.getByRole('region', { name: '招聘活动摘要' })).toBeVisible();
  await expect(dialog.getByRole('region', { name: '面试材料' })).toBeVisible();
  await expect(dialog.getByText('流程记录', { exact: true })).toBeVisible();
  await reviewDialog(page, 'application');
  for (const action of ['选择下一步', '调整协作人', '转交负责人', '记录已发生的沟通']) {
    await expect(dialog.getByRole('button', { name: action, exact: true })).toHaveCount(0);
  }

  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

test('keeps workflow correction out of the read-only Application record view', async ({ page }) => {
  const applicationId='01JSKIPAPPLICATION00000000000';
  const stepId='01JSKIPSTEP0000000000000000';
  const application={id:applicationId,candidateId:'01JCANDIDATE00000000000000',demandId:'01JDEMAND0000000000000000',jdId:'01JJOB0000000000000000000',jdVersionId:'01JJDVERSION00000000000000',resumeVersionId:'01JRESUMEVER0000000000000',workflowTemplateVersionId:'01JWORKFLOWV0000000000000',matchPlanVersionId:'01JMATCHPLANV000000000000',recruitmentType:'INTERNAL',currentStage:'IN_PROCESS',outcome:null,ownerUserId:actor.userId,ownerDepartmentId:actor.homeDepartmentId,terminalAt:null,version:1,createId:actor.userId,createTime:'2026-09-02T00:00:00Z',updateId:actor.userId,updateTime:'2026-09-02T00:00:00Z'};
  const step={id:stepId,applicationId,templateVersionId:application.workflowTemplateVersionId,stepDefinitionId:'01JSTEPDEF000000000000000',stepCode:'CANDIDATE_COMMUNICATION',sequenceNo:1,candidateOptional:true,defaultFollowUpChannel:'PHONE',status:'PENDING',ownerUserId:actor.userId,dueAt:'2026-09-04T00:00:00Z',startedAt:null,completedAt:null,resultReason:null,clientSubmissionId:null,version:3};
  const details=()=>({application,collaboratorUserIds:[],workflowSteps:[step],decisions:[],offerStatusRecords:[],elapsedSeconds:3600});
  await page.unroute('**/api/v1/applications?*');
  await page.route('**/api/v1/applications?*',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:[application],nextCursor:null,hasMore:false})}));
  await page.route(`**/api/v1/applications/${applicationId}`,route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(details())}));
  await page.route(`**/api/v1/applications/${applicationId}/communications`,route=>route.fulfill({status:200,contentType:'application/json',body:'[]'}));
  await page.route(`**/api/v1/applications/${applicationId}/intentions`,route=>route.fulfill({status:200,contentType:'application/json',body:'[]'}));
  await page.goto('/recruitment');
  await page.getByRole('button',{name:'测试候选人',exact:true}).click();
  const dialog = page.getByRole('dialog', { name: '招聘活动详情' });
  await expect(dialog.getByRole('region', { name: '招聘活动摘要' })).toContainText('候选人沟通 · 待处理');
  await expect(dialog.getByRole('button', { name: '管理员跳过' })).toHaveCount(0);
});

test('returns to login when a business request reports an expired session', async ({ page }) => {
  await page.unroute('**/api/v1/applications?*');
  await page.route('**/api/v1/applications?*', (route) => route.fulfill({
    status: 401,
    contentType: 'application/problem+json',
    body: JSON.stringify({
      title: 'Unauthorized',
      status: 401,
      detail: '登录会话已失效',
      code: 'SESSION_REQUIRED',
      recoverable: true,
    }),
  }));

  await page.goto('/recruitment');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
});

test('returns to login when an expired-session response contains malformed JSON', async ({ page }) => {
  await page.route('**/api/v1/applications?*', route => route.fulfill({
    status: 401, contentType: 'application/problem+json', body: '{',
  }));
  await page.goto('/recruitment');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
});

test('does not show client recommendation features for applications with historical submissions', async ({ page }) => {
  const applicationId='01JEXTAPPLICATION00000000000';
  const submissionId='01JCLIENTSUBMISSION000000000';
  const application={id:applicationId,candidateId:'01JEXTCANDIDATE00000000000',demandId:'01JEXTDEMAND0000000000000',jdId:'01JEXTJOB0000000000000000',jdVersionId:'01JEXTJDVERSION00000000000',resumeVersionId:'01JEXTRESUMEVER0000000000',workflowTemplateVersionId:'01JEXTWORKFLOW00000000000',matchPlanVersionId:'01JEXTMATCHPLAN0000000000',recruitmentType:'EXTERNAL',clientId:'01JCLIENT000000000000000000',clientName:'测试甲方公司',currentStage:'PATH_SELECTION',outcome:null,ownerUserId:actor.userId,ownerDepartmentId:actor.homeDepartmentId,terminalAt:null,version:2,createId:actor.userId,createTime:'2026-09-02T00:00:00Z',updateId:actor.userId,updateTime:'2026-09-02T00:00:00Z'};
  const submission={id:submissionId,applicationId,candidateId:application.candidateId,demandId:application.demandId,clientId:application.clientId,projectId:null,positionId:'01JPOSITION000000000000000',jdId:application.jdId,jdVersionId:application.jdVersionId,resumeVersionId:application.resumeVersionId,deliveryStatus:'SENT',processStatus:'SCREENING',sentAt:'2026-09-02T01:00:00Z',withdrawnAt:null,withdrawalReason:null,ownerUserId:actor.userId,version:1,createdAt:'2026-09-02T00:00:00Z',updatedAt:'2026-09-02T01:00:00Z'};
  const sendSnapshot={id:'snapshot-1',submissionId,resumeVersionId:application.resumeVersionId,recipient:application.clientName,channel:'MANUAL_OTHER',fileName:'candidate.pdf',contentSha256:'a'.repeat(64),sentByUserId:actor.userId,sentAt:submission.sentAt};
  const writes:string[]=[];
  page.on('request',request=>{if(request.url().includes('/api/v1/')&&request.method()==='POST')writes.push(request.url());});
  await page.route('**/api/v1/applications?*',route=>route.fulfill({json:{items:[application],nextCursor:null,hasMore:false}}));
  await page.route(`**/api/v1/applications/${applicationId}`,route=>route.fulfill({json:{application,collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:7200}}));
  for(const path of [`applications/${applicationId}/communications`,`applications/${applicationId}/intentions`,`candidates/${application.candidateId}/restriction-summaries`,`candidates/${application.candidateId}/education-verification-records?limit=20`])await page.route(`**/api/v1/${path}`,route=>route.fulfill({json:[]}));
  await page.route(`**/api/v1/client-submissions/eligibility?applicationId=${applicationId}`,route=>route.fulfill({json:{applicationId,candidateId:application.candidateId,clientId:application.clientId,companyBlacklist:false,clientRestriction:false,eligible:true,blockingCodes:[]}}));
  await page.route(`**/api/v1/client-submissions?applicationId=${applicationId}&limit=20`,route=>route.fulfill({json:{items:[submission],nextCursor:null,hasMore:false}}));
  await page.route(`**/api/v1/client-submissions/${submissionId}`,route=>route.fulfill({json:{submission,sendSnapshot,feedback:[],riskAcknowledgements:[]}}));
  await page.route(`**/api/v1/clients/${application.clientId}`,route=>route.fulfill({json:{id:application.clientId,name:application.clientName,status:'ACTIVE',version:1}}));
  await page.route(`**/api/v1/client-positions/${submission.positionId}`,route=>route.fulfill({json:{id:submission.positionId,name:'测试客户岗位',clientId:application.clientId,status:'ACTIVE',version:1}}));
  await page.goto('/recruitment');
  await page.getByRole('button',{name:'测试候选人',exact:true}).click();
  const detail=page.getByRole('dialog',{name:'招聘活动详情'});
  await expect(detail.getByText('客户推荐与反馈',{exact:true})).toHaveCount(0);
  await expect(detail.getByRole('button',{name:/登记客户推荐|新建推荐草稿/})).toHaveCount(0);
  await expect(detail.getByRole('button',{name:'查看推荐证据'})).toHaveCount(0);
  expect(writes).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

test('renders the real interview week ledger and opens versioned details without page overflow', async ({ page }) => {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.stack ?? error.message));
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'interview-token' } }));
  const interviewId='01JINTERVIEW000000000000000';
  const start=new Date(); start.setHours(10,0,0,0);
  const end=new Date(start.getTime()+60*60*1000);
  const interview={id:interviewId,applicationId:'01JAPPLICATION0000000000000',candidateId:'01JCANDIDATE00000000000000',candidateName:'测试候选人',jdId:'01JJOB0000000000000000000',jdVersionId:'01JJDVERSION00000000000000',jdTitle:'测试岗位',workflowTemplateVersionId:'01JWORKFLOWV0000000000000',workflowStepId:'01JSTEP0000000000000000000',stepCode:'INTERVIEW_ROUND_1',interviewType:'INTERNAL',roundNo:1,clientSubmissionId:null,status:'SCHEDULED',scheduledStartAt:start.toISOString(),scheduledEndAt:end.toISOString(),timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',locationText:null,meetingUrl:'https://meeting.example/1',candidateNotificationChannel:'IN_APP',candidateConfirmationStatus:'PENDING',ownerUserId:actor.userId,ownerDepartmentId:actor.homeDepartmentId,nextResponsibleUserId:actor.userId,version:0,createdByUserId:actor.userId,createdAt:start.toISOString(),updatedByUserId:actor.userId,updatedAt:start.toISOString()};
  const participant={interviewId,userId:actor.userId,participantRole:'INTERVIEWER',responseStatus:'PENDING',createdAt:start.toISOString()};
  const notification={id:'01JNOTIFICATION000000000000',interviewId,notificationType:'INTERVIEW_INVITATION',recipientType:'CANDIDATE',recipientReference:interview.candidateId,channel:'IN_APP',templateVersionId:'01JTEMPLATEV00000000000000',renderedSubject:'面试邀请',renderedBody:'请于今日 10:00 参加一面',status:'PENDING_MANUAL',externalMessageId:null,lastErrorCode:null,lastErrorDetail:null,retryCount:0,version:0,createdAt:start.toISOString(),updatedAt:start.toISOString()};
  await page.unroute('**/api/v1/interviews?*');
  await page.route('**/api/v1/interviews?*', route => route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({items:[interview],nextCursor:null,hasMore:false})}));
  await page.route(`**/api/v1/interviews/${interviewId}/materials`, route => route.fulfill({json:{interviewId,candidateId:interview.candidateId,candidateName:'测试候选人',jdId:interview.jdId,jdVersionId:interview.jdVersionId,jdVersionNo:1,jdTitle:'测试岗位',jdRawText:'负责测试招聘流程',resumeVersionId:'01JRESUMEVER0000000000000',resumeVersionNo:1,resumeFileName:'测试候选人.pdf',resumeMediaType:'application/pdf',resumeSizeBytes:1024}}));
  await page.route(`**/api/v1/interviews/${interviewId}`, route => route.fulfill({json:{interview,participants:[participant]}}));
  await page.route(`**/api/v1/interviews/${interviewId}/feedback?*`, route => route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/interview-feedback-batch?*', route => route.fulfill({json:{visibleInterviewIds:[interviewId],items:[],hasMore:false,nextCursor:null}}));
  await page.route(`**/api/v1/interviews/${interviewId}/notifications?*`, route => route.fulfill({json:{items:[notification],hasMore:false,nextCursor:null}}));
  await page.route(`**/api/v1/interviews/${interviewId}/schedule-history?*`, route => route.fulfill({json:{items:[{id:'history-1',interviewId,actionType:'CREATE',beforeSnapshot:null,afterSnapshot:'{}',reason:'已确认排期',operatedByUserId:actor.userId,occurredAt:start.toISOString()}],hasMore:false,nextCursor:null}}));
  const interviewApplication={id:interview.applicationId,candidateId:interview.candidateId,demandId:'01JDEMAND0000000000000000',jdId:interview.jdId,jdVersionId:interview.jdVersionId,resumeVersionId:'01JRESUMEVER0000000000000',workflowTemplateVersionId:interview.workflowTemplateVersionId,matchPlanVersionId:'01JMATCHPLANV000000000000',recruitmentType:'INTERNAL',currentStage:'IN_PROCESS',currentStepCode:'INTERVIEW_ROUND_1',currentStepRoundNo:1,currentStepStatus:'PENDING',currentInterviewType:'INTERNAL',activeInterviewRoundNos:[1],outcome:null,ownerUserId:actor.userId,ownerDepartmentId:actor.homeDepartmentId,terminalAt:null,version:0,createId:actor.userId,createTime:start.toISOString(),updateId:actor.userId,updateTime:start.toISOString()};
  await page.route(`**/api/v1/applications/${interview.applicationId}`, route => route.fulfill({ json: { application: interviewApplication, collaboratorUserIds: [], workflowSteps: [{ id: interview.workflowStepId, applicationId: interview.applicationId, templateVersionId: interview.workflowTemplateVersionId, stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, sequenceNo: 1, status: 'PENDING', version: 0 }], decisions: [], offerStatusRecords: [], elapsedSeconds: 3600 } }));

  await page.goto('/schedule');
  await expect(page.getByRole('heading',{name:'任务栏'})).toBeVisible();
  await expect(page.getByText('测试候选人',{exact:true})).toBeVisible();
  await capture(page, 'final-schedule');
  await page.getByText('测试候选人',{exact:true}).click();
  await expect(page.getByText('面试事实与处理')).toBeVisible();
  await expect(page.getByText('排期变更历史', { exact: true })).toBeVisible();
  await page.waitForTimeout(500);
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);
  await reviewDialog(page, 'interview');
  await expect(page.getByRole('dialog').getByRole('button', { name: /^改\s*期$/ })).toHaveCount(0);
  for (const [trigger, title] of [['取消', '取消面试'], ['填写面评', '填写面评']]) {
    const button = page.getByRole('dialog').getByRole('button', { name: new RegExp(`^${trigger!.split('').join('\\s*')}$`) });
    await button.click();
    await expect(page.getByRole('dialog', { name: title!, exact: true })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(1);
    await page.getByRole('dialog').press('Escape');
    await expect(page.getByRole('dialog', { name: '面试事实与处理', exact: true })).toBeVisible();
    await expect(button).toBeFocused();
  }

  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.getByRole('dialog').press('Escape');
  await page.route('**/api/v1/applications?*', route => route.fulfill({ json: { items: [{ id: interview.applicationId, candidateId: interview.candidateId, jdId: interview.jdId, recruitmentType: 'INTERNAL' }], hasMore: false, nextCursor: null } }));
  await page.route(`**/api/v1/applications/${interview.applicationId}`, route => route.fulfill({ json: { application: interviewApplication, collaboratorUserIds: [], workflowSteps: [{ id: interview.workflowStepId, applicationId: interview.applicationId, templateVersionId: interview.workflowTemplateVersionId, stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, sequenceNo: 1, status: 'PENDING', version: 0 }], decisions: [], offerStatusRecords: [], elapsedSeconds: 3600 } }));
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/interviews', async route => {
    expect(route.request().headers()['x-csrf-token']).toBe('interview-token');
    expect(route.request().postDataJSON()).toMatchObject({ applicationId: interview.applicationId, workflowStepId: interview.workflowStepId });
    await pending;
    await route.fulfill({ json: { interview, participants: [participant] } });
  });
  await page.goto('/schedule');
  await page.getByRole('button', { name: '新建面试' }).click();
  await expect(page.getByRole('dialog', { name: '新建面试', exact: true })).toBeVisible();
  await page.getByLabel('候选人 / 应聘岗位', { exact: true }).fill('测试候选人');
  await page.locator('.ant-select-item-option-content').filter({hasText:'测试候选人'}).click();
  await page.getByLabel('面试轮次', { exact: true }).click();
  await page.getByText('面试 · 第 1 轮 · 待处理', { exact: true }).click();
  const nextDay=new Date(Date.now()+86400000).toISOString().slice(0,10);
  await page.getByLabel('开始时间', { exact: true }).fill(`${nextDay} 10:00`);
  await page.getByLabel('开始时间', { exact: true }).press('Tab');
  await page.getByLabel('结束时间', { exact: true }).fill(`${nextDay} 10:30`);
  await page.getByLabel('结束时间', { exact: true }).press('Tab');
  await expect(page.locator('.ant-select-selection-item').filter({hasText:actor.displayName})).toBeVisible();
  await page.getByRole('button', { name: '创建排期' }).click();
  await expect(page.getByRole('button', { name: '创建排期' })).toBeDisabled();
  await expect(page.getByRole('dialog', { name: '新建面试', exact: true })).toBeVisible();
  release();
  await expect(page.getByRole('dialog', { name: '面试事实与处理', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(page.getByText('排期变更历史', { exact: true })).toBeVisible();
  await reviewDialog(page, 'continuous-created-interview');
});

test('searches shared talent and offers scored transfer without effective-contact actions', async ({ page }) => {
  await page.unroute('**/api/v1/talent-search?*');
  await page.route('**/api/v1/talent-search?*', async route => {
    if (!route.request().url().includes('q=Java')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ candidateId: 'candidate-search-1', resumeVersionId: 'resume-search-1', factSnapshotId: 'fact-search-1', fullName: '张三', email: 'zhangsan@example.com', phone: '13800000000', currentRole: 'Java 工程师', city: '上海', skills: ['Java', 'Spring'], employers: ['示例科技'], roles: ['Java 工程师'], projects: ['支付平台'], certificates: [], languages: ['英语'], degrees: ['本科'], industries: ['金融'], tags: ['平台开发'], workYears: 5, engagementLock: null, conditionMatches: [{ code: 'SKILL', expected: 'Java', matched: true, matchedValues: ['Java'] }], applicationSummaries: [], interviewSummaries: [], clientSubmissionSummaries: [] }], nextCursor: null, hasMore: false }) });
  });

  await page.goto('/shared-library');
  await page.getByRole('textbox', { name: '共享简历关键词搜索' }).fill('Java');
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page).toHaveURL(/q=Java/);
  await expect(page.getByRole('row').filter({ has: page.getByRole('button', { name: '张三', exact: true }) })).toContainText('Java');
  await expect(page.getByRole('button', { name: '转入我的简历' }).last()).toBeVisible();
  await expect(page.getByText('记录有效联系')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

for (const failure of ['task-detail', 'task-list'] as const) {
  test(`restores resume parsing after ${failure} read failure without restarting it`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    let unavailable = true;
    let completed = false;
    let parseRequests = 0;
    const candidate = { id: 'candidate-recovery', fullName: '恢复测试候选人', ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, status: 'ACTIVE', version: 1 };
    const task = () => ({ taskId: 'task-recovery', resourceId: 'resume-recovery', resourceType: 'RESUME_VERSION', taskType: 'RESUME_PARSE', status: completed ? 'SUCCEEDED' : 'RUNNING', attemptCount: 1, maxAttempts: 3, progressPercent: completed ? 100 : 40, errorCode: null, errorDetail: null, retryable: false, resultResourceId: completed ? 'snapshot-recovery' : null, version: 1, createdAt: '2026-09-01T00:00:00Z', startedAt: '2026-09-01T00:00:01Z', finishedAt: null, updatedAt: '2026-09-01T00:00:02Z' });
    const problem = { status: 503, title: 'Unavailable', code: 'TEMPORARY_UNAVAILABLE', detail: '解析任务读取暂不可用', recoverable: true };
    await page.route('**/api/v1/candidates?*', route => route.fulfill({ json: { items: [candidate], nextCursor: null, hasMore: false } }));
    await page.route('**/api/v1/candidates/candidate-recovery', route => route.fulfill({ json: candidate }));
    await page.route('**/api/v1/candidates/candidate-recovery/resume-versions?*', route => route.fulfill({ json: { items: [{ id: 'resume-recovery', resumeId: 'resume-1', versionNo: 1, originalFileName: '恢复测试.pdf', status: 'ACTIVE', createdAt: '2026-09-01T00:00:00Z' }], nextCursor: null, hasMore: false } }));
    await page.route('**/api/v1/tasks?*', route => unavailable && failure === 'task-list'
      ? route.fulfill({ status: 503, json: problem }) : route.fulfill({ json: [task()] }));
    await page.route('**/api/v1/tasks/task-recovery', route => unavailable && failure === 'task-detail'
      ? route.fulfill({ status: 503, json: problem }) : route.fulfill({ json: task() }));
    await page.route('**/api/v1/resume-versions/resume-recovery/parse', route => {
      parseRequests++;
      return route.fulfill({ status: 202, json: task() });
    });
    await page.route('**/api/v1/resume-versions/resume-recovery/facts', route => !completed
      ? route.fulfill({ status: 404, json: { status: 404, title: 'Not Found', code: 'RESUME_FACTS_NOT_FOUND', detail: '尚无事实快照', recoverable: false } })
      : route.fulfill({ json: {
        snapshot: { id: 'snapshot-recovery', resumeVersionId: 'resume-recovery', snapshotVersion: 1, sourceType: 'AI_PARSED', fullName: candidate.fullName, currentRole: '恢复后的平台工程师' },
        workExperiences: [], projectExperiences: [], educations: [], simpleFacts: [],
        skills: [{ id: 'recovery-skill', skillName: '恢复验证技能', yearsValue: 3 }],
      } }));
    await page.route('**/api/v1/resume-versions/resume-recovery/extracted-text', route => route.fulfill({ json: { extractionRunId: 'extract-recovery', route: 'NATIVE', pageCount: 1, coverageRatio: 1, pages: [{ pageNo: 1, text: '恢复后的平台工程师' }] } }));
    await page.goto('/talents');
    await page.getByRole('button', { name: '详情与版本' }).click();
    await expect(page.getByText(failure === 'task-detail' ? '暂时无法读取解析任务状态' : '暂时无法恢复解析任务状态', { exact: true })).toBeVisible();
    const start = page.getByRole('button', { name: '开始解析', exact: true });
    await expect(start).toBeDisabled();
    const retry = page.getByRole('button', { name: '重新读取任务', exact: true });
    await expect(retry).toBeVisible();
    unavailable = false;
    await retry.focus();
    await retry.press('Enter');
    await expect(page.getByRole('progressbar', { name: '简历解析进度' })).toHaveAttribute('aria-valuenow', '40');
    await expect(start).toBeDisabled();
    await expect(page.getByRole('button', { name: /^取\s*消$/ })).toBeVisible();
    completed = true;
    await expect(page.getByText('解析任务：已完成', { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('恢复验证技能 · 3年', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '重新解析', exact: true })).toBeEnabled();
    expect(parseRequests).toBe(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    expect(errors).toEqual([]);
  });
}

test('shows separated resume facts and completes a reparse task', async ({ page }) => {
  await page.unroute('**/api/v1/candidates?*');
  await page.route('**/api/v1/candidates?*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 'candidate-1', fullName: '张三', primaryEmail: 'zhangsan@example.com', primaryPhone: '13800000000', ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, status: 'ACTIVE', version: 1, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }], nextCursor: null, hasMore: false }) });
  });
  await page.route('**/api/v1/candidates/candidate-1', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'candidate-1', fullName: '张三', primaryEmail: 'zhangsan@example.com', primaryPhone: '13800000000', ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, status: 'ACTIVE', version: 1, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }) });
  });
  await page.route('**/api/v1/candidates/candidate-1/resume-versions?limit=20', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 'resume-version-1', resumeId: 'resume-1', intakeId: 'intake-1', versionNo: 1, originalFileName: '张三-Java.pdf', mediaType: 'application/pdf', sizeBytes: 4096, contentSha256: 'a'.repeat(64), status: 'ACTIVE', createdAt: '2026-09-01T00:00:00Z' }], nextCursor: null, hasMore: false }) });
  });
  await page.route('**/api/v1/resume-versions/resume-version-1/facts', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ snapshot: { id: 'snapshot-1', resumeVersionId: 'resume-version-1', parseSnapshotId: 'parse-1', previousSnapshotId: null, snapshotVersion: 1, sourceType: 'AI_PARSED', currentMarker: 'CURRENT', fullName: '张三', email: 'zhangsan@example.com', phone: '13800000000', currentRole: 'Java 工程师', city: '上海', correctedByUserId: null, correctionReason: null, createdAt: '2026-09-01T00:00:00Z' }, workExperiences: [{ id: 'work-row-1', factSnapshotId: 'snapshot-1', itemKey: 'work_1', employer: '示例科技', roleName: 'Java 工程师', startDate: '2021-01-01', endDate: null, descriptionText: '负责后端平台', responsibilitiesJson: '["平台开发"]', achievementsJson: '[]' }], projectExperiences: [], educations: [{ id: 'edu-row-1', factSnapshotId: 'snapshot-1', itemKey: 'edu_1', school: '示例大学', degreeName: '本科', major: '计算机', startDate: null, endDate: null, learningMode: '全日制' }], skills: [{ id: 'skill-row-1', factSnapshotId: 'snapshot-1', itemKey: 'skill_1', skillName: 'Java', levelName: '高级', yearsValue: 5 }], simpleFacts: [{ id: 'language-row-1', factSnapshotId: 'snapshot-1', factType: 'LANGUAGE', itemKey: 'language_1', nameValue: '英语', levelValue: 'CET-6', issuerValue: null, startDate: null, endDate: null }] }) });
  });
  await page.route('**/api/v1/resume-versions/resume-version-1/extracted-text', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ extractionRunId: 'extract-1', route: 'NATIVE', pageCount: 1, coverageRatio: 1, pages: [{ pageNo: 1, text: 'Java 工程师，5 年经验' }] }) });
  });
  await page.route('**/api/v1/auth/csrf', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'e2e-token' }) });
  });
  await page.route('**/api/v1/resume-versions/resume-version-1/parse', async (route) => {
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    await route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ taskId: 'task-1', resumeVersionId: 'resume-version-1', status: 'QUEUED', attemptCount: 0, maxAttempts: 3, errorCode: null, retryable: false, resultFactSnapshotId: null, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }) });
  });
  await page.route('**/api/v1/tasks/task-1', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ taskId: 'task-1', resourceId: 'resume-version-1', resourceType: 'RESUME_VERSION', taskType: 'RESUME_PARSE', status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, errorCode: null, errorDetail: null, retryable: false, resultResourceId: 'snapshot-2', version: 2, createdAt: '2026-09-01T00:00:00Z', startedAt: '2026-09-01T00:00:01Z', finishedAt: '2026-09-01T00:00:02Z', updatedAt: '2026-09-01T00:00:02Z' }) });
  });

  await page.goto('/talents');
  await page.getByRole('button', { name: '详情与版本' }).click();
  await expect(page.getByRole('tab', { name: '专业事实' })).toBeVisible();
  await expect(page.getByText('Java · 5年')).toBeVisible();
  await page.locator('.workspace-dialog:visible').evaluate(async element=>{await Promise.all(element.getAnimations({subtree:true}).map(animation=>animation.finished.catch(()=>undefined)));});
  const controlledTab=page.getByRole('tab', { name: /受控条件/ });
  await controlledTab.click();
  await expect(controlledTab).toHaveAttribute('aria-selected','true');
  await expect(page.getByText('示例大学 本科 计算机')).toBeVisible();
  await page.getByRole('button', { name: '重新解析' }).click();
  await expect(page.getByText('解析任务：已完成')).toBeVisible();
  const continuous = await rememberDialog(page);
  await page.route('**/api/v1/fact-corrections', route => {
    expect(route.request().postDataJSON()).toMatchObject({ resumeVersionId: 'resume-version-1', baseFactSnapshotId: 'snapshot-1', facts: { candidate: { fullName: '张三修订' } } });
    return route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: 'Unavailable', detail: '事实保存暂不可用', code: 'UNAVAILABLE', recoverable: true } });
  });
  await page.getByRole('button', { name: 'HR 更正', exact: true }).click();
  await continuous('张三');
  await page.getByLabel('姓名', { exact: true }).fill('张三修订');
  await page.getByLabel('更正原因', { exact: true }).fill('候选人已核对姓名');
  await page.getByRole('button', { name: '保存更正' }).click();
  await expect(page.getByText('事实保存暂不可用')).toBeVisible();
  await expect(page.getByLabel('姓名', { exact: true })).toHaveValue('张三修订');
  await continuous('张三');
  await reviewDialog(page, 'continuous-candidate-correction');
  await page.getByRole('button', { name: '取消更正' }).click();
  await continuous('张三');
});

test('个人评分证据复核后推荐失败可恢复并查看确切招聘版本', async ({ page }, testInfo) => {
  const defaultWeightProfile = { profile: { id: 'default-weight-profile', ownerUserId: null, name: '公司默认权重', status: 'ACTIVE', currentVersionNo: 1, version: 1, updatedAt: '2026-09-01T00:00:00Z' }, currentVersion: { id: 'default-weight-version-1', versionNo: 1, skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20, screeningSettingsJson: JSON.stringify({criteria:[],defaultSortMode:'REFERENCE_SCORE'}) } };
  const scoringConfiguration = { plan: { id: 'plan-1', jdVersionId: 'jd-version-1', name: '正式方案', currentVersionNo: 1 }, version: { id: 'plan-version-1', planId: 'plan-1', jdVersionId: 'jd-version-1', versionNo: 1, status: 'ACTIVE', skillsEnabled: true, roleEnabled: true, yearsEnabled: true, responsibilityEnabled: true, industryProjectEnabled: true, version: 1, activatedAt: '2026-09-01T00:00:00Z' }, criterionPlan: { id: 'criterion-plan-1', planMaxBonusPoints: 10 }, criteria: [{ id: 'criterion-language', planVersionId: 'criterion-plan-1', criterionCode: 'LANGUAGE', level: 'PREFER', operator: 'IN', expectedValueJson: '{"values":["英语"]}', bonusPoints: 10, reason: '客户沟通', evidencePolicy: 'EXPLICIT_FACT_ONLY', selectableByHr: true, sequenceNo: 1 }] };
  await page.route('**/api/v1/scoring-defaults', route => route.fulfill({json:{scoringConfiguration,weightProfile:defaultWeightProfile}}));
  await page.unroute('**/api/v1/jds?limit=20');
  await page.route('**/api/v1/jds?limit=20', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 'jd-1', demandId: 'demand-1', demandCode: 'DEMAND-001', title: 'Java 工程师', status: 'ACTIVE', currentVersionNo: 1, clientId: 'client-1', clientName: '示例客户', ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, createdAt: '2026-09-01T00:00:00Z' }], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/jds/jd-1/versions?limit=20', route => route.fulfill({json:{items:[{id:'jd-version-1',jdId:'jd-1',versionNo:1,title:'Java 工程师',status:'ACTIVE',rawText:'Java 工程师职位描述'}],nextCursor:null,hasMore:false}}));
  await page.route('**/api/v1/jd-versions/jd-version-1/active-scoring-config', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ jdVersionId: 'jd-version-1', workflowTemplateActive: true, scoringConfigurationActive: true, readyForApplication: true, scoringConfiguration }) }));
  await page.unroute('**/api/v1/weight-profiles?*');
  await page.route('**/api/v1/weight-profiles?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ profile: { id: 'weight-profile-1', ownerUserId: actor.userId, name: '默认权重', status: 'ACTIVE', currentVersionNo: 1, version: 1, updatedAt: '2026-09-01T00:00:00Z' }, currentVersion: { id: 'weight-version-1', versionNo: 1, skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20 } }], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/me/jd-scoring-configs/plan-version-1/preferences', route => route.fulfill({ status: 200, contentType: 'application/json', body: 'null' }));
  await page.unroute('**/api/v1/candidates?*');
  await page.route('**/api/v1/candidates?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [{ id: 'candidate-1', fullName: '张三', primaryEmail: 'zhangsan@example.com', primaryPhone: null, ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, status: 'ACTIVE', version: 1, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }], nextCursor: null, hasMore: false }) }));
  await page.route('**/api/v1/resume-versions/resume-version-1/facts**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ snapshot: { id: 'resume-fact-1', resumeVersionId: 'resume-version-1', parseSnapshotId: 'parse-1', previousSnapshotId: null, snapshotVersion: 3, sourceType: 'AI_PARSE', currentMarker: 'CURRENT', correctedByUserId: null, correctionReason: null, fullName: '张三', email: 'zhangsan@example.com', phone: null, currentRole: 'Java 工程师', city: '上海', age: 30, gender: 'MALE', jobIntention: '高级 Java 工程师', createdAt: '2026-09-01T00:00:00Z' }, workExperiences: [], projectExperiences: [], educations: [], skills: [{ id: 'skill-1', factSnapshotId: 'resume-fact-1', itemKey: 'java', skillName: 'Java', levelName: '高级', yearsValue: 6 }], simpleFacts: [] }) }));
  let candidateDetailReads = 0;
  await page.route('**/api/v1/candidates/candidate-1', route => {
    candidateDetailReads += 1;
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'candidate-1', fullName: '张三', primaryEmail: 'zhangsan@example.com', primaryPhone: null, ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, status: 'ACTIVE', version: 1, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }) });
  });
  await page.route('**/api/v1/candidate-pool-statuses?*', route => route.fulfill({json:[{candidateId:'candidate-1',candidateName:'张三',resumeVersionId:'resume-version-1',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[]}]}));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'e2e-token' }) }));
  const run = { id: 'run-1', taskId: 'task-match-1', jdVersionId: 'jd-version-1', jdFactSnapshotId: 'jd-fact-1', planVersionId: 'plan-version-1', weightProfileVersionId: 'weight-version-1', personalPreferenceVersionId: null, selectionSnapshotId: 'selection-1', sortMode: 'REFERENCE_SCORE', status: 'SUCCEEDED', totalCount: 1, succeededCount: 1, failedCount: 0, errorCode: null, version: 2, createdAt: '2026-09-01T00:00:00Z', startedAt: '2026-09-01T00:00:01Z', finishedAt: '2026-09-01T00:00:02Z', updatedAt: '2026-09-01T00:00:02Z' };
  await page.route('**/api/v1/match-runs/run-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(run) }));
  await page.route('**/api/v1/tasks/task-match-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ taskId: 'task-match-1', resourceId: 'run-1', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN', status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, errorCode: null, errorDetail: null, retryable: false, resultResourceId: 'run-1', version: 2, createdAt: '2026-09-01T00:00:00Z', startedAt: '2026-09-01T00:00:01Z', finishedAt: '2026-09-01T00:00:02Z', updatedAt: '2026-09-01T00:00:02Z' }) }));
  const result = { resultId: 'result-1', baseEvaluationId: 'base-1', candidateId: 'candidate-1', resumeVersionId: 'resume-version-1', professionalScore: 82.5, bonusScore: 10, referenceScore: 92.5, mustTier: 'PASS', stableRank: 1, details: { jdFactSnapshotId: 'jd-fact-1', resumeFactSnapshotId: 'resume-fact-1', candidateId: 'candidate-1', resumeVersionId: 'resume-version-1', professionalScore: 82.5, bonusScore: 10, referenceScore: 92.5, mustTier: 'PASS', selectedBonusCap: 10, dimensions: [{ code: 'CORE_SKILLS', enabled: true, configuredWeight: 20, status: 'KNOWN', score: 100, explanation: '2/2 项技能命中', highlights: ['Java ← Java'] }, { code: 'ROLE_EXPERIENCE', enabled: true, configuredWeight: 20, status: 'KNOWN', score: 80, explanation: '岗位经历命中', highlights: [] }, { code: 'RELATED_YEARS', enabled: true, configuredWeight: 20, status: 'KNOWN', score: 75, explanation: '年限事实', highlights: [] }, { code: 'RESPONSIBILITY_MATCH', enabled: true, configuredWeight: 20, status: 'KNOWN', score: 80, explanation: '职责命中', highlights: [] }, { code: 'INDUSTRY_AND_PROJECTS', enabled: true, configuredWeight: 20, status: 'KNOWN', score: 70, explanation: '项目命中', highlights: [] }], criteria: [{ definitionId: 'criterion-language', code: 'LANGUAGE', level: 'PREFER', status: 'PASS', selected: true, configuredBonus: 10, awardedBonus: 10, reason: '客户沟通', actual: ['英语'], expected: ['英语'] }] } };
  await page.route('**/api/v1/match-runs/run-1/results?**', route => route.fulfill({json:{items:[result],hasMore:false,nextCursor:null}}));
  const pageErrors:string[]=[];page.on('pageerror',error=>pageErrors.push(error.message));
  const applicationKeys:string[]=[];
  let applicationCreated = false;
  let allowApplicationCreate = false;
  const createdApplication = { id: 'application-from-match-1', candidateId: 'candidate-1', demandId: 'demand-1', jdId: 'jd-1', jdVersionId: 'jd-version-1', resumeVersionId: 'resume-version-1', workflowTemplateVersionId: 'workflow-version-1', matchPlanVersionId: 'plan-version-1', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', outcome: null, ownerUserId: actor.userId, ownerDepartmentId: actor.homeDepartmentId, terminalAt: null, version: 0, createId: actor.userId, createTime: '2026-09-01T00:00:03Z', updateId: actor.userId, updateTime: '2026-09-01T00:00:03Z' };
  await page.route('**/api/v1/jd-versions/jd-version-1/recruitment-processing-access',route=>route.fulfill({json:{processingAllowed:true}}));
  await page.route('**/api/v1/jd-versions/jd-version-1/recruitment-readiness',route=>route.fulfill({json:{headcount:2,occupiedHeadcount:1,remainingHeadcount:1,jdId:'jd-1',jdVersionId:'jd-version-1',jdTitle:'Java 工程师',demandId:'demand-1',recruitmentType:'INTERNAL',canProcess:true,canManageWorkflow:true,activeWorkflowId:'workflow-version-1',activeWorkflow:null,draftWorkflows:[],standardSteps:[]}}));
  await page.route('**/api/v1/applications/batch', async route => {
    applicationKeys.push(route.request().headers()['idempotency-key']??'');
    expect(route.request().postDataJSON()).toEqual({ jdVersionId:'jd-version-1',items:[{candidateId:'candidate-1',resumeVersionId:'resume-version-1'}],reason:'HR 已复核总分、五维得分、AI 总结与必须项条件，确认进入招聘流程' });
    if (!allowApplicationCreate) {
      await route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ title: 'Conflict', status: 409, detail: '该候选人在当前岗位已有活动中的 Application', code: 'ACTIVE_APPLICATION_EXISTS', recoverable: true }) });
      return;
    }
    applicationCreated = true;
    await route.fulfill({ json:{items:[{candidateId:'candidate-1',resumeVersionId:'resume-version-1',status:'SUCCEEDED',applicationId:createdApplication.id}]} });
  });
  await page.route(`**/api/v1/applications/${createdApplication.id}`, route => route.fulfill({ json: { application: createdApplication, collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [], elapsedSeconds: 0 } }));

  const scorePosts:Record<string,unknown>[]=[];
  const exactFacts:string[]=[];
  const referenceReads:string[][]=[];
  const admission={candidateId:'candidate-1',candidateName:'张三',resumeVersionId:'resume-version-1',jdVersionId:'jd-version-1',resumeVersionNo:1,originalFileName:'绑定招聘简历.pdf',defaultMatchRunId:'company-run',admissionStatus:'ADMITTED',admissionId:'admission-1',admissionVersion:'1',defaultResult:{professionalScore:82.5,bonusScore:10,referenceScore:92.5,mustTier:'PASS'}};
  const companyRun={...run,id:'company-run',taskId:'company-task',weightProfileVersionId:'default-weight-version-1'};
  await page.route('**/api/v1/**',async route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname.replace('/api/v1','');
    const list=(items:unknown[])=>route.fulfill({json:{items,hasMore:false,nextCursor:null}});
    if(path==='/screening-candidates')return route.fulfill({json:[admission]});
    if(path==='/jd-versions/jd-version-1/screening-candidates')return list([admission]);
    if(path==='/match-jobs')return list([{jdVersionId:'jd-version-1',latestRunId:'company-run',lastRunAt:run.createdAt,runCount:1,candidateCount:1,scoredCandidateCount:1,activeRunCount:0}]);
    if(path==='/match-jobs/jd-version-1/bases')return list([{basisRunId:'company-run',basisKey:'company-basis',jdVersionId:'jd-version-1',planVersionId:'plan-version-1',weightProfileVersionId:'default-weight-version-1',sortMode:'REFERENCE_SCORE',lastRunAt:run.createdAt,runCount:1,candidateCount:1,scoredCandidateCount:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20,selectedCriterionIds:[]}]);
    if(path==='/match-jobs/jd-version-1/results')return list([{runId:'company-run',scoredAt:run.createdAt,result}]);
    if(path==='/match-runs/company-run')return route.fulfill({json:companyRun});
    if(path==='/match-runs'&&request.method()==='POST'){
      expect(request.headers()['idempotency-key']).toBeTruthy();
      scorePosts.push(request.postDataJSON() as Record<string,unknown>);
      return route.fulfill({status:202,json:{...run,status:'QUEUED',succeededCount:0,finishedAt:null}});
    }
    if(path==='/match-runs')return list([companyRun]);
    if(path==='/jd-versions/jd-version-1/facts')return route.fulfill({json:{facts:{title:'Java 工程师',summary:'负责业务平台研发'}}});
    if(path==='/jds/jd-1')return route.fulfill({json:{id:'jd-1',demandId:'demand-1',title:'Java 工程师',currentVersionNo:1}});

    if(path==='/resume-version-references'){
      referenceReads.push(url.searchParams.getAll('resumeVersionIds'));
      return route.fulfill({json:[{id:'resume-version-1',versionNo:1,originalFileName:'绑定招聘简历.pdf'}]});
    }
    if(path==='/candidates/candidate-1/resume-versions')return list([{id:'resume-version-1',resumeId:'resume-1',intakeId:'intake-1',versionNo:1,originalFileName:'绑定招聘简历.pdf',mediaType:'application/pdf',sizeBytes:1000,contentSha256:'a'.repeat(64),status:'AVAILABLE',createdAt:run.createdAt}]);
    if(path==='/resume-versions/resume-version-1/extracted-text')return route.fulfill({json:{pages:[],pageCount:0,coverageRatio:0}});
    if(path.startsWith('/resume-versions/resume-version-1/facts'))exactFacts.push(path);
    if(['/applications/application-from-match-1/communications','/applications/application-from-match-1/intentions'].includes(path))return route.fulfill({json:[]});
    return route.fallback();
  });
  await page.goto('/matching');
  await expect(page.locator('.matching-total b')).toHaveText('92.50');
  expect(candidateDetailReads).toBe(0);
  const scheme = page.getByRole('radiogroup', { name: '评分方案', exact: true });
  await scheme.getByText('个人方案', { exact: true }).click();
  await expect(scheme.getByRole('radio', { name: '个人方案', exact: true })).toBeChecked();
  await page.getByRole('button',{name:'重新评分',exact:true}).click();
  await expect.poll(()=>scorePosts.length).toBe(1);
  await expect(page).toHaveURL(/run=run-1/);
  expect(scorePosts[0]).toMatchObject({jdVersionId:'jd-version-1',resumeVersionIds:['resume-version-1'],weightProfileVersionId:'weight-version-1'});
  await expect(page.locator('.matching-total b')).toHaveText('92.50');
  await expect(page.locator('.matching-score-table').getByText('张三',{exact:true})).toBeVisible();
  await expect(page.locator('.matching-total')).toContainText('专业 82.50 · +10');
  expect(candidateDetailReads).toBe(0);
  const evidenceTrigger=page.getByRole('button', { name: '查看候选人的评分详情' });
  if(testInfo.project.name==='mobile')await evidenceTrigger.evaluate(button=>{
    const content=button.closest<HTMLElement>('.ant-table-content');
    if(!content)return;
    const buttonBox=button.getBoundingClientRect(); const contentBox=content.getBoundingClientRect();
    content.scrollLeft+=buttonBox.left-contentBox.left-12;
  });
  await evidenceTrigger.click();
  await expect(page.getByRole('dialog', { name: '张三 · 评分详情' }).getByText('AI 总结', { exact: true })).toBeVisible();
  await expect(page.locator('.matching-evidence-dimension')).toHaveCount(5);
  await expect(page.getByRole('button',{name:/原文依据/})).toHaveCount(0);
  expect(exactFacts).toEqual([]);
  await expect(page.locator('.matching-evidence-dimension')).toHaveCount(5);
  await reviewDialog(page, 'matching-evidence');
  expect((await new AxeBuilder({page}).include('.workspace-dialog').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('scoring-evidence.png'),animations:'disabled'});
  await capture(page, testInfo.project.name === 'mobile' ? 'final-matching-mobile' : 'final-matching');

  await page.getByRole('dialog', { name: '张三 · 评分详情' }).press('Escape');
  await expect(page.getByRole('button', { name: '反馈判断' })).toHaveCount(0);

  await page.getByRole('button', { name: '推荐到流程' }).click();
  const recommendation = page.getByRole('dialog', { name: '推荐候选人到招聘流程' });
  await expect(recommendation.getByText('推荐后待 HR 跟进')).toHaveCount(0);
  await recommendation.getByLabel('人工推荐依据').fill('HR 已复核总分、五维得分、AI 总结与必须项条件，确认进入招聘流程');
  await recommendation.getByRole('button', { name: '确认推荐 1 人' }).click();
  await expect(page.getByText('候选人未处理成功')).toBeVisible();
  await expect(page.getByText(/该候选人在当前岗位已有活动中的 Application/)).toBeVisible();
  await expect(recommendation.getByLabel('人工推荐依据')).toHaveValue('HR 已复核总分、五维得分、AI 总结与必须项条件，确认进入招聘流程');
  allowApplicationCreate = true;
  await recommendation.getByRole('button', { name: '继续处理未完成项' }).click();
  await expect.poll(() => applicationCreated).toBe(true);
  expect(applicationKeys).toHaveLength(2);expect(applicationKeys[0]).toBeTruthy();expect(applicationKeys[1]).toBe(applicationKeys[0]);
  await recommendation.getByRole('button', { name: '查看招聘流程' }).click();
  const applicationDetails = page.getByRole('dialog', { name: '招聘活动详情' });
  await applicationDetails.getByRole('button', { name: '查看简历与 JD', exact: true }).click();
  const materials = page.getByRole('dialog', { name: '招聘材料' });
  await expect(materials.getByRole('region', { name: '简历原文件' })).toContainText('本次简历 · 第 1 版');
  await expect(materials.getByRole('region', { name: '简历原文件' })).toContainText('绑定招聘简历.pdf');
  expect(referenceReads).toContainEqual(['resume-version-1']);
  await page.screenshot({path:testInfo.outputPath('scoring-recruitment-version.png'),animations:'disabled'});
  await materials.press('Escape');
  await expect(applicationDetails).toBeVisible();
  await applicationDetails.press('Escape');
  await expect(recommendation).toBeVisible();
  await recommendation.getByRole('button', { name: /^完\s*成$/ }).click();
  await page.goto('/talents?candidate=candidate-1');
  await expect(page).toHaveURL(/\/talents\?candidate=candidate-1$/);
  await expect(page.getByRole('dialog',{name:'张三',exact:true})).toBeVisible();
  await reviewDialog(page,'scoring-candidate-detail');
  expect(pageErrors).toEqual([]);

});

test('opens the super-admin organization workspace without overflow', async ({ page }) => {
  await page.route('**/api/v1/admin/departments', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
      { id: 'department-1', code: 'ROOT', name: '总部', parentDepartmentId: null, status: 'ACTIVE', version: 0 },
    ]) });
  });
  await page.route('**/api/v1/admin/users?limit=200', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
      { id: actor.userId, displayName: actor.displayName, workEmail: actor.workEmail, homeDepartmentId: 'department-1', homeDepartmentName: '总部', directLeaderUserId: null, directLeaderName: null, status: 'ACTIVE', mustChangePassword: false, version: 1 },
    ]) });
  });
  await page.route('**/api/v1/admin/super-admins', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.goto('/admin/identity');
  await expect(page.getByRole('heading', { name: '组织与账号' })).toBeVisible();
  await expect(page.getByText('admin@example.com')).toBeVisible();
  await capture(page, 'final-settings-identity');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});

test('logs in through the real session and CSRF client flow', async ({ page }) => {
  let csrfReads = 0;
  let loginAttempts = 0;
  await page.unroute('**/api/v1/me');
  await page.route('**/api/v1/me', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Unauthorized',
        status: 401,
        detail: '需要有效登录会话',
        code: 'SESSION_REQUIRED',
        recoverable: true,
      }),
    });
  });
  await page.route('**/api/v1/auth/csrf', async (route) => {
    csrfReads += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: `e2e-token-${csrfReads}` }),
    });
  });
  await page.route('**/api/v1/auth/login', async (route) => {
    loginAttempts += 1;
    expect(route.request().headers()['x-csrf-token']).toBe(`e2e-token-${loginAttempts}`);
    if (loginAttempts === 1) {
      await route.fulfill({ status: 403, contentType: 'application/problem+json', body: JSON.stringify({
        title: '安全令牌失效', status: 403, detail: '请重试', code: 'CSRF_TOKEN_INVALID', recoverable: true,
      }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(actor) });
  });

  await page.goto('/login');
  await capture(page, 'final-login');
  await page.getByRole('textbox', { name: '账号' }).fill('admin@example.com');
  await page.getByRole('textbox', { name: '密码' }).fill('Admin-Password-123');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  await expect(page.getByRole('heading', { level: 2, name: '招聘流程转化', exact: true })).toBeVisible();
  expect(csrfReads).toBe(2);
  expect(loginAttempts).toBe(2);
});

test('presents the public recruiting homepage', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('让人才与机会在这里相遇');
  await expect(page.getByRole('list', { name: '招聘能力' })).toBeVisible();
  await expect(page.getByRole('button', { name: '登录工作台' })).toBeVisible();
  await capture(page, 'final-home');
});

test('renders the settings shell around recruitment configuration', async ({ page }) => {
  for (const endpoint of ['clients?limit=20', 'client-positions?limit=20', 'demands?limit=20']) {
    await page.route(`**/api/v1/${endpoint}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], nextCursor: null, hasMore: false }) }));
  }
  await page.goto('/recruitment/setup');
  await expect(page.getByRole('heading', { name: 'JD 库' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '设置导航' })).toBeVisible();
  await capture(page, 'final-settings');
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

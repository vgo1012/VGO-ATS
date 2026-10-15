import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('候选人姓名自定义搜索提交规范化关键词',async({page})=>{
  await reviewWorkspace(page);
  const applicationRequests:URL[]=[];
  page.on('request',request=>{const url=new URL(request.url());if(url.pathname.endsWith('/applications'))applicationRequests.push(url);});
  await page.route('**/api/v1/applications?**',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.goto('/recruitment');
  await page.getByRole('searchbox',{name:'搜索候选人姓名'}).fill('  林雨桐  ');
  await page.getByRole('button',{name:/^搜\s*索$/}).click();
  await expect.poll(()=>applicationRequests.at(-1)?.searchParams.get('q')).toBe('林雨桐');
});

test('列表直接办理，详情仅用流程记录展示多轮进度', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.emulateMedia({reducedMotion:'reduce'});
  const errors: string[] = [];
  const removedSectionRequests: string[] = [];
  page.on('request', request => { if (/\/(intentions|activity-records)(\?|$)/.test(request.url())) removedSectionRequests.push(request.url()); });
  page.on('pageerror', error => errors.push(error.message));
  const application = { id: 'app-ledger', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-a', workflowTemplateVersionId: 'workflow', matchPlanVersionId: 'plan', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', outcome: null, terminalAt: null, version: 1, createTime: '2026-09-07T01:00:00Z', updateTime: '2026-09-07T01:00:00Z' };
  const detail = { application, collaboratorUserIds: [], workflowSteps: [1, 2, 3, 4].map(roundNo => ({ id: `step-${roundNo}`, stepCode: `INTERVIEW_ROUND_${Math.min(roundNo, 3)}`, roundNo, sequenceNo: roundNo + 4, status: roundNo === 4 ? 'IN_PROGRESS' : 'PASSED', resultReason: roundNo === 4 ? null : `第 ${roundNo} 轮通过`, completedAt: roundNo === 4 ? null : '2026-09-07T01:00:00Z' })), decisions: [], offerStatusRecords: [], elapsedSeconds: 100 };
  let comment = '';
  let origin = '';
  let writes = 0;
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: [application], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/applications/app-ledger', route => route.fulfill({ json: detail }));
  await page.route('**/api/v1/applications/app-ledger/communications', async route => {
    if (route.request().method() === 'POST') {
      writes++;
      comment = route.request().postDataJSON().summary;
      origin = route.request().headers()['x-recruitment-action'];
      await route.fulfill({ json: { id: 'communication', applicationId: application.id, summary: comment } });
    } else await route.fulfill({ json: comment?[{id:'communication',summary:comment,communicatedAt:'2026-09-14T01:00:00Z',recordedByUserId:'hr-a'}]:[] });
  });
  await page.route('**/api/v1/clients?**', route => route.fulfill({ json: { items: [{ id: 'client-a', name: '甲公司' }, { id: 'client-b', name: '乙公司' }], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/candidates/person-a/client-restrictions?**', route => route.fulfill({ json: [] }));
  let feedbackReads=0;
  await page.route('**/api/v1/interviews?**', route => route.fulfill({json:{items:[1,2,3].map(roundNo=>({id:`interview-${roundNo}`,applicationId:application.id,workflowStepId:`step-${roundNo}`,stepCode:`INTERVIEW_ROUND_${roundNo}`,roundNo,interviewType:'INTERNAL',status:'COMPLETED',scheduledStartAt:'2026-09-07T01:00:00Z'})),hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/interview-feedback-batch?**', route => {
    feedbackReads++;
    return route.fulfill({json:{visibleInterviewIds:[1,2,3].map(n=>`interview-${n}`),items:[1,2,3].map(n=>({feedback:{id:`feedback-${n}`,interviewId:`interview-${n}`,authorUserId:'hr-a',status:'SUBMITTED'},currentVersion:{verdict:'PASS',shortComment:`第${n}轮正式评价`,createdAt:'2026-09-07T01:00:00Z'}})),hasMore:false,nextCursor:null}});
  });
  await page.goto('/recruitment');
  const row = page.getByRole('row').filter({ has: page.getByRole('button', { name: '详情与版本' }) });
  await expect(row.getByRole('button', { name: '第 6 轮面试' })).toBeVisible();
  await expect(row.getByRole('button', { name: '详情与版本' })).toBeVisible();
  await row.getByRole('button', { name: '待定', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('沟通情况')).toBeVisible();
  await dialog.getByLabel('沟通情况').fill('候选人确认意向，计划安排一面');
  await expect(dialog.getByRole('textbox')).toHaveCount(1);
  for(const label of ['渠道','沟通结果','预约跟进','流程管理'])await expect(dialog.getByText(label,{exact:true})).toHaveCount(0);
  await expect(dialog).toHaveCSS('opacity','1');
  await page.screenshot({path:testInfo.outputPath('pending-note.png'),animations:'disabled'});
  await dialog.getByRole('button', { name: '保存记录' }).click();
  await expect(dialog).toHaveCount(0);
  expect(writes).toBe(1);
  expect(origin).toBe('PENDING');
  await row.getByRole('button', { name: '详情与版本' }).click();
  await expect(dialog.getByText('流程记录', { exact: true })).toBeVisible();
  await expect(dialog.getByText('候选人确认意向，计划安排一面',{exact:true})).toBeVisible();
  for (const label of ['活动记录', '动态意向', '更新意向']) await expect(dialog.getByText(label, { exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('table',{name:'流程记录'}).getByRole('columnheader')).toHaveText(['步骤','面试时间','状态','本轮结果','面评']);
  await expect(dialog.getByRole('row').filter({hasText:'内部面试 · 第 4 轮'})).toHaveCount(0);
  for (const round of [1,2,3]) {
    const stepRow=dialog.getByRole('row').filter({hasText:`内部面试 · 第 ${round} 轮`});
    await expect(stepRow.getByRole('cell').first()).toHaveText(`内部面试 · 第 ${round} 轮`);
    await expect(stepRow).toContainText('已完成');
    await expect(stepRow.getByRole('cell').nth(3)).toHaveText('通过');
    await expect(stepRow).toContainText(`第${round}轮正式评价`);
    if(round===1)await expect(stepRow).not.toContainText('第2轮正式评价');
  }
  expect(feedbackReads).toBe(1);
  await expect(dialog.getByRole('button',{name:'查看面试材料',exact:true})).toBeVisible();
  await page.setViewportSize({width:1440,height:900});
  await expect(dialog.locator('.application-profile')).toContainText('林雨桐');
  await expect(dialog.locator('.application-profile')).toContainText('招聘历时：1 分钟');
  await expect(dialog.getByText('招聘资料',{exact:true})).toHaveCount(0);
  await expect(dialog.getByRole('combobox',{name:'材料对应面试轮次'})).toHaveCount(0);
  const materialsBox=await dialog.locator('.application-materials').boundingBox();
  const recordsBox=await dialog.getByText('流程记录',{exact:true}).boundingBox();
  expect(materialsBox!.y+materialsBox!.height).toBeLessThanOrEqual(recordsBox!.y);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.screenshot({ path: testInfo.outputPath('application-steps.png') });
  expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  expect(removedSectionRequests).toEqual([]);
  await expect(dialog.getByRole('button', { name: '预约沟通', exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: '安排面试时间', exact: true })).toHaveCount(0);
  await dialog.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(0);
  const interviewButton = row.getByRole('button', { name: '面试', exact: true });
  await interviewButton.focus();
  await interviewButton.press('Enter');
  await expect(dialog.getByLabel('开始时间', { exact: true })).toBeVisible();
  await expect(page.locator('.ant-modal-wrap:visible')).toHaveCount(1);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.screenshot({ path: testInfo.outputPath('ledger-interview.png') });
  const axe = await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(axe.violations).toEqual([]);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(interviewButton).toBeFocused();
  expect(writes).toBe(1);
  await row.getByRole('button', { name: '黑名单', exact: true }).click();
  await expect(dialog.getByRole('button', { name: /保\s*存/ })).toBeDisabled();
  await dialog.getByLabel('公司标签').click();
  await page.getByTitle('甲公司', { exact: true }).click();
  await expect(dialog.getByText(/记录【甲公司】黑名单标签并结束当前招聘流程/)).toBeVisible();
  await dialog.getByRole('button', { name: /取\s*消/ }).click();
  expect(writes).toBe(1);
  expect(errors).toEqual([]);
});

test('轮次数字直接打开对应表单，后续轮次不提前创建排期', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.emulateMedia({reducedMotion:'reduce'});
  const application={id:'app-rounds',candidateId:'person-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'resume-a',recruitmentType:'INTERNAL',currentStage:'IN_PROCESS',currentStepCode:'INTERVIEW_ROUND_1',currentStepRoundNo:2,ownerUserId:'hr-a',terminalAt:null};
  const steps=[1,2].map(roundNo=>({id:`round-step-${roundNo}`,stepCode:'INTERVIEW_ROUND_1',roundNo,sequenceNo:roundNo,status:roundNo===1?'PASSED':'IN_PROGRESS'}));
  const interviews=[1,2].map(roundNo=>({id:`round-interview-${roundNo}`,applicationId:application.id,workflowStepId:`round-step-${roundNo}`,candidateId:'person-a',jdId:'jd-a',stepCode:'INTERVIEW_ROUND_1',roundNo,interviewType:'INTERNAL',status:roundNo===1?'COMPLETED':'SCHEDULED',scheduledStartAt:'2099-09-15T06:00:00Z',scheduledEndAt:'2099-09-15T06:30:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',createdAt:'2026-09-14T01:00:00Z',version:1}));
  const writes:string[]=[];
  page.on('request',request=>{if(request.method()==='POST'&&/\/interviews|\/next-action/.test(request.url()))writes.push(request.url());});
  await page.route('**/api/v1/applications?**',route=>route.fulfill({json:{items:[application],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications/app-rounds',route=>route.fulfill({json:{application,workflowSteps:steps,collaboratorUserIds:[],decisions:[],offerStatusRecords:[]}}));
  await page.route('**/api/v1/interviews?**',route=>{const params=new URL(route.request().url()).searchParams;return route.fulfill({json:{items:interviews.filter(item=>(!params.get('workflowStepId')||item.workflowStepId===params.get('workflowStepId'))&&(!params.get('status')||item.status===params.get('status'))),hasMore:false,nextCursor:null}});});
  await page.route('**/api/v1/interviews/round-interview-*',route=>route.fulfill({json:{interview:interviews.find(item=>route.request().url().endsWith(item.id)),participants:[{userId:'hr-a'}]}}));
  await page.route('**/api/v1/interviews/round-interview-*/feedback?**',route=>route.fulfill({json:{items:route.request().url().includes('round-interview-1/')?[{feedback:{id:'feedback-1',status:'SUBMITTED',authorUserId:'hr-a'},currentVersion:{verdict:'PASS',shortComment:'第一轮正式面评',changeReason:'实际面试记录'}}]:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/interviews/round-interview-*/materials',route=>route.fulfill({json:{candidateName:'林雨桐',jdTitle:'高级前端工程师',candidateId:'person-a',jdId:'jd-a'}}));
  await page.goto('/recruitment');
  const first=page.getByRole('button',{name:'第 1 轮面试',exact:true});
  await first.click();
  const dialog=page.getByRole('dialog');
  await expect(dialog.getByLabel('面评',{exact:true})).toHaveValue('第一轮正式面评');
  await expect(dialog.getByLabel('面试轮次')).toHaveValue('内部面试 · 第 1 轮');
  await expect(dialog.getByRole('button',{name:'正式提交面评'})).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(first).toBeFocused();
  await page.getByRole('button',{name:'第 2 轮面试',exact:true}).click();
  await expect(dialog.getByLabel('开始时间',{exact:true})).toBeVisible();
  await expect(dialog.getByLabel('面评',{exact:true})).toBeVisible();
  await expect(dialog.getByLabel('提交依据',{exact:true})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'查看本次面试材料'})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'正式提交面评'})).toBeVisible();
  await expect(dialog.getByRole('button',{name:'填写面评'})).toHaveCount(0);
  const actions=dialog.locator('.ledger-interview-actions-main');
  await expect(actions.getByRole('button')).toHaveText(['关闭','正式提交面评']);
  await expect(dialog).toHaveCount(1);
  await expect(dialog).toHaveCSS('opacity','1');
  await page.screenshot({path:testInfo.outputPath('round-form.png'),animations:'disabled'});
  expect((await new AxeBuilder({page}).include('.ant-modal').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await actions.getByRole('button',{name:'关闭',exact:true}).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('button',{name:'第 2 轮面试',exact:true})).toBeFocused();
  await page.getByRole('button',{name:'第 3 轮面试',exact:true}).click();
  await expect(dialog.getByLabel('开始时间',{exact:true})).toBeEditable();
  await page.route('**/api/v1/wecom/collaboration/members?**',route=>route.fulfill({json:[]}));
  await dialog.getByText('企业微信日程',{exact:true}).click();
  await expect(dialog.getByRole('textbox',{name:/日程标题/})).toHaveValue('高级前端工程师（招聘平台与复杂业务系统方向） · 林雨桐');
  await dialog.getByText('企业微信会议',{exact:true}).click();
  const meetingTitle=dialog.getByRole('textbox',{name:/会议标题/});
  await expect(meetingTitle).toHaveValue(/ · 林雨桐$/);
  expect((await meetingTitle.inputValue()).length).toBeLessThanOrEqual(20);
  await expect(dialog.getByRole('combobox',{name:/企业微信发起人/})).toHaveCount(0);
  await expect(dialog.getByLabel('面评',{exact:true})).toHaveCount(0);
  await expect(dialog.getByRole('textbox',{name:/参会说明/})).toBeVisible();
  const extraPeople=dialog.getByRole('combobox',{name:/额外企业微信参与人/});
  await expect(extraPeople).toBeVisible();
  await expect(dialog.getByText('更多设置',{exact:true})).toHaveCount(0);
  await expect(dialog.getByText(/会议由本应用创建|会议管理员使用当前 HR|日程管理员使用当前 HR|实际面试官自动加入/)).toHaveCount(0);
  await dialog.screenshot({path:testInfo.outputPath('simplified-schedule.png')});
  expect(writes).toEqual([]);
});

test('黑名单无需证据引用即可保存，保留公司范围和原因', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const application = { id: 'app-blacklist', candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'resume-a', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', ownerUserId: 'hr-a', terminalAt: null };
  const writes: unknown[] = [];
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: [application], hasMore: false } }));
  await page.route('**/api/v1/applications/app-blacklist', route => route.fulfill({ json: { application, collaboratorUserIds: [], workflowSteps: [] } }));
  await page.route('**/api/v1/clients?**', route => route.fulfill({ json: { items: [{ id: 'client-a', name: '甲公司' }], hasMore: false } }));
  await page.route('**/api/v1/candidates/person-a/client-restrictions?**', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/applications/app-blacklist/client-restrictions', route => {
    writes.push(route.request().postDataJSON());
    return route.fulfill({ json: { id: 'restriction-a' } });
  });
  await page.goto('/recruitment');
  const trigger = page.getByRole('button', { name: '黑名单', exact: true });
  await trigger.focus();
  await trigger.press('Enter');
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('证据引用')).toHaveCount(0);
  await dialog.getByLabel('公司标签').click();
  await page.getByTitle('甲公司', { exact: true }).click();
  await dialog.getByLabel('原因评论').fill('已核验公司限制');
  await expect(dialog.getByLabel('事实来源')).toBeVisible();
  for (const width of [390, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await expect(dialog.getByRole('button', { name: /保\s*存/ })).toBeInViewport();
  }
  await page.screenshot({ path: testInfo.outputPath('blacklist-without-evidence.png') });
  expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await dialog.getByRole('button', { name: /保\s*存/ }).click();
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  expect(writes).toEqual([{ clientId: 'client-a', reasonDetail: '已核验公司限制', sourceChannel: 'MANUAL' }]);
  expect(errors).toEqual([]);
});

test('放弃成功移出进行中列表后焦点回到招聘日期控件', async ({ page }) => {
  await reviewWorkspace(page);
  const application = { id: 'app-ended-focus', candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'resume-a', recruitmentType: 'INTERNAL', currentStage: 'PATH_SELECTION', ownerUserId: 'hr-a', terminalAt: null };
  let ended = false;
  let saved: Record<string, unknown> | undefined;
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: ended ? [] : [application], hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/applications/app-ended-focus', route => route.fulfill({ json: { application, collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [] } }));
  await page.route('**/api/v1/applications/app-ended-focus/decisions', route => {
    saved = route.request().postDataJSON() as Record<string, unknown>;
    ended = true;
    return route.fulfill({ json: { id: 'decision-ended-focus', applicationId: application.id, decision: 'REJECT', reason: saved.reason } });
  });

  await page.goto('/recruitment');
  const row = page.getByRole('row').filter({ has: page.getByRole('button', { name: '详情与版本' }) });
  const trigger = row.getByRole('button', { name: '放弃', exact: true });
  await trigger.focus();
  await trigger.press('Enter');
  const dialog = page.getByRole('dialog', { name: '放弃', exact: true });
  await dialog.getByLabel('放弃类型').press('ArrowDown');
  await dialog.getByLabel('放弃类型').press('Enter');
  await dialog.getByLabel('原因评论').fill('双方确认不再继续');
  await dialog.getByRole('button', { name: '确认放弃' }).click();

  await expect(dialog).toHaveCount(0);
  await expect(row).toHaveCount(0);
  await expect(page.getByRole('button', { name: '上一周' })).toBeFocused();
  expect(saved).toMatchObject({ decision: 'REJECT', reason: '双方确认不再继续' });
});

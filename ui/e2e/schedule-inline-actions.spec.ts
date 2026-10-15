import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { ApplicationDetails } from '../src/recruitment/api';
import type { FollowUpPlan } from '../src/recruitment/followUpPlansApi';
import { reviewWorkspace } from './fixtures/ux-review';

test.use({ launchOptions: { ignoreDefaultArgs: ['--hide-scrollbars'] }, isMobile: false, hasTouch: false });

const empty = { items: [], hasMore: false, nextCursor: null };
const details: ApplicationDetails = {
  application: { id: 'a', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-v1', workflowTemplateVersionId: 'workflow-v1', matchPlanVersionId: 'match-v1', recruitmentType: 'INTERNAL', progressiveFollowUp: true, currentStage: 'PATH_SELECTION', outcome: null, ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', terminalAt: null, version: 1, createId: 'hr-a', createTime: '2026-09-07T00:00:00Z', updateId: 'hr-a', updateTime: '2026-09-07T00:00:00Z' },
  collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [], elapsedSeconds: 300,
};
const original: FollowUpPlan = { id: 'plan-a', applicationId: 'a', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2', recruitmentType: 'INTERNAL', clientId: null, clientName: null, ownerUserId: 'hr-a', startsAt: '2026-09-07T01:00:00Z', endsAt: '2026-09-07T01:30:00Z', channel: 'PHONE', note: '确认到岗意向', status: 'SCHEDULED', version: '1', createTime: '2026-09-06T00:00:00Z', updateTime: '2026-09-06T00:00:00Z' };

async function fixture(page: Page, options: { interviewCard?: boolean; loseRescheduleResponse?: boolean; loseCancelResponse?: boolean; loseCompleteResponse?: boolean; conflictComplete?: boolean } = {}) {
  await reviewWorkspace(page);
  const plans = [structuredClone(original)];
  const writes: Array<{ path: string; body: Record<string, unknown>; key:string|null }> = [];
  const applicationDetails=structuredClone(details);
  const completion={effects:0};
  const completeRequests=new Map<string,string>();
  if(options.loseCompleteResponse||options.conflictComplete)applicationDetails.workflowSteps=[{id:'communication-step',applicationId:'a',templateVersionId:'workflow-v1',stepDefinitionId:'definition',stepCode:'CANDIDATE_COMMUNICATION',sequenceNo:1,candidateOptional:true,defaultFollowUpChannel:'PHONE',status:'IN_PROGRESS',ownerUserId:'hr-a',dueAt:null,startedAt:'2026-09-07T01:00:00Z',completedAt:null,resultReason:null,clientSubmissionId:null,version:1}];
  const scheduledInterview = { id: 'interview-a', applicationId: 'a', candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2', workflowStepId: 'step-a', stepCode: 'INTERVIEW_ROUND_1', roundNo: 1, interviewType: 'INTERNAL', status: 'COMPLETED', scheduledStartAt: original.startsAt, scheduledEndAt: original.endsAt, timezoneId: 'Asia/Shanghai', meetingMode: 'ONLINE', ownerUserId: 'hr-a', nextResponsibleUserId: 'hr-a', version: 1 };
  const interviewFilters: string[] = [];
  let failCancel = !options.loseCancelResponse;
  let loseCancelResponse = options.loseCancelResponse;
  let loseCompleteResponse = options.loseCompleteResponse;
  let conflictComplete = options.conflictComplete;
  let loseRescheduleResponse = options.loseRescheduleResponse;
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/applications') return route.fulfill({ json: { ...empty, items: [details.application] } });
    if (path === '/applications/a/communications' && request.method() === 'POST') {
      writes.push({ path, body: request.postDataJSON() as Record<string, unknown>, key: request.headers()['idempotency-key'] ?? null });
      return route.fulfill({ json: { id: 'communication-new' } });
    }
    if (path === '/interview-feedback-batch') return route.fulfill({ json: { ...empty, visibleInterviewIds: ['interview-a'] } });
    if (path === '/interviews/interview-a') return route.fulfill({ json: { interview: scheduledInterview, participants: [] } });
    if (path === '/interviews/interview-a/materials') return route.fulfill({ json: { interviewId: 'interview-a', candidateId: 'person-a', candidateName: '林雨桐', jdId: 'jd-a', jdVersionId: 'jd-v2', jdTitle: '高级前端工程师', resumeVersionId: 'resume-v1' } });
    if (path === '/applications/a') return route.fulfill({ json: applicationDetails });
    if (['/applications/a/communications', '/applications/a/intentions'].includes(path)) return route.fulfill({ json: [] });
    if (path === '/applications/a/follow-up-plan-history') return route.fulfill({ json: empty });
    if (path === '/candidates/person-a/resume-versions') return route.fulfill({ json: { ...empty, items: [{ id: 'resume-v1', versionNo: 1 }] } });
    if (path === '/follow-up-plans') return route.fulfill({ json: { ...empty, items: url.searchParams.has('applicationId')?plans:plans.filter(item => item.status === 'SCHEDULED') } });
    if (path === '/interviews') {
      interviewFilters.push(url.searchParams.get('interviewType') ?? '');
      return route.fulfill({ json: { ...empty, items: options.interviewCard ? [scheduledInterview] : [] } });
    }
    if (path.startsWith('/applications/a/follow-up-plans') && request.method() !== 'GET') {
      const body = request.postDataJSON() as Record<string, unknown>;
      const key=request.headers()['idempotency-key']??null;
      writes.push({ path, body, key });
      if(path.endsWith('/complete')){
        if(!key)return route.fulfill({status:400,json:{status:400,detail:'缺少幂等键'}});
        if(conflictComplete){
          conflictComplete=false;applicationDetails.workflowSteps[0]!.version=2;plans[0]!.version='2';
          return route.fulfill({status:409,contentType:'application/problem+json',json:{title:'步骤已变化',status:409,code:'FOLLOW_UP_STEP_CHANGED',detail:'当前沟通步骤已变化，请刷新后重新确认',recoverable:true}});
        }
        const previous=completeRequests.get(key);
        if(previous)return previous===JSON.stringify(body)?route.fulfill({json:plans[0]}):route.fulfill({status:409,json:{status:409,code:'IDEMPOTENCY_KEY_REUSED',detail:'同键请求内容发生变化'}});
      }
      if (path.endsWith('/cancel') && failCancel) {
        failCancel = false;
        return route.fulfill({ status: 503, contentType: 'application/problem+json', json: { title: '暂未保存', detail: '预约服务暂不可用，请重试', status: 503, code: 'SERVICE_UNAVAILABLE', recoverable: true } });
      }
      if (path.endsWith('/cancel') || path.endsWith('/complete')) {
        if(body.version!==plans[0]!.version||plans[0]!.status!=='SCHEDULED')return route.fulfill({status:409,json:{status:409,code:'FOLLOW_UP_PLAN_CHANGED',detail:'预约已更新，请核对最新内容'}});
        plans[0]!.status = path.endsWith('/cancel') ? 'CANCELLED' : 'COMPLETED';
        plans[0]!.resultReason=String(body.reason??'').trim()||(path.endsWith('/cancel')?'HR 取消沟通预约':'未填写沟通备注');
        plans[0]!.version=String(Number(plans[0]!.version)+1);
        if(path.endsWith('/complete')){
          completeRequests.set(key!,JSON.stringify(body));completion.effects++;
          if(applicationDetails.workflowSteps[0])Object.assign(applicationDetails.workflowSteps[0],{status:'PASSED',version:2});
          if(loseCompleteResponse){loseCompleteResponse=false;return route.fulfill({status:200,contentType:'application/json',body:'{"id":'});}
        }
        if(path.endsWith('/cancel')&&loseCancelResponse){loseCancelResponse=false;return route.fulfill({status:200,contentType:'application/json',body:'{"id":'});}
        return route.fulfill({ json: plans[0] });
      }
      if (request.method() === 'PATCH') {
        if(body.version!==plans[0]!.version)return route.fulfill({status:409,json:{status:409,code:'FOLLOW_UP_PLAN_CHANGED',detail:'预约已更新，请核对最新内容'}});
        Object.assign(plans[0]!, body, {version:String(Number(plans[0]!.version)+1)});
        if(loseRescheduleResponse){loseRescheduleResponse=false;return route.fulfill({status:200,contentType:'application/json',body:'{"id":'});}
        return route.fulfill({ json: plans[0] });
      }
      const plan = { ...original, ...body, id: 'plan-new', status: 'SCHEDULED' as const };
      plans.push(plan);
      return route.fulfill({ json: plan });
    }
    return route.fallback();
  });
  return { plans, writes, interviewFilters, completion };
}

test('完成沟通响应丢失后重试原请求，沟通与步骤只写一次',async({page})=>{
  const state=await fixture(page,{loseCompleteResponse:true});
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
  await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:'记录沟通结果'}).click();
  const dialog=page.getByRole('dialog',{name:'确认沟通已完成'});
  await dialog.getByLabel('本次沟通结果').click();
  await page.locator('.ant-select-item-option-content').getByText('沟通完成，继续推进',{exact:true}).click();
  await dialog.getByLabel('实际沟通情况（选填）',{exact:true}).fill('已核对实际沟通事实');
  await dialog.getByLabel('下一步计划（选填）',{exact:true}).fill('安排面试');
  await dialog.getByRole('button',{name:'记录完成',exact:true}).click();
  const retry=dialog.getByRole('button',{name:'重试记录完成',exact:true});
  await expect(retry).toBeEnabled();
  await expect(dialog.locator('.ant-modal-footer').getByRole('button',{name:/记录完成$/})).toBeDisabled();
  await expect(dialog.getByLabel('实际沟通情况（选填）',{exact:true})).toBeDisabled();
  expect(state.completion.effects).toBe(1);
  await retry.focus();await page.keyboard.press('Enter');
  await expect(dialog).not.toBeVisible();
  expect(state.writes).toHaveLength(2);
  expect(state.writes[0]?.key).toBeTruthy();
  expect(state.writes[1]).toEqual(state.writes[0]);
  expect(state.writes[1]?.body).toMatchObject({version:'1',workflowStepId:'communication-step',workflowStepVersion:'1',result:'CONTINUE',nextPlan:'安排面试'});
  expect(state.completion.effects).toBe(1);
});

test('完成沟通明确冲突后保留草稿，核对新版本再提交',async({page},testInfo)=>{
  const state=await fixture(page,{conflictComplete:true});
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
  await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:'记录沟通结果'}).click();
  const dialog=page.getByRole('dialog',{name:'确认沟通已完成'});
  await dialog.getByLabel('本次沟通结果').click();
  await page.locator('.ant-select-item-option-content').getByText('沟通完成，继续推进',{exact:true}).click();
  await dialog.getByLabel('实际沟通情况（选填）',{exact:true}).fill('保留沟通草稿');
  await dialog.getByRole('button',{name:'记录完成',exact:true}).click();
  const check=page.getByRole('button',{name:'重新核对当前步骤',exact:true});
  await expect(check).toBeEnabled();
  for(const close of await page.locator('.ant-notification-notice-close').all())await close.click();
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await expect.poll(()=>dialog.evaluate(element=>element.scrollWidth-element.clientWidth)).toBeLessThanOrEqual(1);
  await dialog.screenshot({path:`../.planning/2026-09-11-continuous-optimization/evidence/complete-plan-recovery-${testInfo.project.name}.png`});
  expect(state.completion.effects).toBe(0);
  await check.focus();await page.keyboard.press('Enter');
  await expect(dialog.getByRole('button',{name:'记录完成',exact:true})).toBeEnabled();
  await expect(dialog.getByLabel('实际沟通情况（选填）',{exact:true})).toHaveValue('保留沟通草稿');
  await dialog.getByRole('button',{name:'记录完成',exact:true}).focus();await page.keyboard.press('Enter');
  await expect(dialog).not.toBeVisible();
  expect(state.writes).toHaveLength(2);
  expect(state.writes[1]?.key).toBeTruthy();expect(state.writes[1]?.key).not.toBe(state.writes[0]?.key);
  expect(state.writes[1]?.body).toMatchObject({version:'2',workflowStepVersion:'2',reason:'保留沟通草稿'});
  expect(state.completion.effects).toBe(1);
});

test('取消响应丢失后核对已取消事实，不重复取消',async({page})=>{
  const state=await fixture(page,{loseCancelResponse:true});
  await page.goto('/schedule');
  const calendar=page.getByRole('region',{name:'所选周面试日历'});
  await calendar.getByRole('button',{name:/沟通预约/}).click();
  await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:'取消预约'}).click();
  const dialog=page.getByRole('dialog',{name:'取消沟通预约'});
  await dialog.getByLabel('取消原因').fill('双方重新约定联系时间');
  await dialog.getByRole('button',{name:'确认取消'}).click();
  const recover=dialog.getByRole('button',{name:'核对最新预约'});
  await expect(recover).toBeEnabled();
  await expect(dialog.getByRole('button',{name:'确认取消'})).toBeDisabled();
  await expect(dialog.getByLabel('取消原因')).toHaveValue('双方重新约定联系时间');
  await recover.focus();await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(calendar.getByRole('button',{name:/沟通预约/})).toHaveCount(0);
  expect(state.writes).toHaveLength(1);
  expect(state.plans[0]).toMatchObject({status:'CANCELLED',version:'2',resultReason:'双方重新约定联系时间'});
});

for(const status of ['SCHEDULED','CANCELLED','COMPLETED'] as const){
  test(`取消恢复核对${status}的新状态后再决定操作`,async({page},testInfo)=>{
    const state=await fixture(page);
    await page.goto('/schedule');
    await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
    await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:'取消预约'}).click();
    const dialog=page.getByRole('dialog',{name:'取消沟通预约'});
    await dialog.getByLabel('取消原因').fill('保留我的取消说明');
    await dialog.getByRole('button',{name:'确认取消'}).click();
    await expect(page.getByText('预约服务暂不可用，请重试',{exact:true})).toBeVisible();
    Object.assign(state.plans[0]!,{status,version:'3',note:'同事已更新此预约',resultReason:status==='SCHEDULED'?null:'同事已处理的原因'});
    const recover=dialog.getByRole('button',{name:'核对最新预约'});
    await expect(recover).toBeEnabled();await recover.focus();await page.keyboard.press('Enter');
    await expect(dialog).toContainText('同事已更新此预约');
    await expect(dialog.getByLabel('取消原因')).toHaveValue('保留我的取消说明');
    await expect(dialog.getByRole('button',{name:'确认取消'})).toBeDisabled();
    expect(state.writes).toHaveLength(1);
    if(status==='SCHEDULED'){
      const retry=dialog.getByRole('button',{name:'按最新版本重试'});
      await retry.focus();await page.keyboard.press('Enter');
      await expect(page.getByRole('dialog')).toHaveCount(0);
      expect(state.writes).toHaveLength(2);
      expect(state.writes[1]?.body).toMatchObject({version:'3',reason:'保留我的取消说明'});
    }else{
      await expect(dialog).toContainText('同事已处理的原因');
      await expect(dialog).toContainText('当前预约已结束，不能继续取消');
      await expect(dialog.getByRole('button',{name:'按最新版本重试'})).toHaveCount(0);
      if(status==='CANCELLED'){
        for(const close of await page.locator('.ant-notification-notice-close').all())await close.click();
        expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
        await expect.poll(()=>dialog.evaluate(element=>element.scrollWidth-element.clientWidth)).toBeLessThanOrEqual(1);
        await dialog.screenshot({path:`../.planning/2026-09-11-continuous-optimization/evidence/cancel-plan-recovery-${testInfo.project.name}.png`});
      }
    }
  });
}

test('改期保存响应丢失后核对已保存预约，不重复写入',async({page})=>{
  const state=await fixture(page,{loseRescheduleResponse:true});
  await page.clock.setFixedTime(new Date('2026-09-07T00:59:59Z'));
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
  await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:/^改\s*期$/}).click();
  const dialog=page.getByRole('dialog',{name:'调整沟通预约'});
  await dialog.getByLabel('沟通事项（选填）',{exact:true}).fill('改为核对到岗时间');
  await dialog.getByRole('button',{name:'保存到日程'}).click();
  await expect.poll(()=>state.writes.length).toBe(1);
  const recover=dialog.getByRole('button',{name:'核对最新预约'});
  await expect(recover).toBeVisible();
  await expect(dialog.getByRole('button',{name:'保存到日程'})).toBeDisabled();
  await expect(dialog.getByLabel('沟通事项（选填）',{exact:true})).toHaveValue('改为核对到岗时间');
  // 已提交的开始时间已经过去，也必须能核对保存事实。
  await page.clock.setFixedTime(new Date('2026-09-07T01:01:00Z'));
  await recover.focus();await page.keyboard.press('Enter');
  await expect(dialog).not.toBeVisible();
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
  await expect(page.getByRole('dialog',{name:'沟通预约详情',exact:true})).toContainText('改为核对到岗时间');
  expect(state.writes).toHaveLength(1);
  expect(state.plans[0]?.version).toBe('2');
});

for(const status of ['SCHEDULED','CANCELLED'] as const){
  test(`改期恢复遇到${status}的新版本时保留草稿并核对后操作`,async({page},testInfo)=>{
    const state=await fixture(page,{loseRescheduleResponse:true});
    await page.clock.setFixedTime(new Date('2026-09-07T00:59:59Z'));
    await page.goto('/schedule');
    await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/沟通预约/}).click();
    await page.getByRole('dialog',{name:'沟通预约详情',exact:true}).getByRole('button',{name:/^改\s*期$/}).click();
    const dialog=page.getByRole('dialog',{name:'调整沟通预约'});
    await dialog.getByLabel('沟通事项（选填）',{exact:true}).fill('保留我的改期说明');
    await dialog.getByRole('button',{name:'保存到日程'}).click();
    const recover=dialog.getByRole('button',{name:'核对最新预约'});
    await expect(recover).toBeVisible();
    Object.assign(state.plans[0]!,{status,note:'另一位同事已更新预约',version:'3'});
    await recover.focus();await page.keyboard.press('Enter');
    await expect(dialog).toContainText('另一位同事已更新预约');
    await expect(dialog.getByLabel('沟通事项（选填）',{exact:true})).toHaveValue('保留我的改期说明');
    await expect(dialog.getByRole('button',{name:'保存到日程'})).toBeDisabled();
    expect(state.writes).toHaveLength(1);
    if(status==='SCHEDULED'){
      const retry=dialog.getByRole('button',{name:'按最新版本重试'});
      await retry.focus();await page.keyboard.press('Enter');
      await expect(dialog).not.toBeVisible();
      expect(state.writes).toHaveLength(2);
      expect(state.writes[1]?.body).toMatchObject({version:'3',note:'保留我的改期说明'});
    }else{
      await expect(dialog).toContainText('当前预约已结束，不能继续改期');
      await expect(dialog.getByRole('button',{name:'按最新版本重试'})).toHaveCount(0);
      await expect.poll(()=>dialog.evaluate(element=>element.scrollWidth-element.clientWidth)).toBeLessThanOrEqual(1);
      for(const close of await page.locator('.ant-notification-notice-close').all())await close.click();
      expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
      await dialog.screenshot({path:`../.planning/2026-09-11-continuous-optimization/evidence/reschedule-recovery-${testInfo.project.name}.png`});
    }
  });
}

test('日历预约就地查看、取消失败重试，关闭后返回原日历焦点', async ({ page }, testInfo) => {
  const state = await fixture(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.clock.setFixedTime(new Date('2026-09-07T00:59:59Z'));
  await page.goto('/schedule');
  const event = page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /沟通预约/ });
  await event.click();
  const detail = page.getByRole('dialog', { name: '沟通预约详情', exact: true });
  await expect(detail).toContainText('确认到岗意向');
  await expect(detail.getByRole('button', { name: '记录沟通结果' })).toBeDisabled();
  await page.clock.setFixedTime(new Date('2026-09-07T01:30:00Z'));
  await expect(detail.getByRole('button', { name: '记录沟通结果' })).toBeEnabled();
  await expect(page).toHaveURL(/\/schedule$/);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await expect.poll(() => detail.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
  }
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await page.setViewportSize({ width: testInfo.project.name === 'mobile' ? 390 : 1440, height: 900 });
  await page.screenshot({ path: `../.planning/2026-09-11-continuous-optimization/evidence/appointment-${testInfo.project.name}.png`, fullPage: true });
  await page.keyboard.press('Escape');
  await expect(detail).not.toBeVisible();
  await expect(event).toBeFocused();
  await event.click();
  await detail.getByRole('button', { name: '取消预约' }).click();
  const cancel = page.getByRole('dialog', { name: '取消沟通预约' });
  await cancel.getByLabel('取消原因').fill('重新约定时间');
  await cancel.getByRole('button', { name: '确认取消' }).click();
  await expect(page.getByText('预约服务暂不可用，请重试',{exact:true})).toBeVisible();
  await expect(cancel.getByLabel('取消原因')).toHaveValue('重新约定时间');
  await expect(cancel.getByRole('button',{name:'确认取消'})).toBeDisabled();
  await cancel.getByRole('button',{name:'核对最新预约'}).focus();
  await page.keyboard.press('Enter');
  await cancel.getByRole('button', { name: '按最新版本重试' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(event).toHaveCount(0);
  expect(state.writes).toHaveLength(2);
  expect(state.writes[1]?.body).toMatchObject({ version: '1', reason: '重新约定时间' });
  expect(errors).toEqual([]);
});

test('日程移除直接预约入口，保留日历记录结果与甲方面试筛选', async ({ page }) => {
  const state = await fixture(page);
  await page.goto('/schedule');
  await page.getByRole('combobox', { name: '面试类型', exact: true }).click();
  await expect(page.locator('.ant-select-item-option-content').getByText('甲方 AI 面试', { exact: true })).toHaveCount(0);
  await page.locator('.ant-select-item-option-content').getByText('外部面试', { exact: true }).click();
  await expect.poll(() => state.interviewFilters.includes('EXTERNAL')).toBe(true);
  await page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /沟通预约/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: '记录沟通结果' }).click();
  const complete = page.getByRole('dialog', { name: '确认沟通已完成' });
  await complete.getByLabel('本次沟通结果').click();
  await page.locator('.ant-select-item-option-content').getByText('还需跟进', { exact: true }).click();
  await complete.getByRole('button', { name: '记录完成' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(state.writes.at(-1)?.body).toMatchObject({ result: 'FOLLOW_UP', version: '1' });
  await page.reload();
  await expect(page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /沟通预约/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '预约沟通', exact: true })).toHaveCount(0);
});

test('日程不再重复展示独立沟通预约列表', async ({ page }) => {
  await fixture(page);
  await page.goto('/schedule');
  await expect(page.getByRole('button', { name: '继续跟进', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: '所选周面试日历' }).getByRole('button', { name: /沟通预约/ })).toBeVisible();
  await expect(page).toHaveURL(/\/schedule$/);
});


for (const interviewCard of [false, true]) {
  test(`${interviewCard ? '已完成面试' : '沟通预约'}卡共用招聘选择区，就地记录后返回原详情`, async ({ page }, testInfo) => {
    const state = await fixture(page, { interviewCard });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('/schedule');
    const calendar = page.getByRole('region', { name: '所选周面试日历' });
    await calendar.getByRole('button', { name: interviewCard ? /已完成/ : /沟通预约/ }).click();
    const title = interviewCard ? '面试事实与处理' : '沟通预约详情';
    const dialog = page.getByRole('dialog', { name: title, exact: true });
    for (const name of ['待定', '面试', '放弃', '入职', '黑名单', '第 6 轮面试']) {
      await expect(dialog.getByRole('button', { name, exact: true })).toBeVisible();
    }
    for (const width of [390, 768, 1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      await expect.poll(() => dialog.evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }
    await dialog.screenshot({ path: testInfo.outputPath(`schedule-actions-${interviewCard}.png`) });
    expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    await dialog.getByRole('button', { name: '待定', exact: true }).focus();
    await page.keyboard.press('Enter');
    const pending = page.getByRole('dialog', { name: '待定', exact: true });
    await pending.getByLabel('沟通情况', { exact: true }).fill('日程联系后继续跟进');
    await pending.getByRole('button', { name: '保存记录', exact: true }).click();
    await expect(dialog).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(1);
    await expect(dialog.getByRole('button', { name: '待定', exact: true })).toBeFocused();
    expect(state.writes).toHaveLength(1);
    expect(state.writes[0]).toMatchObject({ path: '/applications/a/communications', body: { summary: '日程联系后继续跟进' } });
    await expect(page).toHaveURL(/\/schedule$/);
    await dialog.getByRole('button', { name: '面试', exact: true }).click();
    const interview = page.getByRole('dialog', { name: '面试', exact: true });
    await expect(interview.getByLabel('开始时间', { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: '第 6 轮面试', exact: true }).click();
    if (interviewCard) await expect(interview.getByText('第 6 轮', { exact: true })).toBeVisible();
    else await expect(interview.getByText('请先安排第 1 轮面试，再选择后续轮次。')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    expect(state.writes).toHaveLength(1);
    await expect(page).toHaveURL(/\/schedule$/);
    expect(errors).toEqual([]);
  });
}

test.describe('原生滚动条下的弹窗布局', () => {
  test('日程详情一键复制面试信息供HR手动发送',async({page,context},testInfo)=>{
    await fixture(page,{interviewCard:true});
    await context.grantPermissions(['clipboard-read','clipboard-write']);
    await page.route('**/api/v1/interviews/interview-a',route=>route.fulfill({json:{interview:{id:'interview-a',applicationId:'a',candidateId:'person-a',stepCode:'INTERVIEW_ROUND_1',roundNo:1,interviewType:'INTERNAL',status:'SCHEDULED',scheduledStartAt:original.startsAt,scheduledEndAt:original.endsAt,meetingMode:'ONLINE'},participants:[],externalCalendar:{mode:'WECOM_MEETING',status:'SYNCED',meetingCode:'123456789',meetingLink:'https://meeting.example.test/join'}}}));
    await page.goto('/schedule');
    await page.locator('.interview-event.completed').click();
    const dialog=page.getByRole('dialog',{name:'面试事实与处理',exact:true});
    const summaryColumns=dialog.locator('.interview-summary-column');
    await expect(summaryColumns).toHaveCount(2);
    await expect(dialog.getByText('会议：',{exact:true})).toBeVisible();
    await expect(dialog.getByText('企业微信联动',{exact:true})).toHaveCount(0);
    const columnBoxes=await summaryColumns.evaluateAll(elements=>elements.map(element=>element.getBoundingClientRect()).map(({x,y})=>({x,y})));
    if(testInfo.project.name==='desktop'){
      expect(Math.abs(columnBoxes[0]!.y-columnBoxes[1]!.y)).toBeLessThanOrEqual(1);
      expect(columnBoxes[1]!.x).toBeGreaterThan(columnBoxes[0]!.x);
      const conferenceRow=dialog.locator('.interview-conference-row');
      const rowBox=await conferenceRow.boundingBox();
      const contentBox=await conferenceRow.locator('.interview-conference-content').boundingBox();
      expect(Math.abs((rowBox!.x+rowBox!.width/2)-(contentBox!.x+contentBox!.width/2))).toBeLessThanOrEqual(1);
    }else{
      expect(columnBoxes[1]!.y).toBeGreaterThan(columnBoxes[0]!.y);
    }
    await dialog.locator('.ant-modal-body').evaluate(element=>{element.scrollTop=0;});
    await dialog.screenshot({path:testInfo.outputPath('interview-facts-layout.png')});
    const copy=dialog.getByRole('button',{name:/复制面试信息/});
    await copy.click();
    const text=await page.evaluate(()=>navigator.clipboard.readText());
    for(const part of ['林雨桐','高级前端工程师','2026/09/07 09:00','2026/09/07 09:30','北京时间','123456789','https://meeting.example.test/join'])expect(text).toContain(part);
    await expect(page.getByText('面试信息已复制，请手动发送给面试人')).toBeVisible();
    await dialog.screenshot({path:testInfo.outputPath('copy-interview-info.png')});
  });
  test('打开关闭面试卡片保持日历和侧栏位置', async ({ page }) => {
    await fixture(page, { interviewCard: true });
    await page.setViewportSize({ width: 1440, height: 700 });
    await page.goto('/schedule');
    const card = page.locator('.interview-event.completed');
    await card.scrollIntoViewIfNeeded();
    const geometry = () => page.locator('.interview-calendar, .schedule-rail').evaluateAll(nodes => nodes.map(node => {
      const { x, y, width, height } = node.getBoundingClientRect();
      return { x, y, width, height };
    }));
    const before = await geometry();
    await card.click();
    const dialog = page.getByRole('dialog', { name: '面试事实与处理', exact: true });
    await expect(dialog).toBeVisible();
    await expect.poll(geometry).toEqual(before);
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).overflowY)).toBe('hidden');
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect.poll(geometry).toEqual(before);
  });
});

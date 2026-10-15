import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
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
  return { id, applicationId, candidateId, jdId: 'jd-a', jdVersionId: 'jd-v2', workflowTemplateVersionId: 'workflow-v1', workflowStepId: step.id, stepCode: step.stepCode, interviewType: 'INTERNAL', initialComment: '', roundNo: step.roundNo, clientSubmissionId: null, status, scheduledStartAt: '2026-09-09T03:00:00Z', scheduledEndAt: '2026-09-09T03:30:00Z', timezoneId: 'Asia/Shanghai', meetingMode: 'ONLINE', locationText: null, meetingUrl: null, candidateNotificationChannel: 'NONE', candidateConfirmationStatus: 'PENDING', ownerUserId: actorId, ownerDepartmentId: 'dept-a', nextResponsibleUserId: actorId, version: 1, createdByUserId: actorId, createdAt: now, updatedByUserId: actorId, updatedAt: now };
}

async function fixture(page: Page) {
  await reviewWorkspace(page);
  let steps: Step[] = [{id:'step-1',stepCode:'INTERVIEW_ROUND_1',roundNo:1,status:'IN_PROGRESS',version:1}];
  const interviews = [interview('interview-1',steps[0]!)];
  const feedback = new Map<string, {feedback:Record<string,unknown>;currentVersion:Record<string,unknown>}>();
  const state = {reschedules:[] as Record<string,unknown>[],creates:[] as Record<string,unknown>[],nextActions:[] as Record<string,unknown>[],feedbackWrites:[] as Record<string,unknown>[],interviews};
  await page.route('**/api/v1/**',async route=>{
    const request=route.request();
    const url=new URL(request.url());
    const path=url.pathname.replace('/api/v1','');
    if(path==='/applications')return route.fulfill({json:{...empty,items:[application(steps).application]}});
    if(path===`/applications/${applicationId}`)return route.fulfill({json:application(steps)});
    if(path==='/wecom/collaboration/members')return route.fulfill({json:[]});
    if(path===`/applications/${applicationId}/next-actions`){
      const body=request.postDataJSON() as {roundNo:number};state.nextActions.push(body);
      steps=[...steps,{id:`step-${body.roundNo}`,stepCode:'INTERVIEW_ROUND_1',roundNo:body.roundNo,status:'IN_PROGRESS',version:1}];
      return route.fulfill({json:application(steps)});
    }
    if(path==='/interviews'&&request.method()==='GET')return route.fulfill({json:{...empty,items:interviews}});
    if(path==='/interviews'&&request.method()==='POST'){
      const body=request.postDataJSON() as {workflowStepId:string;interviewType:string;initialComment:string;startAt:string;endAt:string};state.creates.push(body);
      const step=steps.find(item=>item.id===body.workflowStepId)!;
      const value={...interview(`interview-${step.roundNo}`,step),interviewType:body.interviewType,initialComment:body.initialComment,scheduledStartAt:body.startAt,scheduledEndAt:body.endAt};
      interviews.push(value);return route.fulfill({json:{interview:value,participants:[]}});
    }
    const match=path.match(/^\/interviews\/(interview-\d+)(?:\/(.*))?$/);
    if(match){
      const value=interviews.find(item=>item.id===match[1])!;
      if(!match[2])return route.fulfill({json:{interview:value,participants:[]}});
      if(match[2]==='reschedule'){
        const body=request.postDataJSON();state.reschedules.push(body);
        Object.assign(value,{scheduledStartAt:body.startAt,scheduledEndAt:body.endAt,timezoneId:body.timezoneId,version:value.version+1});
        return route.fulfill({json:{interview:value,participants:[]}});
      }
      if(match[2]==='feedback'){
        if(request.method()==='GET')return route.fulfill({json:{...empty,items:feedback.has(value.id)?[feedback.get(value.id)]:[]}});
        const body=request.postDataJSON() as Record<string,unknown>;state.feedbackWrites.push(body);value.status='COMPLETED';
        const saved={feedback:{id:`feedback-${value.id}`,interviewId:value.id,authorUserId:actorId,status:'SUBMITTED'},currentVersion:{...body,createdAt:now}};
        feedback.set(value.id,saved);return route.fulfill({json:saved});
      }
      if(match[2]==='materials')return route.fulfill({json:{interviewId:value.id,candidateId,candidateName:'林雨桐',jdId:'jd-a',jdTitle:'高级前端工程师'}});
    }
    const complete=path.match(/workflow-steps\/(step-\d+)\/complete$/);
    if(complete){steps.find(item=>item.id===complete[1])!.status='PASSED';return route.fulfill({json:application(steps)});}
    return route.fallback();
  });
  return state;
}

test('已安排一面可跳三面并独立选择外部标签，排期后可提交空面评',async({page},testInfo)=>{
  const state=await fixture(page);
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.goto('/recruitment');
  const trigger=page.getByRole('button',{name:'第 3 轮面试',exact:true});
  await trigger.focus();await trigger.press('Enter');
  const dialog=page.getByRole('dialog',{name:'面试',exact:true});
  await expect(dialog.getByLabel('开始时间',{exact:true})).toBeVisible();
  await page.setViewportSize({width:1280,height:720});
  await expect.poll(()=>dialog.locator('.ant-modal-body').evaluate(element=>element.scrollHeight-element.clientHeight)).toBeLessThanOrEqual(1);
  await expect(dialog.getByText('陈佳',{exact:true})).toBeVisible();
  await expect(dialog.getByRole('radio',{name:'直接填写面评',exact:true})).toBeChecked();
  await dialog.getByText('企业微信会议',{exact:true}).click();
  const meetingSwitches=dialog.locator('.interview-meeting-switches > .ant-form-item');
  await expect(meetingSwitches).toHaveCount(3);
  const switchBoxes=await meetingSwitches.evaluateAll(elements=>elements.map(element=>element.getBoundingClientRect()).map(({x,y,width})=>({x,y,width})));
  expect(Math.max(...switchBoxes.map(box=>box.y))-Math.min(...switchBoxes.map(box=>box.y))).toBeLessThanOrEqual(1);
  expect(Math.max(...switchBoxes.map(box=>box.width))-Math.min(...switchBoxes.map(box=>box.width))).toBeLessThanOrEqual(1);
  expect(switchBoxes[1]!.x-switchBoxes[0]!.x).toBeCloseTo(switchBoxes[2]!.x-switchBoxes[1]!.x,0);
  await dialog.getByText('直接填写面评',{exact:true}).click();
  await dialog.getByLabel('实际面试官',{exact:true}).hover();
  await page.screenshot({path:testInfo.outputPath('create-interview-compact.png'),fullPage:true,animations:'disabled'});
  await dialog.getByRole('radio',{name:'内部面试',exact:true}).focus();
  await page.keyboard.press('ArrowRight');
  await expect(dialog.getByRole('radio',{name:'外部面试',exact:true})).toBeChecked();
  await expect(dialog.getByLabel('实际面试官',{exact:true})).toHaveCount(0);
  for(const width of [390,1280,1440,1920]){
    await page.setViewportSize({width,height:900});
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    const bounds=await dialog.boundingBox();expect(bounds!.x).toBeGreaterThanOrEqual(0);expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(width+1);
    await page.screenshot({path:testInfo.outputPath(`interview-${width}.png`),fullPage:true});
  }
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await dialog.getByRole('button',{name:/^保\s*存$/}).click();
  await expect(dialog.getByRole('button',{name:'正式提交面评',exact:true})).toBeVisible();
  await expect(dialog.getByRole('button',{name:/保存排期|^保\s*存$/})).toHaveCount(0);
  for(const label of ['开始时间','结束时间','时区'])await expect(dialog.getByLabel(label,{exact:true})).toHaveAttribute('readonly','');
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  for(const width of [390,1280,1440,1920]){
    await page.setViewportSize({width,height:width===1280?720:900});
    const body=dialog.locator('.ant-modal-body');
    if(width>=1280)await expect.poll(()=>body.evaluate(element=>element.scrollHeight-element.clientHeight)).toBeLessThanOrEqual(1);
    await dialog.getByRole('button',{name:'正式提交面评',exact:true}).scrollIntoViewIfNeeded();
    const actions=dialog.locator('.ledger-interview-actions');
    const submitBox=await actions.getByRole('button',{name:'正式提交面评',exact:true}).boundingBox();
    const cancelBox=await actions.getByRole('button',{name:'取消本轮面试',exact:true}).boundingBox();
    await expect(actions.getByRole('button',{name:'改期',exact:true})).toHaveCount(0);
    if(width>=1280){
      expect(submitBox!.x).toBeGreaterThan(cancelBox!.x+cancelBox!.width+32);
      expect(Math.abs(submitBox!.y-cancelBox!.y)).toBeLessThanOrEqual(1);
    }
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`saved-interview-feedback-${width}.png`),fullPage:true});
  }
  expect(state.nextActions).toEqual([expect.objectContaining({stepCode:'INTERVIEW_ROUND_1',roundNo:3})]);
  expect(state.creates[0]).toMatchObject({interviewType:'EXTERNAL',participantUserIds:[]});
  expect(state.creates[0]).not.toHaveProperty('initialComment');
  expect(state.interviews[0]!.status).toBe('SCHEDULED');
  await page.reload();await trigger.click();
  await expect(dialog.getByLabel('面评',{exact:true})).toHaveValue('');
  await expect(dialog.getByLabel('面试轮次',{exact:true})).toHaveValue('外部面试 · 第 3 轮');
  await dialog.getByRole('button',{name:'正式提交面评',exact:true}).click();
  await expect.poll(()=>state.feedbackWrites.length).toBe(1);
  expect(state.feedbackWrites[0]).toMatchObject({status:'SUBMITTED',shortComment:''});
  expect(state.interviews[0]!.status).toBe('SCHEDULED');
  await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  expect(errors).toEqual([]);
});

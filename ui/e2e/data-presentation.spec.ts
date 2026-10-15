import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdir } from 'node:fs/promises';

const empty={items:[],hasMore:false,nextCursor:null};
const candidateId='01M1KGD2APP6WBBJ59JDKR7E70';
const ownerId='01M1KEGRX6GEG4Q50SVNPP85VA';
const interviewId='01M1INTERVIEW00000000000000';
const applicationId='01M1APPLICATION000000000000';
const resumeId='01M1RESUMEVERSION0000000000';
const actor={userId:ownerId,displayName:'陈佳',workEmail:'hr@example.com',roles:['HR'],permissions:['talent.read','talent.manage','interview.feedback.read-authorized','interview.schedule','interview.external-feedback.record','demand.read','recruitment.manage'],superAdmin:false,mustChangePassword:false,version:'1'};
const person={id:candidateId,fullName:'林雨桐',primaryEmail:'lin@example.com',primaryPhone:'13800001234',ownerUserId:ownerId,createTime:'2026-08-01T03:00:00Z',updateTime:'2026-09-05T04:00:00Z'};
const resume={id:resumeId,resumeId:'resume',intakeId:'intake',versionNo:2,originalFileName:'林雨桐-前端开发.pdf',createTime:'2026-09-05T04:00:00Z'};
const job={id:'job',demandId:'demand',title:'高级前端工程师',demandCode:'FE-026',currentVersionNo:2};
const interview={id:interviewId,applicationId,candidateId,candidateName:person.fullName,jdId:'job',jdVersionId:'job-v2',jdTitle:job.title,workflowStepId:'01M1WORKFLOWSTEP00000000000',stepCode:'INTERVIEW_ROUND_1',interviewType:'EXTERNAL_CLIENT',status:'COMPLETED',scheduledStartAt:'2026-09-05T06:00:00Z',scheduledEndAt:'2026-09-05T07:00:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',meetingUrl:'https://meeting.example.com/room',locationText:null,nextResponsibleUserId:ownerId,candidateConfirmationStatus:'CONFIRMED'};
const facts={snapshot:{id:'fact-v2',fullName:'林雨桐',currentRole:'前端工程师',jobIntention:'高级前端工程师',city:'上海',sourceType:'AI_PARSED',snapshotVersion:2},workExperiences:[{id:'work-1',employer:'云杉科技',roleName:'前端工程师',startDate:'2020-01-01',endDate:'2025-01-01',descriptionText:'负责招聘平台及复杂表单开发',responsibilitiesJson:'[]',achievementsJson:'[]'}],projectExperiences:[],educations:[{degreeName:'BACHELOR',school:'示例大学',major:'计算机科学'},{degreeName:'硕士研究生',school:'示例研究院',major:'人工智能'}],skills:['React','TypeScript','CSS','Vite','复杂业务表单与权限系统开发','无障碍交互','接口契约联调','系统性能优化','自动化回归测试'].map((skillName,index)=>({id:`skill-${index}`,skillName})),simpleFacts:[]};

async function workspace(page:Page) {
  await page.clock.setFixedTime(new Date('2026-09-05T12:00:00+08:00'));
  const requests:string[]=[];
  await page.route('**/api/v1/**',route=>{
    const url=new URL(route.request().url());const path=url.pathname.replace('/api/v1','');requests.push(path);
    let body:unknown=empty;
    if(path==='/me')body=actor;
    if(path==='/candidate-pool-statuses')body=[{candidateId,resumeVersionId:resumeId,libraryEligible:false,screenedOut:false,canScreenOut:false,stages:[]}];
    if(path==='/candidate-resume-summaries')body=url.searchParams.getAll('candidateIds').includes(candidateId)?[{candidateId,resumeVersion:resume,facts,factsStatus:'READY',source:{intakeId:'historical-intake',sourceChannelName:'历史校园招聘',sourceRecruiterUserId:'historical-owner',receivedAt:'2026-07-01T03:00:00Z'}}]:[];
    if(path==='/applications/stage-counts')body=[];
    if(path==='/directory/users/resolve')body=url.searchParams.get('ids')==='unknown-user'?[]:[{id:'historical-owner',displayName:'历史负责人',status:'DISABLED'}];
    if(path==='/directory/users')body={...empty,items:[{id:ownerId,displayName:actor.displayName,status:'ACTIVE'}]};
    if(path==='/source-channels')body={...empty,items:[{id:'source',name:'招聘邮箱',channelType:'MAIL',status:'ACTIVE'}]};
    if(path==='/intakes')body={...empty,items:[{id:'intake',originalFileName:resume.originalFileName,sourceChannelId:'source',ownerUserId:ownerId,handledByUserId:ownerId,sizeBytes:102400,status:'CONFIRMED',importStatus:'IMPORTED',scanStatus:'CLEAN',createTime:'2026-09-05T04:00:00Z'}]};
    if(path==='/candidates')body=url.searchParams.get('cursor')?{...empty,items:[{...person,id:'no-resume',fullName:'周可',ownerUserId:'unknown-user'}]}:{items:[person],hasMore:true,nextCursor:'next-page'};
    if(path===`/candidates/${candidateId}`)body=person;
    if(path===`/candidates/${candidateId}/resume-versions`)body={...empty,items:[resume]};
    if(path===`/candidates/${candidateId}/resume-versions/${resumeId}/source`)body={intakeId:'historical-intake',sourceChannelName:'历史校园招聘',sourceRecruiterUserId:'historical-owner',receivedAt:'2026-07-01T03:00:00Z'};
    if(path===`/candidates/${candidateId}/company-blacklist-records`)body={current:null,active:false,records:empty};
    if(path===`/resume-versions/${resumeId}/facts`)body=facts;
    if(path===`/resume-versions/${resumeId}/extracted-text`)body={pages:[],pageCount:0,coverageRatio:0};
    if(path==='/jds')body={...empty,items:[job]};
    if(path==='/jds/job/versions')body={...empty,items:[{id:'job-v2',jdId:'job',title:job.title,versionNo:2}]};
    if(path==='/interviews')body={...empty,items:url.searchParams.get('status')==='SCHEDULED'?[]:[interview]};
    if(path==='/applications')body={...empty,items:[{id:applicationId,candidateId,jdId:'job',currentStage:'ONBOARDED'}]};
    if(path===`/applications/${applicationId}`)body={application:{id:applicationId,ownerUserId:ownerId,terminalAt:'2026-09-05T09:00:00Z'},collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:0};
    if(path==='/my/interview-tasks')body=[{...interview,interviewId,feedbackDueAt:'2026-09-04T07:00:00Z',submitted:true,overdue:true}];
    const interviewDetails={interview,participants:[{userId:ownerId}],feedback:[{feedback:{status:'SUBMITTED',authorUserId:ownerId},currentVersion:{verdict:'PASS',versionNo:1,shortComment:'组件设计与协作能力符合要求',externalFeedbackSource:'甲方邮件反馈',createTime:'2026-09-05T08:00:00Z',feedbackOccurredAt:'2026-09-05T07:30:00Z'}}],scheduleHistory:[{id:'history',actionType:'CREATE',reason:'与候选人确认时间',operatedByUserId:ownerId,occurredAt:'2026-09-04T03:00:00Z'}],notifications:[{id:'notice',recipientType:'CANDIDATE',channel:'IN_APP',status:'PENDING_MANUAL',renderedSubject:'面试邀请｜INTERVIEW_ROUND_1',renderedBody:`Application ${applicationId} 的面试安排在 ${interview.scheduledStartAt}（Asia/Shanghai），方式：ONLINE。`}]};
    if(path==='/interview-feedback-batch')body={...empty,visibleInterviewIds:[interviewId],items:interviewDetails.feedback.map(item=>({...item,feedback:{...item.feedback,id:'feedback',interviewId}}))};
    if(path===`/interviews/${interviewId}`)body={interview,participants:interviewDetails.participants};
    if(path===`/interviews/${interviewId}/materials`)body={interviewId,candidateId,jdId:'job',jdVersionId:'job-v2',resumeVersionId:resumeId,candidateName:person.fullName,jdTitle:job.title,resumeVersionNo:2,resumeFileName:resume.originalFileName,resumeMediaType:'application/pdf',resumeSizeBytes:1024,jdVersionNo:2,jdRawText:'负责招聘平台及复杂表单开发'};
    if(path===`/interviews/${interviewId}/feedback`)body={...empty,items:interviewDetails.feedback};
    if(path===`/interviews/${interviewId}/notifications`)body={...empty,items:interviewDetails.notifications};
    if(path===`/interviews/${interviewId}/schedule-history`)body={...empty,items:interviewDetails.scheduleHistory};
    return route.fulfill({json:body});
  });
  return requests;
}

async function capture(page:Page,name:string) {
  const directory='test-results/review/2026-09-05-data';
  await mkdir(directory,{recursive:true});
  await page.screenshot({path:`${directory}/${name}-${test.info().project.name}.png`,animations:'disabled'});
}

async function showCompletedInterviews(page:Page) {
  await page.getByRole('combobox',{name:'状态',exact:true}).click();
  await page.locator('.ant-select-item-option-content').getByText('已完成',{exact:true}).click();
  await expect(page.locator('.interview-event').first()).toBeVisible();
}

test('resume library uses parsed facts, real dates and cursor pages without exposing IDs',async({page})=>{
  const requests=await workspace(page);
  await page.goto('/talents?view=pool');
  const table=page.getByRole('table');
  await expect(table).toContainText('林雨桐');
  await expect(table).toContainText('硕士');
  await expect(table).not.toContainText('本科');
  await expect(table).toContainText('5 年');
  await expect(table).toContainText('React');
  const moreSkills=page.getByRole('button',{name:'查看全部 9 项技能'});
  await expect(page.getByText('自动化回归测试',{exact:true})).not.toBeVisible();
  await moreSkills.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('自动化回归测试',{exact:true})).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByText('自动化回归测试',{exact:true})).not.toBeVisible();
  await expect(table).toContainText('2026/09/05 12:00');
  await expect(table).toContainText('陈佳');
  await expect(table).toContainText('历史校园招聘');
  await expect(table).not.toContainText(candidateId);
  await expect(table).not.toContainText(ownerId);
  if(test.info().project.name==='mobile'){await expect(page.locator('.talent-mobile-summary')).toBeVisible();await expect(page.locator('.talent-mobile-summary')).toContainText('5 年 · 硕士');}
  await capture(page,'library');
  await page.getByRole('button',{name:'下一页'}).click();
  await expect(table).toContainText('周可');
  await expect(table).toContainText('暂无简历');
  await expect(table).toContainText('成员姓名暂不可用');
  await expect(table).not.toContainText('林雨桐');
  expect(requests.some(path=>path.startsWith('/admin/'))).toBe(false);
  await page.getByRole('button',{name:'上一页'}).click();
  await expect(table).toContainText('林雨桐');
});

test('interview shows people, position and Chinese feedback while preserving trace IDs',async({page})=>{
  const requests=await workspace(page);
  await page.route(`**/api/v1/interviews/${interviewId}/materials/resume`,route=>route.fulfill({status:200,contentType:'application/pdf',headers:{'Content-Disposition':'attachment; filename="interview-resume.pdf"'},body:'%PDF-1.4\n%%EOF'}));
  await page.goto('/schedule');
  await showCompletedInterviews(page);
  const event=page.locator('.interview-event');
  await expect(event).toContainText('林雨桐');
  await expect(event).toContainText('高级前端工程师');
  await expect(page.getByRole('region',{name:'待反馈'})).toContainText('0 项');
  await event.click();
  const dialog=page.getByRole('dialog',{name:'面试事实与处理'});
  await expect(dialog).toContainText('已确认');
  await expect(dialog).toContainText('面评已正式提交');
  await expect(dialog).toContainText('组件设计与协作能力符合要求');
  await expect(dialog).toContainText('陈佳');
  await expect(dialog).toContainText('2026/09/05 14:00');
  await expect(dialog.getByRole('button',{name:'代录甲方反馈'})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'记录已人工发送'})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'重试外部渠道'})).toHaveCount(0);
  await expect(dialog).toContainText('甲方面试已完成 · 记录只读');
  await expect(dialog).toContainText('线上面试');
  const materialButton=dialog.getByRole('button',{name:'查看本次面试材料'});
  await expect(materialButton).toBeVisible();
  await expect(dialog).not.toContainText('负责招聘平台及复杂表单开发');
  await materialButton.click();
  const materials=page.getByRole('dialog',{name:'本次面试材料'});
  const resumePane=materials.getByRole('region',{name:'本次简历，第 2 版'});
  const jdPane=materials.getByRole('region',{name:'岗位 JD，第 2 版'});
  await expect(resumePane).toBeVisible();
  await expect(jdPane).toContainText('负责招聘平台及复杂表单开发');
  await expect(materials.getByTitle('林雨桐-前端开发.pdf 在线预览')).toBeVisible();
  const viewport=page.viewportSize();
  const materialsBox=await materials.boundingBox();
  if(test.info().project.name==='desktop') {
    for(const width of [1280,1920,1440]) {
      await page.setViewportSize({width,height:900});
      expect((await materials.boundingBox())?.width).toBeGreaterThan(width*.9);
      expect((await materials.boundingBox())?.height).toBeGreaterThan(900*.88);
      expect((await resumePane.boundingBox())?.height).toBeGreaterThan(900*.7);
      expect((await jdPane.boundingBox())?.height).toBeGreaterThan(900*.7);
    }
  } else {
    expect(materialsBox?.width).toBeGreaterThan((viewport?.width??0)*.8);
    expect(materialsBox?.height).toBeGreaterThan((viewport?.height??0)*.85);
    expect((await resumePane.boundingBox())?.height).toBeGreaterThan((viewport?.height??0)*.45);
  }
  await capture(page,'interview-materials');
  await materials.press('Escape');
  await expect(dialog).toBeVisible();
  await expect(materialButton).toBeFocused();
  const visibleText=await dialog.innerText();
  expect(visibleText).not.toMatch(/01M1|ONLINE|INTERVIEW_ROUND_1|SUBMITTED|PASS|Asia\/Shanghai/);
  await capture(page,'interview');
  await dialog.locator('summary').click();
  await expect(dialog).toContainText(interview.workflowStepId);
  expect(requests.some(path=>path.startsWith('/admin/'))).toBe(false);
  await dialog.press('Escape');
  await expect(event).toBeFocused();
  await page.getByRole('button',{name:'自定义',exact:true}).click();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.getByRole('button',{name:'查看与处理'}).click();
  await expect(dialog).toContainText('高级前端工程师');
});

test('interview feedback loads every authorized page and schedule history recovers independently',async({page})=>{
  await workspace(page);
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  const feedbackRequests:string[]=[];
  const feedbackRow=(index:number)=>({feedback:{id:`feedback-${index}`,interviewId,status:'SUBMITTED',authorUserId:ownerId},currentVersion:{verdict:'PASS',versionNo:1,shortComment:`分页面评事实 ${index}`,feedbackOccurredAt:'2026-09-05T07:30:00Z'}});
  await page.route('**/api/v1/interview-feedback-batch?*',route=>{
    const url=new URL(route.request().url());feedbackRequests.push(url.search);
    return route.fulfill({json:url.searchParams.has('cursor')?{visibleInterviewIds:[interviewId],items:[feedbackRow(51)],nextCursor:null,hasMore:false}:{visibleInterviewIds:[interviewId],items:Array.from({length:50},(_,index)=>feedbackRow(index+1)),nextCursor:'feedback-next',hasMore:true}});
  });
  let historyRequests=0;
  await page.route(`**/api/v1/interviews/${interviewId}/schedule-history?*`,route=>{
    historyRequests++;
    return historyRequests===1?route.fulfill({status:503,json:{title:'暂不可用',status:503,detail:'排期历史暂时无法读取',code:'SERVICE_UNAVAILABLE',recoverable:true}}):route.fulfill({json:empty});
  });
  await page.goto('/schedule');
  await showCompletedInterviews(page);
  await page.locator('.interview-event').click();
  const dialog=page.getByRole('dialog',{name:'面试事实与处理'});
  await expect(dialog).toContainText('高级前端工程师');
  await expect(dialog.getByText('分页面评事实 50',{exact:true})).toBeVisible();
  await expect(dialog.getByText('分页面评事实 51',{exact:true})).toBeVisible();
  await expect(dialog.getByText('分页面评事实 1',{exact:true})).toBeAttached();
  expect(feedbackRequests.some(search=>new URLSearchParams(search).get('cursor')==='feedback-next')).toBe(true);
  await expect(dialog.getByText(/第 \d+ 版/)).toHaveCount(0);
  await page.screenshot({path:test.info().outputPath('history-page-retry.png'),animations:'disabled'});
  await dialog.getByRole('button',{name:'重试排期历史'}).click();
  await expect(dialog.getByRole('button',{name:'重试排期历史'})).toHaveCount(0);
  await expect(dialog).toContainText('已显示全部 0 条');
  expect(historyRequests).toBe(2);
  expect(errors).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  if(test.info().project.name==='desktop') {
    for(const width of [390,768,1280,1440,1920]) {
      await page.setViewportSize({width,height:900});
      expect(await dialog.locator('.ant-modal-body').evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
      expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }
    const accessibility=await new AxeBuilder({page}).include('.workspace-dialog').withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
    expect(accessibility.violations).toEqual([]);
  }
});

test('interview details no longer request or display notification history',async({page})=>{
  await workspace(page);
  let reads=0;
  await page.route(`**/api/v1/interviews/${interviewId}/notifications?*`,route=>{
    reads++;
    return route.fulfill({json:{items:[],hasMore:false,nextCursor:null}});
  });
  await page.goto('/schedule');
  await showCompletedInterviews(page);
  await page.locator('.interview-event').click();
  const dialog=page.getByRole('dialog',{name:'面试事实与处理'});
  await expect(dialog).toContainText('流程记录');
  await expect(dialog).not.toContainText('面试通知');
  expect(reads).toBe(0);
});

test('ordinary HR sees another member and exact historical source in candidate details',async({page})=>{
  const requests=await workspace(page);
  const historical={...person,ownerUserId:'historical-owner'};
  await page.route('**/api/v1/candidates?*',route=>route.fulfill({json:{...empty,items:[historical]}}));
  await page.route(`**/api/v1/candidates/${candidateId}`,route=>route.fulfill({json:historical}));
  await page.goto('/talents?view=pool');
  await expect(page.getByRole('table')).toContainText('历史负责人');
  await expect(page.getByRole('table')).toContainText('历史校园招聘');
  await page.getByRole('button',{name:'详情与版本'}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog).toContainText('历史负责人');
  await expect(dialog).not.toContainText('历史校园招聘');
  await page.goto(`/shared-library?candidate=${candidateId}`);
  const shared=page.getByRole('dialog',{name:person.fullName});
  await expect(shared).toContainText('历史校园招聘');
  await expect(shared).toContainText('历史负责人');
  await expect(shared).toContainText('2026/07/01 11:00');
  expect(requests).toContain(`/candidates/${candidateId}/resume-versions/${resumeId}/source`);
});

test('populated data keeps horizontal scrolling inside tables at supported widths',async({page},info)=>{
  test.skip(info.project.name!=='desktop','Representative viewport matrix runs once.');
  await workspace(page);
  for(const width of [390,768,1280,1440,1920]) {
    await page.setViewportSize({width,height:900});
    await page.goto('/talents?view=pool');
    await expect(page.getByRole('table')).toContainText('林雨桐');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
});

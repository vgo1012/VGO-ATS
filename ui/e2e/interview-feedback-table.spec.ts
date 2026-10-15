import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty={items:[],hasMore:false,nextCursor:null};
const interviews=[1,3].map(roundNo=>({
  id:`interview-${roundNo}`,applicationId:'app',candidateId:'person-a',jdId:'jd-a',jdVersionId:'jd-v2',
  workflowStepId:`step-${roundNo}`,stepCode:'INTERVIEW_ROUND_1',roundNo,interviewType:'INTERNAL',status:'SCHEDULED',
  scheduledStartAt:`2026-09-0${roundNo+6}T02:00:00Z`,scheduledEndAt:`2026-09-0${roundNo+6}T02:30:00Z`,timezoneId:'Asia/Shanghai',
  meetingMode:'ONLINE',candidateConfirmationStatus:'PENDING',nextResponsibleUserId:'hr-a',ownerUserId:'hr-a',version:1,
}));

test('流程记录使用紧凑表格保留跳轮、评价内容与窄屏滚动',async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path==='/interviews')return route.fulfill({json:{...empty,items:interviews}});
    if(path==='/interviews/interview-1')return route.fulfill({json:{interview:interviews[0],participants:[]}});
    if(path==='/applications/app')return route.fulfill({json:{application:{id:'app',candidateId:'person-a',jdId:'jd-a',demandId:'demand-a',recruitmentType:'INTERNAL',currentStage:'INTERVIEW',ownerUserId:'hr-a',terminalAt:null},collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:3600}});
    if(path==='/interviews/interview-1/materials')return route.fulfill({json:{interviewId:'interview-1',candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师'}});
    if(path==='/interview-feedback-batch')return route.fulfill({json:{...empty,visibleInterviewIds:interviews.map(item=>item.id),items:[{
      feedback:{id:'feedback-1',interviewId:'interview-1',authorUserId:'hr-a',status:'SUBMITTED'},
      currentVersion:{verdict:'PASS',shortComment:'第一轮评价内容',createdAt:'2026-09-07T03:00:00Z'},
    }]}});
    return route.fallback();
  });
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/schedule');
  const trigger=page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/第 1 轮/}).first();
  await trigger.focus();await trigger.press('Enter');
  const dialog=page.getByRole('dialog',{name:'面试事实与处理',exact:true});
  const table=dialog.getByRole('table').filter({has:page.getByRole('columnheader',{name:'面评',exact:true})});
  await expect(table.getByText('第一轮评价内容')).toBeVisible();
  await expect(table.getByRole('row').filter({hasText:'第一轮评价内容'}).getByRole('cell').last()).toHaveText('第一轮评价内容');
  await expect(table.getByRole('columnheader')).toHaveText(['步骤','面试时间','状态','本轮结果','面评']);
  await expect(table.getByRole('row').filter({hasText:'第一轮评价内容'})).toContainText('通过');
  await expect(table.getByRole('row').filter({hasText:'内部面试 · 第 3 轮'})).toContainText('待提交');
  for(const cell of await table.getByRole('columnheader').or(table.getByRole('cell')).all())await expect(cell).toHaveCSS('text-align','center');
  const neutralStyle=await dialog.getByRole('button',{name:'黑名单',exact:true}).evaluate(element=>{const style=getComputedStyle(element);return {color:style.color,background:style.backgroundColor,border:style.borderColor};});
  for(const label of ['待定','放弃','入职']){
    const button=dialog.getByRole('button',{name:label,exact:true});
    await expect(button).toHaveCSS('color',neutralStyle.color);
    await expect(button).toHaveCSS('background-color',neutralStyle.background);
    await expect(button).toHaveCSS('border-color',neutralStyle.border);
  }
  await expect(dialog.locator('.application-process .ant-card-head-title')).toBeVisible();
  await expect(table.getByRole('row')).toHaveCount(3);
  await expect(table.getByText('内部面试 · 第 3 轮',{exact:true})).toBeVisible();
  await expect(table.getByText(/第 2 轮/)).toHaveCount(0);
  await expect(table.getByText('本轮暂无面评')).toBeVisible();
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:1000});
    if(width>=1280){
      await expect(dialog.getByText('本次面试',{exact:true})).toBeVisible();
      await expect(dialog.getByText('当前阶段',{exact:true})).toBeVisible();
      expect(await table.evaluate(element=>{const container=element.parentElement!;return container.scrollWidth-container.clientWidth;})).toBeLessThanOrEqual(1);
    }
    if(width>=1280)await dialog.locator('.ant-modal-body').evaluate(element=>{element.scrollTop=0;});
    else await table.scrollIntoViewIfNeeded();
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    const bounds=await dialog.boundingBox();expect(bounds!.x).toBeGreaterThanOrEqual(0);expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(width+1);
    await page.screenshot({path:testInfo.outputPath(`feedback-table-${width}.png`)});
  }
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  const scheduleRows=await table.getByRole('row').allTextContents();
  await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);await expect(trigger).toBeFocused();
  await page.goto('/recruitment?application=app');
  const applicationDialog=page.getByRole('dialog',{name:'招聘活动详情',exact:true});
  const applicationTable=applicationDialog.getByRole('table',{name:'流程记录',exact:true});
  await expect(applicationTable.getByText('第一轮评价内容')).toBeVisible();
  await expect(applicationTable.getByRole('columnheader')).toHaveText(['步骤','面试时间','状态','本轮结果','面评']);
  for(const cell of await applicationTable.getByRole('columnheader').or(applicationTable.getByRole('cell')).all())await expect(cell).toHaveCSS('text-align','center');
  expect(await applicationTable.getByRole('row').allTextContents()).toEqual(scheduleRows);
  for(const width of [390,1440]){
    await page.setViewportSize({width,height:1000});
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`application-records-${width}.png`)});
  }
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
});

test('超长面评只占摘要行，桌面和窄屏均可查看完整原文',async({page})=>{
  await reviewWorkspace(page);
  const comment=`面试结论清晰\n${'/usr/bin/git-config-very-long-path/'.repeat(25)}`;
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path==='/applications/app')return route.fulfill({json:{application:{id:'app',candidateId:'person-a',jdId:'jd-a',demandId:'demand-a',recruitmentType:'INTERNAL',currentStage:'INTERVIEW',ownerUserId:'hr-a',terminalAt:null},collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:3600}});
    if(path==='/interviews')return route.fulfill({json:{...empty,items:[interviews[0]]}});
    if(path==='/interviews/interview-1/materials')return route.fulfill({json:{interviewId:'interview-1',candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师'}});
    if(path==='/interview-feedback-batch')return route.fulfill({json:{...empty,visibleInterviewIds:['interview-1'],items:[{feedback:{id:'feedback-1',interviewId:'interview-1',authorUserId:'hr-a',status:'SUBMITTED'},currentVersion:{verdict:'PASS',shortComment:comment,createdAt:'2026-09-07T03:00:00Z'}}]}});
    return route.fallback();
  });
  await page.goto('/recruitment?application=app');
  const dialog=page.getByRole('dialog',{name:'招聘活动详情',exact:true});
  const table=dialog.getByRole('table',{name:'流程记录'});
  const more=table.getByRole('button',{name:'查看更多面评'});
  await expect(more).toBeVisible();
  await expect(table).not.toContainText(comment);
  for(const width of [1440,390]){
    await page.setViewportSize({width,height:900});
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    expect((await table.getByRole('row').last().boundingBox())!.height).toBeLessThan(130);
    await more.click();
    const full=page.getByRole('dialog',{name:'完整面评'});
    await expect(full.locator('.long-text-full')).toHaveText(comment);
    await expect(full.locator('.long-text-full')).toHaveCSS('overflow-wrap','anywhere');
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await full.getByRole('button',{name:'关闭'}).click();
    await expect(more).toBeVisible();
  }
});

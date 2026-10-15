import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('黑名单只在详情展示，材料在右侧且流程紧随其后', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const application={id:'app',candidateId:'person-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'bound-v1',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',ownerUserId:'hr-a',terminalAt:null,version:1};
  const reads:string[]=[];
  page.on('request',request=>{if(/candidate-blacklist-tags|\/facts$|activity-records/.test(new URL(request.url()).pathname))reads.push(request.url());});
  await page.route('**/api/v1/applications?**',route=>route.fulfill({json:{items:[application],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications/app',route=>route.fulfill({json:{application,collaboratorUserIds:[],workflowSteps:[1,2,3,4,5].map((round,index)=>({id:`step-${round}`,sequenceNo:round,stepCode:round<=3?'INTERVIEW_ROUND_1':round===4?'DECISION':'OFFER',roundNo:round,status:round===5?'IN_PROGRESS':'PASSED',resultReason:round===5?'':`已完成第 ${round} 阶段的沟通与确认，详细记录保留供后续招聘核对。`,completedAt:index<4?'2026-09-07T01:00:00Z':null})),decisions:[],offerStatusRecords:[],elapsedSeconds:61200}}));
  await page.route('**/api/v1/candidates/person-a/restriction-summaries',route=>route.fulfill({json:[{restrictionType:'CLIENT_RESTRICTION',clientId:'client',clientName:'公司标签 A',reason:'仅作提醒的已核验原因',sourceChannel:'MANUAL',occurredAt:'2026-09-07T01:00:00Z',blocking:false}]}));
  await page.route('**/api/v1/interviews?**',route=>route.fulfill({json:{items:[1,2,3].map(round=>({id:`interview-${round}`,workflowStepId:`step-${round}`,stepCode:'INTERVIEW_ROUND_1',roundNo:round,interviewType:'INTERNAL',scheduledStartAt:'2026-09-07T01:00:00Z'})),hasMore:false}}));
  await page.route('**/api/v1/interviews/*/materials',route=>route.fulfill({json:{interviewId:'interview',resumeVersionId:'bound-v1',resumeVersionNo:1,resumeFileName:'本次绑定简历.docx',resumeMediaType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',jdVersionNo:2,jdTitle:'本次岗位',jdRawText:'本次岗位材料'}}));
  await page.route('**/api/v1/interview-feedback-batch?**',route=>route.fulfill({json:{items:[],visibleInterviewIds:['interview-1','interview-2','interview-3'],hasMore:false}}));
  await page.route('**/api/v1/applications/app/communications',route=>route.fulfill({json:[]}));
  await page.goto('/recruitment');
  const row=page.getByRole('row').filter({has:page.getByRole('button',{name:'详情与版本'})});
  await expect(row.getByText(/重试黑名单|读取标签|黑名单需授权/)).toHaveCount(0);
  await expect(row.locator('.ant-tag').filter({hasText:/^黑名单$/})).toHaveCount(0);
  await row.getByRole('button',{name:'详情与版本'}).click();
  const dialog=page.getByRole('dialog',{name:'招聘活动详情'});
  await expect(dialog.getByText('仅作提醒的已核验原因',{exact:true})).toBeVisible();
  await expect(dialog.getByText('流程记录',{exact:true})).toBeVisible();
  await expect(dialog.getByText(/本次简历 · AI 提取信息|AI 风险与简历核对|活动记录 · 内容汇总/)).toHaveCount(0);
  const material=dialog.getByRole('button',{name:'查看面试材料',exact:true});
  await expect(material).toHaveCount(1);
  await expect(dialog.getByRole('combobox',{name:'材料对应面试轮次'})).toHaveCount(0);
  for(const width of [390,1280,1440,1920]) {
    await page.setViewportSize({width,height:900});
    await material.scrollIntoViewIfNeeded();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1)).toBe(true);
    if(width>=768){
      const label=await dialog.getByText('黑名单标签',{exact:true}).boundingBox();
      const button=await material.boundingBox();
      const flow=await dialog.getByText('流程记录',{exact:true}).boundingBox();
      expect(button!.x).toBeGreaterThan(label!.x);
      expect(flow!.y).toBeGreaterThan(button!.y+button!.height);
    }
    const table=dialog.locator('.application-process .ant-table-content').first();
    expect(await table.evaluate(node=>node.scrollHeight-node.clientHeight)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`detail-${width}.png`),animations:'disabled'});
  }
  expect(reads).toEqual([]);
  await material.click();
  await expect(page.getByRole('dialog',{name:'面试材料',exact:true}).getByText('本次岗位材料',{exact:true})).toBeVisible();
});


test('黑名单选择公司并填写理由后结束当前流程，已有标签也可执行', async ({ page }) => {
  await reviewWorkspace(page);
  const application={id:'app-blacklist',candidateId:'person-a',demandId:'demand-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'bound-v1',workflowTemplateVersionId:'workflow',matchPlanVersionId:'plan',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',outcome:null,terminalAt:null,version:1,createTime:'2026-09-07T01:00:00Z',updateTime:'2026-09-07T01:00:00Z'};
  let ended=false;
  let saved:Record<string,unknown>|undefined;
  await page.route('**/api/v1/applications?**',route=>route.fulfill({json:{items:ended?[]:[application],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications/app-blacklist',route=>route.fulfill({json:{application,collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:10}}));
  await page.route('**/api/v1/candidate-blacklist-tags?**',route=>route.fulfill({json:['person-a']}));
  await page.route('**/api/v1/clients?**',route=>route.fulfill({json:{items:[{id:'client-a',name:'公司标签 A'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidates/person-a/client-restrictions?**',route=>route.fulfill({json:[{id:'existing',candidateId:'person-a',clientId:'client-a',status:'ACTIVE',reasonDetail:'历史标签原因',expiresAt:null}]}));
  await page.route('**/api/v1/applications/app-blacklist/client-restrictions',route=>{
    saved=route.request().postDataJSON() as Record<string,unknown>;
    ended=true;
    return route.fulfill({json:{id:'new-tag',candidateId:'person-a',clientId:'client-a',status:'ACTIVE',reasonDetail:saved.reasonDetail}});
  });
  await page.goto('/recruitment');
  const row=page.getByRole('row').filter({has:page.getByRole('button',{name:'详情与版本'})});
  await row.getByRole('button',{name:'黑名单',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'黑名单',exact:true});
  const save=dialog.getByRole('button',{name:'保存并结束当前流程'});
  await expect(save).toBeDisabled();
  await dialog.getByLabel('公司标签').click();
  await page.locator('.ant-select-item-option-content').getByText('公司标签 A',{exact:true}).click();
  await expect(dialog.getByText('已有有效标签：历史标签原因')).toBeVisible();
  await save.click();
  await expect(dialog.locator('.ant-form-item-explain-error')).toBeVisible();
  expect(saved).toBeUndefined();
  await dialog.getByLabel('原因评论').fill('用户确认的本次结束原因');
  await expect(save).toBeEnabled();
  await save.click();
  await expect(dialog).toHaveCount(0);
  await expect(row).toHaveCount(0);
  expect(saved).toEqual({clientId:'client-a',reasonDetail:'用户确认的本次结束原因',sourceChannel:'MANUAL'});
});

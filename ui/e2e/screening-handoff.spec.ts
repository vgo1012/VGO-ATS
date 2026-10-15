import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

async function routingWorkspace(page:Page) {
  await reviewWorkspace(page);
  let state:'pool'|'screening'|'recruitment'='pool',version=0,failReturn=false;
  const empty={items:[],hasMore:false,nextCursor:null};
  const person={id:'person-a',fullName:'林雨桐',primaryEmail:'lin@example.com',primaryPhone:'13800001234',ownerUserId:'hr-a',createdAt:'2026-09-05T01:00:00Z',updatedAt:'2026-09-07T01:00:00Z'};
  const result={resultId:'result-a',baseEvaluationId:'base-a',candidateId:'person-a',resumeVersionId:'resume-a',stableRank:1,professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS',details:{dimensions:[],criteria:[],selectedBonusCap:0}};
  const run={id:'run-a',jdVersionId:'jd-v2',status:'SUCCEEDED',totalCount:1,succeededCount:1,failedCount:0};
  const jd={id:'jd-a',title:'前端工程师',status:'ACTIVE',currentVersionNo:2,clientId:null,clientName:null,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-10T00:00:00Z'};
  const basis={basisRunId:'run-a',jdVersionId:'jd-v2',basisKey:'company-default',planVersionId:companyScoring.version.id,weightProfileVersionId:'weight-v1',sortMode:'REFERENCE_SCORE',lastRunAt:'2026-09-10T00:00:00Z',runCount:1,candidateCount:1,scoredCandidateCount:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20,selectedCriterionIds:[],personalScreeningSettings:null};
  await page.route('**/api/v1/**',async route=>{
    const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
    const admission={candidateId:'person-a',resumeVersionId:'resume-a',jdVersionId:'jd-v2',defaultMatchRunId:'run-a',defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:state==='screening'?'ADMITTED':'READY',admissionId:state==='screening'?'admission-a':null,admissionVersion:state==='screening'?String(version):null,admittedAt:'2026-09-10T00:00:00Z'};
    const list=(items:unknown[])=>route.fulfill({json:{...empty,items}});
    if(path==='/talent-search')return list(state==='pool'?[{candidateId:'person-a'}]:[]);
    if(path==='/candidates')return list(state==='pool'?[person]:[]);
    if(path==='/jds/jd-a')return route.fulfill({json:jd});
    if(path==='/jds/jd-a/versions')return list([{id:'jd-v2',jdId:'jd-a',title:'前端工程师',rawText:'前端工程师岗位说明',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-a',createTime:'2026-09-10T00:00:00Z',versionNo:2}]);
    if(path==='/jd-versions/jd-v2')return route.fulfill({json:{id:'jd-v2',jdId:'jd-a',title:'前端工程师',rawText:'前端工程师岗位说明',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-a',createTime:'2026-09-10T00:00:00Z',versionNo:2}});
    if(path==='/jd-versions/jd-v2/active-scoring-config')return route.fulfill({json:{jdVersionId:'jd-v2',workflowTemplateActive:true,scoringConfigurationActive:true,readyForApplication:true,scoringConfiguration:companyScoring}});
    if(path==='/screening-candidates')return route.fulfill({json:state==='screening'||url.searchParams.has('candidateIds')&&state==='pool'?[admission]:[]});
    if(path==='/screening-admissions'){
      expect(route.request().postDataJSON()).toEqual({items:[{candidateId:'person-a',resumeVersionId:'resume-a',jdVersionId:'jd-v2',defaultMatchRunId:'run-a'}]});
      state='screening';version++;
      return route.fulfill({json:{requestedCount:1,admittedCount:1,alreadyAdmittedCount:0,items:[]}});
    }
    if(path==='/screening-admissions/return'){
      expect(route.request().postDataJSON()).toEqual({items:[{admissionId:'admission-a',expectedVersion:String(version)}]});
      if(failReturn){failReturn=false;return route.fulfill({status:409,json:{type:'about:blank',title:'退回未完成',status:409,detail:'暂时无法退回，请重试',code:'SCREENING_RETURN_RETRY',recoverable:true}});}
      state='pool';version++;
      return route.fulfill({json:{requestedCount:1,returnedCount:1,alreadyReturnedCount:0}});
    }
    if(path==='/candidate-pool-statuses')return route.fulfill({json:[{candidateId:'person-a',candidateName:'林雨桐',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:state==='recruitment'?['IN_PROCESS']:[],jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'前端工程师',versionNo:2,resumeVersionId:'resume-a',status:state==='recruitment'?'ADVANCED':'AI_SCREENED',stage:null,applicationId:state==='recruitment'?'application-a':null,matchRunId:'run-a'}]}]});
    if(path==='/match-runs/run-a')return route.fulfill({json:run});
    if(path==='/match-runs/run-a/results')return list([result]);
    if(path==='/match-jobs')return list(state==='screening'?[{jdVersionId:'jd-v2',latestRunId:'run-a',lastRunAt:'2026-09-10T00:00:00Z',runCount:1,candidateCount:1,scoredCandidateCount:1,activeRunCount:0}]:[]);
    if(path==='/match-jobs/jd-v2/bases')return list(state==='screening'?[basis]:[]);
    if(path==='/match-jobs/jd-v2/results')return list(state==='screening'?[{runId:'run-a',scoredAt:'2026-09-10T00:00:00Z',result}]:[]);
    if(path==='/jd-versions/jd-v2/facts')return route.fulfill({json:{facts:{title:'前端工程师'}}});
    if(path==='/jd-versions/jd-v2/recruitment-processing-access')return route.fulfill({json:{processingAllowed:true}});
    if(path==='/jd-versions/jd-v2/recruitment-readiness')return route.fulfill({json:{headcount:2,occupiedHeadcount:1,remainingHeadcount:1,jdId:'jd-a',jdVersionId:'jd-v2',jdTitle:'前端工程师',demandId:'demand-a',recruitmentType:'INTERNAL',canProcess:true,canManageWorkflow:true,activeWorkflowId:'workflow-a',activeWorkflow:{template:{id:'workflow-a',status:'ACTIVE'},steps:[]},draftWorkflows:[],standardSteps:[]}});
    if(path==='/applications/batch'){
      state='recruitment';
      return route.fulfill({json:{items:[{candidateId:'person-a',resumeVersionId:'resume-a',applicationId:'application-a',status:'SUCCEEDED'}]}});
    }
    return route.fallback();
  });
  return {failNextReturn:()=>{failReturn=true;}};
}

for(const width of [390,1440]) for(const resource of ['汇总','评分依据']) test(`离开筛选页取消未完成的${resource}请求 ${width}`,async({page})=>{
  await page.setViewportSize({width,height:900});
  await routingWorkspace(page);
  const path=resource==='汇总'?'/api/v1/match-jobs':'/api/v1/match-jobs/jd-v2/bases';
  let started=false,aborted=false;
  let release!:()=>void;
  const pending=new Promise<void>(resolve=>{release=resolve;});
  page.on('requestfailed',request=>{if(new URL(request.url()).pathname===path&&request.failure()?.errorText.includes('ERR_ABORTED'))aborted=true;});
  await page.route(`**${path}?**`,async route=>{
    started=true;
    await pending;
    await route.fallback();
  });
  try {
    await page.goto('/talents');
    await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
    await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).click();
    await expect.poll(()=>started).toBe(true);
    await page.getByRole('navigation',{name:width===390?'移动端主导航':'主导航',exact:true}).getByRole('button',{name:/^(我的候选人|团队候选人|候选人库)$/}).click();
    await expect(page).toHaveURL(/\/talents$/);
    await expect.poll(()=>aborted).toBe(true);
    await expect(page.locator('.error-notification')).toHaveCount(0);
  } finally {
    release();
  }
});

for(const width of [390,1440]) test(`岗位汇总撤权后隐藏缓存结果并恢复 ${width}`,async({page})=>{
  await page.setViewportSize({width,height:900});
  await routingWorkspace(page);
  let failure=0;
  let release!:()=>void;
  const retryPending=new Promise<void>(resolve=>{release=resolve;});
  const remountGate:{pending?:Promise<void>}={};
  await page.route('**/api/v1/match-jobs?**',async route=>{
    if(failure===503)await retryPending;
    if(remountGate.pending)await remountGate.pending;
    return failure
      ?route.fulfill({status:failure,json:{type:'about:blank',title:'岗位读取失败',status:failure,detail:'岗位暂时无法读取',code:'ACCESS_DENIED',recoverable:false}})
      :route.fulfill({json:{items:[{jdVersionId:'jd-v2',latestRunId:'run-a',runCount:1,candidateCount:1,scoredCandidateCount:1,activeRunCount:1}],hasMore:false,nextCursor:null}});
  });
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).click();
  const candidate=page.locator('.matching-score-table').getByText('林雨桐');
  await expect(candidate).toBeVisible();
  failure=403;
  const retry=page.getByRole('button',{name:'重试岗位评分',exact:true});
  await expect(retry).toBeVisible();
  await expect(candidate).toHaveCount(0);
  await expect(page.getByRole('button',{name:'推荐到流程',exact:true})).toHaveCount(0);
  failure=503;
  await retry.click();
  try {
    await expect(retry).toBeDisabled();
    await expect(candidate).toHaveCount(0);
  } finally {
    release();
  }
  await expect(retry).toBeEnabled();
  await expect(candidate).toHaveCount(0);
  await expect(page.locator('.error-notification')).toBeVisible();
  await page.getByRole('navigation',{name:width===390?'移动端主导航':'主导航',exact:true}).getByRole('button',{name:/^(我的候选人|团队候选人|候选人库)$/}).click();
  await expect(page).toHaveURL(/\/talents$/);
  let releaseRemount!:()=>void;
  remountGate.pending=new Promise<void>(resolve=>{releaseRemount=resolve;});
  await page.goBack();
  try {
    await expect(retry).toBeDisabled();
    await expect(candidate).toHaveCount(0);
    await expect(page.getByRole('button',{name:'推荐到流程',exact:true})).toHaveCount(0);
  } finally {
    releaseRemount();
  }
  await expect(retry).toBeEnabled();
  await expect(candidate).toHaveCount(0);
  failure=0;
  await retry.click();
  await expect(candidate).toBeVisible();
  await expect(page.getByRole('button',{name:'推荐到流程',exact:true})).toBeEnabled();
});

for(const width of [390,1440]) test(`推荐资格撤权后隐藏旧流程并允许恢复 ${width}`,async({page})=>{
  await page.setViewportSize({width,height:900});
  await routingWorkspace(page);
  const readiness={headcount:2,occupiedHeadcount:1,remainingHeadcount:1,jdId:'jd-a',jdVersionId:'jd-v2',jdTitle:'受限外部岗位',demandId:'demand-a',recruitmentType:'EXTERNAL',clientName:'受限甲方公司',canProcess:true,canManageWorkflow:true,activeWorkflowId:null,activeWorkflow:null,draftWorkflows:[],standardSteps:[]};
  let denied=false, activations=0;
  await page.route('**/api/v1/jd-versions/jd-v2/recruitment-readiness',route=>denied
    ?route.fulfill({status:403,json:{type:'about:blank',title:'无权读取岗位',status:403,detail:'岗位权限已撤销'}})
    :route.fulfill({json:readiness}));
  await page.route('**/api/v1/jd-versions/jd-v2/standard-workflow-activation',route=>{
    activations++;denied=true;
    return route.fulfill({json:readiness});
  });
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
  await expect(dialog.getByText('甲方公司：受限甲方公司')).toBeVisible();
  await dialog.getByRole('button',{name:'使用并激活标准流程'}).click();
  await expect(dialog.getByRole('button',{name:'重试流程检查'})).toBeVisible();
  await expect(dialog.getByText('甲方公司：受限甲方公司')).toHaveCount(0);
  await expect(dialog.getByText('推荐岗位：受限外部岗位')).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'使用并激活标准流程'})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
  denied=false;
  await dialog.getByRole('button',{name:'重试流程检查'}).click();
  await expect(dialog.getByText('甲方公司：受限甲方公司')).toBeVisible();
  await expect(dialog.getByRole('button',{name:'使用并激活标准流程'})).toBeEnabled();
  expect(activations).toBe(1);
});

test('满额岗位阻止推荐，刷新名额后恢复操作',async({page})=>{
  await routingWorkspace(page);
  let full=true;
  await page.route('**/api/v1/jd-versions/jd-v2/recruitment-readiness',route=>route.fulfill({json:{headcount:2,occupiedHeadcount:full?2:1,remainingHeadcount:full?0:1,jdId:'jd-a',jdVersionId:'jd-v2',jdTitle:'前端工程师',demandId:'demand-a',recruitmentType:'INTERNAL',canProcess:true,canManageWorkflow:true,activeWorkflowId:'workflow-a',activeWorkflow:{template:{id:'workflow-a',status:'ACTIVE'},steps:[]},draftWorkflows:[],standardSteps:[]}}));
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
  await expect(dialog.getByText(/HC 已满/)).toBeVisible();
  await expect(dialog.getByRole('button',{name:'确认推荐 1 人'})).toBeDisabled();
  full=false;
  await dialog.getByRole('button',{name:'刷新名额'}).click();
  await expect(dialog.getByText('已占用 1 / 2 · 剩余 1')).toBeVisible();
  await expect(dialog.getByRole('button',{name:'确认推荐 1 人'})).toBeEnabled();
});

test('直接推荐取消保留简历，成功后从我的简历移出',async({page})=>{
  await routingWorkspace(page);
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
  await dialog.getByRole('button',{name:/取\s*消/}).click();
  await expect(page.locator('.talent-ledger-card').getByRole('button',{name:'林雨桐',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  await dialog.getByRole('button',{name:'确认推荐 1 人'}).click();
  await dialog.getByRole('button',{name:/^完\s*成$/}).click();
  await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
  await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toHaveCount(0);
  await page.reload();
  await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
  await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toHaveCount(0);
});

for(const width of [390,1280,1440,1920]) {
  test(`简历分流、退回、重新加入与推荐后不回流 ${width}`,async({page},testInfo)=>{
    await page.setViewportSize({width,height:900});
    const fixture=await routingWorkspace(page);
    const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto('/talents');
    await expect(page.getByRole('columnheader',{name:'处理状态',exact:true})).toHaveCount(0);
    await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
    await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).click();
    await expect(page).toHaveURL(/scoreJob=jd-v2/);
    await expect(page.getByRole('button',{name:'退回我的简历',exact:true})).toBeEnabled();
    await page.goto('/talents');
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toHaveCount(0);
    await page.reload();
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toHaveCount(0);
    await page.goto('/matching?scoreJob=jd-v2');
    await page.getByRole('button',{name:'退回我的简历',exact:true}).click();
    let dialog=page.getByRole('dialog',{name:'退回我的简历',exact:true});
    await dialog.getByRole('button',{name:/取\s*消/}).click();
    await expect(page.locator('.matching-score-table').getByText('林雨桐')).toBeVisible();
    await page.getByRole('button',{name:'全选此依据全部候选人'}).click();
    await page.getByRole('button',{name:'批量退回我的简历'}).click();
    dialog=page.getByRole('dialog',{name:'退回我的简历',exact:true});
    fixture.failNextReturn();
    await dialog.getByRole('button',{name:'确认退回'}).click();
    await expect(page.getByRole('alert').filter({hasText:'暂时无法退回，请重试'})).toBeVisible();
    await page.screenshot({path:testInfo.outputPath(`return-${width}.png`),fullPage:true});
    await dialog.getByRole('button',{name:'重试退回'}).focus();
    await page.keyboard.press('Enter');
    await expect(dialog).toHaveCount(0);
    await page.goto('/talents');
    await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    expect((await new AxeBuilder({page}).include('.app-main').analyze()).violations).toEqual([]);
    await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
    await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).click();
    await expect(page.getByRole('button',{name:'退回我的简历',exact:true})).toBeEnabled();
    await page.getByRole('button',{name:'推荐到流程',exact:true}).click();
    const advance=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
    await advance.getByRole('button',{name:'确认推荐 1 人'}).click();
    await expect(advance).toHaveCount(0);
    await page.goto('/talents');
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    await expect(page.getByRole('button',{name:'林雨桐',exact:true})).toHaveCount(0);
    await page.goto('/matching?run=run-a');
    await expect(page).toHaveURL(/run=run-a/);
    await expect(page.locator('.matching-score-table').getByText('林雨桐')).toBeVisible();
    await expect(page.getByRole('button',{name:'退回我的简历',exact:true})).toBeDisabled();
    await expect(page.getByRole('button',{name:'批量退回我的简历'})).toBeDisabled();
    expect(errors).toEqual([]);
  });
}

test('推荐弹窗精简摘要与分页',async({page},testInfo)=>{
  await routingWorkspace(page);
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
  await expect(dialog.getByRole('button',{name:'确认推荐 1 人'})).toBeEnabled();
  await expect(dialog.getByText('推荐后待 HR 跟进')).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'刷新名额'})).toHaveCount(0);
  const pagination=dialog.getByRole('navigation',{name:'候选人分页'});
  await expect(pagination.getByRole('button')).toHaveCount(2);
  await expect(pagination.getByRole('button',{name:'上一页'})).toBeDisabled();
  await expect(pagination.getByRole('button',{name:'下一页'})).toBeDisabled();
  expect(await dialog.evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:testInfo.outputPath('recommendation-compact.png'),animations:'disabled'});
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
});

test('推荐全部失败显示失败进度，重试成功后才显示成功',async({page},testInfo)=>{
  await routingWorkspace(page);
  let failed=true;
  await page.route('**/api/v1/applications/batch',route=>failed?route.fulfill({json:{items:[{candidateId:'person-a',resumeVersionId:'resume-a',status:'FAILED',errorCode:'APPLICATION_BATCH_ITEM_FAILED',message:'请重试此候选人，已创建记录不会重复生成'}]}}):route.fallback());
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.getByRole('button',{name:'直接推荐',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'推荐候选人到招聘流程'});
  await dialog.getByRole('button',{name:'确认推荐 1 人'}).click();
  await expect(dialog.getByText('候选人未处理成功',{exact:true})).toBeVisible();
  const progress=dialog.getByRole('progressbar',{name:'候选人批量处理进度'});
  await expect(progress).toHaveClass(/ant-progress-status-exception/);
  await expect(dialog.getByText(/成功项已保存/)).toHaveCount(0);
  await expect(dialog.getByText('已完成 0 人 · 失败 1 人 · 尚未处理 0 人')).toBeVisible();
  for(const width of [1440,390]){
    await page.setViewportSize({width,height:900});
    await progress.scrollIntoViewIfNeeded();
    await expect(progress).toBeVisible();
    await page.screenshot({path:testInfo.outputPath(`recommendation-failed-${width}.png`),animations:'disabled'});
  }
  failed=false;
  await dialog.getByRole('button',{name:'继续处理未完成项'}).click();
  await expect(progress).toHaveClass(/ant-progress-status-success/);
  await expect(dialog.getByText('已完成 1 人 · 失败 0 人 · 尚未处理 0 人')).toBeVisible();
  await expect(dialog.getByText('候选人未处理成功',{exact:true})).toHaveCount(0);
});

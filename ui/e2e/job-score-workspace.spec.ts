import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const pageResult=(candidateId:string,resultId:string,runId:string,score=88)=>({
  runId,scoredAt:'2026-09-07T01:00:00Z',result:{resultId,baseEvaluationId:`base-${candidateId}`,candidateId,resumeVersionId:`resume-${candidateId}`,professionalScore:score-8,bonusScore:8,referenceScore:score,mustTier:'PASS',stableRank:1,
    details:{jdFactSnapshotId:'jd-fact',resumeFactSnapshotId:`resume-fact-${candidateId}`,candidateId,resumeVersionId:`resume-${candidateId}`,professionalScore:score-8,bonusScore:8,referenceScore:score,mustTier:'PASS',selectedBonusCap:10,
      dimensions:[{code:'CORE_SKILLS',enabled:true,configuredWeight:29,status:'KNOWN',score:90,explanation:'核心技能匹配',highlights:['操作系统与网络协议 ← Linux 系统安装操作；售后工程师现场交付与故障排查 ← 客户运维支持经验']}],criteria:[]}}
});
const basisRun=(id:string,totalCount=54)=>({id,taskId:`task-${id}`,jdVersionId:'jd-v2',jdFactSnapshotId:'jd-fact',planVersionId:`plan-${id}`,weightProfileVersionId:`weight-${id}`,personalPreferenceVersionId:null,selectionSnapshotId:`selection-${id}`,sortMode:'REFERENCE_SCORE',status:'SUCCEEDED',totalCount,succeededCount:totalCount,failedCount:0,errorCode:null,version:1,createdAt:'2026-09-07T01:00:00Z',startedAt:'2026-09-07T01:00:00Z',finishedAt:'2026-09-07T01:00:00Z',updatedAt:'2026-09-07T01:00:00Z'});

async function mockJobScores(page:Page,firstPageCount=1) {
  const requests:string[]=[];
  let failSecondPage=true;
  const jd={id:'jd-a',title:'IT运维工程师',status:'ACTIVE',currentVersionNo:2,clientId:null,clientName:null,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-01T00:00:00Z'};
  await page.route('**/api/v1/jds**',route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/api/v1/jds/jd-a/versions')return route.fulfill({json:{items:[{id:'jd-v2',jdId:'jd-a',versionNo:2,title:jd.title,rawText:'负责 Linux 系统、网络和现场运维支持。',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-a',createTime:'2026-09-01T00:00:00Z'}],hasMore:false,nextCursor:null}});
    if(path==='/api/v1/jds/jd-a')return route.fulfill({json:jd});
    return route.fulfill({json:{items:[jd],hasMore:false,nextCursor:null}});
  });
  await page.route('**/api/v1/screening-candidates**',route=>route.fulfill({json:[{candidateId:'person-a',resumeVersionId:'resume-person-a',jdVersionId:'jd-v2',admissionStatus:'ADMITTED'}]}));
  await page.route('**/api/v1/me/jd-scoring-configs/*/preferences',route=>route.fulfill({json:null}));
  await page.route('**/api/v1/jd-versions/jd-v2',route=>route.fulfill({json:{id:'jd-v2',jdId:'jd-a',versionNo:2,title:jd.title,rawText:'负责 Linux 系统、网络和现场运维支持。',status:'PUBLISHED',sourceType:'MANUAL',createId:'hr-a',createTime:'2026-09-01T00:00:00Z'}}));
  await page.route('**/api/v1/jd-versions/jd-v2/active-scoring-config',route=>route.fulfill({json:{jdVersionId:'jd-v2',scoringConfiguration:companyScoring,scoringConfigurationActive:true,workflowTemplateActive:false,readyForApplication:false}}));
  await page.route('**/api/v1/match-jobs**',route=>{
    const url=new URL(route.request().url());requests.push(`${url.pathname}?${url.searchParams}`);
    if(url.pathname==='/api/v1/match-jobs')return route.fulfill({json:{items:[{jdVersionId:'jd-v2',latestRunId:'basis-a',lastRunAt:'2026-09-07T01:00:00Z',runCount:3,evaluationCount:54,succeededCount:54,failedCount:0,candidateCount:54,scoredCandidateCount:54,activeRunCount:0}],hasMore:false,nextCursor:null}});
    if(url.pathname.endsWith('/bases')&&url.searchParams.get('cursor')==='bases-next')return route.fulfill({json:{items:[
      {basisRunId:'basis-c',jdVersionId:'jd-v2',basisKey:'basis-c',planVersionId:'plan-c',weightProfileVersionId:'weight-c',sortMode:'BONUS_SCORE',lastRunAt:'2026-09-05T01:00:00Z',runCount:1,candidateCount:1,scoredCandidateCount:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20,selectedCriterionIds:[],personalScreeningSettings:null},
    ],hasMore:false,nextCursor:null}});
    if(url.pathname.endsWith('/bases'))return route.fulfill({json:{items:[
      {basisRunId:'basis-a',jdVersionId:'jd-v2',basisKey:'basis-a',planVersionId:'company-v1',weightProfileVersionId:'weight-a',sortMode:'REFERENCE_SCORE',lastRunAt:'2026-09-07T01:00:00Z',runCount:3,candidateCount:54,scoredCandidateCount:54,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20,selectedCriterionIds:[],personalScreeningSettings:null},
      {basisRunId:'basis-b',jdVersionId:'jd-v2',basisKey:'basis-b',planVersionId:'plan-b',weightProfileVersionId:'weight-b',sortMode:'PROFESSIONAL_SCORE',lastRunAt:'2026-09-06T01:00:00Z',runCount:1,candidateCount:1,scoredCandidateCount:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20,selectedCriterionIds:[],personalScreeningSettings:null},
    ],hasMore:true,nextCursor:'bases-next'}});
    if(url.pathname.endsWith('/results')){
      if(url.searchParams.get('cursor')==='next'&&failSecondPage)return route.fulfill({status:503,contentType:'application/problem+json',json:{type:'https://vgo.example/problems/pagination-unavailable',title:'分页读取失败',status:503,detail:'下一页暂不可用',instance:'/api/v1/match-jobs/jd-v2/results',code:'TEST_PAGE_UNAVAILABLE',traceId:'trace-test-page',recoverable:true}});
      const items=url.searchParams.get('cursor')==='next'?[pageResult('person-b','result-b','original-run',79)]:Array.from({length:firstPageCount},(_,index)=>pageResult(index===0?'person-a':`person-layout-${index}`,index===0?'result-a':`result-layout-${index}`,'original-run',88-index));
      return route.fulfill({json:{items,hasMore:!url.searchParams.has('cursor'),nextCursor:url.searchParams.has('cursor')?null:'next'}});
    }
    return route.fallback();
  });
  await page.route('**/api/v1/match-runs**',route=>{
    const url=new URL(route.request().url());requests.push(`${url.pathname}?${url.searchParams}`);
    if(url.pathname==='/api/v1/match-runs')return route.fulfill({json:{items:[basisRun('history-52',52),basisRun('history-1a',1),basisRun('history-1b',1)],hasMore:false,nextCursor:null}});
    if(url.pathname.endsWith('/results'))return route.fulfill({json:{items:[pageResult('person-a','history-result','history-52').result],hasMore:false,nextCursor:null}});
    const id=url.pathname.split('/').at(-1)!;
    return route.fulfill({json:basisRun(id)});
  });
  await page.route('**/api/v1/candidates/person-*',route=>{
    const id=new URL(route.request().url()).pathname.split('/').at(-1)!;
    return route.fulfill({json:{id,fullName:id==='person-a'?'候选人甲':'候选人乙',primaryEmail:null,primaryPhone:null,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',status:'ACTIVE',version:1,createdAt:'2026-09-07T01:00:00Z',updatedAt:'2026-09-07T01:00:00Z'}});
  });
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:[{candidateId:'person-a',candidateName:'候选人甲',resumeVersionId:'resume-person-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]},{candidateId:'person-b',candidateName:'候选人乙',resumeVersionId:'resume-person-b',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]},...Array.from({length:8},(_,index)=>({candidateId:`person-layout-${index}`,candidateName:`布局候选人 ${index+1}`,resumeVersionId:`resume-person-layout-${index}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]}))]}));
  await page.route('**/api/v1/jd-versions/jd-v2/facts',route=>route.fulfill({json:{factSnapshotId:'jd-fact',jdVersionId:'jd-v2',snapshotVersion:1,sourceType:'AI_PARSE',schemaVersion:'1',facts:{title:'IT运维工程师',summary:'负责 Linux 系统与客户现场运维',professional:{coreSkills:[{id:'linux',name:'Linux',required:true,aliases:[]}],roleExperiences:[],minimumYears:3,preferredYears:null,responsibilities:['维护 Linux 系统'],industryTags:[],projectTags:[]},controlledConditions:{ageMin:null,ageMax:null,sex:'ANY',educations:['本科'],credentials:[],languages:[]},fulfillmentConditions:{locations:['上海'],availability:null,salaryMin:null,salaryMax:null,salaryPeriod:null},evidence:[]},createdAt:'2026-09-01T00:00:00Z'}}));
  await page.route('**/api/v1/resume-versions/**/facts/**',route=>route.fulfill({json:{snapshot:{id:'resume-fact-person-a',resumeVersionId:'resume-person-a',parseSnapshotId:null,previousSnapshotId:null,snapshotVersion:1,sourceType:'AI_PARSE',currentMarker:'CURRENT',correctedByUserId:null,correctionReason:null,createdAt:'2026-09-07T01:00:00Z'},workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[]}}));
  return {requests,recover:()=>{failSecondPage=false;}};
}

test('手动刷新绕过评分依据缓存并更新结果、统计和候选人状态',async({page},testInfo)=>{
  await reviewWorkspace(page);const api=await mockJobScores(page);
  let updated=false;
  let release!:()=>void;
  const pending=new Promise<void>(resolve=>{release=resolve;});
  const reads:string[]=[];
  const writes:string[]=[];
  page.on('request',request=>{
    const url=new URL(request.url());
    if(!url.pathname.startsWith('/api/v1/'))return;
    if(request.method()==='GET')reads.push(url.pathname);
    else if(request.method()==='POST')writes.push(url.pathname);
  });
  await page.route('**/api/v1/match-jobs/jd-v2/results?**',async route=>{
    if(updated)await pending;
    const items=[pageResult('person-a','result-a','original-run',updated?81:73)];
    if(updated)items.push(pageResult('person-b','result-b','original-run',79));
    await route.fulfill({json:{items,hasMore:false,nextCursor:null}});
  });
  await page.route('**/api/v1/match-jobs/jd-v2/results-summary?**',route=>route.fulfill({json:{total:updated?2:1,strong:0,consider:0,reject:0,missing:updated?2:1}}));
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:[
    {candidateId:'person-a',candidateName:updated?'候选人甲（已更新）':'候选人甲',resumeVersionId:'resume-person-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]},
    {candidateId:'person-b',candidateName:'候选人乙',resumeVersionId:'resume-person-b',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]},
  ]}));
  await page.goto('/matching');
  const table=page.locator('.matching-score-table');
  await expect(table).toContainText('73.00');
  await expect(table).toContainText('候选人甲');
  const search=page.getByRole('textbox',{name:'搜索本页候选人'});
  await search.fill('候选人甲');
  const grade=page.getByRole('button',{name:'信息缺失',exact:true});
  await grade.click();
  await table.getByRole('checkbox').first().check();
  await expect(page.getByText('已选 1 人',{exact:true})).toBeVisible();
  const basesBefore=api.requests.filter(value=>value.includes('/bases?')).length;
  const readsBefore=reads.length;
  updated=true;
  const refresh=page.getByRole('button',{name:'刷新',exact:true});
  await refresh.focus();await page.keyboard.press('Enter');
  await expect(refresh).toHaveClass(/ant-btn-loading/);
  await expect(page.getByText('已选 0 人',{exact:true})).toBeVisible();
  release();
  await expect(table).toContainText('81.00');
  await expect(table).toContainText('候选人甲（已更新）');
  await expect(refresh).not.toHaveClass(/ant-btn-loading/);
  await expect(search).toHaveValue('候选人甲');
  await expect(grade).toHaveAttribute('aria-pressed','true');
  await expect(page.getByRole('button',{name:'简历总数',exact:true}).locator('strong')).toHaveText('2');
  expect(api.requests.filter(value=>value.includes('/bases?')).length).toBeGreaterThan(basesBefore);
  const refreshReads=reads.slice(readsBefore);
  for(const path of ['/api/v1/match-jobs','/api/v1/match-jobs/jd-v2/results','/api/v1/match-jobs/jd-v2/results-summary','/api/v1/match-runs/basis-a','/api/v1/screening-candidates','/api/v1/candidate-pool-statuses'])expect(refreshReads).toContain(path);
  expect(writes).toEqual([]);
  await refresh.scrollIntoViewIfNeeded();
  await expect(refresh).toBeInViewport();
  const bounds=(await refresh.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x+bounds.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await page.screenshot({path:testInfo.outputPath('screening-refresh.png'),fullPage:true});
});

test('精简评分界面保留当前结果与 AI 总结，不调用移除的接口',async({page})=>{
  await reviewWorkspace(page);await mockJobScores(page);
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  const removedRequests:string[]=[];
  page.on('request',request=>{const url=new URL(request.url());if(url.pathname.endsWith('/feedback')||url.pathname==='/api/v1/match-runs'&&request.method()==='GET')removedRequests.push(url.pathname);});
  await page.goto('/matching');
  await expect(page.getByText('候选人甲',{exact:true})).toBeVisible();
  for(const label of ['证据型评分台','评分记录','刷新评分','反馈判断','结果已冻结','公司默认评分结果'])await expect(page.getByText(label,{exact:true})).toHaveCount(0);
  await expect(page.locator('.screening-overview > p, .matching-result-ledger > header, .matching-result-actions')).toHaveCount(0);
  await page.getByLabel('匹配结果与人工决策').getByLabel('查看候选人的评分详情').click();
  await expect(page.getByRole('dialog')).toContainText('AI 总结');
  await expect(page.getByRole('dialog').locator('.matching-evidence-dimension')).toHaveCount(5);
  await expect(page.getByText('本次评分的人工反馈')).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByLabel('查看候选人的评分详情')).toBeFocused();
  await page.reload();
  await expect(page.getByText('候选人甲',{exact:true})).toBeVisible();
  expect(removedRequests).toEqual([]);
  expect(errors).toEqual([]);
});

test('统计卡切换与本页搜索叠加，清除后恢复全部候选人',async({page})=>{
  await reviewWorkspace(page);await mockJobScores(page);
  const rows=[pageResult('person-a','result-a','original-run',90),pageResult('person-b','result-b','original-run',75),pageResult('person-layout-1','result-c','original-run',50),pageResult('person-layout-2','result-d','original-run',99)];
  for(const item of rows)item.result.details.dimensions.push(...(['ROLE_EXPERIENCE','RELATED_YEARS','RESPONSIBILITY_MATCH','INDUSTRY_AND_PROJECTS'] as const).map(code=>({code,enabled:true,configuredWeight:10,status:'KNOWN',score:80,explanation:`${code} 已评分`,highlights:[]})));
  rows[3]!.result.details.dimensions[0]!.status='UNKNOWN';
  await page.route('**/api/v1/match-jobs/jd-v2/results?**',route=>route.fulfill({json:{items:rows,hasMore:false,nextCursor:null}}));
  await page.goto('/matching');
  const cards=page.getByRole('region',{name:'岗位全部候选人统计'});
  const table=page.locator('.matching-score-table');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(4);
  const clear=page.getByRole('button',{name:'清除筛选',exact:true});
  await expect(clear).toBeDisabled();
  const clearPosition=(await clear.boundingBox())!.x;
  for(const [label,name] of [['强推荐','候选人甲'],['可考虑','候选人乙'],['不推荐','布局候选人 2'],['信息缺失','布局候选人 3']]){
    const card=cards.getByRole('button',{name:label!,exact:true});
    await card.click();
    await expect(card).toHaveAttribute('aria-pressed','true');
    await expect(card).toHaveCSS('background-color','rgb(239, 246, 255)');
    await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(1);
    await expect(table).toContainText(name!);
  }
  const all=cards.getByRole('button',{name:'简历总数',exact:true});
  await all.focus();await page.keyboard.press('Enter');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(4);
  const search=page.getByRole('textbox',{name:'搜索本页候选人'});
  await search.fill('候选人乙');
  await expect(clear).toBeEnabled();
  expect((await clear.boundingBox())!.x).toBeCloseTo(clearPosition,0);
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(1);
  await page.getByRole('button',{name:'全选本页筛选结果',exact:true}).click();
  await expect(page.getByText('已选 1 人',{exact:true})).toBeVisible();
  await cards.getByRole('button',{name:'强推荐',exact:true}).click();
  await expect(page.getByText('已选 0 人',{exact:true})).toBeVisible();
  await expect(table).toContainText('本页没有符合条件的候选人');
  await clear.click();
  await expect(search).toHaveValue('');
  await expect(all).toHaveAttribute('aria-pressed','true');
  await expect(clear).toBeDisabled();
  await expect(page.getByText(/^本页显示/)).toHaveCount(0);
  await search.fill(' linux ');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(4);
});

test('少量评分按实际内容显示，不显示评分时间或固定十行留白',async({page})=>{
  await page.setViewportSize({width:1440,height:900});
  await reviewWorkspace(page);await mockJobScores(page,2);
  await page.goto('/matching');
  const table=page.locator('.matching-score-table');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(2);
  await expect(table.getByRole('columnheader',{name:'评分时间'})).toHaveCount(0);
  await expect(page.getByText(/每页10条/)).toHaveCount(0);
  expect((await table.locator('.ant-table-content').boundingBox())!.height).toBeLessThan(400);
  const footer=(await page.getByLabel('岗位结果分页',{exact:true}).boundingBox())!;
  expect(Math.abs(900-footer.y-footer.height-4)).toBeLessThanOrEqual(2);
});

test('岗位汇总全选完整遍历候选人分页，失败后保留原选择并可恢复',async({page})=>{
  await reviewWorkspace(page);const api=await mockJobScores(page);
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/matching?scoreJob=jd-v2&basis=basis-a');
  await expect(page.getByText('候选人甲',{exact:true})).toBeVisible();
  const selectAll=page.getByRole('button',{name:/全选此依据全部候选人/});
  await selectAll.click();
  await expect(page.getByText('下一页暂不可用',{exact:true})).toBeVisible();
  await expect(page.getByText('已选 0 人',{exact:true})).toBeVisible();
  await expect(selectAll).not.toHaveClass(/ant-btn-loading/);
  api.recover();
  await selectAll.click();
  await expect(page.getByText('已选 2 人',{exact:true})).toBeVisible();
  expect(api.requests.some(value=>value.includes('/results?limit=20&basisRunId=basis-a&cursor=next'))).toBe(true);
  expect(errors).toEqual([]);
});

test('评分结果长表保留内部滚动、服务器游标和分页回退',async({page})=>{
  await page.setViewportSize({width:1440,height:900});
  await reviewWorkspace(page);const api=await mockJobScores(page,20);api.recover();
  await page.goto('/matching');
  const table=page.locator('.matching-score-table .ant-table-content');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(20);
  expect(await table.evaluate(node=>node.scrollHeight>node.clientHeight)).toBe(true);
  await table.scrollIntoViewIfNeeded();
  await table.evaluate(node=>{node.scrollTop=node.scrollHeight;});
  await expect(table.locator('tbody tr.ant-table-row').last()).toBeInViewport();
  const footer=page.getByLabel('岗位结果分页',{exact:true});
  await footer.scrollIntoViewIfNeeded();
  const next=footer.getByRole('button',{name:'下一页结果',exact:true});
  const previous=footer.getByRole('button',{name:'上一页结果',exact:true});
  await expect(next).toBeInViewport();
  await next.click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  await expect(table).toContainText('候选人乙');
  await expect(next).toBeDisabled();
  expect(api.requests.some(value=>value.includes('basisRunId=basis-a&cursor=next'))).toBe(true);
  await previous.click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(20);
  await expect(previous).toBeDisabled();
  await expect(next).toBeEnabled();
});

test('主页面移除重复岗位与依据容器，评分表内容不会越过单元格或容器',async({page})=>{
  await page.setViewportSize({width:1280,height:900});
  await reviewWorkspace(page);await mockJobScores(page,8);
  await page.goto('/matching?scoreJob=jd-v2&basis=basis-a');
  await expect(page.locator('.matching-job-table, .matching-basis-table')).toHaveCount(0);
  await expect(page.getByText('已加入智能筛选',{exact:true})).toHaveCount(0);

  const ledger=page.getByLabel('匹配结果与人工决策');
  await ledger.scrollIntoViewIfNeeded();
  const tableWrapper=ledger.locator('.ant-table-wrapper');
  await expect(tableWrapper).toHaveCSS('overflow-x','hidden');
  const overflowCount=await ledger.locator('.matching-evidence-trigger small, .matching-summary').evaluateAll(elements=>elements.filter(element=>{
    const cell=element.closest('td');
    if(!cell)return false;
    const content=element.getBoundingClientRect();
    const boundary=cell.getBoundingClientRect();
    return content.left<boundary.left-1||content.right>boundary.right+1;
  }).length);
  expect(overflowCount).toBe(0);
});

for(const width of [390,768,1280,1440,1920])test(`岗位评分汇总在 ${width}px 可访问且页面无横向溢出`,async({page},testInfo)=>{
  await page.setViewportSize({width,height:900});
  await reviewWorkspace(page);await mockJobScores(page,8);
  await page.goto('/matching');
  const results=page.getByLabel('匹配结果与人工决策');
  await expect(results).toBeVisible();
  const header = page.getByRole('region', { name: '当前筛选岗位' });
  const tags = header.locator('.screening-job-tags');
  await expect(tags.locator('.ant-tag').first()).toBeVisible();
  await expect(header).not.toContainText('岗位 JD 摘要');
  const mainBox = (await page.locator('.app-main').boundingBox())!;
  const workspaceBox = (await page.locator('.matching-workspace-page').boundingBox())!;
  expect(workspaceBox.y - mainBox.y).toBe(8);
  const headerBox = (await header.boundingBox())!;
  const metricsBox = (await page.getByLabel('岗位全部候选人统计').boundingBox())!;
  expect(metricsBox.y - headerBox.y - headerBox.height).toBeLessThanOrEqual(20);
  const resultsBox = (await results.boundingBox())!;
  expect(resultsBox.y - metricsBox.y - metricsBox.height).toBeLessThanOrEqual(9);
  await page.screenshot({path:testInfo.outputPath('compact-header.png'),animations:'disabled'});
  await expect(page.getByRole('button',{name:'维护个人方案',exact:true})).toHaveCount(1);
  await expect(page.getByRole('button',{name:'重新评分',exact:true})).toHaveCount(1);
  await expect(page.locator('.matching-toolstrip')).toHaveCount(0);
  await expect(page.locator('.matching-basis-table, .matching-job-table')).toHaveCount(0);
  await expect(results.locator('xpath=ancestor::*[@aria-busy][1]')).toHaveAttribute('aria-busy','false');
  await expect(page.getByRole('button',{name:'下一页结果',exact:true})).toBeEnabled();
  const search=results.getByRole('textbox',{name:'搜索本页候选人'});
  await expect(search).toBeVisible();
  if(width>=1440){
    const searchBox=(await search.boundingBox())!;
    const actionsBox=(await results.getByLabel('匹配结果批量操作').boundingBox())!;
    expect(searchBox.x).toBeGreaterThan(actionsBox.x+actionsBox.width);
    expect(Math.abs(searchBox.y+searchBox.height/2-actionsBox.y-actionsBox.height/2)).toBeLessThanOrEqual(2);
  }
  if(width>=1280){
    const content=results.locator('.ant-table-content');
    expect(await content.evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
    await expect(results.locator('thead th')).toHaveCount(14);
    const clipped=await results.locator('tbody tr.ant-table-row').first().locator('td').evaluateAll(cells=>cells.filter(cell=>cell.scrollWidth>cell.clientWidth+1).length);
    expect(clipped).toBe(0);
    const summary=results.locator('.matching-summary').first();
    await expect(summary).toContainText('客户运维支持经验');
    expect(await summary.evaluate(node=>node.scrollHeight-node.clientHeight)).toBeLessThanOrEqual(1);
    await expect(results.locator('.ant-table-cell-fix-right')).toHaveCount(0);
  }
  const footer=page.getByLabel('岗位结果分页',{exact:true});
  await page.locator('.matching-workspace-page').evaluate(node=>{node.scrollTop=node.scrollHeight;});
  const footerBox=(await footer.boundingBox())!;
  const bottom=width<=900?(await page.locator('.mobile-bottom-nav').boundingBox())!.y:900;
  expect(Math.abs(bottom-footerBox.y-footerBox.height-4)).toBeLessThanOrEqual(2);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({page}).include('.app-main').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('job-score-workspace.png'),animations:'disabled'});
  await results.scrollIntoViewIfNeeded();
  await results.locator('.ant-table-content').evaluate(element=>{element.scrollLeft=element.scrollWidth;});
  const [viewportBox,actionBox]=await Promise.all([results.locator('.ant-table-content').boundingBox(),results.locator('tbody tr').first().locator('td').last().boundingBox()]);
  expect(viewportBox).not.toBeNull();expect(actionBox).not.toBeNull();
  expect.soft(actionBox!.x).toBeGreaterThanOrEqual(viewportBox!.x-1);
  expect.soft(actionBox!.x+actionBox!.width).toBeLessThanOrEqual(viewportBox!.x+viewportBox!.width+1);
  await page.screenshot({path:testInfo.outputPath('job-score-table-detail.png'),animations:'disabled'});
});

test('旧版未评和信息不足维度不呈现零分或失败进度',async({page},testInfo)=>{
  await reviewWorkspace(page);await mockJobScores(page);
  await page.route('**/api/v1/match-jobs/jd-v2/results**',route=>{
    const item=pageResult('person-a','result-a','original-run');
    item.result.details.dimensions.push(
      {code:'ROLE_EXPERIENCE',enabled:false,configuredWeight:18,status:'KNOWN',score:0,explanation:'岗位未设置经历要求，本维度不参与评分。',highlights:[]},
      {code:'RELATED_YEARS',enabled:false,configuredWeight:12,status:'UNKNOWN',score:0,explanation:'岗位不设年限门槛。',highlights:[]},
      {code:'RESPONSIBILITY_MATCH',enabled:true,configuredWeight:23,status:'UNKNOWN',score:0,explanation:'职责信息待补充。',highlights:[]},
    );
    return route.fulfill({json:{items:[item],hasMore:false,nextCursor:null}});
  });
  await page.goto('/matching');
  const table=page.locator('.matching-score-table');
  await expect(table.getByText('信息不足',{exact:true})).toBeVisible();
  await expect(table.getByText('旧版未评·请重评',{exact:true})).toHaveCount(3);
  await expect(table.getByRole('img',{name:/职责匹配贡献|岗位经历贡献|相关年限贡献/})).toHaveCount(0);
  await page.getByLabel('查看候选人的评分详情').click();
  const dialog=page.getByRole('dialog');
  for(const title of ['岗位经历','相关年限']) {
    const card=dialog.locator('.matching-evidence-dimension').filter({has:page.getByText(title,{exact:true})});
    await expect(card).toContainText('旧版未评·请重评');
    await expect(card).not.toContainText('分贡献');
    await expect(card).not.toContainText('贡献 0 分');
  }
  await expect(dialog).toContainText('岗位未设置经历要求，本维度不参与评分。');
  await expect(dialog).toContainText('岗位不设年限门槛。');
  const responsibility=dialog.locator('.matching-evidence-dimension').filter({has:page.getByText('职责匹配',{exact:true})});
  await expect(responsibility).toContainText('信息不足');
  await expect(responsibility).toContainText('职责信息待补充。');
  await expect(dialog).not.toContainText('贡献 0 分');
  await dialog.locator('.matching-evidence-dimension').filter({has:page.getByText('岗位经历',{exact:true})}).scrollIntoViewIfNeeded();
  await page.screenshot({path:testInfo.outputPath('dimension-participation.png'),animations:'disabled'});
});

for (const width of [390, 1440]) test(`长匹配说明保持两行并可悬停或键盘查看全文 ${width}`, async ({page},testInfo) => {
  await page.setViewportSize({width,height:1000});
  await reviewWorkspace(page);await mockJobScores(page);
  const text='数据中心运维：负责服务器、网络、故障排查与客户支持。'.repeat(12)+'完整说明结尾';
  const item=pageResult('person-a','result-a','original-run');
  item.result.details.dimensions[0]!.highlights=[text];
  await page.route('**/api/v1/match-jobs/jd-v2/results?*',route=>route.fulfill({json:{items:[item],hasMore:false,nextCursor:null}}));
  await page.goto('/matching');
  const row=page.locator('.matching-score-table tr.ant-table-row').first();
  const trigger=row.getByRole('button',{name:'查看完整匹配说明'});
  await expect(trigger).toBeVisible();
  const preview=trigger.locator('.match-summary-preview');
  expect((await preview.boundingBox())!.height).toBeLessThanOrEqual(40);
  const height=(await row.boundingBox())!.height;
  await trigger.hover();
  const panel=page.getByRole('region',{name:'完整匹配说明'});
  await expect(panel.getByText(text,{exact:true})).toBeVisible();
  expect((await row.boundingBox())!.height).toBe(height);
  await expect.poll(async()=>{const box=(await panel.boundingBox())!;return box.x>=0 && box.x+box.width<=width;}).toBe(true);
  await page.locator('.ant-popover').evaluateAll(async elements=>{await Promise.all(elements.flatMap(element=>element.getAnimations({subtree:true})).map(animation=>animation.finished.catch(()=>undefined)));});
  await panel.screenshot({path:testInfo.outputPath(`summary-${width}.png`)});
  await panel.getByRole('button',{name:'收起'}).click();
  await expect(trigger).toHaveAttribute('aria-expanded','false');
  await trigger.focus();await page.keyboard.press('Enter');
  await expect(panel).toBeVisible();
  await page.locator('.ant-popover').evaluateAll(async elements=>{await Promise.all(elements.flatMap(element=>element.getAnimations({subtree:true})).map(animation=>animation.finished.catch(()=>undefined)));});
  expect((await new AxeBuilder({page}).include('.match-summary-panel').analyze()).violations).toEqual([]);
  await panel.locator('p').focus();await page.keyboard.press('Escape');
  await expect(trigger).toHaveAttribute('aria-expanded','false');
  await expect(trigger).toBeFocused();
  await expect(panel).not.toBeVisible();
  await page.screenshot({path:testInfo.outputPath(`ledger-${width}.png`),animations:'disabled'});
});

for(const width of [390,1440])test(`查看简历在智能筛选原页打开统一详情并恢复操作状态 ${width}`,async({page},testInfo)=>{
  await page.setViewportSize({width,height:900});
  await reviewWorkspace(page);
  const scores=await mockJobScores(page);
  scores.recover();
  await page.route('**/api/v1/candidates/*/resume-versions?**',route=>route.fulfill({json:{items:[{id:'resume-person-b',resumeId:'resume-b',intakeId:'intake-b',versionNo:1,originalFileName:'候选人乙.pdf',mediaType:'application/pdf',sizeBytes:100,status:'ACTIVE',createdAt:'2026-09-07T01:00:00Z'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/resume-versions/resume-person-b/facts',route=>route.fulfill({json:{snapshot:{id:'facts-b',resumeVersionId:'resume-person-b',fullName:'候选人乙',email:'candidate.with.a.long.email.address@example.invalid',phone:'13800001234',sourceType:'AI_PARSE',currentRole:'运维工程师'},workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[{id:'risk-date',factType:'UNKNOWN',nameValue:'工作时间需要核对'},{id:'risk-project',factType:'UNKNOWN',nameValue:'项目职责需要核对'},{id:'risk-skill',factType:'UNKNOWN',nameValue:'技能描述需要核对'}]}}));
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/matching');
  await expect(page.getByText('联系方式见简历',{exact:true})).toHaveCount(0);
  const footer=page.getByLabel('岗位结果分页',{exact:true});
  await footer.getByRole('button',{name:'下一页结果',exact:true}).click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  const results=page.getByLabel('匹配结果与人工决策');
  const search=results.getByRole('textbox',{name:'搜索本页候选人'});
  await search.fill('候选人乙');
  await results.locator('tbody').getByRole('checkbox').check();
  const trigger=results.getByRole('button',{name:/查看简历$/});
  const url=page.url();
  await trigger.click();
  const dialog=page.getByRole('dialog',{name:'候选人乙',exact:true});
  await expect(dialog.getByRole('region',{name:'关键资料'})).toBeVisible();
  await expect(dialog.getByRole('button',{name:'查看面试材料'})).toBeVisible();
  await expect(dialog.getByText('AI 事实解析',{exact:true})).toBeVisible();
  await expect(dialog.getByText('运维工程师',{exact:true})).toBeVisible();
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
  expect(page.url()).toBe(url);
  const verifyLayout=async()=>{
    await expect(dialog.getByText('3 项待核实',{exact:true})).toBeVisible();
    const summary=dialog.locator('.candidate-detail-summary');
    const materials=(await dialog.getByRole('button',{name:'查看面试材料'}).boundingBox())!;
    const correction=(await dialog.getByRole('button',{name:'HR 更正',exact:true}).boundingBox())!;
    const facts=(await summary.locator('.ant-descriptions').boundingBox())!;
    const blacklist=(await summary.locator('.candidate-blacklist-details').boundingBox())!;
    if(width>=768){
      expect(Math.abs(materials.y-correction.y)).toBeLessThanOrEqual(2);
      const heading=(await summary.locator(':scope > .ant-card-head .ant-card-head-title').boundingBox())!;
      expect(materials.x-heading.x-heading.width).toBeGreaterThanOrEqual(8);
      expect(materials.x-heading.x-heading.width).toBeLessThanOrEqual(24);
      expect(Math.abs(materials.y+materials.height/2-heading.y-heading.height/2)).toBeLessThanOrEqual(2);
      expect(blacklist.x).toBeGreaterThanOrEqual(facts.x+facts.width);
      expect(Math.abs(blacklist.y-facts.y)).toBeLessThanOrEqual(2);
    }else expect(blacklist.y).toBeGreaterThanOrEqual(facts.y+facts.height);
    const summaryBox=(await summary.boundingBox())!;
    const analysis=(await dialog.locator('.candidate-detail-analysis').boundingBox())!;
    expect(analysis.y-summaryBox.y-summaryBox.height).toBeLessThanOrEqual(16);
    const reparse=(await dialog.getByRole('button',{name:'重新解析',exact:true}).boundingBox())!;
    expect(reparse.y-analysis.y).toBeGreaterThanOrEqual(12);
    expect(reparse.y-analysis.y).toBeLessThanOrEqual(20);
    expect(await dialog.evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
  };
  await verifyLayout();
  await page.screenshot({path:testInfo.outputPath(`screening-resume-${width}.png`),animations:'disabled'});
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await expect(search).toHaveValue('候选人乙');
  await expect(results.locator('tbody').getByRole('checkbox')).toBeChecked();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  expect(page.url()).toBe(url);
  await page.goto('/talents?candidate=person-b');
  await expect(dialog.getByText('AI 事实解析',{exact:true})).toBeVisible();
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
  await verifyLayout();
  await page.screenshot({path:testInfo.outputPath(`talent-resume-${width}.png`),animations:'disabled'});
  expect(errors).toEqual([]);
});

test('智能筛选原页简历详情保留候选人权限拒绝',async({page})=>{
  await reviewWorkspace(page);await mockJobScores(page);
  await page.route('**/api/v1/candidates/person-a',route=>route.fulfill({status:403,json:{title:'无权查看',status:403,detail:'无权查看',code:'FORBIDDEN'}}));
  await page.goto('/matching');
  await page.getByRole('button',{name:/查看简历$/}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog).toContainText('暂无该候选人的查看权限');
  await expect(dialog.getByRole('region',{name:'关键资料'})).toHaveCount(0);
  await expect(page).toHaveURL(/\/matching/);
});

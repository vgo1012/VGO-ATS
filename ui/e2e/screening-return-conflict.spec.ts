import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

async function conflictWorkspace(page:Page){
  await reviewWorkspace(page);
  const empty={items:[],hasMore:false,nextCursor:null};
  const people=[
    {candidateId:'person-a',name:'林雨桐',resumeVersionId:'resume-a',resultId:'result-a',admissionId:'admission-a'},
    {candidateId:'person-b',name:'周明远',resumeVersionId:'resume-b',resultId:'result-b',admissionId:'admission-b'},
  ];
  let stale=false,returnPosts=0,admissionReads=0;
  await page.route('**/api/v1/**',async route=>{
    const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
    const list=(items:unknown[])=>route.fulfill({json:{...empty,items}});
    if(path==='/screening-candidates'){
      admissionReads++;
      return route.fulfill({json:people.map((person,index)=>({candidateId:person.candidateId,resumeVersionId:person.resumeVersionId,jdVersionId:'jd-v2',defaultMatchRunId:'run-a',defaultResult:{professionalScore:88-index*6,bonusScore:0,referenceScore:88-index*6,mustTier:'PASS'},admissionStatus:stale&&index===0?'READY':'ADMITTED',admissionId:stale&&index===0?null:person.admissionId,admissionVersion:stale&&index===0?null:'3',admittedAt:'2026-09-10T00:00:00Z'}))});
    }
    if(path==='/screening-admissions/return'){
      returnPosts++;
      expect(route.request().postDataJSON()).toEqual({items:[{admissionId:'admission-a',expectedVersion:'3'}]});
      stale=true;
      return route.fulfill({status:409,json:{type:'about:blank',title:'筛选状态已变化',status:409,detail:'候选人的筛选准入版本已变化，请刷新后重新选择。',code:'SCREENING_ADMISSION_VERSION_CONFLICT',recoverable:true}});
    }
    if(path==='/match-runs/run-a')return route.fulfill({json:{id:'run-a',jdVersionId:'jd-v2',status:'SUCCEEDED',totalCount:2,succeededCount:2,failedCount:0}});
    if(path==='/match-runs/run-a/results')return list(people.map((person,index)=>({resultId:person.resultId,baseEvaluationId:`base-${index}`,candidateId:person.candidateId,resumeVersionId:person.resumeVersionId,stableRank:index+1,professionalScore:88-index*6,bonusScore:0,referenceScore:88-index*6,mustTier:'PASS',details:{dimensions:[],criteria:[],selectedBonusCap:0}})));
    if(path==='/candidate-batch')return route.fulfill({json:people.filter(person=>url.searchParams.getAll('candidateIds').includes(person.candidateId)).map(person=>({id:person.candidateId,fullName:person.name,ownerUserId:'hr-a'}))});
    if(path==='/candidate-pool-statuses')return route.fulfill({json:people.map(person=>({candidateId:person.candidateId,candidateName:person.name,resumeVersionId:person.resumeVersionId,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'高级前端工程师',versionNo:2,resumeVersionId:person.resumeVersionId,status:'AI_SCREENED',stage:null,applicationId:null,matchRunId:'run-a'}]}))});
    if(path==='/jd-versions/jd-v2/facts')return route.fulfill({json:{facts:{title:'高级前端工程师'}}});
    if(path==='/jd-versions/jd-v2/recruitment-processing-access')return route.fulfill({json:{processingAllowed:true}});
    return route.fallback();
  });
  return {returnPosts:()=>returnPosts,admissionReads:()=>admissionReads};
}

test('退回版本冲突后只刷新准入状态并保留选择，随后可重新选择合资格候选人',async({page},testInfo)=>{
  await page.setViewportSize({width:testInfo.project.name==='mobile'?390:1440,height:900});
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  const api=await conflictWorkspace(page);
  await page.goto('/matching?run=run-a');
  const firstRow=page.getByRole('row').filter({hasText:'林雨桐'});
  await firstRow.getByRole('checkbox').click();
  await page.getByRole('button',{name:'批量退回我的简历'}).click();
  const dialog=page.getByRole('dialog',{name:'退回我的简历'});
  await dialog.getByRole('button',{name:'确认退回'}).click();
  await expect(dialog.getByRole('alert')).toContainText('筛选状态已变化');
  await page.getByRole('button',{name:'关闭错误提示'}).click();
  await expect(dialog.getByRole('button',{name:'刷新筛选状态'})).toBeEnabled();
  await expect(dialog).not.toHaveClass(/appear|enter/);
  const readsBeforeRefresh=api.admissionReads();
  await page.screenshot({path:testInfo.outputPath('screening-return-version-conflict.png'),fullPage:true});
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);

  await dialog.getByRole('button',{name:'刷新筛选状态'}).click();
  await expect(dialog).toHaveCount(0);
  await expect.poll(()=>api.admissionReads()).toBeGreaterThan(readsBeforeRefresh);
  expect(api.returnPosts()).toBe(1);
  await expect(page.getByText('已选 1 人')).toBeVisible();
  await expect(firstRow.getByRole('button',{name:'退回我的简历'})).toBeDisabled();

  await page.getByRole('button',{name:'清空选择'}).click();
  const eligibleRow=page.getByRole('row').filter({hasText:'周明远'});
  await eligibleRow.getByRole('checkbox').click();
  await expect(page.getByRole('button',{name:'批量退回我的简历'})).toBeEnabled();
  expect(api.returnPosts()).toBe(1);
  expect(errors).toEqual([]);
});

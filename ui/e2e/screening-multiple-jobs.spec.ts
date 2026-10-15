import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

for(const width of [390,1280,1440,1920]) {
  for(const bothScored of [true,false]) {
    test(`筛选岗位A后${bothScored?'只准入A':'不准入仅有评分的B'} ${width}`,async({page})=>{
      await page.setViewportSize({width,height:900});
      await reviewWorkspace(page);
      const person={id:'person-a',fullName:'多岗位候选人',ownerUserId:'hr-a',version:1};
      const jobs=['a','b'].map(id=>({id:`jd-${id}`,title:`岗位${id.toUpperCase()}`,currentVersionNo:1}));
      const contexts=(bothScored?['a','b']:['b']).map(id=>({candidateId:person.id,resumeVersionId:'resume-a',jdVersionId:`version-${id}`,defaultMatchRunId:`run-${id}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null}));
      const posts:unknown[]=[];
      const errors:string[]=[];
      page.on('pageerror',error=>errors.push(error.message));
      await page.route('**/api/v1/**',async route=>{
        const path=new URL(route.request().url()).pathname.replace('/api/v1','');
        const list=(items:unknown[])=>route.fulfill({json:{items,hasMore:false,nextCursor:null}});
        if(path==='/jds')return list(jobs);
        if(path==='/candidates')return list([person]);
        if(path==='/candidates/person-a')return route.fulfill({json:person});
        if(path==='/candidate-batch')return route.fulfill({json:new URL(route.request().url()).searchParams.getAll('candidateIds').includes(person.id)?[person]:[]});
        if(path==='/talent-search')return list([{candidateId:person.id}]);
        if(path==='/candidate-pool-statuses')return route.fulfill({json:[{candidateId:person.id,resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:jobs.map(job=>({jdId:job.id,jdVersionId:`version-${job.id.slice(-1)}`,title:job.title,versionNo:1,resumeVersionId:'resume-a',status:'PENDING',stage:null,applicationId:null,matchRunId:null}))}]});
        if(path==='/screening-candidates')return route.fulfill({json:contexts});
        if(path==='/screening-admissions'){
          posts.push(route.request().postDataJSON());
          return route.fulfill({json:{requestedCount:1,admittedCount:1,alreadyAdmittedCount:0,items:[]}});
        }
        return route.fallback();
      });
      await page.goto('/talents');
      await page.getByRole('combobox',{name:'求职岗位（选择 JD）'}).click();
      const jobOption=page.getByRole('option',{name:/^岗位A · V1/});
      await jobOption.click();
      await page.keyboard.press('Escape');
      await expect(jobOption).not.toBeVisible();
      await expect(page.getByRole('button',{name:person.fullName,exact:true})).toBeVisible();
      const selectAll=page.getByRole('checkbox',{name:'全选本页候选人'});
      await expect(selectAll).toBeEnabled();
      await selectAll.click();
      await expect(selectAll).toBeChecked();
      expect((await new AxeBuilder({page}).include('.talent-ledger-card').analyze()).violations).toEqual([]);
      expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).focus();
      await page.keyboard.press('Enter');
      if(bothScored){
        await expect(page).toHaveURL(/\/matching\?scoreJob=version-a$/);
        expect(posts).toEqual([{items:[{candidateId:person.id,resumeVersionId:'resume-a',jdVersionId:'version-a',defaultMatchRunId:'run-a'}]}]);
      }else{
        await expect(page.getByText('所选简历尚未完成公司默认评分，请刷新后重试',{exact:true})).toBeVisible();
        expect(posts).toEqual([]);
        await expect(page).toHaveURL(/\/talents\?pool_jd=jd-a$/);
        await expect(page.getByRole('checkbox',{name:'全选本页候选人'})).toBeChecked();
      }
      expect(errors).toEqual([]);
    });
  }
}

for(const width of [390,1280,1440,1920]) {
  test(`多岗位简历按各自 JD 加入智能筛选 ${width}`,async({page})=>{
    await page.setViewportSize({width,height:900});
    await reviewWorkspace(page);
    const people=['a','b'].map(id=>({id:`person-${id}`,fullName:`候选人${id}`,ownerUserId:'hr-a',version:1}));
    const contexts=people.map((person,index)=>({candidateId:person.id,resumeVersionId:`resume-${index}`,jdVersionId:`jd-version-${index}`,defaultMatchRunId:`run-${index}`,defaultResult:{professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS'},admissionStatus:'READY',admissionId:null,admissionVersion:null,admittedAt:null}));
    const posts:unknown[]=[];
    const scoringPosts:string[]=[];
    const errors:string[]=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/api/v1/**',async route=>{
      const request=route.request();
      const path=new URL(request.url()).pathname.replace('/api/v1','');
      const list=(items:unknown[])=>route.fulfill({json:{items,hasMore:false,nextCursor:null}});
      if(request.method()==='POST'&&path==='/match-runs')scoringPosts.push(path);
      if(path==='/candidates')return list(posts.length?[]:people);
      if(path==='/talent-search')return list(posts.length?[]:people.map(person=>({candidateId:person.id})));
      if(path==='/candidate-pool-statuses')return route.fulfill({json:contexts.map((context,index)=>({candidateId:context.candidateId,resumeVersionId:context.resumeVersionId,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:`jd-${index}`,jdVersionId:context.jdVersionId,title:`岗位${index}`,versionNo:1,resumeVersionId:context.resumeVersionId,status:'AI_SCREENED',stage:null,applicationId:null,matchRunId:context.defaultMatchRunId}]}))});
      if(path==='/screening-candidates')return route.fulfill({json:contexts});
      if(path==='/screening-admissions'){
        posts.push(request.postDataJSON());
        return route.fulfill({json:{requestedCount:2,admittedCount:2,alreadyAdmittedCount:0,items:[]}});
      }
      return route.fallback();
    });
    await page.goto('/talents');
    await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
    expect((await new AxeBuilder({page}).include('.talent-ledger-card').analyze()).violations).toEqual([]);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/matching$/);
    expect(posts).toEqual([{items:contexts.map(({candidateId,resumeVersionId,jdVersionId,defaultMatchRunId})=>({candidateId,resumeVersionId,jdVersionId,defaultMatchRunId}))}]);
    await page.goto('/talents');
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    expect(scoringPosts).toEqual([]);
    expect(errors).toEqual([]);
  });
}

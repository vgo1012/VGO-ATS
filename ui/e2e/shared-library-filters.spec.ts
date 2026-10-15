import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

test('共享库50人只批量读取候选人基本资料',async({page})=>{
  await reviewWorkspace(page);
  const ids=Array.from({length:50},(_,index)=>`synthetic-${index}`);
  const details:string[]=[];
  const batches:string[][]=[];
  page.on('request',request=>{if(/\/api\/v1\/candidates\//.test(request.url()))details.push(request.url());});
  await page.route('**/api/v1/talent-search?*',route=>route.fulfill({json:{items:ids.map(candidateId=>({candidateId,fullName:candidateId,skills:[],conditionMatches:[],engagementLock:null})),hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidate-batch?*',route=>{
    batches.push(new URL(route.request().url()).searchParams.getAll('candidateIds'));
    return route.fulfill({json:ids.map(id=>({id,ownerUserId:'hr-a'}))});
  });
  await page.goto('/shared-library');
  await expect(page.locator('tr[data-row-key="synthetic-49"]')).toContainText('陈佳');
  expect(batches).toEqual([ids]);
  expect(details).toEqual([]);
});

test('共享库按钮筛选、关键词和刷新恢复，移除高级搜索及批量推进入口',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const queries:URLSearchParams[]=[];const removedRequests:string[]=[];
  await page.route('**/api/v1/jds?*',route=>route.fulfill({json:{items:[
    {id:'jd-other-new',code:'JD-100',title:'其他人新岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-b',ownerDepartmentId:'dept-b',createTime:'2026-09-20T00:00:00Z'},
    {id:'jd-a',code:'JD-9',title:'高级前端工程师',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:2,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-09T00:00:00Z'},
    {id:'jd-other-old',code:'JD-20',title:'其他人旧岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-b',ownerDepartmentId:'dept-b',createTime:'2026-09-08T00:00:00Z'},
    {id:'jd-mine-new',code:'JD-10',title:'我的新岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-10T00:00:00Z'},
  ],hasMore:false,nextCursor:null}}));
  page.on('request',request=>{if(request.url().includes('filter-templates'))removedRequests.push(request.url());});
  await page.route('**/api/v1/talent-search?*',route=>{queries.push(new URL(route.request().url()).searchParams);return route.fulfill({json:{items:[{candidateId:'shared-a',fullName:'测试候选人',email:'sample@example.test',currentRole:'Java工程师',skills:['Java','Spring','MySQL','Redis','Docker','Kubernetes','Git','Linux','Terraform','Nginx'],conditionMatches:[],workYears:2,engagementLock:null}],hasMore:false,nextCursor:null}});});
  await page.route('**/api/v1/candidate-resume-summaries?*',route=>route.fulfill({json:[{candidateId:'shared-a',resumeVersion:{createdAt:'2026-09-06T01:00:00Z'},source:{sourceChannelName:'内推'},facts:{educations:[{degreeName:'本科'}]},factsStatus:'READY'}]}));
  await page.route('**/api/v1/candidate-pool-statuses?*',route=>route.fulfill({json:[{candidateId:'shared-a',resumeVersionId:'resume-a',libraryEligible:true,jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'resume-a',title:'Java工程师',versionNo:2,status:'FINISHED',stage:'REJECTED',applicationId:'shared-app'}]}]}));
  await page.route('**/api/v1/candidate-batch?*',route=>route.fulfill({json:[{id:'shared-a',ownerUserId:'hr-a'}]}));
  await page.route('**/api/v1/applications/shared-app', route=>route.fulfill({status:403,json:{status:403,detail:'招聘详情需授权'}}));
  await page.goto('/shared-library');
  await expect(page.getByRole('checkbox', { name: '全选本页候选人', exact: true })).toBeVisible();
  for(const name of ['候选人','年龄','历史关联岗位','工作年限','学历','招聘流程','核心技能','来源 / 归属人','简历更新 / 解析','操作'])await expect(page.getByRole('columnheader',{name,exact:true})).toBeVisible();
  await expect(page.getByText('候选人结果 · 本页 1')).toHaveCount(0);
  const row=page.locator('tr[data-row-key="shared-a"]');
  for(const text of ['Java工程师','本科','内推','陈佳'])await expect(row).toContainText(text);
  await row.getByRole('button',{name:/Java工程师 ·/}).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog',{name:'招聘活动详情',exact:true})).toBeVisible();
  await expect(page).toHaveURL(/shared-library/);
  await expect(page.getByRole('button',{name:'重试招聘详情'})).toBeVisible();
  await page.getByRole('dialog',{name:'招聘活动详情',exact:true}).getByRole('button',{name:'关闭',exact:true}).click();
  await expect(row.getByRole('button',{name:/Java工程师 ·/})).toBeFocused();
  for(const text of ['共享简历库','可联系','已解析'])await expect(row.getByText(text,{exact:true})).toHaveCount(0);
  await expect(row.locator('.talent-skills-more')).toHaveCount(0);
  await expect(row.locator('.candidate-skills-preview > span')).toHaveCount(8);
  await expect(row.getByText('Terraform',{exact:true})).toHaveCount(0);
  const height=(await row.boundingBox())!.height;
  const more=row.getByRole('button',{name:'查看全部 10 项技能'});
  await expect(more).toHaveText('+2 项');
  await more.click();
  const skills=page.getByRole('region',{name:'全部核心技能'});
  await expect(skills.getByText('Terraform',{exact:true})).toBeVisible();
  expect((await row.boundingBox())!.height).toBe(height);
  await skills.getByRole('button',{name:'收起',exact:true}).click();
  await expect(more).toHaveAttribute('aria-expanded','false');
  await page.setViewportSize({width:390,height:1000});
  await more.click();
  await expect(skills.getByText('Kubernetes',{exact:true})).toBeVisible();
  expect((await row.boundingBox())!.height).toBeLessThan(150);
  await skills.getByRole('button',{name:'收起',exact:true}).click();
  await page.setViewportSize({width:1440,height:1000});
  const job=page.getByRole('combobox',{name:'求职岗位（选择 JD）'});
  await job.click();
  await expect.poll(()=>page.locator('.jd-selector-popup .ant-select-item-option-content').allTextContents()).toEqual([
    expect.stringContaining('JD-10'),expect.stringContaining('JD-9'),expect.stringContaining('JD-100'),expect.stringContaining('JD-20'),
  ]);
  await page.locator('.ant-select-item-option-content').filter({hasText:'高级前端工程师'}).click();
  await expect.poll(()=>queries.at(-1)?.get('jdId')).toBe('jd-a');
  await page.reload();
  await expect.poll(()=>queries.at(-1)?.get('jdId')).toBe('jd-a');
  await page.getByRole('button',{name:'清空岗位筛选'}).click();
  await expect.poll(()=>queries.at(-1)?.has('jdId')).toBe(false);
  await expect(page.getByText('查找已结束个人筛选、当前没有招聘流程的候选人，并核对可解释命中依据。')).toHaveCount(0);
  await page.getByRole('textbox',{name:'共享简历关键词搜索',exact:true}).fill('Java');
  const form=page.getByRole('form',{name:'简历筛选条件'});
  await form.getByRole('button',{name:'本科',exact:true}).click();
  await expect.poll(()=>queries.some(query=>query.getAll('degrees').includes('本科'))).toBe(true);
  await form.getByRole('button',{name:'1-3 年',exact:true}).click();
  await expect.poll(()=>queries.some(query=>query.get('maxWorkYears')==='3')).toBe(true);
  await form.getByRole('button',{name:'25-30',exact:true}).click();
  await expect.poll(()=>queries.some(query=>query.get('maxAge')==='29')).toBe(true);
  await form.getByRole('button',{name:'女',exact:true}).click();
  await expect(form.getByRole('button',{name:/^筛\s*选$/})).toHaveCount(0);
  await expect.poll(()=>queries.at(-1)?.get('q')).toBe('Java');
  await expect.poll(()=>queries.some(query=>query.get('q')==='Java'&&query.getAll('degrees').includes('本科')&&query.get('maxWorkYears')==='3'&&query.get('maxAge')==='29'&&query.get('gender')==='FEMALE')).toBe(true);
  await page.reload();
  await expect(form.getByRole('button',{name:'本科',exact:true})).toHaveAttribute('aria-pressed','true');
  for(const label of ['可解释人才搜索','编辑搜索条件','保存模板','直接推荐','筛选结束入共享库','批量上传'])await expect(page.getByText(label,{exact:true})).toHaveCount(0);
  await expect(page.locator('.shared-library-toolbar').getByText('智能筛选',{exact:true})).toHaveCount(0);
  for(const width of [390,1280,1440,1920]){
    await page.setViewportSize({width,height:1000});
    const transferButton = row.getByRole('button',{name:'转入我的简历',exact:true});
    const resume = row.getByRole('button',{name:'查看简历',exact:true});
    await resume.scrollIntoViewIfNeeded();
    const contactBox = (await transferButton.boundingBox())!;
    const resumeBox = (await resume.boundingBox())!;
    expect(Math.abs(contactBox.x-resumeBox.x)).toBeLessThanOrEqual(1);
    expect(Math.abs(contactBox.width-resumeBox.width)).toBeLessThanOrEqual(1);
    expect(resumeBox.y).toBeGreaterThan(contactBox.y+contactBox.height);
    await row.screenshot({path:testInfo.outputPath(`shared-actions-${width}.png`)});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    const button=form.getByRole('button',{name:'本科',exact:true});
    await expect(button).toHaveCSS('height',width>=768?'30px':'44px');
    if(width>=768){
      await expect(button).toHaveCSS('width','104px');
      const heading=await page.locator('.ledger-heading').boundingBox();
      const filters=await form.boundingBox();
      expect(Math.round(filters!.y-heading!.y-heading!.height)).toBe(14);
    }
    const contact=await row.locator('.talent-contact').boundingBox();
    const identity=await row.locator('.talent-identity-cell').boundingBox();
    expect(contact!.x+contact!.width).toBeLessThanOrEqual(identity!.x+identity!.width);
    await page.screenshot({path:testInfo.outputPath(`shared-${width}.png`),fullPage:true});
  }
  expect((await new AxeBuilder({page}).include('.talent-filters').include('.shared-library-toolbar').analyze()).violations).toEqual([]);
  await form.getByRole('button',{name:/重\s*置/}).click();
  await expect.poll(()=>queries.at(-1)?.getAll('degrees')).toEqual([]);
  const toolbar=page.locator('.shared-library-toolbar');
  await toolbar.scrollIntoViewIfNeeded();
  await page.getByRole('checkbox',{name:'全选本页候选人',exact:true}).check();
  await expect(page.locator('tr[data-row-key="shared-a"] input[type="checkbox"]')).toBeChecked();
  await page.getByRole('checkbox',{name:'全选本页候选人',exact:true}).uncheck();
  await expect(page.locator('tr[data-row-key="shared-a"] input[type="checkbox"]')).not.toBeChecked();
  expect(removedRequests).toEqual([]);
});

test('共享库摘要失败可重试，空列表保留工具栏与分页',async({page})=>{
  await reviewWorkspace(page);
  let failSummary=true;
  let empty=false;
  await page.route('**/api/v1/talent-search/count?*',route=>route.fulfill({json:{count:empty?0:1,hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/talent-search?*',route=>route.fulfill({json:{items:empty?[]:[{candidateId:'shared-a',fullName:'共享候选人',skills:['Java'],degrees:['本科'],conditionMatches:[],engagementLock:null}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidate-resume-summaries?*',route=>failSummary?route.fulfill({status:503,json:{detail:'摘要暂不可用'}}):route.fulfill({json:[{candidateId:'shared-a',factsStatus:'FORBIDDEN',facts:null,resumeVersion:null,source:null}]}));
  await page.goto('/shared-library');
  await expect(page.getByText('部分简历摘要暂不可用，基础信息仍可查看')).toBeVisible();
  await expect(page.getByRole('button',{name:'查看简历'})).toBeVisible();
  failSummary=false;
  await page.getByRole('button',{name:'重试摘要'}).click();
  await expect(page.getByText('摘要需授权')).toBeVisible();
  await expect(page.locator('tr[data-row-key="shared-a"]')).not.toContainText('本科');
  empty=true;
  await page.reload();
  await expect(page.getByText('暂无共享候选人，请调整搜索词或筛选条件')).toBeVisible();
  await expect(page.getByRole('checkbox',{name:'全选本页候选人',exact:true})).toBeDisabled();
  await expect(page.getByLabel('共享简历库分页')).toContainText('共0条记录');
});

test('空扫描分段自动续取且不误报检索结束',async({page})=>{
  await reviewWorkspace(page);
  const cursors:Array<string|null>=[];
  await page.route('**/api/v1/talent-search?*',route=>{
    const cursor=new URL(route.request().url()).searchParams.get('cursor');
    cursors.push(cursor);
    return route.fulfill({json:cursor?{items:[{candidateId:'later',fullName:'后续候选人',skills:[],conditionMatches:[],engagementLock:null}],hasMore:false,nextCursor:null,scanIncomplete:false}:{items:[],hasMore:true,nextCursor:'continue-after-stale',scanIncomplete:true}});
  });
  await page.goto('/shared-library');
  await expect(page.locator('tr[data-row-key="later"]')).toBeVisible();
  await expect(page.getByText('暂无共享候选人，请调整搜索词或筛选条件')).toHaveCount(0);
  expect(cursors).toEqual([null,'continue-after-stale']);
  await expect(page.getByRole('button',{name:'下一页',exact:true})).toBeDisabled();
});

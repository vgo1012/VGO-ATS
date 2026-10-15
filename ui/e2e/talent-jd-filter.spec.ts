import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const width of [390,1280,1440,1920]){
  test(`restores an off-page job label after a direct URL and refresh at ${width}`,async({page},testInfo)=>{
    await reviewWorkspace(page);
    await page.setViewportSize({width,height:1000});
    const job={id:'jd-off-page',title:'非首页岗位',clientName:'测试客户',distinction:'远程协作',currentVersionNo:2};
    const filters:string[]=[];
    const errors:string[]=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/api/v1/jds?**',route=>route.fulfill({json:{items:[],hasMore:true,nextCursor:'next'}}));
    await page.route('**/api/v1/jds/jd-off-page',route=>route.fulfill({json:job}));
    await page.route('**/api/v1/talent-search?**',route=>{
      filters.push(new URL(route.request().url()).searchParams.get('jdId')??'');
      return route.fulfill({json:{items:[],hasMore:false,nextCursor:null}});
    });
    await page.goto('/talents?pool_jd=jd-off-page');
    const selector=page.locator('.jd-selector');
    await expect(selector).toContainText('非首页岗位 · 测试客户 · V2');
    await page.reload();
    await expect(selector).toContainText('非首页岗位 · 测试客户 · V2');
    expect(filters.length).toBeGreaterThanOrEqual(2);
    expect(filters.every(value=>value===job.id)).toBe(true);
    expect((await new AxeBuilder({page}).include('.jd-selector').analyze()).violations).toEqual([]);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath('restored-job.png')});
    await page.getByRole('button',{name:'清空岗位筛选'}).focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/talents$/);
    expect(errors).toEqual([]);
  });
}

test('keeps an unavailable off-page job filter recoverable without displaying its ID',async({page})=>{
  await reviewWorkspace(page);
  await page.setViewportSize({width:390,height:900});
  let unavailable=true;
  await page.route('**/api/v1/jds?**',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/jds/jd-unavailable',route=>route.fulfill(unavailable
    ? {status:403,json:{title:'无权访问',status:403,detail:'暂不可见',code:'FORBIDDEN',recoverable:false}}
    : {json:{id:'jd-unavailable',title:'已恢复岗位',currentVersionNo:1}}));
  await page.route('**/api/v1/talent-search?**',route=>route.fulfill({json:{items:[],hasMore:false,nextCursor:null}}));
  await page.goto('/talents?pool_jd=jd-unavailable');
  await expect(page.getByText('岗位信息暂不可用',{exact:true})).toBeVisible();
  await expect(page.locator('.jd-selector')).not.toContainText('jd-unavailable');
  await expect(page).toHaveURL(/pool_jd=jd-unavailable/);
  expect((await new AxeBuilder({page}).include('.jd-selector').analyze()).violations).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  unavailable=false;
  await page.getByRole('button',{name:'重新加载岗位'}).focus();
  await page.keyboard.press('Enter');
    await expect(page.locator('.jd-selector')).toContainText('已恢复岗位 · V1');
});

test('JD selection immediately filters candidates, resets selection and survives refresh',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const empty={items:[],hasMore:false,nextCursor:null};
  const jobs=[{id:'jd-a',title:'平台开发 JD',currentVersionNo:1},{id:'jd-b',title:'产品设计 JD',currentVersionNo:1},{id:'jd-empty',title:'暂无候选人的 JD',currentVersionNo:1}];
  const people=[{id:'person-a',fullName:'开发候选人',ownerUserId:'hr-a'},{id:'person-b',fullName:'设计候选人',ownerUserId:'hr-a'}];
  const queries:URLSearchParams[]=[];
  await page.route('**/api/v1/jds**',route=>{
    const path=new URL(route.request().url()).pathname;
    const job=jobs.find(item=>path.includes(`/${item.id}`));
    return route.fulfill({json:path.endsWith('/versions')?{...empty,items:[{id:`${job?.id}-v1`,versionNo:1}]}:job??{...empty,items:jobs}});
  });
  await page.route('**/api/v1/candidates?**',route=>{const hasMore=!new URL(route.request().url()).searchParams.has('cursor');return route.fulfill({json:{items:people,hasMore,nextCursor:hasMore?'pool-page-2':null}});});
  await page.route('**/api/v1/candidates/person-*',route=>route.fulfill({json:people.find(item=>route.request().url().endsWith(item.id))}));
  await page.route('**/api/v1/candidate-batch?**',route=>{
    const ids=new URL(route.request().url()).searchParams.getAll('candidateIds');
    return route.fulfill({json:people.filter(person=>ids.includes(person.id))});
  });
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:people.map((person,index)=>({candidateId:person.id,resumeVersionId:`resume-${index}`,stages:[],libraryEligible:false,jobContexts:[{jdId:jobs[index]!.id,jdVersionId:`${jobs[index]!.id}-v1`,title:jobs[index]!.title,versionNo:1,resumeVersionId:`resume-${index}`,status:'PENDING'}]}))}));
  await page.route('**/api/v1/talent-search?**',route=>{
    const params=new URL(route.request().url()).searchParams;queries.push(params);
    const index=jobs.findIndex(job=>job.id===params.get('jdId'));
    return route.fulfill({json:{...empty,items:index<2?[{candidateId:people[index]!.id}]:[]}});
  });
  await page.goto('/talents');
  const selector=page.getByRole('combobox',{name:'求职岗位（选择 JD）'});
  await expect(page.getByRole('cell',{name:'平台开发 JD',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'下一页'}).click();
  const pagination=page.getByLabel('简历库分页');
  await expect(pagination.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await selector.click();
  await page.getByRole('option',{name:/^平台开发 JD · V1/}).click();
  await expect(page.getByRole('button',{name:'开发候选人',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'设计候选人',exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'清空选择'})).toBeDisabled();
  await expect.poll(()=>queries.at(-1)?.get('jdId')).toBe('jd-a');
  expect(queries.at(-1)?.has('cursor')).toBe(false);
  await expect(pagination.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  expect(queries.every(params=>params.get('limit')!=='100')).toBe(true);
  expect(queries.at(-1)?.get('jdId')).toBe('jd-a');
  await expect(page.getByRole('button',{name:'清空选择'})).toBeEnabled();
  await selector.click();
  await page.getByRole('option',{name:/^产品设计 JD · V1/}).click();
  await expect(page.getByRole('button',{name:'设计候选人',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'开发候选人',exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'清空选择'})).toBeDisabled();
  await page.reload();
  await expect(page).toHaveURL(/pool_jd=jd-b/);
  await expect(page.getByRole('button',{name:'设计候选人',exact:true})).toBeVisible();
  await selector.focus();
  await selector.press('Enter');
  await page.getByRole('option',{name:/^暂无候选人的 JD · V1/}).click();
  await expect(page.getByText('没有匹配的候选人，请调整搜索词或筛选条件')).toBeVisible();
  await page.getByRole('button',{name:'清空岗位筛选'}).click();
  await expect(page.getByRole('button',{name:'开发候选人',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'设计候选人',exact:true})).toBeVisible();
  expect((await new AxeBuilder({page}).include('.jd-selector').analyze()).violations).toEqual([]);
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:1000});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
  await page.screenshot({path:testInfo.outputPath('jd-filter.png'),fullPage:true});
});

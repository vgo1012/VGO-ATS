import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty={items:[],hasMore:false,nextCursor:null};
// 智能筛选改为岗位内评分结果分页，游标、长表及五视口验证见 job-score-workspace.spec.ts。
const pages=[
  {path:'/recruitment',label:'招聘流程分页',endpoint:'applications'},
  {path:'/shared-library',label:'共享简历库分页',endpoint:'talent-search'},
  {path:'/onboarded',label:'入职流程分页',endpoint:'applications'},
];

for(const width of [390,768,1280,1440,1920])test(`marked pages match resume pagination spacing and controls at ${width}px`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/candidates?**',route=>route.fulfill({json:empty}));
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.setViewportSize({width,height:898});
  await page.goto('/talents');
  await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
  await page.evaluate(()=>document.fonts.ready);
  const reference=page.getByLabel('简历库分页');
  await reference.scrollIntoViewIfNeeded();
  const referenceBox=(await reference.boundingBox())!;
  const referenceBottom=width<=900?(await page.locator('.mobile-bottom-nav').boundingBox())!.y:898;
  expect(Math.abs(referenceBottom-referenceBox.y-referenceBox.height-4)).toBeLessThanOrEqual(2);
  const referenceFont=await reference.evaluate(node=>getComputedStyle(node).fontSize);
  const referenceButton=await reference.getByRole('button',{name:'上一页',exact:true}).boundingBox();
  for(const target of pages){
    await page.goto(target.path);
    const heading = page.getByRole('heading', { level: 1 });
    await expect(heading).toHaveCSS('font-size', width <= 640 ? '26px' : '30px');
    await expect(heading).toHaveCSS('font-weight', '600');
    await expect(heading).toHaveCSS('border-bottom-width', '0px');
    await expect(page.locator('.ledger-heading')).toHaveCSS('margin-bottom', '14px');
    const footer=page.getByLabel(target.label,{exact:true});
    await expect(footer).toBeVisible();
    await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
    await page.evaluate(()=>document.fonts.ready);
    await page.locator('.pagination-workspace').evaluate(node=>{node.scrollTop=node.scrollHeight;});
    const box=(await footer.boundingBox())!;
    const card=(await page.locator('.paged-panel').filter({has:footer}).boundingBox())!;
    const number=footer.getByRole('spinbutton',{name:'页码',exact:true});
    const previous=footer.getByRole('button',{name:'上一页',exact:true});
    const next=footer.getByRole('button',{name:'下一页',exact:true});
    const previousBox=(await previous.boundingBox())!;
    const nextBox=(await next.boundingBox())!;
    const bottom=width<=900?(await page.locator('.mobile-bottom-nav').boundingBox())!.y:898;
    expect(Math.abs(bottom-box.y-box.height-4),`${target.path} bottom gap at ${width}`).toBeLessThanOrEqual(2);
    const goBox=(await footer.getByRole('button',{name:'跳转页面'}).boundingBox())!;
    if(width>=768)expect(Math.abs(card.x+card.width-goBox.x-goBox.width-17)).toBeLessThanOrEqual(1);
    expect(Math.abs(previousBox.y-nextBox.y)).toBeLessThanOrEqual(1);
    expect(Math.abs(previousBox.width-referenceButton!.width)).toBeLessThanOrEqual(1);
    expect(Math.abs(previousBox.height-referenceButton!.height)).toBeLessThanOrEqual(1);
    await expect(footer).toHaveCSS('font-size',referenceFont);
    await expect(number).toHaveValue('1');
    await expect(footer.getByRole('button',{name:'首页',exact:true})).toBeDisabled();
    await expect(footer.getByRole('button',{name:'末页',exact:true})).toBeDisabled();
    await expect(previous).toBeDisabled();
    await expect(next).toBeDisabled();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`${target.endpoint}-${target.path.slice(1)}-${width}.png`),animations:'disabled'});
    if(width===390||width===1920)expect((await new AxeBuilder({page}).include('.app-main').analyze()).violations).toEqual([]);
  }
  expect(errors).toEqual([]);
});

for(const target of pages)test(`${target.path} pagination keeps server cursors and resets changed filters`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  const requests:URLSearchParams[]=[];
  await page.route('**/api/v1/jd-versions/*/facts',route=>route.fulfill({json:{facts:{title:'分页测试岗位'}}}));
  await page.route(`**/api/v1/${target.endpoint}?**`,route=>{
    const params=new URL(route.request().url()).searchParams;
    requests.push(params);
    const suffix=params.has('cursor')?'second':'first';
    const item=target.endpoint==='match-jobs'?{jdVersionId:'jd-v2',latestRunId:`run-${suffix}`,lastRunAt:'2026-09-07T01:00:00Z',runCount:1,evaluationCount:1,succeededCount:1,failedCount:0,candidateCount:1,scoredCandidateCount:1,activeRunCount:0}:target.endpoint==='talent-search'?{candidateId:suffix,fullName:`分页测试 ${suffix}`,skills:[],conditionMatches:[]}: {id:suffix,candidateId:'person-a',jdId:'jd-a',ownerUserId:'hr-a',recruitmentType:'INTERNAL',currentStage:target.path==='/onboarded'?'ONBOARDED':'PATH_SELECTION',updateTime:'2026-09-07T01:00:00Z'};
    const items=suffix==='first'?Array.from({length:20},(_,index)=>({...item,id:`first-${index}`,...(target.endpoint==='match-jobs'?{jdVersionId:`${suffix}-jd-${index}`} : target.endpoint==='talent-search'?{candidateId:`first-${index}`}:{})})):[item];
    return route.fulfill({json:{items,hasMore:suffix==='first',nextCursor:suffix==='first'?'second':null}});
  });
  await page.goto(target.path);
  const footer=page.getByLabel(target.label,{exact:true});
  const next=footer.getByRole('button',{name:'下一页',exact:true});
  const previous=footer.getByRole('button',{name:'上一页',exact:true});
  await expect(next).toBeEnabled();
  const table=page.locator('.paged-panel .ant-table-content');
  await expect(page.getByRole('columnheader',{name:'#',exact:true})).toBeVisible();
  await expect(table.locator('tr[data-row-key]').first().getByRole('cell').nth(1)).toHaveText('1');
  await expect(table.locator('tr[data-row-key]').last().getByRole('cell').nth(1)).toHaveText('20');
  const scroll=await table.evaluate(node=>({height:node.clientHeight,content:node.scrollHeight}));
  expect(scroll.content).toBeGreaterThan(scroll.height);
  await footer.scrollIntoViewIfNeeded();
  await table.evaluate(node=>{node.scrollTop=node.scrollHeight;});
  await expect(table.locator('tr[data-row-key]').last()).toBeInViewport();
  await expect(next).toBeInViewport();
  await page.screenshot({path:testInfo.outputPath('long-table.png'),animations:'disabled'});
  await next.click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  await expect(next).toBeDisabled();
  await expect(table.locator('tr[data-row-key]').first().getByRole('cell').nth(1)).toHaveText('21');
  await expect.poll(()=>requests.at(-1)?.get('cursor')).toBe('second');
  await previous.click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await expect(previous).toBeDisabled();
  await expect(next).toBeEnabled();
  await next.click();
  await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  if(target.path==='/shared-library'){
    await page.getByRole('textbox', { name: '共享简历关键词搜索', exact: true }).fill('Java');
    await page.getByRole('button',{name:'搜索',exact:true}).click();
    await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
    await expect.poll(()=>requests.at(-1)?.get('q')).toBe('Java');
    expect(requests.at(-1)?.has('cursor')).toBe(false);
  }else if(target.path==='/recruitment'){
    await page.getByRole('combobox',{name:'任务状态',exact:true}).click();
    await page.locator('.ant-select-item-option-content').getByText('已完成',{exact:true}).click();
    await expect(footer.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
    await expect.poll(()=>requests.at(-1)?.get('stage')).toBe('ONBOARDED');
    expect(requests.at(-1)?.get('lifecycle')).toBe('ALL');
    expect(requests.at(-1)?.has('cursor')).toBe(false);
  }else if(target.path==='/onboarded')expect(requests.every(params=>params.get('stage')==='ONBOARDED')).toBe(true);
});

for(const width of [390,1440])test(`candidate library numbering stays visible at ${width}px`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.setViewportSize({width,height:900});
  await page.goto('/talents');
  const header=page.getByRole('columnheader',{name:'#',exact:true});
  await expect(header).toBeVisible();
  await expect(page.locator('tr[data-row-key]').first().getByRole('cell').nth(1)).toHaveText('1');
  await page.getByRole('row',{name:'简历库表头'}).focus();
  await expect(page.getByRole('row',{name:'简历库表头'})).toBeFocused();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:testInfo.outputPath(`candidate-number-${width}.png`),animations:'disabled'});
});

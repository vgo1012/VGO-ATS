import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

for(const viewport of [{width:1440,height:900},{width:390,height:844}]){
  test(`人才池跨页选择与顶部共享入口的可访问性 ${viewport.width}`,async({page},testInfo)=>{
    await page.setViewportSize(viewport);
    await reviewWorkspace(page);
    const people=[{id:'person-a',fullName:'合成测试甲',ownerUserId:'hr-a',version:1,status:'ACTIVE'},{id:'person-b',fullName:'合成测试乙',ownerUserId:'hr-a',version:1,status:'ACTIVE'}];
    const requests:string[]=[];
    await page.route('**/api/v1/**',route=>{
      const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
      if(path==='/candidates'){
        requests.push(url.searchParams.get('poolOnly')??'');
        const all=url.searchParams.get('limit')==='100',second=Boolean(url.searchParams.get('cursor'));
        return route.fulfill({json:{items:all?people:[people[second?1:0]],hasMore:!all&&!second,nextCursor:all||second?null:'next'}});
      }
      if(path==='/candidate-pool-statuses')return route.fulfill({json:people.map((person,index)=>({candidateId:person.id,resumeVersionId:`resume-${index}`,libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',title:'合成前端岗位',versionNo:2,resumeVersionId:`resume-${index}`,status:index?'AI_SCREENED':'PENDING',stage:null,applicationId:null,matchRunId:null}]}))});
      if(path==='/candidate-resume-summaries')return route.fulfill({json:people.map((person,index)=>({candidateId:person.id,resumeVersion:{id:`resume-${index}`,createdAt:'2026-09-07T01:00:00Z'},source:null,facts:null,factsStatus:'NOT_READY'}))});
      if(path.startsWith('/candidates/'))return route.fulfill({json:people.find(person=>path===`/candidates/${person.id}`)??{items:[],hasMore:false,nextCursor:null}});
      return route.fallback();
    });
    await page.goto('/talents?view=pool');
    await expect(page.getByText('合成测试甲',{exact:true})).toBeVisible();
    await page.locator('tr[data-row-key="person-a"] input[type="checkbox"]').check();
    await page.getByRole('button',{name:'下一页',exact:true}).click();
    await expect(page.getByText('合成测试乙',{exact:true})).toBeVisible();
    await page.locator('tr[data-row-key="person-b"] input[type="checkbox"]').check();
    await page.getByRole('button',{name:'上一页',exact:true}).click();
    await expect(page.locator('tr[data-row-key="person-a"] input[type="checkbox"]')).toBeChecked();
    await page.getByRole('button',{name:'下一页',exact:true}).click();
    await expect(page.locator('tr[data-row-key="person-b"] input[type="checkbox"]')).toBeChecked();
    expect(requests.every(value=>value==='true')).toBe(true);
    const share=page.getByRole('button',{name:'筛选结束入共享库',exact:true});
    await expect(page.locator('.talent-toolbar').getByRole('button',{name:'筛选结束入共享库',exact:true})).toBeVisible();
    await expect(page.locator('.ant-table-tbody').getByRole('button',{name:'筛选结束入共享库',exact:true})).toHaveCount(0);
    if(viewport.width===1440){
      const table=page.locator('.talent-ledger-card .ant-table-content');
      expect(await table.evaluate(element=>element.scrollWidth)).toBeLessThanOrEqual(await table.evaluate(element=>element.clientWidth));
    }
    await page.waitForLoadState('networkidle');
    expect((await new AxeBuilder({page}).include('.app-main').analyze()).violations).toEqual([]);
    await expect(page.getByRole('button',{name:'批量转共享库',exact:true})).toHaveCount(0);
    await share.click();
    const shareDialog=page.getByRole('dialog',{name:'结束筛选并转入共享简历库',exact:true});
    await expect(shareDialog).toBeVisible();
    await expect(shareDialog.getByLabel('结束筛选原因')).toBeVisible();
    await expect(shareDialog.getByText('合成测试乙',{exact:true})).toBeVisible();
    await expect(shareDialog.getByText('合成测试甲',{exact:true})).toBeVisible();
    await expect(shareDialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
    expect((await new AxeBuilder({page}).include('.candidate-action-dialog').analyze()).violations).toEqual([]);
    await page.screenshot({path:testInfo.outputPath(`talent-batch-${viewport.width}.png`),fullPage:true});
  });
}

test('单条确认响应丢失后可恢复且不重复入库',async({page})=>{
  await reviewWorkspace(page);
  let confirmed=false,posts=0,reads=0,aiCalls=0;
  const intake={id:'intake-one',batchId:'batch',sourceChannelId:'channel',sourceRecruiterUserId:'hr-a',ownerUserId:'hr-a',ownerDepartmentId:'dept',jdVersionId:'jd-v2',originalFileName:'合成恢复样本.pdf',sizeBytes:1200,scanStatus:'CLEAN',status:'READY_FOR_CONFIRMATION',importStatus:'PENDING',version:1};
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path.includes('/parse'))aiCalls++;
    if(path==='/intakes')return route.fulfill({json:{items:[{...intake,status:confirmed?'CONFIRMED':'READY_FOR_CONFIRMATION'}],hasMore:false,nextCursor:null}});
    if(path==='/intakes/intake-one'){reads++;return route.fulfill({json:{...intake,status:confirmed?'CONFIRMED':'READY_FOR_CONFIRMATION',version:confirmed?2:1}});}
    if(path==='/intakes/intake-one/confirm'){posts++;confirmed=true;return route.fulfill({status:503,json:{title:'响应传输失败',detail:'已保存，但响应未能确认，请重试',status:503,code:'TEST_RESPONSE_LOST',recoverable:true}});}
    return route.fallback();
  });
  await page.goto('/talents?view=intakes');
  await page.getByRole('button',{name:'确认入库',exact:true}).click();
  const confirmation=page.getByRole('dialog',{name:'确认入库 · 合成恢复样本.pdf',exact:true});
  await expect(confirmation).toBeVisible();
  await expect(page.getByText('明确关联已有候选人（可选）')).toHaveCount(0);
  await page.getByLabel('候选人姓名').fill('合成恢复测试');
  await confirmation.getByRole('button',{name:'确认入库',exact:true}).click();
  await expect(page.getByText('已保存，但响应未能确认，请重试')).toBeVisible();
  const retry=confirmation.getByRole('button',{name:/确认入库$/});
  await expect(retry).toBeEnabled();
  await retry.click();
  await expect(confirmation).toBeHidden();
  expect(posts).toBe(1);expect(reads).toBe(2);expect(aiCalls).toBe(0);
});

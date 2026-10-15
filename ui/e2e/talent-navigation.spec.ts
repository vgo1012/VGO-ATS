import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const navigation=['招聘洞察','候选人库','AI 评估','招募管道','面试日历','人才公海','录用交付'];
const empty={items:[],hasMore:false,nextCursor:null};

test('candidate pool retains accessible search, actions and responsive layout',async({page},testInfo)=>{
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('console',message=>{if(message.type()==='error')errors.push(message.text());});
  await reviewWorkspace(page);
  const searches:string[]=[];
  await page.route('**/api/v1/candidates?**',route=>{
    searches.push(new URL(route.request().url()).searchParams.get('query')??'');
    return route.fulfill({json:empty});
  });
  await page.route('**/api/v1/talent-search?**',route=>{
    searches.push(new URL(route.request().url()).searchParams.get('q')??'');
    return route.fulfill({json:empty});
  });
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:898});
    await page.goto('/talents');
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    await page.evaluate(()=>document.fonts.ready);
    await expect(page.getByRole('heading',{name:'我的简历',exact:true})).toHaveCount(0);
    const pool=page.getByRole('form',{name:'简历筛选条件',exact:true});
    await expect(pool).toBeVisible();
    await expect(page.getByRole('tab',{name:'待处置'})).toHaveCount(0);
    await expect(page.getByRole('button',{name:/^(超时简历|招聘待办)$/})).toHaveCount(0);
    const bodyBox=await page.locator('.talent-ledger-card tbody').boundingBox();
    const emptyBox=await page.locator('.talent-ledger-card .ant-empty').boundingBox();
    expect(Math.abs(emptyBox!.y+emptyBox!.height/2-bodyBox!.y-bodyBox!.height/2)).toBeLessThanOrEqual(2);
    await expect(page.getByRole('columnheader',{name:'求职岗位',exact:true})).toBeAttached();
    for(const name of ['直接推荐','智能筛选'])await expect(page.locator('.talent-bulk-actions').getByRole('button',{name,exact:true})).toBeDisabled();
    await expect(page.getByRole('button',{name:'批量转共享库',exact:true})).toHaveCount(0);
    const search=page.getByRole('textbox',{name:'搜索候选人',exact:true});
    await expect(page.getByRole('status',{name:'人才池选择数量'})).toHaveCount(0);
    await expect(page.getByText(/这里是我负责的候选人/)).toHaveCount(0);
    const toolbar=page.locator('.talent-toolbar');
    const searchBox=await page.getByRole('search',{name:'搜索候选人'}).boundingBox();
    const toolbarBox=await toolbar.boundingBox();
    const searchRightGap=toolbarBox!.x+toolbarBox!.width-searchBox!.x-searchBox!.width;
    expect(searchRightGap).toBeGreaterThanOrEqual(0);
    expect(searchRightGap).toBeLessThanOrEqual(20);
    expect((await pool.boundingBox())!.y-(await page.locator('.app-main').boundingBox())!.y).toBeLessThan(48);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`my-resumes-${width}.png`),fullPage:true,animations:'disabled'});
    await search.fill('测试姓名');
    await search.press('Enter');
    await expect.poll(()=>searches.at(-1)).toBe('测试姓名');
    await expect(page.getByText('没有匹配的候选人，请调整搜索词')).toBeVisible();
    await page.getByRole('search',{name:'搜索候选人'}).getByRole('button',{name:'清空条件'}).click();
    await expect(search).toHaveValue('');
    await expect(page.getByText('暂无候选人，请先上传简历并确认入库')).toBeVisible();
    expect((await new AxeBuilder({page}).include('.app-main').analyze()).violations).toEqual([]);
  }
  expect(errors).toEqual([]);
});

test('my resumes keeps long tables reachable with page and table-local scrolling',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const people=Array.from({length:20},(_,index)=>({id:`height-${index}`,fullName:`高度测试${index+1}`,ownerUserId:'hr-a',version:1,status:'ACTIVE'}));
  await page.route('**/api/v1/candidates?**',route=>route.fulfill({json:{...empty,items:people,hasMore:true,nextCursor:'next'}}));
  await page.goto('/talents');
  for(const viewport of [{width:1920,height:898},{width:1280,height:720},{width:390,height:844}]){
    await page.setViewportSize(viewport);
    const table=page.locator('.talent-ledger-card .ant-table-content');
    await expect(table.locator('tr[data-row-key]')).toHaveCount(20);
    await table.locator('tr[data-row-key="height-19"]').scrollIntoViewIfNeeded();
    await expect(table.locator('tr[data-row-key="height-19"]')).toBeInViewport();
    await page.getByRole('button',{name:'下一页',exact:true}).scrollIntoViewIfNeeded();
    await expect(page.getByRole('button',{name:'下一页',exact:true})).toBeInViewport();
    await table.evaluate(node=>{node.scrollLeft=node.scrollWidth;});
    if(viewport.width>=768)expect(await page.getByRole('columnheader',{name:'操作',exact:true}).evaluate(node=>{
      const box=node.getBoundingClientRect();
      return node.contains(document.elementFromPoint(box.x+box.width/2,box.y+box.height/2));
    })).toBe(true);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`full-height-${viewport.width}.png`),animations:'disabled'});
  }
});

test('seven destinations and personal candidate actions remain keyboard accessible after refresh',async({page})=>{
  await reviewWorkspace(page);await page.goto('/talents');
  await expect(page.locator('.workspace-nav:visible button')).toHaveText(navigation);
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('tablist',{name:'简历库视图'})).toHaveCount(0);
  const select=page.getByRole('checkbox',{name:'全选本页候选人'});
  await expect(select).toBeEnabled();
  await select.focus();await page.keyboard.press('Space');
  await expect(select).toBeChecked();
  await page.reload();await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect((await new AxeBuilder({page}).include('.app-shell').analyze()).violations).toEqual([]);
});

for(const [view,destination] of [['library','shared-library'],['intakes','talents'],['channels','admin/source-channels']]){
  test(`old ${view} entry redirects to ${destination}`,async({page})=>{
    await reviewWorkspace(page);await page.goto(`/talents?view=${view}`);await expect(page).toHaveURL(new RegExp(`/${destination}(?:\\?|$)`));
    if(view==='library')await expect(page.getByRole('heading',{name:'共享简历库',exact:true})).toBeVisible();
    else if(view==='intakes')await expect(page.getByRole('dialog',{name:'收件校验',exact:true})).toBeVisible();
    else await expect(page.getByRole('heading',{name:'来源渠道',exact:true})).toBeVisible();
  });
}

test('intake filters reach the server, survive refresh and reset together',async({page})=>{
  await reviewWorkspace(page);const queries:URLSearchParams[]=[];
  await page.route('**/api/v1/intakes?**',route=>{queries.push(new URL(route.request().url()).searchParams);return route.fulfill({json:empty});});
  await page.goto('/settings/intakes?name=林雨桐&query=简历&from=2026-09-06T16%3A00%3A00.000Z&to=2026-09-07T16%3A00%3A00.000Z');
  await expect(page.getByLabel('姓名',{exact:true})).toHaveValue('林雨桐');await expect(page.getByLabel('关键词',{exact:true})).toHaveValue('简历');
  await expect.poll(()=>queries.length).toBeGreaterThan(0);
  expect(queries.at(-1)?.get('from')).toBe('2026-09-06T16:00:00.000Z');
  expect(queries.at(-1)?.get('to')).toBe('2026-09-07T16:00:00.000Z');
  await page.getByLabel('关键词',{exact:true}).fill('开发');await page.getByRole('button',{name:/查\s*询/}).click();
  await expect.poll(()=>queries.at(-1)?.get('query')).toBe('开发');
  await page.reload();await expect(page.getByLabel('关键词',{exact:true})).toHaveValue('开发');
  const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
  await drawer.getByRole('button',{name:/重\s*置/}).click();await expect(drawer.getByLabel('姓名',{exact:true})).toHaveValue('');await expect(drawer.getByLabel('关键词',{exact:true})).toHaveValue('');
  await expect.poll(()=>queries.at(-1)?.get('query')).toBeNull();
  expect((await new AxeBuilder({page}).include('.app-shell').analyze()).violations).toEqual([]);
});

test('public channels are shared but ordinary HR cannot enter maintenance',async({page},testInfo)=>{
  await reviewWorkspace(page);let writes=0;
  await page.route('**/api/v1/me',route=>route.fulfill({json:{userId:'hr-a',displayName:'招聘专员',workEmail:'hr@example.com',roles:['RECRUITER'],superAdmin:false,permissions:['talent.read','talent.manage','matching.run','recruitment.manage','interview.feedback.read-authorized']}}));
  await page.route('**/api/v1/source-channels**',route=>{if(route.request().method()!=='GET')writes++;return route.fulfill({json:{...empty,items:['BOSS','智联','猎聘','内推'].map((name,index)=>({id:`c${index}`,name,code:`C${index}`,channelType:'MANUAL',status:'ACTIVE',version:1}))}});});
  await page.goto('/talents');await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'批量上传',exact:true}).click();await page.getByLabel('上传来源渠道').click();
  await expect(page.locator('.ant-select-item-option-content')).toHaveText(['BOSS','智联','猎聘','内推']);
  await expect(page.locator('.ant-select-dropdown')).toHaveCSS('opacity','1');
  await page.screenshot({path:testInfo.outputPath('upload-source-options.png')});
  await page.getByLabel('上传来源渠道').press('ArrowUp');await page.getByLabel('上传来源渠道').press('Enter');
  await expect(page.locator('.ant-select-content').filter({has:page.getByLabel('上传来源渠道')})).toHaveText('内推');
  expect((await new AxeBuilder({page}).include('.ant-drawer').analyze()).violations).toEqual([]);
  await page.getByRole('dialog',{name:'批量导入简历',exact:true}).getByRole('button',{name:'关闭',exact:true}).click();
  await page.screenshot({path:testInfo.outputPath('my-resumes.png'),animations:'disabled'});
  await page.goto('/admin/source-channels');await expect(page.getByRole('button',{name:'新建来源渠道'})).toHaveCount(0);expect(writes).toBe(0);
});

test('onboarded records are requested by server stage with cursor pagination and an empty state',async({page})=>{
  await reviewWorkspace(page);const requests:URLSearchParams[]=[];
  await page.route('**/api/v1/applications?**',route=>{const params=new URL(route.request().url()).searchParams;requests.push(params);return route.fulfill({json:params.has('cursor')?empty:{items:[{id:'app-onboarded',candidateId:'person-a',resumeVersionId:'resume-onboarded',jdId:'jd-a',demandId:'demand-a',ownerUserId:'hr-a',currentStage:'ONBOARDED',onboardingStatus:'ONBOARDED'}],hasMore:true,nextCursor:'second'}});});
  await page.route('**/api/v1/resume-version-fact-summaries?**',route=>route.fulfill({status:403,json:{status:403,detail:'摘要需授权'}}));
  await page.route('**/api/v1/resume-version-sources?**',route=>route.fulfill({json:[{resumeVersionId:'resume-onboarded',source:{sourceChannelName:'内推'}}]}));
  await page.route('**/api/v1/applications/app-onboarded',route=>route.fulfill({status:403,json:{status:403,title:'无法读取',detail:'当前无法读取该招聘活动',code:'ACCESS_DENIED',recoverable:false}}));
  await page.goto('/onboarded');
  const row=page.locator('tr[data-row-key="app-onboarded"]');
  await row.getByRole('button',{name:'更正入职时间'}).click();
  const denied=page.getByRole('dialog',{name:'更正入职时间'});
  await expect(denied.getByRole('button',{name:'重试活动'})).toBeVisible();
  await expect(page.getByRole('alert').filter({hasText:'当前无法读取该招聘活动'})).toBeVisible();
  await expect(denied.getByRole('button',{name:'保存更正'})).toHaveCount(0);
  await expect(page).toHaveURL(/\/onboarded$/);await page.keyboard.press('Escape');await expect(denied).not.toBeVisible();
  const footer=page.getByRole('navigation',{name:'入职流程分页'});
  expect(requests[0]?.get('stage')).toBe('ONBOARDED');await footer.getByRole('button',{name:'下一页'}).click();await expect(page.getByText('当前没有入职流程记录')).toBeVisible();
  expect(requests.at(-1)?.get('cursor')).toBe('second');expect(requests.every(value=>value.get('stage')==='ONBOARDED')).toBe(true);
  await footer.getByRole('button',{name:'上一页'}).click();await expect(row.getByRole('button',{name:'更正入职时间'})).toBeVisible();
});

test('new pages keep local scrolling across representative viewports',async({page},testInfo)=>{
  test.skip(testInfo.project.name==='mobile','desktop project covers the complete viewport matrix');
  await reviewWorkspace(page);
  for(const width of [390,768,1280,1440,1920]){
    await page.setViewportSize({width,height:900});
    for(const path of ['/talents','/shared-library','/settings/intakes','/admin/source-channels','/onboarded']){
      await page.goto(path);
      if(path==='/talents')await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
      else if(path==='/settings/intakes')await expect(page.getByRole('dialog',{name:'收件校验',exact:true})).toBeVisible();
      else await expect(page.locator('h1')).toBeVisible();
      await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
      expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth),`${path} at ${width}`).toBeLessThanOrEqual(1);
      if(width<=900){
        const bar=page.locator('.mobile-bottom-nav');
        expect(await bar.getByRole('button').first().evaluate(node=>node.getBoundingClientRect().width)).toBeGreaterThanOrEqual(88);
        const current=bar.locator('[aria-current="page"]');
        if(await current.count())await expect(current).toBeInViewport();
      }
      await page.screenshot({path:testInfo.outputPath(`${path.replaceAll('/','-')}-${width}.png`),fullPage:true,animations:'disabled'});
    }
  }
});

test('administrator can create, edit and disable a shared source with recoverable errors',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const channels=['BOSS','智联','猎聘','内推'].map((name,index)=>({id:`c${index}`,name,code:`C${index}`,channelType:'MANUAL',status:'ACTIVE',version:1}));
  let reject=true,creates=0;
  await page.route('**/api/v1/source-channels**',route=>{
    const request=route.request(),path=new URL(request.url()).pathname;
    if(request.method()==='GET')return route.fulfill({json:{...empty,items:channels}});
    if(path.endsWith('/source-channels')){
      creates++;
      if(reject){reject=false;return route.fulfill({status:409,json:{title:'版本冲突',detail:'渠道名称已占用，请修改后重试',code:'CONFLICT',status:409,recoverable:true}});}
      const channel={...request.postDataJSON(),id:'new',code:'NEW',status:'ACTIVE',version:1};channels.push(channel);return route.fulfill({json:channel});
    }
    const channel=channels.find(value=>path.includes(`/${value.id}`))!;
    if(path.endsWith('/disable'))channel.status='DISABLED';else channel.name=request.postDataJSON().name;
    channel.version++;return route.fulfill({json:channel});
  });
  await page.goto('/admin/source-channels');
  for(const name of ['BOSS','智联','猎聘','内推'])await expect(page.getByRole('cell',{name,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'新建来源渠道'}).click();
  await page.getByRole('button',{name:'保存渠道'}).focus();await page.keyboard.press('Enter');
  await expect(page.getByText('请输入渠道名称',{exact:true})).toBeVisible();
  await expect(page.getByLabel('名称',{exact:true})).toBeFocused();expect(creates).toBe(0);
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('channel-validation.png'),animations:'disabled'});
  await page.getByLabel('名称',{exact:true}).fill('校园招聘');
  await page.getByRole('button',{name:'保存渠道'}).click();await expect(page.getByText('渠道名称已占用，请修改后重试')).toBeVisible();await expect(page.getByLabel('名称',{exact:true})).toHaveValue('校园招聘');
  await page.getByLabel('名称',{exact:true}).fill('校招');await page.getByRole('button',{name:'保存渠道'}).click();await expect(page.getByRole('dialog')).toBeHidden();expect(creates).toBe(2);
  const row=page.getByRole('row').filter({has:page.getByRole('cell',{name:'校招',exact:true})});await row.getByRole('button',{name:/编\s*辑/}).click();await page.getByLabel('名称',{exact:true}).fill('校园宣讲');await page.getByRole('button',{name:'保存渠道'}).click();await expect(page.getByRole('dialog')).toBeHidden();
  const renamed=page.getByRole('row').filter({has:page.getByRole('cell',{name:'校园宣讲',exact:true})});await renamed.getByRole('button',{name:/停\s*用/}).click();await page.getByRole('button',{name:/确\s*定/}).click();await expect(renamed.getByRole('button',{name:/停\s*用/})).toHaveCount(0);await expect(renamed).toContainText('停用');
});

test('intake paging restores previous cursors after refresh and resolves conflicts in the receipt row',async({page},testInfo)=>{
  await reviewWorkspace(page);let resolved=false;
  const intake=(id:string)=>({id,originalFileName:`${id}.pdf`,jdVersionId:'jd-v2',sourceChannelId:'c0',ownerUserId:'hr-a',sizeBytes:1024,status:'READY_FOR_CONFIRMATION',importStatus:'PENDING',scanStatus:'CLEAN',createdAt:'2026-09-07T01:00:00Z',version:1});
  await page.route('**/api/v1/intakes?**',route=>{const cursor=new URL(route.request().url()).searchParams.get('cursor');return route.fulfill({json:{items:[intake(cursor?'second':'first')],hasMore:!cursor,nextCursor:cursor?null:'next'}});});
  await page.route('**/api/v1/identity-conflicts**',route=>{
    if(route.request().method()==='POST'){resolved=true;return route.fulfill({json:{id:'conflict',status:'RESOLVED'}});}
    const ids=new URL(route.request().url()).searchParams.getAll('intakeIds');
    return route.fulfill({json:{...empty,items:!resolved&&ids.includes('second')?[{id:'conflict',intakeId:'second',candidateIdA:'person-a',candidateIdB:null,conflictType:'PHONE_EMAIL_SPLIT',status:'OPEN'}]:[]}});
  });
  await page.goto('/settings/intakes');const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});await drawer.getByRole('button',{name:'下一页'}).click();await expect(drawer.getByText('second.pdf')).toBeVisible();await page.reload();
  await drawer.getByRole('button',{name:'首页',exact:true}).click();await expect(drawer.getByText('first.pdf')).toBeVisible();
  await drawer.getByRole('button',{name:'下一页'}).click();await drawer.getByRole('button',{name:'处理身份冲突'}).click();
  const conflictDialog=page.getByRole('dialog',{name:'人工处理身份冲突',exact:true});
  await conflictDialog.getByRole('button',{name:'确认处理'}).click();
  await expect(conflictDialog.getByLabel('处理原因')).toBeFocused();await expect(conflictDialog.getByLabel('处理原因')).toHaveAttribute('aria-invalid','true');expect(resolved).toBe(false);
  await expect(page.locator('.ant-form-item-explain-error')).not.toHaveClass(/appear|enter/);
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('conflict-validation.png'),animations:'disabled'});
  await conflictDialog.getByLabel('处理原因').fill('已核对原简历和联系方式');await conflictDialog.getByRole('button',{name:'确认处理'}).click();await expect(conflictDialog).toBeHidden();
  await expect(drawer.getByRole('button',{name:'处理身份冲突'})).toHaveCount(0);expect(resolved).toBe(true);await expect(drawer.getByRole('tab',{name:/身份冲突/})).toHaveCount(0);
});

test('receipt confirmation preserves input on failure, blocks duplicate submission and resets for another receipt',async({page},testInfo)=>{
  await reviewWorkspace(page);let posts=0,finish:(()=>void)|undefined;
  const rows=['a','b'].map(id=>({id,originalFileName:`${id}.pdf`,jdVersionId:'jd-v2',ownerUserId:'hr-a',sourceChannelId:'source',sizeBytes:100,status:'READY_FOR_CONFIRMATION',failureCode:null,importStatus:'PENDING',scanStatus:'CLEAN',version:1}));
  await page.route('**/api/v1/intakes**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path.endsWith('/confirm')){posts++;await new Promise<void>(resolve=>{finish=resolve;});return route.fulfill({status:503,json:{status:503,title:'临时失败',detail:'暂时无法确认，请重试',code:'TEST_FAILED',recoverable:true}});}
    const id=path.split('/').at(-1);return route.fulfill({json:id==='intakes'?{...empty,items:rows}:rows.find(row=>row.id===id)});
  });
  await page.goto('/settings/intakes');const intakeDrawer=page.getByRole('dialog',{name:'收件校验',exact:true});await intakeDrawer.getByRole('row').filter({hasText:'a.pdf'}).getByRole('button',{name:'确认入库',exact:true}).click();
  const confirmDialog=page.getByRole('dialog',{name:'确认入库 · a.pdf',exact:true});
  await confirmDialog.getByRole('button',{name:'确认入库',exact:true}).focus();await page.keyboard.press('Enter');
  await expect(confirmDialog.getByLabel('候选人姓名')).toBeFocused();await expect(confirmDialog.getByLabel('候选人姓名')).toHaveAttribute('aria-invalid','true');expect(posts).toBe(0);
  await expect(page.locator('.ant-form-item-explain-error')).not.toHaveClass(/appear|enter/);
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('intake-validation.png'),animations:'disabled'});
  await confirmDialog.getByLabel('候选人姓名').fill('甲候选人');await confirmDialog.getByRole('button',{name:'确认入库',exact:true}).click();
  await expect.poll(()=>posts).toBe(1);await expect(confirmDialog.getByRole('button',{name:/确认入库$/})).toBeDisabled();await page.keyboard.press('Escape');await expect(confirmDialog).toBeVisible();finish?.();
  const failureAlert=page.getByRole('alert').filter({hasText:'暂时无法确认，请重试'});await expect(failureAlert).toBeVisible();await expect(confirmDialog.getByLabel('候选人姓名')).toHaveValue('甲候选人');await failureAlert.getByRole('button',{name:'关闭错误提示'}).click();await confirmDialog.getByRole('button',{name:/取\s*消/}).click();
  await intakeDrawer.getByRole('row').filter({hasText:'b.pdf'}).getByRole('button',{name:'确认入库',exact:true}).click();const secondDialog=page.getByRole('dialog',{name:'确认入库 · b.pdf',exact:true});await expect(secondDialog.getByLabel('候选人姓名')).toHaveValue('');await expect(secondDialog.getByText('暂时无法确认，请重试')).toHaveCount(0);expect(posts).toBe(1);
});

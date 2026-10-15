import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

test('候选人库蓝色主操作、紧凑筛选和工具条批量上传',async({page},info)=>{
  await reviewWorkspace(page);
  const uploaded:string[]=[];const imported:string[]=[];
  await page.route('**/api/v1/source-channels?*',route=>route.fulfill({json:{items:[{id:'source-a',name:'内推',status:'ACTIVE'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intake-batches',route=>{expect(route.request().postDataJSON()).toMatchObject({sourceChannelId:'source-a'});return route.fulfill({json:{id:'batch-a'}});});
  await page.route('**/api/v1/intake-batches/batch-a/files',route=>{uploaded.push(route.request().postData()??'');const id=`intake-${uploaded.length}`;return route.fulfill({json:{id,batchId:'batch-a',sourceChannelId:'source-a',ownerUserId:'hr-a',originalFileName:`sample-${uploaded.length}.pdf`,sizeBytes:100,status:'PENDING_IMPORT',importStatus:'PENDING',scanStatus:'CLEAN',jdVersionId:'jd-version-1',version:1,duplicateContent:false}});});
  await page.route(/\/api\/v1\/intakes\/(intake-\d+)(\/import)?$/,route=>{const match=new URL(route.request().url()).pathname.match(/\/(intake-\d+)(\/import)?$/)!;const id=match[1]!;const base={id,batchId:'batch-a',sourceChannelId:'source-a',ownerUserId:'hr-a',originalFileName:`${id}.pdf`,sizeBytes:100,scanStatus:'CLEAN',jdVersionId:'jd-version-1',version:1};if(route.request().method()==='GET')return route.fulfill({json:{...base,status:'PENDING_IMPORT',importStatus:'PENDING'}});imported.push(id);expect(route.request().postDataJSON()).toEqual({version:'1'});return route.fulfill({json:{...base,status:'PARSING',importStatus:'PROCESSING'}});});
  await page.route('**/api/v1/intake-batches/batch-a/complete',route=>route.fulfill({json:{id:'batch-a'}}));
  await page.goto('/talents');
  for(const width of [1920,1440,1280,390]){
    await page.setViewportSize({width,height:1000});
    await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
    await expect(page.getByRole('button',{name:'批量上传',exact:true})).toHaveCSS('background-color','rgb(37, 99, 235)');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    if(width>=768){
      expect((await page.locator('.talent-filters').boundingBox())!.height).toBeLessThan(180);
      const rows=await page.locator('.talent-filter-options').evaluateAll(groups=>groups.map(group=>Array.from(group.children).map(el=>{const box=el.getBoundingClientRect();return {x:box.x,width:box.width,height:box.height};})));
      for(const row of rows)for(const [index,box] of row.entries()){
        expect(box.width).toBe(104);expect(box.height).toBe(30);expect(box.x).toBe(rows[1]![index]!.x);
      }
      await expect(page.getByRole('group',{name:'学历要求'}).getByRole('button',{name:'本科',exact:true})).toHaveCSS('font-size','13px');
    }
    await expect(page.getByRole('columnheader',{name:'候选人',exact:true})).toHaveCSS('font-size','12px');
    await page.screenshot({path:info.outputPath(`talent-${width}.png`),fullPage:true});
  }
  await page.setViewportSize({width:1920,height:1000});
  const button=page.getByRole('button',{name:'批量上传',exact:true});
  await button.click();
  const drawer=page.getByRole('dialog',{name:'批量导入简历'});
  await expect(drawer).toBeVisible();
  await drawer.getByLabel('上传来源渠道').click();
  await drawer.getByLabel('上传来源渠道').press('ArrowDown');
  await drawer.getByLabel('上传来源渠道').press('Enter');
  await drawer.getByRole('combobox',{name:'选择已解析成功的 JD'}).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'高级前端工程师'}).click();
  await drawer.locator('input[type=file]').setInputFiles([
    {name:'sample-a.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 synthetic-a')},
    {name:'sample-b.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 synthetic-b')},
  ]);
  await drawer.getByRole('button',{name:'开始导入',exact:true}).click();
  await expect.poll(()=>uploaded.length).toBe(2);
  await expect.poll(()=>imported).toEqual(['intake-1','intake-2']);
  await expect(drawer.getByRole('group',{name:'成功 0，失败 0，解析中 2'})).toBeVisible();
  await expect(drawer.getByRole('list',{name:'本次上传文件结果'})).toContainText('sample-a.pdf：解析中');
  await drawer.getByRole('button',{name:/^(关闭|Close)$/}).click();
  await expect(drawer).not.toBeVisible();
  expect((await new AxeBuilder({page}).include('.talent-filters').analyze()).violations).toEqual([]);
});

test('只读用户不显示批量上传入口',async({page})=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/me',route=>route.fulfill({json:{userId:'reader',displayName:'只读',workEmail:'reader@example.com',homeDepartmentId:'dept-a',roles:['RECRUITER'],permissions:['talent.read'],superAdmin:false,mustChangePassword:false,version:'1'}}));
  await page.goto('/talents');
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'批量上传',exact:true})).toHaveCount(0);
});

import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('recovers lost upload responses without creating duplicate batches or intakes', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/source-channels?*', route => route.fulfill({json:{items:[{id:'channel-a',name:'手工上传',status:'ACTIVE'}],hasMore:false,nextCursor:null}}));
  const creates: Array<{key:string|undefined;body:string|null}> = [];
  const uploads: Array<{key:string|undefined;name:string;path:string}> = [];
  const imports: string[] = [];
  const completions: string[] = [];
  const intake = (id: string) => ({id,status:'PENDING_IMPORT',importStatus:'PENDING',scanStatus:'CLEAN',jdVersionId:'jd-v2',version:'0'});
  await page.route('**/api/v1/intake-batches', async route => {
    creates.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()});
    if(creates.length===1)return route.fulfill({status:200,contentType:'application/json',body:'{'});
    return route.fulfill({json:{id:'batch-original'}});
  });
  await page.route('**/api/v1/intake-batches/*/files', async route => {
    const body=route.request().postDataBuffer()!.toString();
    const name=body.includes('accepted.pdf')?'accepted.pdf':'uncertain.pdf';
    uploads.push({key:route.request().headers()['idempotency-key'],name,path:new URL(route.request().url()).pathname});
    if(name==='uncertain.pdf'&&uploads.filter(item=>item.name===name).length===1)return route.fulfill({status:200,contentType:'application/json',body:'{'});
    return route.fulfill({json:intake(name==='accepted.pdf'?'accepted':'uncertain')});
  });
  await page.route('**/api/v1/intakes/*', route => route.fulfill({json:intake(new URL(route.request().url()).pathname.split('/').at(-1)!)}));
  await page.route('**/api/v1/intakes/*/import', route => {
    imports.push(new URL(route.request().url()).pathname);
    return route.fulfill({json:{...intake('imported'),importStatus:'PROCESSING'}});
  });
  await page.route('**/api/v1/intake-batches/*/complete', route => {
    completions.push(new URL(route.request().url()).pathname);
    return route.fulfill(completions.length===1
      ?{status:503,json:{title:'暂不可用',status:503,detail:'汇总响应丢失',code:'UNAVAILABLE',recoverable:true}}
      :{json:{id:'batch-original',status:'COMPLETED'}});
  });
  await page.goto('/talents');
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  const drawer=page.getByRole('dialog',{name:'批量导入简历'});
  await drawer.getByRole('combobox',{name:'上传来源渠道'}).click();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await drawer.getByRole('combobox',{name:'选择已解析成功的 JD'}).click();
  await page.getByRole('option').filter({hasText:'高级前端工程师'}).click();
  await drawer.locator('input[type=file]').setInputFiles([
    {name:'accepted.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 accepted')},
    {name:'uncertain.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 uncertain')},
  ]);
  const start=drawer.getByRole('button',{name:'开始导入',exact:true});
  await start.click();
  await expect.poll(()=>creates.length).toBe(1);
  await expect(page.locator('.ant-notification')).toContainText(/Expected property name|Unexpected end of JSON input/);
  const retryStart=drawer.getByRole('button',{name:/开始导入$/});
  await expect(retryStart).toBeEnabled({timeout:15_000});
  await expect(retryStart).not.toHaveClass(/ant-btn-loading/);
  await page.locator('.ant-notification').getByRole('button',{name:/关闭|Close/}).click();
  await retryStart.focus();
  await page.keyboard.press('Enter');
  await expect(drawer.getByText(/uncertain.pdf：上传失败/)).toBeVisible();
  expect(creates[0]!.key).toBeTruthy();
  expect(creates[1]).toEqual(creates[0]);
  expect(completions).toHaveLength(0);
  await expect(drawer.getByRole('combobox',{name:'上传来源渠道'})).toBeDisabled();
  await expect(drawer.getByRole('combobox',{name:'选择已解析成功的 JD'})).toBeDisabled();
  await drawer.getByRole('button',{name:/^(关闭|Close)$/}).click();
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  await expect(drawer.getByText('uncertain.pdf',{exact:true})).toBeVisible();
  await expect(retryStart).toBeEnabled();
  await expect(retryStart).not.toHaveClass(/ant-btn-loading/);
  await retryStart.focus();
  await page.keyboard.press('Enter');
  await expect(drawer.getByText(/批次汇总未完成/)).toBeVisible();
  expect(creates).toHaveLength(2);
  expect(uploads.map(item=>item.name)).toEqual(['accepted.pdf','uncertain.pdf','uncertain.pdf']);
  expect(uploads[1]!.key).toBeTruthy();
  expect(uploads[2]).toEqual(uploads[1]);
  expect(uploads.every(item=>item.path==='/api/v1/intake-batches/batch-original/files')).toBe(true);
  expect(imports).toEqual(['/api/v1/intakes/accepted/import','/api/v1/intakes/uncertain/import']);
  await expect(drawer.getByText('uncertain.pdf',{exact:true})).toHaveCount(0);
  expect((await new AxeBuilder({page}).include('[role="dialog"]').analyze()).violations).toEqual([]);
  expect(await drawer.evaluate(node=>node.scrollWidth-node.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:testInfo.outputPath('upload-recovery.png')});
  await drawer.getByRole('button',{name:'重试完成批次'}).focus();
  await page.keyboard.press('Enter');
  await expect(drawer.getByRole('group',{name:'成功 0，失败 0，解析中 2'})).toBeVisible();
  await drawer.getByRole('button',{name:/^(关闭|Close)$/}).click();
  await expect(drawer).not.toBeVisible();
  expect(completions).toEqual(['/api/v1/intake-batches/batch-original/complete','/api/v1/intake-batches/batch-original/complete']);
  expect(creates).toHaveLength(2);
  expect(uploads).toHaveLength(3);
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  const reopenedStart=drawer.getByRole('button',{name:/开始导入$/});
  await expect(reopenedStart).toBeDisabled();
  await expect(reopenedStart).not.toHaveClass(/ant-btn-loading/);
  await expect(drawer.getByText('uncertain.pdf',{exact:true})).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('keeps source-channel recovery reachable before opening the upload drawer',async({page})=>{
  await reviewWorkspace(page);
  let unavailable=true;
  await page.route('**/api/v1/source-channels?*',route=>route.fulfill(unavailable
    ?{status:403,json:{title:'暂不可用',status:403,detail:'渠道读取失败',code:'FORBIDDEN',recoverable:false}}
    :{json:{items:[{id:'channel-a',name:'手工上传',status:'ACTIVE'}],hasMore:false,nextCursor:null}}));
  await page.goto('/talents');
  await page.getByRole('button',{name:'批量上传',exact:true}).click();
  await expect(page.getByText('上传来源加载失败',{exact:true})).toBeVisible();
  await expect(page.getByRole('dialog',{name:'批量导入简历'})).not.toBeVisible();
  unavailable=false;
  await page.getByRole('button',{name:/^重\s*试$/}).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog',{name:'批量导入简历'})).toBeVisible();
  await expect(page.getByRole('combobox',{name:'上传来源渠道'})).toBeEnabled();
});

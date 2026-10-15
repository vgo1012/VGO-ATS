import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('待筛选抽屉保留原列表状态，只有待筛选提供入口',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/talents?view=pool&pool_q=保留搜索');
  const shortcut=page.getByRole('button',{name:'收件校验',exact:true});
  await expect(shortcut).toBeEnabled();
  await shortcut.focus();await page.keyboard.press('Enter');
  const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
  await expect(drawer).toBeVisible();
  await expect(drawer.getByText('当前条件下暂无导入任务')).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/talents');
  await drawer.getByLabel('关键词',{exact:true}).fill('收件测试');
  await drawer.getByRole('button',{name:/^查\s*询$/}).click();
  expect(new URL(page.url()).searchParams.get('pool_q')).toBe('保留搜索');
  expect(new URL(page.url()).searchParams.get('query')).toBe('收件测试');
  await drawer.getByRole('button',{name:/^重\s*置$/}).click();
  expect(new URL(page.url()).searchParams.get('pool_q')).toBe('保留搜索');
  expect(new URL(page.url()).searchParams.get('intake')).toBe('1');
  await expect(drawer.getByRole('button',{name:'批量上传简历'})).toHaveCount(0);
  for(const width of testInfo.project.name==='desktop'?[1280,1440,1920]:[390,768]){
    await page.setViewportSize({width,height:900});
    expect(await drawer.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBe(true);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:testInfo.outputPath(`intake-drawer-${width}.png`),fullPage:true});
  }
  expect((await new AxeBuilder({page}).include('.intake-drawer').analyze()).violations).toEqual([]);
  await drawer.getByRole('button',{name:'关闭',exact:true}).click();
  await expect(drawer).not.toBeVisible();
  await expect(shortcut).toBeFocused();
  const candidateSearch=page.getByRole('search',{name:'搜索候选人'});
  await expect(candidateSearch.getByText('保留搜索',{exact:true})).toBeVisible();
  await expect(candidateSearch.getByRole('textbox',{name:'搜索候选人'})).toHaveValue('');
  await page.goto('/settings/preferences');
  await expect(page.getByRole('navigation',{name:'设置导航'})).toBeVisible();
  await expect(page.getByRole('link',{name:'收件校验',exact:true})).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('旧收件地址携带原筛选进入待筛选抽屉，刷新仍可恢复',async({page})=>{
  await reviewWorkspace(page);
  await page.goto('/settings/intakes?query=历史文件&importStatus=PENDING');
  const drawer=page.getByRole('dialog',{name:'收件校验',exact:true});
  await expect(drawer).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/talents');
  await expect(drawer.getByLabel('关键词',{exact:true})).toHaveValue('历史文件');
  await page.reload();
  await expect(drawer).toBeVisible();
  await expect(drawer.getByLabel('关键词',{exact:true})).toHaveValue('历史文件');
});

test('只读用户通过新旧链接均不显示上传和收件入口，也不请求收件',async({page})=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/me',route=>route.fulfill({json:{userId:'hr-a',displayName:'陈佳',permissions:['talent.read'],roles:['HR'],superAdmin:false}}));
  const intakeRequests:string[]=[];
  page.on('request',request=>{if(new URL(request.url()).pathname==='/api/v1/intakes')intakeRequests.push(request.url());});
  for(const view of ['pool','todo']){
    await page.goto(`/talents?view=${view}&intake=1`);
    await expect(page.getByRole('search',{name:'搜索候选人'})).toBeVisible();
    await expect(page.getByRole('button',{name:'收件校验',exact:true})).toHaveCount(0);
    await expect(page.getByRole('button',{name:'批量上传',exact:true})).toHaveCount(0);
    await expect(page.getByRole('dialog',{name:'收件校验',exact:true})).toHaveCount(0);
  }
  expect(intakeRequests).toEqual([]);
});

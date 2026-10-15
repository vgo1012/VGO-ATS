import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';
import { mockRecruitmentAnalysis } from './fixtures/recruitment-analysis';

for(const width of [390,1280,1440])test(`蓝色工作台在 ${width}px 保持内容清晰、对比度和局部滚动`,async({page},info)=>{
  test.skip(info.project.name==='mobile','desktop project covers these viewport sizes');
  await page.setViewportSize({width,height:900});
  await reviewWorkspace(page);
  await page.route('**/api/v1/candidates/count?**',route=>route.fulfill({json:{count:1,nextCursor:null,hasMore:false}}));
  await page.goto('/talents');
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'待筛选',exact:true})).toHaveCount(0);
  const primary=page.getByRole('button',{name:'批量上传',exact:true});
  await expect(primary).toHaveCSS('background-color','rgb(37, 99, 235)');
  await primary.hover();
  await page.mouse.down();
  try { await expect(primary).toHaveCSS('background-color','rgb(30, 64, 175)'); }
  finally { await page.mouse.move(0,0); await page.mouse.up(); }
  if(width===390)await expect(page.locator('.mobile-bottom-nav .workspace-nav button').first()).toHaveCSS('font-size','12px');
  const current=page.locator('.workspace-nav:visible [aria-current="page"]');
  await expect(current).toHaveCSS('color','rgb(255, 255, 255)');
  const background=await page.locator('.app-shell').evaluate(node=>{
    const wash=getComputedStyle(node,'::before');
    return {image:wash.backgroundImage,blur:wash.filter,pointerEvents:wash.pointerEvents};
  });
  expect(background.image).toContain('radial-gradient');
  expect(background.blur).toBe('blur(36px)');
  expect(background.pointerEvents).toBe('none');
  await expect(page.locator('.talent-ledger-card')).toHaveCSS('filter','none');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({page}).include('.app-shell').withRules(['color-contrast']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath(`blue-talents-${width}.png`),fullPage:true,animations:'disabled'});
  await mockRecruitmentAnalysis(page);
  await page.goto('/recruitment-analysis');
  await expect(page.getByRole('heading',{name:'招聘分析',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'刷新',exact:true})).toHaveCSS('background-color','rgb(37, 99, 235)');
  await expect(page.getByRole('heading',{name:'招聘流程转化',exact:true})).toHaveCSS('filter','none');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({page}).include('.app-shell').withRules(['color-contrast']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath(`blue-analytics-${width}.png`),fullPage:true,animations:'disabled'});
});

test('候选人详情仅虚化弹窗背景，关闭回到列表操作',async({page})=>{
  await reviewWorkspace(page);await page.goto('/talents');
  const open=page.getByRole('button',{name:'详情与版本',exact:true});
  await open.click();
  const dialog=page.getByRole('dialog',{name:'林雨桐',exact:true});
  await expect(dialog).toBeVisible();
  await expect(page.locator('.workspace-dialog-root .ant-modal-mask:visible')).toHaveCSS('backdrop-filter','blur(4px)');
  await expect(dialog).toHaveCSS('filter','none');
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(open).toBeFocused();
});

test('蓝色批量上传按钮打开抽屉，关闭后返回原按钮',async({page})=>{
  await reviewWorkspace(page);await page.goto('/talents');
  const open=page.getByRole('button',{name:'批量上传',exact:true});
  await open.click();
  const drawer=page.getByRole('dialog',{name:'批量上传简历',exact:true});
  await expect(drawer).toBeVisible();
  await expect(drawer).toHaveCSS('filter','none');
  await expect(drawer.getByRole('button',{name:'开始上传',exact:true})).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(drawer).not.toBeVisible();
  await expect(open).toBeFocused();
});

import {expect,test} from '@playwright/test';
import {reviewWorkspace} from './fixtures/ux-review';
import AxeBuilder from '@axe-core/playwright';

test('settings and personal menus omit task exports, and the removed route does not load its APIs',async({page},info)=>{
  await reviewWorkspace(page);
  const removedRequests: string[]=[];
  page.on('request',request=>{
    const path=new URL(request.url()).pathname;
    if(path.includes('/exports')||path.includes('/overdue-thresholds')) removedRequests.push(path);
  });
  await page.goto('/talents');
  await expect(page.getByRole('button',{name:'提醒时间',exact:true})).toHaveCount(0);
  await page.goto('/admin/scoring');
  if(info.project.name==='mobile') {
    const picker=page.getByRole('combobox',{name:'切换设置页面'});
    await expect(picker).toBeVisible();
    await expect(page.locator('.settings-mobile-navigation')).toContainText('评分方案');
    await expect(page.locator('.settings-mobile-navigation')).not.toContainText(/导出|逾期阈值/);
    await picker.click();
    await expect(page.locator('.ant-select-dropdown:visible')).not.toContainText(/导出|逾期阈值/);
  } else {
    await expect(page.getByRole('navigation',{name:'设置导航'})).not.toContainText(/导出|逾期阈值/);
  }
  await page.getByRole('button',{name:'打开陈佳的个人菜单'}).click();
  await expect(page.getByRole('menu')).not.toContainText('审计');
  await expect(page.getByRole('menu')).toContainText('工作区设置');
  await expect(page.getByRole('menu')).not.toContainText(/导出|逾期阈值/);
  await page.keyboard.press('Escape');
  await page.goto('/admin/tasks');
  await expect(page.getByText('页面不存在',{exact:true})).toBeVisible();
  expect(removedRequests).toEqual([]);
});

test('personal weights remain compact and all five proportions can be read on a phone',async({page},info)=>{
  await reviewWorkspace(page);
  await page.goto('/settings/preferences');
  await expect(page.getByText('技术岗位通用比例',{exact:true}).filter({visible:true})).toBeVisible();
  if(info.project.name==='mobile') {
    const list=page.getByRole('region',{name:'个人权重方案列表'});
    await expect(list).toBeVisible();
    for(const label of ['核心技能','岗位经历','相关年限','职责匹配','行业与项目']) await expect(list.getByText(label,{exact:true})).toBeVisible();
    await expect(list).toContainText('23%');
    const edit=list.getByRole('button',{name:'编辑技术岗位通用比例'});
    const box=await edit.boundingBox();
    expect(box!.x+box!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    await edit.click();
    await expect(page.getByRole('dialog')).toContainText('编辑个人筛选方案');
  } else {
    const panel=await page.locator('.weight-profile-panel').boundingBox();
    expect(panel!.height).toBeLessThan(360);
  }
});

test('report columns stay readable and a failed query never presents a zero result',async({page})=>{
  await reviewWorkspace(page);
  await page.goto('/admin/reports');
  await expect(page.getByRole('cell',{name:'统计所选日期范围内成功入库的简历版本。同一候选人的不同简历版本分别计入。',exact:true})).toBeVisible();
  const metricHeader=page.getByRole('columnheader',{name:'指标',exact:true});
  expect((await metricHeader.boundingBox())!.width).toBeGreaterThanOrEqual(90);
  await page.route('**/api/v1/reports/ai-effect**',route=>route.fulfill({status:503,json:{title:'报表暂时不可用',status:503,detail:'请稍后重试'}}));
  await page.getByText('AI 效果',{exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('报表暂时不可用');
  await expect(page.getByText('解析覆盖率',{exact:true})).toHaveCount(0);
});

test('matching starts from the selected job without presenting unqueried zero statistics',async({page})=>{
  await reviewWorkspace(page);
  await page.goto('/matching');
  await expect(page.getByRole('button',{name:'选择招聘岗位',exact:true})).toBeVisible();
  await expect(page.getByRole('region',{name:'当前筛选统计'})).toHaveCount(0);
  await expect(page.locator('.screening-metrics')).toHaveCount(0);
  await expect(page.getByText('暂无公司默认的评分结果',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'维护个人方案',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'维护个人方案',exact:true})).toContainText('技术岗位通用比例');
});

test('desktop calendar shows all seven days while phone scrolling stays local',async({page},info)=>{
  await reviewWorkspace(page);
  for(const width of info.project.name==='mobile'?[390]:[1280,1440,1920]) {
    await page.setViewportSize({width,height:900});
    await page.goto('/schedule');
    const days=page.locator('.interview-week-grid');
    await expect(days).toBeVisible();
    const container=await page.locator('.interview-calendar .ant-card-body').boundingBox();
    const sunday=await days.locator('section > header').last().boundingBox();
    if(width>=1280) expect(sunday!.x+sunday!.width).toBeLessThanOrEqual(container!.x+container!.width+1);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
});

test('workspace status, empty tables and inline links remain accessible',async({page})=>{
  await reviewWorkspace(page);
  for(const path of ['/recruitment','/schedule','/admin/identity','/admin/scoring','/recruitment/setup']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.ant-spin-spinning:visible')).toHaveCount(0);
    const results=await new AxeBuilder({page}).include('.app-shell').analyze();
    expect(results.violations,path).toEqual([]);
  }
});

test('candidate pool exposes a single heading with keyboard accessible selection',async({page})=>{
  await reviewWorkspace(page);
  await page.goto('/talents');
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('tablist',{name:'简历库视图'})).toHaveCount(0);
  await expect(page.getByText('林雨桐',{exact:true})).toBeVisible();
  const select=page.getByRole('checkbox',{name:'全选本页候选人'});
  await select.focus();await page.keyboard.press('Space');
  await expect(select).toBeChecked();
  expect((await new AxeBuilder({page}).include('.app-shell').analyze()).violations).toEqual([]);
});

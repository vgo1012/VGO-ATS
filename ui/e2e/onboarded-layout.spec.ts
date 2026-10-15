import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reviewWorkspace } from './fixtures/ux-review';

test('已入职筛选、跨页选择、岗位恢复与共享库布局一致', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const requests: URLSearchParams[] = [];
  await page.route('**/api/v1/jds?*',route=>route.fulfill({json:{items:[
    {id:'jd-other-new',code:'JD-100',title:'其他人新岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-b',ownerDepartmentId:'dept-b',createTime:'2026-09-20T00:00:00Z'},
    {id:'jd-a',code:'JD-9',title:'高级前端工程师',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:2,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-09T00:00:00Z'},
    {id:'jd-other-old',code:'JD-20',title:'其他人旧岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-b',ownerDepartmentId:'dept-b',createTime:'2026-09-08T00:00:00Z'},
    {id:'jd-mine-new',code:'JD-10',title:'我的新岗位',clientId:null,clientName:null,status:'ACTIVE',currentVersionNo:1,ownerUserId:'hr-a',ownerDepartmentId:'dept-a',createTime:'2026-09-10T00:00:00Z'},
  ],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications?*', route => {
    const params = new URL(route.request().url()).searchParams;
    requests.push(params);
    return route.fulfill({ json: { items: [{ id: params.has('cursor') ? 'app-b' : 'app-a', candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'bound-resume', ownerUserId: 'hr-a', currentStage: 'OFFER', onboardingStatus: 'IN_PROGRESS', onboardingStartedAt: '2026-09-01T00:00:00Z', actualOnboardedAt: null, onboardingElapsedSeconds: 90000 }], hasMore: !params.has('cursor'), nextCursor: params.has('cursor') ? null : 'next' } });
  });
  await page.route('**/api/v1/resume-version-fact-summaries?*', route => route.fulfill({ json: [{ resumeVersionId:'bound-resume', facts:{ snapshot: { createdAt: '2026-09-06T01:00:00Z' }, educations: [{ degreeName: '本科' }], workExperiences: [{ startDate: '2020-01-01', endDate: '2025-01-01' }], skills: ['Java','Spring','MySQL','Redis','Docker','Kubernetes','Git','Linux','Terraform','Nginx'].map(skillName=>({id:skillName,skillName})) } }] }));
  await page.route('**/api/v1/resume-version-sources?*', route => route.fulfill({ json: [{ resumeVersionId:'bound-resume', source:{ sourceChannelName: '内推' } }] }));
  await page.goto('/onboarded');
  const form = page.getByRole('form', { name: '简历筛选条件' });
  for (const name of [ '候选人', '年龄', '求职岗位', '工作年限', '学历', '招聘流程', '入职状态', '核心技能', '来源 / 归属人', '入职操作', '操作']) await expect(page.getByRole('columnheader', { name, exact: true })).toBeVisible();
  const row=page.locator('tr[data-row-key="app-a"]');
  await expect(row.getByText('入职中',{exact:true})).toBeVisible();
  await expect(row.getByText('已历时 1天1小时',{exact:true})).toBeVisible();
  await expect(row.getByRole('button',{name:'确认实际入职'})).toBeVisible();
  await expect(row.locator('.candidate-skills-preview > span')).toHaveCount(8);
  const height=(await row.boundingBox())!.height;
  await row.getByRole('button',{name:'查看全部 10 项技能'}).click();
  const skills=page.getByRole('region',{name:'全部核心技能'});
  await expect(skills.getByText('Terraform',{exact:true})).toBeVisible();
  expect((await row.boundingBox())!.height).toBe(height);
  await skills.getByRole('button',{name:'收起'}).click();
  await expect(page.locator('tr[data-row-key="app-a"]')).toContainText('内推');
  await page.getByRole('combobox', { name: '求职岗位（选择 JD）' }).click();
  await expect.poll(()=>page.locator('.jd-selector-popup .ant-select-item-option-content').allTextContents()).toEqual([
    expect.stringContaining('JD-10'),expect.stringContaining('JD-9'),expect.stringContaining('JD-100'),expect.stringContaining('JD-20'),
  ]);
  await page.locator('.ant-select-item-option-content').filter({ hasText: '高级前端工程师' }).click();
  await page.getByRole('textbox', { name: '入职流程关键词搜索', exact: true }).fill('林');
  for (const value of ['本科', '3-5 年', '25-30', '女']) await form.getByRole('button', { name: value, exact: true }).click();
  await expect(form.getByRole('button', { name: /^筛\s*选$/ })).toHaveCount(0);
  await expect.poll(() => ({
    q: requests.at(-1)?.get('q'),
    jdId: requests.at(-1)?.get('jdId'),
    degrees: requests.at(-1)?.get('degrees'),
    maxWorkYears: requests.at(-1)?.get('maxWorkYears'),
    maxAge: requests.at(-1)?.get('maxAge'),
    gender: requests.at(-1)?.get('gender'),
  })).toEqual({ q: '林', jdId: 'jd-a', degrees: '本科', maxWorkYears: '5', maxAge: '29', gender: 'FEMALE' });
  await page.reload();
  await expect(form.getByRole('button', { name: '本科', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('checkbox', { name: '全选本页入职流程记录' }).check();
  await expect(page.locator('tr[data-row-key="app-a"] input[type="checkbox"]')).toBeChecked();
  await page.getByRole('button', { name: '下一页' }).click();
  await expect(page.locator('tr[data-row-key="app-b"] input[type="checkbox"]')).not.toBeChecked();
  await page.getByRole('checkbox', { name: '全选本页入职流程记录' }).uncheck();
  await expect(page.locator('tr[data-row-key="app-b"] input[type="checkbox"]')).not.toBeChecked();
  await form.getByRole('button', { name: '高中', exact: true }).click();
  await expect(page.getByLabel('入职流程分页').getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await expect.poll(() => requests.at(-1)?.get('degrees')).toBe('高中');
  expect(requests.at(-1)?.has('cursor')).toBe(false);
  await form.getByRole('button', { name: /重\s*置/ }).click();
  await expect.poll(() => requests.at(-1)?.has('degrees')).toBe(false);
  await page.getByRole('button', { name: '清空岗位筛选' }).click();
  await expect(page.getByLabel('入职流程分页').getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('1');
  await expect.poll(() => requests.at(-1)?.has('jdId')).toBe(false);
  for (const width of [390, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await expect(form.getByRole('button', { name: '本科', exact: true })).toHaveCSS('height', width >= 768 ? '30px' : '44px');
    await page.screenshot({ path: testInfo.outputPath(`onboarded-${width}.png`), fullPage: true });
  }
  await page.mouse.move(0,0);
  expect((await new AxeBuilder({ page }).include('.talent-filters').include('.talent-toolbar').analyze()).violations).toEqual([]);
  expect(requests.every(params => params.get('stage') === 'ONBOARDED' && params.get('onboarding') === 'true')).toBe(true);
});

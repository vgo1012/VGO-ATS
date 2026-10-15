import { expect, test } from '@playwright/test';
import { analyticsActor, mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

const employees = Array.from({ length: 12 }, (_, index) => ({
  ownerUserId: `employee-${index}`, displayName: index === 0 ? '招聘负责人' : index === 11 ? '姓名较长的零业务记录员工' : `招聘员工${index}`,
  self: index === 0, inactive: index === 11,
  resumes: index === 11 ? 0 : 20, applications: index === 11 ? 0 : 10,
  interview1: index === 11 ? 0 : 8, interview2: index === 11 ? 0 : 5, interview3Plus: index === 11 ? 0 : 2,
  interviews: index === 11 ? 0 : 8, offers: index === 11 ? 0 : 2, rejected: index === 11 ? 0 : 1, onboarded: index === 11 ? 0 : 1,
}));

test('员工表在看板下方分页展示，搜索仅影响员工，保留零数据及键盘操作', async ({ page }, testInfo) => {
  const report = { ...recruitmentAnalysis, risk: { activeBlacklists: 0 }, hrWorkload: employees };
  const state = await mockRecruitmentAnalysis(page, { report });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const panel = page.getByRole('region', { name: '员工招聘统计' });
  await expect(panel.getByRole('heading')).toBeVisible();
  await expect(panel.getByRole('row', { name: /招聘负责人 我/ })).toBeVisible();
  const total = panel.getByText('共 12 位员工');
  await expect(total).toBeVisible();
  const pager = panel.locator('.ant-table-pagination');
  const [totalBox, pagerBox] = await Promise.all([total.boundingBox(), pager.boundingBox()]);
  expect(Math.abs(totalBox!.y + totalBox!.height / 2 - pagerBox!.y - pagerBox!.height / 2)).toBeLessThan(2);
  expect(totalBox!.x).toBeLessThan(pagerBox!.x + pagerBox!.width / 2);
  const dashboard = page.locator('.analytics-dashboard');
  const originalBounds = await dashboard.boundingBox();
  const panelBounds = await panel.boundingBox();
  expect(panelBounds!.y).toBeGreaterThanOrEqual(originalBounds!.y + originalBounds!.height);
  await expect(panel.getByRole('columnheader')).toHaveText(['员工', '入库简历人数', '进入流程', '一面', '二面', '三面+', 'Offer', '不通过', '入职']);
  await expect(panel.locator('.ant-table')).toHaveCSS('font-size', '14px');
  const applicationsHeader = panel.getByRole('columnheader', { name: '进入流程' });
  await expect(applicationsHeader).toHaveCSS('font-size', '13px');
  await expect(applicationsHeader).toHaveCSS('font-weight', '500');
  await expect(applicationsHeader).toHaveCSS('text-align', 'center');
  await expect(panel.getByRole('row', { name: /招聘负责人 我/ }).getByRole('cell').nth(2)).toHaveCSS('text-align', 'center');
  await panel.getByTitle('2', { exact: true }).click();
  await expect(panel.getByRole('row', { name: /姓名较长的零业务记录员工 已停用 0 0 0 0 0 0 0 0/ })).toBeVisible();
  const requestCount = state.reportRequests.length;
  const personnel = panel.getByRole('combobox', { name: '招聘人员' });
  await personnel.fill('招聘负责人');
  await expect(page.getByRole('option', { name: '招聘负责人（我）', exact: true })).toBeVisible();
  await personnel.press('ArrowDown');
  await personnel.press('Enter');
  await expect(panel.getByRole('row', { name: /招聘负责人 我/ })).toBeVisible();
  await expect(panel.getByRole('row', { name: /姓名较长/ })).toHaveCount(0);
  await expect(total).toBeVisible();
  expect(state.reportRequests).toHaveLength(requestCount);
  await expect(dashboard.locator('.analytics-funnel-stage').first().locator('strong .sr-only')).toHaveText('120');
  const afterBounds = await dashboard.boundingBox();
  expect(afterBounds!.height).toBeCloseTo(originalBounds!.height, 0);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await personnel.fill('');
  await page.getByRole('option', { name: '全部人员', exact: true }).click();
  await personnel.press('Escape');
  if (testInfo.project.name === 'mobile') {
    const scroll = panel.locator('.ant-table-content');
    await scroll.evaluate(element => { element.scrollLeft = element.scrollWidth; });
    await expect(panel.getByRole('cell', { name: '招聘负责人 我', exact: true })).toBeInViewport();
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: testInfo.outputPath('employee-statistics.png'), fullPage: true, animations: 'disabled' });
});

test('普通 HR 保留个人整页并且授权撤销后移除员工表', async ({ page }) => {
  const report = { ...recruitmentAnalysis, risk: { activeBlacklists: 0 }, hrWorkload: null };
  const state = await mockRecruitmentAnalysis(page, { report, actor: { ...analyticsActor, roles: ['RECRUITER'] } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  await expect(page.getByRole('heading', { name: '招聘流程转化' })).toBeVisible();
  await expect(page.getByRole('region', { name: '员工招聘统计' })).toHaveCount(0);
  const authorizedReport = { ...report, hrWorkload: employees };
  state.report = authorizedReport;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByRole('region', { name: '员工招聘统计' })).toBeVisible();
  state.reportStatus = 403;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByRole('region', { name: '员工招聘统计' })).toHaveCount(0);
});

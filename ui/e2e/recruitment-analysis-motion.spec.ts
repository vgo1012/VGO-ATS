import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyRecruitmentAnalysis, mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

const motionReport = {
  ...recruitmentAnalysis,
  risk: { activeBlacklists: 3 },
  channels: recruitmentAnalysis.channels.map((row, index) => ({ ...row, resumes: index === 0 ? 80 : 40 })),
  portraits: recruitmentAnalysis.portraits.map(row => ({ ...row, resumes: row.passed + row.rejected + row.pending + row.withdrawn })),
};

test('招聘分析文字、筛选与图表满足无障碍要求', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: motionReport });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  await expect(page.getByRole('heading', { level: 1, name: '招聘分析' })).toBeVisible();
  const result = await new AxeBuilder({ page }).include('.analytics-page').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations).toEqual([]);
});

test('招聘分析短过渡后读数准确且不保留循环装饰动画', async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await mockRecruitmentAnalysis(page, { report: motionReport });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  await expect(page.locator('.analytics-page')).toHaveAttribute('data-motion', 'on');
  const firstStage = page.locator('.analytics-funnel-stage').first();
  await expect(firstStage.locator('.sr-only')).toHaveText('120');
  await expect(firstStage.locator('strong [aria-hidden="true"]')).toHaveText('120');
  await expect(firstStage.getByText('人', { exact: true })).toBeVisible();
  await expect(page.locator('.reference-channel-donut svg')).toBeVisible();
  await expect(page.getByRole('list', { name: '入库简历工作城市数据' })).toContainText('上海');
  await expect(page.locator('.analytics-ambient,.analytics-city-chart,.analytics-funnel-dots,.analytics-hc-grid')).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: '动效速度' })).toHaveCount(0);
  const continuousAnimations = await page.locator('.analytics-page').evaluate(element => element.getAnimations({ subtree: true }).filter(animation => animation.effect?.getTiming().iterations === Infinity).length);
  expect(continuousAnimations).toBe(0);
  await page.screenshot({ path: testInfo.outputPath('analytics-short-motion.png'), fullPage: true });
  expect(errors).toEqual([]);
});

test('减少动态效果和隐藏页面保留最终读数及刷新恢复', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: motionReport });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  await expect(page.locator('.analytics-page')).toHaveAttribute('data-motion', 'off');
  const firstStage = page.locator('.analytics-funnel-stage').first();
  await expect(firstStage.locator('strong [aria-hidden="true"]')).toHaveText('120');
  await expect(page.locator('.reference-channel-donut svg')).toBeVisible();
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect(page.locator('.analytics-page')).toHaveAttribute('data-motion', 'on');
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')); });
  await expect(page.locator('.analytics-page')).toHaveAttribute('data-motion', 'off');
  let releaseRequest = () => {};
  const heldRequest = new Promise<void>(resolve => { releaseRequest = resolve; });
  await page.route('**/api/v1/reports/recruitment-analysis**', async route => { await heldRequest; await route.fallback(); });
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByRole('button', { name: '刷新', exact: true })).toHaveClass(/ant-btn-loading/);
  releaseRequest();
  await expect(page.getByRole('button', { name: '刷新', exact: true })).not.toHaveClass(/ant-btn-loading/);
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')); });
  await expect(page.locator('.analytics-page')).toHaveAttribute('data-motion', 'on');
});

test('空数据刷新失败可重试，权限撤销后清除可见报表', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page, { report: emptyRecruitmentAnalysis });
  await page.goto('/recruitment-analysis');
  await expect(page.getByRole('heading', { name: '招聘需求与风险', exact: true })).toBeVisible();
  await expect(page.locator('.analytics-summary .sr-only')).toHaveText(['0', '0', '0', '0']);
  state.reportStatus = 503;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByText('招聘分析加载失败', { exact: true })).toBeVisible();
  state.reportStatus = 200;
  await page.getByRole('button', { name: '重试', exact: true }).click();
  await expect(page.getByText('招聘分析加载失败', { exact: true })).toHaveCount(0);
  state.reportStatus = 403;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect(page.getByText('暂无招聘分析查看权限', { exact: true })).toBeVisible();
  await expect(page.locator('.analytics-dashboard')).toHaveCount(0);
});

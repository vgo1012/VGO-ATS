import { expect, test } from '@playwright/test';
import { mockRecruitmentAnalysis } from './fixtures/recruitment-analysis';

test('手机流程可用键盘滑至不通过结果，平板分析区块无需整页横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page);
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');

  const funnel = page.locator('.analytics-funnel');
  await expect(funnel.locator('.analytics-funnel-stage').last()).toContainText('不通过');
  const funnelWidths = await funnel.evaluate(element => ({ visible: element.clientWidth, full: element.scrollWidth }));
  expect(funnelWidths.full).toBeGreaterThan(funnelWidths.visible);
  await funnel.focus();
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => funnel.evaluate(element => element.scrollLeft)).toBeGreaterThan(0);
  await funnel.evaluate(element => { element.scrollLeft = element.scrollWidth; });
  const lastStage = await funnel.locator('.analytics-funnel-stage').last().evaluate(element => {
    const stage = element.getBoundingClientRect();
    const viewport = element.closest('.analytics-funnel')!.getBoundingClientRect();
    return { stageLeft: stage.left, stageRight: stage.right, viewportLeft: viewport.left, viewportRight: viewport.right };
  });
  expect(lastStage.stageLeft).toBeGreaterThanOrEqual(lastStage.viewportLeft - 1);
  expect(lastStage.stageRight).toBeLessThanOrEqual(lastStage.viewportRight + 1);
  await expect(page.locator('.analytics-funnel-stage').nth(6).locator('.analytics-funnel-arrow i')).toHaveCount(0);
  await expect(page.locator('.analytics-funnel-outcome .analytics-funnel-arrow i')).toHaveCount(0);

  await page.setViewportSize({ width: 768, height: 900 });
  for (const heading of ['招聘趋势', '地区分布', '招聘需求与风险', '年龄分布', '学历分布', '招聘渠道']) {
    const sectionHeading = page.getByRole('heading', { name: heading, exact: true });
    await sectionHeading.scrollIntoViewIfNeeded();
    await expect(sectionHeading).toBeInViewport();
  }
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

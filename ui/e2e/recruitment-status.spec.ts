import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('招聘流程逐行显示状态且窄屏只在表格内横向滚动', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const base = {
    candidateId: 'person-a', jdId: 'jd-a', jdVersionId: 'jd-v2', demandId: 'demand-a',
    resumeVersionId: 'resume-a', recruitmentType: 'INTERNAL', ownerUserId: 'hr-a',
    updateTime: '2026-09-07T01:00:00Z', terminalAt: null,
  };
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: {
    items: [
      { ...base, id: 'active', currentStage: 'IN_PROCESS' },
      { ...base, id: 'completed', currentStage: 'OFFER', onboardingStatus: 'IN_PROGRESS' },
      { ...base, id: 'cancelled', currentStage: 'TERMINATED', terminalAt: '2026-09-07T01:00:00Z' },
    ], hasMore: false, nextCursor: null,
  } }));

  await page.goto('/recruitment?lifecycle=ALL');
  await expect(page.locator('.pipeline-table-card tr[data-row-key]')).toHaveCount(3);
  const headers = page.getByRole('columnheader');
  const headerTexts = await headers.allTextContents();
  expect(headerTexts.indexOf('当前阶段') + 1).toBe(headerTexts.indexOf('状态'));
  expect(headerTexts.indexOf('状态') + 1).toBe(headerTexts.indexOf('负责人'));
  for (const [id, status] of [['active', '进行中'], ['completed', '已完成'], ['cancelled', '已取消']] as const) {
    await expect(page.locator(`tr[data-row-key="${id}"]`).getByRole('cell', { name: status, exact: true })).toBeVisible();
  }
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.setViewportSize({ width: 390, height: 900 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  const content = page.locator('.pipeline-table-card .ant-table-content');
  await expect.poll(() => content.evaluate(element => element.scrollWidth > element.clientWidth)).toBe(true);
  await expect(page.getByText('横向滚动表格，查看当前阶段、状态和操作')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('recruitment-scroll-hint.png'), animations: 'disabled' });
  const header = page.locator('.pipeline-table-card thead tr').first();
  await header.focus();
  await header.press('ArrowRight');
  await expect.poll(() => content.evaluate(element => element.scrollLeft)).toBeGreaterThan(0);
});

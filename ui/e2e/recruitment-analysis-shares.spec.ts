import { expect, test } from '@playwright/test';
import { mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

test('漏斗百分比与本阶段对齐并明确流程分母', async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const counts: Record<string, number> = { RESUME: 267, APPLICATION: 18, INTERVIEW_1: 11, INTERVIEW_2: 8, INTERVIEW_3_PLUS: 6, OFFER: 5, ONBOARDED: 2 };
  await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis,
    funnel: recruitmentAnalysis.funnel.map(row => ({ ...row, count: counts[row.key] ?? 0 })),
    portraits: [{ dimension: 'AGE', label: '未知', resumes: 267, passed: 2, rejected: 2, pending: 14, withdrawn: 0 }],
  } });
  await page.goto('/recruitment-analysis');
  const stages = page.locator('.analytics-funnel-stage');
  await expect(stages.nth(0)).toContainText('按人去重');
  await expect(stages.nth(1)).toContainText('比例基数');
  for (const [index, label, numerator, percentage] of [[2, '一面', 11, 61], [3, '二面', 8, 44], [4, '三面+', 6, 33], [5, 'Offer', 5, 28], [6, '已入职', 2, 11], [7, '不通过', 2, 11]] as const) {
    const stage = stages.nth(index);
    await stage.scrollIntoViewIfNeeded();
    const share = stage.getByRole('img', { name: `${label}占进入流程比例 ${percentage}%（${numerator} ÷ 18）` });
    await expect(share).toBeVisible();
    await expect(share).toHaveText(`占流程 ${percentage}%`);
    const headingBox = await stage.locator('.analytics-funnel-step > span').boundingBox();
    const shareBox = await share.boundingBox();
    expect(Math.abs(shareBox!.x - headingBox!.x)).toBeLessThan(2);
    expect(shareBox!.y).toBeGreaterThan(headingBox!.y + headingBox!.height);
  }
  await expect(page.getByRole('note', { name: '漏斗统计口径' })).toHaveCount(0);
  await expect(page.locator('.analytics-conversion-summary')).toContainText('流程至 Offer 28%');
  await expect(page.locator('.analytics-conversion-summary')).toContainText('流程至入职 11%');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.locator('.analytics-funnel').evaluate(element => { element.scrollLeft = 0; });
  await page.locator('#analysis-funnel').screenshot({ path: testInfo.outputPath('funnel-shares.png'), animations: 'disabled' });
});

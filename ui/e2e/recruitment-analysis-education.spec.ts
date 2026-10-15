import { expect, test } from '@playwright/test';
import { mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

test('学历分布从高到低展示且未知置后，保留各项数量占比', async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis, portraits: [
    ...recruitmentAnalysis.portraits.filter(row => row.dimension !== 'EDUCATION'),
    ...[{ label: '专科', count: 45 }, { label: '未知', count: 15 }, { label: '本科', count: 36 }, { label: '硕士', count: 2 }, { label: '高中/中专', count: 2 }]
      .map(({ label, count }) => ({ dimension: 'EDUCATION' as const, label, resumes: count, passed: count, rejected: 0, pending: 0, withdrawn: 0 })),
  ] } });
  await page.goto('/recruitment-analysis');
  const chart = page.locator('.reference-education-bars');
  await chart.scrollIntoViewIfNeeded();
  await expect(chart).toHaveAccessibleName('硕士 2，2%；本科 36，36%；专科 45，45%；高中/中专 2，2%；未知 15，15%');
  await expect(chart.locator('svg')).toBeVisible();
  const labels = ['硕士', '本科', '专科', '高中/中专', '未知'];
  const positions: number[] = [];
  for (const label of labels) {
    const axisLabel = chart.locator('svg text').filter({ hasText: new RegExp(`^${label}$`) });
    await expect(axisLabel).toBeVisible();
    positions.push((await axisLabel.boundingBox())!.x);
  }
  expect(positions).toEqual([...positions].sort((left, right) => left - right));
  await page.locator('.analytics-education-panel').screenshot({ path: testInfo.outputPath('education-order.png'), animations: 'disabled' });
});

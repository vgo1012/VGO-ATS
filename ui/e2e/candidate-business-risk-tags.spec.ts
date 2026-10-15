import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('候选人表格只展示流程标签，黑名单统一留在招聘详情', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  let flow = false;
  let blacklistReads = 0;
  await page.route('**/api/v1/candidate-pool-statuses?**', route => route.fulfill({ json: [{ candidateId: 'person-a', resumeVersionId: 'resume-a', jobContexts: flow ? [{ applicationId: 'app-a', status: 'ADVANCED', title: '当前岗位', resumeVersionId: 'resume-a' }] : [] }] }));
  await page.route('**/api/v1/candidates/person-a/company-blacklist-records?**', route => {
    blacklistReads++;
    return route.fulfill({ json: { active: true, current: null, records: { items: [], hasMore: false } } });
  });
  await page.goto('/talents');
  await expect(page.getByRole('columnheader', { name: '风险标签', exact: true })).toBeVisible();
  const riskColumn = (await page.locator('.ant-table-thead > tr > th').allTextContents()).findIndex(text => text.trim() === '风险标签');
  expect(riskColumn).toBeGreaterThanOrEqual(0);
  const cell = () => page.locator('.ant-table-tbody > tr.ant-table-row').first().getByRole('cell').nth(riskColumn);
  await expect(cell()).toHaveText('无');
  await page.goto('/talents?view=todo');
  await expect(page.getByRole('columnheader', { name: '风险标签', exact: true })).toBeVisible();
  await page.route('**/api/v1/me', route => route.fulfill({ json: { userId: 'hr-a', displayName: '陈佳', permissions: ['talent.read'], roles: ['HR'], superAdmin: false } }));
  await page.goto('/talents');
  await expect(cell()).toHaveText('无');
  expect(blacklistReads).toBe(0);
  await page.unroute('**/api/v1/me');
  await page.reload();
  flow = true;
  await page.reload();
  await expect(cell()).toContainText('已有招聘流程');
  await expect(cell()).not.toContainText('黑名单');
  await expect(cell()).not.toContainText('当前岗位');
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  }
  await page.screenshot({ path: testInfo.outputPath('business-tags.png') });
  flow = false;
  await page.reload();
  await expect(cell()).toHaveText('无');
  expect(blacklistReads).toBe(0);
});

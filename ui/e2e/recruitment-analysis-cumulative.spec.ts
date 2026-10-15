import { expect, test } from '@playwright/test';
import { analyticsActor, mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

for (const role of ['SUPER_ADMIN', 'HR_DIRECTOR', 'RECRUITMENT_MANAGER']) {
  test(`${role} 月初默认显示本月并可切换累计及重置`, async ({ page }, testInfo) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await mockRecruitmentAnalysis(page, { actor: { ...analyticsActor, roles: [role], superAdmin: role === 'SUPER_ADMIN' } });
    await page.clock.setFixedTime(new Date('2026-10-01T07:39:00+08:00'));
    const requests: URL[] = [];
    await page.route('**/api/v1/reports/recruitment-analysis*', route => {
      const url = new URL(route.request().url());
      requests.push(url);
      const cumulative = !url.searchParams.has('from');
      const resumes = cumulative ? 202 : 0;
      const applications = cumulative ? 14 : 0;
      return route.fulfill({ json: {
        ...recruitmentAnalysis, asOf: '2026-09-30T23:39:00Z',
        from: url.searchParams.get('from') ?? '1970-01-01T00:00:00Z',
        to: url.searchParams.get('to') ?? '2026-09-30T23:39:00.000001Z',
        funnel: [{ key: 'RESUME', label: '全部简历', count: resumes }, { key: 'APPLICATION', label: '进入流程', count: applications }],
        trend: cumulative ? [{ date: '2026-09-30', applications, offers: 0, onboarded: 0 }] : [],
        channels: [{ id: 'referral', name: '内推', resumes, applications, offers: 0, onboarded: 0 }],
        portraits: [{ dimension: 'AGE', label: '未知', resumes, passed: 0, rejected: 0, pending: 0, withdrawn: 0 }],
        hrWorkload: [{ ownerUserId: 'hr-one', displayName: '招聘同事', self: false, inactive: false,
          resumes, applications, interviews: 0, interview1: 0, interview2: 0, interview3Plus: 0, offers: 0, onboarded: 0, rejected: 0 }],
      } });
    });
    await page.goto('/recruitment-analysis?departmentId=dept-product');
    await expect(page.getByRole('radio', { name: '月', exact: true })).toBeChecked();
    await expect(page.getByLabel('选择统计期间')).toHaveValue('2026-10');
    await expect(page.locator('.analytics-funnel-stage').first().locator('.sr-only')).toHaveText('0');
    await expect(page.getByRole('row', { name: /招聘同事 0 0/ })).toBeVisible();
    await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toEqual({
      from: '2026-09-30T16:00:00.000Z', to: '2026-10-31T16:00:00.000Z', departmentId: 'dept-product',
    });
    await expect(page.locator('.analytics-period-label')).toContainText('2026.10.01 — 2026.10.31');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath('current-month.png'), fullPage: true, animations: 'disabled' });

    await page.getByLabel('统计周期', { exact: true }).getByText('累计', { exact: true }).click();
    await expect(page.getByLabel('选择统计期间')).toHaveCount(0);
    await expect(page.locator('.analytics-funnel-stage').first().locator('.sr-only')).toHaveText('202');
    await expect(page.getByRole('row', { name: /招聘同事 202 14/ })).toBeVisible();
    await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toEqual({ departmentId: 'dept-product' });
    await page.getByLabel('统计周期', { exact: true }).getByText('月', { exact: true }).click();
    await expect(page.getByLabel('选择统计期间')).toHaveValue('2026-10');
    await expect(page.locator('.analytics-funnel-stage').first()).toContainText('本期简历');
    await expect(page.locator('.analytics-funnel-stage').first().locator('.sr-only')).toHaveText('0');
    await expect(page.getByRole('row', { name: /招聘同事 0 0/ })).toBeVisible();
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toEqual({
      from: '2026-09-30T16:00:00.000Z', to: '2026-10-31T16:00:00.000Z', departmentId: 'dept-product',
    });
    await page.getByRole('button', { name: '上一周期', exact: true }).click();
    await expect(page.getByLabel('选择统计期间')).toHaveValue('2026-09');
    await page.getByRole('button', { name: '重置筛选' }).click();
    await expect(page.getByRole('radio', { name: '月', exact: true })).toBeChecked();
    await expect(page.getByLabel('选择统计期间')).toHaveValue('2026-10');
    await expect(page.locator('.analytics-funnel-stage').first().locator('.sr-only')).toHaveText('0');
    await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toEqual({
      from: '2026-09-30T16:00:00.000Z', to: '2026-10-31T16:00:00.000Z',
    });
  });
}

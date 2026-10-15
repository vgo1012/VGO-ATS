import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const superAdmin of [false, true]) {
  test(`mail resume review ${superAdmin ? 'allows super administrators' : 'denies ordinary talent managers'}`, async ({ page }) => {
    await reviewWorkspace(page);
    await page.route('**/api/v1/me', route => route.fulfill({ json: {
      userId: 'reviewer', displayName: '校验人员', workEmail: 'reviewer@example.com',
      homeDepartmentId: 'dept-a', roles: [superAdmin ? 'SUPER_ADMIN' : 'RECRUITER'],
      permissions: ['talent.manage', 'demand.read'], superAdmin,
    } }));
    let mailRequests = 0;
    await page.route('**/api/v1/mail-resume-reviews?**', route => {
      mailRequests += 1;
      return route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } });
    });
    await page.goto('/recruitment/setup');
    await expect(page.locator('.settings-content')).toBeVisible();
    const mobileNavigation = page.getByRole('combobox', { name: '切换设置页面' });
    if (await mobileNavigation.isVisible()) {
      await mobileNavigation.click();
      const entry = page.locator('.ant-select-dropdown').getByTitle('邮件简历校验', { exact: true });
      if (superAdmin) await expect(entry).toBeVisible();
      else await expect(entry).toHaveCount(0);
      await page.keyboard.press('Escape');
    } else {
      const entry = page.getByRole('link', { name: '邮件简历校验' });
      if (superAdmin) await expect(entry).toBeVisible();
      else await expect(entry).toHaveCount(0);
    }
    await page.goto('/admin/mail-resume-reviews');
    if (superAdmin) {
      await expect(page.getByRole('heading', { name: '邮件简历校验' })).toBeVisible();
      await expect(page.getByText('当前条件下没有待匹配邮件')).toBeVisible();
      expect(mailRequests).toBeGreaterThan(0);
    } else {
      await expect(page.getByText('无权访问', { exact: true })).toBeVisible();
      await expect(page.getByRole('heading', { name: '邮件简历校验' })).toHaveCount(0);
      expect(mailRequests).toBe(0);
    }
  });
}

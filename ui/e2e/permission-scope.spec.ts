import { expect, test, type Page } from '@playwright/test';

const empty = { items: [], nextCursor: null, hasMore: false };
const director = {
  userId: 'director', displayName: '周总监', workEmail: 'director@example.com', homeDepartmentId: 'root',
  roles: ['HR_DIRECTOR'], permissions: ['demand.read', 'matching.run', 'talent.read', 'recruitment.manage'],
  superAdmin: false, mustChangePassword: false, version: '1',
};
const profile = (id: string, ownerUserId: string, name: string) => ({
  profile: { id, ownerUserId, name, status: 'ACTIVE', currentVersionNo: 1, version: 1, updatedAt: '2026-09-12T00:00:00Z' },
  currentVersion: { id: `${id}-v1`, versionNo: 1, skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20 },
});

async function mockDirector(page: Page, requested: string[]) {
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    requested.push(`${path}${url.search}`);
    if (path === '/me') return route.fulfill({ json: director });
    if (path === '/weight-profiles') return route.fulfill({ json: { ...empty, items: [profile('mine', 'director', '总监本人方案'), profile('other', 'recruiter', '招聘专员方案')] } });
    if (path === '/directory/users/resolve') return route.fulfill({ json: [{ id: 'director', displayName: '周总监', status: 'ACTIVE' }, { id: 'recruiter', displayName: '陈招聘', status: 'ACTIVE' }] });
    if (path === '/directory/users') return route.fulfill({ json: { ...empty, items: [{ id: 'director', displayName: '周总监', status: 'ACTIVE' }, { id: 'recruiter', displayName: '陈招聘', status: 'ACTIVE' }] } });
    if (path === '/jds') return route.fulfill({ json: empty });
    if (path === '/clients') return route.fulfill({ json: empty });
    if (path === '/talent-search') return route.fulfill({ json: empty });
    if (path === '/candidate-batch' || path === '/candidate-resume-summaries' || path === '/resume-version-fact-summaries' || path === '/resume-version-sources' || path === '/applications/stage-counts') return route.fulfill({ json: [] });
    return route.fulfill({ json: empty });
  });
}

for (const width of [390, 1280, 1440, 1920]) {
  test(`HR director permission boundaries at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const requested: string[] = [];
    await mockDirector(page, requested);

    await page.goto('/settings/preferences');
    if (width >= 768) await expect(page.getByText('人力负责人')).toBeVisible();
    const profileList = width < 768 ? page.getByLabel('个人权重方案列表') : page.locator('.weight-profile-table');
    await expect(profileList.getByText('招聘专员方案')).toBeVisible();
    await expect(profileList.getByText('陈招聘')).toBeVisible();
    await expect(profileList.getByText('仅可查看')).toBeVisible();
    const ownProfile = width < 768 ? profileList.locator('article').filter({ hasText: '总监本人方案' }) : profileList.locator('tr').filter({ hasText: '总监本人方案' });
    await expect(ownProfile.getByRole('button', { name: /编\s*辑/ })).toBeVisible();
    expect(requested.some(value => value === '/weight-profiles?limit=20&mineOnly=false')).toBe(true);

    await page.goto('/recruitment/setup');
    await expect(page.getByRole('tab', { name: '客户管理' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: '身份与组织' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: '评分方案与岗位关联' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /新增 JD/ })).toHaveCount(0);
    await expect(page.getByText('设为公共 JD')).toHaveCount(0);

    await page.goto('/admin/identity');
    await expect(page.getByText('无权访问')).toBeVisible();
    expect(requested.some(value => value.startsWith('/admin/users') || value.startsWith('/admin/departments'))).toBe(false);

    await page.goto('/shared-library');
    await expect(page.getByRole('heading', { name: '共享简历库' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  });
}

test('shared library requires a logged-in session', async ({ page }) => {
  await page.route('**/api/v1/me', route => route.fulfill({ status: 401, json: { status: 401, title: 'Unauthorized', code: 'SESSION_REQUIRED', detail: '请登录' } }));
  await page.goto('/shared-library');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
});

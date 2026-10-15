import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const role of ['SUPER_ADMIN', 'HR_DIRECTOR', 'RECRUITMENT_MANAGER']) {
  test(`${role} 已结束流程保留历史 JD 展示且不能直接推荐`, async ({ page }, testInfo) => {
    await reviewWorkspace(page);
    const person = { id: 'person-a', fullName: '历史岗位测试候选人', ownerUserId: 'hr-a', version: 1, status: 'ACTIVE' };
    let writes = 0;
    await page.route('**/api/v1/**', route => {
      const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
      if (route.request().method() !== 'GET') writes++;
      if (path === '/me') return route.fulfill({ json: { userId: 'hr-a', displayName: role, homeDepartmentId: 'dept-a', roles: [role], superAdmin: role === 'SUPER_ADMIN', permissions: ['talent.read', 'talent.manage', 'demand.read'] } });
      if (path === '/candidates') return route.fulfill({ json: { items: [person], hasMore: false, nextCursor: null } });
      if (path === '/candidates/count') return route.fulfill({ json: { count: 1, hasMore: false, nextCursor: null } });
      if (path === '/candidate-resume-summaries') return route.fulfill({ json: [{ candidateId: person.id, resumeVersion: { id: 'resume-a', createdAt: '2026-09-07T00:00:00Z' }, facts: null, factsStatus: 'NOT_READY', source: null }] });
      if (path === '/candidate-pool-statuses') return route.fulfill({ json: [{ candidateId: person.id, resumeVersionId: 'resume-a', libraryEligible: false, stages: ['TERMINATED'], jobContexts: [{ jdId: 'jd-a', jdVersionId: 'jd-v2', jdCode: 'JD-000001', title: 'AI工程师', versionNo: 2, resumeVersionId: 'resume-a', status: 'FINISHED', stage: 'TERMINATED', applicationId: 'old-application', matchRunId: null }] }] });
      return route.fallback();
    });
    await page.goto('/talents');
    const row = page.locator('tr[data-row-key="person-a"]');
    await expect(row.getByText('历史关联：AI工程师 · JD-000001').filter({ visible: true }).first()).toBeVisible();
    await expect(row.getByText('未关联 JD', { exact: true })).toHaveCount(0);
    await row.getByRole('checkbox').check();
    await page.getByRole('button', { name: '直接推荐', exact: true }).click();
    await expect(page.getByText('所选简历仅有已结束流程的历史关联岗位，请先确认本次应聘岗位', { exact: true })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(writes).toBe(0);
    await page.screenshot({ path: testInfo.outputPath('historical-jd.png'), fullPage: true });
  });
}

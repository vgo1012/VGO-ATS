import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const company = { id: 'client-a', name: '测试甲方公司', status: 'ACTIVE', version: 1 };
const application = {
  id: 'application-a', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2',
  resumeVersionId: 'resume-v1', workflowTemplateVersionId: 'workflow-v1', matchPlanVersionId: 'match-v1',
  recruitmentType: 'EXTERNAL', clientId: company.id, clientName: company.name, progressiveFollowUp: true,
  currentStage: 'PATH_SELECTION', outcome: null, ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', terminalAt: null,
  version: 1, createId: 'hr-a', createTime: '2026-09-07T01:30:00Z', updateId: 'hr-a', updateTime: '2026-09-07T01:30:00Z',
};

test('甲方筛选提交到服务端并随 URL 刷新恢复，旧隐藏条件不再生效', async ({ page }) => {
  await reviewWorkspace(page);
  const applicationQueries: URLSearchParams[] = [];
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/clients') return route.fulfill({ json: { items: [company], hasMore: false, nextCursor: null } });
    if (path === '/applications') {
      applicationQueries.push(url.searchParams);
      const matches = !url.searchParams.get('clientId') || url.searchParams.get('clientId') === company.id;
      return route.fulfill({ json: { items: matches ? [application] : [], hasMore: false, nextCursor: null } });
    }
    return route.fallback();
  });

  await page.goto('/recruitment?recruitmentType=INTERNAL&jdVersionId=old-version&workflowStepCode=old-step');
  const clientFilter = page.getByRole('combobox', { name: '甲方公司', exact: true });
  await expect(clientFilter).toBeEnabled();
  await expect.poll(() => applicationQueries.length).toBeGreaterThan(0);
  for (const key of ['recruitmentType', 'jdVersionId', 'workflowStepCode']) expect(applicationQueries.at(-1)?.has(key)).toBe(false);
  await expect(page.getByText('更多条件', { exact: true })).toHaveCount(0);

  await clientFilter.click();
  await page.locator('.ant-select-item-option-content').getByText(company.name, { exact: true }).click();
  await expect(page).toHaveURL(/clientId=client-a/);
  await expect(page).not.toHaveURL(/recruitmentType=|jdVersionId=|workflowStepCode=/);
  await expect.poll(() => applicationQueries.at(-1)?.get('clientId')).toBe(company.id);
  await page.reload();
  await expect(clientFilter.locator('..')).toContainText(company.name);

  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  }
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect.poll(() => applicationQueries.at(-1)?.has('clientId')).toBe(false);
});

import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('补录入职连续纠正时间，非法值不提交，合法值保留秒和北京时间', async ({ page }) => {
  await reviewWorkspace(page);
  const application = {
    id: 'app-onboard', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a',
    jdVersionId: 'jd-v2', resumeVersionId: 'resume-a', recruitmentType: 'INTERNAL',
    currentStage: 'OFFER', onboardingStatus: 'IN_PROGRESS', onboardingStartedAt: '2026-01-01T01:00:00Z', ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a',
    outcome: null, terminalAt: null, version: 1,
    createTime: '2026-01-01T00:00:00Z', updateTime: '2026-09-07T01:00:00Z',
  };
  const detail = {
    application, collaboratorUserIds: [], workflowSteps: [], decisions: [],
    offerStatusRecords: [{ status: 'ISSUED', occurredAt: '2026-01-01T01:31:23Z' }],
  };
  const writes: unknown[] = [];
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/applications?**', route => route.fulfill({
    json: { items: [application], hasMore: false, nextCursor: null },
  }));
  await page.route('**/api/v1/applications/app-onboard', route => route.fulfill({ json: detail }));
  await page.route('**/api/v1/applications/app-onboard/onboarding-reviews', route => {
    writes.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.goto('/onboarded');
  await page.getByRole('button', { name: '确认实际入职', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '确认实际入职', exact: true });
  await dialog.getByRole('radio', { name: '补录入职时间' }).check();
  const input = dialog.getByLabel('实际入职日期与时间（北京时间）');
  const submit = dialog.getByRole('button', { name: /确认已入职$/ });

  await input.fill('2099-01-01T09:31:24');
  await submit.click();
  await expect(dialog.getByText('实际入职不能选择未来时间')).toBeVisible();
  expect(writes).toEqual([]);

  await input.fill('2026-01-01T09:31:22');
  await submit.click();
  await expect(dialog.getByText('不能早于 Offer 发出时间：2026-01-01 09:31:23')).toBeVisible();
  expect(writes).toEqual([]);

  await input.fill('2026-01-01T09:31:24');
  await submit.click();
  await expect(dialog).toHaveCount(0);
  expect(writes).toEqual([{ onboardedAt: '2026-01-01T01:31:24.000Z' }]);
  expect(errors).toEqual([]);
});

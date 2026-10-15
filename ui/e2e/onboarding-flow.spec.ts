import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('入职流程支持复核时间并直接退回共享简历库', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const application = {
    id: 'app-onboarded', candidateId: 'person-a', resumeVersionId: 'resume-a', jdId: 'jd-a', demandId: 'demand-a',
    ownerUserId: 'hr-a', currentStage: 'OFFER', recruitmentType: 'INTERNAL', version: '1',
    onboardingStatus: 'IN_PROGRESS', onboardingStartedAt: '2026-09-01T01:00:00Z',
    actualOnboardedAt: null, onboardingElapsedSeconds: 522000,
    createTime: '2026-09-01T01:00:00Z', updateTime: '2026-09-07T01:00:00Z',
  };
  const candidate = { id: 'person-a', fullName: '林雨桐', primaryPhone: '13800001234', ownerUserId: 'hr-a' };
  const submissions: Array<{ path: string; body: unknown }> = [];

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/applications' && request.method() === 'GET') return route.fulfill({ json: { items: [application], hasMore: false, nextCursor: null } });
    if (path === '/applications/app-onboarded') return route.fulfill({ json: {
      application, collaboratorUserIds: [], workflowSteps: [], decisions: [], elapsedSeconds: 3600,
      offerStatusRecords: [{ id: 'issued', applicationId: application.id, recordType: 'STATUS', status: 'ISSUED',
        terminationReason: null, applicationOutcome: null, reason: '已发出', correctsRecordId: null, reopensRecordId: null,
        source: 'MANUAL', sourceReference: null, sourceStatus: null, occurredAt: '2026-09-01T01:00:00Z', operatedByUserId: 'hr-a' }],
    } });
    if (path === '/candidate-batch') return route.fulfill({ json: [candidate] });
    if (path === '/resume-version-fact-summaries') return route.fulfill({ json: [] });
    if (path === '/resume-version-sources') return route.fulfill({ json: [{ resumeVersionId: 'resume-a', source: { sourceChannelName: '内部推荐' } }] });
    if (path === '/applications/app-onboarded/onboarding-reviews' || path === '/applications/app-onboarded/onboarding-return') {
      submissions.push({ path, body: request.postDataJSON() });
      return route.fulfill({ status: 201, json: {} });
    }
    return route.fallback();
  });

  await page.goto('/onboarded');
  await expect(page.getByRole('heading', { name: '入职流程' })).toBeVisible();
  await expect(page.getByRole('button', { name: '录用交付', exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: '林雨桐' })).toBeVisible();

  await expect(page.getByText('入职中', { exact: true })).toBeVisible();
  await expect(page.getByText('已历时 6天1小时', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '确认实际入职' })
    .evaluate(element => (element as HTMLElement).click());
  const reviewDialog = page.getByRole('dialog', { name: '确认实际入职' });
  await expect(reviewDialog.getByRole('radio', { name: '现在入职' })).toBeChecked();
  await reviewDialog.getByRole('button', { name: '确认已入职' }).click();
  await expect.poll(() => submissions.find(value => value.path.endsWith('/onboarding-reviews'))?.body).toMatchObject({ onboardedAt: '2026-09-07T01:30:00.000Z' });

  await page.getByRole('button', { name: '退回共享简历库' })
    .evaluate(element => (element as HTMLElement).click());
  const returnDialog = page.getByRole('dialog', { name: '退回共享简历库' });
  await expect(returnDialog.getByText('不选择则按“入职库退回”记录')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('onboarding-return-dialog.png'), animations: 'disabled' });
  await returnDialog.getByRole('button', { name: '直接退回' }).click();
  await expect.poll(() => submissions.find(value => value.path.endsWith('/onboarding-return'))?.body).toEqual({});
  expect(new URL(page.url()).pathname).toBe('/onboarded');
});

import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('intake keeps compact file information without candidate lookups and supports keyboard pagination', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await reviewWorkspace(page);
  const batches: string[][] = [];
  const individualRequests: string[] = [];
  const firstIds = Array.from({ length: 20 }, (_, index) => `person-${String(index).padStart(2, '0')}`);
  const names = new Map([...firstIds, 'person-next'].map((id, index) => [id, `候选姓名${index}`]));
  await page.route('**/api/v1/candidate-batch?**', async route => {
    const ids = new URL(route.request().url()).searchParams.getAll('candidateIds');
    batches.push(ids);
    await route.fulfill({ json: ids.map(id => ({
      id, fullName: names.get(id), primaryEmail: null, primaryPhone: null,
      ownerUserId: 'hr-a', ownerDepartmentId: 'department', status: 'ACTIVE', version: 1,
      createTime: '2026-09-05T01:00:00Z', updateTime: '2026-09-07T01:00:00Z',
    })) });
  });
  await page.route('**/api/v1/candidates/*', async route => {
    if (new URL(route.request().url()).pathname === '/api/v1/candidates/count') return route.fallback();
    individualRequests.push(route.request().url());
    await route.fulfill({ status: 404, json: { title: 'unexpected individual request', status: 404 } });
  });
  await page.route('**/api/v1/intakes?**', async route => {
    const second = new URL(route.request().url()).searchParams.has('cursor');
    const ids = second ? ['person-next'] : [...firstIds, firstIds[0]!];
    await route.fulfill({ json: {
      items: ids.map((candidateId, index) => ({
        id: `${second ? 'second' : 'first'}-${index}`, originalFileName: `收件${second ? '下一页' : index}.pdf`,
        ownerUserId: 'hr-a', sourceChannelId: 'source', sizeBytes: 1024, status: 'COMPLETED',
        importStatus: 'IMPORTED', jdVersionId: 'jd-v2', jdTitle: '前端工程师',
        scoreStatus: 'SUCCEEDED', scanStatus: 'CLEAN', version: 1, candidateId,
        resumeVersionId: `resume-${candidateId}`, createdAt: '2026-09-05T01:00:00Z',
      })), hasMore: !second, nextCursor: second ? null : 'next',
    } });
  });
  await page.goto('/settings/intakes');
  await expect(page.getByText('收件0.pdf', { exact: true })).toBeVisible();
  await expect(page.getByText('收件20.pdf', { exact: true })).toBeVisible();
  expect(batches).toEqual([]);
  expect(individualRequests).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  const next = page.locator('.intake-validation-page').getByRole('button', { name: '下一页', exact: true });
  await next.scrollIntoViewIfNeeded();
  await next.focus();
  await expect(next).toBeFocused();
  await next.press('Enter');
  await expect(page.getByText('收件下一页.pdf', { exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '导入任务分页' }).getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
  await expect(next).toBeDisabled();
  expect(batches).toEqual([]);
  expect(individualRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

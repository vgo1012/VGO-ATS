import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const path of ['/shared-library', '/onboarded']) test(`${path} uses header checkbox without fetching every page`, async ({ page }) => {
  await reviewWorkspace(page);
  const cursors: Array<string | null> = [];
  const endpoint = path === '/shared-library' ? 'talent-search' : 'applications';
  await page.route(`**/api/v1/${endpoint}?**`, route => {
    const cursor = new URL(route.request().url()).searchParams.get('cursor');
    cursors.push(cursor);
    const ids = cursor ? ['c'] : ['a', 'b'];
    const items = ids.map(id => path === '/shared-library' ? { candidateId: id, fullName: `候选人${id}`, skills: [], conditionMatches: [] } : { id, candidateId: 'person-a', resumeVersionId: 'resume-a', jdId: 'jd-a', ownerUserId: 'hr-a', currentStage: 'ONBOARDED' });
    return route.fulfill({ json: { items, hasMore: !cursor, nextCursor: cursor ? null : 'second' } });
  });
  await page.goto(path);
  const table = page.getByRole('table');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(2);
  await expect(page.getByRole('button', { name: /^(选择全部|选择本页)$/ })).toHaveCount(0);
  const clear = page.getByRole('button', { name: '清空选择', exact: true });
  if (path === '/shared-library') await expect(clear).toBeDisabled();
  const header = table.locator('thead').getByRole('checkbox');
  const rows = table.locator('tbody').getByRole('checkbox');
  await rows.first().focus();
  await rows.first().press('Space');
  await expect(header).toHaveJSProperty('indeterminate', true);
  await header.focus();
  await header.press('Space');
  await expect(rows.nth(1)).toBeChecked();
  await header.press('Space');
  await expect(rows.first()).not.toBeChecked();
  await header.press('Space');
  expect(cursors).toEqual([null]);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(1);
  await expect(header).not.toBeChecked();
  if (path === '/shared-library') await expect(clear).toBeDisabled();
  await header.focus();
  await header.press('Space');
  if (path === '/shared-library') await expect(clear).toBeEnabled();
  await page.getByRole('button', { name: '上一页', exact: true }).click();
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(2);
  if (path === '/shared-library') {
    await expect(header).not.toBeChecked();
    await expect(clear).toBeDisabled();
    await header.focus();
    await header.press('Space');
    await expect(header).toBeChecked();
    await expect(clear).toBeEnabled();
    await clear.click();
    await expect(header).not.toBeChecked();
    await expect(rows.first()).not.toBeChecked();
  } else {
    await expect(header).toBeChecked();
    await header.focus();
    await header.press('Space');
    await expect(header).not.toBeChecked();
  }
});

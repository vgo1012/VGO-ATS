import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const target of [
  { path: '/recruitment', endpoint: 'applications', label: '全选本页招聘记录' },
  { path: '/shared-library', endpoint: 'talent-search', label: '全选本页候选人' },
  { path: '/onboarded', endpoint: 'applications', label: '全选本页入职流程记录' },
]) test(`${target.path} header checkbox selects, clears and supports keyboard`, async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/resume-versions/*/facts', route => route.fulfill({ json: { snapshot: {}, workExperiences: [], projectExperiences: [], educations: [], skills: [], simpleFacts: [] } }));
  await page.route(`**/api/v1/${target.endpoint}?**`, route => {
    const second = new URL(route.request().url()).searchParams.has('cursor');
    const items = Array.from({ length: 2 }, (_, index) => ({
      id: `${second ? 'second' : 'first'}-${index}`, candidateId: target.endpoint === 'talent-search' ? `${second ? 'second' : 'first'}-${index}` : 'person-a',
      resumeVersionId: 'resume-a', fullName: `测试候选人${index}`, jdId: 'jd-a', ownerUserId: 'hr-a', recruitmentType: 'INTERNAL',
      currentStage: target.path === '/onboarded' ? 'ONBOARDED' : 'PATH_SELECTION', skills: [], conditionMatches: [],
    }));
    return route.fulfill({ json: { items, hasMore: !second, nextCursor: second ? null : 'second' } });
  });
  await page.goto(target.path);
  const header = page.getByRole('checkbox', { name: target.label, exact: true });
  const rows = page.locator('tbody tr[data-row-key] input[type=checkbox]');
  await expect(rows).toHaveCount(2);
  await header.click();
  await expect(rows.nth(0)).toBeChecked();
  await expect(rows.nth(1)).toBeChecked();
  await header.click();
  await expect(rows.nth(0)).not.toBeChecked();
  await expect(rows.nth(1)).not.toBeChecked();
  await rows.nth(0).focus();
  await rows.nth(0).press('Space');
  await expect(header).toBeChecked({ indeterminate: true });
  await header.focus();
  await header.press('Space');
  await expect(rows.nth(1)).toBeChecked();
  await page.screenshot({ path: testInfo.outputPath('selected.png'), animations: 'disabled' });
  expect((await new AxeBuilder({ page }).include('.ant-table-selection-column').analyze()).violations).toEqual([]);
  if (target.path !== '/recruitment') {
    await page.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(page.locator('tr[data-row-key="second-0"]')).toBeVisible();
    await header.check();
    await header.uncheck();
    await page.getByRole('button', { name: '上一页', exact: true }).click();
    await expect(page.locator('tr[data-row-key="first-0"]')).toBeVisible();
    if (target.path === '/shared-library') {
      await expect(rows.nth(0)).not.toBeChecked();
      await expect(rows.nth(1)).not.toBeChecked();
      await expect(header).not.toBeChecked();
    } else {
      await expect(rows.nth(0)).toBeChecked();
      await expect(rows.nth(1)).toBeChecked();
    }
  }
  await page.route(`**/api/v1/${target.endpoint}?**`, route => route.fulfill({ json: { items: [], hasMore: false } }));
  await page.reload();
  await expect(header).toBeDisabled();
});

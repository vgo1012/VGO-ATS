import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'age-person', fullName: '出生信息测试', primaryEmail: 'age@example.com', ownerUserId: 'age-user', createdAt: '2026-09-11T03:00:00Z' };

for (const birth of [
  { birthDate: '1998', age: null, ageAsOf: null },
  { birthDate: '1998-09', age: null, ageAsOf: null },
  { birthDate: '1998-09-12', age: 27, ageAsOf: '2026-09-11' },
]) test(`candidate preserves ${birth.birthDate} precision and age snapshot when correcting facts`, async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  let correction: { facts: { candidate: { birthDate: string; age: number | null } } } | undefined;
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = ['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/candidate-blacklist-tags', '/candidate-pool-statuses', '/candidate-resume-summaries', '/resume-version-fact-summaries', '/resume-version-sources', '/resume-version-references'].includes(path) ? [] : empty;
    if (path === '/me') body = { userId: 'age-user', displayName: '招聘负责人', roles: [], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };
    if (path === '/auth/csrf') body = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'age-csrf' };
    if (path.startsWith('/admin/') || path === '/directory/users/resolve') body = [];
    if (path === '/candidates') body = { ...empty, items: [person] };
    if (path === '/candidates/age-person') body = person;
    if (path === '/candidates/age-person/company-blacklist-records') body = { current: null, active: false, records: empty };
    if (path === '/candidates/age-person/resume-versions') body = { ...empty, items: [{ id: 'age-resume', versionNo: 1, originalFileName: '出生信息.pdf', createdAt: person.createdAt }] };
    if (path === '/resume-versions/age-resume/facts') body = { snapshot: { id: 'age-snapshot', resumeVersionId: 'age-resume', fullName: person.fullName, snapshotVersion: 1, sourceType: 'AI_PARSE', ...birth }, workExperiences: [], projectExperiences: [], educations: [], simpleFacts: [], skills: [{ id: 'java', skillName: 'Java' }] };
    if (path === '/resume-versions/age-resume/extracted-text') body = { pages: [], pageCount: 0, coverageRatio: 0 };
    if (path === '/fact-corrections') { correction = route.request().postDataJSON(); body = { snapshotVersion: 2 }; }
    return route.fulfill({ json: body });
  });
  await page.goto('/talents');
  await page.getByRole('button', { name: '详情与版本' }).click();
  const controlled = page.getByRole('tab', { name: '受控条件' });
  await controlled.focus();
  await page.keyboard.press('Enter');
  const panel = page.getByRole('tabpanel', { name: '受控条件' });
  await expect(panel.getByText(birth.birthDate, { exact: true })).toBeVisible();
  const ageRow = panel.locator('tr').filter({ has: page.getByText('年龄', { exact: true }) });
  await expect(ageRow).toContainText(birth.age === null ? '未识别' : '27 岁');
  if (birth.ageAsOf) await expect(ageRow).toContainText(`截至 ${birth.ageAsOf}`);
  else await expect(ageRow).not.toContainText('截至');
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({ page }).include(`[id="${await panel.getAttribute('id')}"]`).analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath(`birth-${birth.birthDate}.png`), animations: 'disabled' });
  if (birth.ageAsOf && testInfo.project.name === 'desktop') {
    for (const width of [390, 768, 1280, 1920]) {
      await page.setViewportSize({ width, height: 900 });
      await expect(panel.getByText(birth.birthDate, { exact: true })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    }
  }
  await page.getByRole('button', { name: /^(人工更正|HR 更正)$/ }).click();
  await page.getByLabel('更正原因', { exact: true }).fill('核实技能，保留原始出生信息');
  await page.getByRole('button', { name: /^(保存新事实快照|保存更正)$/ }).click();
  await expect.poll(() => correction?.facts.candidate.birthDate).toBe(birth.birthDate);
  expect(correction?.facts.candidate.age).toBe(birth.age);
  expect(errors).toEqual([]);
});

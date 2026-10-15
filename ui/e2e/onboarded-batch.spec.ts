import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('onboarded page batches twenty historical profiles and keeps accessible navigation', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  const applications = Array.from({ length: 20 }, (_, index) => ({
    id: `application-${index}`, candidateId: `person-${index}`, resumeVersionId: `historical-${index}`,
    jdId: 'jd-a', demandId: 'demand-a', ownerUserId: 'hr-a', currentStage: 'ONBOARDED',
  }));
  let candidateReads = 0, factReads = 0, individualReads = 0;
  let sourceReads = 0;
  await page.route('**/api/v1/applications?**', route => route.fulfill({ json: { items: applications, hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/candidate-batch?**', route => {
    candidateReads++;
    const ids = new URL(route.request().url()).searchParams.getAll('candidateIds');
    expect(ids).toEqual(applications.map(item => item.candidateId));
    return route.fulfill({ json: ids.map(id => ({ id, fullName: `入职-${id}`, primaryPhone: `电话-${id}`, ownerUserId: 'hr-a' })) });
  });
  await page.route('**/api/v1/resume-version-fact-summaries?**', route => {
    factReads++;
    const ids = new URL(route.request().url()).searchParams.getAll('resumeVersionIds');
    expect(ids).toEqual(applications.map(item => item.resumeVersionId));
    return route.fulfill({ json: ids.map(resumeVersionId => ({ resumeVersionId, facts: {
      snapshot: { age: 31, gender: 'FEMALE', createdAt: '2026-09-01T00:00:00Z' },
      educations: [{ degreeName: '硕士' }], workExperiences: [], skills: [],
    } })) });
  });
  await page.route('**/api/v1/resume-version-sources?**', route => {
    sourceReads++;
    const ids = new URL(route.request().url()).searchParams.getAll('resumeVersionIds');
    expect(ids).toEqual(applications.map(item => item.resumeVersionId));
    return route.fulfill({ json: ids.map(resumeVersionId => ({ resumeVersionId, source: { sourceChannelName: '入职时的历史渠道' } })) });
  });
  await page.route(/\/api\/v1\/(candidates\/[^/?]+(?:\/resume-versions\/[^/?]+\/source)?|resume-versions\/[^/?]+\/facts)(\?.*)?$/, route => {
    individualReads++;
    return route.fulfill({ status: 500, json: {} });
  });
  await page.goto('/onboarded');
  await expect(page.getByText('电话-person-0', { exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: '31 岁', exact: true })).toHaveCount(20);
  await expect(page.getByText('入职时的历史渠道', { exact: true })).toHaveCount(20);
  expect({ candidateReads, factReads, sourceReads, individualReads }).toEqual({ candidateReads: 1, factReads: 1, sourceReads: 1, individualReads: 0 });
  const select = page.getByRole('checkbox', { name: '全选本页入职流程记录', exact: true });
  await select.focus();
  await page.keyboard.press('Space');
  await expect(select).toBeChecked();
  for (const width of testInfo.project.name === 'desktop' ? [390, 768, 1280, 1440, 1920] : [393]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`onboarded-${width}.png`), animations: 'disabled' });
  }
  expect((await new AxeBuilder({ page }).include('.app-main').analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
});

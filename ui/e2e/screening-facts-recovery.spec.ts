import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const rawText = '独立岗位版本原文：平台建设与日常协作。';
const originalSummary = '受控岗位事实摘要';
const originalSkill = '专属事实技能';
const originalResponsibility = '受控事实职责';

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, restored: false, requests: 0, wait: undefined as Promise<void> | undefined };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/jds/jd-a/versions') return route.fulfill({ json: {
      items: [{ id: 'jd-v2', jdId: 'jd-a', title: '高级前端工程师', versionNo: 2, rawText,
        status: 'PUBLISHED', sourceType: 'MANUAL', createId: 'hr-a', createTime: '2026-09-07T00:00:00Z' }],
      hasMore: false, nextCursor: null,
    } });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path === '/me/jd-scoring-configs/jd-v2/preferences') return route.fulfill({ json: null });
    if (path !== '/jd-versions/jd-v2/facts') return route.fallback();
    state.requests++;
    await state.wait;
    if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
      status: state.status, title: '岗位事实读取失败',
      detail: state.status === 403 ? '岗位事实权限已撤销' : '岗位事实服务暂不可用',
      code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
    } });
    return route.fulfill({ json: {
      factSnapshotId: 'facts-jd-v2', jdVersionId: 'jd-v2', snapshotVersion: 1, sourceType: 'AI_PARSE', schemaVersion: '1',
      facts: {
        title: '高级前端工程师', summary: state.restored ? '恢复后的岗位事实摘要' : originalSummary,
        professional: {
          coreSkills: [{ id: 'skill-a', name: state.restored ? '恢复后的岗位技能' : originalSkill, required: true, aliases: [] }],
          roleExperiences: [], minimumYears: 7, preferredYears: null,
          responsibilities: [state.restored ? '恢复后的岗位职责' : originalResponsibility], industryTags: [], projectTags: [],
        },
        controlledConditions: { ageMin: 25, ageMax: 45, sex: 'ANY', educations: ['本科'], credentials: [], languages: [] },
        fulfillmentConditions: { locations: ['上海'], availability: null, salaryMin: null, salaryMax: null, salaryPeriod: null },
        evidence: [],
      },
      createdAt: '2026-09-07T00:00:00Z',
    } });
  });
  return state;
}

test('岗位事实首次失败后详情慢重试持续显示忙碌按钮和独立原文', async ({ page }, testInfo) => {
  const width = testInfo.project.name === 'mobile' ? 390 : 1440;
  await page.setViewportSize({ width, height: 1000 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  const state = await fixture(page);
  state.status = 503;
  await page.goto('/matching');
  const header = page.locator('.screening-job-header');
  await expect(header.getByText('岗位事实读取失败', { exact: true })).toBeVisible();
  await expect(header).not.toContainText(rawText);
  await page.getByRole('button', { name: 'JD 详情', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: /JD 详情/ });
  const retry = dialog.getByRole('button', { name: '重试岗位事实', exact: true });
  await expect(retry).toBeEnabled();
  await expect(dialog).toContainText(rawText);
  await expect(dialog).not.toContainText(originalSummary);

  state.status = 200;
  state.restored = true;
  let release!: () => void;
  state.wait = new Promise<void>(resolve => { release = resolve; });
  const before = state.requests;
  await retry.focus();
  await page.keyboard.press('Enter');
  try {
    await expect.poll(() => state.requests).toBe(before + 1);
    await expect(retry).toBeVisible();
    await expect(retry).toBeDisabled();
    await expect(retry).toHaveAttribute('aria-busy', 'true');
    await expect(dialog).toContainText(rawText);
    await expect(dialog).not.toContainText('恢复后的岗位事实摘要');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`screening-facts-first-retry-pending-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  } finally {
    release();
  }
  await expect(retry).toHaveCount(0);
  await expect(dialog).toContainText('恢复后的岗位事实摘要');
  await expect(dialog).toContainText('恢复后的岗位技能');
  await expect(dialog).toContainText('恢复后的岗位职责');
  await expect(dialog).toContainText(rawText);
  await page.keyboard.press('Escape');
  await expect(header).toContainText('恢复后的岗位技能');
  expect(errors).toEqual([]);
});

for (const status of [403, 503]) {
  test(`岗位事实缓存刷新 ${status} 后正确展示并可重试`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching');
    const header = page.locator('.screening-job-header');
    await expect(header).not.toContainText(originalSummary);
    await expect(header).toContainText(originalSkill);
    await page.getByRole('button', { name: 'JD 详情', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: /JD 详情/ });
    await expect(dialog).toContainText(originalSummary);
    await expect(dialog).toContainText(originalResponsibility);
    await expect(dialog).toContainText(rawText);
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();

    await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: /^(我的候选人|团队候选人|候选人库)$/ }).click();
    await expect(page).toHaveURL(/\/talents$/);
    state.status = status;
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: 'AI 评估', exact: true }).click();
    await expect(page).toHaveURL(/\/matching$/);
    const title = status === 403 ? '暂无岗位事实的查看权限' : '岗位事实读取失败';
    await expect(header.getByText(title, { exact: true })).toBeVisible();
    await expect(header.getByRole('button', { name: '重试岗位事实', exact: true })).toBeEnabled();
    await page.getByRole('button', { name: 'JD 详情', exact: true }).click();
    await expect(dialog.getByText(title, { exact: true })).toBeVisible();
    await expect(dialog).toContainText(rawText);
    if (status === 403) {
      for (const secret of [originalSummary, originalSkill, '7年以上', '本科', '年龄≥25', '年龄≤45']) {
        await expect(header).not.toContainText(secret);
        await expect(dialog).not.toContainText(secret);
      }
      await expect(dialog).not.toContainText(originalResponsibility);
      await expect(header).not.toContainText(rawText);
    } else {
      await expect(header).not.toContainText(originalSummary);
      await expect(header).toContainText(originalSkill);
      await expect(dialog).toContainText(originalSummary);
      await expect(dialog).toContainText(originalResponsibility);
    }
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
      await expect(dialog).toHaveCSS('opacity', '1');
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`screening-facts-${status}-${width}.png`), animations: 'disabled', fullPage: true });
      expect((await new AxeBuilder({ page }).include('.ant-modal').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    }

    state.status = 200;
    state.restored = true;
    let release!: () => void;
    state.wait = new Promise<void>(resolve => { release = resolve; });
    const beforeRetry = state.requests;
    const retry = dialog.getByRole('button', { name: '重试岗位事实', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.requests).toBe(beforeRetry + 1);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      release();
    }
    await expect(dialog.getByRole('button', { name: '重试岗位事实', exact: true })).toHaveCount(0);
    await expect(dialog).toContainText('恢复后的岗位事实摘要');
    await expect(dialog).toContainText('恢复后的岗位职责');
    await expect(dialog).toContainText(rawText);
    await page.keyboard.press('Escape');
    await expect(header).toContainText('恢复后的岗位技能');
    expect(state.requests).toBe(3);
    expect(errors).toEqual([]);
  });
}

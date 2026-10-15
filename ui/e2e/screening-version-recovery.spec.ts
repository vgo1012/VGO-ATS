import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const rawText = '旧岗位版本原文：仅当前授权范围内可见的具体招聘要求。';

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, restored: false, requests: [] as string[], wait: undefined as Promise<void> | undefined };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: null });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path !== '/jds/jd-a/versions') return route.fallback();
    state.requests.push(path);
    await state.wait;
    if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
      status: state.status, title: '岗位版本读取失败', detail: state.status === 403 ? '岗位版本访问已撤销' : '岗位版本服务暂不可用',
      code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
    } });
    return route.fulfill({ json: { items: [{ id: 'jd-v2', jdId: 'jd-a', title: '高级前端工程师', versionNo: 2,
      rawText: state.restored ? '恢复后的岗位版本原文' : rawText, status: 'PUBLISHED', sourceType: 'MANUAL',
      createId: 'hr-a', createTime: '2026-09-07T00:00:00Z' }], hasMore: false, nextCursor: null } });
  });
  return state;
}

for (const status of [403, 503]) {
  test(`岗位版本缓存刷新 ${status} 后收起无权原文并允许原岗位重试`, async ({ page }, testInfo) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching');
    const header = page.locator('.screening-job-header');
    await expect(header).toContainText('高级前端工程师');
    const detailButton = page.getByRole('button', { name: 'JD 详情', exact: true });
    const dialog = page.getByRole('dialog', { name: /JD 详情/ });
    await detailButton.click();
    await expect(dialog).toContainText(rawText);
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: /^(我的候选人|团队候选人|候选人库)$/ }).click();
    await expect(page).toHaveURL(/\/talents$/);
    state.status = status;
    let releaseRefresh!: () => void;
    state.wait = new Promise<void>(resolve => { releaseRefresh = resolve; });
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name: 'AI 评估', exact: true }).click();
    await expect.poll(() => state.requests.length).toBe(2);
    try {
      await detailButton.click();
      await expect(dialog).toContainText(rawText);
    } finally {
      releaseRefresh();
    }
    const title = status === 403 ? '暂无岗位版本的查看权限' : '岗位版本读取失败';
    await expect(page.getByRole('button', { name: '重试岗位版本', exact: true })).toBeVisible();
    if (status === 403) {
      await expect(dialog).not.toContainText(rawText);
      await expect(dialog).toContainText('当前版本未提供 JD 原文');
    } else {
      await expect(dialog).toContainText(rawText);
    }
    await expect(page.getByText(title, { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    if (status === 403) {
      await expect(detailButton).toBeDisabled();
      await expect(page.getByRole('button', { name: '重新评分', exact: true })).toBeDisabled();
    } else {
      await expect(detailButton).toBeEnabled();
    }
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`screening-version-${status}-${width}.png`), animations: 'disabled', fullPage: true });
    }
    expect((await new AxeBuilder({ page }).include('.app-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    state.status = 200;
    state.restored = true;
    let releaseRetry!: () => void;
    state.wait = new Promise<void>(resolve => { releaseRetry = resolve; });
    const retry = page.getByRole('button', { name: '重试岗位版本', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.requests.length).toBe(3);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      releaseRetry();
    }
    await expect(retry).toHaveCount(0);
    await expect(detailButton).toBeEnabled();
    await detailButton.click();
    await expect(dialog).toContainText('恢复后的岗位版本原文');
    await expect(dialog).not.toContainText(rawText);
    expect(state.requests).toEqual(Array(3).fill('/jds/jd-a/versions'));
    expect(errors).toEqual([]);
  });
}

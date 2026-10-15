import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'person-a', fullName: '版本权限候选人', primaryEmail: 'versions@example.com', primaryPhone: '13812345678', ownerUserId: 'hr-a' };

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, restored: false, requests: 0 };
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/talent-search') return route.fulfill({ json: { ...empty, items: [{ candidateId: person.id, resumeVersionId: 'versions-latest', fullName: person.fullName, email: person.primaryEmail, phone: person.primaryPhone, skills: [], degrees: [], conditionMatches: [], engagementLock: null }] } });
    if (path === '/talent-search/count') return route.fulfill({ json: { count: 1, hasMore: false, nextCursor: null } });
    if (path === '/candidate-batch') return route.fulfill({ json: [person] });
    if (path === '/candidates/person-a') return route.fulfill({ json: person });
    if (path === '/candidates/person-a/resume-versions') {
      state.requests++;
      if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
        status: state.status, title: '版本列表读取失败', detail: state.status === 403 ? '简历版本列表权限已撤销' : '简历版本服务暂不可用',
        code: state.status === 403 ? 'CANDIDATE_RESUME_ACCESS_FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
      } });
      return route.fulfill({ json: { ...empty, items: [
        { id: 'versions-latest', versionNo: 2, originalFileName: state.restored ? '恢复后的当前版本.pdf' : '旧当前版本.pdf', createdAt: '2026-09-07T01:00:00Z' },
        { id: 'versions-older', versionNo: 1, originalFileName: state.restored ? '恢复后的历史版本.pdf' : '旧历史版本.pdf', createdAt: '2026-09-06T01:00:00Z' },
      ] } });
    }
    if (path === '/candidates/person-a/resume-versions/versions-latest/source') return route.fulfill({ json: {
      sourceChannelName: '单版本已授权来源', sourceRecruiterUserId: 'hr-a', receivedAt: '2026-09-07T01:00:00Z',
    } });
    if (path === '/resume-versions/versions-latest/facts') return route.fulfill({ json: {
      snapshot: { id: 'versions-facts', resumeVersionId: 'versions-latest', snapshotVersion: 1, sourceType: 'AI_PARSE', fullName: person.fullName, email: person.primaryEmail, phone: person.primaryPhone, currentRole: '单版本已授权岗位' },
      workExperiences: [], projectExperiences: [], educations: [], skills: [{ id: 'skill', skillName: '单版本已授权技能' }], simpleFacts: [],
    } });
    if (path === '/resume-versions/versions-latest/extracted-text') return route.fulfill({ json: { pages: [], pageCount: 0, coverageRatio: 0 } });
    if (path === '/tasks') return route.fulfill({ json: [] });
    return route.fallback();
  });
  return state;
}

for (const width of [390, 1440]) {
  for (const status of [403, 503]) {
    test(`简历版本列表 ${status} 恢复不影响独立授权的单版本详情 ${width}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 1000 });
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      const state = await fixture(page);
      await page.goto('/shared-library');
      const open = page.getByRole('button', { name: person.fullName, exact: true });
      await open.click();
      const dialog = page.getByRole('dialog', { name: person.fullName, exact: true });
      const content = dialog.locator('.candidate-detail-content');
      await expect(content).toContainText('旧当前版本.pdf');
      await expect(content).toContainText('旧历史版本.pdf');
      await expect(content.getByRole('button', { name: '受控下载', exact: true })).toHaveCount(2);
      await expect(content).toContainText('单版本已授权技能');
      await expect(content).toContainText('单版本已授权来源');
      await dialog.getByRole('button', { name: '关闭', exact: true }).click();
      await expect(dialog).not.toBeVisible();
      state.status = status;
      await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
      await open.click();
      const retry = content.getByRole('button', { name: '重试简历版本', exact: true });
      await expect(retry).toBeVisible();
      expect(state.requests).toBe(2);
      if (status === 403) {
        await expect(content).toContainText('暂无该候选人简历版本列表的查看权限');
        await expect(content).not.toContainText('旧当前版本.pdf');
        await expect(content).not.toContainText('旧历史版本.pdf');
        await expect(content.getByRole('button', { name: '受控下载', exact: true })).toHaveCount(0);
      } else {
        await expect(content).toContainText('旧当前版本.pdf');
        await expect(content).toContainText('旧历史版本.pdf');
        await expect(content.getByRole('button', { name: '受控下载', exact: true })).toHaveCount(2);
      }
      for (const text of [person.primaryEmail, person.primaryPhone, '单版本已授权岗位', '单版本已授权技能', '单版本已授权来源']) await expect(content).toContainText(text);
      await expect(content.getByRole('button', { name: 'HR 更正', exact: true })).toBeEnabled();
      for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
      expect((await new AxeBuilder({ page }).include('.candidate-detail-content').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await retry.scrollIntoViewIfNeeded();
      await page.screenshot({ path: testInfo.outputPath(`candidate-versions-${status}-${width}.png`), animations: 'disabled' });
      state.status = 200;
      state.restored = true;
      await retry.focus();
      await page.keyboard.press('Enter');
      await expect(content).toContainText('恢复后的当前版本.pdf');
      await expect(content).toContainText('恢复后的历史版本.pdf');
      await expect(content.getByRole('button', { name: '受控下载', exact: true })).toHaveCount(2);
      await expect(content).toContainText('单版本已授权技能');
      await expect(retry).toHaveCount(0);
      expect(state.requests).toBe(3);
      expect(errors).toEqual([]);
    });
  }
}

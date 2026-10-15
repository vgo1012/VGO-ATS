import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const original = { id: 'person-a', fullName: '候选人权限旧姓名', primaryEmail: 'candidate-private@example.com', primaryPhone: '13877778888', ownerUserId: 'hr-a' };

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, restored: false, requests: 0, sourceRequests: 0 };
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/candidates') return route.fulfill({ json: { ...empty, items: [original] } });
    if (path === '/candidates/person-a') {
      state.requests++;
      if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
        status: state.status, title: '候选人资料读取失败', detail: state.status === 403 ? '候选人详情访问已撤销' : '候选人详情服务暂不可用',
        code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
      } });
      return route.fulfill({ json: { ...original, fullName: state.restored ? '候选人权限恢复姓名' : original.fullName } });
    }
    if (path === '/candidates/person-a/resume-versions') return route.fulfill({ json: { ...empty, items: [{ id: 'access-resume', versionNo: 1, originalFileName: '权限专属简历.pdf', createdAt: '2026-09-07T01:00:00Z' }] } });
    if (path === '/candidates/person-a/resume-versions/access-resume/source') {
      state.sourceRequests++;
      return route.fulfill({ json: { sourceChannelName: '权限专属来源', receivedAt: '2026-09-07T01:00:00Z' } });
    }
    if (path === '/resume-version-references') return route.fulfill({ json: [{ id: 'access-resume', versionNo: 1, originalFileName: '权限专属简历.pdf' }] });
    if (path === '/resume-versions/access-resume/download-authorizations') return route.fulfill({ json: { token: 'access-preview' } });
    if (path === '/download-authorizations/access-preview') return route.fulfill({ contentType: 'application/octet-stream', headers: { 'content-disposition': 'attachment; filename="access-resume.pdf"' }, body: 'fixture' });
    if (path === '/resume-versions/access-resume/facts') return route.fulfill({ json: {
      snapshot: { id: 'access-facts', resumeVersionId: 'access-resume', snapshotVersion: 1, sourceType: 'AI_PARSE', fullName: original.fullName, email: original.primaryEmail, phone: original.primaryPhone, currentRole: '权限专属履历岗位' },
      workExperiences: [], projectExperiences: [], educations: [], skills: [{ id: 'skill', skillName: '权限专属技能' }], simpleFacts: [],
    } });
    if (path === '/resume-versions/access-resume/extracted-text') return route.fulfill({ json: { pages: [], pageCount: 0, coverageRatio: 0 } });
    if (path === '/tasks') return route.fulfill({ json: [] });
    return route.fallback();
  });
  return state;
}

async function expectAuthorizedMaterials(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: '查看面试材料' }).click();
  const materials = page.getByRole('dialog', { name: '面试材料', exact: true });
  await expect(materials).toContainText('权限专属简历.pdf');
  await materials.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(materials).not.toBeVisible();
}

for (const width of [390, 1440]) {
  for (const status of [403, 503]) {
    test(`候选人弹窗主请求刷新 ${status} 的恢复与缓存边界 ${width}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 1000 });
      const pageErrors: string[] = [];
      page.on('pageerror', error => pageErrors.push(error.message));
      const state = await fixture(page);
      await page.goto('/talents');
      const open = page.getByRole('button', { name: '详情与版本', exact: true });
      await open.click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toHaveAccessibleName(original.fullName);
      for (const text of [original.primaryEmail, original.primaryPhone, '权限专属履历岗位', '权限专属技能']) await expect(dialog).toContainText(text);
      await expectAuthorizedMaterials(page);
      await expect(dialog).not.toContainText('权限专属来源');
      await dialog.getByRole('button', { name: '关闭', exact: true }).click();
      await expect(dialog).not.toBeVisible();
      state.status = status;
      await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
      await open.click();
      const retry = dialog.getByRole('button', { name: '重试候选人详情', exact: true });
      await expect(retry).toBeVisible();
      if (status === 403) {
        await expect(dialog).toHaveAccessibleName('候选人详情');
        for (const text of [original.fullName, original.primaryEmail, original.primaryPhone, '权限专属简历.pdf', '权限专属来源', '权限专属履历岗位', '权限专属技能']) await expect(dialog).not.toContainText(text);
        await expect(dialog.getByRole('button', { name: 'HR 更正', exact: true })).toHaveCount(0);
        await expect(dialog.getByRole('button', { name: '查看面试材料', exact: true })).toHaveCount(0);
        for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
        expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
      } else {
        await expect(dialog).toHaveAccessibleName(original.fullName);
        for (const text of [original.primaryEmail, original.primaryPhone, '权限专属履历岗位', '权限专属技能']) await expect(dialog).toContainText(text);
        await expectAuthorizedMaterials(page);
      }
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await expect.poll(() => dialog.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`candidate-access-${status}-${width}.png`), animations: 'disabled' });
      state.status = 200;
      state.restored = true;
      await retry.focus();
      await page.keyboard.press('Enter');
      await expect(dialog).toHaveAccessibleName('候选人权限恢复姓名');
      await expect(dialog).toContainText(original.primaryEmail);
      await expect(dialog).toContainText(original.primaryPhone);
      await expect(dialog).toContainText('权限专属履历岗位');
      await expect(dialog).toContainText('权限专属技能');
      await expectAuthorizedMaterials(page);
      await expect(retry).toHaveCount(0);
      expect(state.requests).toBeGreaterThanOrEqual(3);
      expect(state.sourceRequests).toBe(0);
      expect(pageErrors).toEqual([]);
    });
  }
}

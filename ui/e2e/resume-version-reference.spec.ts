import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const application = {
  id: 'reference-app', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'historical-resume',
  workflowTemplateVersionId: 'workflow', matchPlanVersionId: 'match', recruitmentType: 'INTERNAL', currentStage: 'ONBOARDED',
  outcome: 'PASS', ownerUserId: 'hr-a', ownerDepartmentId: 'dept', terminalAt: '2026-09-07T01:00:00Z', version: 1,
  createTime: '2026-09-07T01:00:00Z', updateTime: '2026-09-07T01:00:00Z',
};

for (const width of [390, 1440]) {
  for (const fullList of ['forbidden', 'first-100'] as const) {
    test(`精确简历引用不依赖${fullList === 'forbidden' ? '全版本权限' : '前100个版本'}并处理引用撤权 ${width}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 1000 });
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      await reviewWorkspace(page);
      let mode: 'readable' | 'omitted' | 'denied' = 'readable';
      let revision = 0;
      let fullListRequests = 0;
      const referenceRequests: string[][] = [];
      await page.route('**/api/v1/**', route => {
        const url = new URL(route.request().url());
        const path = url.pathname.replace('/api/v1', '');
        if (path === '/applications') return route.fulfill({ json: { ...empty, items: [application] } });
        if (path === '/applications/reference-app') return route.fulfill({ json: { application, collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [], elapsedSeconds: 100 } });
        if (['/applications/reference-app/communications', '/applications/reference-app/intentions'].includes(path)) return route.fulfill({ json: [] });
        if (path === '/candidates/person-a/resume-versions') {
          fullListRequests++;
          return fullList === 'forbidden' ? route.fulfill({ status: 403, contentType: 'application/problem+json', json: {
            status: 403, title: '无权查看全版本列表', detail: '当前仅授权招聘活动绑定版本', code: 'CANDIDATE_RESUME_ACCESS_FORBIDDEN', recoverable: false,
          } }) : route.fulfill({ json: {
            items: Array.from({ length: 100 }, (_, i) => ({ id: `recent-${i}`, versionNo: 102 - i, originalFileName: `新版本${i}.pdf` })), hasMore: true, nextCursor: 'older-page',
          } });
        }
        if (path === '/resume-version-references') {
          referenceRequests.push(url.searchParams.getAll('resumeVersionIds'));
          if (mode === 'denied') return route.fulfill({ status: 403, contentType: 'application/problem+json', json: {
            status: 403, title: '简历引用读取失败', detail: '版本引用访问已撤销', code: 'FORBIDDEN', recoverable: false,
          } });
          return route.fulfill({ json: mode === 'omitted' ? [] : [{ id: 'historical-resume', versionNo: 1, originalFileName: revision ? `恢复引用${revision}.pdf` : '绑定历史版本.pdf' }] });
        }
        if (path === '/resume-versions/historical-resume/download-authorizations') return route.fulfill({ json: { token: 'historical-preview', expiresAt: '2026-09-07T02:00:00Z' } });
        if (path === '/download-authorizations/historical-preview') return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4' });
        return route.fallback();
      });
      await page.goto('/recruitment?application=reference-app');
      const dialog = page.getByRole('dialog', { name: '招聘活动详情', exact: true });
      const materials = page.getByRole('dialog', { name: '招聘材料', exact: true });
      const openMaterials = async () => {
        await dialog.getByRole('button', { name: '查看简历与 JD', exact: true }).click();
        await expect(materials).toBeVisible();
      };
      await openMaterials();
      await expect(materials).toContainText('绑定历史版本.pdf');
      expect(fullListRequests).toBe(0);
      expect(referenceRequests.length).toBeGreaterThan(0);
      expect(referenceRequests.every(ids => ids.length === 1 && ids[0] === 'historical-resume')).toBe(true);
      let elapsed = 0;
      const reopen = async () => {
        await materials.getByRole('button', { name: '关闭', exact: true }).click();
        await expect(materials).not.toBeVisible();
        await dialog.getByRole('button', { name: '关闭', exact: true }).click();
        await expect(dialog).not.toBeVisible();
        elapsed += 120_000;
        await page.clock.setFixedTime(new Date(Date.parse('2026-09-07T01:30:00Z') + elapsed));
        await page.getByRole('button', { name: '林雨桐', exact: true }).click();
        await expect(dialog).toBeVisible();
        await openMaterials();
      };
      for (const rejected of ['omitted', 'denied'] as const) {
        mode = rejected;
        await reopen();
        await expect(page.locator('.error-notification').filter({hasText:'简历读取失败'}).last()).toBeVisible();
        await expect(materials).not.toContainText('绑定历史版本.pdf');
        await expect(materials).not.toContainText('恢复引用1.pdf');
        mode = 'readable';
        revision++;
        await reopen();
        await expect(materials).toContainText(`恢复引用${revision}.pdf`);
      }
      expect(fullListRequests).toBe(0);
      // 引用缓存可能在弹窗 240ms 淡入结束前显示；等待真实动画完成后测最终对比度。
      await expect(materials).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
      await expect(materials).toHaveCSS('opacity','1');
      const restoredName=materials.getByText(`恢复引用${revision}.pdf`,{exact:true});
      await restoredName.scrollIntoViewIfNeeded();
      await expect(restoredName).toBeVisible();
      expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`resume-version-reference-${fullList}-${width}.png`), animations: 'disabled' });
      expect(errors).toEqual([]);
    });
  }
}

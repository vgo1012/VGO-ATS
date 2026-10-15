import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };

for (const width of [390, 1440]) {
  test(`简历事实标签支持完整键盘导航及合法无障碍结构 ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await reviewWorkspace(page);
    await page.route('**/api/v1/**', route => {
      const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
      if (path === '/candidates/person-a/resume-versions') return route.fulfill({ json: { ...empty, items: [{ id: 'tabs-resume', versionNo: 1, originalFileName: '标签导航简历.pdf', createdAt: '2026-09-07T01:00:00Z' }] } });
      if (path === '/candidates/person-a/resume-versions/tabs-resume/source') return route.fulfill({ json: { sourceChannelName: '内推', sourceRecruiterUserId: 'hr-a', receivedAt: '2026-09-07T01:00:00Z' } });
      if (path === '/resume-versions/tabs-resume/facts') return route.fulfill({ json: {
        snapshot: { id: 'tabs-facts', resumeVersionId: 'tabs-resume', snapshotVersion: 1, sourceType: 'AI_PARSE', fullName: '林雨桐', currentRole: '标签导航工程师', city: '上海', jobIntention: '前端工程师' },
        workExperiences: [], projectExperiences: [], educations: [], skills: [{ id: 'skill', skillName: '键盘导航技能' }], simpleFacts: [],
      } });
      if (path === '/tasks') return route.fulfill({ json: [] });
      return route.fallback();
    });
    await page.goto('/talents');
    await page.getByRole('button', { name: '详情与版本', exact: true }).click();
    const content = page.locator('.candidate-detail-content');
    const first = content.getByRole('tab', { name: /专业事实$/ });
    const last = content.getByRole('tab', { name: /履约与意向$/ });
    await expect(first).toHaveAttribute('aria-selected', 'true');
    await first.focus();
    await page.keyboard.press('End');
    await expect(last).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(last).toHaveAttribute('aria-selected', 'true');
    await expect(last).toBeInViewport();
    const panel = content.getByRole('tabpanel', { name: /履约与意向$/ });
    await expect(panel).toBeVisible();
    await expect(last).toHaveAttribute('aria-controls', (await panel.getAttribute('id'))!);
    await expect(panel).toHaveAttribute('aria-labelledby', (await last.getAttribute('id'))!);
    await expect(panel).toContainText('上海');
    expect((await new AxeBuilder({ page }).include('.candidate-detail-content').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`candidate-fact-tabs-fulfillment-${width}.png`), animations: 'disabled' });
    await last.focus();
    await page.keyboard.press('ArrowLeft');
    const previous = content.getByRole('tab', { name: /受控条件$/ });
    await expect(previous).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(previous).toHaveAttribute('aria-selected', 'true');
    await expect(previous).toBeInViewport();
    await expect(content.getByRole('tabpanel', { name: /受控条件$/ })).toBeVisible();
    await page.keyboard.press('ArrowRight');
    await expect(last).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(last).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Home');
    await expect(first).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(first).toHaveAttribute('aria-selected', 'true');
    await expect(first).toBeInViewport();
    await expect(content.getByRole('tabpanel', { name: /专业事实$/ })).toContainText('键盘导航技能');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await expect.poll(() => content.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
    expect((await new AxeBuilder({ page }).include('.candidate-detail-content').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`candidate-fact-tabs-professional-${width}.png`), animations: 'disabled' });
    expect(errors).toEqual([]);
  });
}

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'person-a', fullName: '基础档案姓名', primaryEmail: 'base@example.com', primaryPhone: '13800001111', ownerUserId: 'hr-a', createdAt: '2026-09-07T01:00:00Z' };

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { factsStatus: 200, restored: false, factsRequests: 0 };
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const problem = (status: number) => route.fulfill({ status, contentType: 'application/problem+json', json: {
      status, title: '简历资料读取失败', detail: status === 403 ? '简历资料访问已撤销' : '简历资料暂不可用',
      code: status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: status === 503,
    } });
    if (path === '/candidates') return route.fulfill({ json: { ...empty, items: [person] } });
    if (path === '/candidates/person-a') return route.fulfill({ json: person });
    if (path === '/candidates/person-a/resume-versions') return route.fulfill({ json: { ...empty, items: [{ id: 'permission-resume', versionNo: 1, originalFileName: '权限回归简历.pdf', createdAt: person.createdAt }] } });
    if (path === '/resume-versions/permission-resume/facts') {
      state.factsRequests++;
      if (state.factsStatus !== 200) return problem(state.factsStatus);
      return route.fulfill({ json: {
        snapshot: { id: 'snapshot', resumeVersionId: 'permission-resume', snapshotVersion: 1, sourceType: 'AI_PARSE', fullName: '事实快照姓名', email: 'facts@example.com', phone: '13900002222', currentRole: '事实快照岗位', createdAt: person.createdAt },
        workExperiences: [{ id: 'work', itemKey: 'work', employer: state.restored ? '恢复后的履历公司' : '旧履历公司', roleName: '工程师', startDate: '2020-01-01', endDate: '2024-01-01', responsibilitiesJson: '[]', achievementsJson: '[]' }],
        projectExperiences: [], educations: [], simpleFacts: [], skills: [{ id: 'skill', skillName: '独立授权技能' }],
      } });
    }
    if (path === '/tasks') return route.fulfill({ json: [] });
    return route.fallback();
  });
  return state;
}

for (const status of [403, 503]) {
  test(`候选人事实缓存刷新 ${status} 后按授权范围显示并可重试`, async ({ page }, testInfo) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/talents');
    await page.getByRole('button', { name: '详情与版本', exact: true }).click();
    const content = page.locator('.candidate-detail-content');
    const basics = content.getByRole('region', { name: '关键资料', exact: true });
    await expect(basics).toContainText('facts@example.com');
    await expect(content.getByText(/旧履历公司/)).toBeVisible();
    await expect(content.getByRole('tab', { name: /证据与可靠性/ })).toHaveCount(0);
    await page.getByRole('dialog').press('Escape');
    state.factsStatus = status;
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.getByRole('button', { name: '详情与版本', exact: true }).click();
    const retry = content.getByRole('button', { name: '重试解析事实', exact: true });
    await expect(retry).toBeVisible();
    if (status === 403) {
      await expect(content.getByText('暂无该简历事实的查看权限', { exact: true })).toBeVisible();
      await expect(content.getByText(/旧履历公司/)).toHaveCount(0);
      await expect(basics).toContainText('基础档案姓名');
      await expect(basics).toContainText('base@example.com');
      await expect(basics).not.toContainText('facts@example.com');
    } else await expect(content.getByText(/旧履历公司/)).toBeVisible();
    state.factsStatus = 200;
    state.restored = true;
    await retry.focus();
    await page.keyboard.press('Enter');
    await expect(retry).toHaveCount(0);
    await content.getByRole('tab', { name: '专业事实', exact: true }).click();
    await expect(content.getByText(/恢复后的履历公司/)).toBeVisible();
    expect(state.factsRequests).toBeGreaterThanOrEqual(3);
    const dialog = page.getByRole('dialog');
    await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
    await expect(dialog).toHaveCSS('opacity', '1');
    expect((await new AxeBuilder({ page }).include('.candidate-detail-content').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    expect(errors).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`candidate-facts-recovery-${status}.png`), animations: 'disabled' });
  });
}

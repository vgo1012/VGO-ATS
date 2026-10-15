import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

type Source = 'candidate' | 'facts' | 'source';
const empty = { items: [], hasMore: false, nextCursor: null };

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: { candidate: 200, facts: 200, source: 200 }, requests: { candidate: 0, facts: 0, source: 0 } };
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/applications') return route.fulfill({ json: { ...empty, items: [{
      id: 'app-permission', candidateId: 'person-a', jdId: 'jd-a', resumeVersionId: 'permission-resume', ownerUserId: 'hr-a', currentStage: 'ONBOARDED',
    }] } });
    const source: Source | undefined = path === '/candidate-batch' ? 'candidate'
      : path === '/resume-version-fact-summaries' ? 'facts'
        : path === '/resume-version-sources' ? 'source' : undefined;
    if (!source) return route.fallback();
    state.requests[source]++;
    const status = state.status[source];
    if (status !== 200) return route.fulfill({ status, contentType: 'application/problem+json', json: {
      status, title: '候选人资料读取失败', detail: `${source}资料${status === 403 ? '无权读取' : '暂不可用'}`,
      code: status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: status === 503,
    } });
    const data = source === 'candidate' ? [{
      id: 'person-a', fullName: '权限回归候选人', primaryPhone: '13987654321', primaryEmail: 'permission@example.com', ownerUserId: 'hr-a',
    }] : source === 'facts' ? [{ resumeVersionId: 'permission-resume', facts: {
      snapshot: { age: 29, gender: 'FEMALE', createdAt: '2026-09-06T01:00:00Z' },
      educations: [{ degreeName: '硕士' }], workExperiences: [{ startDate: '2020-01-01', endDate: '2025-01-01' }],
      skills: [{ id: 'skill', skillName: '权限测试技能' }],
    } }] : [{ resumeVersionId: 'permission-resume', source: { sourceChannelName: '权限测试内推渠道' } }];
    return route.fulfill({ json: data });
  });
  return state;
}

async function revisit(page: Page) {
  await page.getByRole('button', { name: '面试日历', exact: true }).first().click();
  await expect(page).toHaveURL(/\/schedule$/);
  await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
  await page.getByRole('button', { name: '录用交付', exact: true }).click();
  await expect(page).toHaveURL(/\/onboarded$/);
}

for (const rejected of ['candidate', 'facts', 'source', 'all-unavailable'] as const) {
  test(`已入职缓存刷新 ${rejected} 后按数据来源保护字段并支持恢复`, async ({ page }, testInfo) => {
    const pageErrors: string[] = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    const state = await fixture(page);
    await page.goto('/onboarded');
    const row = page.locator('tr[data-row-key="app-permission"]');
    await expect(row).toContainText('13987654321');
    await expect(row).toContainText('29 岁');
    await expect(row).toContainText('硕士');
    await expect(row).toContainText('权限测试技能');
    await expect(row).toContainText('权限测试内推渠道');
    if (rejected === 'all-unavailable') state.status = { candidate: 503, facts: 503, source: 503 };
    else state.status[rejected] = 403;
    await revisit(page);
    const retry = page.getByRole('button', { name: '重试候选人资料', exact: true });
    await expect(retry).toBeVisible();
    await expect(page.getByText('部分候选人资料暂不可用，已授权信息仍可查看', { exact: true })).toBeVisible();
    if (rejected === 'candidate') {
      await expect(row).not.toContainText('13987654321');
      await expect(row).not.toContainText('权限回归候选人');
      await expect(row).toContainText('联系方式需授权');
    } else await expect(row).toContainText('13987654321');
    for (const text of ['29 岁', '硕士', '权限测试技能']) {
      if (rejected === 'facts') await expect(row).not.toContainText(text);
      else await expect(row).toContainText(text);
    }
    if (rejected === 'facts') await expect(row.getByRole('cell', { name: '—', exact: true })).toHaveCount(4);
    if (rejected === 'source') {
      await expect(row).not.toContainText('权限测试内推渠道');
      await expect(row).toContainText('来源需授权');
    }
    else await expect(row).toContainText('权限测试内推渠道');
    await expect(row).toContainText('高级前端工程师');
    await expect(row).toContainText('招聘负责人');
    await expect(row.getByRole('button', { name: '查看简历', exact: true })).toBeVisible();
    if (rejected !== 'all-unavailable') expect(state.requests[rejected]).toBeGreaterThan(1);
    if (rejected === 'facts') {
      for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
      for (const width of [390, 1440]) {
        await page.setViewportSize({ width, height: 1000 });
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
        await retry.scrollIntoViewIfNeeded();
        await page.screenshot({ path: testInfo.outputPath(`onboarded-permission-${width}.png`), fullPage: true, animations: 'disabled' });
      }
      expect((await new AxeBuilder({ page }).include('.onboarded-page').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    }
    state.status = { candidate: 200, facts: 200, source: 200 };
    await retry.focus();
    await page.keyboard.press('Enter');
    await expect(row).toContainText('13987654321');
    await expect(row).toContainText('29 岁');
    await expect(row).toContainText('硕士');
    await expect(row).toContainText('权限测试技能');
    await expect(row).toContainText('权限测试内推渠道');
    await expect(retry).toHaveCount(0);
    expect(pageErrors).toEqual([]);
  });
}

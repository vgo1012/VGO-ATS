import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const width of [390, 1440]) {
  test(`收件总数在 ${width}px 随查询更新且翻页不变`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await reviewWorkspace(page);
    const counts: URL[] = [];
    let deleted = false;
    await page.route('**/api/v1/intakes**', route => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith('/ignore')) {
        deleted = true;
        return route.fulfill({ json: {} });
      }
      if (url.pathname.endsWith('/count')) {
        counts.push(url);
        return route.fulfill({ json: { count: url.searchParams.has('name') ? 0 : deleted ? 120 : 121, hasMore: false, nextCursor: null } });
      }
      const second = url.searchParams.has('cursor');
      return route.fulfill({ json: {
        items: deleted || url.searchParams.has('name') ? [] : [{
          id: second ? 'receipt-21' : 'receipt-1', originalFileName: second ? '第21份简历.pdf' : '第1份简历.pdf',
          ownerUserId: 'hr-a', sourceChannelId: 'source', status: 'CONFIRMED', importStatus: 'IMPORTED',
          scanStatus: 'CLEAN', version: 1, jdVersionId: 'jd-v2', jdTitle: 'IT运维工程师', scoreStatus: 'SUCCEEDED', createdAt: '2026-09-09T08:35:00Z',
        }], hasMore: !second && !deleted && !url.searchParams.has('name'), nextCursor: second ? null : 'next',
      } });
    });
    await page.goto('/settings/intakes');
    const drawer = page.getByRole('dialog', { name: '收件校验', exact: true });
    const pager = drawer.getByRole('navigation', { name: '导入任务分页' });
    await expect(pager).toContainText('共121条记录');
    await expect(pager).toContainText('共7页');
    await pager.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(drawer.getByText('第21份简历.pdf', { exact: true })).toBeVisible();
    await expect(pager).toContainText('共121条记录');
    expect(counts.every(url => !url.searchParams.has('cursor'))).toBe(true);
    if (width === 1440) {
      await drawer.getByRole('button', { name: /^删\s*除$/ }).click();
      await page.getByRole('button', { name: '确认删除', exact: true }).click();
      await expect(pager).toContainText('共120条记录');
    }
    await drawer.getByPlaceholder('已识别候选人姓名').fill('无匹配姓名');
    await drawer.getByRole('button', { name: /^查\s*询$/ }).click();
    await expect(pager).toContainText('共0条记录');
    await expect(pager.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('1');
    expect(counts.at(-1)?.searchParams.get('name')).toBe('无匹配姓名');
    await pager.scrollIntoViewIfNeeded();
    await expect(pager).toBeInViewport();
    await page.screenshot({ path: testInfo.outputPath(`intake-total-${width}.png`), fullPage: true });
  });
}

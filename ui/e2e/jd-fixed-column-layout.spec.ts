import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('JD固定操作列不覆盖表头或分页', async ({ page }, info) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/jds?**', route => route.fulfill({ json: { items: Array.from({ length: 50 }, (_, i) => ({ id: `jd-${i}`, title: `运维工程师 ${i}`, ownerUserId: 'hr-a', currentVersionNo: 1, currentParseStatus: 'SUCCEEDED' })), hasMore: false } }));
  await page.goto('/recruitment/setup');
  const heading = page.getByRole('heading', { name: 'JD 库' });
  const introductionTitle = page.getByText('同名同地区可创建独立 JD', { exact: true });
  const introduction = introductionTitle.locator('..');
  await expect(heading).toBeVisible();
  await expect(introductionTitle).toBeVisible();
  await expect(introduction).toContainText('每份 JD 有独立编号、负责人和 HC；多人分别招聘时填写各自分配的名额，工作地区如实填写。');
  await expect(page.getByText('填写岗位名称、招聘人数（HC）、招聘周期和 JD 原文。Offer 发出后占用名额，推荐和面试可同时推进多人。', { exact: true })).toHaveCount(0);
  const headingBox = await heading.boundingBox();
  const introductionBox = await introduction.boundingBox();
  expect(headingBox).not.toBeNull();
  expect(introductionBox).not.toBeNull();
  expect(introductionBox!.y - (headingBox!.y + headingBox!.height)).toBeLessThanOrEqual(5);
  const lineHeight = await introduction.evaluate(element => parseFloat(getComputedStyle(element).lineHeight));
  const expectedLines = page.viewportSize()!.width <= 600 ? 3 : 2;
  expect(introductionBox!.height).toBeLessThanOrEqual(lineHeight * expectedLines + 1);
  expect(await introduction.evaluate(element => element.scrollHeight - element.clientHeight)).toBeLessThanOrEqual(1);
  for (const width of [1280, 1440, 1920, 768, 390]) {
    await page.setViewportSize({ width, height: 800 });
    const content = page.locator('.ant-table-content').first();
    await expect(content).toBeVisible();
    await content.scrollIntoViewIfNeeded();
    expect(await content.evaluate(element => element.scrollHeight)).toBeGreaterThan(await content.evaluate(element => element.clientHeight));
    await expect(content).toHaveCSS('overflow-y', 'auto');
    const workspace = await page.locator('.jd-library-page').boundingBox();
    const paginationBox = await page.getByRole('navigation', { name: 'JD 库分页' }).boundingBox();
    expect(workspace).not.toBeNull();
    expect(paginationBox).not.toBeNull();
    if (width > 600) expect(Math.abs((workspace!.y + workspace!.height) - (paginationBox!.y + paginationBox!.height))).toBeLessThanOrEqual(1);
    for (const end of [false, true]) {
      await content.evaluate((element, atEnd) => { element.scrollTop = atEnd ? element.scrollHeight : 90; element.scrollLeft = element.scrollWidth; }, end);
      const header = page.getByRole('columnheader', { name: '操作', exact: true });
      await header.scrollIntoViewIfNeeded();
      await expect(header).toBeInViewport();
      const headerHit = await header.evaluate((element, viewportWidth) => {
        const rect = element.getBoundingClientRect();
        const hit = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
        return { viewportWidth, ownsPoint: element.contains(hit), rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }, hit: hit?.className };
      }, width);
      expect(headerHit.ownsPoint, JSON.stringify(headerHit)).toBe(true);
      const pagination = page.locator('.compact-pagination').first();

      await pagination.scrollIntoViewIfNeeded();
      expect(await pagination.evaluate(element => { const r = element.getBoundingClientRect(); return element.contains(document.elementFromPoint(r.right - 25, r.y + r.height / 2)); })).toBe(true);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    }
    await page.screenshot({ path: info.outputPath(`jd-${width}.png`) });
  }
});

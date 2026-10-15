import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('设置移除云存储入口且旧地址不再加载存储状态', async ({ page }, info) => {
  await reviewWorkspace(page);
  const storageRequests: string[] = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/admin/storage')) storageRequests.push(request.url());
  });
  await page.goto('/settings/preferences');
  await expect(page.locator('.settings-content')).toBeVisible();
  await expect(page.locator('a[href="/admin/storage"]')).toHaveCount(0);
  const settings = page.getByRole('combobox', { name: '切换设置页面', exact: true });
  if (await settings.isVisible()) {
    await settings.click();
    await expect(page.locator('.ant-select-dropdown:visible')).toBeVisible();
    await expect(page.getByRole('option', { name: '云存储', exact: true })).toHaveCount(0);
    await page.keyboard.press('Escape');
  } else {
    await expect(page.getByRole('link', { name: /身份与组织/ })).toBeVisible();
  }
  await page.screenshot({ path: info.outputPath(`settings-without-storage-${info.project.name}.png`), animations: 'disabled' });
  await page.goto('/admin/storage');
  await expect(page.getByText('页面不存在', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '返回首页', exact: true })).toHaveAttribute('href', '/');
  expect(storageRequests).toEqual([]);
});

test('settings keep intake filters aligned and pagination outside the scrolling rows', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/intakes?**', route => route.fulfill({ json: {
    items: Array.from({ length: 20 }, (_, index) => ({
      id: `receipt-${index}`, originalFileName: `前端工程师候选人 ${index + 1} 的简历.pdf`,
      ownerUserId: 'hr-a', sourceChannelId: 'source', sizeBytes: 300000,
      status: 'READY_FOR_CONFIRMATION', importStatus: 'PENDING', scanStatus: 'CLEAN', version: 1,
      createdAt: '2026-09-09T08:35:00Z',
    })), hasMore: true, nextCursor: 'next-page',
  } }));
  await page.goto('/settings/intakes');
  await expect(page.getByText('前端工程师候选人 1 的简历.pdf', { exact: true })).toBeVisible();
  await expect(page.getByText('核对上传简历的身份与安全状态', { exact: false })).toHaveCount(0);
  const drawer = page.getByRole('dialog', { name: '收件校验', exact: true });
  for (const [width, height] of [[390, 900], [768, 900], [1280, 720], [1440, 900], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight && document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await expect.poll(async()=>Math.abs((await drawer.boundingBox())!.width-Math.min(width*.94,1180))).toBeLessThan(1);
    const pager = drawer.locator('.data-page-controls');
    await expect(pager).toBeInViewport({ ratio: 1 });
    const before = await pager.boundingBox();
    const table = drawer.locator('.ant-table-content');
    expect(await table.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true);
    expect(await table.evaluate(element => element.scrollWidth > element.clientWidth + 1)).toBe(width < 1100);
    await table.evaluate((element, narrow) => {
      element.scrollTop = element.scrollHeight;
      if (narrow) element.scrollLeft = element.scrollWidth;
    }, width < 1100);
    const actionHeader = drawer.getByRole('columnheader', { name: '操作' });
    expect(await actionHeader.evaluate(element => {
      const box = element.getBoundingClientRect();
      return document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)?.closest('th') === element;
    })).toBe(true);
    const after = await pager.boundingBox();
    await expect(pager).toBeInViewport({ ratio: 1 });
    for (const side of ['x', 'y', 'width', 'height'] as const) {
      expect(Math.abs(after![side] - before![side])).toBeLessThan(1);
    }
    await table.evaluate(element => { element.scrollTop = 0; });
    if (width >= 1280) {
      const fields = await page.locator('.intake-filters .ant-form-item').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().bottom));
      expect(Math.max(...fields) - Math.min(...fields)).toBeLessThan(2);
    }
    await expect(page.locator('.ant-spin-container').first()).toHaveCSS('opacity', '1');
    if (width === 390 || width === 1440) await page.screenshot({ path: testInfo.outputPath(`intakes-${width}.png`), fullPage: true, animations: 'disabled' });
  }
  await drawer.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(drawer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
  await drawer.getByRole('button', { name: '上一页', exact: true }).click();
  await expect(drawer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('1');
  expect((await new AxeBuilder({ page }).include('.intake-drawer').analyze()).violations).toEqual([]);
});

test('every settings route stays in the viewport and account pagination remains visible', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/admin/users**', route => route.fulfill({ json: Array.from({ length: 25 }, (_, index) => ({
    id: `hr-${index}`, displayName: `招聘成员 ${index}`, workEmail: `hr${index}@example.com`,
    status: 'ACTIVE', roles: ['RECRUITER'], mustChangePassword: false, version: '1',
  })) }));
  for (const path of ['/recruitment/setup', '/settings/preferences', '/admin/reports', '/admin/source-channels', '/admin/scoring', '/admin/identity']) {
    await page.goto(path);
    await expect(page.locator('.settings-content')).toBeVisible();
    await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight)).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (path === '/admin/scoring') await expect(page.getByRole('cell', { name: '当前生效', exact: true })).toBeInViewport({ ratio: 1 });
  }
  await expect(page.getByText('hr0@example.com', { exact: true })).toBeVisible();
  const pagination = page.getByRole('navigation', { name: '招聘账号分页', exact: true });
  if (await page.evaluate(() => matchMedia('(max-width: 600px)').matches)) {
    const content = page.locator('.settings-content');
    expect(await content.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true);
    await pagination.scrollIntoViewIfNeeded();
    expect(await content.evaluate(element => element.scrollTop)).toBeGreaterThan(0);
  }
  await expect(pagination).toBeInViewport({ ratio: 1 });
  await expect(pagination.getByRole('spinbutton', { name: '每页条数', exact: true })).toHaveValue('20');
  await pagination.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(pagination.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
  await expect(page.getByText('hr20@example.com', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('settings-account-pagination.png'), fullPage: true });
});

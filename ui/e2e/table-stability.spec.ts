import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('long tables keep columns and page position stable when paginating', async ({ page }) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/admin/users**', route => route.fulfill({ json: Array.from({ length: 25 }, (_, i) => ({
    id: `u${i}`, displayName: i < 10 ? '测试成员' : '长名称测试成员',
    workEmail: i < 10 ? `hr${i}@example.com` : `long.name.${i}@recruitment.example.com`,
    homeDepartmentName: i < 10 ? '人力资源中心' : '示例跨区域运营部门的招聘支持小组',
    directLeaderName: '测试领导', status: 'ACTIVE', version: 1, mustChangePassword: true,
  })) }));
  await page.goto('/admin/identity');
  const table = page.locator('.ant-table-content').first();
  await expect(table).toContainText('hr0@example.com');
  const headers = () => page.getByRole('columnheader').evaluateAll(elements => elements.map(element => ({
    width: element.getBoundingClientRect().width, x: element.getBoundingClientRect().x,
  })));
  const before = await headers();
  const pagination = page.getByLabel('招聘账号分页');
  await pagination.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(pagination.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
  await expect(table).toContainText('long.name.20@recruitment.example.com');
  expect(await headers()).toEqual(before);
  // 分页点击会让设置面板滚到页脚；回到表头再比较表格自身的吸顶位置。
  await page.locator('.settings-content').evaluate(element => { element.scrollTop = 0; });
  const top = await table.boundingBox();
  const head = await page.getByRole('columnheader').first().boundingBox();
  const appHeader = await page.locator('.app-header').boundingBox();
  expect(head!.y).toBeGreaterThanOrEqual(appHeader!.height);
  await table.evaluate(element => { element.scrollTop = 200; });
  const scrolledHead = await page.getByRole('columnheader').first().boundingBox();
  expect(Math.abs(scrolledHead!.y - top!.y)).toBeLessThan(2);
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
});

test('password-change status tag keeps accessible warning contrast', async ({ page }) => {
  await reviewWorkspace(page);
  await page.route('**/api/v1/admin/users**', route => route.fulfill({ json: [{
    id: 'password-change-user', displayName: '待改密成员', workEmail: 'password-change@example.com',
    homeDepartmentName: '人力资源中心', directLeaderName: '测试领导', status: 'ACTIVE', version: 1, mustChangePassword: true,
  }] }));
  await page.goto('/admin/identity');
  const tag = page.getByText('待改密', { exact: true });
  await expect(tag).toBeVisible();
  await expect(tag).toHaveCSS('color', 'rgb(173, 78, 0)');
  const contrast = await new AxeBuilder({ page }).include('.identity-admin-page .ant-tag-orange').withRules(['color-contrast']).analyze();
  expect(contrast.violations).toEqual([]);
});

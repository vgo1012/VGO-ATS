import { expect, test } from '@playwright/test';
import { emptyRecruitmentAnalysis, mockRecruitmentAnalysis, recruitmentAnalysis } from './fixtures/recruitment-analysis';

const populatedReport = {
  ...recruitmentAnalysis,
  channels: [21, 1, 1, 1, 1, 1].map((resumes, index) => ({ id: String(index), name: ['官网招聘', 'BOSS直聘', '猎聘', '内部推荐', '前程无忧', '官网投递'][index]!, resumes, applications: resumes, offers: 0, onboarded: 0 })),
  portraits: [...recruitmentAnalysis.portraits.filter(row => row.dimension !== 'INTENDED_LOCATION'),
    ...['杭州', '区域驻场', '华北数据中心', '南通', '未知', '上海、北京、深圳长期驻场'].map((label, index) => ({ dimension: 'INTENDED_LOCATION' as const, label, resumes: 21 - index, passed: 0, rejected: 0, pending: 0, withdrawn: 0 }))],
};

for (const width of [390, 768, 1280, 1440, 1920]) {
  for (const empty of [false, true]) {
    test(`${empty ? '空数据' : '有数据'}招聘分析在 ${width}px 完整显示且无页面横向溢出`, async ({ page }, testInfo) => {
      test.skip(testInfo.project.name === 'mobile' && width !== 390, '手机项目保留真实窄屏，其他断点由桌面项目覆盖。');
      await page.setViewportSize({ width, height: 900 });
      await page.emulateMedia({ reducedMotion: 'reduce' });
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      await mockRecruitmentAnalysis(page, { report: empty ? emptyRecruitmentAnalysis : populatedReport });
      await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
      await expect(page.getByRole('heading', { level: 1, name: '招聘分析', exact: true })).toBeVisible();
      for (const name of ['招聘流程转化', '招聘趋势', '地区分布', '招聘需求与风险', '年龄分布', '学历分布', '招聘渠道']) {
        await expect(page.getByRole('heading', { level: 2, name, exact: true })).toBeVisible();
      }
      const summary = page.locator('.analytics-summary');
      expect(await summary.locator('dt').allTextContents()).toEqual(['入库简历', '进入流程', '进入入职流程', '已入职']);
      await expect(summary.locator('.sr-only')).toHaveText(empty ? ['0', '0', '0', '0'] : ['120', '84', '12', '7']);
      await expect(page.locator('.analytics-funnel-stage')).toHaveCount(8);
      await expect(page.locator('.analytics-funnel-bar')).toHaveCount(8);
      await expect(page.locator('.analytics-ambient,.analytics-city-chart,.analytics-funnel-dots,.analytics-hc-grid')).toHaveCount(0);
      await expect(page.getByRole('combobox', { name: '动效速度' })).toHaveCount(0);
      if (empty) {
        for (const text of ['暂无岗位工作城市数据', '所选期间暂无招聘流程', '暂无年龄分布数据', '暂无学历分布数据', '暂无招聘渠道数据']) {
          await expect(page.getByText(text, { exact: true })).toBeVisible();
        }
        await expect(page.getByText('— 天', { exact: true })).toHaveCount(2);
        expect(await page.locator('.analytics-funnel-bar i').evaluateAll(items => items.map(item => item.getBoundingClientRect().height))).toEqual(Array(8).fill(0));
      } else {
        const cities = page.getByRole('list', { name: '入库简历工作城市数据' });
        await expect(cities.getByRole('listitem')).toHaveCount(6);
        expect(await cities.locator('.reference-location-label > span:first-child').allTextContents()).toEqual(['杭州', '区域驻场', '华北数据中心', '南通', '未知', '上海、北京、深圳长期驻场']);
        expect(await cities.locator('.reference-location-label strong').allTextContents()).toEqual(['21 人', '20 人', '19 人', '18 人', '17 人', '16 人']);
        await expect(page.locator('.reference-channel').getByRole('listitem')).toHaveCount(4);
        await expect(page.locator('.reference-channel').getByText('其它', { exact: true })).toBeVisible();
      }
      if (width >= 1280) {
        const [funnel, trend, regions, demand] = await Promise.all([
          page.locator('.analytics-funnel-panel').boundingBox(), page.locator('.analytics-trend-panel').boundingBox(),
          page.locator('.analytics-region-panel').boundingBox(), page.locator('.analytics-hc-panel').boundingBox(),
        ]);
        expect(Math.abs(funnel!.y - trend!.y)).toBeLessThan(2);
        expect(funnel!.x + funnel!.width).toBeLessThanOrEqual(trend!.x);
        expect(funnel!.width).toBeGreaterThan(trend!.width * 1.4);
        expect(regions!.y).toBeGreaterThanOrEqual(funnel!.y + funnel!.height);
        expect(Math.abs(regions!.y - demand!.y)).toBeLessThan(2);
        const panelTops = await page.locator('.analytics-portrait-row > .analytics-panel').evaluateAll(items => items.map(item => Math.round(item.getBoundingClientRect().top)));
        expect(panelTops).toHaveLength(3);
        expect(new Set(panelTops).size).toBe(1);
      }
      const overlaps = await page.locator('.analytics-dimension-controls').evaluate(element => {
        const boxes = [...element.children].map(child => child.getBoundingClientRect());
        return boxes.some((box, index) => boxes.slice(index + 1).some(other =>
          Math.min(box.right, other.right) - Math.max(box.left, other.left) > 1
          && Math.min(box.bottom, other.bottom) - Math.max(box.top, other.top) > 1));
      });
      expect(overlaps).toBe(false);
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`analytics-${empty ? 'empty' : 'populated'}-${width}.png`), fullPage: true, animations: 'disabled' });
      expect(errors).toEqual([]);
    });
  }
}

test('矮桌面和等效 200% 缩放保留员工表与趋势对话框键盘操作', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis, hrWorkload: [] } });
  await page.goto('/recruitment-analysis');
  await expect(page.getByRole('heading', { name: '员工招聘统计' })).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 600 });
  const dashboard = (await page.locator('.analytics-dashboard').boundingBox())!;
  const employees = (await page.locator('.analytics-hr-panel').boundingBox())!;
  expect(employees.y).toBeGreaterThanOrEqual(dashboard.y + dashboard.height);
  await page.setViewportSize({ width: 720, height: 450 });
  const detail = page.getByRole('button', { name: '查看数据表' });
  await detail.focus();
  await expect(detail).toBeInViewport();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog', { name: '招聘趋势明细' })).toBeVisible();
  await expect(page.locator('.workspace-dialog-root .ant-modal-mask:visible')).toHaveCSS('backdrop-filter', 'blur(4px)');
  await expect(page.getByRole('dialog', { name: '招聘趋势明细' }).locator('.ant-modal-container')).toHaveCSS('border-radius', '18px');
  await expect(page.getByRole('dialog', { name: '招聘趋势明细' })).toHaveCSS('opacity', '1');
  await page.screenshot({ path: test.info().outputPath('trend-dialog-unified.png') });
  await page.keyboard.press('Escape');
  await expect(detail).toBeFocused();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

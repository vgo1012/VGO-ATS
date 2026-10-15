import { expect, test, type Page } from '@playwright/test';
import {
  analyticsActor,
  emptyRecruitmentAnalysis,
  mockRecruitmentAnalysis,
  recruitmentAnalysis,
} from './fixtures/recruitment-analysis';

const reportPath = '/api/v1/reports/recruitment-analysis';

for (const scenario of [
  { period: 'week', count: 7, row: '2026-09-15 30 4 3' },
  { period: 'month', count: 5, row: '2026-09-14 ～ 2026-09-20 30 4 3' },
  { period: 'quarter', count: 3, row: '2026-09-01 ～ 2026-09-30 84 12 7' },
  { period: 'year', count: 4, row: '2026-07-01 ～ 2026-09-30 84 12 7' },
]) {
  test(`招聘趋势 ${scenario.period} 按对应粒度聚合且数据表保留准确数量`, async ({ page }) => {
    await mockRecruitmentAnalysis(page);
    await page.goto(`/recruitment-analysis?period=${scenario.period}&date=2026-09-15`);
    await page.getByRole('button', { name: '查看数据表', exact: true }).click();
    await expect(page.getByRole('row', { name: /^2026-\d{2}-\d{2} / })).toHaveCount(scenario.count);
    await expect(page.getByRole('row', { name: scenario.row, exact: true })).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: '关闭', exact: true }).click();
    await expect(page.getByRole('row', { name: /^2026-\d{2}-\d{2} / })).toHaveCount(0);
  });
}

async function chooseOption(page: Page, label: string, option: string | RegExp) {
  await page.getByLabel(label, { exact: true }).click();
  await page.getByRole('option', { name: option }).click();
}

function latestReportParams(requests: URL[]) {
  return Object.fromEntries(requests.at(-1)?.searchParams.entries() ?? []);
}

test('有权限用户从真实主导航进入招聘分析并由 URL 恢复全部筛选', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/recruitment/setup');
  await expect(page.getByRole('heading', { name: 'JD 库' })).toBeVisible();

  const navigation = page.getByRole('navigation', { name: '主导航', exact: true });
  await expect(navigation.getByRole('button').first()).toHaveAccessibleName('招聘洞察');
  await expect(navigation.getByRole('button', { name: '概览', exact: true })).toHaveCount(0);
  await navigation.getByRole('button', { name: '招聘洞察', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险', exact: true })).toBeVisible();
  await expect(navigation.getByRole('button', { name: '招聘洞察', exact: true })).toHaveAttribute('aria-current', 'page');

  const periodControls = page.getByLabel('统计周期', { exact: true });
  await periodControls.getByText('年', { exact: true }).click();
  await expect(periodControls.getByRole('radio', { name: '年', exact: true })).toBeChecked();
  await chooseOption(page, '岗位归属', '外部招聘');
  await chooseOption(page, '部门', '产品研发部');
  await chooseOption(page, '筛选岗位（含历史版本）', /平台工程师/);

  await expect.poll(() => latestReportParams(state.reportRequests)).toEqual({
    from: '2025-12-31T16:00:00.000Z',
    to: '2026-12-31T16:00:00.000Z',
    departmentId: 'dept-product',
    jdId: 'jd-platform',
    recruitmentType: 'EXTERNAL',
  });
  await expect(page).toHaveURL(/period=year/);
  await expect(page).toHaveURL(/departmentId=dept-product/);
  await expect(page).toHaveURL(/jdId=jd-platform/);
  await expect(page).toHaveURL(/recruitmentType=EXTERNAL/);

  const requestsBeforeRefresh = state.reportRequests.length;
  await page.getByRole('button', { name: '刷新', exact: true }).click();
  await expect.poll(() => state.reportRequests.length).toBeGreaterThan(requestsBeforeRefresh);
  expect(latestReportParams(state.reportRequests)).toEqual({
    from: '2025-12-31T16:00:00.000Z',
    to: '2026-12-31T16:00:00.000Z',
    departmentId: 'dept-product',
    jdId: 'jd-platform',
    recruitmentType: 'EXTERNAL',
  });

  const requestsBeforeReload = state.reportRequests.length;
  await page.reload();
  await expect(page.getByRole('radio', { name: '年', exact: true })).toBeChecked();
  await expect(page.getByLabel('选择统计期间', { exact: true })).toHaveValue('2026');
  await expect(page.locator('.analytics-dimension-controls label').filter({ hasText: '岗位归属' })).toContainText('外部招聘');
  await expect(page.locator('.analytics-dimension-controls label').filter({ hasText: '部门' })).toContainText('产品研发部');
  await expect(page.locator('.analytics-jd')).toContainText('平台工程师');
  await expect.poll(() => state.reportRequests.length).toBeGreaterThan(requestsBeforeReload);
  expect(latestReportParams(state.reportRequests)).toEqual({
    from: '2025-12-31T16:00:00.000Z',
    to: '2026-12-31T16:00:00.000Z',
    departmentId: 'dept-product',
    jdId: 'jd-platform',
    recruitmentType: 'EXTERNAL',
  });
  expect(state.reportRequests.every(url => url.pathname === reportPath)).toBe(true);
});

for (const width of [390, 1440]) {
  test(`地区排名在 ${width}px 保留真实标签与数量`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await mockRecruitmentAnalysis(page);
    await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
    const locations = page.getByRole('list', { name: '入库简历工作城市数据' });
    await expect(locations.getByRole('listitem')).toHaveCount(2);
    expect(await locations.locator('.reference-location-label > span:first-child').allTextContents()).toEqual(['上海', '北京']);
    expect(await locations.locator('.reference-location-label strong').allTextContents()).toEqual(['46 人', '38 人']);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await locations.screenshot({ path: testInfo.outputPath(`locations-${width}.png`), animations: 'disabled' });
  });

  test(`招聘分析区块在 ${width}px 无页面级横向溢出并保留截图`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const pageErrors: string[] = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis,
      channels: recruitmentAnalysis.channels.map(row => ({ ...row, resumes: row.id === 'channel-boss' ? 80 : 40 })),
      portraits: recruitmentAnalysis.portraits.map(row => ({ ...row, resumes: ({ '25–29 岁': 72, '30–34 岁': 48, '本科': 86, '硕士': 34, '上海': 66, '北京': 54 } as Record<string, number>)[row.label] ?? 0 })),
    } });
    await page.goto('/recruitment-analysis?period=month&date=2026-09-15');

    await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险', exact: true })).toBeVisible();
    await expect(page.getByLabel('统计周期', { exact: true }).getByRole('radio')).toHaveCount(5);
    await expect(page.getByLabel('统计周期', { exact: true }).getByText('累计', { exact: true })).toBeVisible();
    await expect(page.getByLabel('选择统计期间', { exact: true })).toBeVisible();
    for (const title of ['招聘需求与风险', '招聘流程转化', '招聘趋势', '招聘渠道', '年龄分布', '学历分布']) {
      await expect(page.getByRole('heading', { level: 2, name: title })).toBeVisible();
    }
    const funnelStages = page.locator('.analytics-funnel-stage');
    await expect(funnelStages).toHaveCount(8);
    await expect(funnelStages.nth(0).locator('strong .sr-only')).toHaveText('120');
    await expect(funnelStages.nth(0)).toContainText('按人去重');
    await expect(funnelStages.nth(1).locator('strong .sr-only')).toHaveText('84');
    await expect(funnelStages.nth(1)).toContainText('比例基数');
    await expect(funnelStages.nth(2)).toContainText('占流程 67%');
    await expect(page.locator('.analytics-dashboard')).not.toContainText(/\d+\.\d+\s*(%|天)/);
    await expect(page.getByText('筛选通过', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('img', { name: /按流程创建日期统计/ })).toBeVisible();
    for (const stage of ['一面', '二面', '三面+']) await expect(page.getByText(stage, { exact: true })).toBeVisible();
    await expect(page.locator('#analysis-channels').getByText('BOSS直聘', { exact: true })).toBeVisible();
    await expect(page.getByRole('img', { name: /25–29 岁 72，60%/ })).toBeVisible();
    await expect(page.getByRole('list', { name: '入库简历工作城市数据' })).toBeVisible();
    await expect(page.locator('.analytics-capacity-track')).toBeVisible();
    await expect(page.getByText(/^新增流程 \d+/)).toHaveCount(0);
    await expect(page.locator('.analytics-channel-note')).toHaveCSS('text-align', 'center');
    if (width === 1440) {
      const ageBox = await page.locator('.analytics-age-panel').boundingBox();
      const educationBox = await page.locator('.analytics-education-panel').boundingBox();
      expect(Math.abs(educationBox!.y - ageBox!.y)).toBeLessThan(2);
    }
    await page.getByRole('radiogroup', { name: '画像结果' }).getByText('通过', { exact: true }).click();
    await expect(page.getByRole('radio', { name: '通过', exact: true })).toBeChecked();
    await expect(page.getByRole('img', { name: /25–29 岁 24，59%/ })).toBeVisible();
    await page.getByRole('radiogroup', { name: '画像结果' }).getByText('全部', { exact: true }).click();
    await expect(page.getByRole('radio', { name: '全部', exact: true })).toBeChecked();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`recruitment-analysis-${width}.png`), fullPage: true, animations: 'disabled' });
    expect(pageErrors).toEqual([]);
  });
}

test('单年龄段占比条和峰值趋势数字保持在图表内', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: {
    ...recruitmentAnalysis,
    portraits: [
      { dimension: 'AGE', label: '25岁以下', resumes: 1, passed: 1, rejected: 0, pending: 0, withdrawn: 0 },
      ...recruitmentAnalysis.portraits.filter(row => row.dimension !== 'AGE'),
    ],
    trend: [{ date: '2026-09-22', applications: 3, offers: 1, onboarded: 0 }],
  } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const age = page.getByRole('img', { name: '25岁以下 1，100%' });
  await expect(age).toBeVisible();
  await expect(age.locator('.reference-age-row')).toHaveCount(1);
  await expect(age.locator('.reference-age-fill')).toHaveAttribute('style', /width: 100%/);
  await expect(age.getByText('100%')).toBeVisible();
  const trend = page.locator('.reference-trend-chart');
  await expect(trend.locator('svg')).toBeVisible();
  const top = (await trend.boundingBox())!.y;
  const textTops = await trend.locator('svg text').evaluateAll(items => items.map(item => item.getBoundingClientRect().top));
  expect(textTops.length).toBeGreaterThan(0);
  expect(Math.min(...textTops)).toBeGreaterThanOrEqual(top + 2);
  await page.screenshot({ path: testInfo.outputPath('age-bars-trend.png'), fullPage: true, animations: 'disabled' });
});

test('年龄占比条按数据比例显示四个年龄段', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockRecruitmentAnalysis(page, { report: {
    ...recruitmentAnalysis,
    portraits: [
      ...['25岁以下', '25–29岁', '35–39岁', '未知'].map((label, index) => ({ dimension: 'AGE' as const, label, resumes: [25, 17, 25, 33][index]!, passed: [25, 17, 25, 33][index]!, rejected: 0, pending: 0, withdrawn: 0 })),
      ...recruitmentAnalysis.portraits.filter(row => row.dimension !== 'AGE'),
    ],
  } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const age = page.getByRole('img', { name: /25岁以下 25，25%；25–29岁 17，17%；35–39岁 25，25%；未知 33，33%/ });
  await expect(age).toBeVisible();
  await expect(age.locator('.reference-age-row')).toHaveCount(4);
  const widths = await age.locator('.reference-age-fill').evaluateAll(items => items.map(item => Number.parseFloat((item as HTMLElement).style.width)));
  expect(widths).toEqual([25, 17, 25, 33]);
  await page.screenshot({ path: testInfo.outputPath('age-bars.png'), fullPage: true, animations: 'disabled' });
});

test('六个真实年龄段在占比条中完整显示', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const labels = ['25岁以下', '25–29岁', '30–34岁', '35–39岁', '40岁及以上', '未知'];
  await mockRecruitmentAnalysis(page, { report: {
    ...recruitmentAnalysis,
    portraits: [
      ...labels.map((label, index) => ({ dimension: 'AGE' as const, label, resumes: 6 - index, passed: 6 - index, rejected: 0, pending: 0, withdrawn: 0 })),
      ...recruitmentAnalysis.portraits.filter(row => row.dimension !== 'AGE'),
    ],
  } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const age = page.locator('.reference-age');
  await expect(age.locator('.reference-age-row')).toHaveCount(6);
  for (const label of labels) await expect(age.getByText(label, { exact: true })).toBeVisible();
  expect(await age.evaluate(element => element.scrollHeight - element.clientHeight)).toBeLessThanOrEqual(1);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
});

test('单渠道图例紧凑对齐且底部说明不被裁切', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis, channels: [recruitmentAnalysis.channels[0]!] } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const panel = page.locator('.analytics-channel-panel');
  const legend = panel.locator('.reference-chart-legend');
  const note = panel.locator('.analytics-channel-note');
  await expect(legend.getByText('BOSS直聘', { exact: true })).toBeVisible();
  await expect(legend).not.toContainText('%');
  const [panelBox, legendBox, noteBox, donutBox] = await Promise.all([panel.boundingBox(), legend.boundingBox(), note.boundingBox(), panel.locator('.reference-channel-donut').boundingBox()]);
  expect(panelBox).not.toBeNull();
  expect(legendBox).not.toBeNull();
  expect(noteBox).not.toBeNull();
  expect(legendBox!.y).toBeGreaterThan(panelBox!.y);
  expect(legendBox!.y + legendBox!.height).toBeLessThanOrEqual(panelBox!.y + panelBox!.height);
  expect(legendBox!.width).toBeLessThan(panelBox!.width * .45);
  expect(legendBox!.x - (donutBox!.x + donutBox!.width)).toBeLessThanOrEqual(16);
  expect(noteBox!.y + noteBox!.height).toBeLessThanOrEqual(panelBox!.y + panelBox!.height);
  await panel.locator('.reference-channel-donut').hover();
  await expect(panel.locator('.reference-channel-donut svg')).not.toContainText('BOSS直聘');
  await panel.screenshot({ path: testInfo.outputPath('single-channel-panel.png'), animations: 'disabled' });
});

test('未知和多地点记录保留原始标签，不推测城市', async ({ page }, testInfo) => {
  const portraits = recruitmentAnalysis.portraits.filter(row => row.dimension !== 'INTENDED_LOCATION');
  portraits.push({ dimension: 'INTENDED_LOCATION', label: '未知', resumes: 5, passed: 0, rejected: 0, pending: 0, withdrawn: 0 },
    { dimension: 'INTENDED_LOCATION', label: '上海、北京、深圳长期驻场', resumes: 3, passed: 0, rejected: 0, pending: 0, withdrawn: 0 });
  await mockRecruitmentAnalysis(page, { report: { ...recruitmentAnalysis, portraits } });
  await page.goto('/recruitment-analysis?period=month&date=2026-09-15');
  const locations = page.getByRole('list', { name: '入库简历工作城市数据' });
  await expect(locations.getByRole('listitem')).toHaveCount(2);
  expect(await locations.locator('.reference-location-label > span:first-child').allTextContents()).toEqual(['未知', '上海、北京、深圳长期驻场']);
  expect(await locations.locator('.reference-location-label strong').allTextContents()).toEqual(['5 人', '3 人']);
  await expect(locations.getByText('上海', { exact: true })).toHaveCount(0);
  await expect(locations.getByText('北京', { exact: true })).toHaveCount(0);
  await locations.screenshot({ path: testInfo.outputPath('unknown-and-multiple-locations.png'), animations: 'disabled' });
});

test('筛选后的聚合失败可重试且请求条件保持不变', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page, { reportStatus: 503 });
  const expected = {
    from: '2026-06-30T16:00:00.000Z',
    to: '2026-09-30T16:00:00.000Z',
    departmentId: 'dept-delivery',
    jdId: 'jd-delivery',
    recruitmentType: 'INTERNAL',
  };
  await page.goto('/recruitment-analysis?period=quarter&date=2026-09-15&departmentId=dept-delivery&jdId=jd-delivery&recruitmentType=INTERNAL');

  await expect(page.getByText('招聘分析加载失败', { exact: true })).toBeVisible();
  await expect(page.getByText('请重试；当前筛选条件已保留。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible();
  expect(latestReportParams(state.reportRequests)).toEqual(expected);

  state.reportStatus = 200;
  state.report = recruitmentAnalysis;
  await page.getByRole('button', { name: '重试', exact: true }).click();
  await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险' })).toBeVisible();
  expect(latestReportParams(state.reportRequests)).toEqual(expected);
  await expect(page.getByRole('radio', { name: '季度', exact: true })).toBeChecked();
  await expect(page.locator('.analytics-jd')).toContainText('客户交付经理');
});

test('服务端撤销报表范围时显示权限态且不提供无效重试', async ({ page }) => {
  await mockRecruitmentAnalysis(page, { reportStatus: 403 });
  await page.goto('/recruitment-analysis');
  await expect(page.getByText('暂无招聘分析查看权限', { exact: true })).toBeVisible();
  await expect(page.getByText('请使用有报表查看权限的账号。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重试', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险' })).toHaveCount(0);
});

test('没有路由权限时隐藏导航入口并由页面守卫拒绝访问', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page, {
    actor: { ...analyticsActor, roles: ['RECRUITER'], permissions: ['demand.read'] },
  });
  await page.goto('/recruitment/setup');
  await expect(page.getByRole('heading', { name: 'JD 库' })).toBeVisible();
  await expect(page.getByRole('button', { name: '招聘洞察', exact: true })).toHaveCount(0);
  await page.goto('/recruitment-analysis');
  await expect(page.getByText('无权访问', { exact: true })).toBeVisible();
  expect(state.reportRequests).toHaveLength(0);
});

test('会话失效时返回登录页且不读取聚合接口', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page, { actorStatus: 401 });
  await page.goto('/recruitment-analysis');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  expect(state.reportRequests).toHaveLength(0);
});

test('空数据仍保留需求口径并分别说明漏斗、渠道和画像空态', async ({ page }) => {
  await mockRecruitmentAnalysis(page, { report: emptyRecruitmentAnalysis });
  await page.goto('/recruitment-analysis?period=week&date=2026-09-15');
  await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险' })).toBeVisible();
  await expect(page.getByText('HC 数量', { exact: true })).toBeVisible();
  await expect(page.getByText('所选期间暂无招聘流程', { exact: true })).toBeVisible();
  await expect(page.getByText('暂无招聘渠道数据', { exact: true })).toBeVisible();
  await expect(page.getByText('暂无年龄分布数据', { exact: true })).toBeVisible();
  await expect(page.getByText('暂无学历分布数据', { exact: true })).toBeVisible();
  await expect(page.getByText('— 天', { exact: true })).toHaveCount(2);
  await expect(page.getByRole('img', { name: 'HC 占用 0，计划 0，占用率 —' })).toBeVisible();
  await expect(page.getByRole('button', { name: '查看统计口径与数据说明' })).toHaveCount(0);

});

test('旧概览入口跳转招聘分析且不请求旧概览数据', async ({ page }) => {
  await mockRecruitmentAnalysis(page);
  const obsoleteRequests: string[] = [];
  page.on('request', request => {
    const pathname = new URL(request.url()).pathname;
    if (['/api/v1/reports/overview', '/api/v1/management/workbench', '/api/v1/workbench'].includes(pathname)) obsoleteRequests.push(pathname);
  });
  await page.goto('/overview');
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  await expect(page.getByRole('heading', { level: 2, name: '招聘需求与风险', exact: true })).toBeVisible();
  expect(obsoleteRequests).toEqual([]);
});


test('已删除的设计与动效地址不再进入招聘分析', async ({ page }) => {
  const state = await mockRecruitmentAnalysis(page);
  for (const path of ['motions', 'designs']) {
    await page.goto(`/recruitment-analysis/${path}`);
    await expect(page.getByText('页面不存在', { exact: true })).toBeVisible();
    await expect(page.locator('.analytics-page')).toHaveCount(0);
  }
  expect(state.reportRequests).toHaveLength(0);
});

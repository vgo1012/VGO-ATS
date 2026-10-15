import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const rawTexts = new Map<string, string>();
const jobs = Array.from({ length: 56 }, (_, index) => {
  const number = index + 1;
  const id = `screening-jd-${number}`;
  const title = number === 1 ? '默认平台工程师' : number === 2 ? '数据平台架构师' : number === 3 ? '数据治理专家' : `招聘岗位 ${String(number).padStart(2, '0')}`;
  rawTexts.set(id, `${title}岗位原文：负责核心业务建设与跨团队协作。`);
  return {
    id,
    code: `SCREEN-${String(number).padStart(3, '0')}`,
    title,
    status: 'ACTIVE',
    currentVersionNo: number === 2 ? 4 : 1,
    clientId: null,
    clientName: null,
    ownerUserId: number === 2 ? 'hr-b' : 'hr-a',
    ownerDepartmentId: 'dept-a',
    createTime: '2026-09-01T00:00:00Z',
  };
});

async function mockScreeningJobs(page: Page) {
  await reviewWorkspace(page);
  const requests: URL[] = [];
  await page.route('**/api/v1/screening-candidates**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/v1/me/jd-scoring-configs/*/preferences', (route) => route.fulfill({ json: null }));
  await page.route('**/api/v1/jd-versions/historical-jd-version', (route) => route.fulfill({ json: {
    id: 'historical-jd-version', jdId: 'screening-jd-2', versionNo: 2, title: '数据平台架构师',
    rawText: '数据平台架构师历史 V2 岗位原文。', status: 'SUPERSEDED', sourceType: 'MANUAL',
    createId: 'hr-a', createTime: '2026-08-01T00:00:00Z',
  } }));
  await page.route('**/api/v1/jd-versions/*/active-scoring-config', (route) => {
    const jdVersionId = new URL(route.request().url()).pathname.split('/').at(-2)!;
    return route.fulfill({ json: {
      jdVersionId,
      scoringConfiguration: companyScoring,
      scoringConfigurationActive: true,
      workflowTemplateActive: false,
      readyForApplication: false,
    } });
  });
  await page.route('**/api/v1/jd-versions/*/facts', (route) => {
    const jdVersionId = new URL(route.request().url()).pathname.split('/').at(-2)!;
    const job = jdVersionId === 'historical-jd-version' ? jobs[1]! : jobs.find((item) => jdVersionId.startsWith(`${item.id}-v`)) ?? jobs[0]!;
    return route.fulfill({ json: {
      factSnapshotId: `facts-${jdVersionId}`,
      jdVersionId,
      snapshotVersion: 1,
      sourceType: 'AI_PARSE',
      schemaVersion: '1',
      facts: {
        title: job.title,
        summary: `${job.title}的结构化岗位摘要`,
        professional: {
          coreSkills: ['Java', '服务器配置', '硬件故障排查', '软件故障排查'].map((name, index) => ({ id: `skill-${index}`, name, required: true, aliases: [] })),
          roleExperiences: [],
          minimumYears: 5,
          preferredYears: null,
          responsibilities: ['负责核心业务建设', '推动跨团队协作'],
          industryTags: [],
          projectTags: [],
        },
        controlledConditions: {
          ageMin: null,
          ageMax: null,
          sex: 'ANY',
          educations: ['本科'],
          credentials: [],
          languages: [],
        },
        fulfillmentConditions: {
          locations: ['上海'],
          availability: null,
          salaryMin: null,
          salaryMax: null,
          salaryPeriod: null,
        },
        evidence: [],
      },
      createdAt: '2026-09-08T00:00:01Z',
    } });
  });
  await page.route('**/api/v1/match-runs/historical-run**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/results')) return route.fulfill({ json: empty });
    return route.fulfill({ json: {
      id: 'historical-run',
      taskId: 'historical-task',
      jdVersionId: 'historical-jd-version',
      jdFactSnapshotId: 'historical-facts',
      planVersionId: 'historical-plan',
      weightProfileVersionId: 'historical-weight',
      personalPreferenceVersionId: null,
      selectionSnapshotId: 'historical-selection',
      sortMode: 'REFERENCE_SCORE',
      status: 'SUCCEEDED',
      totalCount: 0,
      succeededCount: 0,
      failedCount: 0,
      errorCode: null,
      version: 1,
      createdAt: '2026-09-01T00:00:00Z',
      startedAt: '2026-09-01T00:00:01Z',
      finishedAt: '2026-09-01T00:00:02Z',
      updatedAt: '2026-09-01T00:00:02Z',
    } });
  });
  await page.route('**/api/v1/tasks/historical-task', (route) => route.fulfill({ json: {
    taskId: 'historical-task', resourceId: 'historical-run', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN',
    status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, retryable: false, version: 1,
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:02Z',
  } }));
  await page.route('**/api/v1/jds**', (route) => {
    const url = new URL(route.request().url());
    const versionMatch = url.pathname.match(/\/jds\/(screening-jd-\d+)\/versions$/);
    if (versionMatch) {
      const jdId = versionMatch[1]!;
      const job = jobs.find((item) => item.id === jdId)!;
      return route.fulfill({ json: { ...empty, items: [{
        id: `${jdId}-v${job.currentVersionNo}`,
        jdId,
        versionNo: job.currentVersionNo,
        title: job.title,
        rawText: rawTexts.get(jdId),
        status: 'PUBLISHED',
        sourceType: 'MANUAL',
        createId: 'hr-a',
        createTime: '2026-09-08T00:00:00Z',
      }] } });
    }
    const itemMatch = url.pathname.match(/\/jds\/(screening-jd-\d+)$/);
    if (itemMatch) return route.fulfill({ json: jobs.find((item) => item.id === itemMatch[1]) });
    if (url.pathname !== '/api/v1/jds') return route.fallback();
    requests.push(url);
    const query = url.searchParams.get('query')?.trim().toLocaleLowerCase('zh-CN');
    const filtered = query ? jobs.filter((item) => item.title.toLocaleLowerCase('zh-CN').includes(query)) : jobs;
    const secondPage = url.searchParams.get('cursor') === 'screening-jobs-page-2';
    return route.fulfill({ json: {
      items: secondPage ? filtered.slice(50) : filtered.slice(0, 50),
      hasMore: !secondPage && filtered.length > 50,
      nextCursor: !secondPage && filtered.length > 50 ? 'screening-jobs-page-2' : null,
    } });
  });
  return requests;
}

async function expectSelectedJob(page: Page, title: string) {
  const trigger = page.getByRole('button', { name: '选择招聘岗位', exact: true });
  await expect(trigger).toBeVisible();
  await expect(trigger).toContainText(title);
}

for (const width of [390, 1440]) test(`岗位慢加载仅暴露有名称的状态 ${width}`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await mockScreeningJobs(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/jds/screening-jd-1/versions**', async route => {
    await pending;
    await route.fallback();
  });
  try {
    await page.goto('/matching');
    const loading = page.getByRole('status', { name: '正在加载智能筛选', exact: true });
    await expect(loading).toBeVisible();
    expect((await new AxeBuilder({ page }).include('.screening-results-loading').analyze()).violations).toEqual([]);
    await expect(loading.getByRole('heading')).toHaveCount(0);
  } finally {
    release();
  }
  await expect(page.getByRole('status', { name: '正在加载智能筛选', exact: true })).toHaveCount(0);
  await expect(page.locator('.screening-job-tags')).toContainText('Java');
});

for (const width of [390, 1440]) test(`手选岗位在后退前进、刷新及跨页返回后保持一致 ${width}`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await mockScreeningJobs(page);
  await page.goto('/matching');
  await expectSelectedJob(page, '默认平台工程师');
  const selectJob = async (title: string) => {
    await page.getByRole('button', { name: '选择招聘岗位', exact: true }).click();
    const picker = page.getByRole('dialog', { name: '选择招聘岗位', exact: true });
    await picker.getByRole('button', { name: new RegExp(title) }).click();
    await expect(picker).toHaveCount(0);
    await expectSelectedJob(page, title);
  };
  await selectJob('数据平台架构师');
  await selectJob('数据治理专家');
  await page.goBack();
  await expectSelectedJob(page, '数据平台架构师');
  await page.getByRole('button', { name: 'JD 详情', exact: true }).click();
  const details = page.getByRole('dialog', { name: /数据平台架构师 .* JD 详情/ });
  await expect(details).toContainText('完整岗位职责与任职要求 · V4');
  await expect(details).toContainText(rawTexts.get('screening-jd-2')!);
  await page.keyboard.press('Escape');
  await expect(details).toHaveCount(0);
  await page.goBack();
  await expectSelectedJob(page, '默认平台工程师');
  await page.goForward();
  await expectSelectedJob(page, '数据平台架构师');
  await page.goForward();
  await expectSelectedJob(page, '数据治理专家');
  await page.reload();
  await expectSelectedJob(page, '数据治理专家');
  const navigation = page.getByRole('navigation', { name: width === 390 ? '移动端主导航' : '主导航' });
  const resumes = navigation.getByRole('button', { name: /^(我的候选人|团队候选人|候选人库)$/ });
  await resumes.click();
  await expect(page).toHaveURL(/\/talents$/);
  await expect(resumes).toHaveAttribute('aria-current', 'page');
  await navigation.getByRole('button', { name: 'AI 评估', exact: true }).click();
  await expect(page).toHaveURL(/\/matching$/);
  await expectSelectedJob(page, '数据治理专家');
  await expect(page.locator('.screening-job-tags')).toContainText('Java');
  await expect(page.locator('.app-main .ant-skeleton')).toHaveCount(0);
  expect((await new AxeBuilder({ page }).include('.app-main').analyze()).violations).toEqual([]);
});

test('岗位头部默认首个 JD，历史版本 URL 精确恢复，handoff.jdId 优先', async ({ page }) => {
  await mockScreeningJobs(page);
  await page.goto('/matching');
  await expectSelectedJob(page, '默认平台工程师');

  await page.goto('/matching?scoreJob=historical-jd-version');
  const trigger = page.getByRole('button', { name: '选择招聘岗位', exact: true });
  await expectSelectedJob(page, '数据平台架构师');
  await expect(page.getByRole('button', { name: 'JD 详情' })).toBeEnabled();
  await expect(page).toHaveURL(/scoreJob=historical-jd-version/);
  await page.getByRole('button', { name: 'JD 详情' }).click();
  let details = page.getByRole('dialog', { name: /数据平台架构师 .* JD 详情/ });
  await expect(details).toContainText('完整岗位职责与任职要求 · V2');
  await expect(details).toContainText('数据平台架构师历史 V2 岗位原文。');
  await page.keyboard.press('Escape');
  await expect(details).toHaveCount(0);
  await page.reload();
  await expectSelectedJob(page, '数据平台架构师');
  await page.getByRole('button', { name: 'JD 详情' }).click();
  details = page.getByRole('dialog', { name: /数据平台架构师 .* JD 详情/ });
  await expect(details).toContainText('完整岗位职责与任职要求 · V2');
  await expect(details).toContainText('数据平台架构师历史 V2 岗位原文。');
  await page.keyboard.press('Escape');
  await expect(details).toHaveCount(0);
  await page.goto('/matching?run=historical-run');
  await expect(trigger).toContainText('选择招聘岗位');
  await expect(trigger).not.toContainText('默认平台工程师');
  await expect(page).toHaveURL(/run=historical-run/);

  await page.addInitScript(() => {
    window.history.replaceState({ usr: { candidateIds: [], jdId: 'screening-jd-2' }, key: 'e2e-handoff', idx: 0 }, '', window.location.href);
  });
  await page.reload();
  await expectSelectedJob(page, '数据平台架构师');
});

test('岗位弹窗支持两列可视区、服务端搜索、游标加载、切换和焦点返回', async ({ page }, testInfo) => {
  const requests = await mockScreeningJobs(page);
  await page.route('**/api/v1/directory/users/resolve?**', route => route.fulfill({ json: [{ id: 'hr-b', displayName: '张国玉', homeDepartmentId: 'dept-a', status: 'ACTIVE' }] }));
  await page.goto('/matching');
  const trigger = page.getByRole('button', { name: '选择招聘岗位', exact: true });
  await expectSelectedJob(page, '默认平台工程师');
  await trigger.click();

  const dialog = page.getByRole('dialog', { name: '选择招聘岗位', exact: true });
  const options = dialog.locator('.screening-jd-options');
  const grid = options.locator('.screening-jd-grid');
  const cards = grid.locator(':scope > button');
  await expect(dialog).toBeVisible();
  await expect(cards).toHaveCount(50);
  await expect(cards.nth(0)).toContainText('V1 · 所属人：陈佳');
  await expect(cards.nth(1)).toContainText('V4 · 所属人：张国玉');
  const geometry = await grid.evaluate((element) => {
    const optionsElement = element.parentElement!;
    const style = getComputedStyle(element);
    const children = Array.from(element.querySelectorAll(':scope > button')) as HTMLElement[];
    const bounds = optionsElement.getBoundingClientRect();
    return {
      height: bounds.height,
      columns: style.gridTemplateColumns.split(' ').filter(Boolean).length,
      firstSixInside: children.slice(0, 6).every((child) => child.getBoundingClientRect().top < bounds.bottom),
      seventhBelow: children[6]!.getBoundingClientRect().bottom > bounds.bottom,
      scrollable: optionsElement.scrollHeight > optionsElement.clientHeight,
    };
  });
  expect(geometry.columns).toBe(2);
  expect(geometry.height).toBeGreaterThanOrEqual(260);
  expect(geometry.height).toBeLessThanOrEqual(268);
  expect(geometry.firstSixInside).toBe(true);
  expect(geometry.seventhBelow).toBe(true);
  expect(geometry.scrollable).toBe(true);
  await expect.poll(() => requests[0]?.searchParams.get('limit')).toBe('20');
  await dialog.evaluate(async element => { await Promise.all(element.closest('.ant-modal-root')!.getAnimations({ subtree: true }).map(animation => animation.finished)); });
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
  expect((await new AxeBuilder({ page }).include('.ant-modal').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath('screening-jd-picker.png'), fullPage: true });

  await dialog.getByRole('button', { name: '加载更多岗位', exact: true }).click();
  await expect(cards).toHaveCount(56);
  await expect.poll(() => requests.at(-1)?.searchParams.get('cursor')).toBe('screening-jobs-page-2');

  const search = dialog.getByRole('textbox', { name: '搜索招聘岗位', exact: true });
  await search.fill('招聘岗位');
  await expect.poll(() => requests.at(-1)?.searchParams.get('query')).toBe('招聘岗位');
  await expect(cards).toHaveCount(50);
  await options.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
    element.dispatchEvent(new Event('scroll', { bubbles: true }));
  });
  await expect(cards).toHaveCount(53);
  await expect.poll(() => requests.at(-1)?.searchParams.get('cursor')).toBe('screening-jobs-page-2');

  await search.fill('数据');
  await expect.poll(() => requests.at(-1)?.searchParams.get('query')).toBe('数据');
  expect(requests.at(-1)?.searchParams.has('cursor')).toBe(false);
  await expect(cards).toHaveCount(2);
  await dialog.getByRole('button', { name: /数据平台架构师/ }).click();
  await expect(dialog).toHaveCount(0);
  await expectSelectedJob(page, '数据平台架构师');

  await trigger.click();
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

for (const width of [390, 1280, 1440, 1920]) {
  test(`JD 详情展示真实版本原文且页面无整页溢出 ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await mockScreeningJobs(page);
    await page.goto('/matching');
    await expectSelectedJob(page, '默认平台工程师');
    await expect(page.locator('.screening-job-tags')).toContainText('Java');
    await expect(page.locator('.app-main .ant-skeleton')).toHaveCount(0);
    expect((await new AxeBuilder({ page }).include('.app-main').analyze()).violations).toEqual([]);
    await page.getByRole('button', { name: 'JD 详情', exact: true }).click();
    const detail = page.getByRole('dialog', { name: /JD 详情/ });
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('V1');
    await expect(detail).toContainText('默认平台工程师的结构化岗位摘要');
    await expect(detail).toContainText(rawTexts.get('screening-jd-1')!);
    await detail.evaluate(async element => { await Promise.all(element.closest('.ant-modal-root')!.getAnimations({ subtree: true }).map(animation => animation.finished)); });
    await expect(detail).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    expect((await new AxeBuilder({ page }).include('.ant-modal').analyze()).violations).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`screening-jd-details-${width}.png`), fullPage: true });
  });
}

for (const width of [390, 1440, 1920]) test(`岗位标签紧凑排列并为候选人列表留出空间 ${width}`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockScreeningJobs(page);
  await page.goto('/matching');
  const header = page.locator('.screening-job-header');
  const tags = header.locator('.screening-job-tags');
  await expect(tags.locator('.ant-tag')).toHaveCount(6);
  await expect(header.locator('.screening-job-summary')).toHaveCount(0);
  await expect(header).not.toContainText('默认平台工程师的结构化岗位摘要');
  await expect(header).not.toContainText('受控业务条件');
  await expect(tags).toContainText('本科');
  const bounds = await header.evaluate(element => {
    const rect = (selector: string) => element.querySelector(selector)!.getBoundingClientRect().toJSON();
    return {
      header: element.getBoundingClientRect().toJSON(),
      heading: rect('.screening-job-heading'),
      tags: rect('.screening-job-tags'),
      firstTag: rect('.screening-job-tags .ant-tag'),
      weights: rect('.screening-job-weights'),
      controls: rect('.screening-model-controls'),
    };
  });
  expect(Math.abs(bounds.firstTag.left - bounds.tags.left)).toBeLessThanOrEqual(1);
  expect(bounds.tags.right).toBeLessThanOrEqual(bounds.header.right + 1);
  expect(bounds.tags.top).toBeGreaterThanOrEqual(bounds.weights.bottom);
  expect(bounds.tags.left).toBe(bounds.heading.left);
  if (width === 1920) {
    expect(bounds.controls.left).toBeGreaterThanOrEqual(bounds.tags.right);
    expect(Math.abs(bounds.tags.bottom - bounds.controls.bottom)).toBeLessThanOrEqual(1);
    expect(bounds.header.height).toBeLessThan(150);
  } else {
    expect(bounds.controls.top).toBeGreaterThanOrEqual(bounds.tags.bottom);
  }
  const detailsButton = header.getByRole('button', { name: 'JD 详情', exact: true });
  await header.getByRole('button', { name: '选择招聘岗位', exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(detailsButton).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: testInfo.outputPath(`screening-compact-header-${width}.png`), fullPage: true, animations: 'disabled' });
});

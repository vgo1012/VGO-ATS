import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const pageResult=(candidateId:string,resultId:string,runId:string,score=88)=>({
  runId,scoredAt:'2026-09-07T01:00:00Z',result:{resultId,baseEvaluationId:`base-${candidateId}`,candidateId,resumeVersionId:`resume-${candidateId}`,professionalScore:score-8,bonusScore:8,referenceScore:score,mustTier:'PASS',stableRank:1,
    details:{jdFactSnapshotId:'jd-fact',resumeFactSnapshotId:`resume-fact-${candidateId}`,candidateId,resumeVersionId:`resume-${candidateId}`,professionalScore:score-8,bonusScore:8,referenceScore:score,mustTier:'PASS',selectedBonusCap:10,
      dimensions:[{code:'CORE_SKILLS',enabled:true,configuredWeight:29,status:'KNOWN',score:90,explanation:'核心技能匹配',highlights:['操作系统与网络协议 ← Linux 系统安装操作；售后工程师现场交付与故障排查 ← 客户运维支持经验']}],criteria:[]}}
});
const basisRun=(id:string,totalCount=54)=>({id,taskId:`task-${id}`,jdVersionId:'jd-v2',jdFactSnapshotId:'jd-fact',planVersionId:`plan-${id}`,weightProfileVersionId:`weight-${id}`,personalPreferenceVersionId:null,selectionSnapshotId:`selection-${id}`,sortMode:'REFERENCE_SCORE',status:'SUCCEEDED',totalCount,succeededCount:totalCount,failedCount:0,errorCode:null,version:1,createdAt:'2026-09-07T01:00:00Z',startedAt:'2026-09-07T01:00:00Z',finishedAt:'2026-09-07T01:00:00Z',updatedAt:'2026-09-07T01:00:00Z'});

async function fixture(page: Page) {
  await reviewWorkspace(page);
  const state = { status: 200, requests: 0, wait: undefined as Promise<void> | undefined };
  const empty = { items: [], hasMore: false, nextCursor: null };
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: null });
    if (path.startsWith('/me/jd-scoring-configs/') && path.endsWith('/preferences')) return route.fulfill({ json: null });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path === '/match-jobs') return route.fulfill({ json: { ...empty, items: [{ jdVersionId: 'jd-v2', latestRunId: 'basis-a',
      lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, evaluationCount: 1, succeededCount: 1, failedCount: 0,
      candidateCount: 1, scoredCandidateCount: 1, activeRunCount: 0 }] } });
    if (path === '/match-jobs/jd-v2/bases') {
      state.requests++;
      await state.wait;
      if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
        status: state.status, title: '判断依据读取失败', detail: state.status === 403 ? '判断依据权限已撤销' : '判断依据暂不可用',
        code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
      } });
      return route.fulfill({ json: { ...empty, items: [{ basisRunId: 'basis-a', jdVersionId: 'jd-v2', basisKey: 'basis-a',
        planVersionId: 'company-v1', weightProfileVersionId: 'weight-a', sortMode: 'REFERENCE_SCORE',
        lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, candidateCount: 1, scoredCandidateCount: 1,
        skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20,
        selectedCriterionIds: [], personalScreeningSettings: null }] } });
    }
    if (path === '/match-runs/basis-a') return route.fulfill({ json: basisRun('basis-a', 1) });
    if (path === '/match-jobs/jd-v2/results') return route.fulfill({ json: { ...empty, items: [pageResult('person-a', 'result-a', 'basis-a')] } });
    if (path === '/candidate-batch') return route.fulfill({ json: [{ id: 'person-a', fullName: '公司依据结果成员', ownerUserId: 'hr-a', status: 'ACTIVE' }] });
    if (path === '/candidates/person-a') return route.fulfill({ json: { id: 'person-a', fullName: '公司依据结果成员', ownerUserId: 'hr-a', status: 'ACTIVE' } });
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: [{ candidateId: 'person-a', candidateName: '公司依据结果成员',
      resumeVersionId: 'resume-person-a', libraryEligible: false, screenedOut: false, canScreenOut: true, stages: [], jobContexts: [] }] });
    return route.fallback();
  });
  return state;
}

test('公司依据首次失败后慢重试保留可见忙碌按钮', async ({ page }, testInfo) => {
  const width = testInfo.project.name === 'mobile' ? 390 : 1440;
  await page.setViewportSize({ width, height: 1000 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  const state = await fixture(page);
  state.status = 503;
  await page.goto('/matching');
  const retry = page.getByRole('button', { name: '重试判断依据', exact: true });
  await expect(page.getByText('判断依据读取失败', { exact: true })).toBeVisible();
  await expect(retry).toBeEnabled();
  await expect(page.locator('.screening-basis-results')).toHaveCount(0);

  state.status = 200;
  let release!: () => void;
  state.wait = new Promise<void>(resolve => { release = resolve; });
  const before = state.requests;
  await retry.focus();
  await page.keyboard.press('Enter');
  try {
    await expect.poll(() => state.requests).toBe(before + 1);
    await expect(retry).toBeVisible();
    await expect(retry).toBeDisabled();
    await expect(retry).toHaveAttribute('aria-busy', 'true');
    await expect(page.getByText('公司依据结果成员', { exact: true })).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await retry.scrollIntoViewIfNeeded();
    await page.screenshot({ path: testInfo.outputPath(`scoring-basis-first-retry-pending-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('.app-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  } finally {
    release();
  }
  await expect(retry).toHaveCount(0);
  await expect(page.locator('.screening-basis-results')).toContainText('公司依据结果成员');
  await expect(page.getByLabel('查看候选人的评分详情')).toBeVisible();
  expect(errors).toEqual([]);
});

for (const status of [403, 503]) {
  test(`自动公司依据缓存刷新 ${status} 后裁剪结果并可重试`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching');
    await expect(page.locator('.screening-basis-results')).toContainText('公司依据结果成员');
    expect(new URL(page.url()).searchParams.has('basis')).toBe(false);
    state.status = status;
    await page.context().setOffline(true);
    await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.context().setOffline(false);
    // 隔离容器没有外部网络接口；明确派发恢复事件，验证当前页的重连查询。
    await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
    const title = status === 403 ? '暂无判断依据的查看权限' : '判断依据读取失败';
    await expect(page.getByText(title, { exact: true })).toBeVisible();
    if (status === 403) {
      await expect(page.locator('.screening-basis-results')).toHaveCount(0);
      await expect(page.getByText('公司依据结果成员', { exact: true })).toHaveCount(0);
      await expect(page.getByLabel('查看候选人的评分详情')).toHaveCount(0);
      await expect(page.getByRole('button', { name: '全选本页筛选结果', exact: true })).toHaveCount(0);
    } else {
      await expect(page.locator('.screening-basis-results')).toContainText('公司依据结果成员');
    }
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`scoring-basis-selection-${status}-${width}.png`), animations: 'disabled', fullPage: true });
    }
    expect((await new AxeBuilder({ page }).include('.app-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    state.status = 200;
    let release!: () => void;
    state.wait = new Promise<void>(resolve => { release = resolve; });
    const before = state.requests;
    const retry = page.getByRole('button', { name: '重试判断依据', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.requests).toBe(before + 1);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      release();
    }
    await expect(retry).toHaveCount(0);
    await expect(page.locator('.screening-basis-results')).toContainText('公司依据结果成员');
    await expect(page.getByLabel('查看候选人的评分详情')).toBeVisible();
    expect(errors).toEqual([]);
  });
}

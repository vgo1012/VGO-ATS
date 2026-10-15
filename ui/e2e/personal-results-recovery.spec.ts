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
  const state = { status: 200, source: 'results', cursors: [] as Array<string | null>, wait: undefined as Promise<void> | undefined };
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/tasks/task-personal-run') return route.fulfill({ json: {
      taskId: 'task-personal-run', resourceId: 'personal-run', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN',
      status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, retryable: false, version: 1,
      createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:01Z',
    } });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: null });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path === '/candidate-batch') return route.fulfill({ json: url.searchParams.getAll('candidateIds').map(id => ({
      id, fullName: id === 'person-a' ? '第一页结果成员' : '第二页结果成员', ownerUserId: 'hr-a', status: 'ACTIVE',
    })) });
    if (path === '/candidates/person-a' || path === '/candidates/person-b') {
      const id = path.split('/').at(-1)!;
      return route.fulfill({ json: { id, fullName: id === 'person-a' ? '第一页结果成员' : '第二页结果成员', ownerUserId: 'hr-a', status: 'ACTIVE' } });
    }
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: ['person-a', 'person-b'].map(candidateId => ({
      candidateId, candidateName: candidateId === 'person-a' ? '第一页结果成员' : '第二页结果成员', resumeVersionId: `resume-${candidateId}`,
      libraryEligible: false, screenedOut: false, canScreenOut: true, stages: [], jobContexts: [],
    })) });
    const isRun = path === '/match-runs/personal-run';
    const isResults = path === '/match-runs/personal-run/results';
    if (!isRun && !isResults) return route.fallback();
    if (isResults) state.cursors.push(url.searchParams.get('cursor'));
    if (isResults) await state.wait;
    if (state.status !== 200 && (state.source === 'run' ? isRun : isResults)) return route.fulfill({
      status: state.status, contentType: 'application/problem+json', json: {
        status: state.status, title: '匹配进度或结果读取失败', detail: state.status === 403 ? '岗位结果权限已撤销' : '岗位结果暂不可用',
        code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
      },
    });
    if (isRun) return route.fulfill({ json: basisRun('personal-run', 2) });
    const second = url.searchParams.get('cursor') === 'results-next';
    return route.fulfill({ json: { items: [pageResult(second ? 'person-b' : 'person-a', second ? 'result-b' : 'result-a', 'personal-run').result],
      hasMore: !second, nextCursor: second ? null : 'results-next' } });
  });
  return state;
}

for (const scenario of [{ status: 403, source: 'run' }, { status: 403, source: 'results' }, { status: 503, source: 'results' }]) {
  test(`个人评分 ${scenario.source} 刷新 ${scenario.status} 的权限与第二页恢复`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching?run=personal-run');
    const results = page.locator('.matching-personal-results');
    await expect(results).toContainText('第一页结果成员');
    await results.getByRole('button', { name: '下一页结果', exact: true }).click();
    await expect(results).toContainText('第二页结果成员');
    await expect(results.getByLabel('匹配结果分页', { exact: true }).getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
    await expect(page.getByRole('button', { name: '返回岗位全部结果', exact: true })).toHaveCount(0);
    state.status = scenario.status;
    state.source = scenario.source;
    // 保持当前页挂载，网络恢复后重读过期查询；应用已禁用标签聚焦刷新。
    await page.context().setOffline(true);
    await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.context().setOffline(false);
    // 隔离容器没有外部网络接口；显式派发重连事件以读取当前页的权限和结果。
    await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
    const title = scenario.status === 403 ? '暂无匹配进度或结果的查看权限' : '匹配进度或结果读取失败';
    await expect(results.getByText(title, { exact: true })).toBeVisible();
    if (scenario.status === 403) {
      await expect(results).not.toContainText('第二页结果成员');
      await expect(results.getByLabel('查看候选人的评分详情')).toHaveCount(0);
      await expect(results.getByRole('button', { name: '全选本页筛选结果', exact: true })).toHaveCount(0);
      await expect(results.getByLabel('匹配结果分页', { exact: true })).toHaveCount(0);
    } else {
      await expect(results).toContainText('第二页结果成员');
      await expect(results.getByLabel('匹配结果分页', { exact: true }).getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
    }
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      await page.screenshot({ path: testInfo.outputPath(`personal-results-${scenario.source}-${scenario.status}-${width}.png`), animations: 'disabled', fullPage: true });
    }
    expect((await new AxeBuilder({ page }).include('.app-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
    state.status = 200;
    let release!: () => void;
    state.wait = new Promise<void>(resolve => { release = resolve; });
    const count = state.cursors.length;
    const retry = results.getByRole('button', { name: '重试匹配结果', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.cursors.length).toBeGreaterThan(count);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      release();
    }
    await expect(retry).toHaveCount(0);
    await expect(results).toContainText('第二页结果成员');
    await expect(results).not.toContainText('第一页结果成员');
    await expect(results.getByLabel('匹配结果分页', { exact: true }).getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
    expect(state.cursors.slice(count)).toEqual(['results-next']);
    expect(errors).toEqual([]);
  });
}

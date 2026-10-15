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
  const state = { status: 200, progress: 73, requests: [] as string[], wait: undefined as Promise<void> | undefined };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: null });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path === '/match-runs/personal-run') return route.fulfill({ json: {
      ...basisRun('personal-run', 2), status: 'PARTIAL', succeededCount: 1, failedCount: 1,
    } });
    if (path === '/match-runs/personal-run/results') return route.fulfill({ json: {
      items: [pageResult('person-a', 'result-a', 'personal-run').result], hasMore: false, nextCursor: null,
    } });
    const person = { id: 'person-a', fullName: '独立授权结果成员', ownerUserId: 'hr-a', status: 'ACTIVE' };
    if (path === '/candidate-batch') return route.fulfill({ json: [person] });
    if (path === '/candidates/person-a') return route.fulfill({ json: person });
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: [{ candidateId: person.id, candidateName: person.fullName,
      resumeVersionId: 'resume-person-a', libraryEligible: false, screenedOut: false, canScreenOut: true, stages: [], jobContexts: [] }] });
    if (path !== '/tasks/task-personal-run') return route.fallback();
    state.requests.push(path);
    await state.wait;
    if (state.status !== 200) return route.fulfill({ status: state.status, contentType: 'application/problem+json', json: {
      status: state.status, title: '任务进度暂不可用', detail: state.status === 403 ? '任务读取权限已撤销' : '任务服务暂不可用',
      code: state.status === 403 ? 'FORBIDDEN' : 'SERVICE_UNAVAILABLE', recoverable: state.status === 503,
    } });
    return route.fulfill({ json: {
      taskId: 'task-personal-run', resourceId: 'personal-run', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN',
      status: 'FAILED', attemptCount: 1, maxAttempts: 3, progressPercent: state.progress, retryable: true, version: 1,
      createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:01Z',
    } });
  });
  return state;
}

for (const status of [403, 503]) {
  test(`独立任务进度刷新 ${status} 后保留运行结果且可重试`, async ({ page }, testInfo) => {
    const width = testInfo.project.name === 'mobile' ? 390 : 1440;
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await fixture(page);
    await page.goto('/matching?run=personal-run');
    const results = page.locator('.matching-personal-results');
    const progress = results.getByRole('progressbar', { name: '候选人匹配进度' });
    const failedRetry = results.getByRole('button', { name: '仅重试失败项', exact: true });
    await expect(results).toContainText('独立授权结果成员');
    await expect(progress).toHaveAttribute('aria-valuenow', '73');
    await expect(failedRetry).toBeEnabled();
    state.status = status;
    await page.context().setOffline(true);
    await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
    await page.clock.setFixedTime(new Date('2026-09-07T01:32:00Z'));
    await page.context().setOffline(false);
    // 隔离容器没有外部网络接口；明确派发恢复事件，验证当前页的重连查询。
    await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
    await expect(results.getByText(status === 403 ? '暂无任务进度的查看权限' : '任务进度暂不可用', { exact: true })).toBeVisible();
    if (status === 403) {
      await expect(progress).toHaveCount(0);
      await expect(failedRetry).toHaveCount(0);
    } else {
      await expect(progress).toHaveAttribute('aria-valuenow', '73');
      await expect(failedRetry).toBeEnabled();
    }
    await expect(results).toContainText('成功 1 / 失败 1 / 共 2');
    await expect(results).toContainText('独立授权结果成员');
    await expect(page.getByRole('button', { name: '返回岗位全部结果', exact: true })).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`matching-task-${status}-${width}.png`), animations: 'disabled', fullPage: true });
    expect((await new AxeBuilder({ page }).include('.app-main').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);

    state.status = 200;
    state.progress = 81;
    let release!: () => void;
    state.wait = new Promise<void>(resolve => { release = resolve; });
    const before = state.requests.length;
    const retry = results.getByRole('button', { name: '重试任务进度', exact: true });
    await retry.focus();
    await page.keyboard.press('Enter');
    try {
      await expect.poll(() => state.requests.length).toBe(before + 1);
      await expect(retry).toBeDisabled();
      await expect(retry).toHaveAttribute('aria-busy', 'true');
    } finally {
      release();
    }
    await expect(retry).toHaveCount(0);
    await expect(progress).toHaveAttribute('aria-valuenow', '81');
    await expect(failedRetry).toBeEnabled();
    await expect(results).toContainText('独立授权结果成员');
    expect(state.requests.slice(before)).toEqual(['/tasks/task-personal-run']);
    expect(errors).toEqual([]);
  });
}

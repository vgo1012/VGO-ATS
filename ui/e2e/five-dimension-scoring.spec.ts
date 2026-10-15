import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const dimensions = [
  ['CORE_SKILLS', 29], ['ROLE_EXPERIENCE', 18], ['RELATED_YEARS', 12],
  ['RESPONSIBILITY_MATCH', 23], ['INDUSTRY_AND_PROJECTS', 18],
] as const;
const names: Record<string, string> = { incomplete: '合成信息不足候选人', complete: '合成完整评分候选人' };
function result(candidateId: string) {
  const incomplete = candidateId === 'incomplete';
  const score = incomplete ? 79.2 : 90;
  return {
    runId: 'five-dimensions', scoredAt: '2026-09-20T01:00:00Z',
    result: {
      resultId: `result-${candidateId}`, baseEvaluationId: `base-${candidateId}`, candidateId,
      resumeVersionId: `resume-${candidateId}`, professionalScore: score, bonusScore: 0,
      referenceScore: score, mustTier: 'PASS', stableRank: incomplete ? 2 : 1,
      details: {
        jdFactSnapshotId: 'jd-fact', resumeFactSnapshotId: `fact-${candidateId}`, candidateId,
        resumeVersionId: `resume-${candidateId}`, professionalScore: score, bonusScore: 0,
        referenceScore: score, mustTier: 'PASS', selectedBonusCap: 0,
        algorithmVersion: incomplete ? 'deterministic-v5-five-dimensions' : 'ai-professional-v2',
        ...(incomplete ? {} : { aiAssessment: { assessmentId: 'assessment-complete', provider: 'synthetic-provider', model: 'synthetic-model', promptVersion: 'professional-v1', schemaVersion: 'professional-schema-v1', evaluatedOn: '2026-09-20T01:00:00Z', defaultProfessionalScore: score, summary: '合成 AI 评分摘要' } }),
        criteria: [],
        dimensions: dimensions.map(([code, configuredWeight]) => ({
          code, configuredWeight, enabled: true,
          status: incomplete && code === 'RELATED_YEARS' ? 'UNKNOWN' : 'KNOWN',
          score: incomplete && code === 'RELATED_YEARS' ? 0 : 90,
          explanation: incomplete && code === 'RELATED_YEARS'
            ? '信息不足：相关履历日期待补充；默认 3 年仅作评价参照，不是 JD 门槛'
            : 'AI 结合工作和项目经历给出的评分原因',
          highlights: incomplete && code === 'RELATED_YEARS' ? [] : ['设备巡检与故障排查经验匹配'],
        })),
      },
    },
  };
}

async function fixture(page: Page) {
  await reviewWorkspace(page);
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/screening-candidates') return route.fulfill({ json: [] });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: null });
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: {
      jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true,
      workflowTemplateActive: false, readyForApplication: false,
    } });
    if (path === '/candidate-batch') return route.fulfill({ json: url.searchParams.getAll('candidateIds').map(id => ({
      id, fullName: names[id], ownerUserId: 'hr-a', status: 'ACTIVE',
    })) });
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: Object.keys(names).map(candidateId => ({
      candidateId, candidateName: names[candidateId], resumeVersionId: `resume-${candidateId}`,
      libraryEligible: false, screenedOut: false, canScreenOut: true, stages: [], jobContexts: [],
    })) });
    if (path === '/match-runs/five-dimensions') return route.fulfill({ json: {
      id: 'five-dimensions', taskId: 'task-five', jdVersionId: 'jd-v2', jdFactSnapshotId: 'jd-fact',
      planVersionId: 'plan-five', weightProfileVersionId: 'weight-five', personalPreferenceVersionId: null,
      selectionSnapshotId: 'selection-five', sortMode: 'REFERENCE_SCORE', status: 'SUCCEEDED',
      totalCount: 2, succeededCount: 2, failedCount: 0, errorCode: null, version: 1,
      createdAt: '2026-09-20T01:00:00Z', startedAt: '2026-09-20T01:00:00Z',
      finishedAt: '2026-09-20T01:00:00Z', updatedAt: '2026-09-20T01:00:00Z',
    } });
    if (path === '/match-jobs/jd-v2/results') return route.fulfill({ json: {
      items: [result('complete'), result('incomplete')], hasMore: false, nextCursor: null,
    } });
    return route.fallback();
  });
}

test('五维信息不足与完整评分分别展示 AI 总结和原因', async ({ page }, testInfo) => {
  await page.setViewportSize(testInfo.project.name === 'mobile' ? { width: 390, height: 844 } : { width: 1920, height: 1080 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await fixture(page);
  await page.goto('/matching?scoreJob=jd-v2&basis=five-dimensions');
  const results = page.locator('.screening-basis-results');
  const incomplete = results.getByRole('row').filter({ hasText: names.incomplete });
  const complete = results.getByRole('row').filter({ hasText: names.complete });
  await expect(incomplete).toContainText('79.20');
  await expect(incomplete).toContainText('信息缺失');
  await expect(incomplete).toContainText('历史算法评分');
  await expect(incomplete).not.toContainText('不推荐');
  await expect(incomplete).not.toContainText('不参与评分');
  await expect(complete).toContainText('90.00');
  await expect(complete).toContainText('强推荐');
  await expect(complete).toContainText('AI 评分');
  await expect(complete.locator('.matching-score-blocks')).toHaveCount(5);
  await expect(incomplete.locator('.matching-score-blocks')).toHaveCount(4);
  await page.screenshot({ path: testInfo.outputPath('five-dimension-results.png'), animations: 'disabled', fullPage: true });
  await complete.getByRole('button', { name: '查看候选人的评分详情' }).click();
  const aiDialog = page.getByRole('dialog', { name: /合成完整评分候选人.*评分详情/ });
  await expect(aiDialog).toContainText('AI 总结');
  await expect(aiDialog).toContainText('合成 AI 评分摘要');
  await expect(aiDialog).toContainText('AI 结合工作和项目经历给出的评分原因');
  await page.keyboard.press('Escape');
  await expect(aiDialog).toBeHidden();
  await incomplete.getByRole('button', { name: '查看候选人的评分详情' }).click();
  const dialog = page.getByRole('dialog', { name: /合成信息不足候选人.*评分详情/ });
  await expect(dialog).toContainText('历史评分未保存 AI 总结，请重新评分');
  await expect(dialog).toContainText('信息不足');
  await expect(dialog).toContainText('默认 3 年仅作评价参照，不是 JD 门槛');
  await page.screenshot({ path: testInfo.outputPath('five-dimension-details.png'), animations: 'disabled', fullPage: true });
});

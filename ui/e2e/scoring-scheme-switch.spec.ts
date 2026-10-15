import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const personal = {
  profile: { id: 'personal-profile', name: '技术能力优先', ownerUserId: 'hr-a', version: '1' },
  currentVersion: { id: 'personal-v1', versionNo: 1, skillsWeight: 37, roleWeight: 18, yearsWeight: 21, responsibilityWeight: 14, industryProjectWeight: 10 },
};
const candidate = {
  candidateId: 'person-a', candidateName: '林雨桐', resumeVersionId: 'resume-a', jdVersionId: 'jd-v2',
  resumeVersionNo: 2, originalFileName: '林雨桐_第2版.pdf', defaultMatchRunId: 'company-run',
  admissionStatus: 'ADMITTED', admissionId: 'admission-a', admissionVersion: '1', admittedAt: '2026-09-07T01:00:00Z',
  professionalScore: 62.54, referenceScore: 62.54, defaultResult: { professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS' },
};
const run = { id: 'company-run', taskId: 'company-task', jdVersionId: 'jd-v2', jdFactSnapshotId: 'jd-facts', planVersionId: 'company-v1', weightProfileVersionId: 'weight-v1', personalPreferenceVersionId: null, selectionSnapshotId: 'selection-a', sortMode: 'REFERENCE_SCORE', status: 'SUCCEEDED', totalCount: 1, succeededCount: 1, failedCount: 0, errorCode: null, version: 1, createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:01Z', startedAt: '2026-09-07T01:00:00Z', finishedAt: '2026-09-07T01:00:01Z' };
const result = { resultId: 'company-result', baseEvaluationId: 'base-a', candidateId: 'person-a', resumeVersionId: 'resume-a', professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS', stableRank: 1, details: { jdFactSnapshotId: 'jd-facts', resumeFactSnapshotId: 'resume-facts', candidateId: 'person-a', resumeVersionId: 'resume-a', professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS', selectedBonusCap: 0, criteria: [], dimensions: [['CORE_SKILLS', 90, 29], ['ROLE_EXPERIENCE', 80, 18], ['RELATED_YEARS', 100, 12], ['RESPONSIBILITY_MATCH', 28, 23], ['INDUSTRY_AND_PROJECTS', 20, 18]].map(([code, score, configuredWeight]) => ({ code, score, configuredWeight, enabled: true, status: 'KNOWN', explanation: '固定事实评分', highlights: ['简历事实证据'] })) } };

async function schemeWorkspace(page: Page) {
  await reviewWorkspace(page);
  const posts: Record<string, unknown>[] = [];
  const updates: Record<string, unknown>[] = [];
  let saved = false;
  let currentWeights = personal.currentVersion;
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const list = (items: unknown[]) => route.fulfill({ json: { ...empty, items } });
    if (path === '/weight-profiles' || path === '/weight-profiles/personal-profile') {
      if (request.method() === 'PATCH') {
        const body = request.postDataJSON() as Record<string, unknown>;
        updates.push(body);
        const weights = body.weights as Record<string, number>;
        currentWeights = { ...personal.currentVersion, skillsWeight: weights.skills!,
          roleWeight: weights.roleExperience!, yearsWeight: weights.relatedYears!,
          responsibilityWeight: weights.responsibilityMatch!, industryProjectWeight: weights.industryAndProjects! };
        saved = true;
        return route.fulfill({ json: { profile: { ...personal.profile, name: '技术能力优先（已更新）', version: '2' }, currentVersion: { ...currentWeights, id: 'personal-v2', versionNo: 2 } } });
      }
      return list([{ profile: { ...personal.profile, ...(saved ? { name: '技术能力优先（已更新）', version: '2' } : {}) }, currentVersion: { ...currentWeights, ...(saved ? { id: 'personal-v2', versionNo: 2 } : {}) } }]);
    }
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: { jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true, workflowTemplateActive: false, readyForApplication: false } });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: { facts: { title: '前端工程师', summary: '负责业务平台研发' } } });
    if (path === '/screening-candidates') return route.fulfill({ json: [candidate] });
    if (path === '/jd-versions/jd-v2/screening-candidates') return list([candidate]);
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: [] });
    if (path === '/match-jobs') return list([{ jdVersionId: 'jd-v2', latestRunId: 'company-run', lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, candidateCount: 1, scoredCandidateCount: 1, activeRunCount: 0 }]);
    if (path.endsWith('/bases')) return list([{ basisRunId: 'company-run', basisKey: 'company-basis', jdVersionId: 'jd-v2', planVersionId: 'company-v1', weightProfileVersionId: 'weight-v1', sortMode: 'REFERENCE_SCORE', lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, candidateCount: 1, scoredCandidateCount: 1, skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20, selectedCriterionIds: [], personalScreeningSettings: null }]);
    if (path === '/match-jobs/jd-v2/results') return list([{ runId: 'company-run', scoredAt: '2026-09-07T01:00:00Z', result }]);
    if (path === '/match-runs') {
      if (request.method() !== 'POST') return list([run]);
      posts.push(request.postDataJSON() as Record<string, unknown>);
      return route.fulfill({ json: { ...run, id: 'personal-run', taskId: 'personal-task', weightProfileVersionId: saved ? 'personal-v2' : 'personal-v1' } });
    }
    if (path === '/match-runs/personal-run') return route.fulfill({ json: { ...run, id: 'personal-run', taskId: 'personal-task', weightProfileVersionId: saved ? 'personal-v2' : 'personal-v1' } });
    if (path === '/match-runs/personal-run/results') return list([result]);
    if (path === '/tasks/personal-task') return route.fulfill({ json: { taskId: 'personal-task', resourceId: 'personal-run', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN', status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, retryable: false, version: 1, createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:01Z' } });
    return route.fallback();
  });
  return { posts, updates };
}

test('维护个人方案后直接用于整岗重新评分', async ({ page }) => {
  const api = await schemeWorkspace(page);
  await page.goto('/matching');
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  await page.getByRole('radiogroup', { name: '评分方案', exact: true }).getByText('个人方案', { exact: true }).click();
  await page.getByRole('button', { name: '维护个人方案', exact: true }).click();
  const maintenance = page.getByRole('dialog', { name: '维护个人方案', exact: true });
  await maintenance.getByRole('button', { name: /^编\s*辑$|^编辑技术能力优先$/ }).click();
  const editor = page.getByRole('dialog', { name: '编辑个人筛选方案', exact: true });
  await editor.getByRole('textbox', { name: '方案名称', exact: true }).fill('技术能力优先（已更新）');
  await editor.getByRole('spinbutton', { name: '相关年限' }).fill('0');
  await editor.getByRole('spinbutton', { name: '核心技能' }).fill('58');
  await expect(editor.getByRole('button', { name: '保存方案', exact: true })).toBeEnabled();
  await editor.getByRole('button', { name: '保存方案', exact: true }).click();
  await expect(editor).not.toBeVisible();
  expect(api.updates[0]?.weights).toMatchObject({ skills: 58, relatedYears: 0 });
  if (await maintenance.isVisible()) await page.keyboard.press('Escape');
  await expect(page.locator('.screening-profile-select')).toContainText('技术能力优先（已更新）');
  await page.getByRole('button', { name: '重新评分', exact: true }).click();
  await expect.poll(() => api.posts[0]?.weightProfileVersionId).toBe('personal-v2');
  expect(api.posts[0]?.resumeVersionIds).toEqual(['resume-a']);
  await expect(page.getByRole('dialog', { name: /重新评分|选择候选人/ })).toHaveCount(0);
});

test('方案控件支持键盘且切换后保持可访问', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await schemeWorkspace(page);
  await page.goto('/matching');
  const company = page.getByRole('radio', { name: '公司默认', exact: true });
  const personalOption = page.getByRole('radio', { name: '个人方案', exact: true });
  await company.focus();
  await page.keyboard.press('ArrowRight');
  await expect(personalOption).toBeChecked();
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({ page }).include('.screening-job-header').analyze()).violations).toEqual([]);
});

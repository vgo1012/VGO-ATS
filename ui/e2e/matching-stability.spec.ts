import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const companyWeights = { skillsWeight: 20, roleWeight: 20, yearsWeight: 20, responsibilityWeight: 20, industryProjectWeight: 20 };
const personalWeights = { skillsWeight: 42, roleWeight: 18, yearsWeight: 21, responsibilityWeight: 14, industryProjectWeight: 5 };
const personalProfile = {
  profile: { id: 'personal-profile', name: '验收-招聘专员-五维方案V2', ownerUserId: 'hr-a', version: '1' },
  currentVersion: { id: 'personal-v1', versionNo: 1, ...personalWeights },
};
const admittedCandidate = (index: number) => ({
  candidateId: `person-${index}`, candidateName: `候选人${String(index).padStart(3, '0')}`,
  resumeVersionId: `resume-${index}`, jdVersionId: 'jd-v2', resumeVersionNo: 2,
  originalFileName: `候选人${index}_第2版.pdf`, defaultMatchRunId: 'company-run',
  admissionStatus: 'ADMITTED', admissionId: `admission-${index}`, admissionVersion: '1',
  admittedAt: '2026-09-07T01:00:00Z', professionalScore: 62.54, referenceScore: 62.54,
  defaultResult: { professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS' },
});
const result = {
  resultId: 'company-result', baseEvaluationId: 'base-a', candidateId: 'person-0', resumeVersionId: 'resume-0',
  professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS', stableRank: 1,
  details: {
    jdFactSnapshotId: 'jd-facts', resumeFactSnapshotId: 'resume-facts', candidateId: 'person-0', resumeVersionId: 'resume-0',
    professionalScore: 62.54, bonusScore: 0, referenceScore: 62.54, mustTier: 'PASS', selectedBonusCap: 0, criteria: [],
    dimensions: [
      ['CORE_SKILLS', 90, 29], ['ROLE_EXPERIENCE', 80, 18], ['RELATED_YEARS', 100, 12],
      ['RESPONSIBILITY_MATCH', 28, 23], ['INDUSTRY_AND_PROJECTS', 20, 18],
    ].map(([code, score, configuredWeight]) => ({ code, score, configuredWeight, enabled: true, status: 'KNOWN', explanation: '固定事实评分', highlights: ['简历事实证据'] })),
  },
};
const companyRun = {
  id: 'company-run', taskId: 'company-task', jdVersionId: 'jd-v2', jdFactSnapshotId: 'jd-facts',
  planVersionId: 'company-v1', weightProfileVersionId: 'weight-v1', personalPreferenceVersionId: null,
  selectionSnapshotId: 'selection-a', sortMode: 'REFERENCE_SCORE', status: 'SUCCEEDED', totalCount: 1,
  succeededCount: 1, failedCount: 0, errorCode: null, version: 1, createdAt: '2026-09-07T01:00:00Z',
  updatedAt: '2026-09-07T01:00:01Z', startedAt: '2026-09-07T01:00:00Z', finishedAt: '2026-09-07T01:00:01Z',
};

type FixtureOptions = { admissionFailureCursor?: string; admissionDelayMs?: number; initialDelayMs?: number };

async function stabilityWorkspace(page: Page, options: FixtureOptions = {}) {
  await reviewWorkspace(page);
  const candidates = Array.from({ length: 205 }, (_, index) => admittedCandidate(index));
  const posts: Record<string, unknown>[] = [];
  const admissionRequests: URL[] = [];
  const resultRequests: URL[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const list = (items: unknown[]) => route.fulfill({ json: { ...empty, items } });
    if (path === '/jds/jd-a') return route.fulfill({ json: { id: 'jd-a', demandId: 'demand-a', demandCode: 'FE-2026-036', title: 'IT运维工程师', currentVersionNo: 2, clientName: '阿里巴巴' } });
    if (path === '/weight-profiles') return list([personalProfile]);
    if (path === '/jd-versions/jd-v2/active-scoring-config') return route.fulfill({ json: { jdVersionId: 'jd-v2', scoringConfiguration: companyScoring, scoringConfigurationActive: true, workflowTemplateActive: false, readyForApplication: false } });
    if (path === '/jd-versions/jd-v2/facts') return route.fulfill({ json: { facts: { title: 'IT运维工程师', summary: '负责数据中心服务器、交换机及动力环境的日常维护巡检。' } } });
    if (path === '/screening-candidates') {
      if (options.initialDelayMs) await new Promise(resolve => setTimeout(resolve, options.initialDelayMs));
      return route.fulfill({ json: candidates });
    }
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: [] });
    if (path === '/match-jobs') return list([{ jdVersionId: 'jd-v2', latestRunId: 'company-run', lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, candidateCount: 1, scoredCandidateCount: 1, activeRunCount: 0 }]);
    if (path.endsWith('/bases')) return list([{ basisRunId: 'company-run', basisKey: 'company-basis', jdVersionId: 'jd-v2', planVersionId: 'company-v1', weightProfileVersionId: 'weight-v1', sortMode: 'REFERENCE_SCORE', lastRunAt: '2026-09-07T01:00:00Z', runCount: 1, candidateCount: 1, scoredCandidateCount: 1, ...companyWeights, selectedCriterionIds: [], personalScreeningSettings: null }]);
    if (path === '/match-jobs/jd-v2/results-summary') return route.fulfill({ json: {total:1,strong:0,consider:0,reject:1,missing:0} });
    if (path === '/match-jobs/jd-v2/results') {
      resultRequests.push(url);
      return list([{ runId: 'company-run', scoredAt: '2026-09-07T01:00:00Z', result }]);
    }
    if (path === '/match-runs') {
      if (request.method() === 'POST') {
        posts.push(request.postDataJSON() as Record<string, unknown>);
        return route.fulfill({ json: { ...companyRun, id: 'personal-run', taskId: 'personal-task', weightProfileVersionId: 'personal-v1', totalCount: candidates.length, succeededCount: candidates.length } });
      }
      return list([companyRun]);
    }
    if (path === '/jd-versions/jd-v2/screening-candidates') {
      admissionRequests.push(url);
      if (options.admissionDelayMs) await new Promise(resolve => setTimeout(resolve, options.admissionDelayMs));
      const cursor = url.searchParams.get('cursor');
      if (cursor && cursor === options.admissionFailureCursor) return route.fulfill({ status: 503, json: { title: '候选人读取失败', status: 503, detail: '第二页暂时不可用' } });
      const limit = Number(url.searchParams.get('limit') ?? 100);
      const start = Number(cursor?.replace('admissions-', '') ?? 0);
      const next = start + limit;
      return route.fulfill({ json: { items: candidates.slice(start, next), hasMore: next < candidates.length, nextCursor: next < candidates.length ? `admissions-${next}` : null } });
    }
    if (path === '/match-runs/personal-run') return route.fulfill({ json: { ...companyRun, id: 'personal-run', taskId: 'personal-task', weightProfileVersionId: 'personal-v1', totalCount: candidates.length, succeededCount: candidates.length } });
    if (path === '/match-runs/personal-run/results') return list([result]);
    if (path === '/tasks/personal-task') return route.fulfill({ json: { taskId: 'personal-task', resourceId: 'personal-run', resourceType: 'MATCH_RUN', taskType: 'MATCH_RUN', status: 'SUCCEEDED', attemptCount: 1, maxAttempts: 3, progressPercent: 100, retryable: false, version: 1, createdAt: '2026-09-07T01:00:00Z', updatedAt: '2026-09-07T01:00:01Z' } });
    return route.fallback();
  });
  return { candidates, posts, admissionRequests, resultRequests };
}

async function openReadyWorkspace(page: Page, options?: FixtureOptions) {
  const api = await stabilityWorkspace(page, options);
  await page.goto('/matching');
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  return api;
}

test('评分详情的长亮点完整换行且不溢出维度卡片', async ({ page }, testInfo) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await stabilityWorkspace(page);
  const longHighlight = '交接班记录 ← 拥有一年大型数据中心机房值班及运维工作经验，熟练运用动环、BA及电力监控系统实时监控机房运行数据。'.repeat(4);
  const longToken = 'DataCenterOperations'.repeat(18);
  const longResult = { ...result, details: { ...result.details, dimensions: result.details.dimensions.map(dimension => ({ ...dimension, highlights: [longHighlight, longToken] })) } };
  await page.route('**/api/v1/match-jobs/jd-v2/results?**', route => route.fulfill({ json: { ...empty, items: [{ runId: 'company-run', scoredAt: companyRun.finishedAt, result: longResult }] } }));
  await page.route('**/api/v1/match-runs/company-run', route => route.fulfill({ json: companyRun }));
  await page.goto('/matching');
  const trigger = page.getByRole('button', { name: '查看候选人的评分详情' });
  const dialog = page.getByRole('dialog', { name: /评分详情/ });
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expect(dialog.locator('.matching-evidence-dimension')).toHaveCount(5);
    await expect.poll(() => dialog.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
    for (const card of await dialog.locator('.matching-evidence-dimension').all()) {
      const bounds = await card.boundingBox();
      for (const tag of await card.locator('.ant-card-body .ant-tag').all()) {
        const box = await tag.boundingBox();
        expect(box!.x).toBeGreaterThanOrEqual(bounds!.x - 1);
        expect(box!.x + box!.width).toBeLessThanOrEqual(bounds!.x + bounds!.width + 1);
        expect(await tag.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
      }
      await expect(card.getByRole('button', { name: /原文依据/ })).toHaveCount(0);
    }
    await dialog.screenshot({ path: testInfo.outputPath(`scoring-detail-${width}.png`) });
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
  }
});

test('智能筛选长列表的操作列不遮挡表头和分页', async ({ page }) => {
  await stabilityWorkspace(page);
  await page.route('**/api/v1/match-jobs/jd-v2/results?**', route => route.fulfill({ json: { items: Array.from({ length: 30 }, (_, index) => ({ runId: 'company-run', result: { ...result, resultId: `result-${index}`, candidateId: `person-${index}`, resumeVersionId: `resume-${index}` } })), hasMore: false, nextCursor: null } }));
  await page.goto('/matching');
  const table = page.locator('.matching-score-table');
  await expect(table.locator('tbody tr.ant-table-row')).toHaveCount(30);
  for (const cell of await table.locator('thead th, tbody tr.ant-table-row:first-child > td').all()) {
    await expect(cell).toHaveCSS('text-align', 'center');
    await expect(cell).toHaveCSS('vertical-align', 'middle');
  }

  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const content = table.locator('.ant-table-content');
    await content.scrollIntoViewIfNeeded();
    await content.evaluate(element => { element.scrollLeft = element.scrollWidth; element.scrollTop = element.scrollHeight; });
    const header = table.getByRole('columnheader', { name: '操作', exact: true });
    await header.scrollIntoViewIfNeeded();
    expect(await header.evaluate(element => { const box = element.getBoundingClientRect(); return element.contains(document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)); })).toBe(true);
    const pager = page.locator('.compact-pagination').last();
    await pager.scrollIntoViewIfNeeded();
    expect(await pager.evaluate(element => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  }
});

test('全选结果读取期间改变筛选，不恢复已清空的旧选择',async({page})=>{
  await stabilityWorkspace(page);
  let requests=0;
  let release!:()=>void;
  const pending=new Promise<void>(resolve=>{release=resolve;});
  await page.route('**/api/v1/match-runs/personal-run/results?*',async route=>{
    requests++;
    if(requests>1)await pending;
    return route.fulfill({json:{...empty,items:[result]}});
  });
  await page.goto('/matching?run=personal-run');
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  await page.getByRole('button',{name:'全选本次全部结果'}).click();
  await expect.poll(()=>requests).toBe(2);
  await page.getByRole('textbox',{name:'搜索本页候选人'}).fill('没有匹配的候选人');
  release();
  await expect(page.locator('.candidate-bulk-actions .ant-btn-loading')).toHaveCount(0);
  await expect(page.getByText('已选 0 人',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'批量转入共享库'})).toBeDisabled();
  await page.getByRole('textbox',{name:'搜索本页候选人'}).fill('');
  await page.getByRole('button',{name:'全选本次全部结果'}).click();
  await expect(page.getByText('已选 1 人',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'批量转入共享库'})).toBeEnabled();
});

test('部分评分失败在成功结果旁保留重试入口，重试期间保留结果与取消操作',async({page})=>{
  const api=await stabilityWorkspace(page);
  let retryCount=0;
  let status='PARTIAL';
  await page.route('**/api/v1/match-runs/personal-run',route=>route.fulfill({json:{...companyRun,id:'personal-run',taskId:'personal-task',weightProfileVersionId:'personal-v1',status,totalCount:2,succeededCount:1,failedCount:status==='PARTIAL'?1:0}}));
  await page.route('**/api/v1/tasks/personal-task',route=>route.fulfill({json:{taskId:'personal-task',resourceId:'personal-run',resourceType:'MATCH_RUN',taskType:'MATCH_RUN',status:status==='PARTIAL'?'FAILED':status,attemptCount:1,maxAttempts:3,progressPercent:status==='RUNNING'?50:100,retryable:true,version:1}}));
  await page.route('**/api/v1/match-runs/personal-run/retry-failed',route=>{
    retryCount++;status='RUNNING';
    return route.fulfill({status:204});
  });
  await page.goto('/matching?run=personal-run');
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  const retry=page.getByRole('button',{name:'仅重试失败项'});
  await expect(retry).toBeVisible();
  await retry.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button',{name:/^取\s*消$/})).toBeVisible();
  await expect(page.getByRole('progressbar',{name:'候选人匹配进度'})).toHaveAttribute('aria-valuenow','50');
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  status='SUCCEEDED';
  await expect(page.getByRole('progressbar',{name:'候选人匹配进度'})).toHaveCount(0);
  await expect(page.getByRole('button',{name:/^取\s*消$/})).toHaveCount(0);
  await expect(retry).toHaveCount(0);
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  expect(retryCount).toBe(1);
  expect(api.posts).toEqual([]);
});

test('公司与个人方案切换对应评分结果，未评分的个人方案保留重评分入口且方案框固定占位', async ({ page }) => {
  const api = await openReadyWorkspace(page);
  await expect(page.getByRole('button', { name: '重新评分', exact: true })).toBeDisabled();
  await expect(page.getByRole('columnheader', { name: '分级', exact: true })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '必须项', exact: true })).toBeVisible();
  // 必须条件满足但综合分偏低时，应显示不推荐，不能用“满足”冒充分级。
  const resultRow=page.locator('.matching-score-table tbody tr.ant-table-row').first();
  await expect(resultRow.getByText('已核对：满足',{exact:true})).toBeVisible();
  await expect(resultRow.getByText('不推荐',{exact:true})).toBeVisible();
  const header = page.getByRole('region', { name: '当前筛选岗位' });
  const scheme = page.getByRole('radiogroup', { name: '评分方案', exact: true });
  const profile = page.getByRole('combobox', { name: '选择个人方案', exact: true });
  await expect(profile).toBeVisible();
  await expect(profile).toBeDisabled();
  await expect(page.locator('.screening-profile-select')).toContainText(/公司默认/);
  const requestsBefore = api.resultRequests.length;
  const profileBefore = await profile.boundingBox();

  await scheme.getByText('个人方案', { exact: true }).click();
  await expect(profile).toBeEnabled();
  await expect(page.locator('.screening-profile-select')).toContainText(personalProfile.profile.name);
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
  await expect(page.getByText('暂无个人方案的评分结果', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '重新评分', exact: true })).toBeEnabled();
  await expect(page.locator('.matching-total')).toHaveCount(0);
  expect(api.resultRequests).toHaveLength(requestsBefore);
  expect(await profile.boundingBox()).toEqual(profileBefore);

  await scheme.getByText('公司默认', { exact: true }).click();
  await expect(profile).toBeDisabled();
  await expect(page.locator('.screening-profile-select')).toContainText(/公司默认/);
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  await expect(page.getByText('暂无个人方案的评分结果', { exact: true })).toHaveCount(0);
  expect(api.posts).toEqual([]);
  await expect(header.getByText('本次筛选候选人', { exact: false })).toHaveCount(0);
});

test('重新评分直接读取当前 JD 全部已准入候选人，并按个人权重整批提交', async ({ page }) => {
  const api = await openReadyWorkspace(page);
  await page.getByRole('radiogroup', { name: '评分方案', exact: true }).getByText('个人方案', { exact: true }).click();
  await page.getByRole('button', { name: '重新评分', exact: true }).click();

  await expect.poll(() => api.posts).toHaveLength(1);
  await expect(page.getByRole('dialog', { name: /重新评分|选择候选人/ })).toHaveCount(0);
  await expect(page.getByText(/本次筛选候选人/)).toHaveCount(0);
  expect(api.admissionRequests.map(url => ({ limit: url.searchParams.get('limit'), cursor: url.searchParams.get('cursor') }))).toEqual([
    { limit: '100', cursor: null },
    { limit: '100', cursor: 'admissions-100' },
    { limit: '100', cursor: 'admissions-200' },
  ]);
  expect(api.posts[0]).toEqual({
    jdVersionId: 'jd-v2', resumeVersionIds: api.candidates.map(item => item.resumeVersionId),
    weightProfileVersionId: 'personal-v1', selectedCriterionIds: [], sortMode: 'REFERENCE_SCORE',
  });
  await expect(page).toHaveURL(/run=personal-run/);
  await page.getByRole('radiogroup', { name: '评分方案', exact: true }).getByText('公司默认', { exact: true }).click();
  await expect(page).not.toHaveURL(/run=/);
  await expect(page.locator('.matching-score-table')).toContainText('62.54');
  await expect(page.getByText('公司默认评分结果', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /重新评分$/ })).toBeDisabled();
  expect(api.posts).toHaveLength(1);
});

test('全部候选人分页读取完成前不提交，任一页失败时禁止部分评分', async ({ page }) => {
  const api = await openReadyWorkspace(page, { admissionFailureCursor: 'admissions-100', admissionDelayMs: 500 });
  await page.getByRole('radiogroup', { name: '评分方案', exact: true }).getByText('个人方案', { exact: true }).click();
  const rescore = page.getByRole('button', { name: '重新评分', exact: true });
  await rescore.click();
  await expect.poll(() => api.admissionRequests.length).toBe(2);
  expect(api.posts).toEqual([]);
  await expect(page.getByRole('alert')).toContainText(/第二页暂时不可用|候选人读取失败|重新评分失败|无法识别的错误/);
  await expect(rescore).toBeEnabled();
});

test('首次慢加载只显示一个稳定骨架，不闪现旧加载文案、岗位提示或结果空态', async ({ page }) => {
  await stabilityWorkspace(page, { initialDelayMs: 1_500 });
  await page.goto('/matching');
  const loading = page.getByRole('status', { name: '正在加载智能筛选', exact: true });
  await expect(loading).toHaveCount(1);
  await expect(loading).toBeVisible();
  for (const staleCopy of ['正在读取评分记录', '正在读取候选人评分', '正在读取已加入智能筛选的简历', '请先选择招聘岗位']) {
    await expect(page.getByText(staleCopy, { exact: false })).toHaveCount(0);
  }
  await expect(page.locator('.ant-empty-description')).toHaveCount(0);
  await expect(page.locator('.matching-total b')).toHaveText('62.54');
  await expect(loading).toHaveCount(0);
});

for (const width of [390, 1280, 1440, 1920]) {
  test(`${width}px 下方案切换保持头部和方案框坐标，空结果无页面溢出且通过 axe`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await stabilityWorkspace(page, { admissionDelayMs: 80 });
    await page.goto('/matching');
    const header = page.getByRole('region', { name: '当前筛选岗位' });
    const score = page.locator('.matching-total').first();
    await expect(header).toBeVisible();
    await expect(score).toBeVisible();
    const headerBefore = await header.boundingBox();
    const profile = page.getByRole('combobox', { name: '选择个人方案', exact: true });
    await expect(profile).toBeVisible();
    const profileBefore = await profile.boundingBox();
    await page.screenshot({ path: testInfo.outputPath(`matching-company-${width}.png`), animations: 'disabled' });

    await page.getByRole('radiogroup', { name: '评分方案', exact: true }).getByText('个人方案', { exact: true }).click();
    await expect(profile).toBeEnabled();
    await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
    expect(await header.boundingBox()).toEqual(headerBefore);
    expect(await profile.boundingBox()).toEqual(profileBefore);
    await expect(page.getByText('暂无个人方案的评分结果', { exact: true })).toBeVisible();
    await expect(score).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    expect((await new AxeBuilder({ page }).include('.matching-workspace-page').analyze()).violations).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`matching-personal-${width}.png`), animations: 'disabled' });
  });
}


test('岗位41人跨三页保留全部推荐统计与总页数，统计失败可恢复', async ({ page }, testInfo) => {
  await stabilityWorkspace(page);
  const all = Array.from({length:41}, (_, index) => ({runId:'company-run',scoredAt:companyRun.finishedAt,result:{...result,resultId:`r-${index}`,candidateId:`person-${index}`,resumeVersionId:`resume-${index}`,stableRank:index+1}}));
  let summaryFailure=false;
  let summaryRequests=0;
  await page.route('**/api/v1/match-jobs/jd-v2/results-summary?*',route=>{
    summaryRequests++;
    return summaryFailure
      ? route.fulfill({status:503,json:{status:503,title:'统计暂不可用',detail:'请重试'}})
      : route.fulfill({json:{total:41,strong:12,consider:24,reject:4,missing:1}});
  });
  await page.route('**/api/v1/match-jobs/jd-v2/results?*',route=>{
    const params=new URL(route.request().url()).searchParams;
    const offset=Number(params.get('cursor')??0);
    const limit=Number(params.get('limit'));
    return route.fulfill({json:{items:all.slice(offset,offset+limit),hasMore:offset+limit<41,nextCursor:offset+limit<41?String(offset+limit):null}});
  });
  await page.goto('/matching');
  const metrics=page.getByRole('region',{name:'岗位全部候选人统计'});
  const pager=page.getByRole('navigation',{name:'岗位结果分页'});
  for(const [index,size] of [20,20,1].entries()){
    await expect(page.locator('.matching-score-table tbody tr.ant-table-row')).toHaveCount(size);
    await expect(metrics.getByRole('button',{name:'简历总数'}).locator('strong')).toHaveText('41');
    await expect(metrics.getByRole('button',{name:'强推荐'}).locator('strong')).toHaveText('12');
    await expect(metrics.getByRole('button',{name:'可考虑'}).locator('strong')).toHaveText('24');
    await expect(metrics.getByRole('button',{name:'不推荐'}).locator('strong')).toHaveText('4');
    await expect(metrics.getByRole('button',{name:'信息缺失'}).locator('strong')).toHaveText('1');
    await expect(pager).toContainText('共41条记录');
    await expect(pager).toContainText('共3页');
    if(index<2)await pager.getByRole('button',{name:'下一页结果'}).click();
  }
  const sizeInput=pager.getByRole('spinbutton',{name:'每页条数'});
  await sizeInput.fill('30');
  await sizeInput.press('Enter');
  await expect(page.locator('.matching-score-table tbody tr.ant-table-row')).toHaveCount(30);
  await expect(pager.getByRole('spinbutton',{name:'页码'})).toHaveValue('1');
  await expect(pager).toContainText('共2页');
  await pager.getByRole('button',{name:'下一页结果'}).click();
  await expect(page.locator('.matching-score-table tbody tr.ant-table-row')).toHaveCount(11);
  await expect(metrics.getByRole('button',{name:'简历总数'}).locator('strong')).toHaveText('41');
  expect(summaryRequests).toBe(1);
  await page.screenshot({path:testInfo.outputPath('job-summary-last-page.png'),fullPage:true});
  summaryFailure=true;
  await page.reload();
  await expect(pager).toContainText('统计失败');
  await expect(metrics.getByRole('button',{name:'简历总数'}).locator('strong')).toHaveText('—');
  summaryFailure=false;
  await pager.getByRole('button',{name:'重试统计'}).click();
  await expect(pager).toContainText('共41条记录');
  await expect(metrics.getByRole('button',{name:'简历总数'}).locator('strong')).toHaveText('41');
});


for (const count of [0, 4, 30]) test(`候选人列表与分页连续衔接 ${count} 行`, async ({page},testInfo) => {
  await stabilityWorkspace(page);
  await page.route('**/api/v1/match-jobs/jd-v2/results?*', route => route.fulfill({json:{...empty,items:Array.from({length:count},(_,index)=>({runId:'company-run',result:{...result,resultId:`joined-${index}`,candidateId:`person-${index}`}}))}}));
  await page.goto('/matching');
  const ledger=page.locator('.matching-result-ledger');
  const pager=page.getByRole('navigation',{name:'岗位结果分页'});
  await expect(ledger).toBeVisible();
  await expect(pager).toBeVisible();
  await page.locator('.matching-workspace-page').evaluate(element=>{element.scrollTop=element.scrollHeight;});
  const bounds=await ledger.evaluate(element=>{
    const list=element.getBoundingClientRect();
    const footer=element.nextElementSibling!.getBoundingClientRect();
    return {gap:footer.top-list.bottom,left:list.left-footer.left,right:list.right-footer.right};
  });
  expect(Math.abs(bounds.gap)).toBeLessThanOrEqual(1);
  expect(Math.abs(bounds.left)).toBeLessThanOrEqual(1);
  expect(Math.abs(bounds.right)).toBeLessThanOrEqual(1);
  await pager.getByRole('spinbutton',{name:'每页条数'}).fill('30');
  await pager.getByRole('spinbutton',{name:'每页条数'}).press('Enter');
  await expect(pager.getByRole('spinbutton',{name:'每页条数'})).toHaveValue('30');
  await page.screenshot({path:testInfo.outputPath('joined-list-footer.png'),animations:'disabled'});
});

for (const width of [390, 1280, 1440, 1920]) test(`方案操作与简历总数右对齐 ${width}`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await stabilityWorkspace(page);
  await page.goto('/matching');
  const total = page.getByRole('button', { name: '简历总数', exact: true });
  await expect(total).toBeVisible();
  const rescore = page.getByRole('button', { name: '重新评分', exact: true });
  await expect(rescore).toBeVisible();
  if (width > 600) {
    const button = (await rescore.boundingBox())!;
    const card = (await total.boundingBox())!;
    expect(Math.abs(button.x + button.width - card.x - card.width)).toBeLessThanOrEqual(1);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  expect((await new AxeBuilder({ page }).include('.screening-job-header').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath(`screening-right-aligned-${width}.png`), animations: 'disabled' });
});

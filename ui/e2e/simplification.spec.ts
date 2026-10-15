import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { companyScoring } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const job = { id: 'jd-a', demandId: 'demand-a', demandCode: 'INTEGRATION_DEMAND_2026', title: '高级研发工程师（负责招聘平台与跨部门协同系统的架构设计和交付）', currentVersionNo: 1 };
async function setup(page: Page, superAdmin = false) {
  const requests: URL[] = [];
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    requests.push(url);
    let body: unknown = empty;
    if (path === '/me') body = { userId: 'hr-a', displayName: '招聘专员', roles: ['HR'], permissions: ['talent.read', 'demand.read', 'recruitment.manage', 'interview.feedback.read-authorized', 'matching.run', 'reporting.read-own'], superAdmin };
    if (['/candidate-pool-statuses','/candidate-resume-summaries','/applications/stage-counts'].includes(path)) body = [];
    if (path === '/jds') body = { ...empty, items: [job] };
    if (path.includes('jd-versions') || path === '/jds/jd-a/versions') body = { ...empty, items: [{ id: 'jd-v1', jdId: job.id, versionNo: 1, title: job.title }] };
    if (path === '/my/interview-tasks' || path.startsWith('/directory/users') || path.startsWith('/workbench')) body = [];
    if (path === '/candidates') body = { ...empty, items: [{ id: 'person-a', fullName: '林雨桐', ownerUserId: 'hr-a', createdAt: '2026-09-01T00:00:00Z' }] };
    if (path === '/weight-profiles') body = empty;
    if (path === '/tasks') body = [];
    if(path==='/auth/csrf') body={headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'test'};
    return route.fulfill({ json: body });
  });
  return requests;
}

test('JD choices remain readable and clearing the selected job clears its version filter', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('/schedule');
  await page.getByRole('combobox', { name: '搜索应聘岗位' }).click();
  const option = page.locator('.ant-select-item-option-content').filter({ hasText: job.title });
  await expect(option).toBeVisible();
  expect(await option.evaluate(el => getComputedStyle(el).whiteSpace)).not.toBe('nowrap');
  const popup = await page.locator('.jd-selector-popup').boundingBox();
  expect(popup!.x).toBeGreaterThanOrEqual(0);
  expect(popup!.x + popup!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await option.click();
  await page.getByRole('combobox', { name: '岗位版本' }).click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: '第 1 版' }).click();
  await expect.poll(() => requests.filter(url => url.pathname.endsWith('/interviews')).at(-1)?.searchParams.get('jdVersionId')).toBe('jd-v1');
  await page.getByRole('button', { name: '清空岗位筛选' }).click();
  await page.getByRole('button',{name:'下一周',exact:true}).click();
  await expect.poll(() => requests.filter(url => url.pathname.endsWith('/interviews')).at(-1)?.searchParams.has('jdId')).toBe(false);
  expect(requests.filter(url => url.pathname.endsWith('/interviews')).at(-1)?.searchParams.has('jdVersionId')).toBe(false);
});

test('library pagination stays at the bottom of its panel with one result', async ({ page }) => {
  await setup(page);
  await page.goto('/talents?view=pool');
  await expect(page.getByRole('button', { name: '详情与版本' })).toBeVisible();
  const panel = await page.locator('.talent-ledger-card').boundingBox();
  const footer = await page.locator('.data-page-controls').boundingBox();
  expect(panel!.y + panel!.height - footer!.y - footer!.height).toBeLessThan(25);
});

test('personal weighting opens inside settings and is independent of a JD', async ({ page }) => {
  await setup(page);
  await page.goto('/settings/preferences');
  await expect(page.getByRole('navigation', { name: '设置导航' })).toBeVisible();
  await page.getByRole('button', { name: '新建方案' }).click();
  await expect(page.getByRole('dialog')).toContainText('核心技能');
  await expect(page.getByRole('dialog')).toContainText('100%');
});


test('calendar can query earlier and later weeks and return to this week', async ({page})=>{
  await page.clock.setFixedTime(new Date('2026-09-07T01:00:00Z'));
  const requests=await setup(page);
  await page.goto('/schedule');
  const weekly=()=>requests.filter(url=>url.pathname.endsWith('/interviews')).at(-1);
  await expect.poll(()=>weekly()?.searchParams.get('from')).toBe('2026-09-06T16:00:00.000Z');
  await page.getByRole('button',{name:'上一周',exact:true}).click();
  await expect.poll(()=>weekly()?.searchParams.get('from')).toBe('2026-08-30T16:00:00.000Z');
  await page.getByRole('button',{name:/^本\s*周$/}).first().click();
  await expect(page.locator('.schedule-range-label')).toContainText('9 月 7 日 — 9 月 13 日');
  await page.getByRole('button',{name:'下一周',exact:true}).click();
  await expect.poll(()=>weekly()?.searchParams.get('to')).toBe('2026-09-20T16:00:00.000Z');
});

test('company scoring clones V1 into an editable V2 without a JD or JSON editor',async({page})=>{
  await setup(page,true);
  let body:Record<string,unknown>|undefined;
  let cloned=false;
  const draft={...companyScoring,version:{...companyScoring.version,id:'company-v2',versionNo:2,status:'DRAFT'}};
  await page.route('**/api/v1/scoring-defaults',route=>route.fulfill({json:{scoringConfiguration:companyScoring}}));
  await page.route('**/api/v1/admin/jd-scoring-configs**',route=>{
    if(route.request().url().endsWith('/company-v1/clone')){cloned=true;return route.fulfill({json:draft});}
    if(route.request().method()==='PATCH'){body=route.request().postDataJSON() as Record<string,unknown>;expect(route.request().url()).toContain('/company-v2');return route.fulfill({json:draft});}
    return route.fulfill({json:{...empty,items:cloned?[draft,companyScoring]:[companyScoring]}});
  });
  await page.goto('/admin/scoring');
  await page.getByRole('button',{name:'创建 V2 草稿'}).click();
  await expect(page.getByRole('dialog')).toHaveAccessibleName('编辑评分方案 · V2');
  expect(cloned).toBe(true);
  await page.getByRole('textbox',{name:'方案名称'}).fill('技术岗位通用方案');
  await page.getByRole('combobox',{name:'添加附加要求'}).click();
  await page.locator('.ant-select-item-option-content').filter({hasText:'证书'}).click();
  await page.getByRole('combobox',{name:'证书要求'}).fill('PMP');
  await page.getByRole('combobox',{name:'证书要求'}).press('Enter');
  await page.getByRole('textbox',{name:'方案名称'}).click();
  await expect(page.getByRole('dialog')).not.toContainText('JSON');
  await page.screenshot({animations:'disabled',path:`.planning/2026-09-07-simple-workflows/evidence/scoring-${test.info().project.name}.png`,fullPage:true});
  expect((await new AxeBuilder({page}).include('.ant-modal').analyze()).violations).toEqual([]);
  await page.getByRole('button',{name:'保存草稿',exact:true}).click();
  await expect.poll(()=>body?.name).toBe('技术岗位通用方案');
  expect(body).not.toHaveProperty('jdVersionId');
  expect(body?.criteria).toEqual([expect.objectContaining({code:'CERTIFICATE',operator:'IN',expectedValueJson:'{"values":["PMP"]}',bonusPoints:0,selectableByHr:false})]);
});

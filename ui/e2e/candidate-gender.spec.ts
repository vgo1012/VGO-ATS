import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const people = [
  { id: 'male', fullName: '何伟健 (Howard)', gender: 'MALE' },
  { id: 'female', fullName: '匡菲菲', gender: 'FEMALE' },
  { id: 'unknown', fullName: '性别未提供', gender: null },
  { id: 'other', fullName: '性别未指定', gender: 'OTHER' },
  { id: 'forbidden', fullName: '摘要未授权', gender: null },
];
const empty = { items: [], hasMore: false, nextCursor: null };
const candidate = (person: typeof people[number]) => ({ ...person, primaryPhone: '13800001234', ownerUserId: 'hr-a', status: 'ACTIVE', version: 1 });
const facts = (person: typeof people[number]) => ({ snapshot: { id: `fact-${person.id}`, resumeVersionId: `resume-${person.id}`, gender: person.gender, createdAt: '2026-09-07T01:00:00Z' }, workExperiences: [], projectExperiences: [], educations: [], skills: [], simpleFacts: [] });

async function genderWorkspace(page: Page) {
  await reviewWorkspace(page);
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/candidate-batch') return route.fulfill({ json: people.map(candidate) });
    if (path === '/resume-version-fact-summaries') return route.fulfill({ json: people.filter(person => person.id !== 'forbidden').map(person => ({ resumeVersionId: `resume-${person.id}`, facts: facts(person) })) });
    if (path === '/candidates') return route.fulfill({ json: { ...empty, items: people.map(candidate) } });
    if (path.startsWith('/candidates/')) {
      const person = people.find(person => path === `/candidates/${person.id}`);
      if (person) return route.fulfill({ json: candidate(person) });
    }
    if (path === '/candidate-resume-summaries') return route.fulfill({ json: people.filter(person => url.searchParams.getAll('candidateIds').includes(person.id)).map(person => ({ candidateId: person.id, resumeVersion: null, source: null, factsStatus: person.id === 'forbidden' ? 'FORBIDDEN' : 'READY', facts: person.id === 'forbidden' ? null : facts(person) })) });
    if (path === '/candidate-pool-statuses') return route.fulfill({ json: people.map(person => ({ candidateId: person.id, resumeVersionId: `resume-${person.id}`, jobContexts: [{ jdId: 'jd-a', jdVersionId: 'jd-v2', title: '平台开发 JD', versionNo: 2, resumeVersionId: `resume-${person.id}`, status: 'ADVANCED', applicationId: `application-${person.id}` }] })) });
    if (path === '/talent-search') return route.fulfill({ json: { ...empty, items: people.map(person => ({ candidateId: person.id, resumeVersionId: `resume-${person.id}`, fullName: person.fullName, gender: person.gender, phone: '13800001234', skills: [], degrees: [], conditionMatches: [], engagementLock: null })) } });
    if (path === '/applications') return route.fulfill({ json: { ...empty, items: people.map(person => ({ id: `application-${person.id}`, candidateId: person.id, resumeVersionId: `resume-${person.id}`, jdId: 'jd-a', demandId: 'demand-a', ownerUserId: 'hr-a', currentStage: 'ONBOARDED', recruitmentType: 'INTERNAL', createTime: '2026-09-07T01:00:00Z', updateTime: '2026-09-07T01:00:00Z' })) } });
    if (path === '/workbench') return route.fulfill({ json: people.map(person => ({ applicationId: `application-${person.id}`, candidateId: person.id, jdId: 'jd-a', stepId: `step-${person.id}`, stepCode: 'INTERVIEW', stepStatus: 'IN_PROGRESS', ownerUserId: 'hr-a', dueAt: null, overdue: false })) });
    const person = people.find(person => path === `/resume-versions/resume-${person.id}/facts`);
    if (person) return person.id === 'forbidden' ? route.fulfill({ status: 403, contentType: 'application/problem+json', json: { status: 403, title: '未授权', detail: '无权查看简历摘要' } }) : route.fulfill({ json: facts(person) });
    return route.fallback();
  });
}

const views = [
  { name: '待筛选', path: '/talents' },
  { name: '共享简历库', path: '/shared-library' },
  { name: '已入职', path: '/onboarded' },
];

for (const width of [390, 1440]) for (const view of views) test(`${view.name} shows authorized gender beside names at ${width}px`, async ({ page }, testInfo) => {
  await genderWorkspace(page);
  await page.setViewportSize({ width, height: 900 });
  await page.goto(view.path);
  const table = page.getByRole('table');
  const maleRow = table.getByRole('row').filter({ hasText: '何伟健' });
  const femaleRow = table.getByRole('row').filter({ hasText: '匡菲菲' });
  const male = maleRow.getByRole('img', { name: '男', exact: true });
  const female = femaleRow.getByRole('img', { name: '女', exact: true });
  await expect(male).toBeVisible();
  for (const cell of await table.locator('thead th, tbody tr.ant-table-row > td').all()) {
    await expect(cell).toHaveCSS('text-align', 'center');
    await expect(cell).toHaveCSS('vertical-align', 'middle');
  }

  const name = maleRow.getByRole('button', { name: /何伟健/ });
  await expect(name).toHaveAttribute('title', '何伟健 (Howard)');
  await expect(name).toHaveCSS('white-space', 'nowrap');
  const nameLayout = await name.evaluate((button, fullName) => {
    const cell = button.closest('td');
    const walker = document.createTreeWalker(button, NodeFilter.SHOW_TEXT);
    let nameNode: Node | null = null;
    while (walker.nextNode()) {
      if (walker.currentNode.textContent?.includes(fullName)) {
        nameNode = walker.currentNode;
        break;
      }
    }
    const nameElement = nameNode?.parentElement;
    if (!cell || !nameNode || !nameElement) throw new Error('候选人姓名或所属单元格不存在');
    const range = document.createRange();
    range.selectNodeContents(nameNode);
    const fragments = [...range.getClientRects()];
    const textRect = range.getBoundingClientRect();
    const cellRect = cell.getBoundingClientRect();
    return {
      sameLine: fragments.length > 0 && Math.max(...fragments.map(rect => rect.top)) < Math.min(...fragments.map(rect => rect.bottom)),
      centerOffset: Math.abs(textRect.left + textRect.width / 2 - (cellRect.left + cellRect.width / 2)),
      width: nameElement.clientWidth,
      contentWidth: nameElement.scrollWidth,
    };
  }, '何伟健 (Howard)');
  expect(nameLayout.sameLine).toBe(true);
  expect(nameLayout.centerOffset).toBeLessThanOrEqual(1);
  expect(nameLayout.contentWidth).toBeLessThanOrEqual(nameLayout.width);

  await expect(female).toBeVisible();
  await expect(male).toHaveCSS('color', 'rgb(22, 139, 210)');
  await expect(female).toHaveCSS('color', 'rgb(195, 60, 84)');
  for (const name of ['性别未提供', '性别未指定', '摘要未授权']) {
    const row = table.getByRole('row').filter({ hasText: name });
    await expect(row).toBeVisible();
    await expect(row.getByRole('img', { name: /^(男|女)$/ })).toHaveCount(0);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({ path: testInfo.outputPath(`candidate-gender-${view.name}-${width}.png`), fullPage: true, animations: 'disabled' });
});

test('candidate pool keeps detail actions available when optional gender facts fail', async ({ page }) => {
  await genderWorkspace(page);
  await page.route('**/api/v1/candidate-resume-summaries?**', route => route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: '暂不可用', detail: '摘要读取失败' } }));
  await page.goto('/talents');
  const table = page.getByRole('table');
  await expect(table.getByRole('row').filter({ hasText: '何伟健' })).toBeVisible();
  await expect(table.getByRole('button', { name: '详情与版本' })).toHaveCount(people.length);
  await expect(table.getByRole('img', { name: /^(男|女)$/ })).toHaveCount(0);
});

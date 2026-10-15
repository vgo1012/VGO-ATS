import { expect, test, type Page } from '@playwright/test';

const actor = { userId: 'review-user', displayName: '验收用户', workEmail: 'review@example.com', homeDepartmentId: 'review-dept', roles: ['SUPER_ADMIN'], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };
const emptyPage = { items: [], nextCursor: null, hasMore: false };
const unavailable = { title: '服务暂不可用', status: 503, detail: '连接暂时中断，请重试。', code: 'SERVICE_UNAVAILABLE', recoverable: true };

async function mockWorkspace(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const arrayPaths = ['/applications/stage-counts', '/candidate-pool-statuses', '/directory/users/resolve', '/workbench', '/management/workbench', '/my/interview-tasks', '/tasks'];
    const emptyBlacklistHistory = /^\/candidates\/[^/]+\/company-blacklist-records$/.test(path)
      ? { current: null, active: false, records: emptyPage }
      : undefined;
    return route.fulfill({ json: path === '/me' ? actor : arrayPaths.includes(path) ? [] : emptyBlacklistHistory ?? emptyPage });
  });
}

async function mockLogin(page: Page) {
  await mockWorkspace(page);
  await page.route('**/api/v1/me', route => route.fulfill({ status: 401, json: { ...unavailable, status: 401, code: 'SESSION_REQUIRED' } }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'review-csrf' } }));
}

test('login survives unavailable storage and submits only once while pending', async ({ page }) => {
  await mockLogin(page);
  await page.addInitScript(() => {
    Storage.prototype.getItem = () => { throw new DOMException('Unavailable', 'SecurityError'); };
    Storage.prototype.setItem = () => { throw new DOMException('Unavailable', 'SecurityError'); };
  });
  let submissions = 0;
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/auth/login', async route => {
    submissions++;
    expect(route.request().headers()['x-csrf-token']).toBe('review-csrf');
    expect(route.request().postDataJSON()).toEqual({ workEmail: 'review@example.com', password: 'test-password' });
    await pending;
    await route.fulfill({ json: actor });
  });
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  await page.getByLabel('账号', { exact: true }).fill('review@example.com');
  await page.getByLabel('密码', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('button', { name: '正在验证身份' })).toBeDisabled();
  await page.locator('.login-panel form').evaluate(form => {
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await expect.poll(() => submissions).toBe(1);
  release();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
  expect(submissions).toBe(1);
});

test('login failure preserves fields and supports retry', async ({ page }) => {
  await mockLogin(page);
  let fail = true;
  await page.route('**/api/v1/auth/login', route => fail ? route.fulfill({ status: 503, json: unavailable }) : route.fulfill({ json: actor }));
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill('review@example.com');
  await page.getByLabel('密码', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('连接暂时中断');
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('test-password');
  fail = false;
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/recruitment-analysis$/);
});

test('login intermediate breakpoint keeps the logo navigation and layout usable', async ({ page }) => {
  await mockLogin(page);
  await page.setViewportSize({ width: 985, height: 900 });
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  await expect(page.getByRole('button', { name: '返回品牌首页' })).toBeVisible();
  await expect(page.locator('.login-back')).toHaveCount(0);
});

test('homepage keeps native keyboard login activation', async ({ page }) => {
  await mockLogin(page);
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('让人才与机会在这里相遇');
  const login = page.getByRole('button', { name: '登录工作台' });
  await login.focus();
  await expect(login).toBeFocused();
  await login.press('Space');
  await expect(page).toHaveURL(/\/login$/);
});

test('talent list and detail show recoverable errors', async ({ page }) => {
  await mockWorkspace(page);
  let fail = true;
  await page.route('**/api/v1/candidates?*', route => route.fulfill(fail ? { status: 503, json: unavailable } : { json: emptyPage }));
  await page.goto('/talents');
  await expect(page.getByRole('button', { name: /^重\s*试$/ })).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: /^重\s*试$/ }).click();
  await expect(page.getByRole('button', { name: /^重\s*试$/ })).toHaveCount(0);
  await page.route('**/api/v1/candidates/missing', route => route.fulfill({ status: 503, json: unavailable }));
  await page.goto('/talents?candidate=missing');
  await expect(page.getByRole('button', { name: '重试候选人详情' })).toBeVisible();
  await expect(page.getByText('正在读取候选人详情')).toHaveCount(0);
});

test('Sunday belongs to the current Monday-to-Sunday calendar week', async ({ page }) => {
  await mockWorkspace(page);
  await page.clock.setFixedTime(new Date('2026-09-06T12:00:00+08:00'));
  const interview = { stepCode: 'INTERVIEW_ROUND_1', scheduledStartAt: '2026-09-06T06:00:00Z', scheduledEndAt: '2026-09-06T07:00:00Z', interviewType: 'INTERNAL', meetingMode: 'ONLINE' };
  await page.route('**/api/v1/interviews?*', route => route.fulfill({ json: { ...emptyPage, items: [
    { ...interview, id: 'active', candidateId: 'calendar-person', candidateName: '有效排期候选人', jdTitle: '前端工程师', status: 'SCHEDULED' },
    { ...interview, id: 'cancelled', candidateId: '已取消候选人', candidateName: '已取消候选人', jdTitle: '前端工程师', status: 'CANCELLED' },
  ] } }));
  await page.route('**/api/v1/interviews/active/materials', route => route.fulfill({ json: { interviewId: 'active', candidateId: 'calendar-person', candidateName: '有效排期候选人', jdTitle: '前端工程师' } }));
  await page.goto('/schedule');
  await expect(page.locator('.schedule-range-label')).toContainText('8 月 31 日 — 9 月 6 日');
  await expect(page.locator('.interview-event')).toHaveCount(2);
  await expect(page.locator('.interview-event.cancelled')).toHaveCount(1);
  await expect(page.locator('.interview-event.scheduled')).toContainText('有效排期候选人');
  await expect(page.getByRole('region', { name: '所选周面试日历' })).toBeVisible();
});

test('candidate detail switches candidates without removing drawer exit motion', async ({ page }) => {
  await mockWorkspace(page);
  for(const endpoint of ['candidate-pool-statuses','candidate-resume-summaries','candidate-batch']) await page.route(`**/api/v1/${endpoint}?*`,route=>route.fulfill({json:[]}));
  const candidates = ['甲', '乙'].map((fullName, index) => ({ id: `candidate-${index}`, fullName, ownerUserId: actor.userId, createdAt: '2026-09-01T00:00:00Z' }));
  await page.route('**/api/v1/candidates?*', route => route.fulfill({ json: { ...emptyPage, items: candidates } }));
  for (const candidate of candidates) {
    await page.route(`**/api/v1/candidates/${candidate.id}`, route => route.fulfill({ json: candidate }));
    await page.route(`**/api/v1/candidates/${candidate.id}/resume-versions?*`, route => route.fulfill({ json: { ...emptyPage, items: [2, 1].map(versionNo => ({ id: `${candidate.id}-v${versionNo}`, versionNo, originalFileName: `${candidate.fullName}-${versionNo}.pdf`, createdAt: '2026-09-01T00:00:00Z' })) } }));
  }
  const factsRequests: string[] = [];
  await page.route('**/api/v1/resume-versions/*/facts', route => {
    factsRequests.push(new URL(route.request().url()).pathname);
    return route.fulfill({ status: 404, json: { ...unavailable, status: 404, code: 'NOT_FOUND' } });
  });
  await page.route('**/api/v1/talent-search?*',route=>route.fulfill({json:{...emptyPage,items:candidates.map(candidate=>({candidateId:candidate.id,fullName:candidate.fullName,degrees:[],skills:[],conditionMatches:[]}))}}));
  await page.goto('/shared-library');
  await page.getByRole('button', { name: '查看简历' }).first().click();
  const drawer = page.getByRole('dialog');
  await expect(drawer.getByRole('button', { name: '查看面试材料', exact: true })).toBeVisible();
  await expect.poll(() => factsRequests.at(-1)).toContain('candidate-0-v2');
  const drawerNode = await page.locator('.workspace-dialog:visible').elementHandle();
  await page.keyboard.press('Escape');
  await expect(drawer).not.toBeVisible();
  // Ant Design keeps the closed detail dialog mounted so its leave transition can finish.
  expect(await drawerNode!.evaluate(node => node.isConnected)).toBe(true);
  await page.getByRole('button', { name: '查看简历' }).nth(1).click();
  await expect(drawer).toHaveAccessibleName('乙');
  await expect.poll(() => factsRequests.at(-1)).toContain('candidate-1-v2');
});

test('keyboard scrolling and reduced motion remain usable', async ({ page }) => {
  await mockWorkspace(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/schedule');
  const calendar = page.getByRole('region', { name: '所选周面试日历' });
  await calendar.focus();
  await expect(calendar).toBeFocused();
  await page.keyboard.press('ArrowRight');
  await expect(calendar).toBeFocused();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
  await page.goto('/talents');
  const header = page.getByRole('row', { name: '简历库表头，可横向滚动' });
  await header.focus();
  await expect(header).toBeFocused();
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => page.locator('.talent-ledger-card .ant-table-content').evaluate(node => node.scrollLeft)).toBeGreaterThan(0);
  await mockLogin(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  const login = page.getByRole('button', { name: '登录工作台' });
  expect(await login.evaluate(node => parseFloat(getComputedStyle(node).transitionDuration))).toBeLessThan(0.01);
  await login.press('Space');
  await expect(page).toHaveURL(/\/login$/);
  const duration = await page.locator('.login-panel form').evaluate(node => parseFloat(getComputedStyle(node).animationDuration));
  expect(duration).toBeLessThan(0.01);
});

import { expect, test, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const empty = { items: [], hasMore: false, nextCursor: null };
const candidates = ['林雨桐', '陈亦安'].map((fullName, index) => ({ id: `motion-${index}`, fullName, primaryEmail: `motion${index}@example.com`, ownerUserId: 'motion-user', createdAt: '2026-09-03T03:00:00Z' }));

async function workspace(page: Page, restricted = false) {
  await page.clock.setFixedTime(new Date('2026-09-05T12:00:00+08:00'));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = ['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/candidate-pool-statuses', '/candidate-resume-summaries', '/applications/stage-counts'].includes(path) ? [] : empty;
    if (path === '/admin/users' || path === '/directory/users/resolve') body = [];
    if (path.endsWith('/company-blacklist-records')) body = { current: null, active: false, records: empty };
    if (path === '/me') body = { userId: 'motion-user', displayName: '招聘负责人', workEmail: 'motion@example.com', roles: [], permissions: ['talent.read', 'recruitment.manage'], superAdmin: !restricted, mustChangePassword: false, version: '1' };
    if (path === '/candidates') body = { ...empty, items: candidates };
    if (candidates.some(candidate => path === `/candidates/${candidate.id}`)) body = candidates.find(candidate => path === `/candidates/${candidate.id}`);
    return route.fulfill({ json: body });
  });
}

async function capture(page: Page, name: string) {
  if (!process.env.CAPTURE_MOTION) return;
  await mkdir('test-results/review/2026-09-05-motion', { recursive: true });
  await page.screenshot({ path: `test-results/review/2026-09-05-motion/${name}.png`, animations: 'disabled' });
  if (process.env.HR_QA_AXE_PATH) {
    const { default: AxeBuilder } = await import(pathToFileURL(resolve(process.env.HR_QA_AXE_PATH)).href);
    const audit = new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']);
    if (await page.getByRole('dialog').count()) audit.include('.workspace-dialog');
    else audit.include('.app-header').include('.mobile-bottom-nav');
    const result = await audit.analyze();
    await writeFile(`test-results/review/2026-09-05-motion/${name}-axe.json`, JSON.stringify(result.violations, null, 2));
    expect(result.violations).toEqual([]);
  }
}

async function expectSelectedSurface(page: Page) {
  const nav = page.locator('.workspace-nav:visible');
  await expect.poll(() => nav.evaluate(node => {
    const selected = node.querySelector('[aria-current="page"]')!.getBoundingClientRect();
    const surface = node.querySelector('.workspace-nav-indicator')!.getBoundingClientRect();
    return Math.max(Math.abs(selected.x - surface.x), Math.abs(selected.y - surface.y), Math.abs(selected.width - surface.width), Math.abs(selected.height - surface.height));
  })).toBeLessThanOrEqual(1);
}

test('skip entry is absent even before JavaScript and styles load', async ({ browser, baseURL }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.route('**/*.css', route => route.abort());
  await page.goto(`${baseURL}/login`);
  await expect(page.getByRole('link', { name: /跳.*主要内容/ })).toHaveCount(0);
  await expect(page.locator('body')).not.toContainText('跳到主要内容');
  await context.close();
});

test('selected navigation tracks the actual route through resize and filtered permissions', async ({ page }, info) => {
  await workspace(page, true);
  await page.goto('/recruitment');
  await expect(page.getByRole('heading', { name: '招聘流程' })).toBeVisible();
  await expect(page.locator('.workspace-nav:visible button')).toHaveCount(4);
  await expectSelectedSurface(page);
  await page.locator('.workspace-nav:visible').getByRole('button', { name: '我的候选人' }).click();
  await expect(page.locator('main')).toBeFocused();
  for (const width of [390, 768, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    await expectSelectedSurface(page);
    await expect(page.locator('.workspace-nav:visible [aria-current="page"]')).toHaveText('我的候选人');
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    if (info.project.name === 'desktop' && [390, 1440].includes(width)) await capture(page, `navigation-${width}`);
  }
  await page.locator('.workspace-nav:visible').getByRole('button', { name: '招募管道' }).hover();
  await expectSelectedSurface(page);
  await expect(page.locator('.workspace-nav:visible [aria-current="page"]')).toHaveText('我的候选人');
  await page.goBack();
  await expect(page.locator('.workspace-nav:visible [aria-current="page"]')).toHaveText('招募管道');
  await expectSelectedSurface(page);
});

test('candidate detail keeps its identity through exit and returns focus to the source', async ({ page }, info) => {
  await workspace(page);
  await page.goto('/talents');
  if (info.project.name === 'mobile') {
    const table = page.locator('.talent-ledger-card .ant-table-content');
    await page.getByRole('row', { name: '简历库表头，可横向滚动' }).press('ArrowRight');
    await expect.poll(() => table.evaluate(node => node.scrollLeft)).toBeGreaterThan(0);
  }
  const trigger = page.getByRole('button', { name: '详情与版本' }).first();
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: '林雨桐' });
  await expect(dialog).toContainText('motion0@example.com');
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(enter|appear)/);
  await capture(page, `candidate-${info.project.name}`);
  await dialog.evaluate(node => {
    node.addEventListener('animationstart', event => {
      if ((event as AnimationEvent).animationName === 'workspace-detail-out') document.body.dataset.closingIdentity = node.textContent ?? '';
    });
  });
  await dialog.press('Escape');
  await expect(page.locator('body')).toHaveAttribute('data-closing-identity', /林雨桐.*motion0@example.com/s);
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await page.getByRole('button', { name: '详情与版本' }).nth(1).press('Enter');
  await expect(page.getByRole('dialog', { name: '陈亦安' })).toContainText('motion1@example.com');
});

test('candidate controls and dialogs respect reduced motion', async ({ page }, info) => {
  await workspace(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/talents');
  const detail = page.getByRole('button', { name: '详情与版本' }).first();
  expect((await detail.boundingBox())!.height).toBeGreaterThanOrEqual(info.project.name === 'mobile' ? 44 : 32);
  await detail.press('Enter');
  const dialog = page.getByRole('dialog', { name: '林雨桐' });
  await expect(dialog).toBeVisible();
  expect(Number.parseFloat(await dialog.evaluate(node => getComputedStyle(node).animationDuration))).toBeLessThan(.02);
  await dialog.press('Escape');
  await expect(detail).toBeFocused();
});

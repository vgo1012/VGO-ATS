import { expect, test, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'stable-person', fullName: '林雨桐', primaryEmail: 'stable@example.com', ownerUserId: 'stable-user', createdAt: '2026-09-03T03:00:00Z' };
type Frame = { opacity: number; maskOpacity: number; className: string; x: number; y: number; width: number; height: number };
declare global { interface Window { dialogFrames: Frame[]; recordDialog: boolean } }

async function capture(page: Page, name: string, evidence?: unknown) {
  if (!process.env.CAPTURE_DIALOG_STABILITY) return;
  const directory = 'test-results/review/2026-09-05-dialog-stability';
  await mkdir(directory, { recursive: true });
  await page.screenshot({ path: `${directory}/${name}-${test.info().project.name}.png` });
  if (evidence) await writeFile(`${directory}/${name}-${test.info().project.name}.json`, JSON.stringify(evidence, null, 2));
}

async function workspace(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = empty;
    if (['/admin/users', '/directory/users/resolve', '/candidate-pool-statuses', '/candidate-resume-summaries', '/applications/stage-counts'].includes(path)) body = [];
    if (path === '/me') body = { userId: 'stable-user', displayName: '招聘负责人', workEmail: 'stable@example.com', roles: [], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };
    if (path === '/candidates') body = { ...empty, items: [person] };
    if (path === `/candidates/${person.id}`) body = person;
    if (path.endsWith('/company-blacklist-records')) body = {current:null,active:false,records:empty};
    return route.fulfill({ json: body });
  });
}

async function recordOpening(page: Page) {
  await page.evaluate(() => {
    window.dialogFrames = [];
    window.recordDialog = true;
    const sample = () => {
      const dialog = document.querySelector<HTMLElement>('.workspace-dialog');
      const mask = document.querySelector('.ant-modal-mask');
      if (dialog && getComputedStyle(dialog).display !== 'none') {
        const rect = dialog.getBoundingClientRect();
        window.dialogFrames.push({ opacity: Number(getComputedStyle(dialog).opacity), maskOpacity: mask ? Number(getComputedStyle(mask).opacity) : 0, className: dialog.className, x: rect.x, y: rect.y, width: rect.width, height: rect.height });
      }
      if (window.recordDialog) requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
}

test('every opening fades in once without a full-size flash or opacity reversal', async ({ page }) => {
  await workspace(page);
  await page.goto('/admin/source-channels');
  for (let attempt = 0; attempt < 2; attempt++) {
    await recordOpening(page);
    await page.getByRole('button', { name: '新建来源渠道', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '新建来源渠道' });
    await expect(dialog).toBeVisible();
    await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
    const frames = await page.evaluate(() => { window.recordDialog = false; return window.dialogFrames; });
    expect(frames.length).toBeGreaterThan(2);
    for (const frame of frames.filter(frame => /motion-(appear|enter)-(prepare|start)/.test(frame.className))) expect(frame.opacity).toBe(0);
    for (let index = 1; index < frames.length; index++) {
      expect(frames[index]!.opacity).toBeGreaterThanOrEqual(frames[index - 1]!.opacity - .01);
      expect(frames[index]!.maskOpacity).toBeGreaterThanOrEqual(frames[index - 1]!.maskOpacity - .01);
    }
    if (attempt === 0) await capture(page, 'form', frames);
    await dialog.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: '新建来源渠道', exact: true })).toBeFocused();
  }
});

test('candidate shell stays fixed as identity, versions and facts arrive separately', async ({ page }) => {
  await workspace(page);
  let releaseIdentity!: () => void;
  let releaseVersions!: () => void;
  const identity = new Promise<void>(resolve => { releaseIdentity = resolve; });
  const versions = new Promise<void>(resolve => { releaseVersions = resolve; });
  await page.route(`**/api/v1/candidates/${person.id}`, async route => { await identity; await route.fulfill({ json: person }); });
  await page.route(`**/api/v1/candidates/${person.id}/resume-versions?*`, async route => {
    await versions;
    await route.fulfill({ json: { ...empty, items: [{ id: 'stable-version', versionNo: 1, originalFileName: '林雨桐.pdf', createdAt: '2026-09-03T03:00:00Z' }] } });
  });
  await page.route('**/api/v1/resume-versions/stable-version/facts', route => route.fulfill({ status: 404, json: { status: 404, code: 'NOT_FOUND', detail: '当前尚无解析事实' } }));
  await page.goto('/talents?view=pool');
  const trigger = page.getByRole('button', { name: '详情与版本' });
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
  const initial = (await dialog.boundingBox())!;
  await capture(page, 'candidate-loading', initial);
  const expectStableShell = async () => {
    const rect = (await dialog.boundingBox())!;
    for (const dimension of ['x', 'y', 'width', 'height'] as const) expect(Math.abs(rect[dimension] - initial[dimension])).toBeLessThanOrEqual(1);
  };
  releaseIdentity();
  await expect(dialog).toHaveAccessibleName('林雨桐');
  await expect(dialog).toContainText('stable@example.com');
  await expectStableShell();
  releaseVersions();
  await expect(dialog).toContainText('当前版本尚无有效事实快照');
  await expectStableShell();
  await capture(page, 'candidate-ready', await dialog.boundingBox());
  await dialog.press('Escape');
  await expect(trigger).toBeFocused();
  await trigger.click();
  await expect(dialog).not.toHaveClass(/workspace-dialog-motion-(appear|enter)/);
  await expectStableShell();
});

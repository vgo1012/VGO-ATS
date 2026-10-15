import { expect, test, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { reviewWorkspace } from './fixtures/ux-review';
import AxeBuilder from '@axe-core/playwright';

async function downloadWorkspace(page: Page) {
  await reviewWorkspace(page);
  await page.route('**/api/v1/candidates/person-a/resume-versions?*', route => route.fulfill({ json: { items: [{ id: 'resume-a', versionNo: 1, originalFileName: '林雨桐-简历.pdf', createdAt: '2026-09-07T01:00:00Z' }], hasMore: false } }));
  await page.route('**/api/v1/resume-versions/resume-a/facts', route => route.fulfill({ status: 404, json: {} }));
  await page.route('**/api/v1/resume-versions/resume-a/download-authorizations', route => route.fulfill({ json: { token: 'grant-a' } }));
}

for (const kind of ['resume'] as const) {
  test(`${kind} blocks duplicate clicks and recovers in place from an expired download`, async ({ page, context }) => {
    await downloadWorkspace(page);
    let requests = 0, grants = 0, fail = true;
    let release: () => void = () => {};
    const gate = new Promise<void>(resolve => { release = resolve; });
    await page.route('**/api/v1/resume-versions/resume-a/download-authorizations', route => { grants++; return route.fulfill({ json: { token: `grant-${grants}` } }); });
    await page.route('**/api/v1/download-authorizations/*', async route => {
      requests++;
      await gate;
      await route.fulfill(fail ? { status: 410, json: { title: '下载失败', status: 410, code: 'DOWNLOAD_EXPIRED', detail: '下载已过期，请重试获取文件。', recoverable: true } } : { body: 'retried file', headers: { 'content-disposition': 'attachment' } });
    });
    const button = await openDownload(page);
    const url = page.url(), count = context.pages().length;
    let downloads = 0;
    page.on('download', () => { downloads++; });
    await button.evaluate(element => { (element as HTMLButtonElement).click(); (element as HTMLButtonElement).click(); });
    await expect(button).toBeDisabled();
    await expect.poll(() => requests).toBe(1);
    expect(grants).toBe(1);
    release();
    await expect(page.getByText('下载已过期，请重试获取文件。', { exact: true })).toBeVisible();
    await expect(button).toBeEnabled();
    expect(downloads).toBe(0);
    expect(page.url()).toBe(url);
    expect(context.pages()).toHaveLength(count);
    fail = false;
    const saved = page.waitForEvent('download');
    await button.click();
    await saved;
    expect(requests).toBe(2);
    expect(grants).toBe(2);
    await expect(button).toBeEnabled();
    expect((await new AxeBuilder({ page }).include('.ant-modal .ant-table-wrapper').analyze()).violations).toEqual([]);
  });
}

for (const kind of ['resume'] as const) {
  test(`${kind} saves a file without any window open or page navigation`, async ({ page, context }) => {
    await downloadWorkspace(page);
    const body = '%PDF-1.4\ncontrolled download fixture';
    await page.route('**/api/v1/download-authorizations/grant-a', route => route.fulfill({ body, headers: { 'content-type': 'application/pdf', 'content-disposition': 'attachment' } }));
    await page.addInitScript(() => { window.open = () => { throw new Error('下载不得打开新页面'); }; });
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    const button = await openDownload(page);
    const url = page.url(), pages = context.pages().length;
    const saved = page.waitForEvent('download', { timeout: 7000 });
    await button.click();
    const download = await saved;
    expect(download.suggestedFilename()).toBe('林雨桐-简历.pdf');
    expect(await readFile((await download.path())!, 'utf8')).toBe(body);
    expect(page.url()).toBe(url);
    expect(context.pages()).toHaveLength(pages);
    expect(errors).toEqual([]);
  });
}

async function openDownload(page: Page) {
  await page.goto('/shared-library?candidate=person-a');
  return page.getByRole('dialog', { name: '林雨桐', exact: true }).locator('button').filter({ hasText: '受控下载' });
}

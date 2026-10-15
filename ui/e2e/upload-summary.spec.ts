import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('summarizes a large upload while keeping failures and successful files scrollable', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/source-channels?*', route => route.fulfill({ json: {
    items: [{ id: 'channel-a', name: '手工上传', status: 'ACTIVE' }], hasMore: false, nextCursor: null,
  } }));
  const intake = (id: string) => ({ id, status: 'PENDING_IMPORT', importStatus: 'PENDING', scanStatus: 'CLEAN', jdVersionId: 'jd-v2', version: '0' });
  let uploaded = 0;
  await page.route('**/api/v1/intake-batches', route => route.fulfill({ json: { id: 'batch-summary' } }));
  await page.route('**/api/v1/intake-batches/*/files', route => {
    if (route.request().postDataBuffer()!.toString().includes('failed.pdf')) {
      return route.fulfill({ status: 503, json: { title: '上传失败', status: 503, detail: '文件上传暂不可用，请重试', code: 'UNAVAILABLE', recoverable: true } });
    }
    return route.fulfill({ json: intake(`accepted-${++uploaded}`) });
  });
  await page.route('**/api/v1/intakes/*', route => route.fulfill({ json: intake(new URL(route.request().url()).pathname.split('/').at(-1)!) }));
  await page.route('**/api/v1/intakes/*/import', route => route.fulfill({ json: { ...intake('imported'), importStatus: 'PROCESSING' } }));
  await page.goto('/talents');
  await page.getByRole('button', { name: '批量上传', exact: true }).click();
  const drawer = page.getByRole('dialog', { name: '批量导入简历' });
  await drawer.getByRole('combobox', { name: '上传来源渠道' }).click();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await drawer.getByRole('combobox', { name: '选择已解析成功的 JD' }).click();
  await page.getByRole('option').filter({ hasText: '高级前端工程师' }).click();
  const names = Array.from({ length: 26 }, (_, index) => `【AI工程师_杭州_招聘平台与复杂业务系统方向】候选人${index + 1}_完整简历与项目经历.pdf`);
  await drawer.locator('input[type=file]').setInputFiles([...names, 'failed.pdf'].map(name => ({
    name, mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 fixture'),
  })));
  await drawer.getByRole('button', { name: '开始导入', exact: true }).click();
  await expect(drawer.getByRole('button', { name: /开始导入/ })).toBeEnabled();
  await expect(drawer.locator('.ant-upload-list-item')).toHaveCount(1);
  await page.mouse.move(0, 0);
  const summary = drawer.locator('.upload-result-summary');
  await expect(summary).toContainText('本次上传结果');
  await expect(drawer.getByRole('group', { name: '成功 0，失败 1，解析中 26' })).toBeVisible();
  const results = drawer.getByRole('list', { name: '本次上传文件结果' });
  await expect(results.getByRole('listitem')).toHaveCount(27);
  await expect(results.getByRole('listitem').filter({ hasText: 'failed.pdf' })).toContainText('failed.pdf：上传失败');
  await expect(results.getByRole('listitem').filter({ hasText: names[25]! })).toContainText(`${names[25]}：解析中`);
  await summary.scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath('upload-summary.png') });
  expect(await results.evaluate(node => node.scrollHeight > node.clientHeight && ['auto', 'scroll'].includes(getComputedStyle(node).overflowY))).toBe(true);
  expect(await drawer.evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  expect(await results.evaluate(node => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(1);
  await results.focus();
  await page.keyboard.press('End');
  await expect.poll(() => results.evaluate(node => node.scrollTop)).toBeGreaterThan(0);
  await page.screenshot({ path: testInfo.outputPath('upload-results-scrolled.png') });
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  expect(errors).toEqual([]);
});

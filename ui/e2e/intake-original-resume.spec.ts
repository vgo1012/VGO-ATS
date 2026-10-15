import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('解析收件直接查看本次原文件，支持重试下载并保持桌面和窄屏焦点', async ({ page }, testInfo) => {
  await reviewWorkspace(page);
  const empty = { items: [], hasMore: false, nextCursor: null };
  const base = { batchId: 'batch', sourceChannelId: 'source', ownerUserId: 'hr-a', mediaType: 'application/pdf', sizeBytes: 600, scanStatus: 'CLEAN', version: 1, createdAt: '2026-09-09T01:00:00Z', jdVersionId: 'jd-v2', jdTitle: 'AI 工程师' };
  const failed = { ...base, id: 'failed', originalFileName: '待核查的本次上传原始简历.pdf', status: 'FAILED_RETRYABLE', importStatus: 'PENDING', failureCode: 'INTAKE_COMPLETION_FAILED', resumeVersionId: null };
  const completed = { ...base, id: 'completed', originalFileName: '已入库原始简历.pdf', status: 'COMPLETED', importStatus: 'IMPORTED', failureCode: null, resumeVersionId: 'different-version', candidateId: 'candidate', scoreStatus: 'SUCCEEDED', scoreRunId: 'score' };
  const quarantined = { ...base, id: 'quarantined', originalFileName: '隔离文件.pdf', status: 'QUARANTINED', importStatus: 'PENDING', scanStatus: 'MALICIOUS' };
  const requests: string[] = [];
  let failNext = false;
  const pdf = Buffer.from('JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA0MDAgNTAwXSAvUmVzb3VyY2VzIDw8IC9Gb250IDw8IC9GMSA0IDAgUiA+PiA+PiAvQ29udGVudHMgNSAwIFIgPj4KZW5kb2JqCjQgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNhID4+CmVuZG9iago1IDAgb2JqCjw8IC9MZW5ndGggNTMgPj4Kc3RyZWFtCkJUIC9GMSAxNiBUZiAzMCA0NDAgVGQgKEJvdW5kIHJlc3VtZSB2ZXJzaW9uIDIpIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTggMDAwMDAgbiAKMDAwMDAwMDExNSAwMDAwMCBuIAowMDAwMDAwMjQxIDAwMDAwIG4gCjAwMDAwMDAzMTEgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA2IC9Sb290IDEgMCBSID4+CnN0YXJ0eHJlZgo0MTQKJSVFT0YK', 'base64');
  await page.route('**/api/v1/intake-batches/batch', route => route.fulfill({ json: { id: 'batch', ...base, status: 'COMPLETED_WITH_ERRORS', totalCount: 3, succeededCount: 3, failedCount: 0, completedCount: 1, processingCount: 0, attentionCount: 2, ignoredCount: 0 } }));
  await page.route('**/api/v1/intakes?**', route => route.fulfill({ json: { ...empty, items: [completed, failed, quarantined] } }));
  await page.route('**/api/v1/intakes/count?**', route => route.fulfill({ json: { count: 3, hasMore: false, nextCursor: null } }));
  await page.route('**/api/v1/intakes/*/file', route => {
    const id = new URL(route.request().url()).pathname.split('/').at(-2)!;
    requests.push(id);
    if (failNext) {
      failNext = false;
      return route.fulfill({ status: 503, json: { status: 503, title: '读取失败', detail: '存储暂时不可用，请重试', code: 'OBJECT_STORAGE_READ_FAILED', recoverable: true } });
    }
    return route.fulfill({ contentType: 'application/pdf', headers: { 'cache-control': 'no-store', 'content-disposition': 'attachment' }, body: pdf });
  });

  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/settings/intakes?batchId=batch&importStatus=ALL');
    const drawer = page.getByRole('dialog', { name: '解析校验', exact: true });
    const importedRow = drawer.getByRole('row').filter({ hasText: completed.originalFileName });
    const failedRow = drawer.getByRole('row').filter({ hasText: failed.originalFileName });
    const trigger = failedRow.getByRole('button', { name: '查看原始简历' });
    await expect(drawer.getByRole('row').filter({ hasText: quarantined.originalFileName }).getByRole('button', { name: '查看原始简历' })).toBeDisabled();
    await expect(importedRow.getByRole('button', { name: '查看原始简历' })).toBeEnabled();
    await trigger.scrollIntoViewIfNeeded();
    const bounds = await trigger.boundingBox(), cell = await trigger.locator('xpath=ancestor::td').boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(cell!.x);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(cell!.x + cell!.width);
    await page.screenshot({ path: testInfo.outputPath(`intake-original-actions-${width}.png`), animations: 'disabled' });
    failNext = true;
    await trigger.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: '原始简历', exact: true });
    await expect(dialog.getByRole('button', { name: '重试预览' })).toBeVisible();
    await expect(page.getByText('存储暂时不可用，请重试')).toBeVisible();
    await page.getByRole('button', { name: '关闭错误提示' }).click();
    await dialog.getByRole('button', { name: '重试预览' }).click();
    const preview = dialog.getByTitle(`${failed.originalFileName} 原始简历预览`);
    await expect(preview).toHaveAttribute('src', /^blob:/);
    expect((await preview.boundingBox())!.height).toBeGreaterThan(550);
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    // 内置 PDF 阅读器完成首帧绘制后留存视觉证据。
    await page.waitForTimeout(1000);
    await page.screenshot({ path: testInfo.outputPath(`intake-original-preview-${width}.png`), animations: 'disabled' });
    const download = page.waitForEvent('download');
    await dialog.getByRole('button', { name: '下载原文件' }).click();
    expect((await download).suggestedFilename()).toBe(failed.originalFileName);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    await importedRow.getByRole('button', { name: '查看原始简历' }).click();
    await expect(dialog.getByTitle(`${completed.originalFileName} 原始简历预览`)).toHaveAttribute('src', /^blob:/);
    await page.keyboard.press('Escape');
  }
  expect(requests).toEqual(['failed', 'failed', 'failed', 'completed', 'failed', 'failed', 'failed', 'completed']);
});

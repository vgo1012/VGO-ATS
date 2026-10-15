import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('intake next page stays still during polling and pagination',async({page})=>{
  await reviewWorkspace(page);
  let reads=0,release:(()=>void)|undefined;
  await page.route('**/api/v1/intakes?**',async route=>{
    reads++;
    if(reads===2||reads===3)await new Promise<void>(resolve=>{release=resolve;});
    const second=new URL(route.request().url()).searchParams.has('cursor');
    await route.fulfill({json:{items:[{id:second?'second':'first',originalFileName:second?'第二页.pdf':'第一页.pdf',ownerUserId:'hr-a',sourceChannelId:'source',sizeBytes:100,status:'PARSING',importStatus:'PROCESSING',jdVersionId:'jd-v2',jdTitle:'测试岗位',scanStatus:'CLEAN',version:1}],hasMore:!second,nextCursor:second?null:'next'}});
  });
  await page.goto('/settings/intakes');
  const next=page.locator('.intake-validation-page').getByRole('button',{name:'下一页'});
  await expect(page.getByText('第一页.pdf')).toBeVisible();
  await expect(next).toBeEnabled();
  await expect.poll(()=>reads).toBe(2);
  await expect(next).toBeDisabled();
  await expect(next).not.toHaveClass(/ant-btn-loading/);
  await expect(next.locator('.anticon-loading')).toHaveCount(0);
  release?.();
  await expect(next).toBeEnabled();
  await next.focus();await next.press('Enter');
  await expect.poll(()=>reads).toBe(3);
  await expect(next).toBeDisabled();
  await expect(next.locator('.anticon-loading')).toHaveCount(0);
  release?.();
  await expect(page.getByText('第二页.pdf')).toBeVisible();
  const pagination=page.getByRole('navigation',{name:'导入任务分页'});
  await expect(pagination.getByRole('spinbutton',{name:'页码',exact:true})).toHaveValue('2');
  await expect(next).toBeDisabled();
  await pagination.getByRole('button',{name:'首页',exact:true}).click();
  await expect(page.getByText('第一页.pdf')).toBeVisible();
  expect((await new AxeBuilder({page}).include('.intake-validation-page').analyze()).violations).toEqual([]);
});

for (const width of [390, 1280, 1440, 1920]) {
  test(`intake fixed actions stay inside the scroll region at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await reviewWorkspace(page);
    await page.route('**/api/v1/intakes?**', route => route.fulfill({ json: {
      items: Array.from({ length: 30 }, (_, index) => ({
        id: `intake-${index}`, originalFileName: `售后工程师候选人-${index}.pdf`, ownerUserId: 'hr-a',
        sourceChannelId: 'source', sizeBytes: 100, status: 'COMPLETED', importStatus: 'IMPORTED',
        jdVersionId: 'jd-v2', jdTitle: '售后工程师', scanStatus: 'CLEAN', version: 1,
      })), hasMore: true, nextCursor: 'next',
    } }));
    await page.goto('/settings/intakes');
    const table = page.locator('.intake-validation-page .ant-table-content');
    await expect(table.getByText('售后工程师候选人-0.pdf')).toBeVisible();
    const controls = page.locator('.intake-validation-page .data-page-controls');
    await controls.scrollIntoViewIfNeeded();
    for (const ratio of [0, 0.13, 0.37, 0.5, 0.73, 1]) {
      await page.locator('.intake-drawer .ant-drawer-body').evaluate((element, ratio) => { element.scrollTop = (element.scrollHeight - element.clientHeight) * ratio; }, ratio);
      await table.evaluate((element, ratio) => { element.scrollTop = (element.scrollHeight - element.clientHeight) * ratio; element.scrollLeft = element.scrollWidth; }, ratio);
      await expect.poll(() => controls.evaluate(element => {
        const box = element.getBoundingClientRect();
        for (let x = box.left + 5; x < box.right; x += 10) {
          for (let y = box.top + 2; y < Math.min(box.bottom, innerHeight); y += 5) {
            if (document.elementFromPoint(x, y)?.closest('td')) return false;
          }
        }
        return true;
      })).toBe(true);
    }
    await expect(page.locator('.intake-validation-page').getByRole('button', { name: '下一页', exact: true })).toBeEnabled();
    await page.screenshot({ path: testInfo.outputPath(`intake-actions-${width}.png`) });
  });
}

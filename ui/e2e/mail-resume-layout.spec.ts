import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const attachmentName = `【运维工程师_上海_8-9K】测试候选人_${'云平台与网络基础设施项目经验_'.repeat(5)}.pdf`;
const review = {
  receivedAt: '2026-10-10T04:13:00Z', sender: `${'recruitment-notification-'.repeat(3)}@example.com`,
  subject: `测试候选人 | 4年，应聘运维工程师 | ${'上海七宝云平台与网络基础设施_'.repeat(5)}【招聘邮件】`,
  attachmentName, parsedJdCode: 'JD-99099099999', parsedJdTitle: '运维工程师（云平台与网络基础设施方向）',
  parsedRegion: '上海七宝、北京、成都', failureCode: 'JD_EXACT_MATCH_NOT_FOUND',
  failureDetail: '岗位文本和区域未精确命中当前 JD，请人工核对岗位名称、工作地区与当前招聘需求。',
  status: 'REVIEW_REQUIRED', matchType: 'REVIEW_REQUIRED', jdId: null, jdVersionId: null, intakeId: null,
  linkedByUserId: null, linkedAt: null, version: '0',
};

for (const width of [1920, 1440, 1280, 768, 390]) {
  test(`待处理邮件长内容不串列，操作可用 ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 900 });
    await reviewWorkspace(page);
    await page.route('**/api/v1/mail-resume-reviews?**', route => route.fulfill({ json: {
      items: Array.from({ length: 12 }, (_, index) => ({
        ...review, id: `mail-${index}`,
        ...(index ? { sender: 'cv@example.com', subject: '测试候选人 | 4年，应聘运维工程师 | 上海8-9K【招聘邮件】', attachmentName: '【运维工程师_上海_8-9K】测试候选人.pdf' } : {}),
        ...(index === 1 ? { status: 'DISPATCH_FAILED', failureCode: 'INTAKE_DISPATCH_FAILED' } : {}),
        ...(index === 2 ? { sender: null, subject: null, attachmentName: null, parsedJdCode: null, parsedJdTitle: null, parsedRegion: null, failureCode: null, failureDetail: null } : {}),
      })),
      hasMore: false, nextCursor: null,
    } }));
    await page.route('**/api/v1/mail-resume-reviews/count', route => route.fulfill({ json: { count: 12, nextCursor: null, hasMore: false } }));
    await page.route('**/api/v1/mail-resume-jds?**', route => route.fulfill({ json: [] }));
    await page.goto('/admin/mail-resume-reviews');
    const filterOverflow = await page.locator('.mail-resume-filter-card').evaluate(card => {
      return [...card.querySelectorAll('.ant-form-item-control')].map(control => {
        const boundary = control.closest('.ant-form-item')!.getBoundingClientRect();
        const rect = control.getBoundingClientRect();
        return { label: control.parentElement?.textContent, left: rect.left, right: rect.right,
          boundary: { left: boundary.left, right: boundary.right } };
      }).filter(control => control.left < control.boundary.left - 1 || control.right > control.boundary.right + 1);
    });
    expect(filterOverflow, '筛选控件不得相互覆盖或超出卡片').toEqual([]);
    const table = page.locator('.mail-resume-results-card');
    const row = table.locator('tbody > tr[data-row-key]').first();
    await expect(row).toContainText(review.sender);
    await expect(table).toContainText('下发失败');
    await expect(table).toContainText('系统重试中');
    await expect(table).toContainText('未知发件人');
    await expect(table).toContainText('需要人工确认');
    await expect(table.locator('.ant-spin-blur')).toHaveCount(0);
    await row.scrollIntoViewIfNeeded();
    await expect(row).toBeInViewport();
    const content = table.locator('.ant-table-content');
    expect(await content.evaluate(element => element.clientHeight)).toBeGreaterThan(200);
    await page.screenshot({ path: info.outputPath(`mail-${width}.png`) });

    const overflow = await row.locator('td').evaluateAll(cells => cells.map(cell => ({
      text: cell.textContent, overflow: cell.scrollWidth - cell.clientWidth,
    })).filter(cell => cell.overflow > 1));
    expect(overflow, '单元格内容必须留在本列内').toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

    const code = row.getByText('待关联 JD', { exact: true });
    await code.focus();
    await expect(page.getByRole('tooltip')).toContainText('错误代码：JD_EXACT_MATCH_NOT_FOUND');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('tooltip')).not.toBeVisible();
    await content.evaluate(element => { element.scrollLeft = element.scrollWidth; });
    const action = row.getByRole('button', { name: '关联 JD' });
    await expect(action).toBeInViewport();
    expect(await action.evaluate(element => {
      const rect = element.getBoundingClientRect();
      return element.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2));
    })).toBe(true);
    await page.screenshot({ path: info.outputPath(`mail-${width}-scrolled.png`) });
    await action.focus();
    await page.keyboard.press('Enter');
    const modal = page.getByRole('dialog', { name: '人工关联当前 JD' });
    await expect(modal).toContainText(attachmentName);
    await expect(modal.getByRole('combobox', { name: '选择当前 JD' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(modal).not.toBeVisible();
    await expect(action).toBeFocused();

    await content.evaluate(element => { element.scrollTop = element.scrollHeight; });
    const header = table.getByRole('columnheader', { name: '操作', exact: true });
    expect(await header.evaluate(element => {
      const rect = element.getBoundingClientRect();
      return element.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2));
    })).toBe(true);
    const pagination = page.getByRole('navigation', { name: '邮件简历校验分页' });
    await pagination.scrollIntoViewIfNeeded();
    await expect(pagination.getByRole('spinbutton', { name: '每页条数' })).toBeVisible();
  });
}

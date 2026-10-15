import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const query of ['', '?pool_q=分页', '?view=todo', '?view=todo&todo_kind=recruitment']) {
  test(`candidate page size controls requests and resets cursors ${query || 'unfiltered'}`, async ({ page }, testInfo) => {
    await reviewWorkspace(page);
    const candidates = Array.from({ length: 42 }, (_, index) => ({ id: `size-${index + 1}`, fullName: `分页候选人${index + 1}`, ownerUserId: 'hr-a' }));
    const requests: URLSearchParams[] = [];
    for (const endpoint of ['candidates', 'talent-search']) {
      await page.route(`**/api/v1/${endpoint}?**`, route => {
        const params = new URL(route.request().url()).searchParams;
        requests.push(params);
        const start = Number(params.get('cursor') ?? 0);
        const end = start + Number(params.get('limit'));
        const items = candidates.slice(start, end);
        return route.fulfill({ json: { items: endpoint === 'candidates' ? items : items.map(item => ({ candidateId: item.id })), hasMore: end < candidates.length, nextCursor: end < candidates.length ? String(end) : null } });
      });
      await page.route(`**/api/v1/${endpoint}/count?**`, route => route.fulfill({ json: { count: 42, hasMore: false, nextCursor: null } }));
    }
    await page.route('**/api/v1/candidate-batch?**', route => {
      const ids = new URL(route.request().url()).searchParams.getAll('candidateIds');
      return route.fulfill({ json: candidates.filter(item => ids.includes(item.id)) });
    });
    await page.goto(`/talents${query}`);
    const footer = page.locator('.compact-pagination:visible');
    const rows = page.locator('.ant-table:visible tr[data-row-key]');
    await expect(rows).toHaveCount(20);
    await footer.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(footer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
    await expect(rows).toHaveCount(20);
    const size = footer.getByRole('spinbutton', { name: '每页条数' });
    await size.fill('30');
    await size.press('Enter');
    await expect(size).toHaveValue('30');
    await expect(rows).toHaveCount(30);
    await expect(footer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('1');
    expect(requests.at(-1)?.get('limit')).toBe('30');
    expect(requests.at(-1)?.has('cursor')).toBe(false);
    for (const invalid of ['0', '101', '2.5', '']) {
      const before = requests.length;
      await size.fill(invalid);
      await size.press('Tab');
      await expect(size).toHaveValue('30');
      expect(requests).toHaveLength(before);
    }
    await footer.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(rows).toHaveCount(12);
    expect(requests.at(-1)?.get('limit')).toBe('30');
    expect(requests.at(-1)?.get('cursor')).toBe('30');
    await page.screenshot({ path: testInfo.outputPath('page-size-30.png'), animations: 'disabled' });
  });
}

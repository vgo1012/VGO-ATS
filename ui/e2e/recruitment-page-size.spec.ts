import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for (const target of [
  { path: '/recruitment', label: '招聘流程分页', endpoint: 'applications' },
  { path: '/shared-library', label: '共享简历库分页', endpoint: 'talent-search' },
  { path: '/onboarded', label: '入职流程分页', endpoint: 'applications' },
]) test(`${target.path} changes page size to 30 and discards the old cursor`, async ({ page }) => {
  await reviewWorkspace(page);
  const requests: URLSearchParams[] = [];
  await page.route(`**/api/v1/${target.endpoint}?**`, route => {
    const params = new URL(route.request().url()).searchParams;
    requests.push(params);
    const limit = Number(params.get('limit'));
    const offset = Number(params.get('cursor') ?? 0);
    const items = Array.from({ length: Math.min(limit, 45 - offset) }, (_, index) => target.endpoint === 'talent-search'
      ? { candidateId: `person-${offset + index}`, fullName: `分页候选人${offset + index}`, skills: [], conditionMatches: [] }
      : { id: `app-${offset + index}`, candidateId: 'person-a', resumeVersionId: 'resume-a', jdId: 'jd-a', ownerUserId: 'hr-a', recruitmentType: 'INTERNAL', currentStage: target.path === '/onboarded' ? 'ONBOARDED' : 'PATH_SELECTION', updateTime: '2026-09-07T01:00:00Z' });
    return route.fulfill({ json: { items, hasMore: offset + limit < 45, nextCursor: offset + limit < 45 ? String(offset + limit) : null } });
  });
  await page.goto(target.path);
  const footer = page.getByLabel(target.label, { exact: true });
  const rows = page.locator('.paged-panel tr[data-row-key]');
  await expect(rows).toHaveCount(20);
  await footer.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(footer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('2');
  await expect.poll(() => requests.at(-1)?.get('cursor')).toBe('20');
  const size = footer.getByRole('spinbutton', { name: '每页条数', exact: true });
  await expect(size).toBeEnabled();
  await size.fill('30');
  await size.press('Enter');
  await expect.poll(() => requests.at(-1)?.get('limit')).toBe('30');
  expect(requests.at(-1)?.has('cursor')).toBe(false);
  await expect(size).toHaveValue('30');
  await expect(rows).toHaveCount(30);
  await expect(footer.getByRole('spinbutton', { name: '页码', exact: true })).toHaveValue('1');
  await footer.getByRole('button', { name: '下一页', exact: true }).click();
  await expect.poll(() => requests.at(-1)?.get('cursor')).toBe('30');
  await expect(rows).toHaveCount(15);
  expect(requests.at(-1)?.get('limit')).toBe('30');
});


for (const width of [1280,1920]) for (const count of [0,20]) test(`招聘表格无横向溢出 ${width}px ${count}行`,async({page},testInfo)=>{
  await page.setViewportSize({width,height:900});
  await reviewWorkspace(page);
  await page.route('**/api/v1/applications?*',route=>route.fulfill({json:{items:Array.from({length:count},(_,index)=>({id:`fit-${index}`,candidateId:'person-a',resumeVersionId:'resume-a',jdId:'jd-a',ownerUserId:'hr-a',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',updateTime:'2026-09-07T01:00:00Z'})),hasMore:false,nextCursor:null}}));
  await page.goto('/recruitment');
  const content=page.locator('.pipeline-table-card .ant-table-content');
  await expect(page.getByRole('navigation',{name:'招聘流程分页'})).toBeVisible();
  await expect(page.locator('.pipeline-table-card tr[data-row-key]')).toHaveCount(count);
  expect(await content.evaluate(el=>el.scrollWidth-el.clientWidth)).toBeLessThanOrEqual(1);
  const action=page.getByRole('columnheader',{name:'操作',exact:true});
  const box=(await action.boundingBox())!;
  expect(box.x+box.width).toBeLessThan(width);
  await page.screenshot({path:testInfo.outputPath('recruitment-no-horizontal-scroll.png'),animations:'disabled'});
});

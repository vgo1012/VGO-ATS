import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('旧待处置链接回到待筛选，旧阶段候选人仍可查找且不显示超时功能',async({page})=>{
  await reviewWorkspace(page);
  const requests:URLSearchParams[]=[];
  const person={id:'legacy',fullName:'历史未处理候选人',ownerUserId:'hr-a'};
  await page.route('**/api/v1/candidates?**',route=>{
    requests.push(new URL(route.request().url()).searchParams);
    return route.fulfill({json:{items:[person],hasMore:false,nextCursor:null}});
  });
  await page.route('**/api/v1/candidate-batch?**',route=>route.fulfill({json:[person]}));
  await page.route('**/api/v1/talent-search?**',route=>{
    requests.push(new URL(route.request().url()).searchParams);
    return route.fulfill({json:{items:[{candidateId:person.id}],hasMore:false,nextCursor:null}});
  });
  await page.route('**/api/v1/candidate-pool-statuses?**',route=>route.fulfill({json:[{
    candidateId:person.id,resumeVersionId:'resume-legacy',stages:[],jobContexts:[],
    lifecycle:{phase:'PENDING_DISPOSAL',warningLevel:'RED',elapsedWorkdays:20,enteredAt:'2026-08-01T00:00:00Z',shareDueAt:'2026-08-05T17:00:00Z',pauseReason:'CALENDAR_MISSING'},
  }]}));
  await page.goto('/talents?view=todo&todo_kind=recruitment&todo_q=忽略旧条件');
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:person.fullName,exact:true})).toBeVisible();
  await expect(page).toHaveURL(/\/talents\?view=pool$/);
  await expect(page.getByRole('tab',{name:'待处置'})).toHaveCount(0);
  await expect(page.getByRole('button',{name:/^(超时简历|招聘待办|查看招聘活动)$/})).toHaveCount(0);
  await expect(page.getByText(/红色预警|黄色预警|暂停流转/)).toHaveCount(0);
  const search=page.getByRole('textbox',{name:'搜索候选人',exact:true});
  await search.fill(person.fullName);await search.press('Enter');
  await expect.poll(()=>requests.at(-1)?.get('scope')).toBe('PERSONAL_POOL');
  expect(requests.every(params=>!params.has('poolStage')&&params.get('scope')!=='MY_TASKS')).toBe(true);
  await expect(page.getByRole('button',{name:person.fullName,exact:true})).toBeVisible();
  await page.reload();
  await expect(page.getByRole('form',{name:'简历筛选条件',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:person.fullName,exact:true})).toBeVisible();
  expect(new URL(page.url()).searchParams.get('pool_q')).toBe(person.fullName);
});

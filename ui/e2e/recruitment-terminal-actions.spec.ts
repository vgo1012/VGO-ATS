import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

const stamp='2026-09-22T08:00:00Z';

test('招聘终态仅保留状态与黑名单操作',async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/applications/count?**',route=>route.fulfill({json:{count:2,hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/applications?**',route=>route.fulfill({json:{items:[
    {id:'cancelled',candidateId:'person-a',resumeVersionId:'resume-cancelled',jdId:'jd-a',recruitmentType:'INTERNAL',currentStage:'TERMINATED',terminalAt:stamp,ownerUserId:'hr-a',updateTime:stamp},
    {id:'completed',candidateId:'person-a',resumeVersionId:'resume-completed',jdId:'jd-a',recruitmentType:'INTERNAL',currentStage:'ONBOARDED',terminalAt:stamp,ownerUserId:'hr-a',updateTime:stamp},
  ],hasMore:false,nextCursor:null}}));

  await page.goto('/recruitment');

  const cancelled=page.getByLabel('招聘已取消',{exact:true});
  await expect(cancelled.getByRole('button')).toHaveText(['放弃','黑名单']);
  await expect(cancelled.getByRole('button',{name:'放弃',exact:true})).toBeDisabled();
  await expect(cancelled.getByRole('button',{name:'黑名单',exact:true})).toBeEnabled();
  await expect(cancelled.locator('.recruitment-rounds')).toHaveCount(0);

  const completed=page.getByLabel('招聘已完成',{exact:true});
  await expect(completed).toContainText('已完成');
  await expect(completed).toContainText('已转入入职流程');
  await expect(completed.getByRole('button')).toHaveText(['黑名单']);
  await expect(completed.getByRole('button',{name:'黑名单',exact:true})).toBeEnabled();
  await expect(completed.locator('.recruitment-rounds')).toHaveCount(0);

  expect((await new AxeBuilder({page}).include('.recruitment-ledger').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('terminal-actions.png'),fullPage:true,animations:'disabled'});
});

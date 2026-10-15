import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const width of [390,1440])test(`匹配说明的点击和键盘打开状态不受鼠标离开影响 ${width}`,async({page},testInfo)=>{
  await page.setViewportSize({width,height:900});
  await reviewWorkspace(page);
  const text='数据中心运维：负责服务器、网络、故障排查与客户支持。'.repeat(12)+'完整说明结尾';
  const run={id:'run-a',taskId:'task-run-a',jdVersionId:'jd-v2',jdFactSnapshotId:'jd-fact',planVersionId:'company-v1',weightProfileVersionId:'weight-v1',personalPreferenceVersionId:null,selectionSnapshotId:'selection-a',sortMode:'REFERENCE_SCORE',status:'SUCCEEDED',totalCount:1,succeededCount:1,failedCount:0,errorCode:null,version:1,createdAt:'2026-09-07T01:00:00Z',startedAt:'2026-09-07T01:00:01Z',finishedAt:'2026-09-07T01:00:02Z',updatedAt:'2026-09-07T01:00:02Z'};
  const result={resultId:'result-a',baseEvaluationId:'base-a',candidateId:'person-a',resumeVersionId:'resume-a',stableRank:1,professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS',details:{jdFactSnapshotId:'jd-fact',resumeFactSnapshotId:'resume-fact',candidateId:'person-a',resumeVersionId:'resume-a',professionalScore:80,bonusScore:0,referenceScore:80,mustTier:'PASS',selectedBonusCap:0,dimensions:[{code:'CORE_SKILLS',enabled:true,configuredWeight:29,status:'KNOWN',score:80,explanation:'核心技能匹配',highlights:[text]}],criteria:[]}};
  await page.route('**/api/v1/match-runs/run-a',route=>route.fulfill({json:run}));
  await page.route('**/api/v1/match-runs/run-a/results?**',route=>route.fulfill({json:{items:[result],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/tasks/task-run-a',route=>route.fulfill({json:{taskId:'task-run-a',resourceId:'run-a',resourceType:'MATCH_RUN',taskType:'MATCH_RUN',status:'SUCCEEDED',attemptCount:1,maxAttempts:3,progressPercent:100,retryable:false,version:1,createdAt:'2026-09-07T01:00:00Z',updatedAt:'2026-09-07T01:00:02Z'}}));
  await page.route('**/api/v1/screening-candidates**',route=>route.fulfill({json:[]}));
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:[{candidateId:'person-a',candidateName:'林雨桐',resumeVersionId:'resume-a',libraryEligible:false,screenedOut:false,canScreenOut:true,stages:[],jobContexts:[]}]}));
  const timeline:Array<{step:string;expanded:string|null;visible:boolean;active:string|null}>=[];
  const trigger=page.getByRole('button',{name:'查看完整匹配说明'});
  const panel=page.getByRole('region',{name:'完整匹配说明'});
  const record=async(step:string)=>timeline.push({step,expanded:await trigger.getAttribute('aria-expanded'),visible:await panel.isVisible(),active:await page.evaluate(()=>document.activeElement?.textContent?.trim().slice(0,24)??null)});
  const settle=()=>page.locator('.ant-popover').evaluateAll(async elements=>{await Promise.all(elements.flatMap(element=>element.getAnimations({subtree:true})).map(animation=>animation.finished.catch(()=>undefined)));});

  try {
  await page.goto('/matching?run=run-a');await expect(trigger).toBeVisible();
  await trigger.hover();await expect(panel).toBeVisible();await record('hover-open');
  await page.mouse.move(1,1);await page.waitForTimeout(300);await expect(panel).not.toBeVisible();await expect(trigger).toHaveAttribute('aria-expanded','false');await record('hover-leave-close');
  await trigger.hover();await expect(panel).toBeVisible();await trigger.click();await page.waitForTimeout(300);await settle();
  await expect(panel,'悬停预览时点击应固定打开').toBeVisible();await expect(trigger).toHaveAttribute('aria-expanded','true');await record('hover-then-click-pins');
  await page.mouse.move(1,1);await page.waitForTimeout(300);await record('click-leave');
  await expect(panel,'点击打开后移出鼠标仍应可读').toBeVisible();

  await trigger.click();await expect(panel).not.toBeVisible();await record('trigger-toggle-close');
  await trigger.click();await expect(panel).toBeVisible();await settle();await record('trigger-toggle-reopen');
  await panel.getByRole('button',{name:'收起'}).click();await expect(panel).not.toBeVisible();
  await trigger.focus();await trigger.press('Enter');await expect(panel).toBeVisible();await settle();await record('keyboard-open');
  await panel.locator('p').focus();await expect(panel.locator('p')).toBeFocused();await page.mouse.move(1,1);
  await page.waitForTimeout(300);await record('focused-content-leave');
  await expect(panel,'正文取得焦点后移出鼠标仍应可读').toBeVisible();
  await expect(panel.locator('p'),'正文焦点不应因鼠标离开丢失').toBeFocused();
  expect((await new AxeBuilder({page}).include('.app-main').include('.match-summary-panel').analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath(`summary-interaction-${width}.png`),fullPage:true});
  await page.mouse.click(1,1);await expect(panel).not.toBeVisible();await expect(trigger).toHaveAttribute('aria-expanded','false');await record('outside-click-close');
  await trigger.focus();await trigger.press('Enter');await expect(panel).toBeVisible();await settle();
  await panel.locator('p').focus();await page.keyboard.press('Escape');await expect(panel).not.toBeVisible();await expect(trigger).toBeFocused();await record('escape-close');
  } finally {
    console.log(`SUMMARY_TIMELINE ${JSON.stringify(timeline)}`);
    await testInfo.attach('summary-interaction-timeline',{body:JSON.stringify(timeline,null,2),contentType:'application/json'});
  }
});

import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('招聘列表与日程同时高亮三五轮，取消五轮后立即同步',async({page},testInfo)=>{
  await reviewWorkspace(page);
  await page.emulateMedia({reducedMotion:'reduce'});
  let cancelled=false;
  const empty={items:[],hasMore:false,nextCursor:null};
  const application=()=>({id:'app',candidateId:'person-a',demandId:'demand-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'resume-a',recruitmentType:'INTERNAL',currentStage:'PATH_SELECTION',ownerUserId:'hr-a',terminalAt:null,activeInterviewRoundNos:cancelled?[3]:[3,5]});
  const interviews=()=>[3,5].map(roundNo=>({id:`round-${roundNo}`,applicationId:'app',candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师',jdVersionId:'jd-v2',workflowStepId:`step-${roundNo}`,stepCode:`INTERVIEW_ROUND_${roundNo}`,roundNo,interviewType:roundNo===3?'INTERNAL':'EXTERNAL',status:roundNo===5&&cancelled?'CANCELLED':'SCHEDULED',scheduledStartAt:'2026-09-07T02:00:00Z',scheduledEndAt:'2026-09-07T02:30:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',candidateConfirmationStatus:'PENDING',createdAt:'2026-09-07T01:00:00Z',version:'1'}));
  let applicationReads=0;
  const opened:string[]=[];
  await page.route('**/api/v1/**',route=>{
    const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
    if(path==='/applications')return route.fulfill({json:{...empty,items:[application()]}});
    if(path==='/applications/app'){applicationReads++;return route.fulfill({json:{application:application(),collaboratorUserIds:[],workflowSteps:[3,5].map(roundNo=>({id:`step-${roundNo}`,stepCode:`INTERVIEW_ROUND_${roundNo}`,roundNo,sequenceNo:roundNo,status:roundNo===5&&cancelled?'WITHDRAWN':'IN_PROGRESS'})),decisions:[],offerStatusRecords:[],elapsedSeconds:3600}});}
    if(path==='/interviews')return route.fulfill({json:{...empty,items:interviews().filter(i=>!url.searchParams.get('status')||i.status===url.searchParams.get('status'))}});
    if(path==='/interviews/round-5/cancel'){cancelled=true;return route.fulfill({json:{interview:interviews()[1],participants:[]}});}
    if(['/interviews/round-3','/interviews/round-5'].includes(path)){opened.push(path);return route.fulfill({json:{interview:interviews().find(i=>path.endsWith(i.id)),participants:[]}});}
    if(path.endsWith('/materials'))return route.fulfill({json:{interviewId:path.split('/')[2],candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师'}});
    if(path==='/interview-feedback-batch')return route.fulfill({json:{...empty,visibleInterviewIds:['round-3','round-5']}});
    return route.fallback();
  });
  await page.goto('/recruitment');
  const row=page.getByRole('row').filter({has:page.getByRole('button',{name:'详情与版本'})});
  for(const n of [3,5])await expect(row.getByRole('button',{name:`第 ${n} 轮面试`})).toHaveAttribute('data-active','true');
  await expect(row.locator('.stage-interview')).toHaveCSS('background-color','rgb(37, 99, 235)');
  await expect(row.locator('.stage-pending')).toHaveCSS('color','rgb(89, 106, 131)');
  expect(applicationReads).toBe(0); // 列表高亮使用批量返回字段，不逐行查询详情。
  for (const cell of await page.locator('.pipeline-table-card thead th, .pipeline-table-card tbody tr.ant-table-row > td').all()) {
    await expect(cell).toHaveCSS('text-align', 'center');
    await expect(cell).toHaveCSS('vertical-align', 'middle');
  }
  await page.screenshot({path:testInfo.outputPath('list-active-rounds.png')});
  for(const n of [3,5]){
    await row.getByRole('button',{name:`第 ${n} 轮面试`}).click();
    await expect.poll(()=>opened.includes(`/interviews/round-${n}`)).toBe(true);
    await page.getByRole('dialog',{name:'面试',exact:true}).locator('.ledger-interview-actions-main').getByRole('button',{name:'关闭',exact:true}).click();
  }
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/林雨桐/}).filter({hasText:'第 5 轮'}).click();
  const dialog=page.getByRole('dialog',{name:'面试事实与处理',exact:true});
  for(const n of [3,5]){
    const button=dialog.getByRole('button',{name:`第 ${n} 轮面试`});
    await expect(button).toHaveAttribute('data-active','true');
    await expect(button).toHaveCSS('background-color','rgb(37, 99, 235)');
  }
  await expect(dialog).toHaveCSS('opacity','1');
  expect((await new AxeBuilder({page}).include('.schedule-recruitment-actions').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('schedule-active-rounds.png'),animations:'disabled'});
  await dialog.getByRole('button',{name:/^取\s*消$/}).click();
  await page.getByRole('dialog',{name:'取消面试',exact:true}).getByRole('button',{name:'确认取消'}).click();
  await expect(dialog.getByRole('button',{name:'第 5 轮面试'})).not.toHaveAttribute('data-active');
  await expect(dialog.getByRole('button',{name:'第 3 轮面试'})).toHaveAttribute('data-active','true');
  await page.goto('/recruitment');
  await expect(row.getByRole('button',{name:'第 5 轮面试'})).not.toHaveAttribute('data-active');
  await expect(row.getByRole('button',{name:'第 3 轮面试'})).toHaveAttribute('data-active','true');
});

test('一面完成后招聘和日程保留一面高亮，二面保存后才切换',async({page},testInfo)=>{
  await reviewWorkspace(page);
  let secondScheduled=false;
  let offer=false;
  const empty={items:[],hasMore:false,nextCursor:null};
  const application=()=>({id:'app',candidateId:'person-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'resume-a',recruitmentType:'INTERNAL',currentStage:offer?'OFFER':'PATH_SELECTION',ownerUserId:'hr-a',terminalAt:null,version:1,activeInterviewRoundNos:!offer&&secondScheduled?[2]:[],latestCompletedInterviewRoundNo:1});
  const steps=()=>[1,2].map(roundNo=>({id:`step-${roundNo}`,stepCode:'INTERVIEW_ROUND_1',roundNo,sequenceNo:roundNo,status:roundNo===1?'PASSED':'PENDING',version:1}));
  const details=()=>({application:application(),collaboratorUserIds:[],workflowSteps:steps(),decisions:[],offerStatusRecords:[]});
  const interviews=()=>[1,...(secondScheduled?[2]:[])].map(roundNo=>({id:`round-${roundNo}`,applicationId:'app',candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师',jdVersionId:'jd-v2',workflowStepId:`step-${roundNo}`,stepCode:'INTERVIEW_ROUND_1',roundNo,interviewType:'INTERNAL',status:roundNo===1?'COMPLETED':'SCHEDULED',scheduledStartAt:'2026-09-07T02:00:00Z',scheduledEndAt:'2026-09-07T02:30:00Z',timezoneId:'Asia/Shanghai',meetingMode:'ONLINE',candidateConfirmationStatus:'PENDING',ownerUserId:'hr-a',createdAt:'2026-09-07T01:00:00Z',version:'1'}));
  let detailReads=0;
  await page.route('**/api/v1/**',route=>{
    const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
    if(path==='/applications')return route.fulfill({json:{...empty,items:[application()]}});
    if(path==='/applications/app'){detailReads++;return route.fulfill({json:details()});}
    if(path==='/interviews'&&route.request().method()==='POST'){
      secondScheduled=true;
      return route.fulfill({json:{interview:interviews()[1],participants:[]}});
    }
    if(path==='/interviews')return route.fulfill({json:{...empty,items:interviews().filter(i=>!url.searchParams.get('status')||i.status===url.searchParams.get('status'))}});
    if(['/interviews/round-1','/interviews/round-2'].includes(path))return route.fulfill({json:{interview:interviews().find(i=>path.endsWith(i.id)),participants:[]}});
    if(path.endsWith('/materials'))return route.fulfill({json:{interviewId:path.split('/')[2],candidateId:'person-a',candidateName:'林雨桐',jdId:'jd-a',jdTitle:'前端工程师'}});
    if(path==='/interview-feedback-batch')return route.fulfill({json:{...empty,visibleInterviewIds:['round-1','round-2']}});
    return route.fallback();
  });
  await page.goto('/recruitment');
  const row=page.locator('.ant-table-row').first();
  await expect(row.getByRole('button',{name:'第 1 轮面试'})).toHaveAttribute('data-completed','true');
  await expect(row.locator('.stage-pending')).not.toHaveClass(/selected/);
  expect(detailReads).toBe(0);
  await expect(row.getByText('第 1 轮已完成 · 待安排下一步')).toHaveCount(0);
  await page.screenshot({path:testInfo.outputPath('completed-first-round-list.png')});
  await page.goto('/schedule');
  await page.getByRole('combobox',{name:'状态',exact:true}).click();
  await page.getByTitle('已完成',{exact:true}).click();
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/林雨桐/}).filter({hasText:'第 1 轮'}).click();
  const dialog=page.getByRole('dialog',{name:'面试事实与处理',exact:true});
  const first=page.locator('.schedule-recruitment-actions button[aria-label="第 1 轮面试"]');
  await expect(first).toHaveAttribute('data-completed','true');
  await expect(first).toHaveCSS('background-color','rgb(37, 99, 235)');
  await expect(dialog.getByText('第 1 轮已完成 · 待安排下一步')).toHaveCount(0);
  await page.screenshot({path:testInfo.outputPath('completed-first-round-schedule.png'),animations:'disabled'});
  const originalViewport=page.viewportSize()!;
  await page.setViewportSize({width:390,height:844});
  await expect(first).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:testInfo.outputPath('completed-first-round-mobile.png'),animations:'disabled'});
  await page.setViewportSize(originalViewport);
  await dialog.getByRole('button',{name:'第 2 轮面试'}).click();
  const scheduling=page.getByRole('dialog',{name:'面试',exact:true});
  await expect(scheduling.getByLabel('开始时间',{exact:true})).toBeVisible();
  await expect(first).toHaveAttribute('data-completed','true');
  await scheduling.getByRole('button',{name:/^取\s*消$/}).click();
  await expect(first).toHaveAttribute('data-completed','true');
  await dialog.getByRole('button',{name:'第 2 轮面试'}).click();
  await expect(scheduling.locator('.ant-select-selection-item',{hasText:'陈佳'})).toBeVisible();
  await scheduling.getByRole('button',{name:/^保\s*存$/}).click();
  await expect.poll(()=>secondScheduled).toBe(true);
  await scheduling.locator('.ledger-interview-actions-main').getByRole('button',{name:'关闭',exact:true}).click();
  await expect(dialog.getByRole('button',{name:'第 2 轮面试'})).toHaveAttribute('data-active','true');
  await expect(first).not.toHaveAttribute('data-completed');
  await page.goto('/recruitment');
  await expect(row.getByRole('button',{name:'第 2 轮面试'})).toHaveAttribute('data-active','true');
  await expect(row.getByRole('button',{name:'第 1 轮面试'})).not.toHaveAttribute('data-completed');
  const activeRowHeight=(await row.boundingBox())!.height;
  await page.screenshot({path:testInfo.outputPath('active-second-round-list.png')});
  offer=true;
  await page.reload();
  await expect(row.getByRole('button',{name:'入职',exact:true})).toHaveClass(/selected/);
  await expect(row.getByText('Offer / 入职',{exact:true})).toHaveCount(0);
  expect((await row.boundingBox())!.height).toBe(activeRowHeight);
  await page.screenshot({path:testInfo.outputPath('offer-stage-list.png')});
  await page.goto('/schedule');
  await page.getByRole('region',{name:'所选周面试日历'}).getByRole('button',{name:/林雨桐/}).filter({hasText:'第 2 轮'}).click();
  await expect(dialog.getByRole('button',{name:'入职',exact:true})).toHaveClass(/selected/);
  await expect(dialog.locator('.recruitment-stage small')).toHaveCount(1);
  await expect(dialog.locator('.recruitment-stage').getByText('Offer / 入职',{exact:true})).toHaveCount(0);
});

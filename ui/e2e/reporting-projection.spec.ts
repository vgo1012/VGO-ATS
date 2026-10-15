import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test.use({timezoneId:'Asia/Shanghai'});

async function reconnectAfterCacheExpires(page:Page,minute:number){
  await page.clock.setFixedTime(new Date(`2026-09-07T01:${minute}:00Z`));
  await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
  await page.evaluate(()=>window.dispatchEvent(new Event('online')));
}

for(const width of [390,1440]){
  test(`funnel 统计准备、完成水位、延迟与重试失败保留数据 ${width}`,async({page},testInfo)=>{
    await page.setViewportSize({width,height:900});
    await reviewWorkspace(page);
    const errors:string[]=[];
    page.on('pageerror',error=>errors.push(error.message));
    let mode:'initializing'|'ready'|'lagging'|'failed'='initializing';
    let requests=0;
    await page.route(`**/api/v1/reports/funnel?*`,route=>{
      requests++;
      if(mode==='initializing'||mode==='failed')return route.fulfill({status:503,json:{
        status:503,title:'统计暂不可用',detail:mode==='initializing'?'统计尚未完成首次更新':'统计暂时无法更新',
        code:mode==='initializing'?'METRIC_PROJECTION_INITIALIZING':'TEMPORARY_FAILURE',recoverable:true,
      }});
      return route.fulfill({json:{
        reportType:'funnel',from:'2026-09-01T00:00:00Z',to:'2026-09-08T00:00:00Z',
        dataUpdatedAt:'2026-09-07T00:13:00Z',projectionStatus:mode==='lagging'?'LAGGING':'READY',
        reconciliationUpdatedAt:null,scanIncomplete:false,
        definitions:[{code:'resumes',name:'新增简历',category:'funnel',versionNo:1,eventType:'RESUME_INGESTED',definitionText:'统计成功入库的简历版本。',effectiveFrom:'2026-09-01T00:00:00Z'}],
        points:[{eventType:'RESUME_INGESTED',eventCount:17,eventValue:17,firstOccurredAt:null,lastOccurredAt:'2026-09-07T00:00:00Z'}],
        days:[{date:'2026-09-07',eventType:'RESUME_INGESTED',eventCount:17}],
      }});
    });
    await page.goto('/admin/reports');
    const scope=page.locator('.workspace-page');
    const retry=scope.getByRole('button',{name:/^重\s*试$/});
    await expect(scope.getByText('统计准备中',{exact:true})).toBeVisible();
    await expect(scope.locator('.ant-statistic')).toHaveCount(0);
    await expect(scope.getByText('指标说明',{exact:true})).toHaveCount(0);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);

    mode='ready';
    await retry.click();
    const value=scope.locator('.ant-statistic-content-value');
    await expect(value).toHaveText('17');
    await expect(scope.getByText('更新于 2026-09-07 08:13',{exact:true})).toBeVisible();
    await expect(scope.getByText('统计更新延迟',{exact:true})).toHaveCount(0);
    mode='lagging';
    const readyRequests=requests;
    await reconnectAfterCacheExpires(page,32);
    await expect.poll(()=>requests).toBeGreaterThan(readyRequests);
    await expect(scope.getByText('统计更新延迟',{exact:true})).toBeVisible();
    await expect(value).toHaveText('17');

    mode='failed';
    await reconnectAfterCacheExpires(page,34);
    await expect(retry).toBeVisible();
    await expect(value).toHaveText('17');
    const failedRequests=requests;
    await retry.click();
    await expect.poll(()=>requests).toBeGreaterThan(failedRequests);
    await expect(retry).toBeEnabled();
    await expect(value).toHaveText('17');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:testInfo.outputPath(`funnel-projection-${width}.png`),fullPage:true});
    expect(errors).toEqual([]);
  });
}

import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('候选人统计独立加载、失败重试及游标边界',async({page},testInfo)=>{
 await reviewWorkspace(page);
 let release:()=>void=()=>{};
 const pending=new Promise<void>(resolve=>{release=resolve;});
 let fail=true;
 const counts:string[]=[];
 await page.route('**/api/v1/candidates/count?**',async route=>{
  counts.push(route.request().url());
  await pending;
  if(fail)return route.fulfill({status:503,json:{detail:'统计暂时不可用'}});
  const cursor=new URL(route.request().url()).searchParams.get('cursor');
  return route.fulfill({json:cursor?{count:3,nextCursor:null,hasMore:false}:{count:40,nextCursor:'next',hasMore:true}});
 });
 await page.goto('/talents');
 await expect(page.getByText('林雨桐',{exact:true})).toBeVisible();
 await expect(page.getByText('条/统计中…')).toBeVisible();
 release();
 await expect(page.getByText('条/统计失败')).toBeVisible();
 fail=false;
 await page.getByRole('button',{name:'重试统计'}).click();
 await expect(page.getByText('条/共43条记录')).toBeVisible();
 await expect(page.getByText('共3页',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'末页',exact:true})).toBeDisabled();
 expect(counts.length).toBe(3);
 await page.screenshot({path:testInfo.outputPath('candidate-totals.png'),fullPage:true});
});

for(const entry of [
 {route:'/talents?pool_q=Java',scope:'PERSONAL_POOL',stage:null},
 {route:'/talents?view=todo&pool_q=Java',scope:'PERSONAL_POOL',stage:null},
 {route:'/shared-library?q=Java',scope:'SHARED',stage:null},
])test(`搜索计数与列表范围一致 ${entry.route}`,async({page})=>{
 await reviewWorkspace(page);
 const requests:URL[]=[];
 await page.route('**/api/v1/talent-search/count?**',route=>{
  requests.push(new URL(route.request().url()));
  return route.fulfill({json:{count:27,nextCursor:null,hasMore:false}});
 });
 await page.goto(entry.route);
 await expect(page.getByText('条/共27条记录')).toBeVisible();
 await expect(page.getByText('总页数未提供',{exact:true})).toBeVisible();
 expect(requests[0]!.searchParams.get('scope')??'SHARED').toBe(entry.scope);
 expect(requests[0]!.searchParams.get('poolStage')).toBe(entry.stage);
 if(entry.route.includes('Java'))expect(requests[0]!.searchParams.get('q')).toBe('Java');
});

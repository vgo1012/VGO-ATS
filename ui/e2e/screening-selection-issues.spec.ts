import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('identifies every blocked candidate, marks rows and preserves the selection', async ({page})=>{
  await reviewWorkspace(page);
  const people=['未评分甲','正常乙','未评分丙'].map((fullName,index)=>({id:`person-${index}`,fullName,ownerUserId:'hr-a',primaryPhone:`1380000000${index}`}));
  let admissions=0;
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path==='/candidates')return route.fulfill({json:{items:people,hasMore:false,nextCursor:null}});
    if(path==='/candidate-resume-summaries')return route.fulfill({json:[]});
    if(path==='/candidate-pool-statuses')return route.fulfill({json:people.map(person=>({candidateId:person.id,resumeVersionId:`resume-${person.id}`,stages:[],jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:`resume-${person.id}`,title:'工程师',versionNo:2,status:'PENDING'}]}))});
    if(path==='/screening-candidates')return route.fulfill({json:[{candidateId:'person-1',resumeVersionId:'resume-person-1',jdVersionId:'jd-v2',defaultMatchRunId:'run',admissionStatus:'READY'}]});
    if(path==='/screening-admissions'){admissions++;return route.fulfill({json:{}});}
    return route.fallback();
  });
  await page.goto('/talents');
  await page.getByRole('checkbox',{name:'全选本页候选人'}).click();
  await page.locator('.talent-bulk-actions').getByRole('button',{name:'智能筛选',exact:true}).click();
  const issues=page.getByRole('status',{name:'智能筛选待处理候选人'});
  await expect(issues).toContainText('2 位候选人');
  await expect(issues).toContainText('未评分甲');
  await expect(issues).toContainText('未评分丙');
  await expect(issues).not.toContainText('正常乙');
  const retry=page.locator('.talent-bulk-actions').getByRole('button',{name:/重试智能筛选$/});
  await expect(retry).toBeVisible();
  await expect(retry).toBeEnabled();
  await expect(page.locator('.error-recovery-actions')).toHaveCount(0);
  await expect(page.getByRole('row').filter({has:page.getByRole('checkbox',{name:'选择未评分甲'})})).toContainText('智能筛选待处理');
  await expect(page.getByRole('row').filter({has:page.getByRole('checkbox',{name:'选择正常乙'})})).not.toContainText('智能筛选待处理');
  await expect(page.getByRole('checkbox',{name:'全选本页候选人'})).toBeChecked();
  expect(admissions).toBe(0);
  await page.getByRole('button',{name:'清空选择',exact:true}).click();
  await expect(issues).toHaveCount(0);
  const screening=page.locator('.talent-bulk-actions').getByRole('button',{name:/智能筛选$/});
  await expect(screening).toHaveText('智能筛选');
  await expect(screening).toBeDisabled();
});

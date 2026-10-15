import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

async function fixture(page:Page){
  await reviewWorkspace(page);
  const users=['来源甲','接收乙','接收丙'].map((displayName,index)=>({id:`user-${index}`,displayName,workEmail:`user${index}@example.com`,homeDepartmentId:'dept-a',homeDepartmentName:'招聘团队',status:'ACTIVE',mustChangePassword:false,version:1}));
  await page.route('**/api/v1/admin/users?**',route=>route.fulfill({json:users}));
  let release!:()=>void;
  const pending=new Promise<void>(resolve=>{release=resolve;});
  let oldStarted=false;
  const writes:Record<string,unknown>[]=[];
  await page.route('**/api/v1/admin/users/user-0/handover-previews',async route=>{
    const {targetUserId}=route.request().postDataJSON() as {targetUserId:string};
    if(targetUserId==='user-1'){oldStarted=true;await pending;}
    await route.fulfill({json:{sourceUserId:'user-0',targetUserId,mandatoryCount:0,unresolvedCount:0,resources:[1,2].map(index=>({resourceType:'CANDIDATE',resourceId:`${targetUserId}-${index}`,displayName:`${targetUserId==='user-1'?'乙':'丙'}预览候选人${index}`,mandatory:false,transferable:true,unresolvedReason:null}))}});
  });
  await page.route('**/api/v1/admin/users/user-0/handovers',route=>{
    writes.push(route.request().postDataJSON() as Record<string,unknown>);
    return route.fulfill({json:{batch:{id:'handover',status:'SUCCEEDED',totalCount:1,succeededCount:1,failedCount:0,unresolvedCount:0},items:[]}});
  });
  await page.goto('/admin/identity');
  const open=async()=>{const button=page.getByRole('row').filter({hasText:'user0@example.com'}).getByRole('button',{name:/交接/});await button.focus();await button.press('Enter');};
  return {release,writes,open,oldStarted:()=>oldStarted};
}

async function choose(page:Page,name:string){
  await page.getByRole('dialog').getByRole('combobox').click();
  await page.locator('.ant-select-item-option-content').getByText(`${name} · 招聘团队`,{exact:true}).click();
}

test('切换交接接收人后晚到预览不能覆盖新预览和人工勾选',async({page},testInfo)=>{
  const state=await fixture(page);
  await state.open();await choose(page,'接收乙');
  const dialog=page.getByRole('dialog',{name:'工作交接 · 来源甲'});
  await dialog.getByRole('button',{name:'生成预览'}).click();
  await expect.poll(state.oldStarted).toBe(true);
  await choose(page,'接收丙');
  await expect(dialog.getByRole('button',{name:'执行勾选交接'})).toBeDisabled();
  await dialog.getByRole('button',{name:'生成预览'}).click();
  await dialog.getByRole('checkbox',{name:/丙预览候选人2/}).uncheck();
  const oldResponse=page.waitForResponse(response=>response.url().endsWith('/handover-previews')&&response.request().postDataJSON().targetUserId==='user-1');
  state.release();await oldResponse;
  await expect(dialog.getByText('乙预览候选人1',{exact:false})).toHaveCount(0);
  await expect(dialog.getByRole('checkbox',{name:/丙预览候选人2/})).not.toBeChecked();
  expect((await new AxeBuilder({page}).include('[role="dialog"]').withTags(['wcag2a','wcag2aa']).analyze()).violations).toEqual([]);
  await expect.poll(()=>dialog.evaluate(element=>element.scrollWidth-element.clientWidth)).toBeLessThanOrEqual(1);
  await dialog.screenshot({path:`../.planning/2026-09-11-continuous-optimization/evidence/handover-preview-${testInfo.project.name}.png`});
  await dialog.getByRole('button',{name:'执行勾选交接'}).focus();await page.keyboard.press('Enter');
  await expect(dialog).not.toBeVisible();
  expect(state.writes).toEqual([{targetUserId:'user-2',selectionMode:'SELECTED',selectedResourceKeys:['CANDIDATE:user-2-1'],reason:'人员工作交接'}]);
});

test('关闭交接弹窗后旧请求不能污染再次打开的交接',async({page})=>{
  const state=await fixture(page);
  await state.open();await choose(page,'接收乙');
  const dialog=page.getByRole('dialog',{name:'工作交接 · 来源甲'});
  await dialog.getByRole('button',{name:'生成预览'}).click();
  await expect.poll(state.oldStarted).toBe(true);
  await dialog.getByRole('button',{name:/^取\s*消$/}).click();
  await expect(dialog).not.toBeVisible();
  await state.open();
  const oldResponse=page.waitForResponse(response=>response.url().endsWith('/handover-previews'));
  state.release();await oldResponse;
  await expect(dialog.getByRole('button',{name:'执行勾选交接'})).toBeDisabled();
  await expect(dialog.getByRole('button',{name:'生成预览'})).toBeDisabled();
  await expect(dialog.getByRole('checkbox')).toHaveCount(0);
  expect(state.writes).toHaveLength(0);
});

import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('identity badge opens field evidence, paginates and recovers failed reads',async({page},testInfo)=>{
  await reviewWorkspace(page);
  let failed=true;
  const requests:string[]=[];
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:[{candidateId:'person-a',openIdentityConflictCount:2,stages:[]}]}));
  await page.route('**/api/v1/identity-conflicts?**',route=>{
    const params=new URL(route.request().url()).searchParams;
    expect(params.get('candidateId')).toBe('person-a');
    requests.push(params.get('cursor')??'first');
    if(failed)return route.fulfill({status:503,json:{status:503,detail:'暂不可用'}});
    const next=params.has('cursor');
    return route.fulfill({json:{items:[{id:next?'c2':'c1',intakeId:next?'i2':'i1',candidateIdA:'person-a',candidateIdB:null,conflictType:next?'NAME_MATCH_CONTACT_MISMATCH':'CONTACT_NAME_MISMATCH',status:'OPEN'}],hasMore:!next,nextCursor:next?null:'next'}});
  });
  await page.route('**/api/v1/intakes/i*',route=>route.fulfill({json:{id:'i1',originalFileName:'待核对简历.pdf',parsedFullName:'林小桐',parsedPhone:'13800001234',parsedEmail:null}}));
  await page.goto('/talents');
  const trigger=page.getByRole('button',{name:'身份待核验 · 2 项 · 查看原因'});
  await trigger.focus();await page.keyboard.press('Enter');
  const dialog=page.getByRole('dialog');
  await expect(dialog).toContainText('冲突详情读取失败');
  failed=false;await dialog.getByRole('button',{name:/重\s*试/,exact:true}).click();
  await expect(dialog).toContainText('联系方式匹配已有档案，但姓名不一致');
  const nameRow=dialog.getByRole('row').filter({has:page.getByRole('cell',{name:'姓名',exact:true})});
  await expect(nameRow).toContainText('林小桐');await expect(nameRow).toContainText('林雨桐');await expect(nameRow).toContainText('值不一致');
  await expect(dialog.getByRole('row').filter({has:page.getByRole('cell',{name:'手机号',exact:true})})).toContainText('相同');
  await expect(dialog.getByRole('row').filter({has:page.getByRole('cell',{name:'邮箱',exact:true})})).toContainText('信息缺失');
  await page.screenshot({path:testInfo.outputPath('identity-evidence.png'),fullPage:true});
  await dialog.getByRole('button',{name:'查看更多冲突'}).click();
  await expect(dialog).toContainText('姓名相同，但联系方式未匹配已有档案');expect(requests).toContain('next');
  await dialog.getByRole('button',{name:'核验并处理此项'}).first().click();
  await expect(dialog).toContainText('人工处理身份冲突');
  await page.keyboard.press('Escape');await expect(dialog).toContainText('身份核验 · 林雨桐');
  await page.keyboard.press('Escape');await expect(dialog).toBeHidden();await expect(trigger).toBeFocused();
});

test('unavailable candidate details are not presented as verified differences',async({page})=>{
  await reviewWorkspace(page);
  await page.route('**/api/v1/candidate-pool-statuses**',route=>route.fulfill({json:[{candidateId:'person-a',openIdentityConflictCount:1,stages:[]}]}));
  await page.route('**/api/v1/identity-conflicts?**',route=>route.fulfill({json:{items:[{id:'c1',intakeId:'i1',candidateIdA:'person-a',candidateIdB:null,conflictType:'CONTACT_NAME_MISMATCH',status:'OPEN'}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/intakes/i1',route=>route.fulfill({status:403,json:{status:403,detail:'无权查看'}}));
  await page.route('**/api/v1/candidates/person-a',route=>route.fulfill({status:403,json:{status:403,detail:'无权查看'}}));
  await page.goto('/talents');await page.getByRole('button',{name:'身份待核验 · 1 项 · 查看原因'}).click();
  const dialog=page.getByRole('dialog');
  await expect(dialog).toContainText('部分身份信息暂不可查看');
  await expect(dialog.getByText('值不一致',{exact:true})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'重试读取'})).toBeVisible();
});

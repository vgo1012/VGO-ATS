import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

test('intake identity review explains missing contacts and shows conflict evidence before a decision',async({page},testInfo)=>{
  await reviewWorkspace(page);
  const base={batchId:'batch',sourceChannelId:'source',sourceRecruiterUserId:'hr-a',defaultOwnerUserId:'hr-a',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',handledByUserId:'hr-a',mediaType:'application/pdf',sizeBytes:1000,contentSha256:'a'.repeat(64),scanStatus:'CLEAN',status:'IDENTITY_REVIEW_REQUIRED',importStatus:'PENDING',processingStatus:'SUCCEEDED',scoreStatus:'NOT_APPLICABLE',candidateId:null,resumeVersionId:null,version:1,createdAt:'2026-09-09T01:00:00Z',jdVersionId:'jd-v2',jdTitle:'测试运维岗位'};
  const missing={...base,id:'missing-contact',originalFileName:'缺联系方式示例.pdf',parsedFullName:'测试候选人',parsedPhone:null,parsedEmail:null,failureCode:'IDENTITY_REVIEW_REQUIRED'};
  const conflicting={...base,id:'conflicting-contact',originalFileName:'同名核对示例.pdf',parsedFullName:'林雨桐',parsedPhone:'13900009999',parsedEmail:null,failureCode:'IDENTITY_CONFLICT_REQUIRES_REVIEW'};
  let failed=false,writes=0;
  await page.route('**/api/v1/intakes**',route=>{
    const path=new URL(route.request().url()).pathname;
    if(route.request().method()!=='GET'){writes++;return route.fulfill({status:500,json:{status:500,detail:'不应写入'}});}
    if(path.endsWith('/count'))return route.fulfill({json:{count:2,hasMore:false,nextCursor:null}});
    if(path.endsWith('/missing-contact'))return route.fulfill({json:missing});
    if(path.endsWith('/conflicting-contact'))return route.fulfill({json:conflicting});
    return route.fulfill({json:{items:[missing,conflicting],hasMore:false,nextCursor:null}});
  });
  await page.route('**/api/v1/identity-conflicts?**',route=>route.fulfill({json:{items:[{id:'conflict-a',intakeId:conflicting.id,candidateIdA:'person-a',candidateIdB:null,conflictType:'NAME_MATCH_CONTACT_MISMATCH',status:'OPEN',resolution:null,chosenCandidateId:null,reason:null,createdAt:base.createdAt}],hasMore:false,nextCursor:null}}));
  await page.route('**/api/v1/candidates/person-a',route=>failed
    ?route.fulfill({status:403,json:{status:403,detail:'无权查看'}})
    :route.fulfill({json:{id:'person-a',fullName:'林雨桐',primaryPhone:'13800001234',primaryEmail:'lin@example.com',ownerUserId:'hr-a',ownerDepartmentId:'dept-a',status:'ACTIVE',version:1}}));
  await page.goto('/settings/intakes');
  const missingRow=page.getByRole('row').filter({hasText:missing.originalFileName});
  const conflictRow=page.getByRole('row').filter({hasText:conflicting.originalFileName});
  await expect(missingRow).toContainText('未识别到手机号和邮箱');
  await expect(conflictRow).toContainText('姓名相同，但联系方式未匹配已有档案');
  await page.screenshot({path:testInfo.outputPath('intake-reasons.png'),fullPage:true});
  const missingAction=missingRow.getByRole('button',{name:'核对异常身份'});
  await missingAction.focus();await page.keyboard.press('Enter');
  const confirmation=page.getByRole('dialog',{name:'核对异常身份 · 缺联系方式示例.pdf'});
  await expect(confirmation).toContainText('未识别到手机号和邮箱');
  await expect(confirmation.getByLabel('候选人姓名')).toHaveValue('测试候选人');
  await page.keyboard.press('Escape');await expect(missingAction).toBeFocused();
  const conflictAction=conflictRow.getByRole('button',{name:'处理身份冲突'});
  await conflictAction.click();
  const dialog=page.getByRole('dialog',{name:'人工处理身份冲突',exact:true});
  await expect(dialog).toContainText('姓名相同，但联系方式未匹配已有档案');
  await expect(dialog).toContainText('13900009999');
  await expect(dialog).toContainText('13800001234');
  await expect(dialog).toContainText('本次简历');
  await expect(dialog.getByRole('button',{name:'确认处理'})).toBeEnabled();
  await page.screenshot({path:testInfo.outputPath('intake-conflict-evidence.png'),fullPage:true});
  expect(await dialog.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBe(true);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  await page.keyboard.press('Escape');await expect(conflictAction).toBeFocused();
  failed=true;await conflictAction.click();
  await expect(dialog.getByRole('button',{name:'确认处理'})).toBeDisabled();
  await expect(dialog.getByText('13800001234',{exact:true})).toHaveCount(0);
  failed=false;await dialog.getByRole('button',{name:/重试/}).click();
  await expect(dialog).toContainText('13800001234');
  await expect(dialog.getByRole('button',{name:'确认处理'})).toBeEnabled();
  expect(writes).toBe(0);
});

import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const changed of [false,true]){
  test(`sharing uses the reviewed resume version when changed=${changed}`,async({page})=>{
    await reviewWorkspace(page);
    let currentVersion='resume-v1';
    const submitted:unknown[]=[];
    const errors:string[]=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/api/v1/candidates/person-a',route=>route.fulfill({json:{id:'person-a',fullName:'林雨桐',ownerUserId:'hr-a',version:'1'}}));
    await page.route('**/api/v1/candidate-resume-summaries?*',route=>route.fulfill({json:[{candidateId:'person-a',resumeVersion:{id:'resume-v1',versionNo:1,createdAt:'2026-09-12T00:00:00Z'},facts:null,source:null,factsStatus:'NOT_READY'}]}));
    await page.route('**/api/v1/candidate-pool-statuses?*',route=>route.fulfill({json:[{candidateId:'person-a',resumeVersionId:currentVersion,canScreenOut:true,libraryEligible:false,screenedOut:false,stages:[],jobContexts:[{jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:currentVersion,title:'已核对岗位',versionNo:2,status:'PENDING'}]}]}));
    await page.route('**/api/v1/candidates/person-a/screen-out',route=>{
      submitted.push(route.request().postDataJSON());
      return route.fulfill({json:{candidateId:'person-a',resumeVersionId:currentVersion,libraryEligible:true}});
    });
    await page.goto('/talents');
    const row=page.locator('tr[data-row-key="person-a"]');
    await expect(row.getByText('已核对岗位',{exact:true}).last()).toBeVisible();
    await expect(row.getByText('待解析',{exact:true})).toBeVisible();
    await row.getByRole('checkbox',{name:'选择林雨桐'}).check();
    await page.getByRole('button',{name:'筛选结束入共享库'}).click();
    const dialog=page.getByRole('dialog',{name:'结束筛选并转入共享简历库'});
    await expect(dialog).toBeVisible();
    if(changed)currentVersion='resume-v2';
    await dialog.getByRole('button',{name:'确认转共享 1 人'}).focus();
    await page.keyboard.press('Enter');
    if(changed){
      await expect(dialog.getByText('简历已更新，请查看最新简历后再转共享库',{exact:true})).toBeVisible();
      expect(submitted).toEqual([]);
      await expect(dialog.getByRole('status')).toContainText('失败 1 人');
    }else{
      await expect(dialog.getByText('已转入共享简历库',{exact:true})).toBeVisible();
      expect(submitted).toHaveLength(1);
      expect(submitted[0]).toMatchObject({resumeVersionId:'resume-v1'});
    }
    expect(errors).toEqual([]);
  });
}

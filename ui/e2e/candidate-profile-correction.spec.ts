import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';
import type { ResumeFactsCommand } from '../src/talent/api';

for (const width of [390, 1440]) {
  test(`详情保留 HR 更正入口，保存关键资料并支持继续更正 ${width}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    await reviewWorkspace(page);
    await page.route('**/api/v1/me', route => route.fulfill({ json: {
      userId: 'fact-reader',
      displayName: '简历事实查看者',
      roles: ['HR'],
      permissions: ['talent.read'],
      superAdmin: false,
      mustChangePassword: false,
      version: '1',
    } }));
    const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
    let candidate = { fullName:'林雨桐', email:'lin@example.com', phone:'13800001234', age:28 as number|null, gender:'FEMALE' as string|null, currentRole:'工程师',city:'上海',jobIntention:'后端开发' };
    let version=1; let attempts=0;
    let releaseFirstFailure!:()=>void;
    const firstFailure=new Promise<void>(resolve=>{releaseFirstFailure=resolve;});
    const bundle=()=>({snapshot:{...candidate,id:`facts-${version}`,resumeVersionId:'resume-a',snapshotVersion:version,sourceType:version===1?'AI_PARSE':'HUMAN_CORRECTION'},workExperiences:[],projectExperiences:[],educations:[],skills:[],simpleFacts:[]});
    await page.route('**/api/v1/candidates/person-a/resume-versions?**',route=>route.fulfill({json:{items:[{id:'resume-a',versionNo:1,originalFileName:'简历.pdf'}],hasMore:false}}));
    await page.route('**/api/v1/resume-versions/resume-a/facts',route=>route.fulfill({json:bundle()}));
    await page.route('**/api/v1/resume-versions/resume-a/extracted-text',route=>route.fulfill({json:{pages:[],pageCount:0,coverageRatio:0}}));
    await page.route('**/api/v1/candidate-resume-summaries?**',route=>route.fulfill({json:[{candidateId:'person-a',factsStatus:'READY',facts:bundle(),resumeVersion:{id:'resume-a'},source:null}]}));
    await page.route('**/api/v1/fact-corrections',async route=>{
      attempts++;
      const body=route.request().postDataJSON() as {baseFactSnapshotId:string;facts:ResumeFactsCommand};
      expect(body.baseFactSnapshotId).toBe(`facts-${version}`);
      expect(body.facts.skills).toEqual([]);
      if(attempts===1){await firstFailure;return route.fulfill({status:503,json:{status:503,title:'暂不可用',detail:'保存暂时失败，请重试',code:'UNAVAILABLE',recoverable:true}});}
      candidate={...candidate,...body.facts.candidate,fullName:body.facts.candidate.fullName!,email:body.facts.candidate.email!,phone:body.facts.candidate.phone!,currentRole:body.facts.candidate.currentRole!,city:body.facts.candidate.city!,jobIntention:body.facts.candidate.jobIntention!};version++;
      return route.fulfill({status:201,json:{correctionId:`correction-${version}`,factSnapshotId:`facts-${version}`,snapshotVersion:version,affectedCapabilities:[]}});
    });
    await page.goto('/talents');
    await page.getByRole('button',{name:'详情与版本',exact:true}).click();
    const dialog=page.getByRole('dialog');
    const edit=dialog.getByRole('button',{name:'HR 更正',exact:true});
    await edit.click();
    await page.getByLabel('邮箱',{exact:true}).fill('updated@example.com');
    await page.getByLabel('手机号',{exact:true}).fill('13912345678');
    await page.getByLabel('年龄',{exact:true}).fill('31');
    await dialog.getByRole('combobox',{name:'性别',exact:true}).click();
    await dialog.getByRole('combobox',{name:'性别',exact:true}).press('ArrowUp');
    await dialog.getByRole('combobox',{name:'性别',exact:true}).press('Enter');
    await page.getByLabel('更正原因',{exact:true}).fill('HR 已与候选人核实');
    expect((await new AxeBuilder({page}).include('[aria-label="HR 更正"]').analyze()).violations).toEqual([]);
    await page.getByRole('button',{name:'保存更正',exact:true}).click();
    const save=dialog.locator('button[type="submit"]');
    try {
      await expect(save).toBeDisabled();
      await expect(save).toHaveAccessibleName('保存更正');
      await expect(save).toHaveAttribute('aria-busy','true');
    } finally {releaseFirstFailure();}
    await expect(page.getByText('保存暂时失败，请重试')).toBeVisible();
    await expect(save).toHaveAttribute('aria-busy','false');
    await expect(page.getByLabel('手机号',{exact:true})).toHaveValue('13912345678');
    await page.getByRole('button',{name:'保存更正',exact:true}).click();
    await expect(edit).toBeVisible();
    const profile=dialog.getByRole('region',{name:'关键资料'});
    await expect(profile).toContainText('updated@example.com');
    await expect(profile).toContainText('13912345678');
    await expect(profile).toContainText('31 岁');
    await expect(profile).toContainText('男');
    await edit.click();
    await expect(page.getByLabel('年龄',{exact:true})).toHaveValue('31');
    await page.getByLabel('年龄',{exact:true}).fill('');
    await page.getByLabel('更正原因',{exact:true}).fill('年龄尚待进一步确认');
    await page.getByRole('button',{name:'保存更正',exact:true}).click();
    await edit.click();
    await expect(page.getByLabel('年龄',{exact:true})).toHaveValue('');
    await page.getByLabel('手机号',{exact:true}).fill('13700000000');
    await page.getByRole('button',{name:'取消更正',exact:true}).click();
    await expect(profile).toContainText('13912345678');
    expect(attempts).toBe(3);
    expect((await new AxeBuilder({page}).include('[aria-label="关键资料"]').analyze()).violations).toEqual([]);
    expect(errors).toEqual([]);
    await page.screenshot({path:testInfo.outputPath(`profile-${width}.png`),fullPage:true});
    await page.keyboard.press('Escape');
    const row=page.locator('.talent-ledger-card tbody tr[data-row-key="person-a"]');
    await expect(row).toContainText('13912345678');
    await expect(row.getByRole('img',{name:'男',exact:true})).toBeVisible();
  });
}

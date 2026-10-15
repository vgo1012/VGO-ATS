import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';

for(const routePath of ['/talents','/shared-library','/onboarded']) for(const state of ['PENDING','ADVANCED','FINISHED','NO_JD']) test(`${routePath} 面试材料始终展示简历和关联 JD：${state}`,  async ({page},testInfo)=>{
  await reviewWorkspace(page);
  const empty={items:[],hasMore:false,nextCursor:null};
  const person={id:'person-a',fullName:'材料候选人',ownerUserId:'hr-a'};

  const requests:string[]=[];
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    requests.push(path);
    if(path==='/applications')return route.fulfill({json:{...empty,items:[{id:'app-a',candidateId:person.id,jdId:'jd-a',jdVersionId:'jdv-a',resumeVersionId:'bound-resume',ownerUserId:'hr-a',currentStage:'ONBOARDED'}]}});
    if(path==='/candidate-batch')return route.fulfill({json:[person]});
    if(path==='/candidates')return route.fulfill({json:{...empty,items:[person]}});
    if(path==='/candidates/person-a')return route.fulfill({json:person});
    if(path==='/candidates/person-a/resume-versions')return route.fulfill({json:empty});
    if(path==='/candidate-pool-statuses')return route.fulfill({json:[{candidateId:person.id,stages:[],resumeVersionId:'bound-resume',jobContexts:state==='NO_JD'?[]:[{applicationId:state==='PENDING'?null:'app-a',status:state,title:'工程师',jdId:'jd-a',jdVersionId:'jdv-a',resumeVersionId:'bound-resume',versionNo:3}]}]});
    if(path==='/resume-version-references')return route.fulfill({json:[{id:'bound-resume',versionNo:2,originalFileName:'关联简历.pdf'}]});
    if(path==='/jd-versions/jdv-a')return route.fulfill({json:{id:'jdv-a',versionNo:3,title:'工程师',rawText:'关联岗位的 JD 原文'}});
    if(path==='/resume-versions/bound-resume/download-authorizations')return route.fulfill({json:{token:'material-grant'}});
    if(path==='/download-authorizations/material-grant')return route.fulfill({contentType:'application/pdf',headers:{'content-disposition':'attachment; filename=resume.pdf'},body:Buffer.from('JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA0MDAgNTAwXSAvUmVzb3VyY2VzIDw8IC9Gb250IDw8IC9GMSA0IDAgUiA+PiA+PiAvQ29udGVudHMgNSAwIFIgPj4KZW5kb2JqCjQgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNhID4+CmVuZG9iago1IDAgb2JqCjw8IC9MZW5ndGggNTMgPj4Kc3RyZWFtCkJUIC9GMSAxNiBUZiAzMCA0NDAgVGQgKEJvdW5kIHJlc3VtZSB2ZXJzaW9uIDIpIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDYKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDA5IDAwMDAwIG4gCjAwMDAwMDAwNTggMDAwMDAgbiAKMDAwMDAwMDExNSAwMDAwMCBuIAowMDAwMDAwMjQxIDAwMDAwIG4gCjAwMDAwMDAzMTEgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA2IC9Sb290IDEgMCBSID4+CnN0YXJ0eHJlZgo0MTQKJSVFT0YK', 'base64')});
    return route.fallback();
  });
  await page.goto(`${routePath}?candidate=person-a`);
  if(routePath==='/onboarded')await page.getByRole('button',{name:'查看简历',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:person.fullName,exact:true});
  await expect(dialog.getByText('黑名单标签',{exact:true})).toBeVisible();
  await expect(dialog.getByText('尚未安排面试')).toHaveCount(0);
  if(routePath==='/shared-library'){
    await expect(dialog.getByRole('region',{name:'历史招聘记录'})).toBeVisible();
    await expect(dialog.getByText('简历版本',{exact:true})).toBeVisible();
  }else for(const text of ['公司级黑名单与处置历史','查看不可变更历史','简历版本','当前版本来源'])await expect(dialog.getByText(text,{exact:true})).toHaveCount(0);
  await dialog.getByRole('button',{name:'查看面试材料'}).click();
  const materials=page.getByRole('dialog',{name:'面试材料',exact:true});
  await expect(materials.getByText(state==='NO_JD'?'未关联岗位 JD':'关联岗位的 JD 原文')).toBeVisible();
  await expect(materials.locator('iframe')).toHaveAttribute('src',/^blob:/);
  // 等待浏览器内置 PDF 阅读器完成首帧绘制后留存视觉证据。
  await page.waitForTimeout(1000);
  await page.screenshot({path:testInfo.outputPath('candidate-materials.png'),animations:'disabled'});
  await expect(materials.getByText('关联简历.pdf')).toBeVisible();
  await materials.getByRole('button',{name:'关闭',exact:true}).click();
  await page.screenshot({path:testInfo.outputPath('candidate-compact.png'),animations:'disabled'});
  expect(requests.some(path=>path.includes('/company-blacklist'))).toBe(routePath==='/shared-library');
  expect(requests.some(path=>path.includes('/interviews'))).toBe(false);
});

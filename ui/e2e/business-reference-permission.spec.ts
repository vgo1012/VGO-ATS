import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import type { ApplicationDetails } from '../src/recruitment/api';
import { reviewWorkspace } from './fixtures/ux-review';

const empty={items:[],hasMore:false,nextCursor:null};
const details:ApplicationDetails={
  application:{id:'reference-app',candidateId:'person-a',demandId:'demand-a',jdId:'jd-a',jdVersionId:'jd-v2',resumeVersionId:'resume-v1',workflowTemplateVersionId:'workflow-v1',matchPlanVersionId:'match-v1',recruitmentType:'INTERNAL',progressiveFollowUp:true,currentStage:'PATH_SELECTION',outcome:null,ownerUserId:'hr-b',ownerDepartmentId:'dept-a',terminalAt:null,version:1,createId:'hr-a',createTime:'2026-09-07T00:00:00Z',updateId:'hr-a',updateTime:'2026-09-07T00:00:00Z'},
  collaboratorUserIds:[],workflowSteps:[],decisions:[],offerStatusRecords:[],elapsedSeconds:300,
};

test('招聘详情名称缓存遇到权限拒绝隐藏，恢复权限后重新显示',async({page},info)=>{
  await reviewWorkspace(page);
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  let phase:'initial'|'denied'|'restored'|'temporary'='initial';
  const reads=new Map<string,number>();
  const detailReferencePaths=['/candidates/person-a','/jds/jd-a/versions','/directory/users/resolve'];
  const materialReferencePaths=['/resume-version-references','/jd-versions/jd-v2'];
  const referencePaths=[...detailReferencePaths,...materialReferencePaths];
  await page.route('**/api/v1/**',async route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(referencePaths.includes(path)){
      reads.set(`${phase}:${path}`,(reads.get(`${phase}:${path}`)??0)+1);
      if(phase==='denied'||phase==='temporary')return route.fulfill({status:phase==='denied'?403:503,json:{title:'名称暂不可用',detail:'引用资料读取失败',status:phase==='denied'?403:503,code:phase==='denied'?'ACCESS_DENIED':'SERVICE_UNAVAILABLE',recoverable:phase==='temporary'}});
      const suffix=phase==='restored'?'恢复后':'原缓存';
      if(path==='/candidates/person-a')return route.fulfill({json:{id:'person-a',fullName:`${suffix}候选姓名`}});
      if(path==='/jds/jd-a/versions')return route.fulfill({json:{...empty,items:[{id:'jd-v2',versionNo:2,title:`${suffix}岗位名称`}]}});
      if(path==='/resume-version-references')return route.fulfill({json:[{id:'resume-v1',versionNo:1,originalFileName:`${suffix}简历.pdf`}]});
      if(path==='/jd-versions/jd-v2')return route.fulfill({json:{id:'jd-v2',jdId:'jd-a',versionNo:2,title:`${suffix}材料岗位`,rawText:`${suffix}岗位原文`}});
      return route.fulfill({json:[{id:'hr-b',displayName:`${suffix}负责人`,status:'ACTIVE'}]});
    }
    if(path==='/resume-versions/resume-v1/download-authorizations')return route.fulfill({json:{token:'reference-preview',expiresAt:'2026-09-07T02:00:00Z'}});
    if(path==='/download-authorizations/reference-preview')return route.fulfill({contentType:'application/pdf',body:'%PDF-1.4'});
    if(path==='/applications')return route.fulfill({json:{...empty,items:[details.application]}});
    if(path==='/applications/reference-app')return route.fulfill({json:details});
    if(['/applications/reference-app/communications','/applications/reference-app/intentions'].includes(path))return route.fulfill({json:[]});
    return route.fallback();
  });
  await page.goto('/recruitment?application=reference-app');
  const dialog=page.getByRole('dialog',{name:'招聘活动详情'});
  const materials=page.getByRole('dialog',{name:'招聘材料'});
  const openMaterials=async()=>{
    await dialog.getByRole('button',{name:'查看简历与 JD',exact:true}).click();
    await expect(materials).toBeVisible();
  };
  const closeMaterials=async()=>{
    await materials.getByRole('button',{name:'关闭',exact:true}).click();
    await expect(materials).not.toBeVisible();
  };
  for(const text of ['原缓存候选姓名','原缓存岗位名称','原缓存负责人'])await expect(dialog).toContainText(text);
  await openMaterials();
  for(const text of ['原缓存简历.pdf','原缓存材料岗位'])await expect(materials).toContainText(text);
  await closeMaterials();
  // 关闭会清除 application 参数并卸载详情内容；重新打开后沿用同一 QueryClient 重读过期引用。
  const refresh=async(seconds:number)=>{
    await page.clock.setFixedTime(new Date(Date.parse('2026-09-07T01:30:00Z')+seconds*1000));
    await dialog.getByRole('button',{name:'关闭',exact:true}).click();
    await expect(dialog).not.toBeVisible();
    await expect(page).not.toHaveURL(/application=reference-app/);
    await page.getByRole('button',{name:'详情与版本',exact:true}).click();
    await expect(dialog).toBeVisible();
    await expect.poll(()=>detailReferencePaths.every(path=>(reads.get(`${phase}:${path}`)??0)>0)).toBe(true);
    await openMaterials();
    await expect.poll(()=>materialReferencePaths.every(path=>(reads.get(`${phase}:${path}`)??0)>0)).toBe(true);
  };
  phase='denied';await refresh(61);
  for(const text of ['原缓存简历.pdf','原缓存材料岗位'])await expect(materials).not.toContainText(text);
  for(const text of ['简历读取失败','岗位 JD 读取失败'])await expect(page.locator('.error-notification').filter({hasText:text}).last()).toBeVisible();
  await closeMaterials();
  for(const text of ['原缓存候选姓名','原缓存岗位名称','原缓存负责人'])await expect(dialog).not.toContainText(text);
  for(const text of ['候选人信息暂不可用','岗位名称暂不可用','成员姓名暂不可用'])await expect(dialog).toContainText(text);
  await dialog.screenshot({path:info.outputPath('business-reference-denied.png')});
  expect((await new AxeBuilder({page}).include('.ant-modal').analyze()).violations).toEqual([]);
  phase='restored';await refresh(122);
  for(const text of ['恢复后简历.pdf','恢复后材料岗位'])await expect(materials).toContainText(text);
  await closeMaterials();
  for(const text of ['恢复后候选姓名','恢复后岗位名称','恢复后负责人'])await expect(dialog).toContainText(text);
  phase='temporary';await refresh(183);
  for(const text of ['恢复后简历.pdf','恢复后材料岗位','简历信息刷新失败，继续显示上次已授权内容','岗位 JD 刷新失败，继续显示上次已授权内容'])await expect(materials).toContainText(text);
  await closeMaterials();
  for(const text of ['恢复后候选姓名','恢复后岗位名称','恢复后负责人'])await expect(dialog).toContainText(text);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  expect(errors).toEqual([]);
  await page.screenshot({path:info.outputPath('business-reference-permission.png'),fullPage:true});
});

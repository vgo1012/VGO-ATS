import type { Page } from '@playwright/test';

const empty = { items: [], hasMore: false, nextCursor: null };
const actor = { userId:'hr-a',displayName:'陈佳',workEmail:'chen@example.com',homeDepartmentId:'dept-a',permissions:[],roles:['HR'],superAdmin:true };
const job = { id:'jd-a',demandId:'demand-a',demandCode:'FE-2026-036',title:'高级前端工程师（招聘平台与复杂业务系统方向）',currentVersionNo:2,currentParseStatus:'SUCCEEDED' };
const person = { id:'person-a',fullName:'林雨桐',primaryEmail:'lin@example.com',primaryPhone:'13800001234',ownerUserId:'hr-a',createTime:'2026-09-05T01:00:00Z',updateTime:'2026-09-07T01:00:00Z' };
const profile = {profile:{id:'weight-a',name:'技术岗位通用比例',ownerUserId:actor.userId,version:'1'},currentVersion:{id:'weight-v1',versionNo:1,skillsWeight:20,roleWeight:20,yearsWeight:20,responsibilityWeight:20,industryProjectWeight:20}};
export const companyScoring = {plan:{id:'company-plan',name:'公司默认五维评分'},version:{id:'company-v1',versionNo:1,version:1,status:'ACTIVE',skillsEnabled:true,roleEnabled:true,yearsEnabled:true,responsibilityEnabled:true,industryProjectEnabled:true},criterionPlan:{planMaxBonusPoints:0},criteria:[]};
const definition = {code:'resumes',name:'新增简历',category:'funnel',versionNo:1,eventType:'RESUME_INGESTED',definitionText:'统计所选日期范围内成功入库的简历版本。同一候选人的不同简历版本分别计入。'};

export async function reviewWorkspace(page: Page) {
  await page.clock.setFixedTime(new Date('2026-09-07T01:30:00Z'));
  await page.route('**/api/v1/**',route=>{
    const url=new URL(route.request().url()),path=url.pathname.replace('/api/v1','');
    let body:unknown=empty;
    if(path==='/me')body=actor;
    if(path==='/auth/csrf')body={headerName:'X-CSRF-TOKEN',parameterName:'_csrf',token:'fixture'};
    if(['/workbench','/management/workbench','/my/interview-tasks','/tasks','/admin/super-admins','/directory/users/resolve'].includes(path))body=[];
    if(path==='/directory/users')body={...empty,items:[{id:actor.userId,displayName:actor.displayName,status:'ACTIVE'}]};
    if(path==='/directory/departments')body=[{id:'dept-a',code:'RECRUITING',name:'招聘团队',parentDepartmentId:null,status:'ACTIVE'}];
    if(path==='/jds')body={...empty,items:[job]};
    if(path==='/jds/count')body={count:1,nextCursor:null,hasMore:false};
    if(path==='/jds/jd-a')body=job;
    if(path==='/jd-versions/jd-v2')body={id:'jd-v2',jdId:job.id,title:job.title,versionNo:2,rawText:'负责招聘平台前端开发与复杂业务系统建设。',status:'PUBLISHED',sourceType:'MANUAL',createId:actor.userId,createTime:'2026-09-01T00:00:00Z'};
    if(path==='/jds/jd-a/versions')body={...empty,items:[{id:'jd-v2',jdId:job.id,title:job.title,versionNo:2}]};
    if(path==='/candidates')body={...empty,items:[person]};
    if(path==='/candidates/person-a')body=person;
    if(path==='/candidate-batch')body=url.searchParams.getAll('candidateIds').includes(person.id)?[person]:[];
    if(path==='/weight-profiles')body={...empty,items:[profile]};
    if(path==='/weight-profiles/count')body={count:1,nextCursor:null,hasMore:false};
    if(path==='/applications/count'||path==='/clients/count'||path==='/mail-resume-reviews/count')body={count:0,nextCursor:null,hasMore:false};
    if(path==='/scoring-defaults')body={scoringConfiguration:companyScoring,weightProfile:profile};
    if(path==='/admin/jd-scoring-configs')body={...empty,items:[companyScoring]};
    if(['/candidate-blacklist-tags','/candidate-pool-statuses','/candidate-resume-summaries','/resume-version-fact-summaries','/resume-version-sources','/resume-version-references','/applications/stage-counts'].includes(path))body=[];
    if(path.endsWith('/company-blacklist-records'))body={current:null,active:false,records:empty};
    if(path.startsWith('/reports/'))body={definitions:[definition],points:[{eventType:'RESUME_INGESTED',eventCount:8,eventValue:8,lastOccurredAt:'2026-09-07T01:00:00Z'}],days:[{date:'2026-09-07',eventType:'RESUME_INGESTED',eventCount:8}],dataUpdatedAt:'2026-09-07T01:00:00Z'};
    if(path==='/admin/users')body=[{id:'hr-a',displayName:'陈佳',workEmail:'chen@example.com',status:'ACTIVE',roles:['RECRUITER'],mustChangePassword:false,homeDepartmentId:'dept-a',version:'1'}];
    if(path==='/admin/departments')body=[{id:'dept-a',name:'招聘团队',status:'ACTIVE',version:'1'}];
    return route.fulfill({json:body});
  });
}

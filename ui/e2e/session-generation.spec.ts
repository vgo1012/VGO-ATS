import { expect, test } from '@playwright/test';
import { reviewWorkspace } from './fixtures/ux-review';
import { emptyRecruitmentAnalysis } from './fixtures/recruitment-analysis';

test('登录成功后取消旧身份读取，晚到旧身份不会覆盖或阻塞新账号',async({page})=>{
  await reviewWorkspace(page);
  const actor={userId:'new-user',displayName:'新登录用户',workEmail:'new@example.com',homeDepartmentId:'dept-a',roles:['SUPER_ADMIN'],permissions:[],superAdmin:true,mustChangePassword:false,version:'1'};
  let release!:()=>void;const pending=new Promise<void>(resolve=>{release=resolve;});
  await page.route('**/api/v1/reports/recruitment-analysis?**',route=>route.fulfill({json:emptyRecruitmentAnalysis}));
  await page.route('**/api/v1/me',async route=>{await pending;await route.fulfill({json:{...actor,userId:'old-user',displayName:'旧登录用户'}});});
  await page.route('**/api/v1/auth/login',route=>route.fulfill({json:actor}));
  await page.goto('/login');
  await page.getByLabel('账号',{exact:true}).fill('new@example.com');
  await page.getByLabel('密码',{exact:true}).fill('Synthetic-Password-123');
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.getByRole('button',{name:'打开新登录用户的个人菜单'})).toBeVisible();
  const response=page.waitForResponse(item=>item.url().endsWith('/me'));
  release();await (await response).finished();
  await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
  await expect(page.getByRole('button',{name:'打开新登录用户的个人菜单'})).toBeVisible();
  await expect(page.getByRole('button',{name:'打开旧登录用户的个人菜单'})).toHaveCount(0);
});

for(const malformed of [false,true])test(`重新登录后旧${malformed?'截断':'正常'}401不清新会话，当前401仍退出`,async({page},testInfo)=>{
  await reviewWorkspace(page);
  const actor={userId:'new-user',displayName:'新登录用户',workEmail:'new@example.com',homeDepartmentId:'dept-a',roles:['SUPER_ADMIN'],permissions:[],superAdmin:true,mustChangePassword:false,version:'1'};
  let authenticated=true;
  let loggedIn=false;
  let releaseOld!:()=>void;
  const oldPending=new Promise<void>(resolve=>{releaseOld=resolve;});
  let oldStarted=false;
  let releaseFirst!:()=>void;
  const firstPending=new Promise<void>(resolve=>{releaseFirst=resolve;});
  let initialDepartments=true;
  let initialUsers=true;
  const problem={title:'会话失效',status:401,detail:'请重新登录',code:'SESSION_REQUIRED',recoverable:false};
  await page.route('**/api/v1/me',route=>route.fulfill({status:authenticated?200:401,json:authenticated?{...actor,displayName:loggedIn?'新登录用户':'旧登录用户'}:problem}));
  await page.route('**/api/v1/auth/login',route=>{authenticated=true;loggedIn=true;return route.fulfill({json:actor});});
  await page.route('**/api/v1/admin/users?**',async route=>{
    if(initialUsers){initialUsers=false;oldStarted=true;await oldPending;
      return malformed?route.fulfill({status:401,contentType:'application/problem+json',body:'{'}):route.fulfill({status:401,json:problem});
    }
    return route.fulfill({json:[]});
  });
  await page.route('**/api/v1/admin/departments',async route=>{
    if(initialDepartments){initialDepartments=false;await firstPending;authenticated=false;return route.fulfill({status:401,json:problem});}
    return route.fulfill({json:[]});
  });
  await page.goto('/admin/identity');
  await expect.poll(()=>oldStarted).toBe(true);
  releaseFirst();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('账号',{exact:true}).fill('new@example.com');
  await page.getByLabel('密码',{exact:true}).fill('Synthetic-Password-123');
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page).toHaveURL(/\/admin\/identity$/);
  await expect(page.getByRole('button',{name:'打开新登录用户的个人菜单'})).toBeVisible();
  const oldResponse=page.waitForResponse(response=>response.url().includes('/admin/users?')&&response.status()===401);
  releaseOld();await (await oldResponse).finished();
  // 等待旧响应后的浏览器绘制，确保检查的是其副作用发生之后的会话。
  await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
  await expect(page).toHaveURL(/\/admin\/identity$/);
  await expect(page.getByRole('button',{name:'打开新登录用户的个人菜单'})).toBeVisible();
  await page.route('**/api/v1/source-channels?**',route=>{authenticated=false;return route.fulfill({status:401,json:problem});});
  if(testInfo.project.name==='mobile'){
    await page.getByRole('combobox',{name:'切换设置页面'}).click();
    await page.locator('.ant-select-item-option-content').getByText('来源渠道',{exact:true}).click();
  }else await page.getByRole('link',{name:/来源渠道/}).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading',{name:'欢迎回来'})).toBeVisible();
});

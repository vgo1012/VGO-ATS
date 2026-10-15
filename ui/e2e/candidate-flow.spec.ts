import { expect, test, type Page } from '@playwright/test';
import { rememberDialog } from './dialogFlow';
import { reviewDialog } from './dialogReview';

const empty = { items: [], hasMore: false, nextCursor: null };
const person = { id: 'flow-person', fullName: '林雨桐', primaryEmail: 'flow@example.com', ownerUserId: 'flow-user', createdAt: '2026-09-03T03:00:00Z' };
async function workspace(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = ['/workbench', '/management/workbench', '/my/interview-tasks', '/tasks', '/candidate-blacklist-tags', '/candidate-pool-statuses', '/candidate-resume-summaries', '/resume-version-fact-summaries', '/resume-version-sources', '/resume-version-references', '/directory/departments'].includes(path) ? [] : empty;
    if (path === '/me') body = { userId: 'flow-user', displayName: '招聘负责人', workEmail: 'flow@example.com', roles: [], permissions: [], superAdmin: true, mustChangePassword: false, version: '1' };
    if (path === '/auth/csrf') body = { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'flow-csrf' };
    if (path.startsWith('/admin/') || path === '/directory/users/resolve') body = [];
    if (path === '/candidates') body = { ...empty, items: [person] };
    if (path === `/candidates/${person.id}`) body = person;
    return route.fulfill({ json: body });
  });
}

test('candidate detail error and retry stay in the current window with an empty version list', async ({ page }) => {
  await workspace(page);
  let recover = false;
  await page.route(`**/api/v1/candidates/${person.id}`, route => recover ? route.fulfill({ json: person }) : route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: '服务暂不可用', detail: '候选人读取失败，请重试', code: 'UNAVAILABLE', recoverable: true } }));
  await page.goto('/talents');
  await page.getByRole('button', { name: '详情与版本' }).click();
  const continuous = await rememberDialog(page);
  await expect(page.getByText('候选人读取失败，请重试')).toBeVisible();
  await continuous('候选人详情');
  recover = true;
  await page.getByRole('button', { name: '重试候选人详情' }).click();
  const dialog = page.getByRole('dialog', { name: '林雨桐' });
  await expect(dialog).toContainText('flow@example.com');
  await dialog.getByRole('button', { name: '查看面试材料' }).click();
  const materials = page.getByRole('dialog', { name: '面试材料', exact: true });
  await expect(materials).toContainText('暂无简历文件');
  await materials.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(materials).not.toBeVisible();
  await continuous('林雨桐');
});

test('JD detail and editor restore focus to their table controls', async ({ page }) => {
  await workspace(page);
  const demand = { id: 'flow-demand', title: '前端工程师', code: 'FLOW-JD', recruitmentType: 'INTERNAL', status: 'ACTIVE', headcount: 2, version: 1 };
  const version = { id: 'flow-jd-version', title: demand.title, versionNo: 1, rawText: '负责招聘系统前端开发', createTime: '2026-09-05T00:00:00Z' };
  await page.route('**/api/v1/jds?*', route => route.fulfill({ json: { ...empty, items: [demand] } }));
  await page.route('**/api/v1/jds/flow-demand/versions?*', route => route.fulfill({ json: { ...empty, items: [version] } }));
  await page.route('**/api/v1/jd-versions/flow-jd-version/facts', route => route.fulfill({ status: 404, json: { status: 404, title: 'No facts', detail: '尚未解析' } }));
  await page.goto('/recruitment/setup');
  const detailTrigger = page.getByRole('button', { name: '前端工程师', exact: true });
  await detailTrigger.click();
  await expect(page.getByRole('dialog', { name: '前端工程师', exact: true })).toBeVisible();
  await reviewDialog(page, 'jd-detail');
  await page.getByRole('dialog').press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(detailTrigger).toBeFocused();

  const editTrigger = page.getByRole('button', { name: '更新', exact: true });
  await editTrigger.click();
  await expect(page.getByRole('dialog', { name: '更新 JD', exact: true })).toBeVisible();
  await reviewDialog(page, 'jd-editor');
  await page.getByRole('dialog').press('Escape');
  await expect(page.getByRole('dialog', { name: '前端工程师', exact: true })).toBeVisible();
  await page.getByRole('dialog', { name: '前端工程师', exact: true }).press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(editTrigger).toBeFocused();
});

test('account save retains draft on failure, blocks duplicates and hands off to credentials in place', async ({ page }) => {
  const temporaryPassword = 'Synthetic_Create_Credential_1234';
  await workspace(page);
  await page.route('**/api/v1/admin/departments', route => route.fulfill({ json: [{ id: 'flow-department', name: '研发', status: 'ACTIVE' }] }));
  let attempts = 0;
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/admin/users', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    expect(route.request().postDataJSON()).toMatchObject({ displayName: '测试同事', workEmail: 'flow-new@example.com', roles: ['RECRUITER'] });
    attempts++;
    if (attempts === 1) return route.fulfill({ status: 503, contentType: 'application/problem+json', json: { status: 503, title: 'Unavailable', detail: '注册暂不可用，请重试', code: 'UNAVAILABLE', recoverable: true } });
    await pending;
    return route.fulfill({ json: { userId: 'flow-new-user', oneTimeCredential: temporaryPassword, mustChangePassword: true, version: '1' } });
  });
  await page.goto('/admin/identity');
  const trigger = page.getByRole('button', { name: '注册账号' });
  await trigger.click();
  const continuous = await rememberDialog(page);
  await page.getByLabel('姓名', { exact: true }).fill('测试同事');
  await page.getByLabel('工作邮箱', { exact: true }).fill('  FLOW-NEW@EXAMPLE.COM  ');
  await expect(page.getByLabel('工作邮箱', { exact: true })).toHaveValue('flow-new@example.com');
  await page.getByLabel('所属部门', { exact: true }).click();
  await page.locator('.ant-select-item-option-content').getByText('研发', { exact: true }).click();
  const save = page.getByRole('button', { name: '保存账号' });
  await save.click();
  await expect(page.getByText('注册暂不可用，请重试')).toBeVisible();
  await expect(page.getByLabel('姓名', { exact: true })).toHaveValue('测试同事');
  await continuous('注册账号');
  await save.dblclick();
  await expect(save).toBeDisabled();
  await page.getByRole('dialog').press('Escape');
  await continuous('注册账号');
  expect(attempts).toBe(2);
  release();
  await continuous('临时登录密码');
  await expect(page.getByRole('dialog')).toContainText(temporaryPassword);
  await expect(page.getByRole('dialog')).not.toContainText('flow-new@example.com');
  await reviewDialog(page, 'continuous-credentials');
  await page.getByRole('button', { name: '我已知悉' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

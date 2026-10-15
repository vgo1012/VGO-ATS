import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page, type Route, type TestInfo } from '@playwright/test';
import { companyScoring, reviewWorkspace } from './fixtures/ux-review';

const pageResult = (items: unknown[]) => ({ items, hasMore: false, nextCursor: null });
type FixtureCriterion = {
  criterionCode: string;
  level: string;
  operator: string;
  expectedValueJson: string;
  bonusPoints: number;
  reason: string;
  evidencePolicy: string;
  selectableByHr: boolean;
  sequenceNo: number;
};
const draftV2 = {
  ...companyScoring,
  plan: { ...companyScoring.plan, name: '公司默认五维评分', currentVersionNo: 2 },
  version: { ...companyScoring.version, id: 'company-v2', versionNo: 2, version: 2, status: 'DRAFT' as const },
  criteria: [] as FixtureCriterion[],
};

type PatchAttempt = { key: string | undefined; body: Record<string, unknown> };

async function prepare(page: Page, testInfo: TestInfo) {
  await page.setViewportSize({ width: testInfo.project.name === 'mobile' ? 390 : 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await reviewWorkspace(page);
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  return pageErrors;
}

async function openDraft(page: Page) {
  await page.goto('/admin/scoring');
  const row = page.getByRole('row').filter({ hasText: 'V2' });
  await expect(row).toContainText('草稿');
  await row.getByRole('button', { name: /^编\s*辑$/ }).click();
  await expect(page.getByRole('dialog')).toHaveAccessibleName('编辑评分方案 · V2');
  await expect(page.getByRole('dialog')).not.toHaveClass(/appear|enter/);
}

async function editDraft(page: Page, name: string) {
  await page.getByRole('textbox', { name: '方案名称' }).fill(name);
  await page.getByRole('combobox', { name: '添加附加要求' }).click();
  await expect(page.locator('.ant-select-item-option-content').filter({ hasText: '证书' })).toBeVisible();
  await expect(page.locator('.ant-select-dropdown')).not.toHaveClass(/appear|enter/);
  await page.locator('.ant-select-item-option-content').filter({ hasText: '证书' }).click();
  await page.getByRole('combobox', { name: '证书要求' }).fill('PMP');
  await page.getByRole('combobox', { name: '证书要求' }).press('Enter');
  await page.getByRole('textbox', { name: '方案名称' }).click();
}

function expectCompleteBody(attempt: PatchAttempt, name: string) {
  expect(attempt.body).toEqual({
    name,
    professionalDimensions: {
      skills: true,
      roleExperience: true,
      relatedYears: true,
      responsibilityMatch: true,
      industryAndProjects: true,
    },
    criteria: [{
      code: 'CERTIFICATE',
      level: 'MUST',
      operator: 'IN',
      expectedValueJson: '{"values":["PMP"]}',
      bonusPoints: 0,
      selectableByHr: false,
      reason: '按招聘岗位要求核对明确事实',
      evidencePolicy: 'EXPLICIT_FACT_ONLY',
    }],
    version: 2,
  });
}

function reject(route: Route, status: 400 | 409 | 422, detail: string) {
  return route.fulfill({
    status,
    contentType: 'application/problem+json',
    json: { status, title: '评分草稿保存失败', detail, code: status === 409 ? 'MATCH_PLAN_VERSION_CONFLICT' : 'VALIDATION_FAILED', recoverable: false },
  });
}

test('replays an uncertain scoring draft PATCH with the same complete body and operation key', async ({ page }, testInfo) => {
  const pageErrors = await prepare(page, testInfo);
  const attempts: PatchAttempt[] = [];
  const savedByKey = new Map<string, typeof draftV2>();
  let persisted = draftV2;
  let saveCount = 0;
  let releaseReplay: (() => void) | undefined;

  await page.route('**/api/v1/admin/jd-scoring-configs**', async route => {
    const request = route.request();
    if (request.method() !== 'PATCH') return route.fulfill({ json: pageResult([persisted, companyScoring]) });
    const attempt = { key: request.headers()['idempotency-key'], body: request.postDataJSON() as Record<string, unknown> };
    attempts.push(attempt);
    const key = attempt.key ?? '';
    const replay = savedByKey.get(key);
    if (replay) {
      await new Promise<void>(resolve => { releaseReplay = resolve; });
      return route.fulfill({ json: replay });
    }
    if (attempt.body.version !== persisted.version.version) return reject(route, 409, '评分方案已被其他管理员修改');
    persisted = {
      ...persisted,
      plan: { ...persisted.plan, name: String(attempt.body.name) },
      version: { ...persisted.version, version: persisted.version.version + 1 },
      criteria: [{ criterionCode: 'CERTIFICATE', level: 'MUST', operator: 'IN', expectedValueJson: '{"values":["PMP"]}', bonusPoints: 0, reason: '', evidencePolicy: 'FACT_REQUIRED', selectableByHr: false, sequenceNo: 1 }],
    };
    saveCount++;
    savedByKey.set(key, persisted);
    return route.fulfill({ status: 200, contentType: 'application/json', body: '{invalid-json' });
  });

  await openDraft(page);
  await editDraft(page, '技术岗位恢复方案');
  const dialog = page.getByRole('dialog', { name: '编辑评分方案 · V2' });
  const save = dialog.getByRole('button', { name: '保存草稿', exact: true });
  await save.click();
  await expect.poll(() => attempts.length).toBe(1);
  expectCompleteBody(attempts[0]!, '技术岗位恢复方案');
  expect(attempts[0]?.key).toBeTruthy();
  await expect(dialog.getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  await expect(dialog.getByRole('checkbox', { name: '相关年限' })).toBeDisabled();
  await expect(dialog.getByRole('combobox', { name: '添加附加要求' })).toBeDisabled();
  await expect(dialog.getByRole('combobox', { name: '证书要求' })).toBeDisabled();
  await expect(save).toBeEnabled();
  await page.getByRole('button', { name: '关闭错误提示' }).click();

  await save.focus();
  await page.keyboard.press('Enter');
  await expect.poll(() => attempts.length).toBe(2);
  await expect(save).toBeDisabled();
  await expect(dialog.locator('.ant-modal-close')).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await save.evaluate((button: HTMLButtonElement) => button.click());
  expect(attempts).toHaveLength(2);
  expect(attempts[1]?.key).toBe(attempts[0]?.key);
  expect(attempts[1]?.body).toEqual(attempts[0]?.body);
  expect(saveCount).toBe(1);
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').analyze()).violations).toEqual([]);
  await page.screenshot({ path: testInfo.outputPath(`scoring-save-replay-pending-${testInfo.project.name}.png`), fullPage: true });

  releaseReplay?.();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('row').filter({ hasText: 'V2' })).toContainText('1 项');
  expect(pageErrors).toEqual([]);
});

for (const status of [400, 422] as const) {
  test(`releases a scoring draft after a ${status} rejection and retries corrected values with a new key`, async ({ page }, testInfo) => {
    const pageErrors = await prepare(page, testInfo);
    const attempts: PatchAttempt[] = [];
    let persisted = draftV2;

    await page.route('**/api/v1/admin/jd-scoring-configs**', route => {
      const request = route.request();
      if (request.method() !== 'PATCH') return route.fulfill({ json: pageResult([persisted, companyScoring]) });
      attempts.push({ key: request.headers()['idempotency-key'], body: request.postDataJSON() as Record<string, unknown> });
      if (attempts.length === 1) return reject(route, status, '方案名称不符合保存规则');
      persisted = { ...persisted, plan: { ...persisted.plan, name: String(attempts[1]!.body.name) }, version: { ...persisted.version, version: 3 } };
      return route.fulfill({ json: persisted });
    });

    await openDraft(page);
    await editDraft(page, '待纠正评分方案');
    const dialog = page.getByRole('dialog', { name: '编辑评分方案 · V2' });
    const name = dialog.getByRole('textbox', { name: '方案名称' });
    const save = dialog.getByRole('button', { name: '保存草稿', exact: true });
    await save.click();
    await expect.poll(() => attempts.length).toBe(1);
    await expect(name).toBeEnabled();
    await page.getByRole('button', { name: '关闭错误提示' }).click();
    await name.fill('已纠正评分方案');
    await save.click();
    await expect.poll(() => attempts.length).toBe(2);

    expectCompleteBody(attempts[0]!, '待纠正评分方案');
    expectCompleteBody(attempts[1]!, '已纠正评分方案');
    expect(attempts[0]?.key).toBeTruthy();
    expect(attempts[1]?.key).toBeTruthy();
    expect(attempts[1]?.key).not.toBe(attempts[0]?.key);
    expect(attempts[1]?.body.version).toBe(attempts[0]?.body.version);
    await expect(dialog).toHaveCount(0);
    expect(pageErrors).toEqual([]);
  });
}

test('does not repeat a successful PATCH while the scoring list automatically retries its refresh', async ({ page }, testInfo) => {
  const pageErrors = await prepare(page, testInfo);
  let patchCount = 0;
  let listCount = 0;
  let persisted = draftV2;

  await page.route('**/api/v1/admin/jd-scoring-configs**', route => {
    const request = route.request();
    if (request.method() === 'PATCH') {
      patchCount++;
      persisted = { ...persisted, plan: { ...persisted.plan, name: String((request.postDataJSON() as Record<string, unknown>).name) }, version: { ...persisted.version, version: 3 } };
      return route.fulfill({ json: persisted });
    }
    listCount++;
    if (listCount === 2) return route.fulfill({ status: 200, contentType: 'application/json', body: '{invalid-json' });
    return route.fulfill({ json: pageResult([persisted, companyScoring]) });
  });

  await openDraft(page);
  await page.getByRole('textbox', { name: '方案名称' }).fill('回调恢复方案');
  const dialog = page.getByRole('dialog', { name: '编辑评分方案 · V2' });
  const save = dialog.getByRole('button', { name: '保存草稿', exact: true });
  await save.click();
  await expect(dialog).toHaveCount(0);
  expect(patchCount).toBe(1);
  await expect.poll(() => listCount).toBe(3);
  expect(pageErrors).toEqual([]);
});

test('refreshes a conflicted draft and reopens the latest version before saving', async ({ page }, testInfo) => {
  const pageErrors = await prepare(page, testInfo);
  const attempts: PatchAttempt[] = [];
  let persisted = draftV2;
  await page.route('**/api/v1/admin/jd-scoring-configs**', route => {
    const request = route.request();
    if (request.method() !== 'PATCH') return route.fulfill({ json: pageResult([persisted, companyScoring]) });
    const attempt = { key: request.headers()['idempotency-key'], body: request.postDataJSON() as Record<string, unknown> };
    attempts.push(attempt);
    if (attempts.length === 1) {
      persisted = { ...draftV2, plan: { ...draftV2.plan, name: '其他管理员的最新方案' }, version: { ...draftV2.version, version: 3 } };
      return reject(route, 409, '评分方案已被其他管理员修改');
    }
    if (attempt.body.version !== persisted.version.version) return reject(route, 409, '评分方案版本冲突');
    persisted = { ...persisted, plan: { ...persisted.plan, name: String(attempt.body.name) }, version: { ...persisted.version, version: 4 } };
    return route.fulfill({ json: persisted });
  });
  await openDraft(page);
  await page.getByRole('textbox', { name: '方案名称' }).fill('旧页面上的修改');
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  await page.getByRole('button', { name: '关闭错误提示' }).click();
  await page.getByRole('button', { name: '刷新列表', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(attempts).toHaveLength(1);
  await page.getByRole('row').filter({ hasText: 'V2' }).getByRole('button', { name: /^编\s*辑$/ }).click();
  await expect(page.getByRole('textbox', { name: '方案名称' })).toHaveValue('其他管理员的最新方案');
  await page.getByRole('textbox', { name: '方案名称' }).fill('核对后的方案');
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(attempts).toHaveLength(2);
  expect(attempts[1]!.body).toMatchObject({ name: '核对后的方案', version: 3 });
  expect(attempts[1]!.key).not.toBe(attempts[0]!.key);
  expect(pageErrors).toEqual([]);
});

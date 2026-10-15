import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import type { ApplicationDetails } from '../src/recruitment/api';
import { reviewWorkspace } from './fixtures/ux-review';

const empty = { items: [], hasMore: false, nextCursor: null };
const instant = '2026-09-07T01:30:00Z';
const details: ApplicationDetails = {
  application: {
    id: 'communication-app', candidateId: 'person-a', demandId: 'demand-a', jdId: 'jd-a', jdVersionId: 'jd-v2', resumeVersionId: 'resume-v1',
    workflowTemplateVersionId: 'workflow-v1', matchPlanVersionId: 'match-v1', recruitmentType: 'INTERNAL',
    progressiveFollowUp: true, currentStage: 'IN_PROCESS', outcome: null, ownerUserId: 'hr-a', ownerDepartmentId: 'dept-a', terminalAt: null,
    version: 1, createId: 'hr-a', createTime: instant, updateId: 'hr-a', updateTime: instant,
  },
  collaboratorUserIds: [], decisions: [], offerStatusRecords: [], elapsedSeconds: 300,
  workflowSteps: [{
    id: 'interview-step', applicationId: 'communication-app', templateVersionId: 'workflow-v1', stepDefinitionId: 'definition-1',
    stepCode: 'INTERVIEW_ROUND_1', sequenceNo: 1, roundNo: 1, candidateOptional: true, defaultFollowUpChannel: null,
    status: 'IN_PROGRESS', ownerUserId: 'hr-a', dueAt: null, startedAt: instant, completedAt: null, resultReason: null, clientSubmissionId: null, version: 1,
  }],
};

async function fixture(page: Page, mode: 'lost-response' | 'validation') {
  await reviewWorkspace(page);
  const requests: Array<{ key: string | null; body: Record<string, unknown> }> = [];
  const records: Array<Record<string, unknown>> = [];
  const saved = new Map<string, { signature: string; record: Record<string, unknown> }>();
  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    const problem = (status: number, code: string, detail: string) => route.fulfill({ status, contentType: 'application/problem+json', json: {
      status, code, title: '沟通保存失败', detail, recoverable: false,
    } });
    if (path === '/applications') return route.fulfill({ json: { ...empty, items: [details.application] } });
    if (path === '/applications/communication-app') return route.fulfill({ json: details });
    if (path === '/applications/communication-app/intentions') return route.fulfill({ json: [] });
    if (path === '/applications/communication-app/communications') {
      if (request.method() === 'GET') return route.fulfill({ json: records });
      const body = request.postDataJSON() as Record<string, unknown>;
      const key = request.headers()['idempotency-key'] ?? null;
      requests.push({ key, body });
      if (!key) return problem(400, 'IDEMPOTENCY_KEY_REQUIRED', '缺少沟通记录幂等键');
      if (mode === 'validation' && requests.length === 1) return problem(422, 'COMMUNICATION_RESULT_INVALID', '沟通结果校验未通过，请核对后重新保存');
      const signature = JSON.stringify(body);
      const previous = saved.get(key);
      if (previous) return previous.signature === signature
        ? route.fulfill({ json: previous.record })
        : problem(409, 'IDEMPOTENCY_KEY_REUSED', '同一幂等键的沟通内容不能变化');
      const record = { ...body, id: `communication-${records.length + 1}`, applicationId: details.application.id, recordedByUserId: 'hr-a', communicatedAt: instant };
      saved.set(key, { signature, record });
      records.push(record);
      if (mode === 'lost-response' && requests.length === 1) return route.fulfill({ status: 200, contentType: 'application/json', body: '{"id":' });
      return route.fulfill({ json: record });
    }
    return route.fallback();
  });
  return { requests, records };
}

async function openRecord(page: Page) {
  await page.goto('/recruitment');
  await page.getByRole('row').filter({ hasText: '林雨桐' }).getByRole('button', { name: '待定', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '待定', exact: true });
  await dialog.getByLabel('沟通情况', { exact: true }).fill('候选人已确认本周可面试，明天确认具体时间');
  return dialog;
}

test('待定沟通响应丢失后冻结内容，同键重试仅写一条事实', async ({ page }, testInfo) => {
  const state = await fixture(page, 'lost-response');
  const dialog = await openRecord(page);
  await dialog.getByRole('button', { name: /保存记录/ }).click();
  await expect(dialog.getByText('保存结果尚未确认，请重试核对；当前内容已保留。', { exact: true })).toBeVisible();
  const retry = dialog.getByRole('button', { name: /重试保存记录/ });
  await expect(retry).toBeEnabled();
  await expect(dialog.getByLabel('沟通情况', { exact: true })).toHaveValue('候选人已确认本周可面试，明天确认具体时间');
  await expect(dialog.getByLabel('沟通情况', { exact: true })).toBeDisabled();
  expect(state.records).toHaveLength(1);
  for (const close of await page.locator('.ant-notification-notice-close').all()) await close.click();
  expect((await new AxeBuilder({ page }).include('[role="dialog"]').withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([]);
  await dialog.screenshot({ path: testInfo.outputPath('communication-record-frozen.png') });
  await retry.focus();
  await page.keyboard.press('Enter');
  await expect(dialog).not.toBeVisible();
  expect(state.requests).toHaveLength(2);
  expect(state.requests[0]!.key).toBeTruthy();
  expect(state.requests[1]).toEqual(state.requests[0]);
  expect(state.records).toHaveLength(1);
  await page.getByRole('row').filter({ hasText: '林雨桐' }).getByRole('button', { name: '详情与版本', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '招聘活动详情', exact: true }).getByText('候选人已确认本周可面试，明天确认具体时间', { exact: true })).toBeVisible();
});

test('待定沟通 422 明确拒绝后允许修改，并使用新幂等键保存', async ({ page }) => {
  const state = await fixture(page, 'validation');
  const dialog = await openRecord(page);
  await dialog.getByRole('button', { name: /保存记录/ }).click();
  await expect(page.getByText('沟通结果校验未通过，请核对后重新保存', { exact: true }).first()).toBeVisible();
  const summary = dialog.getByLabel('沟通情况', { exact: true });
  await expect(summary).toBeEnabled();
  await expect(summary).toHaveValue('候选人已确认本周可面试，明天确认具体时间');
  await expect(dialog.getByRole('button', { name: '重试保存记录', exact: true })).toHaveCount(0);
  expect(state.records).toHaveLength(0);
  await summary.fill('已重新核对候选人的实际沟通情况');
  await dialog.getByRole('button', { name: /保存记录/ }).click();
  await expect(dialog).not.toBeVisible();
  expect(state.requests).toHaveLength(2);
  expect(state.requests[0]!.key).toBeTruthy();
  expect(state.requests[1]!.key).toBeTruthy();
  expect(state.requests[1]!.key).not.toBe(state.requests[0]!.key);
  expect(state.requests[1]!.body.summary).toBe('已重新核对候选人的实际沟通情况');
  expect(state.records).toHaveLength(1);
});

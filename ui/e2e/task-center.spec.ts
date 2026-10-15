import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

async function fixture(page: Page, admin = true) {
  const requests: string[] = [];
  let fail = false;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    requests.push(path);
    if (path === '/api/v1/me') return route.fulfill({ json: { userId: 'admin-task-center', displayName: '任务管理员', workEmail: 'synthetic@example.test', homeDepartmentId: 'hr', roles: [admin ? 'SUPER_ADMIN' : 'RECRUITER'], permissions: ['talent.read'], superAdmin: admin, mustChangePassword: false, version: '1' } });
    if (path === '/api/v1/admin/task-center') {
      if (fail) return route.fulfill({ status: 503, json: { title: '服务暂不可用', status: 503, code: 'UNAVAILABLE', detail: '请稍后重试', recoverable: true } });
      return route.fulfill({ json: {
        instanceId: 'synthetic-instance-0000-0000-000000000000', observedSince: '2026-09-16T00:00:00Z', sampledAt: '2026-09-16T01:00:00Z', tasks: [{
          code: 'RESUME_PARSE', name: '简历解析任务轮询', module: '简历解析', enabled: true,
          scheduleType: 'FIXED_DELAY', expression: '2000', zone: 'Asia/Shanghai', intervalMillis: 2000, nextCronAt: null,
          persistentBacklogStatus: 'AVAILABLE', persistentReadyCount: 3, oldestReadyAt: '2026-09-16T00:30:00Z',
          businessTypes: ['RESUME_PARSE'], channels: [{ code: 'RESUME_PARSE', lastTriggeredAt: '2026-09-16T01:00:00Z',
            lastCompletedAt: '2026-09-16T01:00:01Z', triggers: 1, coalesced: 0, rejected: 0, failed: 0, queued: 0, running: 0,
            recentScans: [{ runId: 'synthetic-scan', state: 'COMPLETED', queuedAt: '2026-09-16T01:00:00Z',
              startedAt: '2026-09-16T01:00:00Z', finishedAt: '2026-09-16T01:00:01Z', durationMillis: 1000, errorCode: null }],
          }],
        }, { code: 'AUDIT_CAPTURE_INCREMENTAL', name: '审计增量采集', module: '运营记录', enabled: false,
          scheduleType: 'FIXED_DELAY', expression: '3s', zone: 'Asia/Shanghai', intervalMillis: 3000, nextCronAt: null,
          persistentBacklogStatus: 'UNSUPPORTED', persistentReadyCount: null, oldestReadyAt: null,
          businessTypes: [], channels: [],
        }, { code: 'OUTBOX_PUBLISH', name: 'Outbox 发布', module: '集成事件', enabled: true,
          scheduleType: 'FIXED_DELAY', expression: '2s', zone: 'Asia/Shanghai', intervalMillis: 2000, nextCronAt: null,
          persistentBacklogStatus: 'AVAILABLE', persistentReadyCount: 0, oldestReadyAt: null,
          businessTypes: ['OUTBOX_PUBLISH'], channels: [],
        }],
      } });
    }
    if (path === '/api/v1/admin/task-center/history') return route.fulfill({ json: { items: [{
      taskId: 'SYNTHETIC000000000000000001', taskType: 'RESUME_PARSE', status: 'FAILED', progressPercent: 30,
      resourceId: 'SYNTHETIC-RESUME-001', resultResourceId: null,
      attemptCount: 1, maxAttempts: 3, errorCode: 'MODEL_UNAVAILABLE', errorDetail: '解析服务暂不可用，请在简历业务页面确认后重试',
      createTime: '2026-09-16T00:00:00Z', startedAt: '2026-09-16T00:00:01Z', finishedAt: '2026-09-16T00:00:02Z',
    }], hasMore: false, nextCursor: null } });
    return route.fulfill({ status: 404, json: { title: '未定义测试接口', status: 404, code: 'FIXTURE_NOT_FOUND', detail: path, recoverable: false } });
  });
  return { requests, fail: (value: boolean) => { fail = value; } };
}

test('admin inspects scans separately from durable failures without page overflow', async ({ page }) => {
  const state = await fixture(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/admin/task-center');
  await expect(page.getByRole('heading', { name: '任务运行中心' })).toBeVisible();
  await expect(page.getByText('轮次已完成', { exact: true })).toBeVisible();
  await expect(page.getByText('可领取 3', { exact: true })).toBeVisible();
  const emptyBacklog = page.getByText('可领取 0', { exact: true });
  await expect(emptyBacklog).toHaveCSS('color', 'rgb(35, 120, 4)');
  await expect(emptyBacklog).toHaveCSS('background-color', 'rgb(246, 255, 237)');
  await expect(page.getByText('30 分钟', { exact: true })).toBeVisible();
  await expect(page.getByText('非持久队列', { exact: true })).toBeVisible();
  await expect(page.getByText('审计增量采集', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  const violations = (await new AxeBuilder({ page }).include('section[aria-label="任务运行中心"]').analyze()).violations;
  expect(violations.filter(item => item.impact === 'serious' || item.impact === 'critical')).toEqual([]);
  await page.screenshot({ path: `test-results/task-center-${test.info().project.name}.png`, fullPage: true });
  await page.getByRole('button', { name: '查看简历解析任务轮询' }).click();
  await expect(page.getByText('轮次完成（非业务成功）')).toBeVisible();
  await expect(page.getByText('业务失败', { exact: true })).toBeVisible();
  await expect(page.getByText('解析服务暂不可用，请在简历业务页面确认后重试')).toBeAttached();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: '查看简历解析任务轮询' })).toBeFocused();
  state.fail(true);
  await page.getByRole('button', { name: '刷新运行状态' }).click();
  await expect(page.getByText(/当前未取得最新运行状态/)).toBeVisible();
  state.fail(false);
  await page.getByRole('button', { name: '刷新运行状态' }).click();
  await expect(page.getByText(/当前未取得最新运行状态/)).toHaveCount(0);
  expect(state.requests.filter(path=>path.includes('/resume-lifecycle/'))).toEqual([]);
  expect(errors).toEqual([]);
});

test('ordinary HR cannot enter or request administrator task data', async ({ page }) => {
  const state = await fixture(page, false);
  await page.goto('/admin/task-center');
  await expect(page.getByText('无权访问', { exact: true })).toBeVisible();
  expect(state.requests.filter(path => path.startsWith('/api/v1/admin/task-center')||path.includes('/resume-lifecycle/'))).toEqual([]);
});

test('overview is fetched on entry and manual refresh but not periodically', async ({ page }) => {
  const state = await fixture(page);
  await page.clock.install();
  await page.goto('/admin/task-center');
  await expect(page.getByRole('heading', { name: '任务运行中心' })).toBeVisible();
  await expect(page.getByText(/本页不自动轮询/)).toBeVisible();
  const requests = () => state.requests.filter(path => path === '/api/v1/admin/task-center').length;
  expect(requests()).toBe(1);
  await page.clock.runFor(120000);
  expect(requests()).toBe(1);
  await page.getByRole('button', { name: '刷新运行状态' }).click();
  await expect.poll(requests).toBe(2);
  await expect(page.getByRole('tab', {name:'结算业务记录'})).toHaveCount(0);
  await expect(page.getByRole('tablist')).toHaveCount(0);
  expect(state.requests.filter(path=>path.includes('/resume-lifecycle/'))).toEqual([]);
});

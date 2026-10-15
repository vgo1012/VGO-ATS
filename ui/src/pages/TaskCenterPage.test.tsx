import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { taskCenterApi, type TaskCenterOverview } from '../task-center/api';
import { TaskCenterPage } from './TaskCenterPage';
import * as http from '../api/http';


const overview: TaskCenterOverview = {
  instanceId: 'test-instance', observedSince: '2026-09-16T00:00:00Z', sampledAt: '2026-09-16T01:00:00Z', tasks: [{
    code: 'RESUME_PARSE', name: '简历解析任务轮询', module: '简历解析', enabled: true,
    scheduleType: 'FIXED_DELAY', expression: '2000', zone: 'Asia/Shanghai', intervalMillis: 2000,
    nextCronAt: null, businessTypes: ['RESUME_PARSE'], persistentBacklogStatus: 'AVAILABLE',
    persistentReadyCount: 7, oldestReadyAt: '2026-09-16T00:30:00Z', channels: [{
      code: 'RESUME_PARSE', lastTriggeredAt: '2026-09-16T01:00:00Z', lastCompletedAt: '2026-09-16T01:00:01Z',
      triggers: 1, coalesced: 0, rejected: 0, failed: 0, queued: 0, running: 0, recentScans: [{
        runId: 'scan-one', state: 'COMPLETED', queuedAt: '2026-09-16T01:00:00Z', startedAt: '2026-09-16T01:00:00Z',
        finishedAt: '2026-09-16T01:00:01Z', durationMillis: 1000, errorCode: null,
      }],
    }],
  }],
};
let client: QueryClient;

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  vi.spyOn(http, 'request').mockRejectedValue(new Error('意外访问接口'));
  vi.spyOn(taskCenterApi, 'overview').mockResolvedValue(structuredClone(overview));
  vi.spyOn(taskCenterApi, 'history').mockResolvedValue({ items: [], hasMore: false, nextCursor: null });
});
afterEach(() => { cleanup(); client.clear(); vi.restoreAllMocks(); });

function show() {
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><TaskCenterPage /></QueryClientProvider></App></ConfigProvider>);
}

it('shows scope and scan completion without inventing business success or mutation controls', async () => {
  show();
  expect(await screen.findByText('test-instance')).toBeInTheDocument();
  expect(screen.getByText('轮次已完成')).toBeInTheDocument();
  expect(screen.getByText('可领取 7')).toBeInTheDocument();
  expect(screen.getByText('30 分钟')).toBeInTheDocument();
  expect(screen.queryByText('业务成功')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /立即执行|暂停任务|修改计划/ })).not.toBeInTheDocument();
  expect(taskCenterApi.history).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '查看简历解析任务轮询' }));
  expect(await screen.findByText('轮次完成（非业务成功）')).toBeInTheDocument();
  expect(await screen.findByText('尚无此类业务任务，不代表扫描未运行')).toBeInTheDocument();
  expect(taskCenterApi.history).toHaveBeenCalledWith('RESUME_PARSE', '', expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('button',{name:'Close'}));
  await waitFor(()=>expect(screen.getByRole('button',{name:'查看简历解析任务轮询'})).toHaveFocus());
}, 15000);

it('provides retry after initial load fails rather than reporting an empty healthy system', async () => {
  vi.mocked(taskCenterApi.overview).mockRejectedValueOnce(new Error('暂时不可用'));
  show();
  expect(await screen.findByText(/当前未取得最新运行状态/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /刷新运行状态/ }));
  expect(await screen.findByText('test-instance')).toBeInTheDocument();
});

it('explains idle backoff instead of presenting every scheduler tick as a database scan', async () => {
  const value = structuredClone(overview);
  value.tasks[0]!.idleBackoffMaxMillis = 60000;
  vi.mocked(taskCenterApi.overview).mockResolvedValue(value);
  show();
  expect(await screen.findByText('提交后唤醒；空闲退避至 60 秒检查')).toBeInTheDocument();
  expect(screen.getByText(/本页不自动轮询/)).toBeInTheDocument();
});

it('keeps last data visibly stale after refresh failure', async () => {
  show();
  await screen.findByText('test-instance');
  vi.mocked(taskCenterApi.overview).mockRejectedValue(new Error('网络不可用'));
  fireEvent.click(screen.getByRole('button', { name: /刷新运行状态/ }));
  expect(await screen.findByText(/已显示内容可能过时/)).toBeInTheDocument();
  expect(screen.getByText('test-instance')).toBeInTheDocument();
});

it('shows unavailable backlog explicitly instead of turning it into zero', async () => {
  const value = structuredClone(overview);
  value.tasks[0]!.persistentBacklogStatus = 'UNAVAILABLE';
  value.tasks[0]!.persistentReadyCount = null;
  value.tasks[0]!.oldestReadyAt = null;
  vi.mocked(taskCenterApi.overview).mockResolvedValue(value);
  show();
  expect(await screen.findAllByText('读取失败')).toHaveLength(2);
  expect(screen.queryByText('可领取 0')).not.toBeInTheDocument();
});

it('isolates business history errors and supports explicit recovery', async () => {
  vi.mocked(taskCenterApi.history).mockRejectedValueOnce(new Error('数据库暂不可用'));
  show();
  fireEvent.click(await screen.findByRole('button', { name: '查看简历解析任务轮询' }));
  expect(await screen.findByText(/未取得最新业务记录/)).toBeInTheDocument();
  expect(screen.getByText('轮次完成（非业务成功）')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /刷新业务记录/ }));
  await waitFor(() => expect(screen.queryByText(/未取得最新业务记录/)).not.toBeInTheDocument());
}, 15000);

it('shows the schedule directly and refreshes without settlement tabs or lifecycle API requests', async () => {
  show();
  await screen.findByText('test-instance');
  expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
  expect(screen.queryByRole('tab', { name: '结算业务记录' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '刷新运行状态' }));
  await waitFor(() => expect(taskCenterApi.overview).toHaveBeenCalledTimes(2));
  expect(http.request).not.toHaveBeenCalled();
});

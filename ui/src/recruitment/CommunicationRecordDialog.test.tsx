import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ApiProblemError } from '../api/problem';
import { CommunicationRecordDialog } from './CommunicationRecordDialog';
import { recruitmentApi, type Communication } from './api';

const record: Communication = {
  id: 'communication-1', applicationId: 'app', channel: 'OTHER', summary: '已确认面试意向',
  result: 'FOLLOW_UP', nextPlan: '明天核对时间', communicatedAt: '2026-09-12T01:00:00Z', recordedByUserId: 'hr',
};

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

function mount(onSaved = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  function Harness() {
    const [open, setOpen] = useState(true);
    return <><button onClick={() => setOpen(true)}>重新打开记录</button>
      <CommunicationRecordDialog open={open} applicationId="app" onClose={() => setOpen(false)} onSaved={onSaved} /></>;
  }
  render(<ConfigProvider locale={zhCN} theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><Harness /></QueryClientProvider></App></ConfigProvider>);
  return onSaved;
}

async function fillDraft() {
  fireEvent.mouseDown(screen.getByLabelText('沟通结果'));
  fireEvent.click(await screen.findByText('待跟进', { selector: '.ant-select-item-option-content' }));
  fireEvent.change(screen.getByLabelText('沟通情况（选填）'), { target: { value: record.summary } });
  fireEvent.change(screen.getByLabelText('下一步计划（选填）'), { target: { value: record.nextPlan } });
}

it('freezes uncertain communication and retries the exact body and idempotency key', async () => {
  const write = vi.spyOn(recruitmentApi, 'communicate').mockRejectedValueOnce(new Error('响应连接中断')).mockResolvedValue(record);
  const saved = mount();
  await fillDraft();
  fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
  const retry = await screen.findByRole('button', { name: '重试保存记录' });
  expect(screen.getByText('保存结果尚未确认，请重试核对；当前内容已保留。')).toBeInTheDocument();
  expect(screen.getByLabelText('沟通情况（选填）')).toHaveValue(record.summary);
  for (const label of ['渠道', '沟通结果', '沟通情况（选填）', '下一步计划（选填）']) expect(screen.getByLabelText(label)).toBeDisabled();
  expect(saved).not.toHaveBeenCalled();
  fireEvent.click(retry);
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(write).toHaveBeenCalledTimes(2);
  expect(write.mock.calls[0]?.[2]).toEqual(expect.any(String));
  expect(write.mock.calls[0]?.[2]).toBeTruthy();
  expect(write.mock.calls[1]).toEqual(write.mock.calls[0]);
  expect(write.mock.calls[0]?.[1]).toEqual({ channel: 'OTHER', result: 'FOLLOW_UP', summary: record.summary, nextPlan: record.nextPlan });
});

it.each([400, 422])('permits editing and a new key after explicit %s input rejection', async status => {
  const write = vi.spyOn(recruitmentApi, 'communicate').mockRejectedValueOnce(new ApiProblemError({
    title: '输入拒绝', detail: '请修正沟通内容', status, code: 'COMMUNICATION_RESULT_INVALID', recoverable: false,
  })).mockResolvedValue(record);
  const saved = mount();
  await fillDraft();
  fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
  await screen.findByText('请修正沟通内容');
  await waitFor(() => expect(screen.getByLabelText('沟通情况（选填）')).toBeEnabled());
  expect(screen.queryByRole('button', { name: '重试保存记录' })).not.toBeInTheDocument();
  expect(screen.getByLabelText('沟通情况（选填）')).toHaveValue(record.summary);
  fireEvent.change(screen.getByLabelText('沟通情况（选填）'), { target: { value: '已核对并修正内容' } });
  fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(write).toHaveBeenCalledTimes(2);
  expect(write.mock.calls[1]?.[2]).toBeTruthy();
  expect(write.mock.calls[1]?.[2]).not.toBe(write.mock.calls[0]?.[2]);
  expect(write.mock.calls[1]?.[1].summary).toBe('已核对并修正内容');
});

it('sends only one request while repeated save clicks are pending', async () => {
  let resolve!: (value: Communication) => void;
  const write = vi.spyOn(recruitmentApi, 'communicate').mockImplementation(() => new Promise<Communication>(done => { resolve = done; }));
  const saved = mount();
  await fillDraft();
  const save = screen.getByRole('button', { name: '保存记录' });
  fireEvent.click(save);
  fireEvent.click(save);
  await waitFor(() => expect(write).toHaveBeenCalledTimes(1));
  fireEvent.click(save);
  expect(write).toHaveBeenCalledTimes(1);
  resolve(record);
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
});

it('preserves uncertain snapshot across closing and reopening the same dialog', async () => {
  const write = vi.spyOn(recruitmentApi, 'communicate').mockRejectedValueOnce(new Error('响应未确认')).mockResolvedValue(record);
  const saved = mount();
  await fillDraft();
  fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
  await screen.findByRole('button', { name: '重试保存记录' });
  await waitFor(() => expect(screen.getByRole('button', { name: /^取\s*消$/ })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: /^取\s*消$/ }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: '重新打开记录' }));
  const retry = await screen.findByRole('button', { name: '重试保存记录' });
  expect(screen.getByLabelText('沟通情况（选填）')).toHaveValue(record.summary);
  expect(screen.getByLabelText('沟通情况（选填）')).toBeDisabled();
  fireEvent.click(retry);
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  expect(write.mock.calls[1]).toEqual(write.mock.calls[0]);
});

it('retries only the completion callback when saving succeeded but refreshing failed', async () => {
  const write = vi.spyOn(recruitmentApi, 'communicate').mockResolvedValue(record);
  const saved = vi.fn<() => Promise<void>>().mockRejectedValueOnce(new Error('详情刷新失败')).mockResolvedValue(undefined);
  mount(saved);
  await fillDraft();
  fireEvent.click(screen.getByRole('button', { name: '保存记录' }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  await screen.findByText('详情刷新失败');
  expect(screen.getByLabelText('沟通情况（选填）')).toBeDisabled();
  const save = screen.getByRole('button', { name: /^(保存记录|重试保存记录)$/ });
  await waitFor(() => expect(save).toBeEnabled());
  fireEvent.click(save);
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(2));
  expect(write).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(screen.getByLabelText('沟通情况（选填）')).toBeEnabled());
});

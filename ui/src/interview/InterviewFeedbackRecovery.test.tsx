import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { ApiProblemError } from '../api/problem';
import type { Interview } from './api';
import { InterviewFeedbackModal } from './InterviewFeedbackModal';

vi.mock('../auth/session', () => ({ useSession: () => ({ actor: { userId: 'author' } }) }));
vi.mock('../api/http', () => ({ request: vi.fn(), requestFile: vi.fn(), downloadFile: vi.fn() }));
const saved = { feedback: { id: 'feedback', authorUserId: 'author', status: 'SUBMITTED' }, currentVersion: { verdict: 'PASS', changeReason: '正式提交面评', feedbackOccurredAt: '2026-09-12T00:00:00Z' } };
const posts = () => vi.mocked(request).mock.calls.filter(([, options]) => options?.method === 'POST');
beforeEach(() => { vi.mocked(request).mockReset(); });
afterEach(() => { cleanup(); });
function respond(firstError?: Error) {
  let count = 0;
  vi.mocked(request).mockImplementation((_path, options) => {
    if (options?.method !== 'POST') return Promise.resolve({ items: [], hasMore: false });
    count++;
    return count === 1 && firstError ? Promise.reject(firstError) : Promise.resolve(saved);
  });
}
async function mount(onSaved = vi.fn().mockResolvedValue(undefined)) {
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><InterviewFeedbackModal value={{ id: 'interview', interviewType: 'INTERNAL' } as Interview} onClose={vi.fn()} onSaved={onSaved} /></QueryClientProvider></App></ConfigProvider>);
  fireEvent.change(await screen.findByLabelText('面评'), { target: { value: '技术表达清晰' } });
  return onSaved;
}

it('replays the exact formal feedback key and timestamp after an unknown response', async () => {
  respond(new TypeError('响应丢失'));
  const onSaved = await mount();
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('面评保存失败，请重试。');
  await waitFor(() => expect(screen.getByRole('button', { name: '正式提交面评' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[0]?.[0]).toBe('/interviews/interview/feedback');
  expect(calls[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).toBe(calls[0]?.[1]?.idempotencyKey);
  expect(calls[1]?.[1]?.body).toEqual(calls[0]?.[1]?.body);
  expect(calls[0]?.[1]?.body).toMatchObject({ status: 'SUBMITTED', changeReason: '正式提交面评' });
  expect(calls[0]?.[1]?.body).toHaveProperty('feedbackOccurredAt', expect.any(String));
}, 15_000);

it('freezes uncertain formal feedback while leaving its retry available', async () => {
  respond(new TypeError('响应丢失'));
  await mount();
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('面评保存失败，请重试。');
  await waitFor(() => expect(screen.getByLabelText('面评')).toBeDisabled());
  expect(screen.getByLabelText('本轮结果')).toBeDisabled();
  expect(screen.getByText('上次面评提交结果尚未确认，请重试确认后再修改。')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '正式提交面评' })).toBeEnabled();
}, 15_000);

it.each([400, 422])('allows a revised feedback with a new key after definite %s rejection', async status => {
  respond(new ApiProblemError({ status, code: 'VALIDATION_FAILED', title: '校验失败', detail: '面评需要修改', recoverable: true }));
  const onSaved = await mount();
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('面评需要修改');
  await waitFor(() => expect(screen.getByLabelText('面评')).toBeEnabled());
  fireEvent.change(screen.getByLabelText('面评'), { target: { value: '补充面试意见' } });
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[1]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).not.toBe(calls[0]?.[1]?.idempotencyKey);
  expect(calls[1]?.[1]?.body).toMatchObject({ shortComment: '补充面试意见', changeReason: '正式提交面评' });
}, 15_000);

it('retries only the saved callback after the API has confirmed submission', async () => {
  respond();
  const onSaved = vi.fn().mockRejectedValueOnce(new Error('刷新失败')).mockResolvedValue(undefined);
  await mount(onSaved);
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('面评已保存，后续界面更新未完成，请重试继续。');
  await waitFor(() => expect(screen.getByRole('button', { name: '正式提交面评' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2));
  expect(posts()).toHaveLength(1);
  expect(onSaved.mock.calls[1]?.[0]).toEqual(onSaved.mock.calls[0]?.[0]);
}, 15_000);

it('starts a separate editable attempt when switching to another interview', async () => {
  respond(new TypeError('响应丢失'));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onSaved = vi.fn().mockResolvedValue(undefined);
  const content = (id: string) => <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><InterviewFeedbackModal value={{ id, interviewType: 'INTERNAL' } as Interview} onClose={vi.fn()} onSaved={onSaved} /></QueryClientProvider></App></ConfigProvider>;
  const view = render(content('interview-a'));
  fireEvent.change(await screen.findByLabelText('面评'), { target: { value: '第一场面评' } });
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('上次面评提交结果尚未确认，请重试确认后再修改。');
  view.rerender(content('interview-b'));
  const reason = await screen.findByLabelText('面评');
  expect(reason).toBeEnabled();
  expect(reason).toHaveValue('');
  fireEvent.change(reason, { target: { value: '第二场独立面评' } });
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = posts();
  expect(calls).toHaveLength(2);
  expect(calls[0]?.[0]).toBe('/interviews/interview-a/feedback');
  expect(calls[1]?.[0]).toBe('/interviews/interview-b/feedback');
  expect(calls[1]?.[1]?.body).toMatchObject({ shortComment: '第二场独立面评', changeReason: '正式提交面评' });
  expect(calls[1]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]?.idempotencyKey).not.toBe(calls[0]?.[1]?.idempotencyKey);
}, 15_000);

it('rechecks submitted feedback after reopening despite the shared default cache freshness', async () => {
  let submitted = false;
  vi.mocked(request).mockImplementation((_path, options) => {
    if (options?.method === 'POST') { submitted = true; return Promise.reject(new TypeError('响应丢失')); }
    return Promise.resolve({ items: submitted ? [saved] : [], hasMore: false });
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });
  const onClose = vi.fn();
  const content = (open: boolean) => <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}>{open ? <InterviewFeedbackModal value={{ id: 'interview', interviewType: 'INTERNAL' } as Interview} onClose={onClose} onSaved={vi.fn().mockResolvedValue(undefined)} /> : null}</QueryClientProvider></App></ConfigProvider>;
  const view = render(content(true));
  fireEvent.change(await screen.findByLabelText('面评'), { target: { value: '核对实际面试记录' } });
  fireEvent.click(screen.getByRole('button', { name: '正式提交面评' }));
  await screen.findByText('上次面评提交结果尚未确认，请重试确认后再修改。');
  fireEvent.click(screen.getByRole('button', { name: /^(Cancel|取\s*消)$/ }));
  expect(onClose).toHaveBeenCalledTimes(1);
  view.rerender(content(false));
  view.rerender(content(true));
  await screen.findByText('面评已正式提交');
  expect(screen.queryByRole('button', { name: '正式提交面评' })).not.toBeInTheDocument();
  expect(screen.queryByLabelText('面评')).not.toBeInTheDocument();
  expect(posts()).toHaveLength(1);
  expect(vi.mocked(request).mock.calls.filter(([, options]) => options?.method !== 'POST')).toHaveLength(2);
}, 15_000);

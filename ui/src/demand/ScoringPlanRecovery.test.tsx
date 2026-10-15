import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { ApiProblemError } from '../api/problem';
import type { MatchPlanDetails } from './api';
import { ScoringPlanEditor } from './ScoringPlanEditor';

vi.mock('../api/http', () => ({ request: vi.fn() }));
const draft = { plan: { id: 'plan', name: '公司评分' }, version: { id: 'v2', versionNo: 2, version: 1, status: 'DRAFT', skillsEnabled: true, roleEnabled: true, yearsEnabled: true, responsibilityEnabled: true, industryProjectEnabled: true }, criterionPlan: { planMaxBonusPoints: 0 }, criteria: [] } as unknown as MatchPlanDetails;
const writes = () => vi.mocked(request).mock.calls.filter(([, options]) => options?.method === 'PATCH');
const problem = (status: number) => new ApiProblemError({ status, title: '保存被拒绝', detail: `保存被拒绝${status}`, code: 'REJECTED', recoverable: true });
beforeEach(() => { vi.mocked(request).mockReset(); });
afterEach(cleanup);
function mount(onSaved = vi.fn().mockResolvedValue(undefined), initialValue: MatchPlanDetails | 'new' = draft) {
  const onClose = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const content = (value: MatchPlanDetails | 'new') => <ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><ScoringPlanEditor value={value} onClose={onClose} onSaved={onSaved} /></QueryClientProvider></App></ConfigProvider>;
  const view = render(content(initialValue));
  return { onClose, onSaved, switchTo: (value: MatchPlanDetails) => view.rerender(content(value)) };
}
function firstFailure(error: Error) {
  vi.mocked(request).mockRejectedValueOnce(error).mockResolvedValue({ ...draft, version: { ...draft.version, version: 2 } });
}
const save = () => screen.getByRole('button', { name: '保存草稿' });

it('keeps the create branch on the same operation key without adding an update version', async () => {
  firstFailure(new TypeError('保存响应丢失'));
  const { onSaved } = mount(undefined, 'new');
  fireEvent.change(screen.getByRole('textbox', { name: '方案名称' }), { target: { value: '新方案' } });
  fireEvent.click(save());
  await screen.findByText(/保存响应/);
  await waitFor(() => expect(save()).toBeEnabled());
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  const calls = vi.mocked(request).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0]?.[1]?.method).toBe('POST');
  expect(calls[0]?.[1]?.body).not.toHaveProperty('version');
  expect(calls[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(calls[1]?.[1]).toEqual(calls[0]?.[1]);
}, 15_000);

it('replays a lost update response using the original complete body and operation key', async () => {
  const accepted = new Map<string, unknown>();
  let currentVersion = 1;
  vi.mocked(request).mockImplementation((_path, options) => {
    const key = options?.idempotencyKey;
    if (key && accepted.has(key)) return Promise.resolve(accepted.get(key));
    const body = options?.body as Record<string, unknown>;
    if (body.version !== currentVersion) return Promise.reject(problem(409));
    const result = { ...draft, version: { ...draft.version, version: ++currentVersion } };
    if (key) accepted.set(key, result);
    return Promise.reject(new SyntaxError('保存响应丢失'));
  });
  const { onSaved } = mount();
  fireEvent.change(screen.getByRole('textbox', { name: '方案名称' }), { target: { value: '研发评分' } });
  fireEvent.click(save());
  await screen.findByText(/保存响应/);
  await waitFor(() => expect(save()).toBeEnabled());
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(2);
  expect(writes()[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(writes()[1]?.[1]?.idempotencyKey).toBe(writes()[0]?.[1]?.idempotencyKey);
  expect(writes()[1]?.[1]?.body).toEqual(writes()[0]?.[1]?.body);
  expect(currentVersion).toBe(2);
}, 15_000);

it('freezes the submitted name, dimensions and criteria while the save is uncertain', async () => {
  firstFailure(new TypeError('保存响应丢失'));
  mount();
  fireEvent.click(save());
  await screen.findByText(/保存响应/);
  expect(screen.getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  expect(screen.getByRole('checkbox', { name: '核心技能' })).toBeDisabled();
  expect(screen.getByRole('combobox', { name: '添加附加要求' })).toBeDisabled();
}, 15_000);

it.each([400, 422])('allows correction with a new key after explicit %s validation rejection', async status => {
  firstFailure(problem(status));
  const { onSaved } = mount();
  fireEvent.click(save());
  await screen.findByText(`保存被拒绝${status}`);
  const name = screen.getByRole('textbox', { name: '方案名称' });
  expect(name).toBeEnabled();
  fireEvent.change(name, { target: { value: '修正名称' } });
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(writes()[1]?.[1]?.body).toMatchObject({ name: '修正名称', version: 1 });
  expect(writes()[0]?.[1]?.idempotencyKey).toBeTruthy();
  expect(writes()[1]?.[1]?.idempotencyKey).not.toBe(writes()[0]?.[1]?.idempotencyKey);
}, 15_000);

it('only repeats the UI callback when the update has already been confirmed', async () => {
  vi.mocked(request).mockResolvedValue(draft);
  const onSaved = vi.fn().mockRejectedValueOnce(new Error('列表刷新失败')).mockResolvedValue(undefined);
  const { onClose } = mount(onSaved);
  fireEvent.click(save());
  await screen.findByText('列表刷新失败');
  expect(onClose).not.toHaveBeenCalled();
  await waitFor(() => expect(save()).toBeEnabled());
  fireEvent.click(save());
  await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  expect(onSaved).toHaveBeenCalledTimes(2);
  expect(writes()).toHaveLength(1);
}, 15_000);

it('keeps an uncertain operation frozen after a later validation rejection', async () => {
  vi.mocked(request).mockRejectedValueOnce(new TypeError('保存响应丢失')).mockRejectedValueOnce(problem(422)).mockResolvedValue(draft);
  const { onSaved } = mount();
  fireEvent.click(save());
  await screen.findByText(/保存响应/);
  await waitFor(() => expect(save()).toBeEnabled());
  fireEvent.click(save());
  await screen.findByText('保存被拒绝422');
  expect(screen.getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  await waitFor(() => expect(save()).toBeEnabled());
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(3);
  expect(writes()[1]?.[1]).toEqual(writes()[0]?.[1]);
  expect(writes()[2]?.[1]).toEqual(writes()[0]?.[1]);
}, 15_000);

it('refreshes after an explicit version conflict without replaying the stale PATCH', async () => {
  vi.mocked(request).mockRejectedValue(new ApiProblemError({ status: 409, title: '版本冲突', detail: '版本已变化', code: 'MATCH_PLAN_VERSION_CONFLICT', recoverable: false }));
  const onSaved = vi.fn().mockRejectedValueOnce(new Error('刷新失败')).mockResolvedValue(undefined);
  const { onClose } = mount(onSaved);
  fireEvent.click(save());
  const refresh = await screen.findByRole('button', { name: '刷新列表' });
  expect(screen.getByRole('textbox', { name: '方案名称' })).toBeDisabled();
  expect(screen.queryByText('草稿保存结果尚未确认，请重试核对后再修改。')).not.toBeInTheDocument();
  await waitFor(() => expect(refresh).toBeEnabled());
  fireEvent.click(refresh);
  await screen.findByText('刷新失败');
  expect(onClose).not.toHaveBeenCalled();
  await waitFor(() => expect(refresh).toBeEnabled());
  fireEvent.click(refresh);
  await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(1);
  expect(onSaved).toHaveBeenCalledTimes(2);
}, 15_000);

it('isolates a different draft from an earlier uncertain save', async () => {
  firstFailure(new TypeError('保存响应丢失'));
  const { switchTo, onSaved } = mount();
  fireEvent.click(save());
  await screen.findByText(/保存响应/);
  switchTo({ ...draft, plan: { ...draft.plan, name: '另一份草稿' }, version: { ...draft.version, id: 'v3', versionNo: 3 } });
  const name = await screen.findByRole('textbox', { name: '方案名称' });
  expect(name).toBeEnabled();
  expect(name).toHaveValue('另一份草稿');
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(writes()[1]?.[0]).toBe('/admin/jd-scoring-configs/v3');
  expect(writes()[1]?.[1]?.body).toMatchObject({ name: '另一份草稿' });
}, 15_000);

it('restores all five dimensions when editing a historical draft and cannot disable them', async () => {
  vi.mocked(request).mockResolvedValue(draft);
  const { onSaved } = mount(undefined, { ...draft, version: { ...draft.version, yearsEnabled: false } });
  for (const label of ['核心技能', '岗位经历', '相关年限', '职责匹配', '行业与项目']) {
    expect(screen.getByRole('checkbox', { name: label })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: label })).toBeDisabled();
  }
  fireEvent.click(save());
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
  expect(writes()[0]?.[1]?.body).toHaveProperty('professionalDimensions', { skills: true, roleExperience: true, relatedYears: true, responsibilityMatch: true, industryAndProjects: true });
});

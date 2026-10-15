import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { WorkflowPathModal } from './WorkflowPathModal';
import { recruitmentApi, type ApplicationDetails } from './api';

vi.mock('./api', () => ({ recruitmentApi: { selectPath: vi.fn() } }));
beforeEach(() => { cleanup(); vi.resetAllMocks(); });
function mount(withdrawn: boolean) {
  const value = { application: { id: 'application', currentStage: 'PATH_SELECTION', version: 3 }, workflowSteps: [
    { id: 'communication', stepCode: 'CANDIDATE_COMMUNICATION', sequenceNo: 1, candidateOptional: true, status: withdrawn ? 'NOT_SELECTED' : 'AVAILABLE' },
    { id: 'internal', stepCode: 'PRE_SUBMISSION_INTERNAL_INTERVIEW', sequenceNo: 2, candidateOptional: true, status: withdrawn ? 'PASSED' : 'AVAILABLE' },
    { id: 'submission', stepCode: 'CLIENT_SUBMISSION', sequenceNo: 3, candidateOptional: false, status: withdrawn ? 'WITHDRAWN' : 'AVAILABLE' },
    { id: 'round', stepCode: 'INTERVIEW_ROUND_1', sequenceNo: 4, candidateOptional: true, status: withdrawn ? 'NOT_SELECTED' : 'AVAILABLE' },
  ] } as ApplicationDetails;
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={new QueryClient()}><WorkflowPathModal open value={value} onClose={vi.fn()} onSaved={vi.fn().mockResolvedValue(undefined)} /></QueryClientProvider></App></ConfigProvider>);
}
it('uses a path amendment after withdrawal and prevents reopening earlier facts', async () => {
  mount(true);
  const boxes = await screen.findAllByRole('checkbox');
  expect(screen.getByRole('checkbox', { name: '候选人沟通' })).toBeDisabled();
  expect(boxes[0]).toBeDisabled();
  expect(boxes[1]).toBeDisabled();
  expect(boxes[2]).not.toBeDisabled();
  fireEvent.change(screen.getByLabelText('选路原因'), { target: { value: '合成验收：撤回后调整后续安排' } });
  fireEvent.click(screen.getByRole('button', { name: '确认路径' }));
  await waitFor(() => expect(recruitmentApi.selectPath).toHaveBeenCalledWith(expect.objectContaining({ currentStage: 'PATH_SELECTION' }), expect.any(Array), expect.any(String), true));
});
it('uses initial selection for a fresh application', async () => {
  mount(false);
  fireEvent.change(await screen.findByLabelText('选路原因'), { target: { value: '合成验收：首次选择' } });
  fireEvent.click(screen.getByRole('button', { name: '确认路径' }));
  await waitFor(() => expect(recruitmentApi.selectPath).toHaveBeenCalledWith(expect.any(Object), expect.any(Array), expect.any(String), false));
});
it('returns invalid path reasons to the dialog validation handler before saving', async () => {
  mount(false);
  const reason = await screen.findByLabelText('选路原因');
  const scrollIntoView = vi.fn();
  reason.scrollIntoView = scrollIntoView;
  fireEvent.change(reason, { target: { value: '' } });
  const confirm = screen.getByRole('button', { name: '确认路径' });
  confirm.focus();
  fireEvent.click(confirm);
  await waitFor(() => expect(reason).toHaveAttribute('aria-invalid', 'true'));
  await waitFor(() => expect(reason).toHaveFocus());
  expect(recruitmentApi.selectPath).not.toHaveBeenCalled();
  expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
  fireEvent.change(reason, { target: { value: '确认候选人的实际安排' } });
  fireEvent.click(confirm);
  await waitFor(() => expect(recruitmentApi.selectPath).toHaveBeenCalledTimes(1));
});

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { NextActionDialog } from './NextActionDialog';
import type { ApplicationDetails, RecruitmentApplication, WorkflowStep } from './api';
import { submissionApi } from '../submission/api';

vi.mock('../submission/api', () => ({ submissionApi: { list: vi.fn() } }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const application = { id: 'app', recruitmentType: 'EXTERNAL', candidateId: 'c', clientId: 'client', demandId: 'd', jdId: 'jd', jdVersionId: 'jv', resumeVersionId: 'rv' } as RecruitmentApplication;
function mount(steps: Partial<WorkflowStep>[] = []) {
  const value = { application, workflowSteps: steps } as ApplicationDetails;
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><NextActionDialog value={value} onClose={vi.fn()} onSaved={vi.fn()} /></QueryClientProvider>);
}
it('offers external interviews and decisions without client recommendation', () => {
  mount();
  expect(screen.queryByRole('radio', { name: /^推荐给客户/ })).not.toBeInTheDocument();
  expect(screen.getByRole('radio', { name: /安排甲方面试/ })).toBeEnabled();
  expect(screen.getByRole('radio', { name: /安排甲方 AI 面试/ })).toBeEnabled();
  expect(screen.getByRole('radio', { name: /人工招聘决定/ })).toBeEnabled();
  expect(submissionApi.list).not.toHaveBeenCalled();
});
it.each([6, 7])('offers interview round %i without a preset round limit', round => {
  mount(Array.from({ length: round - 1 }, (_, index) => ({ stepCode: 'INTERVIEW_ROUND_1', roundNo: index + 1, status: 'PASSED' })));
  expect(screen.getByRole('radio', { name: new RegExp(`安排甲方面试 · 第 ${round} 轮面试`) })).toBeEnabled();
});

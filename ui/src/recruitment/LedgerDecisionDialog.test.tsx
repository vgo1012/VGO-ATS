import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { recruitmentApi, type ApplicationDetails } from './api';
import { LedgerDecisionDialog } from './LedgerDecisionDialog';
import { submissionApi } from '../submission/api';

vi.mock('../display/BusinessReferences', () => ({ CandidateName: () => '候选人', JobName: () => '工程师' }));
vi.mock('../submission/api', () => ({ submissionApi: { list: vi.fn() } }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('confirms the offer choice and hire in one operation from the legacy recommendation step', async () => {
  const value = { application: { id: 'app', candidateId: 'candidate', jdId: 'jd', recruitmentType: 'EXTERNAL', currentStage: 'IN_PROCESS', terminalAt: null }, workflowSteps: [{ stepCode: 'CLIENT_SUBMISSION', status: 'IN_PROGRESS' }], decisions: [], offerStatusRecords: [] } as unknown as ApplicationDetails;
  const hire = vi.spyOn(recruitmentApi, 'hire').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.hire>>);
  const changed = vi.fn().mockResolvedValue(undefined);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><LedgerDecisionDialog value={value} action="ONBOARDED" onClose={vi.fn()} onChanged={changed} /></QueryClientProvider>);
  const confirm = screen.getByRole('button', { name: '确认录用' });
  expect(confirm).toBeEnabled();
  fireEvent.change(screen.getByLabelText('原因评论'), { target: { value: '已确认录用' } });
  fireEvent.click(confirm);
  await waitFor(() => expect(hire).toHaveBeenCalledWith('app', { offerStatus: 'ISSUED', reason: '已确认录用' }, expect.objectContaining({ action: 'ONBOARDED' })));
  expect(submissionApi.list).not.toHaveBeenCalled();
  await waitFor(() => expect(changed).toHaveBeenCalledOnce());
  client.clear();
});

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { SessionActor } from '../auth/session';
import { recruitmentApi, type ApplicationDetails, type OfferRecord, type RecruitmentApplication } from './api';
import { OnboardingFlowActions } from './OnboardingFlowActions';

const state = vi.hoisted(() => ({ actor: { userId: 'hr', permissions: ['recruitment.manage'] } as SessionActor }));
vi.mock('../auth/session', () => ({ useSession: () => ({ actor: state.actor }) }));
vi.mock('../display/BusinessReferences', () => ({ CandidateName: () => '候选人', JobName: () => '工程师' }));

const application = { id: 'app', candidateId: 'candidate', jdId: 'jd', ownerUserId: 'hr', currentStage: 'OFFER', onboardingStatus: 'IN_PROGRESS', onboardingStartedAt: '2026-01-01T01:31:23Z' } as RecruitmentApplication;
const offer = (id: string, status: OfferRecord['status'], occurredAt: string): OfferRecord => ({
  id, applicationId: 'app', recordType: 'STATUS', status, terminationReason: null, applicationOutcome: null,
  reason: '测试记录', correctsRecordId: null, reopensRecordId: null, source: 'MANUAL', sourceReference: null,
  sourceStatus: null, occurredAt, operatedByUserId: 'hr',
});
const details = { application, elapsedSeconds: 0, collaboratorUserIds: [], workflowSteps: [], decisions: [], offerStatusRecords: [
  offer('issued', 'ISSUED', '2026-01-01T01:31:23Z'),
  offer('onboarded', 'ONBOARDED', '2026-02-01T01:00:00Z'),
] } as ApplicationDetails;
const clients: QueryClient[] = [];

function mount(onChanged = vi.fn().mockResolvedValue(undefined), value = application) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  clients.push(client);
  render(<ConfigProvider theme={{ token: { motion: false } }}><App><QueryClientProvider client={client}><OnboardingFlowActions application={value} onChanged={onChanged} /></QueryClientProvider></App></ConfigProvider>);
  return onChanged;
}

beforeEach(() => {
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() });
  vi.spyOn(recruitmentApi, 'application').mockResolvedValue(details);
  vi.spyOn(recruitmentApi, 'reviewOnboarding').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.reviewOnboarding>>);
  vi.spyOn(recruitmentApi, 'returnOnboardingToShared').mockResolvedValue({} as Awaited<ReturnType<typeof recruitmentApi.returnOnboardingToShared>>);
});

afterEach(() => {
  cleanup();
  clients.forEach(client => client.clear());
  clients.length = 0;
  vi.restoreAllMocks();
});

it('reviews an exact onboarding time from the onboarding workflow', async () => {
  const changed = mount();
  fireEvent.click(screen.getByRole('button', { name: '确认实际入职' }));
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.click(await dialog.findByRole('radio', { name: '补录入职时间' }));
  fireEvent.change(await dialog.findByLabelText('实际入职日期与时间（北京时间）'), { target: { value: '2026-01-01T09:31:24' } });
  fireEvent.change(dialog.getByLabelText('评论（选填）'), { target: { value: '北森信息已核对' } });
  fireEvent.click(dialog.getByRole('button', { name: '确认已入职' }));
  await waitFor(() => expect(recruitmentApi.reviewOnboarding).toHaveBeenCalledWith('app', {
    onboardedAt: '2026-01-01T01:31:24.000Z', reason: '北森信息已核对',
  }));
  await waitFor(() => expect(changed).toHaveBeenCalledOnce());
});

it('uses a correction action after actual onboarding is complete', () => {
  mount(vi.fn().mockResolvedValue(undefined), { ...application, currentStage: 'ONBOARDED', onboardingStatus: 'ONBOARDED', actualOnboardedAt: '2026-02-01T01:00:00Z' });
  expect(screen.getByRole('button', { name: '更正入职时间' })).toBeInTheDocument();
});

it('supports a direct return with the default reason', async () => {
  mount();
  fireEvent.click(screen.getByRole('button', { name: '退回共享简历库' }));
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.click(await dialog.findByRole('button', { name: '直接退回' }));
  await waitFor(() => expect(recruitmentApi.returnOnboardingToShared).toHaveBeenCalledWith('app', {}));
});

it('submits an optional controlled return reason and detail', async () => {
  mount();
  fireEvent.click(screen.getByRole('button', { name: '退回共享简历库' }));
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.mouseDown(await dialog.findByRole('combobox', { name: '退回原因（选填）' }));
  fireEvent.click(await screen.findByText('候选人原因', { selector: '.ant-select-item-option-content' }));
  fireEvent.change(dialog.getByLabelText('补充说明（选填）'), { target: { value: '候选人未办理入职' } });
  fireEvent.click(dialog.getByRole('button', { name: '直接退回' }));
  await waitFor(() => expect(recruitmentApi.returnOnboardingToShared).toHaveBeenCalledWith('app', {
    reasonCategory: 'CANDIDATE', reasonDetail: '候选人未办理入职',
  }));
});

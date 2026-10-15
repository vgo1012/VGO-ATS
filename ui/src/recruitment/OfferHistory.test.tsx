import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { OfferHistory } from './OfferHistory';
import type { ApplicationDetails } from './api';

vi.mock('../display/BusinessReferences', () => ({ UserName: () => '验收招聘专员' }));
afterEach(cleanup);
it.each(['TERMINATED', 'ONBOARDED'])('retains Offer history after %s without allowing further advancement', stage => {
  const value = { application: { currentStage: stage, terminalAt: '2026-09-07T18:00:00Z' }, offerStatusRecords: [
    { id: 'issued', status: 'ISSUED', recordType: 'STATUS', reason: '历史发出依据', occurredAt: '2026-09-07T17:00:00Z', operatedByUserId: 'hr' },
    { id: 'final', status: stage, recordType: 'STATUS', reason: '最终处理依据', occurredAt: '2026-09-07T18:00:00Z', operatedByUserId: 'hr' },
  ] } as ApplicationDetails;
  render(<OfferHistory value={value} onAdvance={vi.fn()} onCorrect={vi.fn()} onOnboard={vi.fn()} />);
  fireEvent.click(screen.getByText('查看历史（1条）'));
  expect(screen.getByText(/历史发出依据/)).toBeInTheDocument();
  expect(screen.getByText(/最终处理依据/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '记录已发出' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '确认入职' })).not.toBeInTheDocument();
});

it.each(['NOT_STARTED', 'IN_PROGRESS', 'ISSUED', 'ACCEPTED'])('offers only the next primary action from %s', status => {
  const onOnboard = vi.fn(); const onAdvance = vi.fn();
  const value = { application: { currentStage: 'OFFER', terminalAt: null }, offerStatusRecords: [
    { id: 'current', status, recordType: 'STATUS', reason: '当前事实', occurredAt: '2026-09-07T17:00:00Z', operatedByUserId: 'hr' },
  ] } as ApplicationDetails;
  const { rerender } = render(<OfferHistory value={value} onAdvance={onAdvance} onCorrect={vi.fn()} onOnboard={onOnboard} />);
  const canOnboard = ['ISSUED', 'ACCEPTED'].includes(status);
  fireEvent.click(screen.getByRole('button', { name: canOnboard ? '确认入职' : '记录已发出' }));
  expect(canOnboard ? onOnboard : onAdvance).toHaveBeenCalledOnce();
  expect(screen.queryByRole('button', { name: canOnboard ? '记录已发出' : '确认入职' })).not.toBeInTheDocument();
  rerender(<OfferHistory value={value} actionsAllowed={false} onAdvance={onAdvance} onCorrect={vi.fn()} onOnboard={onOnboard} />);
  expect(screen.queryByRole('button', { name: '更多' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /确认入职|记录已发出/ })).not.toBeInTheDocument();
});

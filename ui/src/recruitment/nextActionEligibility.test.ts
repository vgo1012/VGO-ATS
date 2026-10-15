import { describe, expect, it } from 'vitest';
import type { ApplicationDetails } from './api';
import { canSelectNextAction } from './nextActionEligibility';

const legacy = { application: { progressiveFollowUp: false, currentStage: 'DECISION', terminalAt: null }, workflowSteps: [{ stepCode: 'DECISION', status: 'IN_PROGRESS' }], decisions: [], offerStatusRecords: [] } as unknown as ApplicationDetails;
describe('next action eligibility', () => {
  it('recovers an automatic legacy decision without erasing saved facts', () => {
    expect(canSelectNextAction(legacy)).toBe(true);
    expect(canSelectNextAction({ ...legacy, decisions: [{}] as ApplicationDetails['decisions'] })).toBe(false);
    expect(canSelectNextAction({ ...legacy, offerStatusRecords: [{}] as ApplicationDetails['offerStatusRecords'] })).toBe(false);
  });
  it('does not replace an explicitly chosen decision or an active interview', () => {
    expect(canSelectNextAction({ ...legacy, application: { ...legacy.application, progressiveFollowUp: true } })).toBe(false);
    expect(canSelectNextAction({ ...legacy, workflowSteps: [{ ...legacy.workflowSteps[0]!, stepCode: 'INTERVIEW_ROUND_1' }] })).toBe(false);
  });
  it('continues legacy client recommendation without a sent record', () => {
    const value = { ...legacy, application: { ...legacy.application, recruitmentType: 'EXTERNAL' as const, currentStage: 'IN_PROCESS' as const }, workflowSteps: [{ ...legacy.workflowSteps[0]!, stepCode: 'CLIENT_SUBMISSION' }] };
    expect(canSelectNextAction(value)).toBe(true);
    expect(canSelectNextAction({ ...value, application: { ...value.application, terminalAt: '2026-09-15' } })).toBe(false);
    expect(canSelectNextAction({ ...value, workflowSteps: [...value.workflowSteps, { ...legacy.workflowSteps[0]!, stepCode: 'INTERVIEW_ROUND_1' }] })).toBe(false);
  });
  it('allows waiting paths but never reopens a terminal application', () => {
    const value = { ...legacy, application: { ...legacy.application, currentStage: 'PATH_SELECTION' as const } };
    expect(canSelectNextAction(value)).toBe(true);
    expect(canSelectNextAction({ ...value, application: { ...value.application, terminalAt: '2026-09-09' } })).toBe(false);
  });
});

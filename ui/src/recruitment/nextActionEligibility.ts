import type { ApplicationDetails } from './api';
/** Legacy templates can activate a decision without HR choosing it. Keep saved decisions final. */
export function canSelectNextAction(value: ApplicationDetails): boolean {
  const { application, workflowSteps, decisions, offerStatusRecords } = value;
  if (application.terminalAt) return false;
  if (application.currentStage === 'PATH_SELECTION') return true;
  const active = workflowSteps.filter(step => ['PENDING', 'IN_PROGRESS'].includes(step.status));
  if (application.recruitmentType === 'EXTERNAL' && application.currentStage === 'IN_PROCESS'
    && active.length === 1 && active[0]?.stepCode === 'CLIENT_SUBMISSION') return true;
  return application.progressiveFollowUp === false && application.currentStage === 'DECISION'
    && decisions.length === 0 && offerStatusRecords.length === 0
    && active.length === 1 && active[0]?.stepCode === 'DECISION';
}

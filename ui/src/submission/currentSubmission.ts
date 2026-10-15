import type { RecruitmentApplication, WorkflowStep } from '../recruitment/api';
import type { ClientSubmission } from './api';

export function belongsToApplication(application: RecruitmentApplication, item: ClientSubmission) {
  return item.applicationId === application.id && item.candidateId === application.candidateId
    && item.clientId === application.clientId && item.demandId === application.demandId
    && item.jdId === application.jdId && item.jdVersionId === application.jdVersionId
    && item.resumeVersionId === application.resumeVersionId;
}

const sent = (item: ClientSubmission) => item.deliveryStatus === 'SENT' && Boolean(item.sentAt);

export function currentSubmission(application: RecruitmentApplication, items: ClientSubmission[], step?: WorkflowStep): ClientSubmission | undefined {
  if (step?.clientSubmissionId) {
    const item = items.find(value => value.id === step.clientSubmissionId);
    return item && belongsToApplication(application, item) && sent(item) ? item : undefined;
  }
  return items.filter(item => belongsToApplication(application, item) && sent(item))
    .sort((left, right) => right.sentAt!.localeCompare(left.sentAt!))[0];
}

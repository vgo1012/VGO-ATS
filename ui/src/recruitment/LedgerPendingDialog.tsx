import { useState } from 'react';
import { CandidateName, JobName } from '../display/BusinessReferences';
import type { ApplicationDetails } from './api';
import { CommunicationRecordDialog } from './CommunicationRecordDialog';
import { newActivityOrigin } from './activityApi';

export function LedgerPendingDialog({ value, onClose, onChanged }: { value: ApplicationDetails; onClose: () => void; onChanged: () => Promise<unknown> }) {
  const [origin] = useState(() => newActivityOrigin('PENDING'));
  return <CommunicationRecordDialog open simple title="待定" origin={origin} applicationId={value.application.id}
    introduction={<p><CandidateName id={value.application.candidateId} /> · <JobName jdId={value.application.jdId} /> · {value.application.clientName ?? '内部招聘'}</p>}
    onClose={onClose} onSaved={async()=>{await onChanged();onClose();}} />;
}

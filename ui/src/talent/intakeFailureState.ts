import type { ResumeIntake } from './api';

export const hasIntakeFailure=(value:ResumeIntake)=>['FAILED_RETRYABLE','QUARANTINED','REJECTED'].includes(value.status)
  || [value.processingStatus,value.scoreStatus].some(status=>status==='FAILED_RETRYABLE'||status==='FAILED_FINAL');

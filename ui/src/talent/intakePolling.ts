import type { ResumeIntake } from './api';

const ACTIVE_SCORE_STATUSES=new Set(['QUEUED','RUNNING','WAITING_JD','WAITING_CONFIG']);

const hasActiveWork=(value:ResumeIntake)=>value.status==='PARSING'
  ||value.status==='CONFIRMED'
  ||(Boolean(value.jdVersionId)&&ACTIVE_SCORE_STATUSES.has(value.scoreStatus??''))
  ||(value.status==='COMPLETED'&&Boolean(value.jdVersionId)&&!value.scoreRunId&&(!value.scoreStatus||value.scoreStatus==='NOT_APPLICABLE'));

export const shouldPollIntakes=(items:ResumeIntake[]|undefined,visibilityState:DocumentVisibilityState)=>(visibilityState==='visible'&&Boolean(items?.some(hasActiveWork)))?3000:false;

import { afterEach, expect, it, vi } from 'vitest';
import { request } from '../api/http';
import { followUpPlansApi, type FollowUpPlan } from './followUpPlansApi';

vi.mock('../api/http',()=>({request:vi.fn()}));
afterEach(()=>vi.clearAllMocks());
const plan={id:'plan',applicationId:'app',version:'7'} as FollowUpPlan;

it('passes the completion idempotency key together with the original workflow snapshot',async()=>{
  const completion={result:'CONTINUE' as const,nextPlan:'面试',workflowStepId:'step',workflowStepVersion:'4'};
  await followUpPlansApi.finish(plan,'complete','已沟通',completion,'original-key');
  expect(request).toHaveBeenCalledWith('/applications/app/follow-up-plans/plan/complete',{
    method:'POST',body:{reason:'已沟通',version:'7',...completion},idempotencyKey:'original-key',
  });
});

it('preserves the existing cancellation request contract',async()=>{
  await followUpPlansApi.finish(plan,'cancel','取消原因');
  expect(request).toHaveBeenCalledWith('/applications/app/follow-up-plans/plan/cancel',{
    method:'POST',body:{reason:'取消原因',version:'7'},
  });
});

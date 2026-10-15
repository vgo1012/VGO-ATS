import { afterEach, expect, it, vi } from 'vitest';
import { followUpPlansApi, type FollowUpPlan, type PlanFields } from './followUpPlansApi';
import { findCurrentFollowUpPlan, matchesReschedule } from './followUpPlanRecovery';

afterEach(()=>vi.restoreAllMocks());
const body:PlanFields={startsAt:'2030-01-01T02:00:00Z',endsAt:'2030-01-01T02:30:00Z',channel:'PHONE',note:'事项',version:'1'};
const plan={...body,id:'plan',applicationId:'app',status:'SCHEDULED'} as FollowUpPlan;
it('finds the exact plan past the first page without a date or status filter',async()=>{
  const read=vi.spyOn(followUpPlansApi,'list').mockResolvedValueOnce({items:[{...plan,applicationId:'other'}],nextCursor:'next',hasMore:true}).mockResolvedValueOnce({items:[plan],nextCursor:null,hasMore:false});
  expect(await findCurrentFollowUpPlan('app','plan')).toBe(plan);
  expect(read.mock.calls).toEqual([[{applicationId:'app'}],[{applicationId:'app',cursor:'next'}]]);
});
it.each([null,'again'])('rejects incomplete or repeated pagination with cursor %s',async nextCursor=>{
  vi.spyOn(followUpPlansApi,'list').mockResolvedValue({items:[],hasMore:true,nextCursor});
  await expect(findCurrentFollowUpPlan('app','plan')).rejects.toThrow('预约分页读取异常');
});
it('bounds traversal when every cursor is different',async()=>{
  let page=0;
  const read=vi.spyOn(followUpPlansApi,'list').mockImplementation(()=>Promise.resolve({items:[],hasMore:true,nextCursor:String(++page)}));
  await expect(findCurrentFollowUpPlan('app','plan')).rejects.toThrow('预约记录过多');
  expect(read).toHaveBeenCalledTimes(100);
});
it('matches equivalent instants and normalized optional notes but never a terminal plan',()=>{
  expect(matchesReschedule({...plan,startsAt:'2030-01-01T10:00:00+08:00',note:' 事项 '},body)).toBe(true);
  expect(matchesReschedule({...plan,status:'CANCELLED'},body)).toBe(false);
  expect(matchesReschedule({...plan,channel:'WECHAT'},body)).toBe(false);
  expect(matchesReschedule({...plan,note:'不同事项'},body)).toBe(false);
});

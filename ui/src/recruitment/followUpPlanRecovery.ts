import { followUpPlansApi, type FollowUpPlan, type PlanFields } from './followUpPlansApi';

/** 按活动读取所有状态，改期后的预约可能已经移出原日历日期范围。 */
export async function findCurrentFollowUpPlan(applicationId:string,planId:string):Promise<FollowUpPlan> {
  let cursor:string|undefined;
  const seen=new Set<string>();
  for(let pageNo=0;pageNo<100;pageNo++){
    const page=await followUpPlansApi.list({applicationId,...(cursor?{cursor}:{})});
    const plan=page.items.find(item=>item.id===planId&&item.applicationId===applicationId);
    if(plan)return plan;
    if(!page.hasMore)throw new Error('未找到当前预约，请关闭后重新读取日程。填写内容已保留。');
    if(!page.nextCursor||seen.has(page.nextCursor))throw new Error('预约分页读取异常，请重新核对。');
    cursor=page.nextCursor;seen.add(cursor);
  }
  throw new Error('该活动预约记录过多，暂无法核对，请关闭后重新读取日程。');
}

export function matchesReschedule(plan:FollowUpPlan,body:PlanFields):boolean {
  return plan.status==='SCHEDULED'
    && Number.isFinite(Date.parse(body.startsAt))&&Date.parse(plan.startsAt)===Date.parse(body.startsAt)
    && Number.isFinite(Date.parse(body.endsAt))&&Date.parse(plan.endsAt)===Date.parse(body.endsAt)
    && plan.channel===body.channel&&(plan.note??'').trim()===body.note.trim();
}

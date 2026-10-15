export function confirmationLabel(status: string): string {
  return ({CONFIRMED:'已确认',PENDING:'待确认',DECLINED:'已婉拒',NOT_REQUIRED:'无需确认'} as Record<string,string>)[status]??'待确认';
}

export const interviewTypeLabels: Record<string, string> = { INTERNAL: '内部面试', EXTERNAL: '外部面试', EXTERNAL_CLIENT: '甲方面试 · 人工记录', EXTERNAL_CLIENT_AI: '甲方面试 · AI 方式 · 人工记录' };
export const interviewStatusLabels: Record<string, string> = { SCHEDULED: '待进行', COMPLETED: '已完成', CANCELLED: '已取消' };

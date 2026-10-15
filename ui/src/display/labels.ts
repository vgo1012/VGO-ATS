// Display vocabulary only. Wire values and workflow transitions remain unchanged.
const labels: Record<string, string> = {
  ACTIVE:'启用', DISABLED:'停用', DELETED:'已删除', DRAFT:'草稿', PUBLISHED:'已发布', SUPERSEDED:'历史版本', PAUSED:'已暂停', CLOSED:'已关闭',
  PENDING_IMPORT:'待入库', PARSING:'解析处理中', IDENTITY_REVIEW_REQUIRED:'身份待核对', COMPLETED:'导入完成', READY_FOR_CONFIRMATION:'待确认入库', CONFIRMED:'已确认入库', IGNORED:'已忽略', REJECTED:'已拒绝', QUARANTINED:'已隔离', FAILED_RETRYABLE:'失败，可重试',
  CLEAN:'安全检查通过', MALICIOUS:'检测到风险文件', UNAVAILABLE:'暂不可用',
  MANUAL:'手动上传', BATCH:'批量导入', MAIL:'邮箱收件', BOSS:'Boss 直聘',
  QUEUED:'排队中', RUNNING:'处理中', SUCCEEDED:'已完成', PARTIAL:'部分完成', PARTIAL_SUCCESS:'部分完成', FAILED:'失败', CANCELLING:'取消中', CANCELLED:'已取消',
  PASS:'通过', FAIL:'不通过', REJECT:'未通过', WITHDRAW:'候选人退出', SUBMITTED:'已提交',
  AVAILABLE:'可选', NOT_SELECTED:'未选择', PENDING:'待处理', IN_PROGRESS:'进行中', PASSED:'已通过', WITHDRAWN:'已撤回', SKIPPED:'已跳过',
  PATH_SELECTION:'待 HR 跟进', IN_PROCESS:'流程推进', DECISION:'人工决定', OFFER:'录用沟通', TERMINATED:'已终止', ONBOARDED:'已入职',
  NOT_STARTED:'未开始', ISSUED:'已发出', ACCEPTED:'已接受', STATUS:'状态记录', CORRECTION:'追加更正', REOPEN:'重新开启',
  ONLINE:'线上面试', ONSITE:'现场面试', PHONE:'电话', CLIENT_MANAGED:'甲方安排',
  IN_APP:'站内通知', EMAIL:'邮件', WECHAT:'微信', ENTERPRISE_WECHAT:'企业微信', MANUAL_OTHER:'其他人工渠道', IN_PERSON:'当面沟通',
  CANDIDATE:'候选人', INTERVIEWER:'面试官', OWNER:'负责人', HR:'招聘专员',
  SENT:'已发送', SENT_MANUAL:'已人工发送', SENT_MANUALLY:'已人工发送', FAILED_EXTERNAL:'发送失败', PENDING_MANUAL:'待人工发送', PENDING_EXTERNAL:'待发送',
  SCHEDULED:'待进行', CREATED:'已创建', CREATE:'创建排期', RESCHEDULED:'已改期', RESCHEDULE:'调整排期', CANCEL:'取消排期',
  SCREENING:'甲方筛选中', INTERVIEWING:'甲方面试中', EXPIRED:'已过期', NOT_SENT:'尚未发送',
  UNKNOWN:'信息不足', KNOWN:'有事实依据', MUST:'必须满足', PREFER:'优先加分', DISPLAY_ONLY:'仅供参考',
  AI_PARSED:'AI 解析', AI_PARSE:'AI 解析', HUMAN_CORRECTED:'人工更正', HUMAN_CORRECTION:'人工更正', HUMAN_PROVIDED:'人工补充',
  MALE:'男', FEMALE:'女', OTHER:'其他', BACHELOR:'本科', MASTER:'硕士', DOCTOR:'博士', DOCTORATE:'博士', PHD:'博士', ASSOCIATE:'专科', COLLEGE:'专科', HIGH_SCHOOL:'高中',
  FULL_TIME:'全日制', PART_TIME:'非全日制', VERIFIED:'已核验', UNVERIFIED:'未核验', MISMATCH:'信息不一致',
  OPEN:'待处理', RESOLVED:'已解决', EMAIL_PHONE_MISMATCH:'邮箱与手机号关联不一致', STRONG_IDENTITY_CONFLICT:'联系方式关联冲突',
  MONTHLY:'元 / 月', YEARLY:'元 / 年', INTERESTED:'有意向', NOT_INTERESTED:'暂无意向', NO_RESPONSE:'未接通', FOLLOW_UP:'待跟进',
  ANNUAL:'元 / 年', CONTINUE:'继续推进', DECLINED:'已婉拒', CLIENT_SYSTEM:'甲方系统', CANDIDATE_DECLINED:'候选人拒绝', COMPANY_WITHDREW:'公司撤回',
  UNCONFIRMED:'待确认', NOT_REQUIRED:'无需确认', PENDING_CONFIRMATION:'待确认',
  USER:'面试参与人', PARTICIPANT:'面试参与人', INTERNAL_PARTICIPANT:'面试参与人',
};

export function displayEnum(value: string | null | undefined, fallback = '待确认'): string {
  if (!value?.trim()) return fallback;
  return labels[value] ?? (/^[A-Z][A-Z0-9_]*$/.test(value) ? fallback : value);
}

export function criterionResult(value: string | undefined): string {
  return value === 'PASS' ? '满足' : value === 'FAIL' ? '不满足' : '待核实';
}

export function formatDateTime(value: string | null | undefined): string {
  const timestamp = value ? Date.parse(value) : Number.NaN;
  return Number.isNaN(timestamp) ? '时间未记录' : new Intl.DateTimeFormat('zh-CN', {
    timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hour12:false,
  }).format(timestamp);
}

export function safeMeetingUrl(value: string | null | undefined): string | undefined {
  try { const url = new URL(value ?? ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined; }
  catch { return undefined; }
}

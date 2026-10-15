import { jobLabel } from '../demand/jobLabel';
import { displayEnum, formatDateTime, safeMeetingUrl } from '../display/labels';
import { interviewStepLabel } from '../recruitment/workflowLabels';
import type { InterviewDetails, InterviewMaterial } from './api';

export function interviewInfoText(value: InterviewDetails, material: InterviewMaterial) {
  const interview=value.interview;
  const calendar=value.externalCalendar;
  const cancelled=interview.status==='CANCELLED'||calendar?.status==='CANCELLED';
  const link=cancelled?undefined:safeMeetingUrl(calendar?.meetingLink)??safeMeetingUrl(interview.meetingUrl);
  return [
    '面试安排',
    `候选人：${material.candidateName}`,
    `应聘岗位：${jobLabel(material.jdTitle,material.jdCode)}`,
    ...(interview.clientName?[`甲方公司：${interview.clientName}`]:[]),
    `状态：${interview.status==='CANCELLED'?'已取消':interview.status==='COMPLETED'?'已完成':'待进行'}`,
    `面试轮次：${interviewStepLabel(interview,interview.interviewType==='INTERNAL'?'INTERNAL':'EXTERNAL')}`,
    `时间：${formatDateTime(interview.scheduledStartAt)} — ${formatDateTime(interview.scheduledEndAt)}（北京时间）`,
    `面试方式：${displayEnum(interview.meetingMode)}`,
    ...(interview.locationText?[`面试地点：${interview.locationText}`]:[]),
    ...(!cancelled&&calendar?.meetingCode?[`会议号：${calendar.meetingCode}`]:[]),
    ...(link?[`会议链接：${link}`]:[]),
    ...(!cancelled&&calendar?.mode==='WECOM_MEETING'&&!calendar.meetingCode&&!link?['会议接入信息：尚未生成，待 HR 补充']:[]),
  ].join('\n');
}

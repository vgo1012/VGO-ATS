package com.ezaigc.hr.recruiting.activity.mapper;

import com.ezaigc.hr.recruiting.activity.enums.ActivityAction;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackStatus;

/** SQL 在数据库归并同次事实并使用稳定游标；不将完整历史加载到应用内分页。 */
public final class ActivityRecordSql {
  private ActivityRecordSql() {}
  public static final String SELECT = """
<script>
WITH facts AS (
SELECT e.id source_id, 'COMMUNICATION' source_type, '"""
      + ActivityAction.Codes.PENDING
      + """
' category, '实际沟通' title, e.result action, e.summary comment, e.recorded_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('channel',e.channel,'result',e.result,'nextPlan',e.next_plan,'communicatedAt',e.communicated_at) details FROM recruitment_communication e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'FOLLOW_UP_PLAN' source_type, '"""
      + ActivityAction.Codes.PENDING
      + """
' category, '沟通预约' title, e.action action, e.reason comment, e.create_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('beforeSnapshot',e.before_snapshot,'afterSnapshot',e.after_snapshot) details FROM recruitment_follow_up_plan_history e JOIN recruitment_follow_up_plan p ON p.id=e.plan_id WHERE p.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'FOLLOW_UP' source_type, '"""
      + ActivityAction.Codes.PENDING
      + """
' category, '步骤沟通' title, e.channel action, e.summary comment, e.recorded_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('stepId',e.workflow_step_id,'nextPlan',e.next_plan,'contactedAt',e.contacted_at) details FROM recruitment_workflow_follow_up_record e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'WORKFLOW_STEP' source_type, CASE WHEN e.step_code LIKE '%INTERVIEW%' THEN 'INTERVIEW' WHEN e.step_code='CANDIDATE_COMMUNICATION' THEN '"""
      + ActivityAction.Codes.PENDING
      + """
' ELSE 'OTHER' END category, '步骤结果' title, e.status action, e.result_reason comment, e.update_id actor_user_id, e.update_name actor_name, e.completed_at occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('stepCode',e.step_code,'roundNo',e.round_no,'status',e.status,'completedAt',e.completed_at) details FROM recruitment_application_workflow_step e WHERE e.application_id=#{q.applicationId} AND e.completed_at IS NOT NULL AND e.result_reason IS NOT NULL AND e.step_code NOT IN ('DECISION','OFFER')
UNION ALL
SELECT e.id source_id, 'PATH' source_type, 'OTHER' category, '流程选择' title, e.change_type action, e.reason comment, e.operated_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('beforeStepIds',e.before_step_ids_json,'afterStepIds',e.after_step_ids_json) details FROM recruitment_workflow_path_change_record e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'STAGE' source_type, 'OTHER' category, '阶段变更' title, e.to_stage action, e.reason comment, e.operated_by_user_id actor_user_id, e.create_name actor_name, e.occurred_at occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('fromStage',e.from_stage,'toStage',e.to_stage) details FROM recruitment_stage_history e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'DECISION' source_type, CASE WHEN e.decision='PASS' THEN 'ONBOARDED' ELSE 'ENDED' END category, '人工招聘决定' title, e.record_type action, e.reason comment, e.decided_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('decision',e.decision,'decidedAt',e.decided_at,'correctsRecordId',e.corrects_record_id) details FROM recruitment_decision_record e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'OFFER' source_type, 'ONBOARDED' category, 'Offer 进展' title, e.record_type action, e.reason comment, e.operated_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('status',e.status,'occurredAt',e.occurred_at,'correctsRecordId',e.corrects_record_id,'reopensRecordId',e.reopens_record_id,'source',e.source,'terminationReason',e.termination_reason) details FROM recruitment_offer_status_record e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'ONBOARDING' source_type, 'ONBOARDED' category, '确认入职' title, 'ONBOARDED' action, e.reason comment, e.operated_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, NULL company_id, JSON_OBJECT('onboardedAt',e.onboarded_at,'source',e.source,'sourceReference',e.source_reference) details FROM recruitment_onboarding_event e  WHERE e.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'INTERVIEW_SCHEDULE' source_type, 'INTERVIEW' category, '面试排期' title, e.action_type action, CASE WHEN e.action_type='CREATE' THEN NULLIF(CONCAT_WS(CHAR(10),NULLIF(i.initial_comment,''),NULLIF(e.reason,'未填写安排说明')),'') ELSE e.reason END comment, e.operated_by_user_id actor_user_id, e.create_name actor_name, e.occurred_at occurred_at, i.id interview_id, NULL company_id, JSON_OBJECT('roundNo',i.round_no,'interviewType',i.interview_type,'beforeSnapshot',e.before_snapshot,'afterSnapshot',e.after_snapshot) details FROM interview_schedule_history e JOIN interview_interview i ON i.id=e.interview_id WHERE i.application_id=#{q.applicationId}
UNION ALL
SELECT e.id source_id, 'INTERVIEW_FEEDBACK' source_type, 'INTERVIEW' category, '正式面评' title, '"""
      + InterviewFeedbackStatus.Codes.SUBMITTED
      + """
' action, e.short_comment comment, f.author_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, i.id interview_id, NULL company_id, JSON_OBJECT('roundNo',i.round_no,'interviewType',i.interview_type,'verdict',e.verdict,'versionNo',e.version_no,'feedbackKind',f.feedback_kind,'feedbackOccurredAt',e.feedback_occurred_at,'externalFeedbackSource',e.external_feedback_source,'changeReason',e.change_reason) details FROM interview_feedback_version e JOIN interview_feedback f ON f.id=e.feedback_id JOIN interview_interview i ON i.id=f.interview_id WHERE i.application_id=#{q.applicationId} AND f.status='"""
      + InterviewFeedbackStatus.Codes.SUBMITTED
      + """
' AND e.version_no=f.current_version_no
UNION ALL
SELECT e.id source_id, 'RESTRICTION' source_type, 'BLACKLIST' category, '指定公司限制' title, e.record_type action, e.reason_detail comment, e.recorded_by_user_id actor_user_id, e.create_name actor_name, e.create_time occurred_at, NULL interview_id, e.client_id company_id, JSON_OBJECT('status',e.status,'restrictionIntent',e.restriction_intent,'sourceChannel',e.source_channel,'sourceOccurredAt',e.source_occurred_at,'expiresAt',e.expires_at,'correctsRecordId',e.corrects_record_id,'sourceSubmissionId',e.source_submission_id,'sourceApplicationId',COALESCE(e.source_application_id,s.application_id),'evidenceReference',e.evidence_reference,'companyName',c.name) details FROM submission_client_restriction e JOIN demand_client c ON c.id=e.client_id LEFT JOIN submission_client_submission s ON s.id=e.source_submission_id WHERE e.candidate_id=#{q.candidateId}
), annotated AS (
SELECT f.source_id, f.source_type, f.title, f.action, f.comment, f.actor_user_id,
 COALESCE(o.create_name,f.actor_name) actor_name, f.occurred_at, f.interview_id, f.company_id, f.details,
 COALESCE(CONVERT(o.action USING utf8mb4),f.category) category,
 CASE WHEN o.id IS NULL THEN f.source_id ELSE CONCAT(o.create_id,':',o.action,':',o.operation_id) END group_id,
 CASE o.action WHEN '"""
      + ActivityAction.Codes.PENDING
      + """
' THEN '待定' WHEN 'INTERVIEW' THEN '面试' WHEN 'ENDED' THEN '放弃'
 WHEN 'ONBOARDED' THEN '入职' WHEN 'BLACKLIST' THEN '黑名单' ELSE f.title END display_title
FROM facts f LEFT JOIN recruitment_activity_origin o ON o.source_type=f.source_type
 AND o.source_id=f.source_id AND o.application_id=#{q.applicationId}
), grouped AS (
SELECT group_id id, MAX(category) type, MAX(display_title) title, MIN(occurred_at) occurred_at,
 JSON_ARRAYAGG(JSON_OBJECT('sourceType',source_type,'sourceId',source_id,'title',title,
 'action',action,'comment',comment,'actorUserId',actor_user_id,'actorName',actor_name,
 'occurredAt',UNIX_TIMESTAMP(occurred_at),
 'interviewId',interview_id,'companyId',company_id,'details',JSON_EXTRACT(details,'$'))) facts_json
FROM annotated
<if test="q.type != null">WHERE category=#{q.type}</if>
GROUP BY group_id
)
SELECT id,type,title,occurred_at,facts_json FROM grouped
<if test="q.cursorTime != null">
WHERE (occurred_at &lt; #{q.cursorTime} OR (occurred_at=#{q.cursorTime} AND id &lt; #{q.cursorId}))
</if>
ORDER BY occurred_at DESC,id DESC LIMIT #{q.limit}
</script>
      """;
}

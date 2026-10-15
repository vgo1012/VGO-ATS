package com.ezaigc.hr.recruiting.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewTemplateStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InterviewMapper extends BaseMapper<Interview>, InterviewSqlOperations {
  @SpecialSql("仅检索当前负责人面试涉及的固定JD版本，EXISTS去重并稳定分页")
  @Select("""
      SELECT j.jd_id,j.id AS jd_version_id,j.version_no AS jd_version_no,j.title,j.create_time AS sort_time
      FROM demand_jd_version j
      WHERE EXISTS (SELECT 1 FROM interview_interview i
          WHERE i.jd_version_id=j.id AND i.owner_user_id=#{userId})
        AND (#{query} IS NULL OR LOCATE(#{query},j.title)>0)
        AND (#{jdId} IS NULL OR j.jd_id=#{jdId})
        AND (#{cursorTime} IS NULL OR j.create_time>#{cursorTime}
          OR (j.create_time=#{cursorTime} AND j.id>#{cursorId}))
      ORDER BY j.create_time,j.id LIMIT #{limit}
      """)
  List<InterviewJobOption> findInvitedJobOptions(String userId, String query, String jdId,
      Instant cursorTime, String cursorId, int limit);
  @SpecialSql("锁定面试聚合以串行化排期、取消、反馈和通知变更")
  @Select(
      "SELECT"
          + " id,application_id,candidate_id,jd_id,jd_version_id,workflow_template_version_id,workflow_step_id,step_code,initial_comment,interview_type,round_no,client_submission_id,status,scheduled_start_at,scheduled_end_at,timezone_id,meeting_mode,location_text,meeting_url,candidate_notification_channel,candidate_confirmation_status,owner_user_id,owner_department_id,next_responsible_user_id,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM interview_interview WHERE id=#{id} FOR UPDATE")
  Interview lockInterview(String id);

  @SpecialSql("按当前反馈版本的 EXISTS 联表结果筛选并执行稳定游标查询")
  @Select(
      """
      SELECT DISTINCT i.id,i.application_id,i.candidate_id,i.jd_id,i.jd_version_id,i.workflow_template_version_id,i.workflow_step_id,i.step_code,i.initial_comment,i.interview_type,i.round_no,i.client_submission_id,i.status,i.scheduled_start_at,i.scheduled_end_at,i.timezone_id,i.meeting_mode,i.location_text,i.meeting_url,i.candidate_notification_channel,i.candidate_confirmation_status,i.owner_user_id,i.owner_department_id,i.next_responsible_user_id,i.version,i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time FROM interview_interview i
       WHERE (#{jdId} IS NULL OR i.jd_id=#{jdId}) AND (#{jdVersionId} IS NULL OR i.jd_version_id=#{jdVersionId})
         AND (#{applicationId} IS NULL OR i.application_id=#{applicationId})
         AND (#{workflowStepId} IS NULL OR i.workflow_step_id=#{workflowStepId})
         AND (#{interviewType} IS NULL OR i.interview_type=#{interviewType}
              OR (#{interviewType}='EXTERNAL' AND i.interview_type IN ('EXTERNAL_CLIENT','EXTERNAL_CLIENT_AI')))
         AND (#{clientId} IS NULL OR EXISTS(SELECT 1 FROM demand_jd_version v JOIN demand_jd j ON j.id=v.jd_id
              WHERE v.id=i.jd_version_id
                AND CASE WHEN v.recruitment_type IS NOT NULL THEN v.client_id ELSE j.client_id END=#{clientId}))
         AND (#{stepCode} IS NULL OR i.step_code=#{stepCode}) AND (#{roundNo} IS NULL OR i.round_no=#{roundNo})
         AND (#{ownerUserId} IS NULL OR i.owner_user_id=#{ownerUserId}) AND (#{status} IS NULL OR i.status=#{status})
         AND (#{result} IS NULL OR EXISTS(SELECT 1 FROM interview_feedback f JOIN interview_feedback_version fv ON fv.feedback_id=f.id AND fv.version_no=f.current_version_no WHERE f.interview_id=i.id AND f.status='"""
          + InterviewFeedbackStatus.Codes.SUBMITTED
          + """
      ' AND fv.verdict=#{result}))
         AND (#{from} IS NULL OR i.scheduled_start_at>=#{from})
         AND (#{to} IS NULL OR i.scheduled_start_at<#{to})
         AND (#{cursorTime} IS NULL OR i.scheduled_start_at>#{cursorTime} OR (i.scheduled_start_at=#{cursorTime} AND i.id>#{cursorId}))
       ORDER BY i.scheduled_start_at,i.id LIMIT #{limit}
      """)
  /** 查询面试列表。 */
  List<Interview> findInterviews(
      String jdId,
      String jdVersionId,
      String stepCode,
      Integer roundNo,
      String ownerUserId,
      String status,
      String result,
      String applicationId,
      Instant from,
      Instant to,
      Instant cursorTime,
      String cursorId,
      String workflowStepId,
      String interviewType,
      String clientId,
      int limit);

  /** 更新指定业务记录。 */
  @SpecialSql("以排期中状态为前置条件原子完成面试并递增版本")
  @Update(
      "UPDATE interview_interview SET status='"
          + InterviewStatus.Codes.COMPLETED
          + "',version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + InterviewStatus.Codes.SCHEDULED
          + "'")
  int complete(String id, String actorId, Instant now);

  @SpecialSql("显式校验并转换 JSON 快照后追加不可变排期历史")
  @Insert(
      "INSERT INTO"
          + " interview_schedule_history(id,interview_id,action_type,before_snapshot,after_snapshot,reason,operated_by_user_id,occurred_at)"
          + " VALUES(#{id},#{interviewId},#{actionType},CASE WHEN #{beforeJson} IS NULL THEN NULL"
          + " ELSE CAST(#{beforeJson} AS JSON) END,CAST(#{afterJson} AS"
          + " JSON),#{reason},#{actorId},#{now})")
  /** 新增历史。 */
  int insertHistory(
      String id,
      String interviewId,
      String actionType,
      String beforeJson,
      String afterJson,
      String reason,
      String actorId,
      Instant now);

  @SpecialSql("连接模板及其当前版本读取启用的通知模板")
  @Select(
      "SELECT"
          + " tv.id,tv.template_id,tv.version_no,tv.subject_template,tv.body_template,tv.create_id,tv.create_time"
          + " FROM interview_template t JOIN interview_template_version tv ON tv.template_id=t.id"
          + " AND tv.version_no=t.current_version_no WHERE t.code=#{code} AND t.status='"
          + InterviewTemplateStatus.Codes.ACTIVE
          + "'")
  /** 查询生效模板。 */
  TemplateVersion findActiveTemplate(String code);

  @SpecialSql("锁定通知意图以串行化人工发送和外部重试")
  @Select(
      "SELECT"
          + " id,interview_id,notification_type,recipient_type,recipient_reference,channel,template_version_id,rendered_subject,rendered_body,status,external_message_id,last_error_code,last_error_detail,retry_count,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM interview_notification_intent WHERE id=#{id} FOR UPDATE")
  NotificationIntent lockNotification(String id);

  @SpecialSql("通知写入前仅定位不可变候选人关联，保持 Candidate → Interview/通知锁序")
  @Select("SELECT i.candidate_id FROM interview_notification_intent n JOIN interview_interview i ON i.id=n.interview_id WHERE n.id=#{id}")
  String findNotificationCandidateId(String id);

  @SpecialSql("按面试负责人读取待反馈，不向记录的面试官分派任务")
  @Select(
      """
      SELECT i.id,i.application_id,i.candidate_id,i.jd_id,i.jd_version_id,i.workflow_template_version_id,i.workflow_step_id,i.step_code,i.initial_comment,i.interview_type,i.round_no,i.client_submission_id,i.status,i.scheduled_start_at,i.scheduled_end_at,i.timezone_id,i.meeting_mode,i.location_text,i.meeting_url,i.candidate_notification_channel,i.candidate_confirmation_status,i.owner_user_id,i.owner_department_id,i.next_responsible_user_id,i.version,i.ext,i.remark,i.create_id,i.create_name,i.create_time,i.update_id,i.update_name,i.update_time FROM interview_interview i
       WHERE i.owner_user_id=#{userId} AND i.status='"""
          + InterviewStatus.Codes.SCHEDULED
          + """
      '
       ORDER BY i.scheduled_start_at,i.id LIMIT #{limit}
      """)
  List<Interview> findMyInterviewTasks(String userId, int limit);

  @SpecialSql("使用 INSERT IGNORE 原子保留幂等键并处理并发重放")
  @Insert(
      "INSERT IGNORE INTO"
          + " interview_idempotency_record(actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,create_time)"
          + " VALUES(#{actorId},#{key},#{action},#{fingerprint},#{targetId},#{now})")
  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}

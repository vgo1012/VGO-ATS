package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper extends BaseMapper<AuditEventEntity> {
  @SpecialSql("跨 identity 表以 INSERT IGNORE 幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('identity:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,u.id,u.home_department_id,a.reason,a.before_snapshot,a.after_snapshot,'identity',a.id,a.occurred_at,UTC_TIMESTAMP(6)
        FROM identity_audit_event a LEFT JOIN identity_user_account u ON a.target_type='USER_ACCOUNT' AND u.id=a.target_id\
      """)
  /** 采集身份。 */
  int captureIdentity();

  @SpecialSql("跨 demand 表以 INSERT IGNORE 幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('demand:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,d.owner_user_id,d.owner_department_id,a.reason,a.before_snapshot,a.after_snapshot,'demand',a.id,a.occurred_at,UTC_TIMESTAMP(6)
        FROM demand_audit_event a LEFT JOIN demand_recruitment_demand d ON (a.target_type='DEMAND' AND d.id=a.target_id) OR (a.target_type='JD' AND d.id=(SELECT j.demand_id FROM demand_jd j WHERE j.id=a.target_id))\
      """)
  /** 采集招聘需求。 */
  int captureDemand();

  @SpecialSql("以 INSERT SELECT/IGNORE 幂等投影匹配审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('matching:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,a.reason,a.before_snapshot,a.after_snapshot,'matching',a.id,a.occurred_at,UTC_TIMESTAMP(6) FROM matching_audit_event a\
      """)
  /** 采集匹配。 */
  int captureMatching();

  @SpecialSql("跨 talent 表以 INSERT IGNORE 幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('talent:',e.id),256),1,26),e.event_type,e.actor_user_id,e.target_type,e.target_id,c.owner_user_id,c.owner_department_id,e.reason,'talent',e.id,e.occurred_at,UTC_TIMESTAMP(6)
        FROM talent_process_event e LEFT JOIN talent_candidate c ON e.target_type='CANDIDATE' AND c.id=e.target_id\
      """)
  /** 采集人才。 */
  int captureTalent();

  @SpecialSql("连接招聘阶段和申请并幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('recruitment-stage:',h.id),256),1,26),CONCAT('APPLICATION_STAGE_',h.to_stage),h.operated_by_user_id,'APPLICATION',h.application_id,a.owner_user_id,a.owner_department_id,h.reason,'recruitment',h.id,h.occurred_at,UTC_TIMESTAMP(6)
        FROM recruitment_stage_history h JOIN recruitment_application a ON a.id=h.application_id\
      """)
  /** 采集招聘阶段列表。 */
  int captureRecruitmentStages();

  @SpecialSql("连接招聘决策和申请并幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('recruitment-decision:',d.id),256),1,26),CONCAT('APPLICATION_DECISION_',d.record_type,'_',d.decision),d.decided_by_user_id,'APPLICATION',d.application_id,a.owner_user_id,a.owner_department_id,d.reason,'recruitment',d.id,d.decided_at,UTC_TIMESTAMP(6)
        FROM recruitment_decision_record d JOIN recruitment_application a ON a.id=d.application_id\
      """)
  /** 采集招聘决定列表。 */
  int captureRecruitmentDecisions();

  @SpecialSql("连接面试历史和面试主表并幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('interview:',h.id),256),1,26),CONCAT('INTERVIEW_',h.action_type),h.operated_by_user_id,'INTERVIEW',h.interview_id,i.owner_user_id,i.owner_department_id,h.reason,'interview',h.id,h.occurred_at,UTC_TIMESTAMP(6)
        FROM interview_schedule_history h JOIN interview_interview i ON i.id=h.interview_id\
      """)
  /** 采集面试列表。 */
  int captureInterviews();

  @SpecialSql("连接送客快照和送客主表并幂等投影审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('submission-send:',s.id),256),1,26),'CLIENT_SUBMISSION_SENT',s.sent_by_user_id,'CLIENT_SUBMISSION',s.submission_id,c.owner_user_id,c.owner_department_id,NULL,'submission',s.id,s.sent_at,UTC_TIMESTAMP(6)
        FROM submission_send_snapshot s JOIN submission_client_submission c ON c.id=s.submission_id\
      """)
  /** 采集客户推荐发送列表。 */
  int captureSubmissionSends();

  @SpecialSql("按动态数据权限、游标和多条件检索审计事件")
  @Select(
      """
      <script>
      SELECT id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,
             before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at,version,
             ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
        FROM reporting_audit_event
       WHERE occurred_at &gt;= #{from} AND occurred_at &lt; #{to}
         <if test='eventType != null'>AND event_type=#{eventType}</if>
         <if test='actorUserId != null'>AND actor_user_id=#{actorUserId}</if>
         <if test='targetType != null'>AND target_type=#{targetType}</if>
         <if test='targetId != null'>AND target_id=#{targetId}</if>
         <if test='beforeAt != null'>AND (occurred_at &lt; #{beforeAt} OR (occurred_at=#{beforeAt} AND id &lt; #{beforeId}))</if>
         AND (#{allScope}=TRUE OR actor_user_id=#{currentUserId} OR owner_user_id=#{currentUserId}
           <if test='managedUserIds != null and managedUserIds.size() &gt; 0'>OR owner_user_id IN <foreach collection='managedUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>
           <if test='departmentIds != null and departmentIds.size() &gt; 0'>OR department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>)
       ORDER BY occurred_at DESC,id DESC LIMIT #{limit}
      </script>
      """)
  List<AuditEventEntity> search(
      Instant from,
      Instant to,
      String eventType,
      String actorUserId,
      String targetType,
      String targetId,
      Instant beforeAt,
      String beforeId,
      boolean allScope,
      String currentUserId,
      List<String> managedUserIds,
      List<String> departmentIds,
      int limit);
}

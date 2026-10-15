package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.domain.entity.AuditCaptureCheckpointEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuditCaptureCheckpointMapper
    extends BaseMapper<AuditCaptureCheckpointEntity> {

  @SpecialSql("仅忽略唯一键冲突并并发安全地初始化审计采集检查点")
  @Insert(
      """
      INSERT INTO reporting_audit_capture_checkpoint(
          checkpoint_name,source_code,lane,last_source_id,scanned_count,version,
          create_id,create_time,update_id,update_time)
      VALUES(#{checkpointName},#{sourceCode},#{lane},#{lastSourceId},0,0,
          #{actorId},#{now},#{actorId},#{now})
      ON DUPLICATE KEY UPDATE
          checkpoint_name=reporting_audit_capture_checkpoint.checkpoint_name
      """)
  int insertIfAbsent(
      String checkpointName,
      String sourceCode,
      String lane,
      String lastSourceId,
      String actorId,
      Instant now);

  @SpecialSql("锁定单个审计采集检查点以串行化同来源同通道批次")
  @Select(
      """
      SELECT checkpoint_name,source_code,lane,last_source_id,scanned_count,version,
             ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
        FROM reporting_audit_capture_checkpoint
       WHERE checkpoint_name=#{checkpointName}
       FOR UPDATE
      """)
  AuditCaptureCheckpointEntity lock(String checkpointName);

  @SpecialSql("带版本条件推进审计采集检查点")
  @Update(
      """
      UPDATE reporting_audit_capture_checkpoint
         SET last_source_id=#{lastSourceId},scanned_count=scanned_count+#{scannedCount},
             version=version+1,update_id=#{actorId},update_time=#{now}
       WHERE checkpoint_name=#{checkpointName} AND version=#{expectedVersion}
      """)
  int advance(
      String checkpointName,
      String lastSourceId,
      int scannedCount,
      int expectedVersion,
      String actorId,
      Instant now);

  @SpecialSql("完成校对周期后带版本条件重置扫描游标")
  @Update(
      """
      UPDATE reporting_audit_capture_checkpoint
         SET last_source_id=#{lastSourceId},version=version+1,
             update_id=#{actorId},update_time=#{now}
       WHERE checkpoint_name=#{checkpointName} AND version=#{expectedVersion}
      """)
  int reset(
      String checkpointName,
      String lastSourceId,
      int expectedVersion,
      String actorId,
      Instant now);

  @SpecialSql("按主键游标限批读取身份审计来源 ID")
  @Select("SELECT id FROM identity_audit_event WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextIdentityIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取需求审计来源 ID")
  @Select("SELECT id FROM demand_audit_event WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextDemandIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取匹配审计来源 ID")
  @Select("SELECT id FROM matching_audit_event WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextMatchingIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取人才审计来源 ID")
  @Select("SELECT id FROM talent_process_event WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextTalentIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取招聘阶段审计来源 ID")
  @Select(
      "SELECT id FROM recruitment_stage_history WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextRecruitmentStageIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取招聘决定审计来源 ID")
  @Select(
      "SELECT id FROM recruitment_decision_record WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextRecruitmentDecisionIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取面试审计来源 ID")
  @Select(
      "SELECT id FROM interview_schedule_history WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextInterviewIds(String afterId, int limit);

  @SpecialSql("按主键游标限批读取客户推荐发送审计来源 ID")
  @Select(
      "SELECT id FROM submission_send_snapshot WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
  List<String> nextSubmissionSendIds(String afterId, int limit);

  @SpecialSql("按已选主键范围幂等投影身份审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('identity:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,u.id,u.home_department_id,a.reason,a.before_snapshot,a.after_snapshot,'identity',a.id,a.occurred_at,UTC_TIMESTAMP(6)
        FROM identity_audit_event a LEFT JOIN identity_user_account u ON a.target_type='USER_ACCOUNT' AND u.id=a.target_id
       WHERE a.id > #{afterId} AND a.id <= #{throughId}
      """)
  int captureIdentityRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影需求审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('demand:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,d.owner_user_id,d.owner_department_id,a.reason,a.before_snapshot,a.after_snapshot,'demand',a.id,a.occurred_at,UTC_TIMESTAMP(6)
        FROM demand_audit_event a LEFT JOIN demand_recruitment_demand d ON (a.target_type='DEMAND' AND d.id=a.target_id) OR (a.target_type='JD' AND d.id=(SELECT j.demand_id FROM demand_jd j WHERE j.id=a.target_id))
       WHERE a.id > #{afterId} AND a.id <= #{throughId}
      """)
  int captureDemandRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影匹配审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,reason,before_snapshot,after_snapshot,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('matching:',a.id),256),1,26),a.event_type,a.actor_user_id,a.target_type,a.target_id,a.reason,a.before_snapshot,a.after_snapshot,'matching',a.id,a.occurred_at,UTC_TIMESTAMP(6)
        FROM matching_audit_event a
       WHERE a.id > #{afterId} AND a.id <= #{throughId}
      """)
  int captureMatchingRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影人才审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('talent:',e.id),256),1,26),e.event_type,e.actor_user_id,e.target_type,e.target_id,c.owner_user_id,c.owner_department_id,e.reason,'talent',e.id,e.occurred_at,UTC_TIMESTAMP(6)
        FROM talent_process_event e LEFT JOIN talent_candidate c ON e.target_type='CANDIDATE' AND c.id=e.target_id
       WHERE e.id > #{afterId} AND e.id <= #{throughId}
      """)
  int captureTalentRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影招聘阶段审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('recruitment-stage:',h.id),256),1,26),CONCAT('APPLICATION_STAGE_',h.to_stage),h.operated_by_user_id,'APPLICATION',h.application_id,a.owner_user_id,a.owner_department_id,h.reason,'recruitment',h.id,h.occurred_at,UTC_TIMESTAMP(6)
        FROM recruitment_stage_history h JOIN recruitment_application a ON a.id=h.application_id
       WHERE h.id > #{afterId} AND h.id <= #{throughId}
      """)
  int captureRecruitmentStageRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影招聘决定审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('recruitment-decision:',d.id),256),1,26),CONCAT('APPLICATION_DECISION_',d.record_type,'_',d.decision),d.decided_by_user_id,'APPLICATION',d.application_id,a.owner_user_id,a.owner_department_id,d.reason,'recruitment',d.id,d.decided_at,UTC_TIMESTAMP(6)
        FROM recruitment_decision_record d JOIN recruitment_application a ON a.id=d.application_id
       WHERE d.id > #{afterId} AND d.id <= #{throughId}
      """)
  int captureRecruitmentDecisionRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影面试审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('interview:',h.id),256),1,26),CONCAT('INTERVIEW_',h.action_type),h.operated_by_user_id,'INTERVIEW',h.interview_id,i.owner_user_id,i.owner_department_id,h.reason,'interview',h.id,h.occurred_at,UTC_TIMESTAMP(6)
        FROM interview_schedule_history h JOIN interview_interview i ON i.id=h.interview_id
       WHERE h.id > #{afterId} AND h.id <= #{throughId}
      """)
  int captureInterviewRange(String afterId, String throughId);

  @SpecialSql("按已选主键范围幂等投影客户推荐发送审计事件")
  @Insert(
      """
      INSERT IGNORE INTO reporting_audit_event(id,event_type,actor_user_id,target_type,target_id,owner_user_id,department_id,reason,source_module,source_event_id,occurred_at,captured_at)
      SELECT SUBSTRING(SHA2(CONCAT('submission-send:',s.id),256),1,26),'CLIENT_SUBMISSION_SENT',s.sent_by_user_id,'CLIENT_SUBMISSION',s.submission_id,c.owner_user_id,c.owner_department_id,NULL,'submission',s.id,s.sent_at,UTC_TIMESTAMP(6)
        FROM submission_send_snapshot s JOIN submission_client_submission c ON c.id=s.submission_id
       WHERE s.id > #{afterId} AND s.id <= #{throughId}
      """)
  int captureSubmissionSendRange(String afterId, String throughId);
}

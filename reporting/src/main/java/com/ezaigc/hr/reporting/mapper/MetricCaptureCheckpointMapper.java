package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.domain.entity.MetricCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.MetricSourceRecordStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MetricCaptureCheckpointMapper extends BaseMapper<MetricCaptureCheckpointEntity> {
  @SpecialSql("并发初始化指标独立检查点，保留已有进度")
  @Insert("""
      INSERT INTO reporting_metric_capture_checkpoint(checkpoint_name,source_code,lane,last_source_id,version,
        create_id,create_time,update_id,update_time)
      VALUES(#{name},#{source},#{lane},#{minimum},0,#{actor},#{now},#{actor},#{now})
      ON DUPLICATE KEY UPDATE checkpoint_name=reporting_metric_capture_checkpoint.checkpoint_name
      """)
  int insertIfAbsent(String name, String source, String lane, String minimum, String actor, Instant now);

  @SpecialSql("锁定单来源通道，防止多实例并发推进")
  @Select("""
      SELECT checkpoint_name,source_code,lane,last_source_id,cycle_upper_id,cycle_started_at,last_completed_at,
        version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
      FROM reporting_metric_capture_checkpoint WHERE checkpoint_name=#{name} FOR UPDATE
      """)
  MetricCaptureCheckpointEntity lock(String name);

  @SpecialSql("同事务写入下一批游标及完整周期水位，版本不符则拒绝")
  @Update("""
      UPDATE reporting_metric_capture_checkpoint SET last_source_id=#{checkpoint.lastSourceId},cycle_upper_id=#{checkpoint.cycleUpperId},
        cycle_started_at=#{checkpoint.cycleStartedAt},last_completed_at=#{checkpoint.lastCompletedAt},version=version+1,
        update_id=#{actor},update_time=#{now}
      WHERE checkpoint_name=#{checkpoint.checkpointName} AND version=#{checkpoint.version}
      """)
  int advance(MetricCaptureCheckpointEntity checkpoint, String actor, Instant now);

  @SpecialSql("只读加载固定的14个指标采集检查点以计算各来源水位")
  @Select("""
      SELECT checkpoint_name,source_code,lane,last_source_id,cycle_upper_id,cycle_started_at,last_completed_at,
        version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time
      FROM reporting_metric_capture_checkpoint
      """)
  List<MetricCaptureCheckpointEntity> checkpoints();

  @SpecialSql("跨 talent 表以 INSERT IGNORE 幂等投影简历入库指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,source_channel_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('RESUME_INGESTED:',v.id),256),1,26),'RESUME_INGESTED','RESUME_VERSION',v.id,v.create_id,i.owner_user_id,i.owner_department_id,i.source_channel_id,1,v.create_time,UTC_TIMESTAMP(6)
          FROM talent_resume_version v JOIN talent_resume_intake i ON i.id=v.intake_id
         WHERE v.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  int captureResumeIngestedRange(List<String> ids);

  @SpecialSql("跨 talent 表以 INSERT IGNORE 幂等投影简历解析指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,source_channel_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('RESUME_PARSED:',s.id),256),1,26),'RESUME_PARSED','PARSE_SNAPSHOT',s.id,v.create_id,i.owner_user_id,i.owner_department_id,i.source_channel_id,1,s.create_time,UTC_TIMESTAMP(6)
          FROM talent_resume_parse_snapshot s JOIN talent_resume_version v ON v.id=s.resume_version_id JOIN talent_resume_intake i ON i.id=v.intake_id
         WHERE s.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  int captureResumeParsedRange(List<String> ids);

  @SpecialSql("以 INSERT SELECT/IGNORE 幂等投影申请创建指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('APPLICATION_CREATED:',a.id),256),1,26),'APPLICATION_CREATED','APPLICATION',a.id,a.create_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,a.create_time,UTC_TIMESTAMP(6)
          FROM recruitment_application a
         WHERE a.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  /** 采集应聘流程列表。 */
  int captureApplicationsRange(List<String> ids);

  @SpecialSql("连接申请和决策事实并幂等投影人工筛选指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('HUMAN_SCREENED:',d.id),256),1,26),'HUMAN_SCREENED','DECISION_RECORD',d.id,d.decided_by_user_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,d.decided_at,UTC_TIMESTAMP(6)
          FROM recruitment_decision_record d JOIN recruitment_application a ON a.id=d.application_id
         WHERE d.record_type='DECISION' AND d.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  int captureHumanScreenedRange(List<String> ids);

  @SpecialSql("连接投递快照和投递主表并幂等投影送客指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,client_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('SUBMISSION_SENT:',s.id),256),1,26),'SUBMISSION_SENT','SEND_SNAPSHOT',s.id,s.sent_by_user_id,c.owner_user_id,c.owner_department_id,c.jd_id,c.jd_version_id,c.client_id,1,s.sent_at,UTC_TIMESTAMP(6)
          FROM submission_send_snapshot s JOIN submission_client_submission c ON c.id=s.submission_id
         WHERE s.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  /** 采集客户推荐列表。 */
  int captureSubmissionsRange(List<String> ids);

  @SpecialSql("连接面试反馈当前版本并幂等投影通过指标")
  @Insert(
      "<script>INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)"
          + " SELECT SUBSTRING(SHA2(CONCAT('INTERVIEW_PASSED:',v.id),256),1,26),'INTERVIEW_PASSED',CONCAT('INTERVIEW_',i.interview_type),v.id,v.create_id,i.owner_user_id,i.owner_department_id,i.jd_id,i.jd_version_id,1,v.feedback_occurred_at,UTC_TIMESTAMP(6)"
          + " FROM interview_feedback_version v JOIN interview_feedback f ON f.id=v.feedback_id AND f.status='"
          + MetricSourceRecordStatus.Codes.SUBMITTED
          + "' AND f.current_version_no=v.version_no JOIN interview_interview i ON i.id=f.interview_id"
          + " WHERE v.verdict='PASS' AND v.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
  int captureInterviewPassedRange(List<String> ids);

  @SpecialSql("连接入职事件和申请并幂等投影入职指标")
  @Insert(
      """
      <script>
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('ONBOARDED:',o.id),256),1,26),'ONBOARDED','ONBOARDING_EVENT',o.id,o.operated_by_user_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,o.onboarded_at,UTC_TIMESTAMP(6)
          FROM recruitment_onboarding_event o JOIN recruitment_application a ON a.id=o.application_id
         WHERE o.id IN <foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach>\
      </script>
      """)
  /** 采集入职。 */
  int captureOnboardedRange(List<String> ids);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM talent_resume_version")
  String upperResumeIngestedId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM talent_resume_version WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextResumeIngestedIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM talent_resume_parse_snapshot")
  String upperResumeParsedId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM talent_resume_parse_snapshot WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextResumeParsedIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM recruitment_application")
  String upperApplicationsId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM recruitment_application WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextApplicationsIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM recruitment_decision_record")
  String upperHumanScreenedId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM recruitment_decision_record WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextHumanScreenedIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM submission_send_snapshot")
  String upperSubmissionsId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM submission_send_snapshot WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextSubmissionsIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM interview_feedback_version")
  String upperInterviewPassedId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM interview_feedback_version WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextInterviewPassedIds(String afterId, String upperId, int limit);

  @SpecialSql("按来源主键捕获本周期固定上界")
  @Select("SELECT MAX(id) FROM recruitment_onboarding_event")
  String upperOnboardedId();

  @SpecialSql("按来源主键限批扫描，包含尚未满足投影条件的来源以便补偿")
  @Select("SELECT id FROM recruitment_onboarding_event WHERE id > #{afterId} AND id <= #{upperId} ORDER BY id LIMIT #{limit}")
  List<String> nextOnboardedIds(String afterId, String upperId, int limit);

}

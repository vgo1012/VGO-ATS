package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.reporting.domain.dto.MetricAggregateDTO;
import com.ezaigc.hr.reporting.domain.dto.MetricDayDTO;
import com.ezaigc.hr.reporting.domain.dto.ParseCoverageDTO;
import com.ezaigc.hr.reporting.domain.entity.MetricDefinitionEntity;
import com.ezaigc.hr.reporting.domain.entity.MetricEventEntity;
import com.ezaigc.hr.reporting.enums.MetricDefinitionStatus;
import com.ezaigc.hr.reporting.enums.MetricSourceRecordStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MetricMapper extends BaseMapper<MetricEventEntity>, RecruitmentAnalyticsMapper {
  @SpecialSql("跨 talent 表以 INSERT IGNORE 幂等投影简历入库指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,source_channel_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('RESUME_INGESTED:',v.id),256),1,26),'RESUME_INGESTED','RESUME_VERSION',v.id,v.create_id,i.owner_user_id,i.owner_department_id,i.source_channel_id,1,v.create_time,UTC_TIMESTAMP(6)
          FROM talent_resume_version v JOIN talent_resume_intake i ON i.id=v.intake_id
         WHERE v.create_time >= #{from} AND v.create_time < #{to}\
      """)
  int captureResumeIngested(Instant from, Instant to);

  @SpecialSql("跨 talent 表以 INSERT IGNORE 幂等投影简历解析指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,source_channel_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('RESUME_PARSED:',s.id),256),1,26),'RESUME_PARSED','PARSE_SNAPSHOT',s.id,v.create_id,i.owner_user_id,i.owner_department_id,i.source_channel_id,1,s.create_time,UTC_TIMESTAMP(6)
          FROM talent_resume_parse_snapshot s JOIN talent_resume_version v ON v.id=s.resume_version_id JOIN talent_resume_intake i ON i.id=v.intake_id
         WHERE s.create_time >= #{from} AND s.create_time < #{to}\
      """)
  int captureResumeParsed(Instant from, Instant to);

  @SpecialSql("以 INSERT SELECT/IGNORE 幂等投影申请创建指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('APPLICATION_CREATED:',a.id),256),1,26),'APPLICATION_CREATED','APPLICATION',a.id,a.create_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,a.create_time,UTC_TIMESTAMP(6)
          FROM recruitment_application a
         WHERE a.create_time >= #{from} AND a.create_time < #{to}\
      """)
  /** 采集应聘流程列表。 */
  int captureApplications(Instant from, Instant to);

  @SpecialSql("连接申请和决策事实并幂等投影人工筛选指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('HUMAN_SCREENED:',d.id),256),1,26),'HUMAN_SCREENED','DECISION_RECORD',d.id,d.decided_by_user_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,d.decided_at,UTC_TIMESTAMP(6)
          FROM recruitment_decision_record d JOIN recruitment_application a ON a.id=d.application_id
         WHERE d.record_type='DECISION' AND d.decided_at >= #{from} AND d.decided_at < #{to}\
      """)
  int captureHumanScreened(Instant from, Instant to);

  @SpecialSql("连接投递快照和投递主表并幂等投影送客指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,client_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('SUBMISSION_SENT:',s.id),256),1,26),'SUBMISSION_SENT','SEND_SNAPSHOT',s.id,s.sent_by_user_id,c.owner_user_id,c.owner_department_id,c.jd_id,c.jd_version_id,c.client_id,1,s.sent_at,UTC_TIMESTAMP(6)
          FROM submission_send_snapshot s JOIN submission_client_submission c ON c.id=s.submission_id
         WHERE s.sent_at >= #{from} AND s.sent_at < #{to}\
      """)
  /** 采集客户推荐列表。 */
  int captureSubmissions(Instant from, Instant to);

  @SpecialSql("连接面试反馈当前版本并幂等投影通过指标")
  @Insert(
      "INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)"
          + " SELECT SUBSTRING(SHA2(CONCAT('INTERVIEW_PASSED:',v.id),256),1,26),'INTERVIEW_PASSED',CONCAT('INTERVIEW_',i.interview_type),v.id,v.create_id,i.owner_user_id,i.owner_department_id,i.jd_id,i.jd_version_id,1,v.feedback_occurred_at,UTC_TIMESTAMP(6)"
          + " FROM interview_feedback_version v JOIN interview_feedback f ON f.id=v.feedback_id AND f.status='"
          + MetricSourceRecordStatus.Codes.SUBMITTED
          + "' AND f.current_version_no=v.version_no JOIN interview_interview i ON i.id=f.interview_id"
          + " WHERE v.verdict='PASS' AND v.feedback_occurred_at >= #{from} AND v.feedback_occurred_at < #{to}")
  int captureInterviewPassed(Instant from, Instant to);

  @SpecialSql("连接入职事件和申请并幂等投影入职指标")
  @Insert(
      """
      INSERT IGNORE INTO reporting_metric_event(id,event_type,source_type,source_id,actor_user_id,owner_user_id,department_id,jd_id,jd_version_id,value_decimal,occurred_at,captured_at)
        SELECT SUBSTRING(SHA2(CONCAT('ONBOARDED:',o.id),256),1,26),'ONBOARDED','ONBOARDING_EVENT',o.id,o.operated_by_user_id,a.owner_user_id,a.owner_department_id,a.jd_id,a.jd_version_id,1,o.onboarded_at,UTC_TIMESTAMP(6)
          FROM recruitment_onboarding_event o JOIN recruitment_application a ON a.id=o.application_id
         WHERE o.onboarded_at >= #{from} AND o.onboarded_at < #{to}\
      """)
  /** 采集入职。 */
  int captureOnboarded(Instant from, Instant to);

  @SpecialSql("连接指标定义及其当前版本生成只读定义视图")
  @Select(
      "SELECT d.code,d.name,d.category,v.version_no,v.definition_text,v.event_type,v.effective_from"
          + " FROM reporting_metric_definition d JOIN reporting_metric_version v ON"
          + " v.metric_code=d.code AND v.version_no=d.current_version_no WHERE d.status='"
          + MetricDefinitionStatus.Codes.ACTIVE
          + "'"
          + " ORDER BY d.category,d.code")
  List<MetricDefinitionEntity> definitions();

  @SpecialSql("按动态数据权限和筛选条件执行分组聚合")
  @Select(
      """
      <script>
      SELECT e.event_type,COUNT(*) event_count,COALESCE(SUM(e.value_decimal),0) event_value,MIN(e.occurred_at) first_occurred_at,MAX(e.occurred_at) last_occurred_at
        FROM reporting_metric_event e
       WHERE e.occurred_at &gt;= #{from} AND e.occurred_at &lt; #{to}
         <if test='jdId != null'>AND e.jd_id=#{jdId}</if>
         <if test='jdVersionId != null'>AND e.jd_version_id=#{jdVersionId}</if>
         <if test='ownerUserId != null'>AND e.owner_user_id=#{ownerUserId}</if>
         AND (#{allScope}=TRUE OR e.owner_user_id=#{actorId}
           <if test='managedUserIds != null and managedUserIds.size() &gt; 0'>OR e.owner_user_id IN <foreach collection='managedUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>
           <if test='departmentIds != null and departmentIds.size() &gt; 0'>OR e.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>)
       GROUP BY e.event_type ORDER BY e.event_type
      </script>
      """)
  List<MetricAggregateDTO> aggregate(
      Instant from,
      Instant to,
      String jdId,
      String jdVersionId,
      String ownerUserId,
      boolean allScope,
      String actorId,
      List<String> managedUserIds,
      List<String> departmentIds);

  @SpecialSql("按入库事件的日期及数据范围统计简历版本解析覆盖数，存在多个解析快照也只计一次")
  @Select("""
      <script>
      SELECT COUNT(DISTINCT e.source_id) resume_version_count,
        COUNT(DISTINCT CASE WHEN EXISTS (SELECT 1 FROM talent_resume_parse_snapshot s
          WHERE s.resume_version_id=e.source_id) THEN e.source_id END) parsed_resume_version_count
        FROM reporting_metric_event e
       WHERE e.event_type='RESUME_INGESTED'
         AND e.occurred_at &gt;= #{from} AND e.occurred_at &lt; #{to}
         <if test='jdId != null'>AND e.jd_id=#{jdId}</if>
         <if test='jdVersionId != null'>AND e.jd_version_id=#{jdVersionId}</if>
         <if test='ownerUserId != null'>AND e.owner_user_id=#{ownerUserId}</if>
         AND (#{allScope}=TRUE OR e.owner_user_id=#{actorId}
           <if test='managedUserIds != null and managedUserIds.size() &gt; 0'>OR e.owner_user_id IN <foreach collection='managedUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>
           <if test='departmentIds != null and departmentIds.size() &gt; 0'>OR e.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>)
      </script>
      """)
  ParseCoverageDTO parseCoverage(
      Instant from, Instant to, String jdId, String jdVersionId, String ownerUserId,
      boolean allScope, String actorId, List<String> managedUserIds, List<String> departmentIds);
  // JDBC 连接使用 Asia/Shanghai，DATETIME 与 Instant 参数已按同一时区映射，不能再次加八小时。
  @SpecialSql("按北京时间自然日及相同数据范围聚合完整历史事件")
  @Select(
      """
      <script>
      SELECT DATE(e.occurred_at) date, e.event_type, COUNT(*) event_count
        FROM reporting_metric_event e
       WHERE e.occurred_at &gt;= #{from} AND e.occurred_at &lt; #{to}
         <if test='jdId != null'>AND e.jd_id=#{jdId}</if>
         <if test='jdVersionId != null'>AND e.jd_version_id=#{jdVersionId}</if>
         <if test='ownerUserId != null'>AND e.owner_user_id=#{ownerUserId}</if>
         AND (#{allScope}=TRUE OR e.owner_user_id=#{actorId}
           <if test='managedUserIds != null and managedUserIds.size() &gt; 0'>OR e.owner_user_id IN <foreach collection='managedUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>
           <if test='departmentIds != null and departmentIds.size() &gt; 0'>OR e.department_id IN <foreach collection='departmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>)
       GROUP BY DATE(e.occurred_at), e.event_type ORDER BY date, e.event_type
      </script>
      """)
  List<MetricDayDTO> daily(
      Instant from,
      Instant to,
      String jdId,
      String jdVersionId,
      String ownerUserId,
      boolean allScope,
      String actorId,
      List<String> managedUserIds,
      List<String> departmentIds);
}

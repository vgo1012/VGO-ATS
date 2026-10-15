package com.ezaigc.hr.recruiting.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SubmissionMapper extends BaseMapper<ClientSubmission>, SubmissionSqlOperations {
  @SpecialSql("锁定推荐记录以串行化发送、反馈和撤回状态变更")
  @Select(
      "SELECT"
          + " id,application_id,candidate_id,demand_id,client_id,project_id,position_id,jd_id,jd_version_id,resume_version_id,delivery_status,process_status,sent_at,withdrawn_at,withdrawn_by_user_id,withdrawal_reason,owner_user_id,owner_department_id,version,ext,remark,create_id,create_name,create_time,update_id,update_name,update_time"
          + " FROM submission_client_submission WHERE id=#{id} FOR UPDATE")
  ClientSubmission lock(String id);

  @SpecialSql("仅在不存在当前投影时创建，避免覆盖并发新增限制")
  @Insert("INSERT IGNORE INTO submission_client_restriction_current(client_id,candidate_id,record_id,active,expires_at,create_id,create_time,update_id,update_time)"
      + " VALUES(#{clientId},#{candidateId},#{recordId},#{active},#{expiresAt},#{actorId},#{now},#{actorId},#{now})")
  int insertRestrictionProjection(String clientId, String candidateId, String recordId,
      boolean active, Instant expiresAt, String actorId, Instant now);

  @SpecialSql("使用数据库原子 upsert 维护客户候选人限制的当前投影")
  @Insert(
      "INSERT INTO"
          + " submission_client_restriction_current(client_id,candidate_id,record_id,active,expires_at,create_id,create_time,update_id,update_time)"
          + " VALUES(#{clientId},#{candidateId},#{recordId},#{active},#{expiresAt},#{actorId},#{now},#{actorId},#{now})"
          + " ON DUPLICATE KEY UPDATE"
          + " record_id=VALUES(record_id),active=VALUES(active),expires_at=VALUES(expires_at),update_id=VALUES(update_id),update_time=VALUES(update_time)")
  int projectRestriction(
      String clientId,
      String candidateId,
      String recordId,
      boolean active,
      Instant expiresAt,
      String actorId,
      Instant now);

  @SpecialSql("连接当前限制投影与不可变限制事实读取当前记录")
  @Select(
      "SELECT"
          + " r.id,r.client_id,r.candidate_id,r.source_submission_id,r.source_application_id,r.evidence_reference,r.record_type,r.status,r.restriction_intent,r.expires_at,r.reason_detail,r.source_channel,r.source_occurred_at,r.corrects_record_id,r.recorded_by_user_id,r.version,r.ext,r.remark,r.create_id,r.create_name,r.create_time,r.update_id,r.update_name,r.update_time"
          + " FROM submission_client_restriction_current c JOIN submission_client_restriction r ON"
          + " r.id=c.record_id WHERE c.client_id=#{clientId} AND c.candidate_id=#{candidateId}")
  ClientRestriction currentRestriction(String clientId, String candidateId);

  @SpecialSql("按原事实 ID 条件更新客户限制投影，拒绝旧版本更正或停用覆盖并发新事实")
  @Update("UPDATE submission_client_restriction_current"
      + " SET record_id=#{recordId},active=#{active},expires_at=#{expiresAt},update_id=#{actorId},update_time=#{now}"
      + " WHERE client_id=#{clientId} AND candidate_id=#{candidateId} AND record_id=#{expectedRecordId}")
  int replaceRestrictionProjection(
      String clientId,
      String candidateId,
      String expectedRecordId,
      String recordId,
      boolean active,
      Instant expiresAt,
      String actorId,
      Instant now);

  @SpecialSql("使用 INSERT IGNORE 原子保留幂等键并处理并发重放")
  @Insert(
      "INSERT IGNORE INTO"
          + " submission_idempotency_record(actor_user_id,idempotency_key,action_code,request_fingerprint,target_id,create_time)"
          + " VALUES(#{actorId},#{key},#{action},#{fingerprint},#{targetId},#{now})")
  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}

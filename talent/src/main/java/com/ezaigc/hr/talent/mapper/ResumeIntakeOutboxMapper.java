package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.enums.ResumeIntakeOutboxStatus;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeIntakeOutboxMapper extends BaseMapper<ResumeIntakeOutboxEvent> {
  String SELECT_COLUMNS = "id,processing_id,processing_version,event_type,event_version,task_type,"
      + "occurred_at,trace_id,actor_user_id,status,attempt_count,max_attempts,next_retry_at,"
      + "lease_owner,lease_expires_at,published_at,failure_code,version,ext,remark,create_id,"
      + "create_name,create_time,update_id,update_name,update_time";

  @SpecialSql("汇总收件领域通知当前已到期且可领取的持久发布积压")
  @Select("SELECT COUNT(*) AS ready_count,MIN(CASE WHEN status='"
      + ResumeIntakeOutboxStatus.Codes.FAILED_RETRYABLE
      + "' THEN COALESCE(next_retry_at,occurred_at) WHEN status='"
      + ResumeIntakeOutboxStatus.Codes.RUNNING
      + "' THEN lease_expires_at"
      + " ELSE occurred_at END) AS oldest_ready_at FROM talent_resume_intake_outbox"
      + " WHERE attempt_count<max_attempts AND (status='"
      + ResumeIntakeOutboxStatus.Codes.PENDING
      + "' OR (status='"
      + ResumeIntakeOutboxStatus.Codes.FAILED_RETRYABLE
      + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now})) OR (status='"
      + ResumeIntakeOutboxStatus.Codes.RUNNING
      + "'"
      + " AND lease_expires_at<=#{now}))")
  PersistentTaskBacklogSnapshot findReadyBacklog(Instant now);

  @SpecialSql("同一处理版本和唤醒类型只保存一份不可变领域通知")
  @Insert("INSERT IGNORE INTO talent_resume_intake_outbox(id,processing_id,processing_version,event_type,"
      + "event_version,task_type,occurred_at,trace_id,actor_user_id,status,attempt_count,max_attempts,"
      + "create_id,create_time,update_id,update_time,version) VALUES(#{id},#{processingId},#{processingVersion},"
      + "#{eventType},#{eventVersion},#{taskType},#{occurredAt},#{traceId},#{actorUserId},#{status},"
      + "#{attemptCount},#{maxAttempts},#{createId},#{createTime},#{updateId},#{updateTime},#{version})")
  int insertIgnore(ResumeIntakeOutboxEvent event);

  @SpecialSql("唯一业务版本竞争后读取实际保存的不可变信封")
  @Select("SELECT " + SELECT_COLUMNS + " FROM talent_resume_intake_outbox WHERE processing_id=#{processingId}"
      + " AND processing_version=#{processingVersion} AND task_type=#{taskType} FOR UPDATE")
  ResumeIntakeOutboxEvent findByBinding(String processingId, long processingVersion, String taskType);

  @SpecialSql("发布 Worker 以跳锁方式逐条领取到期的领域通知")
  @Select("SELECT " + SELECT_COLUMNS + " FROM talent_resume_intake_outbox WHERE attempt_count<max_attempts"
      + " AND (status='" + ResumeIntakeOutboxStatus.Codes.PENDING
      + "' OR (status='" + ResumeIntakeOutboxStatus.Codes.FAILED_RETRYABLE
      + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now}))"
      + " OR (status='" + ResumeIntakeOutboxStatus.Codes.RUNNING
      + "' AND lease_expires_at<=#{now})) ORDER BY occurred_at,id LIMIT 1"
      + " FOR UPDATE SKIP LOCKED")
  ResumeIntakeOutboxEvent findClaimable(Instant now);

  @SpecialSql("乐观版本和状态共同保护领域通知发布租约")
  @Update("UPDATE talent_resume_intake_outbox SET status='" + ResumeIntakeOutboxStatus.Codes.RUNNING
      + "',attempt_count=attempt_count+1,"
      + "next_retry_at=NULL,lease_owner=#{leaseOwner},lease_expires_at=#{leaseExpiresAt},failure_code=NULL,"
      + "version=version+1,update_id=#{leaseOwner},update_time=#{now} WHERE id=#{id} AND version=#{version}"
      + " AND attempt_count<max_attempts AND (status='" + ResumeIntakeOutboxStatus.Codes.PENDING
      + "' OR (status='" + ResumeIntakeOutboxStatus.Codes.FAILED_RETRYABLE
      + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now})) OR (status='"
      + ResumeIntakeOutboxStatus.Codes.RUNNING + "'"
      + " AND lease_expires_at<=#{now}))")
  int claim(String id, int version, String leaseOwner, Instant leaseExpiresAt, Instant now);

  @SpecialSql("只有当前未过期发布租约可以确认领域通知")
  @Update("UPDATE talent_resume_intake_outbox SET status='" + ResumeIntakeOutboxStatus.Codes.PUBLISHED
      + "',published_at=#{now},lease_owner=NULL,"
      + "lease_expires_at=NULL,failure_code=NULL,version=version+1,update_id=#{leaseOwner},update_time=#{now}"
      + " WHERE id=#{id} AND status='" + ResumeIntakeOutboxStatus.Codes.RUNNING
      + "' AND lease_owner=#{leaseOwner} AND lease_expires_at>#{now}")
  int markPublished(String id, String leaseOwner, Instant now);

  @SpecialSql("发布失败按剩余尝试次数收敛为退避重试或最终失败")
  @Update("UPDATE talent_resume_intake_outbox SET status=CASE WHEN #{retryable}=TRUE"
      + " AND attempt_count<max_attempts THEN '" + ResumeIntakeOutboxStatus.Codes.FAILED_RETRYABLE
      + "' ELSE '" + ResumeIntakeOutboxStatus.Codes.FAILED_FINAL + "' END,"
      + "next_retry_at=CASE WHEN #{retryable}=TRUE AND attempt_count<max_attempts THEN #{nextRetryAt}"
      + " ELSE NULL END,failure_code=#{failureCode},lease_owner=NULL,lease_expires_at=NULL,version=version+1,"
      + "update_id=#{leaseOwner},update_time=#{now} WHERE id=#{id} AND status='"
      + ResumeIntakeOutboxStatus.Codes.RUNNING + "'"
      + " AND lease_owner=#{leaseOwner} AND lease_expires_at>#{now}")
  int markFailed(String id, String leaseOwner, String failureCode, boolean retryable,
      Instant nextRetryAt, Instant now);
}

package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeIntakeProcessingMapper extends BaseMapper<ResumeIntakeProcessing> {
  @SpecialSql("汇总收件解析状态机当前已到期且可领取的持久积压")
  @Select("SELECT COUNT(*) AS ready_count,MIN(CASE WHEN status='"
      + ResumeIntakeProcessingStatus.Codes.FAILED_RETRYABLE
      + "' THEN COALESCE(next_retry_at,create_time) WHEN status='"
      + ResumeIntakeProcessingStatus.Codes.RUNNING
      + "' THEN lease_expires_at"
      + " ELSE create_time END) AS oldest_ready_at FROM talent_resume_intake_processing"
      + " WHERE attempt_count<max_attempts AND (status='"
      + ResumeIntakeProcessingStatus.Codes.QUEUED
      + "' OR (status='"
      + ResumeIntakeProcessingStatus.Codes.FAILED_RETRYABLE
      + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now})) OR (status='"
      + ResumeIntakeProcessingStatus.Codes.RUNNING
      + "'"
      + " AND lease_expires_at<=#{now}))")
  PersistentTaskBacklogSnapshot findReadyBacklog(Instant now);

  @SpecialSql("优先按版本来源收件读取独立解析，历史无关联版本回退到共享解析")
  @Select("SELECT COALESCE(i.processing_id,p.id) FROM talent_resume_version v"
      + " LEFT JOIN talent_resume_intake i ON i.id=v.intake_id"
      + " LEFT JOIN talent_resume_intake_processing p ON p.content_sha256=v.content_sha256"
      + " AND p.processing_scope_key='00000000000000000000000000'"
      + " WHERE v.id=#{resumeVersionId}")
  String findProcessingIdByResumeVersion(String resumeVersionId);
  @SpecialSql("文件哈希唯一约束裁决跨批并发上传，保留已有处理结果")
  @Insert("INSERT IGNORE INTO talent_resume_intake_processing(id,content_sha256,original_file_name,media_type,object_key,status,attempt_count,max_attempts,create_id,create_time,update_id,update_time) VALUES(#{id},#{hash},#{fileName},#{mediaType},#{objectKey},'" + ResumeIntakeProcessingStatus.Codes.QUEUED + "',0,3,#{actorId},#{now},#{actorId},#{now})")
  int insertProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now);

  @SpecialSql("重新解析保留原文件哈希，使用处理 ID 隔离本次收件的结果")
  @Insert("INSERT INTO talent_resume_intake_processing(id,content_sha256,processing_scope_key,original_file_name,media_type,object_key,status,attempt_count,max_attempts,create_id,create_time,update_id,update_time) VALUES(#{id},#{hash},#{id},#{fileName},#{mediaType},#{objectKey},'" + ResumeIntakeProcessingStatus.Codes.QUEUED + "',0,3,#{actorId},#{now},#{actorId},#{now})")
  int insertReparseProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now);

  @SpecialSql("乐观版本与可执行状态原子领取共享解析，每次领取使用不同租约标识")
  @Update("UPDATE talent_resume_intake_processing SET status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "',attempt_count=attempt_count+1,lease_owner=#{worker},lease_expires_at=#{leaseUntil},failure_code=NULL,version=version+1,update_id=#{worker},update_time=#{now} WHERE id=#{id} AND version=#{version} AND attempt_count<max_attempts AND (status='" + ResumeIntakeProcessingStatus.Codes.QUEUED + "' OR (status='" + ResumeIntakeProcessingStatus.Codes.FAILED_RETRYABLE + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now})) OR (status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "' AND lease_expires_at<=#{now}))")
  int claim(String id, long version, String worker, Instant leaseUntil, Instant now);

  @SpecialSql("仅续约仍有效的本次租约，旧持有者不能复活已过期任务")
  @Update("UPDATE talent_resume_intake_processing SET lease_expires_at=#{leaseUntil} WHERE id=#{id} AND status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "' AND lease_owner=#{worker} AND lease_expires_at>#{now}")
  int renewLease(String id, String worker, Instant leaseUntil, Instant now);

  @SpecialSql("解析结果与成功终态一起写入，租约失效后拒绝迟到结果")
  @Update("UPDATE talent_resume_intake_processing SET status='" + ResumeIntakeProcessingStatus.Codes.SUCCEEDED + "',extracted_pages_json=#{pagesJson},parsed_facts_json=#{factsJson},model_provider=#{provider},model_version=#{modelVersion},raw_output_object_key=#{archiveKey},raw_output_hash=#{archiveHash},raw_output_size=#{archiveSize},encryption_version=#{encryptionVersion},failure_code=NULL,next_retry_at=NULL,lease_owner=NULL,lease_expires_at=NULL,version=version+1,update_id=#{worker},update_time=#{now} WHERE id=#{id} AND status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "' AND lease_owner=#{worker} AND lease_expires_at>#{now}")
  int succeed(String id, String worker, String pagesJson, String factsJson, String provider,
      String modelVersion, String archiveKey, String archiveHash, long archiveSize,
      String encryptionVersion, Instant now);

  @SpecialSql("失败时按已尝试次数原子确定重试或耗尽终态，拒绝过期租约")
  @Update("UPDATE talent_resume_intake_processing SET status=CASE WHEN attempt_count<max_attempts AND #{retryable}=TRUE THEN '" + ResumeIntakeProcessingStatus.Codes.FAILED_RETRYABLE + "' ELSE '" + ResumeIntakeProcessingStatus.Codes.FAILED_FINAL + "' END,failure_code=#{failureCode},next_retry_at=CASE WHEN attempt_count<max_attempts AND #{retryable}=TRUE THEN #{nextRetryAt} ELSE NULL END,lease_owner=NULL,lease_expires_at=NULL,version=version+1,update_id=#{worker},update_time=#{now} WHERE id=#{id} AND status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "' AND lease_owner=#{worker} AND lease_expires_at>#{now}")
  int fail(String id, String worker, String failureCode, boolean retryable,
      Instant nextRetryAt, Instant now);

  @SpecialSql("人工重试增加三次尝试窗口，保留累计次数和历史而不把耗尽任务困在队列")
  @Update("UPDATE talent_resume_intake_processing SET status='" + ResumeIntakeProcessingStatus.Codes.QUEUED + "',max_attempts=attempt_count+3,next_retry_at=NULL,failure_code=NULL,version=version+1,update_id=#{actorId},update_time=#{now} WHERE id=#{id} AND status IN ('" + ResumeIntakeProcessingStatus.Codes.FAILED_RETRYABLE + "','" + ResumeIntakeProcessingStatus.Codes.FAILED_FINAL + "')")
  int retry(String id, String actorId, Instant now);

  @SpecialSql("有界收敛已耗尽的崩溃任务，避免永久占据待领取批次")
  @Update("UPDATE talent_resume_intake_processing SET status='" + ResumeIntakeProcessingStatus.Codes.FAILED_FINAL + "',failure_code='INTAKE_PARSE_ATTEMPTS_EXHAUSTED',lease_owner=NULL,lease_expires_at=NULL,version=version+1,update_time=#{now} WHERE status='" + ResumeIntakeProcessingStatus.Codes.RUNNING + "' AND lease_expires_at<=#{now} AND attempt_count>=max_attempts ORDER BY create_time,id LIMIT #{limit}")
  int expireExhausted(Instant now, int limit);
}

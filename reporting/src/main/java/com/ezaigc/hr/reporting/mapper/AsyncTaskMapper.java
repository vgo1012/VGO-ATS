package com.ezaigc.hr.reporting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AsyncTaskMapper extends BaseMapper<AsyncTaskEntity> {

  @SpecialSql("按任务类型汇总当前已到期且可领取的持久任务积压")
  @Select("<script>SELECT COUNT(*) AS ready_count,"
      + "MIN(CASE WHEN next_retry_at IS NULL THEN create_time ELSE next_retry_at END)"
      + " AS oldest_ready_at FROM reporting_async_task WHERE status='"
      + AsyncTaskStatus.Codes.QUEUED
      + "' AND attempt_count&lt;max_attempts"
      + " AND (next_retry_at IS NULL OR next_retry_at&lt;=#{now}) AND task_type IN"
      + " <foreach collection='taskTypes' item='taskType' open='(' separator=',' close=')'>"
      + "#{taskType}</foreach></script>")
  PersistentTaskBacklogSnapshot findReadyBacklog(
      @Param("taskTypes") List<String> taskTypes, @Param("now") Instant now);

  @SpecialSql("人工恢复锁定原失败任务，串行化同一任务的恢复创建，不修改失败记录")
  @Select("SELECT id FROM reporting_async_task WHERE id=#{id} FOR UPDATE")
  String lockTask(String id);

  @SpecialSql("依赖唯一键和 INSERT IGNORE 原子实现任务幂等创建")
  @Insert(
      """
      INSERT IGNORE INTO reporting_async_task(
        id,trace_id,task_type,resource_type,resource_id,requested_by_user_id,owner_user_id,
        owner_department_id,view_permission,manage_permission,status,attempt_count,max_attempts,
        progress_percent,retryable,idempotency_key,request_fingerprint,version,
        create_id,create_time,update_id,update_time)
      VALUES(#{id},#{traceId},#{taskType},#{resourceType},#{resourceId},#{requestedByUserId},#{ownerUserId},
        #{ownerDepartmentId},#{viewPermission},#{managePermission},#{status},#{attemptCount},#{maxAttempts},
        #{progressPercent},#{retryable},#{idempotencyKey},#{requestFingerprint},#{version},
        #{createId},#{createTime},#{updateId},#{updateTime})
      """)
  /** 新增忽略。 */
  int insertIgnore(AsyncTaskEntity task);

  @SpecialSql("通过 FOR UPDATE SKIP LOCKED 并发领取队列任务")
  @Select(
      "SELECT id FROM reporting_async_task WHERE task_type=#{taskType} AND status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "' AND (next_retry_at IS NULL OR next_retry_at<=#{now}) ORDER BY"
          + " next_retry_at,create_time,id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
  List<String> findQueuedIdsForUpdate(String taskType, Instant now, int limit);

  @SpecialSql("在失败事务内按尝试号和版本安排延迟重试，防止覆盖并发人工重试或新尝试")
  @Update("UPDATE reporting_async_task SET status='" + AsyncTaskStatus.Codes.QUEUED
      + "',progress_percent=0,retryable=FALSE,result_resource_id=NULL,started_at=NULL,lease_until=NULL,"
      + "finished_at=NULL,next_retry_at=#{retryAt},version=version+1,update_id=#{actorId},update_time=#{now}"
      + " WHERE id=#{id} AND status='" + AsyncTaskStatus.Codes.FAILED
      + "' AND retryable=TRUE AND attempt_count=#{attemptNo} AND attempt_count<max_attempts AND version=#{version}")
  int scheduleRetry(String id, int attemptNo, long version, Instant retryAt, Instant now, String actorId);

  @SpecialSql("锁定租约已过期或旧版本无租约的在途任务，供崩溃恢复")
  @Select(
      "SELECT id,task_type,resource_id,status,attempt_count,max_attempts FROM reporting_async_task"
          + " WHERE task_type=#{taskType} AND status IN ('"
          + AsyncTaskStatus.Codes.RUNNING
          + "','"
          + AsyncTaskStatus.Codes.CANCELLING
          + "') AND (lease_until IS NULL OR lease_until<=#{now})"
          + " ORDER BY lease_until,id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
  List<AsyncTaskLeaseRow> findExpiredLeasesForUpdate(String taskType, Instant now, int limit);

  /** 更新指定业务记录。 */
  @SpecialSql("原子校验 QUEUED 前置状态并进入 RUNNING")
  @Update(
      "UPDATE reporting_async_task SET"
          + " status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "',attempt_count=attempt_count+1,progress_percent=1,error_code=NULL,safe_error_detail=NULL,retryable=FALSE,started_at=#{now},lease_until=#{leaseUntil},next_retry_at=NULL,finished_at=NULL,version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "' AND attempt_count<max_attempts AND (next_retry_at IS NULL OR next_retry_at<=#{now})")
  int markRunning(String id, Instant now, Instant leaseUntil, String actorId);

  @SpecialSql("验证状态、尝试号和未过期租约，阻止旧执行批次继续写入")
  @Select(
      "SELECT COUNT(*) FROM reporting_async_task WHERE id=#{id} AND attempt_count=#{attemptNo}"
          + " AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND lease_until>#{now}")
  int countActiveLease(String id, int attemptNo, Instant now);

  @SpecialSql("仅允许当前尝试在租约到期前续租")
  @Update(
      "UPDATE reporting_async_task SET lease_until=#{leaseUntil},version=version+1,"
          + "update_id=#{actorId},update_time=#{now} WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND attempt_count=#{attemptNo} AND lease_until>#{now}")
  int renewLease(String id, int attemptNo, Instant now, Instant leaseUntil, String actorId);

  /** 更新指定业务记录。 */
  @SpecialSql("原子保证 RUNNING 状态和进度单调递增")
  @Update(
      "UPDATE reporting_async_task SET"
          + " progress_percent=#{progress},lease_until=#{leaseUntil},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND attempt_count=#{attemptNo} AND lease_until>#{now}"
          + " AND #{progress} BETWEEN progress_percent AND 99")
  /** 更新进度。 */
  int updateProgress(
      String id,
      int attemptNo,
      int progress,
      Instant now,
      Instant leaseUntil,
      String actorId);

  /** 更新指定业务记录。 */
  @SpecialSql("原子校验 RUNNING 状态并完成任务")
  @Update(
      "UPDATE reporting_async_task SET"
          + " status='"
          + AsyncTaskStatus.Codes.SUCCEEDED
          + "',progress_percent=100,result_resource_id=#{resultId},retryable=FALSE,lease_until=NULL,next_retry_at=NULL,finished_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND attempt_count=#{attemptNo} AND lease_until>#{now}")
  /** 标记成功。 */
  int markSucceeded(
      String id, int attemptNo, String resultId, Instant now, String actorId);

  /** 更新指定业务记录。 */
  @SpecialSql("原子校验取消中状态并完成取消")
  @Update(
      "UPDATE reporting_async_task SET"
          + " status='"
          + AsyncTaskStatus.Codes.CANCELLED
          + "',progress_percent=100,retryable=FALSE,lease_until=NULL,next_retry_at=NULL,finished_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status IN ('"
          + AsyncTaskStatus.Codes.RUNNING
          + "','"
          + AsyncTaskStatus.Codes.CANCELLING
          + "') AND attempt_count=#{attemptNo} AND lease_until>#{now}")
  /** 标记已取消。 */
  int markCancelled(String id, int attemptNo, Instant now, String actorId);

  @SpecialSql("租约过期且仍为同一尝试时重新排队，旧执行者由尝试号栅栏隔离")
  @Update(
      "UPDATE reporting_async_task SET status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "',progress_percent=0,error_code=NULL,safe_error_detail=NULL,retryable=FALSE,result_resource_id=NULL,started_at=NULL,lease_until=NULL,next_retry_at=NULL,finished_at=NULL,version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND attempt_count=#{attemptNo} AND (lease_until IS NULL OR lease_until<=#{now})"
          + " AND attempt_count<max_attempts")
  int requeueExpiredLease(String id, int attemptNo, Instant now, String actorId);

  @SpecialSql("租约过期且尝试次数耗尽时原子收敛为最终失败")
  @Update(
      "UPDATE reporting_async_task SET status='"
          + AsyncTaskStatus.Codes.FAILED
          + "',error_code=#{code},safe_error_detail=#{detail},retryable=FALSE,lease_until=NULL,next_retry_at=NULL,finished_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.RUNNING
          + "' AND attempt_count=#{attemptNo} AND (lease_until IS NULL OR lease_until<=#{now})"
          + " AND attempt_count>=max_attempts")
  int failExpiredLease(
      String id, int attemptNo, String code, String detail, Instant now, String actorId);

  @SpecialSql("取消中的任务租约过期后原子完成取消")
  @Update(
      "UPDATE reporting_async_task SET status='"
          + AsyncTaskStatus.Codes.CANCELLED
          + "',progress_percent=100,retryable=FALSE,lease_until=NULL,next_retry_at=NULL,finished_at=#{now},version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.CANCELLING
          + "' AND attempt_count=#{attemptNo} AND (lease_until IS NULL OR lease_until<=#{now})")
  int cancelExpiredLease(String id, int attemptNo, Instant now, String actorId);

  /** 更新指定业务记录。 */
  @SpecialSql("原子校验可取消状态并记录取消请求")
  // 先按原状态计算完成时间和租约，再更新状态，避免后续表达式读到已赋值的新状态。
  @Update(
      "UPDATE reporting_async_task SET next_retry_at=NULL,finished_at=CASE WHEN status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "'"
          + " THEN #{now} ELSE finished_at"
          + " END,lease_until=CASE WHEN status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "' THEN NULL ELSE lease_until"
          + " END,retryable=CASE WHEN status='" + AsyncTaskStatus.Codes.QUEUED
          + "' THEN FALSE ELSE retryable END,status=CASE WHEN status='" + AsyncTaskStatus.Codes.QUEUED
          + "' THEN '" + AsyncTaskStatus.Codes.CANCELLED + "' ELSE '" + AsyncTaskStatus.Codes.CANCELLING
          + "' END,safe_error_detail=#{reason},version=version+1,update_id=#{actorId},update_time=#{now} WHERE id=#{id}"
          + " AND status IN ('"
          + AsyncTaskStatus.Codes.QUEUED
          + "','"
          + AsyncTaskStatus.Codes.RUNNING
          + "')")
  int requestCancel(String id, String reason, Instant now, String actorId);

  /** 更新指定业务记录。 */
  @SpecialSql("原子校验失败与重试次数后重新排队")
  @Update(
      "UPDATE reporting_async_task SET"
          + " status='"
          + AsyncTaskStatus.Codes.QUEUED
          + "',progress_percent=0,error_code=NULL,safe_error_detail=NULL,retryable=FALSE,result_resource_id=NULL,started_at=NULL,lease_until=NULL,next_retry_at=NULL,finished_at=NULL,version=version+1,update_id=#{actorId},update_time=#{now}"
          + " WHERE id=#{id} AND status='"
          + AsyncTaskStatus.Codes.FAILED
          + "' AND retryable=TRUE AND attempt_count <"
          + " max_attempts")
  /** 重试失败。 */
  int retryFailed(String id, Instant now, String actorId);
}

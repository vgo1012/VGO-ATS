package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import java.time.Instant;
import java.util.List;

public interface AsyncTaskService extends IService<AsyncTaskEntity> {

  PersistentTaskBacklogSnapshot findReadyBacklog(List<String> taskTypes, Instant now);

  /** 新增忽略。 */
  int insertIgnore(AsyncTaskEntity task);

  /** 查询任务。 */
  TaskSnapshot findTask(String id);

  /** 在调用方事务中锁定并返回当前任务快照，避免取消决策沿用 RR 旧快照。 */
  TaskSnapshot findTaskForUpdate(String id);

  /** 在调用者短事务中锁定原任务，不改变任务版本和历史。 */
  String lockTask(String id);

  /** 查询任务操作人幂等。 */
  TaskSnapshot findTaskByIdempotency(String actorUserId, String key);

  /** 完整业务关联当前锁读，最多返回两条用于判定歧义，调用方持有业务资源锁。 */
  List<TaskSnapshot> findTasksByResourceIdempotencyForUpdate(
      String taskType, String resourceType, String resourceId, String key);

  /** 唯一键竞争后以当前读查询任务，避免沿用事务开始时的旧快照。 */
  TaskSnapshot findTaskByIdempotencyForUpdate(String actorUserId, String key);

  /** 以当前读查询任务实体，供同一唯一键竞争事务完成请求指纹校验。 */
  AsyncTaskEntity findEntityByIdForUpdate(String id);

  List<TaskSnapshot> findRecentTasks(
      String status,
      String taskType,
      String ownerUserId,
      Instant cursorTime,
      String cursorId,
      int limit);

  List<TaskSnapshot> findRecentTasks(
      String status, String taskType, String ownerUserId, String resourceType, String resourceId,
      Instant cursorTime, String cursorId, int limit);

  List<String> findQueuedIdsForUpdate(String taskType, Instant now, int limit);

  /** 同一失败尝试按指定期限重新排队，版本不匹配时不覆盖。 */
  int scheduleRetry(TaskSnapshot failed, Instant retryAt, Instant now, String actorId);

  List<AsyncTaskLeaseRow> findExpiredLeasesForUpdate(String taskType, Instant now, int limit);

  int markRunning(String id, Instant now, Instant leaseUntil, String actorId);

  boolean hasActiveLease(String id, int attemptNo, Instant now);

  int renewLease(String id, int attemptNo, Instant now, Instant leaseUntil, String actorId);

  /** 更新进度。 */
  int updateProgress(
      String id,
      int attemptNo,
      int progress,
      Instant now,
      Instant leaseUntil,
      String actorId);

  /** 标记成功。 */
  int markSucceeded(String id, int attemptNo, String resultId, Instant now, String actorId);

  /** 标记失败。 */
  int markFailed(
      String id,
      int attemptNo,
      String code,
      String detail,
      boolean retryable,
      Instant now,
      String actorId);

  /** 标记已取消。 */
  int markCancelled(String id, int attemptNo, Instant now, String actorId);

  int requeueExpiredLease(String id, int attemptNo, Instant now, String actorId);

  int failExpiredLease(
      String id, int attemptNo, String code, String detail, Instant now, String actorId);

  int cancelExpiredLease(String id, int attemptNo, Instant now, String actorId);

  int requestCancel(String id, String reason, Instant now, String actorId);

  /** 重试失败。 */
  int retryFailed(String id, Instant now, String actorId);
}

package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskAttemptEntity;
import java.time.Instant;
import java.util.List;

public interface AsyncTaskAttemptService extends IService<AsyncTaskAttemptEntity> {
  /** 新增尝试。 */
  int insertAttempt(String attemptId, String taskId, Instant now);

  /** 标记成功。 */
  int markSucceeded(String taskId, int attemptNo, Instant now);

  /** 标记失败。 */
  int markFailed(String taskId, int attemptNo, String code, String detail, Instant now);

  /** 标记已取消。 */
  int markCancelled(String taskId, int attemptNo, Instant now);

  List<AsyncTaskAttemptEntity> findByTaskId(String taskId);
}

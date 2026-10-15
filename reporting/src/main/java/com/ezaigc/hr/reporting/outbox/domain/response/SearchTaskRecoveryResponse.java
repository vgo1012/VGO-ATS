package com.ezaigc.hr.reporting.outbox.domain.response;

import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 人工恢复关联及新任务的当前状态。 */
@Data
@AllArgsConstructor
public class SearchTaskRecoveryResponse {
  /** 原失败任务 ID，其状态和尝试历史不被覆盖。 */
  private String originalTaskId;
  /** 新任务，可继续通过全局任务接口查询和取消。 */
  private AsyncTaskResponse recoveryTask;
  /** 新任务使用的持久事件 ID。 */
  private String outboxEventId;
  /** 管理员填写的原因，最多 500 字符。 */
  private String reason;
  /** 实际创建者。 */
  private String requestedByUserId;
  /** 恢复创建时间。 */
  private Instant createTime;
}

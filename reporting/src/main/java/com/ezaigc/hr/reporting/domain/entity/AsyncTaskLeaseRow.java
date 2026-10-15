package com.ezaigc.hr.reporting.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 过期异步任务租约的最小锁定投影。
 *
 * <p>仅暴露租约恢复决策所需字段，避免把部分列查询结果伪装成完整持久化实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskLeaseRow {
  /** 任务 ID。 */
  private String id;

  /** 任务类型。 */
  private String taskType;

  /** 任务所属业务资源 ID，用于终态同步时精确定位作业。 */
  private String resourceId;

  /** 当前任务状态。 */
  private String status;

  /** 当前尝试次数。 */
  private int attemptCount;

  /** 最大尝试次数。 */
  private int maxAttempts;
}

package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 异步任务 HTTP 响应对象。 */
@Data
@AllArgsConstructor
public class AsyncTaskResponse {
  /** 异步任务 ID。 */
  private String taskId;
  /** 资源 ID。 */
  private String resourceId;
  /** 资源类型。 */
  private String resourceType;
  /** 任务类型。 */
  private String taskType;
  /** 当前状态。 */
  private String status;
  /** 已尝试次数。 */
  private int attemptCount;
  /** 最大尝试次数。 */
  private int maxAttempts;
  /** 任务进度百分比。 */
  private int progressPercent;
  /** 错误编码。 */
  private String errorCode;
  /** 错误详情。 */
  private String errorDetail;
  /** 失败后是否允许重试。 */
  private boolean retryable;
  /** 结果资源 ID。 */
  private String resultResourceId;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 开始处理时间。 */
  private Instant startedAt;
  /** 完成处理时间。 */
  private Instant finishedAt;
  /** 更新时间。 */
  private Instant updateTime;
  /** 发起人，用于区分代他人提交和本人负责的任务。 */
  private String requestedByUserId;
  /** 当前负责人。 */
  private String ownerUserId;
}

package com.ezaigc.hr.intelligence.matching.domain.response;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 匹配运行任务 HTTP 响应对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRunTaskResponse {
  /** 异步任务 ID。 */
  private String taskId;
  /** 当前状态。 */
  private String status;
  /** 任务进度百分比。 */
  private int progressPercent;
  /** 错误编码。 */
  private String errorCode;
  /** 失败后是否允许重试。 */
  private boolean retryable;
  /** 结果资源 ID。 */
  private String resultResourceId;
  /** 更新时间。 */
  private Instant updateTime;

  public static MatchRunTaskResponse from(TaskSnapshot v) {
    return new MatchRunTaskResponse(
        v.id(),
        v.status(),
        v.progressPercent(),
        v.errorCode(),
        v.retryable(),
        v.resultResourceId(),
        v.updateTime());
  }
}

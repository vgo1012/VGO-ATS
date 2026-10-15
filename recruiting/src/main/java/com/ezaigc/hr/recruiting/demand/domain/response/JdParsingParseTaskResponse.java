package com.ezaigc.hr.recruiting.demand.domain.response;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JD 解析任务 HTTP 响应对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JdParsingParseTaskResponse {

  /** 异步任务 ID。 */
  private String taskId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 当前状态。 */
  private String status;
  /** 已尝试次数。 */
  private int attemptCount;
  /** 最大尝试次数。 */
  private int maxAttempts;
  /** 错误编码。 */
  private String errorCode;
  /** 失败后是否允许重试。 */
  private boolean retryable;
  private String resultFactSnapshotId;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;

  public static JdParsingParseTaskResponse from(TaskSnapshot v) {
    return new JdParsingParseTaskResponse(
        v.id(),
        v.resourceId(),
        v.status(),
        v.attemptCount(),
        v.maxAttempts(),
        v.errorCode(),
        v.retryable(),
        v.resultResourceId(),
        v.createTime(),
        v.updateTime());
  }
}

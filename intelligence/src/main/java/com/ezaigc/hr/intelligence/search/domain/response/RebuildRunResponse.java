package com.ezaigc.hr.intelligence.search.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class RebuildRunResponse {
  /** 主键 ID。 */
  private String id;
  /** 异步任务 ID。 */
  private String taskId;
  /** 目标搜索索引名称。 */
  private String targetIndex;
  /** 当前状态。 */
  private String status;
  /** 预计数量。 */
  private long projectedCount;
  /** 错误编码。 */
  private String errorCode;
  /** 请求发起人用户 ID。 */
  private String requestedByUserId;
  /** 创建时间。 */
  private Instant createTime;
  /** 开始处理时间。 */
  private Instant startedAt;
  /** 完成处理时间。 */
  private Instant finishedAt;
}

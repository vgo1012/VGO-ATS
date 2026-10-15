package com.ezaigc.hr.identity.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HandoverBatchResponse {
  /** 主键 ID。 */
  private String id;
  /** 来源用户 ID。 */
  private String sourceUserId;
  /** 目标用户 ID。 */
  private String targetUserId;
  /** 选择模式。 */
  private String selectionMode;
  /** 当前状态。 */
  private String status;
  /** 总记录数。 */
  private int totalCount;
  /** 成功记录数。 */
  private int succeededCount;
  /** 失败记录数。 */
  private int failedCount;
  /** 未解决记录数。 */
  private int unresolvedCount;
  /** 操作原因。 */
  private String reason;
  /** 请求发起人用户 ID。 */
  private String requestedByUserId;
  /** 开始处理时间。 */
  private Instant startedAt;
  /** 完成时间。 */
  private Instant completedAt;
  /** 技术乐观锁版本号。 */
  private String version;
}

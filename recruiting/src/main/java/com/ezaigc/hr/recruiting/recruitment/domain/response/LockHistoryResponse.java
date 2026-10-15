package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LockHistoryResponse {
  /** 主键 ID。 */
  private String id;
  private String lockId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 操作编码。 */
  private String actionCode;
  /** 变更前负责人用户 ID。 */
  private String fromOwnerUserId;
  /** 变更后负责人用户 ID。 */
  private String toOwnerUserId;
  /** 进度类型。 */
  private String progressType;
  /** 操作原因。 */
  private String reason;
  /** 实际操作人用户 ID。 */
  private String actorUserId;
  /** 事件发生时间。 */
  private Instant occurredAt;
}

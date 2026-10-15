package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LockPolicyHistoryResponse {
  /** 主键 ID。 */
  private String id;
  /** 策略键。 */
  private String policyKey;
  /** 上一时长天数。 */
  private int previousDurationDays;
  /** 新时长天数。 */
  private int newDurationDays;
  /** 操作原因。 */
  private String reason;
  /** 实际操作人用户 ID。 */
  private String actorUserId;
  /** 事件发生时间。 */
  private Instant occurredAt;
}

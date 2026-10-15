package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OfferCommandDTO {
  /** 当前状态。 */
  private String status;
  /** 终止原因。 */
  private String terminationReason;
  /** 应聘流程结果。 */
  private String applicationOutcome;
  /** 操作原因。 */
  private String reason;
  /** 事件发生时间。 */
  private Instant occurredAt;
  /** 外部来源引用。 */
  private String sourceReference;
  /** 外部系统原始状态。 */
  private String sourceStatus;
}

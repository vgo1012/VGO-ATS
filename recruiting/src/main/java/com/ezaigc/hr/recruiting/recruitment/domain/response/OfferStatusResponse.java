package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OfferStatusResponse {
  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 记录类型。 */
  private String recordType;
  /** 当前状态。 */
  private String status;
  /** 终止原因。 */
  private String terminationReason;
  /** 应聘流程结果。 */
  private String applicationOutcome;
  /** 操作原因。 */
  private String reason;
  private String correctsRecordId;
  private String reopensRecordId;
  /** 来源。 */
  private String source;
  /** 外部来源引用。 */
  private String sourceReference;
  /** 外部系统原始状态。 */
  private String sourceStatus;
  /** 事件发生时间。 */
  private Instant occurredAt;
  /** 操作人用户 ID。 */
  private String operatedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}

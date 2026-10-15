package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_offer_status_record")
public class OfferStatusRecord extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 记录类型。 */
  @TableField("record_type")
  private String recordType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 终止原因。 */
  @TableField("termination_reason")
  private String terminationReason;
  /** 应聘流程结果。 */
  @TableField("application_outcome")
  private String applicationOutcome;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  @TableField("corrects_record_id")
  private String correctsRecordId;
  @TableField("reopens_record_id")
  private String reopensRecordId;
  /** 来源。 */
  @TableField("source")
  private String source;
  /** 外部来源引用。 */
  @TableField("source_reference")
  private String sourceReference;
  /** 外部系统原始状态。 */
  @TableField("source_status")
  private String sourceStatus;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
}

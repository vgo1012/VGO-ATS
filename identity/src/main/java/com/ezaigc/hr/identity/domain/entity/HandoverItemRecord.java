package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("identity_handover_item")
public class HandoverItemRecord extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 批次 ID。 */
  @TableField("batch_id")
  private String batchId;
  /** 资源类型。 */
  @TableField("resource_type")
  private String resourceType;
  /** 资源 ID。 */
  @TableField("resource_id")
  private String resourceId;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 是否为强制项。 */
  @TableField("mandatory")
  private boolean mandatory;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 错误信息。 */
  @TableField("error_message")
  private String errorMessage;
  /** 变更前负责人用户 ID。 */
  @TableField("from_owner_user_id")
  private String fromOwnerUserId;
  /** 变更后负责人用户 ID。 */
  @TableField("to_owner_user_id")
  private String toOwnerUserId;
  /** 归属变更历史记录 ID。 */
  @TableField("ownership_history_id")
  private String ownershipHistoryId;
  /** 处理时间。 */
  @TableField("processed_at")
  private Instant processedAt;

  public HandoverItemRecord(
      String id,
      String batchId,
      String resourceType,
      String resourceId,
      String status,
      boolean mandatory,
      String errorCode,
      String errorMessage,
      String fromOwnerUserId,
      String toOwnerUserId,
      String ownershipHistoryId) {
    this.id = id;
    this.batchId = batchId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.status = status;
    this.mandatory = mandatory;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.fromOwnerUserId = fromOwnerUserId;
    this.toOwnerUserId = toOwnerUserId;
    this.ownershipHistoryId = ownershipHistoryId;
  }
}

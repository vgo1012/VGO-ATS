package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_ownership_history")
public class OwnershipHistoryEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 聚合根类型。 */
  @TableField("aggregate_type")
  private String aggregateType;
  /** 聚合根 ID。 */
  @TableField("aggregate_id")
  private String aggregateId;
  /** 变更前负责人用户 ID。 */
  @TableField("from_owner_user_id")
  private String fromOwnerUserId;
  /** 变更后负责人用户 ID。 */
  @TableField("to_owner_user_id")
  private String toOwnerUserId;
  /** 变更前部门 ID。 */
  @TableField("from_department_id")
  private String fromDepartmentId;
  /** 变更后部门 ID。 */
  @TableField("to_department_id")
  private String toDepartmentId;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 实际操作人用户 ID。 */
  @TableField("actor_user_id")
  private String actorUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}

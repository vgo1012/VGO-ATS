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
@TableName("recruitment_stage_history")
public class StageHistory extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 变更前招聘阶段。 */
  @TableField("from_stage")
  private String fromStage;
  /** 变更后招聘阶段。 */
  @TableField("to_stage")
  private String toStage;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}

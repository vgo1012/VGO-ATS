package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_recruitment_collaborator")
public class DemandCollaboratorEntity extends StandardVersionedPersistenceFields {
  /** 招聘需求 ID。 */
  @TableField("demand_id")
  private String demandId;
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 添加操作人用户 ID。 */
  @TableField("added_by_user_id")
  private String addedByUserId;
  /** 添加时间。 */
  @TableField("added_at")
  private Instant addedAt;
}

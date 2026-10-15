package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_user_role")
public class UserRole extends StandardPersistenceFields {
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 角色 ID。 */
  @TableField("role_id")
  private String roleId;
  /** 分配操作人用户 ID。 */
  @TableField("assigned_by_user_id")
  private String assignedByUserId;
  /** 分配时间。 */
  @TableField("assigned_at")
  private Instant assignedAt;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
}

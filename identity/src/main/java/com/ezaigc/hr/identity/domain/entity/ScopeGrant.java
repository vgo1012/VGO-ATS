package com.ezaigc.hr.identity.domain.entity;

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
@TableName("identity_scope_grant")
public class ScopeGrant extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 权限范围类型。 */
  @TableField("scope_type")
  private String scopeType;
  /** 权限范围值。 */
  @TableField("scope_value")
  private String scopeValue;
  /** 授权操作人用户 ID。 */
  @TableField("granted_by_user_id")
  private String grantedByUserId;
  /** 授权时间。 */
  @TableField("granted_at")
  private Instant grantedAt;
}

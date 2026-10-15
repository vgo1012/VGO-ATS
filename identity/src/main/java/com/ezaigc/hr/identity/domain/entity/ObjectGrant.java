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
@TableName("identity_object_grant")
public class ObjectGrant extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 授权对象类型。 */
  @TableField("object_type")
  private String objectType;
  /** 授权对象 ID。 */
  @TableField("object_id")
  private String objectId;
  /** 权限编码。 */
  @TableField("permission_code")
  private String permissionCode;
  /** 授权操作人用户 ID。 */
  @TableField("granted_by_user_id")
  private String grantedByUserId;
  /** 授权时间。 */
  @TableField("granted_at")
  private Instant grantedAt;
}

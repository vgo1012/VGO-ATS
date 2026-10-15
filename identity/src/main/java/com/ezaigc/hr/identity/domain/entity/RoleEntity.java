package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_role")
public class RoleEntity extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 业务编码。 */
  @TableField("code")
  private String code;
  /** 名称。 */
  @TableField("name")
  private String name;
  /** 是否为系统内置角色。 */
  @TableField("system_role")
  private boolean systemRole;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
}

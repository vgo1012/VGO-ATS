package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("identity_department")
public class DepartmentRecord extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 业务编码。 */
  @TableField("code")
  private String code;
  /** 名称。 */
  @TableField("name")
  private String name;
  /** 上级部门 ID。 */
  @TableField("parent_department_id")
  private String parentDepartmentId;

  /** 部门分配队列负责人用户 ID。 */
  @TableField(exist = false)
  private String assignmentQueueOwnerUserId;

  /** 部门分配队列负责人姓名。 */
  @TableField(exist = false)
  private String assignmentQueueOwnerName;

  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 技术乐观锁版本号。 */
  @Version
  @TableField("version")
  private long version;

  public boolean active() {
    return IdentityActivationStatus.ACTIVE.matches(status);
  }
}

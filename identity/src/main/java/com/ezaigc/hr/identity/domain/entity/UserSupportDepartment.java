package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_user_support_department")
public class UserSupportDepartment extends StandardVersionedPersistenceFields {
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 部门 ID。 */
  @TableField("department_id")
  private String departmentId;
}

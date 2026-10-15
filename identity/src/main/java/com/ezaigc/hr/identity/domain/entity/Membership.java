package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_membership")
public class Membership extends StandardVersionedPersistenceFields {
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 部门 ID。 */
  @TableField("department_id")
  private String departmentId;
  /** 成员关系类型。 */
  @TableField("membership_type")
  private String membershipType;
}

package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_department_assignment_queue")
public class DepartmentAssignmentQueue extends StandardPersistenceFields {
  /** 部门 ID。 */
  @TableId(value = "department_id", type = IdType.INPUT)
  private String departmentId;

  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
  /** 配置操作人用户 ID。 */
  @TableField("configured_by_user_id")
  private String configuredByUserId;
  /** 配置时间。 */
  @TableField("configured_at")
  private Instant configuredAt;

}

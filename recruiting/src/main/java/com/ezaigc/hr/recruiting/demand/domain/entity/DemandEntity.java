package com.ezaigc.hr.recruiting.demand.domain.entity;

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
@TableName("demand_recruitment_demand")
public class DemandEntity extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 业务编码。 */
  @TableField("code")
  private String code;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 项目 ID。 */
  @TableField("project_id")
  private String projectId;
  /** 职位 ID。 */
  @TableField("position_id")
  private String positionId;
  /** 招聘类型。 */
  @TableField("recruitment_type")
  private String recruitmentType;
  /** 计划招聘人数。 */
  @TableField("headcount")
  private Integer headcount;
  /** 描述。 */
  @TableField("description")
  private String description;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private Long version;

}

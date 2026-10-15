package com.ezaigc.hr.recruiting.demand.domain.entity;

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
@TableName("demand_workflow_template_version")
public class WorkflowTemplateEntity extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 业务版本号。 */
  @TableField("version_no")
  private Integer versionNo;
  /** 招聘类型。 */
  @TableField("recruitment_type")
  private String recruitmentType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 当前生效版本占位标记。 */
  @TableField("active_slot")
  private Integer activeSlot;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private Long version;

  /** 生效操作人用户 ID。 */
  @TableField("activated_by_user_id")
  private String activatedByUserId;
  /** 生效时间。 */
  @TableField("activated_at")
  private Instant activatedAt;

}

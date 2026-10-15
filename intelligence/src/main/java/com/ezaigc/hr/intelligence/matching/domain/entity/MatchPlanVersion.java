package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("matching_jd_match_plan_version")
public class MatchPlanVersion extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 匹配方案 ID。 */
  @TableField("plan_id")
  private String planId;
  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 当前生效版本占位标记。 */
  @TableField("active_slot")
  private Integer activeSlot;
  /** 受控条件方案版本 ID。 */
  @TableField("controlled_criterion_plan_version_id")
  private String controlledCriterionPlanVersionId;
  /** 是否启用技能维度。 */
  @TableField("skills_enabled")
  private boolean skillsEnabled;
  /** 是否启用角色维度。 */
  @TableField("role_enabled")
  private boolean roleEnabled;
  /** 是否启用年限维度。 */
  @TableField("years_enabled")
  private boolean yearsEnabled;
  /** 是否启用职责维度。 */
  @TableField("responsibility_enabled")
  private boolean responsibilityEnabled;
  /** 是否启用行业项目维度。 */
  @TableField("industry_project_enabled")
  private boolean industryProjectEnabled;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
  /** 配置操作人用户 ID。 */
  @TableField("configured_by_user_id")
  private String configuredByUserId;
  /** 生效操作人用户 ID。 */
  @TableField("activated_by_user_id")
  private String activatedByUserId;
  /** 生效时间。 */
  @TableField("activated_at")
  private Instant activatedAt;

}

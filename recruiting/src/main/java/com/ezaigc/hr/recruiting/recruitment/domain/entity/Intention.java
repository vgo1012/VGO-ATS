package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_intention")
public class Intention extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 期望薪资下限。 */
  @TableField("expected_salary_min")
  private BigDecimal expectedSalaryMin;
  /** 期望薪资上限。 */
  @TableField("expected_salary_max")
  private BigDecimal expectedSalaryMax;
  /** 薪资周期。 */
  @TableField("salary_period")
  private String salaryPeriod;
  /** 到岗时间或可用性。 */
  @TableField("availability")
  private String availability;
  /** 意向工作地点。 */
  @TableField("intention_locations")
  private String intentionLocations;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 记录人用户 ID。 */
  @TableField("recorded_by_user_id")
  private String recordedByUserId;
}

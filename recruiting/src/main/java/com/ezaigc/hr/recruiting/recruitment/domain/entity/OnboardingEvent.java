package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_onboarding_event")
public class OnboardingEvent extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 入职时间。 */
  @TableField("onboarded_at")
  private Instant onboardedAt;
  /** 来源。 */
  @TableField("source")
  private String source;
  /** 外部来源引用。 */
  @TableField("source_reference")
  private String sourceReference;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
}

package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 确认录用后至实际入职或退回之间的入职流程事实。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruitment_onboarding_process")
public class OnboardingProcess extends StandardVersionedPersistenceFields {
  @TableId(value = "application_id", type = IdType.INPUT)
  private String applicationId;

  @TableField("status")
  private String status;

  @TableField("started_at")
  private Instant startedAt;

  @TableField("actual_onboarded_at")
  private Instant actualOnboardedAt;

  @TableField("closed_at")
  private Instant closedAt;

  @TableField("started_by_user_id")
  private String startedByUserId;

  @TableField("completed_by_user_id")
  private String completedByUserId;
}

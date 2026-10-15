package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户将一个确定的候选人、简历版本和 JD 版本显式加入智能筛选的事实。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_screening_admission")
public class ScreeningAdmission extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  @TableField("candidate_id")
  private String candidateId;
  @TableField("resume_version_id")
  private String resumeVersionId;
  @TableField("jd_version_id")
  private String jdVersionId;
  @TableField("default_match_run_id")
  private String defaultMatchRunId;
  @TableField("admitted_by_user_id")
  private String admittedByUserId;
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  @TableField("admitted_at")
  private Instant admittedAt;
  @TableField("returned_at")
  private Instant returnedAt;
}

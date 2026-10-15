package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户重新选择 JD 后创建的不可变公司默认评分绑定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_jd_score_binding")
public class ResumeJdScoreBinding extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  @TableField("operation_id")
  private String operationId;
  @TableField("candidate_id")
  private String candidateId;
  @TableField("resume_version_id")
  private String resumeVersionId;
  @TableField("jd_id")
  private String jdId;
  @TableField("jd_version_id")
  private String jdVersionId;
  @TableField("default_match_run_id")
  private String defaultMatchRunId;
  @TableField("selected_by_user_id")
  private String selectedByUserId;
  @TableField("owner_department_id")
  private String ownerDepartmentId;
  @TableField("selected_at")
  private Instant selectedAt;
}

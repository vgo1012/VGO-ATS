package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_match_run_item_outcome")
public class MatchRunItemOutcome extends StandardVersionedPersistenceFields {
  /** 匹配运行任务 ID。 */
  @TableField("match_run_id")
  private String matchRunId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 最近尝试序号。 */
  @TableField("last_attempt_no")
  private Integer lastAttemptNo;

}

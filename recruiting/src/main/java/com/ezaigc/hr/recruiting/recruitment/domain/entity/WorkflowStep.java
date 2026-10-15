package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_application_workflow_step")
public class WorkflowStep extends StandardPersistenceFields {
  public WorkflowStep(String id, String applicationId, String templateVersionId, String stepDefinitionId, String stepCode, int sequenceNo, boolean candidateOptional, String defaultFollowUpChannel, String status, String ownerUserId, Instant dueAt, Instant startedAt, Instant completedAt, String resultReason, String clientSubmissionId, long version) {
    this.id = id;
    this.applicationId = applicationId;
    this.templateVersionId = templateVersionId;
    this.stepDefinitionId = stepDefinitionId;
    this.stepCode = stepCode;
    this.sequenceNo = sequenceNo;
    this.candidateOptional = candidateOptional;
    this.defaultFollowUpChannel = defaultFollowUpChannel;
    this.status = status;
    this.ownerUserId = ownerUserId;
    this.dueAt = dueAt;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.resultReason = resultReason;
    this.clientSubmissionId = clientSubmissionId;
    this.version = version;
  }

  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 模板版本 ID。 */
  @TableField("template_version_id")
  private String templateVersionId;
  /** 流程步骤定义 ID。 */
  @TableField("step_definition_id")
  private String stepDefinitionId;
  /** 步骤编码。 */
  @TableField("step_code")
  private String stepCode;
  @TableField("round_no")
  private Integer roundNo;
  /** 顺序号。 */
  @TableField("sequence_no")
  private int sequenceNo;
  /** 候选人是否可以跳过该步骤。 */
  @TableField("candidate_optional")
  private boolean candidateOptional;
  /** 默认跟进后续渠道。 */
  @TableField("default_follow_up_channel")
  private String defaultFollowUpChannel;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 截止时间。 */
  @TableField("due_at")
  private Instant dueAt;
  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 完成时间。 */
  @TableField("completed_at")
  private Instant completedAt;
  /** 结果原因。 */
  @TableField("result_reason")
  private String resultReason;
  /** 客户推荐记录 ID。 */
  @TableField("client_submission_id")
  private String clientSubmissionId;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}

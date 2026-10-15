package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.Data;

@Data

public class ApplicationWorkflowStepResponse {
  private Integer roundNo;
  public ApplicationWorkflowStepResponse(String id, String applicationId, String templateVersionId, String stepDefinitionId, String stepCode, int sequenceNo, boolean candidateOptional, String defaultFollowUpChannel, String status, String ownerUserId, Instant dueAt, Instant startedAt, Instant completedAt, String resultReason, String clientSubmissionId, String version, Instant createTime, Instant updateTime) {
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
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 模板版本 ID。 */
  private String templateVersionId;
  /** 流程步骤定义 ID。 */
  private String stepDefinitionId;
  /** 步骤编码。 */
  private String stepCode;
  /** 顺序号。 */
  private int sequenceNo;
  /** 候选人是否可以跳过该步骤。 */
  private boolean candidateOptional;
  /** 默认跟进后续渠道。 */
  private String defaultFollowUpChannel;
  /** 当前状态。 */
  private String status;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 截止时间。 */
  private Instant dueAt;
  /** 开始处理时间。 */
  private Instant startedAt;
  /** 完成时间。 */
  private Instant completedAt;
  /** 结果原因。 */
  private String resultReason;
  /** 客户推荐记录 ID。 */
  private String clientSubmissionId;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}

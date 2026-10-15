package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data

public class ApplicationResponse {
  private boolean progressiveFollowUp;
  private String clientId;
  private String clientName;
  /** 当前正在推进的流程步骤编码；仅流程进行中返回。 */
  private String currentStepCode;
  /** 当前步骤的面试轮次；非面试步骤可为空。 */
  private Integer currentStepRoundNo;
  /** 当前步骤状态；仅返回 PENDING 或 IN_PROGRESS。 */
  private String currentStepStatus;
  /** 当前步骤真实排期保存的面试类型；未排期时为空。 */
  private String currentInterviewType;
  /** 当前全部待进行面试的真实轮次，去重后升序返回。 */
  private List<Integer> activeInterviewRoundNos = List.of();
  /** 已完成真实面试中的最高轮次；无已完成面试或招聘活动已终止时为空。 */
  private Integer latestCompletedInterviewRoundNo;
  /** 独立入职流程状态；未进入入职流程时为空。 */
  private String onboardingStatus;
  /** HR 确认录用并进入入职流程的时间。 */
  private Instant onboardingStartedAt;
  /** HR 最终确认的实际入职时间。 */
  private Instant actualOnboardedAt;
  /** 从进入入职流程到实际入职或当前时刻的自然历时秒数。 */
  private Long onboardingElapsedSeconds;
  public ApplicationResponse(String id, String candidateId, String demandId, String jdId, String jdVersionId, String resumeVersionId, String workflowTemplateVersionId, String matchPlanVersionId, String recruitmentType, String currentStage, String outcome, String ownerUserId, String ownerDepartmentId, Long activeSlot, Instant terminalAt, String version, String createId, Instant createTime, String updateId, Instant updateTime) {
    this.id = id;
    this.candidateId = candidateId;
    this.demandId = demandId;
    this.jdId = jdId;
    this.jdVersionId = jdVersionId;
    this.resumeVersionId = resumeVersionId;
    this.workflowTemplateVersionId = workflowTemplateVersionId;
    this.matchPlanVersionId = matchPlanVersionId;
    this.recruitmentType = recruitmentType;
    this.currentStage = currentStage;
    this.outcome = outcome;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.activeSlot = activeSlot;
    this.terminalAt = terminalAt;
    this.version = version;
    this.createId = createId;
    this.createTime = createTime;
    this.updateId = updateId;
    this.updateTime = updateTime;
  }

  /** 主键 ID。 */
  private String id;
  /** 候选人 ID。 */
  private String candidateId;
  /** 招聘需求 ID。 */
  private String demandId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 招聘流程模板版本 ID。 */
  private String workflowTemplateVersionId;
  /** 匹配方案版本 ID。 */
  private String matchPlanVersionId;
  /** 招聘类型。 */
  private String recruitmentType;
  /** 当前招聘阶段。 */
  private String currentStage;
  /** 流程结果。 */
  private String outcome;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  /** 当前生效版本占位标记。 */
  private Long activeSlot;
  /** 进入终态的时间。 */
  private Instant terminalAt;
  /** 技术乐观锁版本号。 */
  private String version;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
  private String updateId;
  /** 更新时间。 */
  private Instant updateTime;
}

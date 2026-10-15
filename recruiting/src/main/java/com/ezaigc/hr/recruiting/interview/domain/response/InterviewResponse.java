package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.Data;

@Data

public class InterviewResponse {
  /** 仅在已授权列表中批量补充的展示摘要，不包含简历或 JD 原文。 */
  private String candidateName;
  private String jdTitle;
  private String jdCode;
  private String initialComment;
  private String clientId;
  private String clientName;
  public InterviewResponse(String id, String applicationId, String candidateId, String jdId, String jdVersionId, String workflowTemplateVersionId, String workflowStepId, String stepCode, String interviewType, int roundNo, String clientSubmissionId, String status, Instant scheduledStartAt, Instant scheduledEndAt, String timezoneId, String meetingMode, String locationText, String meetingUrl, String candidateNotificationChannel, String candidateConfirmationStatus, String ownerUserId, String ownerDepartmentId, String nextResponsibleUserId, String version, String createId, Instant createTime, String updateId, Instant updateTime) {
    this.id = id;
    this.applicationId = applicationId;
    this.candidateId = candidateId;
    this.jdId = jdId;
    this.jdVersionId = jdVersionId;
    this.workflowTemplateVersionId = workflowTemplateVersionId;
    this.workflowStepId = workflowStepId;
    this.stepCode = stepCode;
    this.interviewType = interviewType;
    this.roundNo = roundNo;
    this.clientSubmissionId = clientSubmissionId;
    this.status = status;
    this.scheduledStartAt = scheduledStartAt;
    this.scheduledEndAt = scheduledEndAt;
    this.timezoneId = timezoneId;
    this.meetingMode = meetingMode;
    this.locationText = locationText;
    this.meetingUrl = meetingUrl;
    this.candidateNotificationChannel = candidateNotificationChannel;
    this.candidateConfirmationStatus = candidateConfirmationStatus;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.nextResponsibleUserId = nextResponsibleUserId;
    this.version = version;
    this.createId = createId;
    this.createTime = createTime;
    this.updateId = updateId;
    this.updateTime = updateTime;
  }

  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 招聘流程模板版本 ID。 */
  private String workflowTemplateVersionId;
  /** 招聘流程步骤 ID。 */
  private String workflowStepId;
  /** 步骤编码。 */
  private String stepCode;
  /** 面试类型。 */
  private String interviewType;
  /** 轮次序号。 */
  private int roundNo;
  /** 客户推荐记录 ID。 */
  private String clientSubmissionId;
  /** 当前状态。 */
  private String status;
  /** 计划开始时间。 */
  private Instant scheduledStartAt;
  /** 计划结束时间。 */
  private Instant scheduledEndAt;
  private String timezoneId;
  /** 面试会议方式。 */
  private String meetingMode;
  /** 地点文本。 */
  private String locationText;
  /** 线上会议地址。 */
  private String meetingUrl;
  /** 候选人通知渠道。 */
  private String candidateNotificationChannel;
  private String candidateConfirmationStatus;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  private String nextResponsibleUserId;
  /** 技术乐观锁版本号。 */
  private String version;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
  private String updateId;
  /** 更新时间。 */
  private Instant updateTime;
}

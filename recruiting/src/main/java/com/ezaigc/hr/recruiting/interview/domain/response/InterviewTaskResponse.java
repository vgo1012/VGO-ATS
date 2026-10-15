package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 面试任务 HTTP 响应对象。 */
@Data
@AllArgsConstructor
public class InterviewTaskResponse {
  /** 面试 ID。 */
  private String interviewId;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 步骤编码。 */
  private String stepCode;
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
  /** 反馈截止时间。 */
  private Instant feedbackDueAt;
  /** 逾期。 */
  private boolean overdue;
  /** 当前负责人已授权任务的候选人姓名。 */
  private String candidateName;
  /** 是否已经提交。 */
  private boolean submitted;
}

package com.ezaigc.hr.recruiting.interview.domain.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateInterviewDTO {
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 招聘流程步骤 ID。 */
  private String workflowStepId;
  /** 开始时间。 */
  private Instant startAt;
  /** 结束时间。 */
  private Instant endAt;
  private String timezoneId;
  /** 面试会议方式。 */
  private String meetingMode;
  /** 地点文本。 */
  private String locationText;
  /** 线上会议地址。 */
  private String meetingUrl;
  private List<String> participantUserIds;
  /** 候选人通知渠道。 */
  private String candidateNotificationChannel;
  /** 客户推荐记录 ID。 */
  private String clientSubmissionId;
  /** 操作原因。 */
  private String reason;
  private String interviewType;
  private String initialComment;
  private InterviewExternalCalendarDTO externalCalendar;

  public CreateInterviewDTO(String applicationId, String workflowStepId, Instant startAt, Instant endAt,
      String timezoneId, String meetingMode, String locationText, String meetingUrl,
      List<String> participantUserIds, String candidateNotificationChannel, String clientSubmissionId,
      String reason, String interviewType, String initialComment) {
    this(applicationId, workflowStepId, startAt, endAt, timezoneId, meetingMode, locationText,
        meetingUrl, participantUserIds, candidateNotificationChannel, clientSubmissionId, reason);
    this.interviewType = interviewType;
    this.initialComment = initialComment;
  }

  public CreateInterviewDTO(String applicationId, String workflowStepId, Instant startAt, Instant endAt, String timezoneId, String meetingMode, String locationText, String meetingUrl, List<String> participantUserIds, String candidateNotificationChannel, String clientSubmissionId, String reason) {
    this.applicationId = applicationId;
    this.workflowStepId = workflowStepId;
    this.startAt = startAt;
    this.endAt = endAt;
    this.timezoneId = timezoneId;
    this.meetingMode = meetingMode;
    this.locationText = locationText;
    this.meetingUrl = meetingUrl;
    this.participantUserIds = participantUserIds;
    this.candidateNotificationChannel = candidateNotificationChannel;
    this.clientSubmissionId = clientSubmissionId;
    this.reason = reason;
  }
}

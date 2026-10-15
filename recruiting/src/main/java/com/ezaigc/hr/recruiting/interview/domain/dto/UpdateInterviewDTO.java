package com.ezaigc.hr.recruiting.interview.domain.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateInterviewDTO {
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
  private String nextResponsibleUserId;
  /** 技术乐观锁版本号。 */
  private long version;
  /** 操作原因。 */
  private String reason;
  private InterviewExternalCalendarDTO externalCalendar;

  public UpdateInterviewDTO(Instant startAt, Instant endAt, String timezoneId, String meetingMode,
      String locationText, String meetingUrl, List<String> participantUserIds,
      String candidateNotificationChannel, String nextResponsibleUserId, long version, String reason) {
    this.startAt = startAt;
    this.endAt = endAt;
    this.timezoneId = timezoneId;
    this.meetingMode = meetingMode;
    this.locationText = locationText;
    this.meetingUrl = meetingUrl;
    this.participantUserIds = participantUserIds;
    this.candidateNotificationChannel = candidateNotificationChannel;
    this.nextResponsibleUserId = nextResponsibleUserId;
    this.version = version;
    this.reason = reason;
  }
}

package com.ezaigc.hr.recruiting.interview.domain.request;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_PARTICIPANTS_PER_INTERVIEW;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewCreateRequest {
  private @NotBlank @Size(min = 26, max = 26) String applicationId;
  private @NotBlank @Size(min = 26, max = 26) String workflowStepId;
  /** 开始时间。 */
  private @NotNull Instant startAt;
  /** 结束时间。 */
  private @NotNull Instant endAt;
  private @NotBlank @Size(max = 64) String timezoneId;
  private @NotBlank @Size(max = 32) String meetingMode;
  private @Size(max = 500) String locationText;
  private @Size(max = 1000) String meetingUrl;
  private @Size(max = MAX_PARTICIPANTS_PER_INTERVIEW) List<@PlatformIdentifier String>
      participantUserIds;
  private @Size(max = 32) String candidateNotificationChannel;
  private @Size(min = 26, max = 26) String clientSubmissionId;
  private @Size(max = 500) String reason;

  private @Size(max = 32) String interviewType;
  private @Size(max = 1000) String initialComment;
  private @Valid InterviewExternalCalendarRequest externalCalendar;

  public InterviewCreateRequest(String applicationId, String workflowStepId, Instant startAt, Instant endAt, String timezoneId, String meetingMode, String locationText, String meetingUrl, List<String> participantUserIds, String candidateNotificationChannel, String clientSubmissionId, String reason) {
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

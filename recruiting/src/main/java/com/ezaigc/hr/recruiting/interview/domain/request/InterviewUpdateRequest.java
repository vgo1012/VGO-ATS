package com.ezaigc.hr.recruiting.interview.domain.request;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_PARTICIPANTS_PER_INTERVIEW;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
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
public class InterviewUpdateRequest {
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
  private @NotBlank @Size(max = 32) String candidateNotificationChannel;
  private @NotBlank @Size(min = 26, max = 26) String nextResponsibleUserId;
  private @ValidResourceVersion String version;
  private @Size(max = 500) String reason;
  private @Valid InterviewExternalCalendarRequest externalCalendar;

}

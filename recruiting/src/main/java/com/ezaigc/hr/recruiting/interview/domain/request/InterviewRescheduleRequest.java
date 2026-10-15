package com.ezaigc.hr.recruiting.interview.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewRescheduleRequest {
  /** 开始时间。 */
  private @NotNull Instant startAt;
  /** 结束时间。 */
  private @NotNull Instant endAt;
  private @NotBlank @Size(max = 64) String timezoneId;
  private @ValidResourceVersion String version;
  private @Size(max = 500) String reason;

}

package com.ezaigc.hr.recruiting.submission.domain.request;

import com.ezaigc.hr.recruiting.submission.enums.ClientRestrictionIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionFeedbackRequest {
  private @NotBlank @Size(max = 24) String result;
  private @NotBlank @Size(max = 60) String reasonCategory;
  private @NotBlank @Size(max = 500) String nextAction;
  private @Size(max = 1000) String note;
  private @NotBlank @Size(max = 40) String sourceChannel;
  /** 来源事件发生时间。 */
  private Instant sourceOccurredAt;
  private boolean explicitClientBan;
  /** 限制过期时间。 */
  private Instant restrictionExpiresAt;
  private @Size(max = 40) @Pattern(regexp = ClientRestrictionIntent.INPUT_PATTERN) String restrictionIntent;

}

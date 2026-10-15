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
public class SubmissionRestrictionRequest {
  private @NotBlank @Size(max = 40) @Pattern(regexp = ClientRestrictionIntent.INPUT_PATTERN) String restrictionIntent;
  /** 过期时间。 */
  private Instant expiresAt;
  private @NotBlank @Size(max = 1000) String reasonDetail;
  private @NotBlank @Size(max = 40) String sourceChannel;
  /** 来源事件发生时间。 */
  private Instant sourceOccurredAt;

}

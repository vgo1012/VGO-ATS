package com.ezaigc.hr.talent.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.DUPLICATE_RESOLUTION_MAX_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeConflictRequest {
  /** 解决结果。 */
  private @NotBlank
      @Size(max = DUPLICATE_RESOLUTION_MAX_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String resolution;
  private @OptionalPlatformIdentifier String chosenCandidateId;
  private @NotBlank @Size(max = 500) String reason;

}

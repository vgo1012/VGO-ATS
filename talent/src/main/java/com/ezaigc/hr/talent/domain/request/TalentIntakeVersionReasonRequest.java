package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeVersionReasonRequest {

  private @ValidResourceVersion String version;
  private @NotBlank @Size(max = 500) String reason;

  /** 转共享库时，HR 实际核对的简历版本；其他操作不使用。 */
  private @OptionalPlatformIdentifier String resumeVersionId;

  public TalentIntakeVersionReasonRequest(String version, String reason) {
    this(version, reason, null);
  }
}

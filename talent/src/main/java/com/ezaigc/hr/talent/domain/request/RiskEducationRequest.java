package com.ezaigc.hr.talent.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.EDUCATION_RISK_CODE_MAX_LENGTH;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
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
public class RiskEducationRequest {
  private @Size(min = 26, max = 26) String applicationId;
  private @NotBlank @Size(min = 26, max = 26) String candidateId;
  private @PlatformIdentifier String educationFactId;
  /** 记录类型。 */
  private @NotBlank
      @Size(max = EDUCATION_RISK_CODE_MAX_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String recordType;
  /** 核验状态。 */
  private @NotBlank
      @Size(max = EDUCATION_RISK_CODE_MAX_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String verificationStatus;
  /** 学习形式。 */
  private @NotBlank
      @Size(max = EDUCATION_RISK_CODE_MAX_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String studyMode;
  private @NotBlank @Size(max = 40) String sourceChannel;
  private @Size(max = 1000) String evidenceReference;
  private @Size(max = 1000) String note;
  private @Size(min = 26, max = 26) String correctsRecordId;
  /** 事件发生时间。 */
  private Instant occurredAt;

}

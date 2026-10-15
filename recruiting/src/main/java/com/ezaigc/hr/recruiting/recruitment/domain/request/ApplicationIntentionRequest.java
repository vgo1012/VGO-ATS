package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_LOCATIONS_PER_INTENTION;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_INTENTION_LOCATION_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationIntentionRequest {
  /** 期望薪资下限。 */
  private @PositiveOrZero BigDecimal expectedSalaryMin;
  /** 期望薪资上限。 */
  private @PositiveOrZero BigDecimal expectedSalaryMax;
  private @Size(max = 20) String salaryPeriod;
  private @Size(max = 120) String availability;
  private @Size(max = MAX_LOCATIONS_PER_INTENTION)
      List<@NotBlank @Size(max = MAX_INTENTION_LOCATION_LENGTH) String> locations;
  private @NotBlank @Size(max = 500) String reason;

}

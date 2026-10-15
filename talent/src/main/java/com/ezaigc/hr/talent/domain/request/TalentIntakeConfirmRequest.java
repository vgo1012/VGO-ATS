package com.ezaigc.hr.talent.domain.request;

import static com.ezaigc.hr.talent.constant.TalentConstants.CANDIDATE_EMAIL_MAX_LENGTH;
import static com.ezaigc.hr.talent.constant.TalentConstants.CANDIDATE_PHONE_INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.CANDIDATE_PHONE_MAX_LENGTH;
import static com.ezaigc.hr.talent.constant.TalentConstants.PLATFORM_ID_LENGTH;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeConfirmRequest {
  /** 候选人 ID。 */
  private @Size(min = PLATFORM_ID_LENGTH, max = PLATFORM_ID_LENGTH) String candidateId;
  private @NotBlank @Size(max = 160) String fullName;
  /** 邮箱地址。 */
  private @Email @Size(max = CANDIDATE_EMAIL_MAX_LENGTH) @ToString.Exclude String email;
  /** 联系电话。 */
  private @Size(max = CANDIDATE_PHONE_MAX_LENGTH)
      @Pattern(regexp = CANDIDATE_PHONE_INPUT_PATTERN) @ToString.Exclude String phone;
  private @ValidResourceVersion String version;

}

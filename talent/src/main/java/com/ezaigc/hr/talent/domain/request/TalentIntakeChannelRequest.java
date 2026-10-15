package com.ezaigc.hr.talent.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.SOURCE_CHANNEL_TYPE_MAX_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeChannelRequest {
  private @NotBlank @Size(max = 120) String name;
  /** 渠道类型。 */
  private @NotBlank
      @Size(max = SOURCE_CHANNEL_TYPE_MAX_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String channelType;

}

package com.ezaigc.hr.recruiting.submission.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionWithdrawRequest {
  private @NotBlank @Size(max = 1000) String reason;
  /** 技术乐观锁版本号。 */
  private @ValidResourceVersion String version;

}

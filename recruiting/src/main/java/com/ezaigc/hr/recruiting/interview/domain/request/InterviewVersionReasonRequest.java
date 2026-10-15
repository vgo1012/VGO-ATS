package com.ezaigc.hr.recruiting.interview.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewVersionReasonRequest {
  private @ValidResourceVersion String version;
  private @Size(max = 500) String reason;

}

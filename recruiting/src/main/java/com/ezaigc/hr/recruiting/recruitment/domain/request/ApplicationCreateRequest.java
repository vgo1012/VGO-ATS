package com.ezaigc.hr.recruiting.recruitment.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreateRequest {
  private @NotBlank @Size(min = 26, max = 26) String candidateId;
  private @NotBlank @Size(min = 26, max = 26) String jdVersionId;
  private @NotBlank @Size(min = 26, max = 26) String resumeVersionId;
  private @Size(min = 26, max = 26) String ownerUserId;
  private @NotBlank @Size(max = 500) String reason;

}

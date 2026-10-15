package com.ezaigc.hr.recruiting.submission.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCreateRequest {
  private @NotBlank @Size(min = 26, max = 26) String applicationId;

}

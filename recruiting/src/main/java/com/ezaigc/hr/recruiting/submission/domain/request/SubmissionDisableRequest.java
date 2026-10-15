package com.ezaigc.hr.recruiting.submission.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDisableRequest {
  private @NotBlank @Size(max = 1000) String reason;

}

package com.ezaigc.hr.intelligence.matching.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRunReasonRequest {
  private @NotBlank @Size(max = 500) String reason;

}

package com.ezaigc.hr.reporting.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReasonRequest {
  /** 操作原因。 */
  @NotBlank
  @Size(max = 500)
  private String reason;
}

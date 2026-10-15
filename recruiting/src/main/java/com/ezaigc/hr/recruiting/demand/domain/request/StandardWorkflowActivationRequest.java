package com.ezaigc.hr.recruiting.demand.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StandardWorkflowActivationRequest {
  @NotBlank @Size(max = 500) private String reason;
}

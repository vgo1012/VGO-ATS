package com.ezaigc.hr.talent.domain.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class ResumeLifecycleRetryRequest {
  @NotBlank @Size(max=500) private String reason;
}

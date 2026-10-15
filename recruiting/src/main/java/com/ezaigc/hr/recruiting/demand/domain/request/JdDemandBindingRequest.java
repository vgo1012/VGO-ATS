package com.ezaigc.hr.recruiting.demand.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JdDemandBindingRequest {
  @PlatformIdentifier private String demandId;
  @NotBlank @Size(max = 500) private String reason;
}

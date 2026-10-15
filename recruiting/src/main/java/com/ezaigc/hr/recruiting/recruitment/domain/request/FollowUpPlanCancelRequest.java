package com.ezaigc.hr.recruiting.recruitment.domain.request;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class FollowUpPlanCancelRequest {
  @Size(max = 500) private String reason;
  @ValidResourceVersion private String version;
}

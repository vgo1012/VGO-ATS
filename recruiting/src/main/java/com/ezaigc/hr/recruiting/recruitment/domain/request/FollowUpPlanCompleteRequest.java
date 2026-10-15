package com.ezaigc.hr.recruiting.recruitment.domain.request;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class FollowUpPlanCompleteRequest {
  @Size(max = 500) private String reason;
  @ValidResourceVersion private String version;
  @NotBlank @Size(max = 32) private String result;
  @Size(max = 500) private String nextPlan;
  @OptionalPlatformIdentifier private String workflowStepId;
  @Size(max = 20) private String workflowStepVersion;
}

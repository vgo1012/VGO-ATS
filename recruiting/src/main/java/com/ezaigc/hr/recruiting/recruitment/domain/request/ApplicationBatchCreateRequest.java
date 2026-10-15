package com.ezaigc.hr.recruiting.recruitment.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ApplicationBatchCreateRequest {
  @PlatformIdentifier private String jdVersionId;
  @OptionalPlatformIdentifier private String ownerUserId;
  @NotBlank @Size(max = 500) private String reason;
  @NotNull @Size(min = 1, max = 100)
  private List<@NotNull @Valid ApplicationBatchItemRequest> items;
  /** null 保留待选路；空数组代表 HR 明确不选择可选步骤。 */
  @Size(max = 16) private List<@NotNull WorkflowStepCode> selectedStepCodes;
}

package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPathRequest {

    private @Size(max = MAX_WORKFLOW_STEP_COUNT) List<@PlatformIdentifier String> selectedStepIds;
    private @ValidResourceVersion String version;
    private @NotBlank
    @Size(max = 500) String reason;

}

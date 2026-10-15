package com.ezaigc.hr.recruiting.demand.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_RECRUITMENT_TYPE_LENGTH;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class WorkflowRequest {

    /**
     * 招聘类型。
     */
    private @NotBlank
            @Size(max = MAX_RECRUITMENT_TYPE_LENGTH)
            @Pattern(regexp = INPUT_PATTERN) String recruitmentType;

    /**
     * 步骤列表。
     */
    @NotNull
    @Size(min = 1, max = MAX_WORKFLOW_STEP_COUNT)
    private List<@NotNull @Valid WorkflowStepRequest> steps;

    /**
     * 技术乐观锁版本号。
     */
    @ValidResourceVersion
    private String version;
}

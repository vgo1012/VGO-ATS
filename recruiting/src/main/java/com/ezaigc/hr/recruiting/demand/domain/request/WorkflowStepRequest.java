package com.ezaigc.hr.recruiting.demand.domain.request;

import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * HTTP 入参中的流程步骤，避免传输模型直接复用应用层 DTO。
 */
@Data
public class WorkflowStepRequest {

    /**
     * 业务编码。
     */
    @NotNull
    private WorkflowStepCode code;

    /**
     * 默认跟进后续渠道。
     */
    @Size(max = 32)
    private String defaultFollowUpChannel;
}

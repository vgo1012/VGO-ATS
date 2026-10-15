package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TalentIntakeBatchJdRequest {

    /**
     * 人工核对后选定的精确岗位版本。
     */
    private @PlatformIdentifier String jdVersionId;

    /**
     * 补关联原因，用于审计。
     */
    private @NotBlank
    @Size(max = 500) String reason;
}

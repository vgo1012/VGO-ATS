package com.ezaigc.hr.identity.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import lombok.Data;

@Data
public class WecomSyncRequest {
    @NotBlank
    @Size(min = 16, max = 80)
    @Pattern(regexp = "[a-zA-Z0-9_-]{16,80}")
    private String requestKey;
    @OptionalPlatformIdentifier
    private String retryOfBatchId;
}

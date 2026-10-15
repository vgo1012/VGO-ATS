package com.ezaigc.hr.recruiting.interview.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewExternalCalendarReconciliationRequest {
    private @ValidResourceVersion String version;
    private @PlatformIdentifier String intentId;
    private @NotBlank @Size(max = 32) String action;
    private @Size(max = 200) String externalObjectId;
    private @NotBlank @Size(max = 500) String reason;
    private Boolean originalRequestTerminated;
}

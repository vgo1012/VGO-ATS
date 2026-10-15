package com.ezaigc.hr.identity.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WecomAccountLinkRequest {
    @PlatformIdentifier
    private String userAccountId;

    @NotBlank
    @Size(max = 500)
    private String reason;
}

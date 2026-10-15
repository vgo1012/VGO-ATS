package com.ezaigc.hr.recruiting.recruitment.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationReasonVersionRequest {

    private @NotBlank
    @Size(max = 500) String reason;
    private @ValidResourceVersion String version;

}

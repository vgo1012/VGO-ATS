package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_REASON_LENGTH;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminDeleteDepartmentRequest {
  private @ValidResourceVersion String version;
  private @NotBlank @Size(max = MAX_REASON_LENGTH) String reason;
}

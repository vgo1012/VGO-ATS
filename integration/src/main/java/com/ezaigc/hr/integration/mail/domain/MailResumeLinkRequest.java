package com.ezaigc.hr.integration.mail.domain;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MailResumeLinkRequest {
  @PlatformIdentifier private String jdId;
  @NotBlank @Size(max = 500) private String reason;
  private boolean saveExactRule;
}

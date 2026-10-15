package com.ezaigc.hr.identity.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminHandoverPreviewRequest {
  /** 目标用户 ID。 */
  private @PlatformIdentifier String targetUserId;

}

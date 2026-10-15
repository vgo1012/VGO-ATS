package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.ASSIGNABLE_ROLE_CODE_INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_REASON_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ROLE_CODE_INPUT_LENGTH;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminRoleAssignmentRequest {
  /** 用户 ID。 */
  private @PlatformIdentifier String userId;
  /** 角色编码。 */
  private @NotBlank
  @Size(max = MAX_ROLE_CODE_INPUT_LENGTH)
  @Pattern(regexp = ASSIGNABLE_ROLE_CODE_INPUT_PATTERN) String roleCode;
  /** 操作原因。 */
  private @NotBlank @Size(max = MAX_REASON_LENGTH) String reason;

}

package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_DISPLAY_NAME_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_REASON_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SOURCE_TITLE_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SUPPORT_DEPARTMENTS_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_WORK_EMAIL_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAdminUpdateUserRequest {
  /** 用户展示名称。 */
  private @NotBlank @Size(max = MAX_DISPLAY_NAME_LENGTH) String displayName;
  /** 工作邮箱。 */
  private @NotBlank @Email @Size(max = MAX_WORK_EMAIL_LENGTH) String workEmail;
  /** 来源职位名称。 */
  private @Size(max = MAX_SOURCE_TITLE_LENGTH) String sourceTitle;
  /** 用户主部门 ID。 */
  private @PlatformIdentifier String homeDepartmentId;
  /** 直属领导用户 ID。 */
  private @OptionalPlatformIdentifier String directLeaderUserId;
  private @Size(max = MAX_SUPPORT_DEPARTMENTS_PER_USER) Set<@PlatformIdentifier String>
      supportDepartmentIds;
  /** 技术乐观锁版本号。 */
  private @ValidResourceVersion String version;
  /** 操作原因。 */
  private @NotBlank @Size(max = MAX_REASON_LENGTH) String reason;

}

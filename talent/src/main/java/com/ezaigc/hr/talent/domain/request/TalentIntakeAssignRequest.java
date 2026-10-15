package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeAssignRequest {
  /** 业务负责人用户 ID。 */
  private @PlatformIdentifier String ownerUserId;
  /** 业务归属部门 ID。 */
  private @PlatformIdentifier String ownerDepartmentId;
  private @ValidResourceVersion String version;
  private @NotBlank @Size(max = 500) String reason;

}

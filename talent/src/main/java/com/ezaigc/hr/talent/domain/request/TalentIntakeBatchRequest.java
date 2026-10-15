package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentIntakeBatchRequest {
  /** 来源渠道 ID。 */
  private @PlatformIdentifier String sourceChannelId;

  private @OptionalPlatformIdentifier String defaultOwnerUserId;

  /** 业务归属部门 ID。 */
  private @OptionalPlatformIdentifier String ownerDepartmentId;

  /** 本批简历唯一绑定的岗位版本；用于入库后的公司默认评分。 */
  private @PlatformIdentifier String jdVersionId;

  public TalentIntakeBatchRequest(
      String sourceChannelId, String defaultOwnerUserId, String ownerDepartmentId) {
    this(sourceChannelId, defaultOwnerUserId, ownerDepartmentId, null);
  }
}

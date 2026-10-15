package com.ezaigc.hr.identity.domain.response;

import com.ezaigc.hr.identity.api.HandoverResource;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HandoverPreviewResponse {
  /** 来源用户 ID。 */
  private String sourceUserId;
  /** 目标用户 ID。 */
  private String targetUserId;
  /** 资源列表。 */
  private List<HandoverResource> resources;
  /** 强制数量。 */
  private long mandatoryCount;
  /** 未解决记录数。 */
  private long unresolvedCount;
}

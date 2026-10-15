package com.ezaigc.hr.identity.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoverResource {
  /** 资源类型。 */
  private String resourceType;
  /** 资源 ID。 */
  private String resourceId;
  /** 用户展示名称。 */
  private String displayName;
  /** 是否为强制项。 */
  private boolean mandatory;
  /** 是否允许转交。 */
  private boolean transferable;
  /** 未解决原因。 */
  private String unresolvedReason;

}

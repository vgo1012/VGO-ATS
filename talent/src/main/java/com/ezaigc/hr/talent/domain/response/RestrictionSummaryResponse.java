package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RestrictionSummaryResponse {
  /** 限制类型。 */
  private String restrictionType;
  /** 客户 ID。 */
  private String clientId;
  /** 客户名称。 */
  private String clientName;
  /** 操作原因。 */
  private String reason;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 事件发生时间。 */
  private Instant occurredAt;
  /** 过期时间。 */
  private Instant expiresAt;
  /** 是否阻塞当前操作。 */
  private boolean blocking;
}

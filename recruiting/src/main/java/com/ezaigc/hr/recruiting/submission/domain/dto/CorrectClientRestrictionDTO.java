package com.ezaigc.hr.recruiting.submission.domain.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CorrectClientRestrictionDTO {
  /** 客户禁推意图。 */
  private String restrictionIntent;
  /** 过期时间。 */
  private Instant expiresAt;
  /** 原因详情。 */
  private String reasonDetail;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 来源事件发生时间。 */
  private Instant sourceOccurredAt;
}

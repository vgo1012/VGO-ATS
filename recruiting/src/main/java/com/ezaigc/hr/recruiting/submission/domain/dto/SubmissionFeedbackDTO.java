package com.ezaigc.hr.recruiting.submission.domain.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmissionFeedbackDTO {
  /** 结果。 */
  private String result;
  /** 原因分类。 */
  private String reasonCategory;
  /** 下一步操作。 */
  private String nextAction;
  /** 备注说明。 */
  private String note;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 来源事件发生时间。 */
  private Instant sourceOccurredAt;
  private boolean explicitClientBan;
  /** 限制过期时间。 */
  private Instant restrictionExpiresAt;
  /** 客户禁推意图。 */
  private String restrictionIntent;
}

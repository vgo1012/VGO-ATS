package com.ezaigc.hr.recruiting.submission.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiskAcknowledgementResponse {
  /** 主键 ID。 */
  private String id;
  /** 客户推荐记录 ID。 */
  private String submissionId;
  /** 风险编码。 */
  private String riskCode;
  /** 操作原因。 */
  private String reason;
  /** 风险确认人用户 ID。 */
  private String acknowledgedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}

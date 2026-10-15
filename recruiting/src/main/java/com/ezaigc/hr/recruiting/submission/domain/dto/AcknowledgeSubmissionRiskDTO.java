package com.ezaigc.hr.recruiting.submission.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AcknowledgeSubmissionRiskDTO {
  /** 风险编码。 */
  private String riskCode;
  /** 操作原因。 */
  private String reason;
}

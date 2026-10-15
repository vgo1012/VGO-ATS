package com.ezaigc.hr.recruiting.submission.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDetails {
  /** 客户推荐。 */
  private ClientSubmission submission;
  /** 发送快照。 */
  private SendSnapshot sendSnapshot;
  /** 反馈。 */
  private List<Feedback> feedback;
  /** 风险确认列表。 */
  private List<RiskAcknowledgement> riskAcknowledgements;
}

package com.ezaigc.hr.recruiting.submission.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmissionDetailsResponse {
  /** 客户推荐。 */
  private ClientSubmissionResponse submission;
  /** 发送快照。 */
  private SendSnapshotResponse sendSnapshot;
  /** 反馈。 */
  private List<FeedbackResponse> feedback;
  /** 风险确认列表。 */
  private List<RiskAcknowledgementResponse> riskAcknowledgements;
}

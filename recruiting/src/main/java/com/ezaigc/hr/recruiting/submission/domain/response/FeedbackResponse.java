package com.ezaigc.hr.recruiting.submission.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeedbackResponse {
  /** 主键 ID。 */
  private String id;
  /** 客户推荐记录 ID。 */
  private String submissionId;
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
  /** 记录人用户 ID。 */
  private String recordedByUserId;
  /** 创建时间。 */
  private Instant createTime;
}

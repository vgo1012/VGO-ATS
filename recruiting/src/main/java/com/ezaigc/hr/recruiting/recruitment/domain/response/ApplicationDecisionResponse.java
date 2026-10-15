package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationDecisionResponse {
  /** 主键 ID。 */
  private String id;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 记录类型。 */
  private String recordType;
  /** 人工决定。 */
  private String decision;
  /** 操作原因。 */
  private String reason;
  private String correctsRecordId;
  /** 客户推荐记录 ID。 */
  private String clientSubmissionId;
  private String decidedByUserId;
  /** 决定时间。 */
  private Instant decidedAt;
  /** 创建时间。 */
  private Instant createTime;
}

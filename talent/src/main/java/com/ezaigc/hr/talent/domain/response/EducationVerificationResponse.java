package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EducationVerificationResponse {
  /** 主键 ID。 */
  private String id;
  /** 候选人 ID。 */
  private String candidateId;
  private String educationFactId;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 记录类型。 */
  private String recordType;
  /** 核验状态。 */
  private String verificationStatus;
  /** 学习形式。 */
  private String studyMode;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 证据引用。 */
  private String evidenceReference;
  /** 备注说明。 */
  private String note;
  private String correctsRecordId;
  /** 记录人用户 ID。 */
  private String recordedByUserId;
  /** 事件发生时间。 */
  private Instant occurredAt;
  /** 创建时间。 */
  private Instant createTime;
  /** 技术乐观锁版本号。 */
  private String version;
}

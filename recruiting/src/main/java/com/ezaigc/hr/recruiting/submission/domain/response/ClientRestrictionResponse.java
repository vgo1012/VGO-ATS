package com.ezaigc.hr.recruiting.submission.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClientRestrictionResponse {
  /** 主键 ID。 */
  private String id;
  /** 客户 ID。 */
  private String clientId;
  /** 候选人 ID。 */
  private String candidateId;
  private String sourceSubmissionId;
  /** 发起限制的招聘活动，独立于客户推荐。 */
  private String sourceApplicationId;
  /** 人工记录的证据引用。 */
  private String evidenceReference;
  /** 记录类型。 */
  private String recordType;
  /** 当前状态。 */
  private String status;
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
  private String correctsRecordId;
  /** 记录人用户 ID。 */
  private String recordedByUserId;
  /** 创建时间。 */
  private Instant createTime;
  /** 技术乐观锁版本号。 */
  private String version;
}

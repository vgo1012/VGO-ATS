package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompanyBlacklistRecordResponse {
  /** 主键 ID。 */
  private String id;
  /** 候选人 ID。 */
  private String candidateId;
  /** 记录类型。 */
  private String recordType;
  /** 当前状态。 */
  private String status;
  /** 原因编码。 */
  private String reasonCode;
  /** 原因详情。 */
  private String reasonDetail;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 证据引用。 */
  private String evidenceReference;
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

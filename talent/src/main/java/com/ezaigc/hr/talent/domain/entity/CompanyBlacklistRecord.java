package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_company_blacklist_record")
public class CompanyBlacklistRecord extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 记录类型。 */
  @TableField("record_type")
  private String recordType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 原因编码。 */
  @TableField("reason_code")
  private String reasonCode;
  /** 原因详情。 */
  @TableField("reason_detail")
  private String reasonDetail;
  /** 来源渠道。 */
  @TableField("source_channel")
  private String sourceChannel;
  /** 证据引用。 */
  @TableField("evidence_reference")
  private String evidenceReference;
  @TableField("corrects_record_id")
  private String correctsRecordId;
  /** 记录人用户 ID。 */
  @TableField("recorded_by_user_id")
  private String recordedByUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
}

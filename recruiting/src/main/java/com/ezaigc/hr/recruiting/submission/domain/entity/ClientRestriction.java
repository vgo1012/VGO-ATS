package com.ezaigc.hr.recruiting.submission.domain.entity;

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
@TableName("submission_client_restriction")
public class ClientRestriction extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 客户 ID。 */
  @TableField("client_id")
  private String clientId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  @TableField("source_submission_id")
  private String sourceSubmissionId;
  /** 发起限制的招聘活动，独立于客户推荐。 */
  @TableField("source_application_id")
  private String sourceApplicationId;
  /** 人工记录的证据引用。 */
  @TableField("evidence_reference")
  private String evidenceReference;
  /** 记录类型。 */
  @TableField("record_type")
  private String recordType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 客户禁推意图。 */
  @TableField("restriction_intent")
  private String restrictionIntent;
  /** 过期时间。 */
  @TableField("expires_at")
  private Instant expiresAt;
  /** 原因详情。 */
  @TableField("reason_detail")
  private String reasonDetail;
  /** 来源渠道。 */
  @TableField("source_channel")
  private String sourceChannel;
  /** 来源事件发生时间。 */
  @TableField("source_occurred_at")
  private Instant sourceOccurredAt;
  @TableField("corrects_record_id")
  private String correctsRecordId;
  /** 记录人用户 ID。 */
  @TableField("recorded_by_user_id")
  private String recordedByUserId;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
}

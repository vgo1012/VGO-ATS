package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_decision_record")
public class Decision extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 记录类型。 */
  @TableField("record_type")
  private String recordType;
  /** 人工决定。 */
  @TableField("decision")
  private String decision;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  @TableField("corrects_record_id")
  private String correctsRecordId;
  /** 客户推荐记录 ID。 */
  @TableField("client_submission_id")
  private String clientSubmissionId;
  @TableField("decided_by_user_id")
  private String decidedByUserId;
  /** 决定时间。 */
  @TableField("decided_at")
  private Instant decidedAt;
}

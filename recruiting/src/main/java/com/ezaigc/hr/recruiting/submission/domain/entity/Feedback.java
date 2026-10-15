package com.ezaigc.hr.recruiting.submission.domain.entity;

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
@TableName("submission_feedback")
public class Feedback extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 客户推荐记录 ID。 */
  @TableField("submission_id")
  private String submissionId;
  /** 结果。 */
  @TableField("result")
  private String result;
  /** 原因分类。 */
  @TableField("reason_category")
  private String reasonCategory;
  /** 下一步操作。 */
  @TableField("next_action")
  private String nextAction;
  /** 备注说明。 */
  @TableField("note")
  private String note;
  /** 来源渠道。 */
  @TableField("source_channel")
  private String sourceChannel;
  /** 来源事件发生时间。 */
  @TableField("source_occurred_at")
  private Instant sourceOccurredAt;
  /** 记录人用户 ID。 */
  @TableField("recorded_by_user_id")
  private String recordedByUserId;
}

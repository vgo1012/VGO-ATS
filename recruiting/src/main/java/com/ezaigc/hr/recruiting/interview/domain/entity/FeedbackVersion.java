package com.ezaigc.hr.recruiting.interview.domain.entity;

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
@TableName("interview_feedback_version")
public class FeedbackVersion extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 反馈记录 ID。 */
  @TableField("feedback_id")
  private String feedbackId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
  /** 评估结论。 */
  @TableField("verdict")
  private String verdict;
  /** 简短说明。 */
  @TableField("short_comment")
  private String shortComment;
  /** 外部反馈来源。 */
  @TableField("external_feedback_source")
  private String externalFeedbackSource;
  /** 反馈发生时间。 */
  @TableField("feedback_occurred_at")
  private Instant feedbackOccurredAt;
  /** 变更原因。 */
  @TableField("change_reason")
  private String changeReason;
}

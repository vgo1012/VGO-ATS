package com.ezaigc.hr.recruiting.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("interview_feedback")
public class Feedback extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 面试 ID。 */
  @TableField("interview_id")
  private String interviewId;
  /** 填写人用户 ID。 */
  @TableField("author_user_id")
  private String authorUserId;
  /** 反馈类型。 */
  @TableField("feedback_kind")
  private String feedbackKind;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 当前业务版本号。 */
  @TableField("current_version_no")
  private int currentVersionNo;

}

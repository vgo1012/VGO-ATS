package com.ezaigc.hr.recruiting.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("interview_participant")
public class Participant extends StandardVersionedPersistenceFields {
  /** 面试 ID。 */
  @TableField("interview_id")
  private String interviewId;
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 面试参与者角色。 */
  @TableField("participant_role")
  private String participantRole;
  /** 响应状态。 */
  @TableField("response_status")
  private String responseStatus;
}

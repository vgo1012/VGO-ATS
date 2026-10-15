package com.ezaigc.hr.talent.domain.entity;

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
@TableName("talent_identity_conflict")
public class IdentityConflict extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("intake_id")
  private String intakeId;
  /** 待合并候选人 A 的 ID。 */
  @TableField("candidate_id_a")
  private String candidateIdA;
  /** 待合并候选人 B 的 ID。 */
  @TableField("candidate_id_b")
  private String candidateIdB;
  /** 冲突类型。 */
  @TableField("conflict_type")
  private String conflictType;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 解决结果。 */
  @TableField("resolution")
  private String resolution;
  @TableField("chosen_candidate_id")
  private String chosenCandidateId;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 解决操作人用户 ID。 */
  @TableField("resolved_by_user_id")
  private String resolvedByUserId;
  /** 解决时间。 */
  @TableField("resolved_at")
  private Instant resolvedAt;

}

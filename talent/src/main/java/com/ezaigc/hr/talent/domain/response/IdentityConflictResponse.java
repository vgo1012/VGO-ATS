package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IdentityConflictResponse {
  /** 主键 ID。 */
  private String id;
  private String intakeId;
  /** 待合并候选人 A 的 ID。 */
  private String candidateIdA;
  /** 待合并候选人 B 的 ID。 */
  private String candidateIdB;
  /** 冲突类型。 */
  private String conflictType;
  /** 当前状态。 */
  private String status;
  /** 解决结果。 */
  private String resolution;
  private String chosenCandidateId;
  /** 操作原因。 */
  private String reason;
  /** 解决操作人用户 ID。 */
  private String resolvedByUserId;
  /** 解决时间。 */
  private Instant resolvedAt;
  /** 创建时间。 */
  private Instant createTime;
}

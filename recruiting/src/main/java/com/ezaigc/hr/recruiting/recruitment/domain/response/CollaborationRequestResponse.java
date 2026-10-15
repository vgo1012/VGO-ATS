package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CollaborationRequestResponse {
  /** 主键 ID。 */
  private String id;
  private String lockId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 申请人用户 ID。 */
  private String requesterUserId;
  /** 申请人部门 ID。 */
  private String requesterDepartmentId;
  private String lockOwnerUserId;
  /** 请求原因。 */
  private String requestReason;
  /** 当前状态。 */
  private String status;
  /** 生效记录唯一标记。 */
  private String activeMarker;
  /** 解决操作人用户 ID。 */
  private String resolvedByUserId;
  /** 解决原因。 */
  private String resolutionReason;
  /** 创建时间。 */
  private Instant createTime;
  /** 解决时间。 */
  private Instant resolvedAt;
}

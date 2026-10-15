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
@TableName("recruitment_engagement_collaboration_request")
public class CollaborationRequest extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("lock_id")
  private String lockId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 申请人用户 ID。 */
  @TableField("requester_user_id")
  private String requesterUserId;
  /** 申请人部门 ID。 */
  @TableField("requester_department_id")
  private String requesterDepartmentId;
  @TableField("lock_owner_user_id")
  private String lockOwnerUserId;
  /** 请求原因。 */
  @TableField("request_reason")
  private String requestReason;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 生效记录唯一标记。 */
  @TableField("active_marker")
  private String activeMarker;
  /** 解决操作人用户 ID。 */
  @TableField("resolved_by_user_id")
  private String resolvedByUserId;
  /** 解决原因。 */
  @TableField("resolution_reason")
  private String resolutionReason;

  /** 解决时间。 */
  @TableField("resolved_at")
  private Instant resolvedAt;
}

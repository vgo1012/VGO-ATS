package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditEventResponse {
  /** 主键 ID。 */
  private String id;
  /** 事件类型。 */
  private String eventType;
  /** 实际操作人用户 ID。 */
  private String actorUserId;
  /** 目标类型。 */
  private String targetType;
  /** 目标记录 ID。 */
  private String targetId;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 部门 ID。 */
  private String departmentId;
  /** 操作原因。 */
  private String reason;
  /** 变更前数据快照（JSON）。 */
  private String beforeSnapshot;
  /** 变更后数据快照（JSON）。 */
  private String afterSnapshot;
  /** 来源业务模块。 */
  private String sourceModule;
  /** 事件发生时间。 */
  private Instant occurredAt;
}

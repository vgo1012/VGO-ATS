package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewScheduleHistoryResponse {
  /** 主键 ID。 */
  private String id;
  /** 面试 ID。 */
  private String interviewId;
  /** 操作类型。 */
  private String actionType;
  /** 变更前数据快照（JSON）。 */
  private String beforeSnapshot;
  /** 变更后数据快照（JSON）。 */
  private String afterSnapshot;
  /** 操作原因。 */
  private String reason;
  /** 操作人用户 ID。 */
  private String operatedByUserId;
  /** 事件发生时间。 */
  private Instant occurredAt;
}

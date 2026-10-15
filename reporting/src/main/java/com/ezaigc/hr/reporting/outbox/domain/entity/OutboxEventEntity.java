package com.ezaigc.hr.reporting.outbox.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 不可变 Outbox 信封；发布状态、领取次数和失败历史由绑定的共享任务持有。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_outbox_event")
public class OutboxEventEntity extends StandardVersionedPersistenceFields {
  /** 同时作为独立发布任务 ID。 */
  @TableId("id") private String id;
  @TableField("work_task_id") private String workTaskId;
  @TableField("work_task_type") private String workTaskType;
  @TableField("event_type") private String eventType;
  @TableField("event_version") private int eventVersion;
  @TableField("aggregate_type") private String aggregateType;
  @TableField("aggregate_id") private String aggregateId;
  @TableField("occurred_at") private Instant occurredAt;
  @TableField("trace_id") private String traceId;
  @TableField("actor_user_id") private String actorUserId;
  @TableField("owner_user_id") private String ownerUserId;
  @TableField("owner_department_id") private String ownerDepartmentId;
}

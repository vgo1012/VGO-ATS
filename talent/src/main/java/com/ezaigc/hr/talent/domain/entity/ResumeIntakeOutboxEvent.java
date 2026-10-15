package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 与收件处理状态同事务保存的不可变通知，以及可恢复的发布状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_intake_outbox")
public class ResumeIntakeOutboxEvent extends StandardVersionedPersistenceFields {
  @TableId("id") private String id;
  @TableField("processing_id") private String processingId;
  @TableField("processing_version") private long processingVersion;
  @TableField("event_type") private String eventType;
  @TableField("event_version") private int eventVersion;
  @TableField("task_type") private String taskType;
  @TableField("occurred_at") private Instant occurredAt;
  @TableField("trace_id") private String traceId;
  @TableField("actor_user_id") private String actorUserId;
  @TableField("status") private String status;
  @TableField("attempt_count") private int attemptCount;
  @TableField("max_attempts") private int maxAttempts;
  @TableField("next_retry_at") private Instant nextRetryAt;
  @TableField("lease_owner") private String leaseOwner;
  @TableField("lease_expires_at") private Instant leaseExpiresAt;
  @TableField("published_at") private Instant publishedAt;
  @TableField("failure_code") private String failureCode;
}

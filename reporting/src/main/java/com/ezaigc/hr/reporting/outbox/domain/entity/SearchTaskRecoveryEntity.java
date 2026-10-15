package com.ezaigc.hr.reporting.outbox.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 不可变人工恢复关联；原任务仍保留失败状态和完整尝试历史。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_search_task_recovery")
public class SearchTaskRecoveryEntity extends StandardVersionedPersistenceFields {
  /** 新恢复任务 ID，同时作为恢复记录主键。 */
  @TableId("id") private String id;
  /** 被恢复的原失败任务，每项只能直接创建一次恢复。 */
  @TableField("original_task_id") private String originalTaskId;
  /** 发布恢复重用原信封；索引恢复绑定新工作对应的新信封。 */
  @TableField("outbox_event_id") private String outboxEventId;
  /** 管理员填写的恢复原因。 */
  @TableField("reason") private String reason;
  /** 实际创建恢复的管理员。 */
  @TableField("requested_by_user_id") private String requestedByUserId;
}

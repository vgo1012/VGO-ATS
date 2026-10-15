package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("identity_handover_batch")
public class HandoverBatchRecord extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 来源用户 ID。 */
  @TableField("source_user_id")
  private String sourceUserId;
  /** 目标用户 ID。 */
  @TableField("target_user_id")
  private String targetUserId;
  /** 选择模式。 */
  @TableField("selection_mode")
  private String selectionMode;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 总记录数。 */
  @TableField("total_count")
  private int totalCount;
  /** 成功记录数。 */
  @TableField("succeeded_count")
  private int succeededCount;
  /** 失败记录数。 */
  @TableField("failed_count")
  private int failedCount;
  /** 未解决记录数。 */
  @TableField("unresolved_count")
  private int unresolvedCount;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 请求发起人用户 ID。 */
  @TableField("requested_by_user_id")
  private String requestedByUserId;
  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 完成时间。 */
  @TableField("completed_at")
  private Instant completedAt;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;
  /** 幂等键。 */
  @TableField("idempotency_key")
  private String idempotencyKey;

}

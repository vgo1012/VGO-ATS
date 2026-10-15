package com.ezaigc.hr.intelligence.search.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 唯一显式创建意图、保守请求 pin 和不可逆删除意图的持久账本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("search_index_resource")
public class SearchIndexResource extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 精确物理目标名称，唯一创建意图不可重开。 */
  @TableField("index_name")
  private String indexName;
  /** 所属搜索业务别名或本地适配器作用域。 */
  @TableField("scope_name")
  private String scopeName;
  /** 资源来源类型编码。 */
  @TableField("resource_kind")
  private String resourceKind;
  /** 原搜索重建业务记录 ID。 */
  @TableField("run_id")
  private String runId;
  /** 原共享异步任务 ID。 */
  @TableField("task_id")
  private String taskId;
  /** 精确创建批次，历史批次不复用。 */
  @TableField("attempt_count")
  private int attemptCount;
  /** 持久资源生命周期编码。 */
  @TableField("status")
  private String status;
  /** 只有远端 marker 归属精确确认后才可自动回收。 */
  @TableField("managed")
  private boolean managed;
  /** 明确创建完成或人工核对完成时间。 */
  @TableField("ready_at")
  private Instant readyAt;
  /** 数据库时钟计算的保守请求保护期限。 */
  @TableField("pinned_until")
  private Instant pinnedUntil;
  /** 持久删除意图登记时间。 */
  @TableField("retired_at")
  private Instant retiredAt;
  /** 首次人工核对原因，不可覆盖。 */
  @TableField("reconciliation_reason")
  private String reconciliationReason;
  /** 首次人工核对超级管理员 ID。 */
  @TableField("reconciled_by")
  private String reconciledBy;
  /** 首次人工核对审计时间。 */
  @TableField("reconciled_at")
  private Instant reconciledAt;
  /** 管理员确认原发送者及在途请求已确定停止。 */
  @TableField("original_request_terminated")
  private boolean originalRequestTerminated;
}

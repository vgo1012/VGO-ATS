package com.ezaigc.hr.intelligence.search.domain.entity;

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
@TableName("search_index_rebuild_run")
public class RebuildRun extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 异步任务 ID。 */
  @TableField("task_id")
  private String taskId;
  /** 目标搜索索引名称。 */
  @TableField("target_index")
  private String targetIndex;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 预计数量。 */
  @TableField("projected_count")
  private long projectedCount;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 请求发起人用户 ID。 */
  @TableField("requested_by_user_id")
  private String requestedByUserId;
  /** 开始处理时间。 */
  @TableField("started_at")
  private Instant startedAt;
  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;
}

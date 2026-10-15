package com.ezaigc.hr.intelligence.search.domain.entity;

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
@TableName("search_projection_checkpoint")
public class ProjectionCheckpoint extends StandardPersistenceFields {
  /** 搜索投影名称。 */
  @TableId(value = "projection_name", type = IdType.INPUT)
  private String projectionName;
  /** observed 行是游标绑定；APPROVED_INDEX 独立行是批准目标，空值表示查询不可用。 */
  @TableField("index_name")
  private String indexName;
  /** 最近已投影记录的创建时间。 */
  @TableField("last_created_at")
  private Instant lastCreatedAt;
  /** 最近已投影快照 ID。 */
  @TableField("last_snapshot_id")
  private String lastSnapshotId;
  /** 预计数量。 */
  @TableField("projected_count")
  private long projectedCount;
  /** observed 行是进度 CAS 版本；APPROVED_INDEX 独立行是单调批准代际。 */
  @Version
  @TableField("version")
  private long version;
}

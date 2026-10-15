package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 一个指标来源及通道的持久扫描进度。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_metric_capture_checkpoint")
public class MetricCaptureCheckpointEntity extends StandardVersionedPersistenceFields {
  @TableId("checkpoint_name")
  private String checkpointName;
  @TableField("source_code")
  private String sourceCode;
  @TableField("lane")
  private String lane;
  @TableField("last_source_id")
  private String lastSourceId;
  @TableField("cycle_upper_id")
  private String cycleUpperId;
  @TableField("cycle_started_at")
  private Instant cycleStartedAt;
  @TableField("last_completed_at")
  private Instant lastCompletedAt;
}

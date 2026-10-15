package com.ezaigc.hr.reporting.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审计投影采集检查点。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_audit_capture_checkpoint")
public class AuditCaptureCheckpointEntity extends StandardVersionedPersistenceFields {

  /** 来源与通道组成的稳定检查点名称。 */
  @TableId("checkpoint_name")
  private String checkpointName;

  /** 审计来源编码。 */
  @TableField("source_code")
  private String sourceCode;

  /** 采集通道。 */
  @TableField("lane")
  private String lane;

  /** 上次完成扫描的来源记录 ID。 */
  @TableField("last_source_id")
  private String lastSourceId;

  /** 累计扫描记录数。 */
  @TableField("scanned_count")
  private long scannedCount;
}

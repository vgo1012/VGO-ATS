package com.ezaigc.hr.talent.domain.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("talent_resume_lifecycle_run")
public class ResumeLifecycleRun extends StandardVersionedPersistenceFields {
  @TableId(value="id",type=IdType.INPUT) private String id;
  @TableField("settlement_date")
  private LocalDate settlementDate;
  @TableField("calendar_generation")
  private long calendarGeneration;
  @TableField("status")
  private String status;
  @TableField("scanned_count")
  private int scannedCount;
  @TableField("succeeded_count")
  private int succeededCount;
  @TableField("shared_count")
  private int sharedCount;
  @TableField("blocked_count")
  private int blockedCount;
  @TableField("failed_count")
  private int failedCount;
  @TableField("error_code")
  private String errorCode;
  @TableField("started_at")
  private Instant startedAt;
  @TableField("finished_at")
  private Instant finishedAt;
}

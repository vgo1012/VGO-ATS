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
@TableName("talent_resume_lifecycle_runtime")
public class ResumeLifecycleRuntime extends StandardVersionedPersistenceFields {
  @TableId(value="id",type=IdType.INPUT) private String id;
  @TableField("activated_at")
  private Instant activatedAt;
  @TableField("calendar_generation")
  private long calendarGeneration;
  @TableField("completed_generation")
  private long completedGeneration;
  @TableField("last_settlement_date")
  private LocalDate lastSettlementDate;
  @TableField("lease_owner")
  private String leaseOwner;
  @TableField("lease_until")
  private Instant leaseUntil;
}

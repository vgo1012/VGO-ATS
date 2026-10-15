package com.ezaigc.hr.reporting.monthlyreport.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_monthly_report")
public class MonthlyReportEntity extends StandardVersionedPersistenceFields {
    @TableId("id") private String id;
    @TableField(value = "report_month") private String reportMonth;
    @TableField(value = "data_as_of") private LocalDate dataAsOf;
    @TableField(value = "status") private String status;
    @TableField(value = "task_id") private String taskId;
    @TableField(value = "phase") private String phase;
    @TableField(value = "owner_user_id") private String ownerUserId;
    @TableField(value = "owner_department_id") private String ownerDepartmentId;
    @TableField(value = "idempotency_key") private String idempotencyKey;
    @TableField(value = "request_fingerprint") private String requestFingerprint;
    @TableField(value = "generation_key") private String generationKey;
    @TableField(value = "generation_fingerprint") private String generationFingerprint;
    @TableField(value = "management_remarks", updateStrategy = FieldStrategy.ALWAYS) private String managementRemarks;
    @TableField(value = "snapshot_json", updateStrategy = FieldStrategy.ALWAYS) private String snapshotJson;
    @TableField(value = "offer_count") private Integer offerCount;
    @TableField(value = "total_score") private BigDecimal totalScore;
    @TableField(value = "error_code", updateStrategy = FieldStrategy.ALWAYS) private String errorCode;
    @TableField(value = "error_detail", updateStrategy = FieldStrategy.ALWAYS) private String errorDetail;
    @TableField(value = "expires_at") private Instant expiresAt;
}

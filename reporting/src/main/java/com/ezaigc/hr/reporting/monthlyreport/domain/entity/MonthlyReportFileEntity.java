package com.ezaigc.hr.reporting.monthlyreport.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reporting_monthly_report_file")
public class MonthlyReportFileEntity extends StandardVersionedPersistenceFields {
    @TableId("id") private String id;
    @TableField(value = "report_id") private String reportId;
    @TableField(value = "file_kind") private String fileKind;
    @TableField(value = "file_name") private String fileName;
    @TableField(value = "object_key") private String objectKey;
    @TableField(value = "content_hash") private String contentHash;
    @TableField(value = "size_bytes") private long sizeBytes;
    @TableField(value = "expires_at") private Instant expiresAt;
    @TableField(value = "deleted") private boolean deleted;
}

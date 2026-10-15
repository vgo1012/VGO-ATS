package com.ezaigc.hr.recruiting.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_external_calendar_attempt")
public class InterviewExternalCalendarAttempt extends StandardVersionedPersistenceFields {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("projection_id")
    private String projectionId;
    @TableField("operation_revision")
    private long operationRevision;
    @TableField("operation_type")
    private String operationType;
    @TableField("external_type")
    private String externalType;
    @TableField("status")
    private String status;
    @TableField(value = "external_object_id", updateStrategy = FieldStrategy.ALWAYS)
    private String externalObjectId;
    @TableField(value = "error_code", updateStrategy = FieldStrategy.ALWAYS)
    private String errorCode;
    @TableField(value = "error_detail", updateStrategy = FieldStrategy.ALWAYS)
    private String errorDetail;
    @TableField("occurred_at")
    private Instant occurredAt;
}

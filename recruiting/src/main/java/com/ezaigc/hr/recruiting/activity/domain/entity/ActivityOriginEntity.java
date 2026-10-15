package com.ezaigc.hr.recruiting.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruitment_activity_origin")
public class ActivityOriginEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  @TableField("application_id") private String applicationId;
  @TableField("source_type") private String sourceType;
  @TableField("source_id") private String sourceId;
  @TableField("action") private String action;
  @TableField("operation_id") private String operationId;
}

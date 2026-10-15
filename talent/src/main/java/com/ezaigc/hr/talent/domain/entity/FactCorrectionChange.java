package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 简历人工纠错中的一项字段级变更。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_fact_correction_change")
public class FactCorrectionChange extends StandardVersionedPersistenceFields {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("correction_id")
  private String correctionId;

  @TableField("json_pointer")
  private String jsonPointer;

  /** 原值 JSON；排除自动字符串输出，避免简历内容进入诊断日志。 */
  @TableField("old_value_json")
  @ToString.Exclude
  private String oldValueJson;

  /** 新值 JSON；排除自动字符串输出，避免简历内容进入诊断日志。 */
  @TableField("new_value_json")
  @ToString.Exclude
  private String newValueJson;

  @TableField("source_type")
  private String sourceType;
}

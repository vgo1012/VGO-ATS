package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位版本的当前评分关联，解除后保留版本防止并发覆盖与重复迁移恢复旧值。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_jd_plan_binding")
public class ScoringBinding extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("plan_version_id")
  private String planVersionId;
}

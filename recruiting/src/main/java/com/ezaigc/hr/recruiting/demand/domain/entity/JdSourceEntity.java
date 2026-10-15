package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** JD 版本来源，每次手动录入和每封原始邮件分别留痕。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_jd_source")
public class JdSourceEntity extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  @TableField("jd_version_id")
  private String jdVersionId;
  @TableField("source_type")
  private String sourceType;
  @TableField("source_key")
  private String sourceKey;
  @TableField("mail_source_id")
  private String mailSourceId;
  @TableField("owner_user_id")
  private String ownerUserId;
}

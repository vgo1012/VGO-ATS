package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_candidate_identity")
public class Identity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 身份类型。 */
  @TableField("identity_type")
  private String identityType;
  /** 规范化值哈希。 */
  @TableField("normalized_value_hash")
  private String normalizedValueHash;
  /** 值最近后四位。 */
  @TableField("value_last_four")
  private String valueLastFour;
  /** 是否已验证。 */
  @TableField("verified")
  private boolean verified;

}

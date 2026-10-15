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
@TableName("talent_resume")
public class Resume extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 当前业务版本号。 */
  @TableField("current_version_no")
  private int currentVersionNo;

}

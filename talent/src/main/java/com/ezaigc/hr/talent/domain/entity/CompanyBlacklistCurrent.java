package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_company_blacklist_current")
public class CompanyBlacklistCurrent extends StandardVersionedPersistenceFields {
  /** 候选人 ID。 */
  @TableId(value = "candidate_id", type = IdType.INPUT)
  private String candidateId;

  /** 业务记录 ID。 */
  @TableField("record_id")
  private String recordId;
  /** 是否处于启用状态。 */
  @TableField("active")
  private boolean active;

}

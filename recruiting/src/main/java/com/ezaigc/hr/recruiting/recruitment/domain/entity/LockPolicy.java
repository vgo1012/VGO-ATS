package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_engagement_lock_policy")
public class LockPolicy extends StandardPersistenceFields {
  /** 策略键。 */
  @TableId(value = "policy_key", type = IdType.INPUT)
  private String policyKey;

  /** 时长天数。 */
  @TableField("duration_days")
  private int durationDays;

  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}

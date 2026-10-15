package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_resume_simple_fact")
public class SimpleFact extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 事实快照 ID。 */
  @TableField("fact_snapshot_id")
  private String factSnapshotId;
  /** 事实类型。 */
  @TableField("fact_type")
  private String factType;
  /** 任务条目业务键。 */
  @TableField("item_key")
  private String itemKey;
  /** 名称值。 */
  @TableField("name_value")
  private String nameValue;
  /** 等级值。 */
  @TableField("level_value")
  private String levelValue;
  /** 签发方值。 */
  @TableField("issuer_value")
  private String issuerValue;
  /** 开始日期。 */
  @TableField("start_date")
  private LocalDate startDate;
  /** 结束日期。 */
  @TableField("end_date")
  private LocalDate endDate;
}

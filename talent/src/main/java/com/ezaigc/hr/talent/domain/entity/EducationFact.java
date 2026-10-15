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
@TableName("talent_resume_education_fact")
public class EducationFact extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 事实快照 ID。 */
  @TableField("fact_snapshot_id")
  private String factSnapshotId;
  /** 任务条目业务键。 */
  @TableField("item_key")
  private String itemKey;
  /** 学校名称。 */
  @TableField("school")
  private String school;
  /** 学历或学位名称。 */
  @TableField("degree_name")
  private String degreeName;
  /** 专业名称。 */
  @TableField("major")
  private String major;
  /** 开始日期。 */
  @TableField("start_date")
  private LocalDate startDate;
  /** 结束日期。 */
  @TableField("end_date")
  private LocalDate endDate;
  /** 学习形式。 */
  @TableField("learning_mode")
  private String learningMode;
}

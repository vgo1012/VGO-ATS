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
@TableName("talent_resume_project_fact")
public class ProjectFact extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 事实快照 ID。 */
  @TableField("fact_snapshot_id")
  private String factSnapshotId;
  /** 任务条目业务键。 */
  @TableField("item_key")
  private String itemKey;
  /** 项目名称。 */
  @TableField("project_name")
  private String projectName;
  /** 角色名称。 */
  @TableField("role_name")
  private String roleName;
  /** 开始日期。 */
  @TableField("start_date")
  private LocalDate startDate;
  /** 结束日期。 */
  @TableField("end_date")
  private LocalDate endDate;
  /** 描述文本。 */
  @TableField("description_text")
  private String descriptionText;
  /** 技术栈列表（JSON）。 */
  @TableField("technologies_json")
  private String technologiesJson;
  /** 工作成果列表（JSON）。 */
  @TableField("achievements_json")
  private String achievementsJson;
}

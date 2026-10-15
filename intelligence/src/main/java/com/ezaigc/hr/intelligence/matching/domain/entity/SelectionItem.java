package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("matching_candidate_selection_item")
public class SelectionItem extends StandardVersionedPersistenceFields {
  /** 候选人选择快照 ID。 */
  @TableField("selection_snapshot_id")
  private String selectionSnapshotId;
  /** 顺序号。 */
  @TableField("sequence_no")
  private int sequenceNo;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 简历事实快照 ID。 */
  @TableField("resume_fact_snapshot_id")
  private String resumeFactSnapshotId;
}

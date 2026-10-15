package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_candidate_selection_snapshot")
public class CandidateSelectionSnapshot extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 请求发起人用户 ID。 */
  @TableField("requested_by_user_id")
  private String requestedByUserId;
  /** 条目总数。 */
  @TableField("item_count")
  private Integer itemCount;
  /** 候选范围选择哈希。 */
  @TableField("selection_hash")
  private String selectionHash;
}

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
@TableName("matching_highlight")
public class MatchingHighlight extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("base_evaluation_id")
  private String baseEvaluationId;
  /** 评分维度编码。 */
  @TableField("dimension_code")
  private String dimensionCode;
  /** 匹配亮点文本。 */
  @TableField("highlight_text")
  private String highlightText;
  /** 顺序号。 */
  @TableField("sequence_no")
  private Integer sequenceNo;
}

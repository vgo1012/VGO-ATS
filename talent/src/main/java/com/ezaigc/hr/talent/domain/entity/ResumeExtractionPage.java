package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_extraction_page")
public class ResumeExtractionPage extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 文本抽取任务 ID。 */
  @TableField("extraction_run_id")
  private String extractionRunId;
  /** 页码。 */
  @TableField("page_no")
  private int pageNo;
  /** 路径。 */
  @TableField("route")
  private String route;
  /** 抽取后的文本内容。 */
  @TableField("extracted_text")
  @ToString.Exclude
  private String extractedText;
  /** 字符数量。 */
  @TableField("character_count")
  private int characterCount;
  /** 可打印字符占比。 */
  @TableField("printable_ratio")
  private BigDecimal printableRatio;
  /** 数据质量评分。 */
  @TableField("quality_score")
  private BigDecimal qualityScore;
  /** 内容哈希值。 */
  @TableField("content_hash")
  private String contentHash;
}

package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_resume_extraction_run")
public class ExtractionRun extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 异步任务 ID。 */
  @TableField("task_id")
  private String taskId;
  /** 抽取器版本。 */
  @TableField("extractor_version")
  private String extractorVersion;
  /** 路径。 */
  @TableField("route")
  private String route;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 页数。 */
  @TableField("page_count")
  private int pageCount;
  /** 成功分页结果。 */
  @TableField("successful_pages")
  private int successfulPages;
  /** 降级分页结果。 */
  @TableField("fallback_pages")
  private int fallbackPages;
  /** 可打印字符占比。 */
  @TableField("printable_ratio")
  private BigDecimal printableRatio;
  /** 文本覆盖率。 */
  @TableField("coverage_ratio")
  private BigDecimal coverageRatio;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;

  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;
}

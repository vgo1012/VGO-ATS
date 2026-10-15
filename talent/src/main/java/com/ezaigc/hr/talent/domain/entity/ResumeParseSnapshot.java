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
@TableName("talent_resume_parse_snapshot")
public class ResumeParseSnapshot extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 文本抽取任务 ID。 */
  @TableField("extraction_run_id")
  private String extractionRunId;
  /** 模型运行记录 ID。 */
  @TableField("model_run_id")
  private String modelRunId;
  /** 数据结构版本号。 */
  @TableField("schema_version")
  private String schemaVersion;
  /** 提示词模板版本。 */
  @TableField("prompt_version")
  private String promptVersion;
  /** 解析结果缓存键。 */
  @TableField("cache_key")
  private String cacheKey;
}

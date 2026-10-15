package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_ai_model_run")
public class ResumeModelRun extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 文本抽取任务 ID。 */
  @TableField("extraction_run_id")
  private String extractionRunId;
  /** 数据结构版本号。 */
  @TableField("schema_version")
  private String schemaVersion;
  /** 提示词模板版本。 */
  @TableField("prompt_version")
  private String promptVersion;
  /** 模型服务提供方。 */
  @TableField("model_provider")
  private String modelProvider;
  /** 模型版本标识。 */
  @TableField("model_version")
  private String modelVersion;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 数据校验状态。 */
  @TableField("validation_status")
  private String validationStatus;
  /** 模型原始输出的对象存储键。 */
  @TableField("raw_output_object_key")
  private String rawOutputObjectKey;
  /** 模型原始输出哈希。 */
  @TableField("raw_output_hash")
  private String rawOutputHash;
  /** 模型原始输出大小（字节）。 */
  @TableField("raw_output_size")
  private Long rawOutputSize;
  /** 加密版本。 */
  @TableField("encryption_version")
  private String encryptionVersion;
  /** 模型输入 Token 数。 */
  @TableField("input_tokens")
  private int inputTokens;
  /** 模型输出 Token 数。 */
  @TableField("output_tokens")
  private int outputTokens;
  /** 处理耗时（毫秒）。 */
  @TableField("duration_millis")
  private long durationMillis;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;

  /** 完成处理时间。 */
  @TableField("finished_at")
  private Instant finishedAt;
}

package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 按文件内容共享的简历收件解析处理。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_resume_intake_processing")
public class ResumeIntakeProcessing extends StandardPersistenceFields {
  /** 共享解析处理 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 原文件 SHA-256，全局唯一去重键。 */
  @TableField("content_sha256")
  private String contentSha256;

  /** 全零表示按内容共享；显式重新解析使用独立处理 ID 隔离结果。 */
  @TableField("processing_scope_key")
  private String processingScopeKey;

  /** 首次安全上传的原始文件名。 */
  @TableField("original_file_name")
  private String originalFileName;

  /** 文件媒体类型。 */
  @TableField("media_type")
  private String mediaType;

  /** 安全扫描通过后的文件存储键。 */
  @TableField("object_key")
  private String objectKey;

  /** 共享解析状态。 */
  @TableField("status")
  private String status;

  /** 累计领取次数，人工重试不清零。 */
  @TableField("attempt_count")
  private int attemptCount;

  /** 当前最大累计尝试次数。 */
  @TableField("max_attempts")
  private int maxAttempts;

  /** 退避后最早允许重试的时间。 */
  @TableField("next_retry_at")
  private Instant nextRetryAt;

  /** 每次领取生成的唯一租约标识。 */
  @TableField("lease_owner")
  private String leaseOwner;

  /** 租约到期时间，过期结果不得写回。 */
  @TableField("lease_expires_at")
  private Instant leaseExpiresAt;

  /** 逐页文本及模型元数据的已校验来源 JSON。 */
  @TableField("extracted_pages_json")
  private String extractedPagesJson;

  /** 通过结构和引用证据校验的简历事实 JSON。 */
  @TableField("parsed_facts_json")
  private String parsedFactsJson;

  /** 模型供应方。 */
  @TableField("model_provider")
  private String modelProvider;

  /** 模型版本。 */
  @TableField("model_version")
  private String modelVersion;

  /** 加密模型原始输出的归档键。 */
  @TableField("raw_output_object_key")
  private String rawOutputObjectKey;

  /** 原始模型输出 SHA-256。 */
  @TableField("raw_output_hash")
  private String rawOutputHash;

  /** 归档原始输出字节数。 */
  @TableField("raw_output_size")
  private Long rawOutputSize;

  /** 原始输出归档加密版本。 */
  @TableField("encryption_version")
  private String encryptionVersion;

  /** 可公开展示的稳定失败编码。 */
  @TableField("failure_code")
  private String failureCode;

  /** 技术乐观锁版本号。 */
  @Version
  @TableField("version")
  private long version;
}

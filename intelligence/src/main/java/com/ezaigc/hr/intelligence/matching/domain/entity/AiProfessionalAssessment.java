package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 一对冻结 JD/简历事实快照的可复用 AI 专业评分。 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("matching_ai_professional_assessment")
public class AiProfessionalAssessment extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("cache_key")
  private String cacheKey;
  @TableField("jd_fact_snapshot_id")
  private String jdFactSnapshotId;
  @TableField("resume_fact_snapshot_id")
  private String resumeFactSnapshotId;
  @TableField("prompt_version")
  private String promptVersion;
  @TableField("schema_version")
  private String schemaVersion;
  @TableField("provider")
  private String provider;
  @TableField("model")
  private String model;
  @TableField("evaluated_on")
  private LocalDate evaluatedOn;
  @TableField("status")
  private String status;
  @TableField("claim_token")
  private String claimToken;
  @TableField("lease_until")
  private Instant leaseUntil;
  @TableField("result_json")
  private String resultJson;
  @TableField("input_tokens")
  private int inputTokens;
  @TableField("output_tokens")
  private int outputTokens;
  @TableField("duration_millis")
  private long durationMillis;
  @TableField("error_code")
  private String errorCode;
  @TableField("deleted")
  private boolean deleted;
}

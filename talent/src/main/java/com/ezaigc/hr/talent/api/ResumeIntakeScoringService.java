package com.ezaigc.hr.talent.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.LocalDate;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** 收件处理所需的评分能力；实现由 intelligence 提供，人才模块不读取评分表。 */
public interface ResumeIntakeScoringService {
  String TASK_TYPE = "MATCH_RUN";

  ScoringTaskRef enqueue(String jdVersionId, String resumeVersionId, String requestedByUserId);

  /** 用户显式重新选择 JD 时强制创建一轮新评分；operationKey 只用于该重评项的幂等回放。 */
  ScoringTaskRef enqueueRescore(
      String jdVersionId, String resumeVersionId, String requestedByUserId, String operationKey);

  /** 读取旧公司默认评分冻结的精确简历事实快照，用于阻止未变更事实的重复重算。 */
  String scoredFactSnapshotId(
      String runId, String jdVersionId, String resumeVersionId, String requestedByUserId);

  default ScoringTaskRef enqueue(String jdVersionId, String resumeVersionId,
      String requestedByUserId, PrecomputedAssessment assessment) {
    return enqueue(jdVersionId, resumeVersionId, requestedByUserId);
  }

  /** 联合调用前冻结有权限的 JD 专业事实；不可用时返回 {@code null} 并退回独立解析。 */
  default ScoringPreparation prepare(String jdVersionId, String requestedByUserId) {
    return null;
  }

  ScoringTaskRef get(String runId, String requestedByUserId);

  ScoringTaskRef retry(String runId, String requestedByUserId);

  @Value
  @Accessors(fluent = true)
  class ScoringTaskRef {
    String taskId;
    String runId;
    String status;
    String errorCode;
    boolean retryable;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class ScoringPreparation {
    String jdVersionId;
    String jdFactSnapshotId;
    @ToString.Exclude String jdFactsJson;
    String promptVersion;
    String schemaVersion;
    LocalDate evaluatedOn;
  }

  @Value
  @Accessors(fluent = true)
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  class PrecomputedAssessment {
    String jdFactSnapshotId;
    String promptVersion;
    String schemaVersion;
    LocalDate evaluatedOn;
    String provider;
    String modelVersion;
    @ToString.Exclude String resultJson;
    int inputTokens;
    int outputTokens;
    long durationMillis;
  }
}

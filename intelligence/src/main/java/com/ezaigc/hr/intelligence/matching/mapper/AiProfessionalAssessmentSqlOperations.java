package com.ezaigc.hr.intelligence.matching.mapper;

import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import java.time.Instant;

/** AI 专业评分缓存的原子领取与终态写入操作。 */
public interface AiProfessionalAssessmentSqlOperations {
  AiProfessionalAssessment findByCacheKey(String cacheKey);

  int claim(
      AiProfessionalAssessment assessment,
      Instant now,
      String actorId,
      String processingStatus,
      String failedStatus);

  int complete(
      String id,
      String claimToken,
      String resultJson,
      String provider,
      String model,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now,
      String actorId,
      String succeededStatus,
      String processingStatus);

  int fail(
      String id,
      String claimToken,
      String errorCode,
      Instant now,
      String actorId,
      String failedStatus,
      String processingStatus);
}

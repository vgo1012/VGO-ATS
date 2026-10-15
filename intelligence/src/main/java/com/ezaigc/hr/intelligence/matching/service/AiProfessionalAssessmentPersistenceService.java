package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import java.time.Instant;

/** 可复用 AI 专业评分的短事务持久化边界。 */
public interface AiProfessionalAssessmentPersistenceService
    extends IService<AiProfessionalAssessment> {
  AiProfessionalAssessment findByCacheKey(String cacheKey);

  boolean claim(AiProfessionalAssessment assessment, Instant now);

  boolean complete(
      String id,
      String claimToken,
      String resultJson,
      String provider,
      String model,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now);

  boolean fail(String id, String claimToken, String errorCode, Instant now);
}

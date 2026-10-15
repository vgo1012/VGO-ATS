package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import com.ezaigc.hr.intelligence.matching.enums.AiProfessionalAssessmentStatus;
import com.ezaigc.hr.intelligence.matching.mapper.AiProfessionalAssessmentMapper;
import com.ezaigc.hr.intelligence.matching.service.AiProfessionalAssessmentPersistenceService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** AI 专业评分缓存薄持久化实现。 */
@Service
public class AiProfessionalAssessmentPersistenceServiceImpl
    extends ServiceImpl<AiProfessionalAssessmentMapper, AiProfessionalAssessment>
    implements AiProfessionalAssessmentPersistenceService {
  @Override
  public AiProfessionalAssessment findByCacheKey(String cacheKey) {
    return baseMapper.findByCacheKey(cacheKey);
  }

  @Override
  public boolean claim(AiProfessionalAssessment assessment, Instant now) {
    baseMapper.claim(
        assessment,
        now,
        PlatformActorConstants.SYSTEM_ACTOR_ID,
        AiProfessionalAssessmentStatus.PROCESSING.getCode(),
        AiProfessionalAssessmentStatus.FAILED.getCode());
    AiProfessionalAssessment claimed = baseMapper.findByCacheKey(assessment.getCacheKey());
    return claimed != null
        && AiProfessionalAssessmentStatus.PROCESSING.matches(claimed.getStatus())
        && Objects.equals(assessment.getClaimToken(), claimed.getClaimToken())
        && claimed.getLeaseUntil() != null
        && claimed.getLeaseUntil().isAfter(now);
  }

  @Override
  public boolean complete(
      String id,
      String claimToken,
      String resultJson,
      String provider,
      String model,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now) {
    return baseMapper.complete(
            id,
            claimToken,
            resultJson,
            provider,
            model,
            inputTokens,
            outputTokens,
            durationMillis,
            now,
            PlatformActorConstants.SYSTEM_ACTOR_ID,
            AiProfessionalAssessmentStatus.SUCCEEDED.getCode(),
            AiProfessionalAssessmentStatus.PROCESSING.getCode())
        == 1;
  }

  @Override
  public boolean fail(String id, String claimToken, String errorCode, Instant now) {
    return baseMapper.fail(
            id,
            claimToken,
            errorCode,
            now,
            PlatformActorConstants.SYSTEM_ACTOR_ID,
            AiProfessionalAssessmentStatus.FAILED.getCode(),
            AiProfessionalAssessmentStatus.PROCESSING.getCode())
        == 1;
  }
}

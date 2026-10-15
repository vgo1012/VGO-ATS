package com.ezaigc.hr.intelligence.matching.domain.service;

import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiAssessmentMetadataDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiProfessionalAssessmentDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionMatchStatus;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 仅复用模型原始分；不从技能、岗位名称或年限计算职业评分。 */
@Component
public class AiScoreWeightingEngine {
  @Resource private ControlledCriteriaEvaluator controlledCriteria;

  public MatchEvaluationDTO calculate(JdFactSnapshotData jd, ResumeFactSnapshotData resume,
      WeightProfileVersion weights, List<ControlledCriterion> criteria, Set<String> selected,
      int planMaxBonus, AiProfessionalAssessmentDTO assessment, AiAssessmentMetadataDTO metadata) {
    new ProfessionalWeights(weights.getSkillsWeight(), weights.getRoleWeight(), weights.getYearsWeight(),
        weights.getResponsibilityWeight(), weights.getIndustryProjectWeight());
    Map<String, Integer> proportions = Map.of("CORE_SKILLS", weights.getSkillsWeight(),
        "ROLE_EXPERIENCE", weights.getRoleWeight(), "RELATED_YEARS", weights.getYearsWeight(),
        "RESPONSIBILITY_MATCH", weights.getResponsibilityWeight(),
        "INDUSTRY_AND_PROJECTS", weights.getIndustryProjectWeight());
    List<MatchDimensionResultDTO> dimensions = assessment.getDimensions().stream().map(dimension -> {
      return new MatchDimensionResultDTO(dimension.getCode(), true,
          proportions.get(dimension.getCode()), dimension.getStatus(), dimension.getScore(),
          dimension.getExplanation(), dimension.getHighlights());
    }).toList();
    boolean companyDefault = DefaultScoringConstants.WEIGHT_VERSION_ID.equals(weights.getId());
    BigDecimal professional = companyDefault ? assessment.getProfessionalScore()
        : dimensions.stream().map(item -> item.getScore().multiply(BigDecimal.valueOf(item.getConfiguredWeight())))
            .reduce(BigDecimal.ZERO, BigDecimal::add).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    criteria = PersonalScreeningSettings.merge(criteria, weights);
    if (weights.getScreeningSettingsJson() != null) {
      planMaxBonus = Math.min(30, criteria.stream().filter(item -> ControlledCriterionLevel.PREFER.matches(item.getLevel()))
          .mapToInt(ControlledCriterion::getBonusPoints).sum());
    }
    var conditions = controlledCriteria.evaluate(criteria, resume, selected);
    String tier = conditions.stream().anyMatch(item -> ControlledCriterionLevel.MUST.matches(item.getLevel())
        && CriterionMatchStatus.FAIL.matches(item.getStatus())) ? CriterionMatchStatus.FAIL.getCode()
        : conditions.stream().anyMatch(item -> ControlledCriterionLevel.MUST.matches(item.getLevel())
            && CriterionMatchStatus.UNKNOWN.matches(item.getStatus())) ? CriterionMatchStatus.UNKNOWN.getCode()
            : CriterionMatchStatus.PASS.getCode();
    int cap = Math.min(planMaxBonus, conditions.stream().filter(item -> item.isSelected()
        && ControlledCriterionLevel.PREFER.matches(item.getLevel())).mapToInt(item -> item.getConfiguredBonus()).sum());
    int bonus = Math.min(cap, conditions.stream().filter(item -> item.isSelected()
        && ControlledCriterionLevel.PREFER.matches(item.getLevel()) && CriterionMatchStatus.PASS.matches(item.getStatus()))
        .mapToInt(item -> item.getConfiguredBonus()).sum());
    var result = new MatchEvaluationDTO(jd.getSnapshotId(), resume.getSnapshotId(), resume.getCandidateId(),
        resume.getResumeVersionId(), metadata.getEvaluatedOn(), professional, bonus,
        professional.add(BigDecimal.valueOf(bonus)), tier, cap, dimensions, conditions);
    result.setAlgorithmVersion(AiScoringConstants.ALGORITHM_VERSION);
    result.setAiAssessment(metadata);
    return result;
  }
}

package com.ezaigc.hr.intelligence.matching.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.intelligence.matching.domain.dto.ActiveScoringConfigurationDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchPlanDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.domain.dto.SavePersonalPreferenceDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateMatchPlanDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanDetails;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileDetails;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationCreateMatchPlanRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationPersonalPreferenceRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationUpdateMatchPlanRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationUpdateWeightProfileRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationVersionReasonRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationWeightProfileRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationWeightRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.ActiveScoringConfigurationResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ControlledCriterionPlanResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ControlledCriterionResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPlanDetailsResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPlanResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPlanVersionResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.PersonalPreferenceResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.WeightProfileDetailsResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.WeightProfileResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.WeightProfileVersionResponse;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import org.springframework.stereotype.Component;

/**
 * 匹配配置边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。
 */
@Component
public class MatchingConfigurationAssembler {

    public ProfessionalWeights toDTO(MatchingConfigurationWeightRequest v) {
        return new ProfessionalWeights(
                v.getSkills(),
                v.getRoleExperience(),
                v.getRelatedYears(),
                v.getResponsibilityMatch(),
                v.getIndustryAndProjects());
    }

    public CreateWeightProfileDTO toDTO(MatchingConfigurationWeightProfileRequest v) {
        return new CreateWeightProfileDTO(v.getName(), toDTO(v.getWeights()), v.getCriteria(), v.getDefaultSortMode());
    }

    public UpdateWeightProfileDTO toDTO(MatchingConfigurationUpdateWeightProfileRequest v) {
        return new UpdateWeightProfileDTO(v.getName(), toDTO(v.getWeights()), parse(v.getVersion()), v.getCriteria(), v.getDefaultSortMode());
    }

    public VersionReasonDTO toDTO(MatchingConfigurationVersionReasonRequest v) {
        return new VersionReasonDTO(parse(v.getVersion()), v.getReason());
    }

    public CreateMatchPlanDTO toDTO(MatchingConfigurationCreateMatchPlanRequest v) {
        var d = v.professionalDimensions();
        return new CreateMatchPlanDTO(
                v.getJdVersionId(),
                v.getName(),
                d.isSkills(),
                d.isRoleExperience(),
                d.isRelatedYears(),
                d.isResponsibilityMatch(),
                d.isIndustryAndProjects(),
                v.getCriteria());
    }

    public UpdateMatchPlanDTO toDTO(MatchingConfigurationUpdateMatchPlanRequest v) {
        var d = v.professionalDimensions();
        return new UpdateMatchPlanDTO(
                v.getName(),
                d.isSkills(),
                d.isRoleExperience(),
                d.isRelatedYears(),
                d.isResponsibilityMatch(),
                d.isIndustryAndProjects(),
                v.getCriteria(),
                parse(v.getVersion()));
    }

    public SavePersonalPreferenceDTO toDTO(MatchingConfigurationPersonalPreferenceRequest v) {
        return new SavePersonalPreferenceDTO(v.getWeightProfileVersionId(), v.getSelectedDefinitionIds());
    }

    public WeightProfileDetailsResponse toResponse(WeightProfileDetails v) {
        var p = v.getProfile();
        var x = v.getCurrentVersion();
        return new WeightProfileDetailsResponse(
                new WeightProfileResponse(
                        p.getId(),
                        p.getOwnerUserId(),
                        p.getName(),
                        p.getStatus(),
                        p.getCurrentVersionNo(),
                        format(p.getVersion()),
                        p.getCreateTime(),
                        p.getUpdateTime()),
                new WeightProfileVersionResponse(
                        x.getId(),
                        x.getProfileId(),
                        x.getVersionNo(),
                        x.getSkillsWeight(),
                        x.getRoleWeight(),
                        x.getYearsWeight(),
                        x.getResponsibilityWeight(),
                        x.getIndustryProjectWeight(),
                        x.getCreateId(),
                        x.getCreateTime(), x.getScreeningSettingsJson()));
    }

    public CursorPage<WeightProfileDetailsResponse> toWeightProfileResponses(
            CursorPage<WeightProfileDetails> v) {
        return v.map(this::toResponse);
    }

    public MatchPlanDetailsResponse toResponse(MatchPlanDetails v) {
      if (v == null) {
        return null;
      }
        var p = v.getPlan();
        var x = v.getVersion();
        var c = v.getCriterionPlan();
        return new MatchPlanDetailsResponse(
                new MatchPlanResponse(
                        p.getId(),
                        p.getJdVersionId(),
                        p.getName(),
                        p.getCurrentVersionNo(),
                        p.getCreateId(),
                        p.getCreateTime()),
                new MatchPlanVersionResponse(
                        x.getId(),
                        x.getPlanId(),
                        x.getJdVersionId(),
                        x.getVersionNo(),
                        x.getStatus(),
                        x.getControlledCriterionPlanVersionId(),
                        x.isSkillsEnabled(),
                        x.isRoleEnabled(),
                        x.isYearsEnabled(),
                        x.isResponsibilityEnabled(),
                        x.isIndustryProjectEnabled(),
                        format(x.getVersion()),
                        x.getCreateId(),
                        x.getConfiguredByUserId(),
                        x.getActivatedByUserId(),
                        x.getActivatedAt(),
                        x.getCreateTime(),
                        x.getUpdateTime()),
                new ControlledCriterionPlanResponse(
                        c.getId(),
                        c.getJdVersionId(),
                        c.getVersionNo(),
                        c.getPlanMaxBonusPoints(),
                        c.getCreateId(),
                        c.getCreateTime()),
                v.getCriteria().stream()
                        .map(
                                i ->
                                        new ControlledCriterionResponse(
                                                i.getId(),
                                                i.getPlanVersionId(),
                                                i.getCriterionCode(),
                                                ControlledCriterionCode.fromCode(i.getCriterionCode()).getDesc(),
                                                i.getLevel(),
                                                ControlledCriterionLevel.fromCode(i.getLevel()).getDesc(),
                                                i.getOperator(),
                                                i.getExpectedValueJson(),
                                                i.getBonusPoints(),
                                                i.getReason(),
                                                i.getEvidencePolicy(),
                                                i.isSelectableByHr(),
                                                i.getSequenceNo()))
                        .toList());
    }

    public CursorPage<MatchPlanDetailsResponse> toMatchPlanResponses(CursorPage<MatchPlanDetails> v) {
        return v.map(this::toResponse);
    }

    public PersonalPreferenceResponse toResponse(PersonalPreference v) {
        // 尚未保存个人偏好是正常状态，保留缺省语义，不生成虚假的偏好版本。
        if (v == null) {
            return null;
        }
        return new PersonalPreferenceResponse(
                v.getId(),
                v.getOwnerUserId(),
                v.getPlanVersionId(),
                v.getWeightProfileVersionId(),
                v.getVersionNo(),
                v.getSelectedDefinitionIdsJson(),
                v.getCreateTime());
    }

    public ActiveScoringConfigurationResponse toResponse(ActiveScoringConfigurationDTO v) {
        return new ActiveScoringConfigurationResponse(
                v.getJdVersionId(),
                v.isWorkflowTemplateActive(),
                v.isScoringConfigurationActive(),
                v.isReadyForApplication(),
                toResponse(v.getScoringConfiguration()));
    }
}

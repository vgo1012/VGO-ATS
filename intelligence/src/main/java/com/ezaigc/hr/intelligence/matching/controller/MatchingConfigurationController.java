package com.ezaigc.hr.intelligence.matching.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.intelligence.matching.application.assembler.MatchingConfigurationAssembler;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchingConfigurationApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationCreateMatchPlanRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationPersonalPreferenceRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationUpdateMatchPlanRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationUpdateWeightProfileRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationVersionReasonRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchingConfigurationWeightProfileRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.ActiveScoringConfigurationResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.DefaultScoringResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPlanDetailsResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.PersonalPreferenceResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.WeightProfileDetailsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;

@RestController
@RequestMapping("/api/v1")
public class MatchingConfigurationController {

  @Resource private MatchingConfigurationApplicationManager manager;
  @Resource private MatchingConfigurationAssembler assembler;

  @GetMapping("/scoring-defaults")
  DefaultScoringResponse defaultScoring() {
    return manager.defaultScoring();
  }

  @GetMapping("/weight-profiles")
  CursorPage<WeightProfileDetailsResponse> weightProfiles(
      @RequestParam(defaultValue = "false") boolean mineOnly,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.listWeightProfileResponses(mineOnly, cursor, limit);
  }

  @GetMapping("/weight-profiles/count")
  CursorCount countWeightProfiles(
      @RequestParam(defaultValue = "false") boolean mineOnly) {
    return manager.countWeightProfiles(mineOnly);
  }

  /** 创建权重配置方案。 */
  @PostMapping("/weight-profiles")
  @ResponseStatus(HttpStatus.CREATED)
  WeightProfileDetailsResponse createWeightProfile(
      @Valid @RequestBody MatchingConfigurationWeightProfileRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.createWeightProfileResponse(assembler.toDTO(request), key);
  }

  @GetMapping("/weight-profiles/{profileId}")
  WeightProfileDetailsResponse weightProfile(@PlatformIdentifier @PathVariable String profileId) {
    return manager.getWeightProfileResponse(profileId);
  }

  /** 更新权重配置方案。 */
  @PatchMapping("/weight-profiles/{profileId}")
  WeightProfileDetailsResponse updateWeightProfile(
      @PlatformIdentifier @PathVariable String profileId,
      @Valid @RequestBody MatchingConfigurationUpdateWeightProfileRequest request) {
    return manager.updateWeightProfileResponse(profileId, assembler.toDTO(request));
  }

  /** 删除权重配置方案。 */
  @DeleteMapping("/weight-profiles/{profileId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteWeightProfile(
      @PlatformIdentifier @PathVariable String profileId,
      @Valid @RequestBody MatchingConfigurationVersionReasonRequest request) {
    manager.deleteWeightProfile(profileId, assembler.toDTO(request));
  }

  @GetMapping("/admin/jd-scoring-configs")
  CursorPage<MatchPlanDetailsResponse> scoringConfigurations(
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.listMatchPlanResponses(jdVersionId, cursor, limit);
  }

  /** 创建评分配置。 */
  @PostMapping("/admin/jd-scoring-configs")
  @ResponseStatus(HttpStatus.CREATED)
  MatchPlanDetailsResponse createScoringConfiguration(
      @Valid @RequestBody MatchingConfigurationCreateMatchPlanRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.createMatchPlanResponse(assembler.toDTO(request), key);
  }

  @GetMapping("/admin/jd-scoring-configs/{configId}")
  MatchPlanDetailsResponse scoringConfiguration(@PlatformIdentifier @PathVariable String configId) {
    return manager.getMatchPlanResponse(configId);
  }

  /** 更新评分配置。 */
  @PatchMapping("/admin/jd-scoring-configs/{configId}")
  MatchPlanDetailsResponse updateScoringConfiguration(
      @PlatformIdentifier @PathVariable String configId,
      @Valid @RequestBody MatchingConfigurationUpdateMatchPlanRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.updateMatchPlanResponse(configId, assembler.toDTO(request), key);
  }

  /** 删除评分配置。 */
  @DeleteMapping("/admin/jd-scoring-configs/{configId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteScoringConfiguration(
      @PlatformIdentifier @PathVariable String configId,
      @Valid @RequestBody MatchingConfigurationVersionReasonRequest request) {
    manager.deleteMatchPlan(configId, assembler.toDTO(request));
  }

  @PostMapping("/admin/jd-scoring-configs/{configId}/clone")
  @ResponseStatus(HttpStatus.CREATED)
  MatchPlanDetailsResponse cloneScoringConfiguration(
      @PlatformIdentifier @PathVariable String configId,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.cloneMatchPlanResponse(configId, key);
  }

  /** 激活评分配置。 */
  @PostMapping("/admin/jd-scoring-configs/{configId}/activate")
  MatchPlanDetailsResponse activateScoringConfiguration(
      @PlatformIdentifier @PathVariable String configId,
      @Valid @RequestBody MatchingConfigurationVersionReasonRequest request) {
    return manager.activateMatchPlanResponse(configId, assembler.toDTO(request));
  }

  @GetMapping("/demands/{demandId}/active-scoring-config")
  ActiveScoringConfigurationResponse activeScoringConfiguration(@PlatformIdentifier @PathVariable String demandId) {
    return manager.activeForDemandResponse(demandId);
  }

  @GetMapping("/jd-versions/{jdVersionId}/active-scoring-config")
  ActiveScoringConfigurationResponse activeForJd(@PlatformIdentifier @PathVariable String jdVersionId) {
    return manager.activeForJdResponse(jdVersionId);
  }

  @GetMapping("/me/jd-scoring-configs/{configId}/preferences")
  PersonalPreferenceResponse personalPreference(@PlatformIdentifier @PathVariable String configId) {
    return manager.personalPreferenceResponse(configId);
  }

  /** 保存个人偏好。 */
  @PutMapping("/me/jd-scoring-configs/{configId}/preferences")
  PersonalPreferenceResponse savePersonalPreference(
      @PlatformIdentifier @PathVariable String configId,
      @Valid @RequestBody MatchingConfigurationPersonalPreferenceRequest request) {
    return manager.savePersonalPreferenceResponse(configId, assembler.toDTO(request));
  }
}

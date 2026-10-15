package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.response.DefaultScoringResponse;

import com.ezaigc.hr.common.core.util.CommonUtils;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.versionConflict;
import static com.ezaigc.hr.common.web.request.RequiredText.require;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.ActiveScoringConfigurationDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchPlanDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.domain.dto.SavePersonalPreferenceDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateMatchPlanDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ValidatedScoringConfigurationDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanDetails;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileDetails;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.response.ActiveScoringConfigurationResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPlanDetailsResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.PersonalPreferenceResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.WeightProfileDetailsResponse;
import com.ezaigc.hr.intelligence.matching.domain.service.ScoringConfigurationValidator;
import com.ezaigc.hr.intelligence.matching.domain.service.PersonalScreeningSettings;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionOperator;
import com.ezaigc.hr.intelligence.matching.enums.MatchingConfigurationStatus;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScoringBindingPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingConfigurationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ezaigc.hr.intelligence.matching.application.assembler.MatchingConfigurationAssembler;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import java.util.stream.Collectors;

/** 匹配配置应用服务，负责用例编排、权限校验和事务边界。 */
@Service
@Transactional
public class MatchingConfigurationApplicationManager {
  @Resource private ScoringBindingPersistenceService bindings;
  @Resource
  private MatchingConfigurationAssembler assembler;

  /** 列出权重配置方案响应列表。 */
  @Transactional(readOnly = true)
  public CursorPage<WeightProfileDetailsResponse> listWeightProfileResponses(
      boolean mineOnly, String c, Integer l) {
    return assembler.toWeightProfileResponses(listWeightProfiles(mineOnly, c, l));
  }

  /** 创建权重配置方案响应。 */
  public WeightProfileDetailsResponse createWeightProfileResponse(
      CreateWeightProfileDTO d, String k) {
    return assembler.toResponse(createWeightProfile(d, k));
  }

  @Transactional(readOnly = true)
  public WeightProfileDetailsResponse getWeightProfileResponse(String id) {
    return assembler.toResponse(getWeightProfile(id));
  }

  /** 更新权重配置方案响应。 */
  public WeightProfileDetailsResponse updateWeightProfileResponse(
      String id, UpdateWeightProfileDTO d) {
    return assembler.toResponse(updateWeightProfile(id, d));
  }

  /** 删除权重配置方案。 */
  public void deleteWeightProfile(String id, VersionReasonDTO d) {
    deleteWeightProfileCore(id, d);
  }

  /** 列出匹配方案响应列表。 */
  @Transactional(readOnly = true)
  public CursorPage<MatchPlanDetailsResponse> listMatchPlanResponses(
      String id, String c, Integer l) {
    return assembler.toMatchPlanResponses(listMatchPlans(id, c, l));
  }

  /** 创建匹配方案响应。 */
  public MatchPlanDetailsResponse createMatchPlanResponse(CreateMatchPlanDTO d, String k) {
    return assembler.toResponse(createMatchPlan(d, k));
  }

  @Transactional(readOnly = true)
  public MatchPlanDetailsResponse getMatchPlanResponse(String id) {
    return assembler.toResponse(getMatchPlan(id));
  }

  /** 更新匹配方案响应。 */
  public MatchPlanDetailsResponse updateMatchPlanResponse(
      String id, UpdateMatchPlanDTO d, String k) {
    return assembler.toResponse(updateMatchPlan(id, d, k));
  }

  /** 删除匹配方案。 */
  public void deleteMatchPlan(String id, VersionReasonDTO d) {
    deleteMatchPlanCore(id, d);
  }

  public MatchPlanDetailsResponse cloneMatchPlanResponse(String id, String k) {
    return assembler.toResponse(cloneMatchPlan(id, k));
  }

  /** 激活匹配方案响应。 */
  public MatchPlanDetailsResponse activateMatchPlanResponse(String id, VersionReasonDTO d) {
    return assembler.toResponse(activateMatchPlan(id, d));
  }

  @Transactional(readOnly = true)
  public ActiveScoringConfigurationResponse activeForDemandResponse(String id) {
    return assembler.toResponse(activeForDemand(id));
  }

  @Transactional(readOnly = true)
  public PersonalPreferenceResponse personalPreferenceResponse(String id) {
    return assembler.toResponse(getPersonalPreference(id));
  }

  /** 保存个人偏好响应。 */
  public PersonalPreferenceResponse savePersonalPreferenceResponse(
      String id, SavePersonalPreferenceDTO d) {
    return assembler.toResponse(savePersonalPreference(id, d));
  }

  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Resource private MatchingConfigurationPersistenceService persistence;
  @Resource private ControlledCriterionPersistenceService controlledCriterionPersistenceService;
  @Resource private ControlledCriterionPlanPersistenceService controlledCriterionPlanPersistenceService;
  @Resource private MatchPlanVersionPersistenceService matchPlanVersionPersistenceService;
  @Resource private MatchingAuditEventPersistenceService matchingAuditEventPersistenceService;
  @Resource private MatchingIdempotencyPersistenceService matchingIdempotencyPersistenceService;
  @Resource private PersonalPreferencePersistenceService personalPreferencePersistenceService;
  @Resource private WeightProfilePersistenceService weightProfilePersistenceService;
  @Resource private WeightProfileVersionPersistenceService weightProfileVersionPersistenceService;
  @Resource private ScoringConfigurationValidator validator;
  @Resource private DemandQueryService demandQuery;
  @Resource private AuthorizationService authorization;
  @Resource private CursorCodec cursorCodec;

  /** 列出权重配置方案列表。 */
  @Transactional(readOnly = true)
  public CursorPage<WeightProfileDetails> listWeightProfiles(
      boolean mineOnly, String cursor, Integer requestedLimit) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    int limit = limit(requestedLimit);
    boolean scopeAll = !mineOnly && actor.hasCompanyWideScope();
    Set<String> managedUserIds = actor.getOperationalManagedUserIds();
    String scope = CursorScopes.scope(
        "matching.weight-profiles", actor.getUserId(), mineOnly, scopeAll, managedUserIds);
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    List<WeightProfile> rows =
        weightProfilePersistenceService.findWeightProfiles(
            actor.getUserId(), scopeAll, managedUserIds, time(decoded), cursorId(decoded), limit + 1);
    var currentVersions = weightProfileVersionPersistenceService
        .findCurrentWeightProfileVersions(
            rows.stream().limit(limit).map(WeightProfile::getId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(
            WeightProfileVersion::getProfileId, Function.identity(), (first, ignored) -> first));
    return CursorPages.page(
        rows,
        limit,
        cursorCodec,
        scope,
        profile -> new WeightProfileDetails(profile, currentVersions.get(profile.getId())),
        WeightProfile::getUpdateTime,
        WeightProfile::getId);
  }

  @Transactional(readOnly = true)
  public CursorCount countWeightProfiles(boolean mineOnly) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    boolean scopeAll = !mineOnly && actor.hasCompanyWideScope();
    long count = weightProfilePersistenceService.countWeightProfiles(
        actor.getUserId(), scopeAll, actor.getOperationalManagedUserIds());
    return new CursorCount(count, null, false);
  }

  @Transactional(readOnly = true)
  public WeightProfileDetails getWeightProfile(String id) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    WeightProfile profile = requireWeightProfile(id);
    if (!actor.hasCompanyWideScope()
        && !actor.getUserId().equals(profile.getOwnerUserId())
        && !actor.getOperationalManagedUserIds().contains(profile.getOwnerUserId())) {
      throw new PlatformException(
          HttpStatus.FORBIDDEN, "DATA_SCOPE_FORBIDDEN", "无权查看该个人配置", false);
    }
    return weightDetails(profile);
  }

  /** 创建权重配置方案。 */
  @Transactional
  public WeightProfileDetails createWeightProfile(CreateWeightProfileDTO command, String rawKey) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    String name = require(command.getName(), "name", 120);
    ProfessionalWeights weights =
        command.getWeights() == null ? ProfessionalWeights.systemDefault() : command.getWeights().validatedCopy();
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    String settings = PersonalScreeningSettings.validateAndSerialize(command.getCriteria(), command.getDefaultSortMode(), validator);
    String fingerprint = fingerprint(name, weights.toString(), settings);
    WeightProfile replay =
        replay(
            key,
            "WEIGHT_PROFILE_CREATE",
            fingerprint,
            weightProfilePersistenceService::findWeightProfile);
    if (replay != null) {
      requireOwner(replay.getOwnerUserId(), actor.getUserId());
      return weightDetails(replay);
    }
    Instant now = clock.instant();
    String profileId = ids.newId();
    String versionId = ids.newId();
    weightProfilePersistenceService.insertWeightProfile(profileId, actor.getUserId(), name, now);
    insertWeightVersion(versionId, profileId, 1, weights, actor.getUserId(), now);
    weightProfileVersionPersistenceService.saveScreeningSettings(versionId, settings);
    remember(actor.getUserId(), key, "WEIGHT_PROFILE_CREATE", fingerprint, profileId, now);
    audit("WEIGHT_PROFILE_CREATED", actor.getUserId(), "WEIGHT_PROFILE", profileId, null, now);
    return weightDetails(weightProfilePersistenceService.findWeightProfile(profileId));
  }

  /** 更新权重配置方案。 */
  @Transactional
  public WeightProfileDetails updateWeightProfile(String id, UpdateWeightProfileDTO command) {
    CurrentActorSnapshot actor = authorization.currentActor();
    WeightProfile profile = requireOwnedWeightProfile(id);
    if (!MatchingConfigurationStatus.ACTIVE.matches(profile.getStatus())) {
      throw PlatformExceptions.conflict("WEIGHT_PROFILE_DISABLED", "停用权重模板不能修改");
    }
    ProfessionalWeights weights = command.getWeights().validatedCopy();
    int nextVersion = profile.getCurrentVersionNo() + 1;
    Instant now = clock.instant();
    if (weightProfilePersistenceService.updateWeightProfile(
            id,
            actor.getUserId(),
            require(command.getName(), "name", 120),
            nextVersion,
            command.getVersion(),
            now)
        == 0) {
      throw versionConflict("WEIGHT_PROFILE_VERSION_CONFLICT");
    }
    String settings = PersonalScreeningSettings.validateAndSerialize(command.getCriteria(), command.getDefaultSortMode(), validator);
    String versionId = ids.newId();
    insertWeightVersion(versionId, id, nextVersion, weights, actor.getUserId(), now);
    weightProfileVersionPersistenceService.saveScreeningSettings(versionId, settings);
    audit("WEIGHT_PROFILE_UPDATED", actor.getUserId(), "WEIGHT_PROFILE", id, null, now);
    return weightDetails(weightProfilePersistenceService.findWeightProfile(id));
  }

  /** 删除权重配置方案核心。 */
  private void deleteWeightProfileCore(String id, VersionReasonDTO command) {
    CurrentActorSnapshot actor = authorization.currentActor();
    WeightProfile profile = requireOwnedWeightProfile(id);
    if (persistence.countWeightProfileReferences(id) > 0) {
      throw PlatformExceptions.conflict("WEIGHT_PROFILE_REFERENCED", "权重模板已被个人方案或历史匹配引用，不能删除");
    }
    weightProfileVersionPersistenceService.deleteWeightProfileVersions(id);
    if (weightProfilePersistenceService.deleteWeightProfile(id, actor.getUserId(), command.getVersion()) == 0) {
      throw versionConflict("WEIGHT_PROFILE_VERSION_CONFLICT");
    }
    audit(
        "WEIGHT_PROFILE_DELETED",
        actor.getUserId(),
        "WEIGHT_PROFILE",
        id,
        require(command.getReason(), "reason", 500),
        clock.instant());
  }

  /** 列出匹配方案列表。 */
  @Transactional(readOnly = true)
  public CursorPage<MatchPlanDetails> listMatchPlans(
      String jdVersionId, String cursor, Integer requestedLimit) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    if (jdVersionId != null) demandQuery.requireJdVersion(jdVersionId);
    int limit = limit(requestedLimit);
    String scope = CursorScopes.scope("matching.match-plans", actor.getUserId(), jdVersionId);
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    List<MatchPlanVersion> rows =
        matchPlanVersionPersistenceService.findMatchPlanVersions(jdVersionId, time(decoded), cursorId(decoded), limit + 1);
    return CursorPages.page(
        rows,
        limit,
        cursorCodec,
        scope,
        this::details,
        MatchPlanVersion::getCreateTime,
        MatchPlanVersion::getId);
  }

  @Transactional(readOnly = true)
  public MatchPlanDetails getMatchPlan(String versionId) {
    requireSuperAdmin();
    MatchPlanVersion version = requireMatchPlanVersion(versionId);
    if (version.getJdVersionId() != null) demandQuery.requireJdVersion(version.getJdVersionId());
    return details(version);
  }

  /** 创建匹配方案。 */
  @Transactional
  public MatchPlanDetails createMatchPlan(CreateMatchPlanDTO command, String rawKey) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    String jdVersionId = CommonUtils.stripToNull(command.getJdVersionId());
    if (jdVersionId != null) demandQuery.requireJdVersion(jdVersionId);
    var validated =
        validator.validate(
            command.isSkillsEnabled(),
            command.isRoleEnabled(),
            command.isYearsEnabled(),
            command.isResponsibilityEnabled(),
            command.isIndustryProjectEnabled(),
            command.getCriteria());
    String name = require(command.getName(), "name", 120);
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    String fingerprint = fingerprint(command.getJdVersionId(), name, validated.toString());
    MatchPlanVersion replay =
        replay(
            key,
            "MATCH_PLAN_CREATE",
            fingerprint,
            matchPlanVersionPersistenceService::findMatchPlanVersion);
    if (replay != null) {
      return details(replay);
    }
    Instant now = clock.instant();
    String planId = ids.newId();
    String criterionPlanId = ids.newId();
    String versionId = ids.newId();
    persistence.insertMatchPlan(planId, jdVersionId, name, actor.getUserId(), now);
    controlledCriterionPlanPersistenceService.insertCriterionPlan(
        criterionPlanId,
        jdVersionId,
        1,
        validated.getPlanMaxBonusPoints(),
        actor.getUserId(),
        now);
    matchPlanVersionPersistenceService.insertMatchPlanVersion(
        versionId,
        planId,
        jdVersionId,
        1,
        criterionPlanId,
        validated.isSkillsEnabled(),
        validated.isRoleEnabled(),
        validated.isYearsEnabled(),
        validated.isResponsibilityEnabled(),
        validated.isIndustryProjectEnabled(),
        actor.getUserId(),
        now);
    insertCriteria(criterionPlanId, validated.getCriteria(), now);
    remember(actor.getUserId(), key, "MATCH_PLAN_CREATE", fingerprint, versionId, now);
    audit("MATCH_PLAN_CREATED", actor.getUserId(), "MATCH_PLAN_VERSION", versionId, null, now);
    return details(matchPlanVersionPersistenceService.findMatchPlanVersion(versionId));
  }

  /** 更新匹配方案。 */
  @Transactional
  public MatchPlanDetails updateMatchPlan(
      String versionId, UpdateMatchPlanDTO command, String rawKey) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    MatchPlanVersion version = requireMatchPlanVersion(versionId);
    if (version.getJdVersionId() != null) demandQuery.requireJdVersion(version.getJdVersionId());
    var validated =
        validator.validate(
            command.isSkillsEnabled(),
            command.isRoleEnabled(),
            command.isYearsEnabled(),
            command.isResponsibilityEnabled(),
            command.isIndustryProjectEnabled(),
            command.getCriteria());
    String name = require(command.getName(), "name", 120);
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    String requestFingerprint = updateMatchPlanFingerprint(versionId, command.getVersion(), name, validated);
    MatchPlanVersion replay =
        replay(
            key,
            "MATCH_PLAN_UPDATE",
            requestFingerprint,
            matchPlanVersionPersistenceService::findMatchPlanVersion);
    if (replay != null) {
      return details(replay);
    }
    if (!MatchingConfigurationStatus.DRAFT.matches(version.getStatus())) {
      throw PlatformExceptions.conflict("MATCH_PLAN_IMMUTABLE", "已激活正式评分配置不可原地修改，请复制新草稿");
    }
    Instant now = clock.instant();
    if (matchPlanVersionPersistenceService.updateMatchPlanVersion(
            versionId,
            validated.isSkillsEnabled(),
            validated.isRoleEnabled(),
            validated.isYearsEnabled(),
            validated.isResponsibilityEnabled(),
            validated.isIndustryProjectEnabled(),
            command.getVersion(),
            actor.getUserId(),
            now)
        == 0) {
      throw versionConflict("MATCH_PLAN_VERSION_CONFLICT");
    }
    MatchPlan plan = persistence.findMatchPlan(version.getPlanId());
    persistence.updateMatchPlanName(
        plan.getId(), name, actor.getUserId(), now);
    controlledCriterionPlanPersistenceService.updateCriterionPlan(
        version.getControlledCriterionPlanVersionId(),
        validated.getPlanMaxBonusPoints(),
        actor.getUserId(),
        now);
    controlledCriterionPersistenceService.deleteCriteria(version.getControlledCriterionPlanVersionId());
    insertCriteria(version.getControlledCriterionPlanVersionId(), validated.getCriteria(), now);
    remember(actor.getUserId(), key, "MATCH_PLAN_UPDATE", requestFingerprint, versionId, now);
    audit("MATCH_PLAN_UPDATED", actor.getUserId(), "MATCH_PLAN_VERSION", versionId, null, now);
    return details(matchPlanVersionPersistenceService.findMatchPlanVersion(versionId));
  }

  /** 删除匹配方案核心。 */
  private void deleteMatchPlanCore(String versionId, VersionReasonDTO command) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    MatchPlanVersion version = requireMatchPlanVersion(versionId);
    if (!MatchingConfigurationStatus.DRAFT.matches(version.getStatus())) {
      throw PlatformExceptions.conflict("MATCH_PLAN_IMMUTABLE", "只有未激活且未引用的正式评分草稿可以删除");
    }
    if (personalPreferencePersistenceService.countMatchPlanReferences(versionId) > 0) {
      throw PlatformExceptions.conflict("MATCH_PLAN_REFERENCED", "正式评分配置已被引用，不能删除");
    }
    MatchPlan plan = persistence.findMatchPlan(version.getPlanId());
    if (matchPlanVersionPersistenceService.deleteMatchPlanVersion(versionId, command.getVersion()) == 0) {
      throw versionConflict("MATCH_PLAN_VERSION_CONFLICT");
    }
    controlledCriterionPersistenceService.deleteCriteria(version.getControlledCriterionPlanVersionId());
    controlledCriterionPlanPersistenceService.deleteCriterionPlan(version.getControlledCriterionPlanVersionId());
    int remainingVersionNo = persistence.findMaxRemainingMatchPlanVersionNo(plan.getId());
    if (remainingVersionNo == 0) {
      persistence.deleteMatchPlan(plan.getId());
    } else if (remainingVersionNo != plan.getCurrentVersionNo()) {
      persistence.updateMatchPlanCurrentVersion(
          plan.getId(), remainingVersionNo, actor.getUserId(), clock.instant());
    }
    audit(
        "MATCH_PLAN_DELETED",
        actor.getUserId(),
        "MATCH_PLAN_VERSION",
        versionId,
        require(command.getReason(), "reason", 500),
        clock.instant());
  }

  @Transactional
  public MatchPlanDetails cloneMatchPlan(String versionId, String rawKey) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    MatchPlanVersion source = requireMatchPlanVersion(versionId);
    MatchPlan plan = persistence.findMatchPlan(source.getPlanId());
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    String fingerprint = fingerprint(versionId, Long.toString(source.getVersion()));
    MatchPlanVersion replay =
        replay(
            key,
            "MATCH_PLAN_CLONE",
            fingerprint,
            matchPlanVersionPersistenceService::findMatchPlanVersion);
    if (replay != null) {
      return details(replay);
    }
    List<ControlledCriterionDraft> criteria =
        drafts(controlledCriterionPersistenceService.findCriteria(source.getControlledCriterionPlanVersionId()));
    var validated =
        validator.validate(
            source.isSkillsEnabled(),
            source.isRoleEnabled(),
            source.isYearsEnabled(),
            source.isResponsibilityEnabled(),
            source.isIndustryProjectEnabled(),
            criteria);
    int nextVersion = plan.getCurrentVersionNo() + 1;
    Instant now = clock.instant();
    String criterionPlanId = ids.newId();
    String cloneId = ids.newId();
    controlledCriterionPlanPersistenceService.insertCriterionPlan(
        criterionPlanId,
        null,
        nextVersion,
        validated.getPlanMaxBonusPoints(),
        actor.getUserId(),
        now);
    matchPlanVersionPersistenceService.insertMatchPlanVersion(
        cloneId,
        plan.getId(),
        null,
        nextVersion,
        criterionPlanId,
        source.isSkillsEnabled(),
        source.isRoleEnabled(),
        source.isYearsEnabled(),
        source.isResponsibilityEnabled(),
        source.isIndustryProjectEnabled(),
        actor.getUserId(),
        now);
    insertCriteria(criterionPlanId, criteria, now);
    persistence.updateMatchPlanCurrentVersion(plan.getId(), nextVersion, actor.getUserId(), now);
    remember(actor.getUserId(), key, "MATCH_PLAN_CLONE", fingerprint, cloneId, now);
    audit(
        "MATCH_PLAN_CLONED",
        actor.getUserId(),
        "MATCH_PLAN_VERSION",
        cloneId,
        "source=" + versionId,
        now);
    return details(matchPlanVersionPersistenceService.findMatchPlanVersion(cloneId));
  }

  /** 激活匹配方案。 */
  @Transactional
  public MatchPlanDetails activateMatchPlan(String versionId, VersionReasonDTO command) {
    CurrentActorSnapshot actor = requireSuperAdmin();
    MatchPlanVersion version = requireMatchPlanVersion(versionId);
    if (!MatchingConfigurationStatus.DRAFT.matches(version.getStatus())) {
      throw PlatformExceptions.conflict("MATCH_PLAN_IMMUTABLE", "只有正式评分草稿可以激活");
    }
    List<ControlledCriterionDraft> criteria =
        drafts(controlledCriterionPersistenceService.findCriteria(version.getControlledCriterionPlanVersionId()));
    var validated =
        validator.validate(
            version.isSkillsEnabled(),
            version.isRoleEnabled(),
            version.isYearsEnabled(),
            version.isResponsibilityEnabled(),
            version.isIndustryProjectEnabled(),
            criteria);
    if (validated.getPlanMaxBonusPoints()
        != controlledCriterionPlanPersistenceService
            .findCriterionPlan(version.getControlledCriterionPlanVersionId())
            .getPlanMaxBonusPoints()) {
      throw PlatformExceptions.unprocessable("MATCH_PLAN_BONUS_SUM_MISMATCH", "受控条件附加分合计与方案上限不一致");
    }
    Instant now = clock.instant();
    // 发布新版本不失效其他岗位选用的历史版本；关联切换单独执行版本校验。
    if (matchPlanVersionPersistenceService.activateMatchPlan(versionId, command.getVersion(), actor.getUserId(), now) == 0) {
      throw versionConflict("MATCH_PLAN_VERSION_CONFLICT");
    }
    if (version.getJdVersionId() != null) {
      bindings.bindLegacy(version.getJdVersionId(), versionId, actor.getUserId(), now);
    }
    audit(
        "MATCH_PLAN_ACTIVATED",
        actor.getUserId(),
        "MATCH_PLAN_VERSION",
        versionId,
        require(command.getReason(), "reason", 500),
        now);
    return details(matchPlanVersionPersistenceService.findMatchPlanVersion(versionId));
  }

  @Transactional(readOnly = true)
  public DefaultScoringResponse defaultScoring() {
    authorization.requirePermission("demand.read");
    MatchPlanVersion plan = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(null);
    if (plan == null) throw PlatformExceptions.notFound("DEFAULT_SCORING_NOT_INITIALIZED", "公司评分没有已激活版本");
    WeightProfileVersion weights = weightProfileVersionPersistenceService.findWeightProfileVersion(DefaultScoringConstants.WEIGHT_VERSION_ID);
    if (weights == null) throw PlatformExceptions.notFound("DEFAULT_SCORING_NOT_INITIALIZED", "公司默认评分未初始化，请联系管理员检查部署");
    WeightProfile profile = weightProfilePersistenceService.findWeightProfile(weights.getProfileId());
    return new DefaultScoringResponse(assembler.toResponse(details(plan)), assembler.toResponse(new WeightProfileDetails(profile, weights)));
  }

  @Transactional(readOnly = true)
  public ActiveScoringConfigurationDTO activeForDemand(String demandId) {
    JdVersionSnapshot jd = demandQuery.requireCurrentJdVersionForDemand(demandId);
    return activeForJdSnapshot(jd);
  }

  @Transactional(readOnly = true)
  public ActiveScoringConfigurationResponse activeForJdResponse(String jdVersionId) {
    return assembler.toResponse(activeForJdSnapshot(demandQuery.requireJdVersion(jdVersionId)));
  }

  private ActiveScoringConfigurationDTO activeForJdSnapshot(JdVersionSnapshot jd) {
    authorization.requireAccess("demand.read", target(jd));
    MatchPlanVersion active = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(jd.getJdVersionId());
    boolean workflowActive = demandQuery.hasActiveWorkflow(jd.getJdVersionId());
    return new ActiveScoringConfigurationDTO(
        jd.getJdVersionId(),
        workflowActive,
        active != null,
        !jd.isDeleted() && workflowActive && active != null,
        active == null ? null : details(active));
  }

  @Transactional(readOnly = true)
  public PersonalPreference getPersonalPreference(String planVersionId) {
    CurrentActorSnapshot actor = authorization.currentActor();
    MatchPlanVersion plan = requireMatchPlanVersion(planVersionId);
    authorization.requirePermission("demand.read");
    return personalPreferencePersistenceService.findLatestPersonalPreference(actor.getUserId(), planVersionId);
  }

  /** 保存个人偏好。 */
  @Transactional
  public PersonalPreference savePersonalPreference(
      String planVersionId, SavePersonalPreferenceDTO command) {
    CurrentActorSnapshot actor = authorization.currentActor();
    MatchPlanVersion plan = requireMatchPlanVersion(planVersionId);
    if (!MatchingConfigurationStatus.ACTIVE.matches(plan.getStatus())) {
      throw PlatformExceptions.unprocessable("MATCH_PLAN_NOT_ACTIVE", "只能基于当前生效的正式评分配置保存个人偏好");
    }
    authorization.requirePermission("demand.read");
    WeightProfileVersion weights =
        weightProfileVersionPersistenceService.findWeightProfileVersion(command.getWeightProfileVersionId());
    if (weights == null) {
      throw PlatformExceptions.notFound("WEIGHT_PROFILE_VERSION_NOT_FOUND", "个人权重版本不存在");
    }
    WeightProfile profile = weightProfilePersistenceService.findWeightProfile(weights.getProfileId());
    if (!DefaultScoringConstants.WEIGHT_VERSION_ID.equals(weights.getId())) {
      requireOwner(profile.getOwnerUserId(), actor.getUserId());
    }
    Set<String> selected =
        new LinkedHashSet<>(
            command.getSelectedDefinitionIds() == null
                ? List.of()
                : command.getSelectedDefinitionIds());
    List<ControlledCriterion> allowed =
        controlledCriterionPersistenceService.findCriteria(plan.getControlledCriterionPlanVersionId()).stream()
            .filter(
                criterion ->
                    ControlledCriterionLevel.PREFER.matches(criterion.getLevel())
                        && criterion.isSelectableByHr())
            .toList();
    Set<String> allowedIds =
        allowed.stream()
            .map(ControlledCriterion::getId)
            .collect(Collectors.toSet());
    if (!allowedIds.containsAll(selected)) {
      throw PlatformExceptions.unprocessable("PERSONAL_PREFERENCE_INVALID", "个人偏好只能选择官方允许的 PREFER 项");
    }
    int selectedMax =
        allowed.stream()
            .filter(criterion -> selected.contains(criterion.getId()))
            .mapToInt(ControlledCriterion::getBonusPoints)
            .sum();
    if (selectedMax > 30) {
      throw PlatformExceptions.unprocessable("PERSONAL_PREFERENCE_INVALID", "个人偏好附加分上限不能超过 30");
    }
    int versionNo = persistence.findMaxPersonalPreferenceVersion(actor.getUserId(), planVersionId) + 1;
    Instant now = clock.instant();
    String id = ids.newId();
    persistence.insertPersonalPreference(
        id,
        actor.getUserId(),
        planVersionId,
        weights.getId(),
        versionNo,
        JsonText.write(selected.stream().sorted().toList()),
        now);
    audit("PERSONAL_PREFERENCE_SAVED", actor.getUserId(), "PERSONAL_PREFERENCE", id, null, now);
    return personalPreferencePersistenceService.findLatestPersonalPreference(actor.getUserId(), planVersionId);
  }

  private WeightProfileDetails weightDetails(WeightProfile profile) {
    return new WeightProfileDetails(
        profile,
        weightProfileVersionPersistenceService.findWeightProfileVersionByNumber(
            profile.getId(), profile.getCurrentVersionNo()));
  }

  private WeightProfile requireOwnedWeightProfile(String id) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    WeightProfile profile = requireWeightProfile(id);
    requireOwner(profile.getOwnerUserId(), actor.getUserId());
    return profile;
  }

  private WeightProfile requireWeightProfile(String id) {
    WeightProfile profile = weightProfilePersistenceService.findWeightProfile(id);
    if (profile == null) {
      throw PlatformExceptions.notFound("WEIGHT_PROFILE_NOT_FOUND", "权重模板不存在");
    }
    return profile;
  }

  private MatchPlanVersion requireMatchPlanVersion(String id) {
    MatchPlanVersion version = matchPlanVersionPersistenceService.findMatchPlanVersion(id);
    if (version == null) {
      throw PlatformExceptions.notFound("MATCH_PLAN_NOT_FOUND", "正式评分配置不存在");
    }
    return version;
  }

  private MatchPlanDetails details(MatchPlanVersion version) {
    MatchPlan plan = persistence.findMatchPlan(version.getPlanId());
    var criterionPlan =
        controlledCriterionPlanPersistenceService.findCriterionPlan(version.getControlledCriterionPlanVersionId());
    return new MatchPlanDetails(
        plan,
        version,
        criterionPlan,
        controlledCriterionPersistenceService.findCriteria(version.getControlledCriterionPlanVersionId()));
  }

  /** 新增权重版本。 */
  private void insertWeightVersion(
      String id,
      String profileId,
      int versionNo,
      ProfessionalWeights weights,
      String actorId,
      Instant now) {
    weightProfileVersionPersistenceService.insertWeightProfileVersion(
        id,
        profileId,
        versionNo,
        weights.getSkills(),
        weights.getRoleExperience(),
        weights.getRelatedYears(),
        weights.getResponsibilityMatch(),
        weights.getIndustryAndProjects(),
        actorId,
        now);
  }

  /** 新增条件。 */
  private void insertCriteria(
      String criterionPlanId, List<ControlledCriterionDraft> criteria, Instant now) {
    for (int index = 0; index < criteria.size(); index++) {
      ControlledCriterionDraft criterion = criteria.get(index);
      persistence.insertCriterion(
          ids.newId(),
          criterionPlanId,
          criterion.getCode().name(),
          criterion.getLevel().name(),
          CriterionOperator.fromCode(criterion.getOperator()).getCode(),
          criterion.getExpectedValueJson(),
          criterion.getBonusPoints(),
          require(criterion.getReason(), "reason", 500),
          require(criterion.getEvidencePolicy(), "evidencePolicy", 80),
          criterion.isSelectableByHr(),
          index + 1,
          now);
    }
  }

  private static List<ControlledCriterionDraft> drafts(List<ControlledCriterion> criteria) {
    return criteria.stream()
        .map(
            criterion ->
                new ControlledCriterionDraft(
                    ControlledCriterionCode.fromCode(
                        criterion.getCriterionCode()),
                    ControlledCriterionLevel.fromCode(
                        criterion.getLevel()),
                    criterion.getOperator(),
                    criterion.getExpectedValueJson(),
                    criterion.getBonusPoints(),
                    criterion.getReason(),
                    criterion.getEvidencePolicy(),
                    criterion.isSelectableByHr()))
        .toList();
  }

  private CurrentActorSnapshot requireSuperAdmin() {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) {
      throw new PlatformException(
          HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED", "只有超级管理员可维护正式评分配置", false);
    }
    return actor;
  }

  private static AuthorizationTarget target(JdVersionSnapshot jd) {
    return new AuthorizationTarget(
        "JD", jd.getJdId(), jd.getOwnerUserId(), jd.getOwnerDepartmentId(), jd.getCollaboratorUserIds());
  }

  private static void requireOwner(String ownerUserId, String actorUserId) {
    if (!actorUserId.equals(ownerUserId)) {
      throw new PlatformException(
          HttpStatus.FORBIDDEN, "DATA_SCOPE_FORBIDDEN", "个人配置只允许本人维护", false);
    }
  }

  private <T> T replay(String key, String action, String fingerprint, Function<String, T> loader) {
    CurrentActorSnapshot actor = authorization.currentActor();
    IdempotencyRecord existing = matchingIdempotencyPersistenceService.findIdempotency(actor.getUserId(), key);
    if (existing == null) {
      return null;
    }
    if (!action.equals(existing.getActionCode())
        || !fingerprint.equals(existing.getRequestFingerprint())) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
    }
    T result = loader.apply(existing.getTargetId());
    if (result == null) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_RESULT_MISSING", "幂等请求的原结果已不可用");
    }
    return result;
  }

  private void remember(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
    matchingIdempotencyPersistenceService.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
  }

  private void audit(
      String event,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now) {
    matchingAuditEventPersistenceService.insertAudit(ids.newId(), event, actorId, targetType, targetId, reason, now);
  }

  private static String fingerprint(String... values) {
    return Sha256Utils.fingerprintStrings("<null>", 0, values);
  }

  private static String updateMatchPlanFingerprint(
      String versionId,
      long expectedVersion,
      String name,
      ValidatedScoringConfigurationDTO validated) {
    List<String> values = new ArrayList<>();
    values.add(versionId);
    values.add(Long.toString(expectedVersion));
    values.add(name);
    values.add(Boolean.toString(validated.isSkillsEnabled()));
    values.add(Boolean.toString(validated.isRoleEnabled()));
    values.add(Boolean.toString(validated.isYearsEnabled()));
    values.add(Boolean.toString(validated.isResponsibilityEnabled()));
    values.add(Boolean.toString(validated.isIndustryProjectEnabled()));
    values.add(Integer.toString(validated.getPlanMaxBonusPoints()));
    for (ControlledCriterionDraft criterion : validated.getCriteria()) {
      values.add(criterion.getCode().name());
      values.add(criterion.getLevel().name());
      values.add(CriterionOperator.fromCode(criterion.getOperator()).getCode());
      values.add(criterion.getExpectedValueJson());
      values.add(Integer.toString(criterion.getBonusPoints()));
      values.add(require(criterion.getReason(), "reason", 500));
      values.add(require(criterion.getEvidencePolicy(), "evidencePolicy", 80));
      values.add(Boolean.toString(criterion.isSelectableByHr()));
    }
    return Sha256Utils.hashUtf8(JsonText.write(values));
  }

  private static Instant time(StableCursor cursor) {
    return cursor == null ? null : cursor.getSortTime();
  }

  private static String cursorId(StableCursor cursor) {
    return cursor == null ? null : cursor.getId();
  }

  /** 使用匹配配置的默认值和上限解析分页大小。 */
  private static int limit(Integer requested) {
    return PageLimits.resolve(
        requested, MatchingConstants.DEFAULT_PAGE_LIMIT, MatchingConstants.MAX_PAGE_LIMIT);
  }

}

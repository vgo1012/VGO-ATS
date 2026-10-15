package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.intelligence.matching.domain.response.ScoringBindingResponse;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScoringBindingPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import java.time.Clock;
import jakarta.annotation.Resource;
import com.ezaigc.hr.intelligence.matching.enums.MatchingConfigurationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScoringBindingApplicationManager {
  @Resource private ScoringBindingPersistenceService bindings;
  @Resource
  private AuthorizationService authorization;
  @Resource
  private DemandQueryService demands;
  @Resource
  private MatchPlanVersionPersistenceService plans;
  @Resource
  private MatchingAuditEventPersistenceService audit;
  @Resource
  private PlatformIdGenerator ids;
  @Resource
  private Clock clock;

  @Transactional(readOnly = true)
  public ScoringBindingResponse get(String jdVersionId) {
    requireAdmin();
    demands.requireJdVersion(jdVersionId);
    var binding = bindings.binding(jdVersionId);
    return new ScoringBindingResponse(jdVersionId, binding == null ? null : binding.getPlanVersionId(),
        binding == null ? "0" : Long.toString(binding.getVersion()));
  }

  @Transactional
  public ScoringBindingResponse bind(String jdVersionId, String planVersionId, long version) {
    requireAdmin();
    demands.requireJdVersion(jdVersionId);
    if (planVersionId != null) {
      var plan = plans.findMatchPlanVersion(planVersionId);
      if (plan == null || !MatchingConfigurationStatus.ACTIVE.matches(plan.getStatus())) {
        throw PlatformExceptions.unprocessable("SCORING_PLAN_NOT_PUBLISHED", "请选择已发布的评分方案");
      }
    }
    var before = bindings.binding(jdVersionId);
    var actor = authorization.currentActor();
    try {
      int changed = before == null && version == 0
          ? bindings.insertBinding(jdVersionId, planVersionId, actor.getUserId(), clock.instant())
          : bindings.updateBinding(jdVersionId, planVersionId, version, actor.getUserId(), clock.instant());
      if (changed != 1) throw PlatformExceptions.conflict("SCORING_BINDING_CHANGED", "岗位关联已被修改，请刷新后再保存");
    } catch (DuplicateKeyException exception) {
      throw PlatformExceptions.conflict("SCORING_BINDING_CHANGED", "岗位关联已被修改，请刷新后再保存");
    }
    audit.insertAudit(ids.newId(), "SCORING_PLAN_BOUND", actor.getUserId(), "JD_VERSION", jdVersionId,
        "评分方案：" + (before == null ? "未关联" : before.getPlanVersionId()) + " → " + planVersionId, clock.instant());
    return new ScoringBindingResponse(jdVersionId, planVersionId, Long.toString(version + 1));
  }

  private void requireAdmin() {
    if (!authorization.currentActor().isSuperAdmin()) {
      throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "只有管理员可以关联正式评分方案");
    }
  }
}

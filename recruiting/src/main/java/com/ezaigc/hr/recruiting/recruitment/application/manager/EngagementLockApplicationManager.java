package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.api.HandoverTransferResult;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.EngagementLockAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.CollaborationRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CollaborationRequestResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyResponse;
import com.ezaigc.hr.recruiting.recruitment.enums.CollaborationRequestStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.EngagementLockStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.LockRenewingProgressType;
import com.ezaigc.hr.recruiting.recruitment.service.CollaborationRequestPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockPolicyHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockPolicyPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 跟进归属锁定应用服务，负责用例编排、权限校验和事务边界。 */
@Service
public class EngagementLockApplicationManager
        implements EngagementLockQueryService, HandoverResourceContributor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(EngagementLockApplicationManager.class);

    /** 授权。 */
    @Resource
    private AuthorizationService authorization;
    @Resource private CandidateLibraryQueryService candidateLibrary;
    @Resource private CompanyBlacklistQueryService candidateCommands;
    /** 目录。 */
    @Resource
    private IdentityDirectoryService directory;
    /** ID 列表。 */
    @Resource
    private PlatformIdGenerator ids;
    /** 时钟。 */
    @Resource
    private Clock clock;

    /** 持久化。 */
    @Resource
    private EngagementLockPersistenceService persistence;
  @Resource private CollaborationRequestPersistenceService collaborationRequestPersistenceService;
  @Resource private LockHistoryPersistenceService lockHistoryPersistenceService;
  @Resource private LockPolicyHistoryPersistenceService lockPolicyHistoryPersistenceService;
  @Resource private LockPolicyPersistenceService lockPolicyPersistenceService;
    @Resource
    private EngagementLockAssembler assembler;
    @Resource
    private TransactionTemplate transactionTemplate;

    public LockSummary activeLock(String candidateId) {
        EngagementLock value = persistence.findByCandidate(candidateId);
        Instant now = clock.instant();
      if (value == null
          || !EngagementLockStatus.ACTIVE.matches(value.getStatus())
          || !value.getExpiresAt().isAfter(now)) {
        return null;
      }
        var actor = authorization.currentActor();
        CollaborationRequest collaboration =
                collaborationRequestPersistenceService.activeCollaboration(value.getId(), actor.getUserId());
        return summary(
                value,
                actor.getUserId().equals(value.getOwnerUserId())
                        || collaboration != null
                            && CollaborationRequestStatus.ACCEPTED.matches(
                                collaboration.getStatus()),
                collaboration == null ? null : collaboration.getStatus());
    }

    @Override
    public Map<String, LockSummary> activeLocks(Collection<String> candidateIds) {
      if (candidateIds == null || candidateIds.isEmpty()) {
        return Map.of();
      }
      List<String> uniqueCandidateIds =
          candidateIds.stream()
              .filter(Objects::nonNull)
              .filter(candidateId -> !candidateId.isBlank())
              .distinct()
              .toList();
      if (uniqueCandidateIds.isEmpty()) {
        return Map.of();
      }
      List<EngagementLock> activeLocks =
          persistence.activeByCandidates(uniqueCandidateIds, clock.instant());
      if (activeLocks.isEmpty()) {
        return Map.of();
      }

      var actor = authorization.currentActor();
      Map<String, CollaborationRequest> collaborationsByLock = new LinkedHashMap<>();
      collaborationRequestPersistenceService
          .activeCollaborations(
              activeLocks.stream().map(EngagementLock::getId).toList(), actor.getUserId())
          .forEach(collaboration -> collaborationsByLock.put(collaboration.getLockId(), collaboration));

      Map<String, LockSummary> summaries = new LinkedHashMap<>();
      for (EngagementLock value : activeLocks) {
        CollaborationRequest collaboration = collaborationsByLock.get(value.getId());
        boolean canContact =
            actor.getUserId().equals(value.getOwnerUserId())
                || collaboration != null
                    && CollaborationRequestStatus.ACCEPTED.matches(collaboration.getStatus());
        summaries.put(
            value.getCandidateId(),
            summary(
                value,
                canContact,
                collaboration == null ? null : collaboration.getStatus()));
      }
      return Map.copyOf(summaries);
    }

    /** 记录进度。 */
    @Transactional
    public void recordProgress(String candidateId, String progressType, String reason) {
        candidateCommands.lockCandidateForCommand(candidateId);
        String type = CommonUtils.upperOrEmpty(progressType);
      if (!LockRenewingProgressType.supports(type)) {
        throw new PlatformException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "ENGAGEMENT_PROGRESS_NOT_RENEWING",
                "该行为不属于可续期的真实招聘进展",
                false);
      }
        EngagementLock value = persistence.findByCandidate(candidateId);
      if (value == null || !active(value)) {
        throw new PlatformException(
                HttpStatus.CONFLICT, "ENGAGEMENT_LOCK_REQUIRED", "记录真实进展前必须存在有效跟进锁", true);
      }
        authorize(value);
        Instant now = clock.instant();
      if (persistence.renew(
              value.getId(),
              type,
              expires(now),
              value.getVersion(),
              authorization.currentActor().getUserId(),
              now)
              == 0) {
        throw state();
      }
        history(
                value,
                "RENEW",
                value.getOwnerUserId(),
                value.getOwnerUserId(),
                type,
                required(reason),
                authorization.currentActor().getUserId(),
                now);
    }

    public List<LockHistoryResponse> history(String candidateId, int limit) {
        boolean shared = sharedCandidate(candidateId);
        if (!shared) authorization.requirePermission("recruitment.manage");
        EngagementLock value = persistence.findByCandidate(candidateId);
      if (value == null) {
        throw new PlatformException(
                HttpStatus.NOT_FOUND, "ENGAGEMENT_LOCK_NOT_FOUND", "候选人没有跟进锁记录", false);
      }
        if (!shared) authorize(value);
        return assembler.toHistoryResponses(
                lockHistoryPersistenceService.history(candidateId, Math.clamp(limit, 1, 200)));
    }

    public LockPolicyResponse policy() {
        authorization.requirePermission("recruitment.manage");
        return assembler.toResponse(lockPolicyPersistenceService.policy());
    }

    public List<LockPolicyHistoryResponse> policyHistory(int limit) {
        var actor = authorization.currentActor();
      if (!actor.isSuperAdmin()) {
        throw new PlatformException(
                HttpStatus.FORBIDDEN, "LOCK_POLICY_ADMIN_REQUIRED", "仅超级管理员可以查看跟进锁配置历史", false);
      }
        return assembler.toPolicyHistoryResponses(lockPolicyHistoryPersistenceService.policyHistory(Math.clamp(limit, 1, 200)));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int expireDue(int limit) {
        Instant now = clock.instant();
        int expired = 0;
        for (EngagementLock value : persistence.due(now, Math.clamp(limit, 1, 500))) {
            try {
                Boolean changed =
                        transactionTemplate.execute(ignored -> expireOne(value, now));
                if (Boolean.TRUE.equals(changed)) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "engagement-lock-expiry-failed lockId={} expectedVersion={}",
                        value.getId(),
                        value.getVersion(),
                        SafeLogException.from(exception));
            }
        }
        return expired;
    }

    /** 同一跟进锁的状态、协作取消与历史记录必须原子提交。 */
    private boolean expireOne(EngagementLock value, Instant now) {
        candidateCommands.lockCandidateForCommand(value.getCandidateId());
        if (persistence.expire(value.getId(), value.getVersion(), now) == 1) {
            collaborationRequestPersistenceService.cancelCollaborations(
                    value.getId(), PlatformActorConstants.SYSTEM_ACTOR_ID, "跟进锁到期自动释放", now);
            history(
                    value,
                    "EXPIRE",
                    value.getOwnerUserId(),
                    null,
                    null,
                    "跟进期限届满且无续期进展，系统自动释放",
                    PlatformActorConstants.SYSTEM_ACTOR_ID,
                    now);
            return true;
        }
        return false;
    }

    public List<CollaborationRequestResponse> collaborations(String candidateId, int limit) {
        if (!sharedCandidate(candidateId)) authorization.requirePermission("recruitment.manage");
        return assembler.toCollaborationResponses(
                collaborationRequestPersistenceService.collaborations(candidateId, Math.clamp(limit, 1, 200)));
    }

    public String resourceType() {
        return "ENGAGEMENT_LOCK";
    }

    public List<HandoverResource> preview(String sourceUserId) {
        return persistence.activeByOwner(sourceUserId, clock.instant()).stream()
                .map(
                        v ->
                                new HandoverResource(
                                        resourceType(), v.getId(), "候选人跟进锁 " + v.getCandidateId(), true, true, null))
                .toList();
    }

    @Transactional
    public HandoverTransferResult transfer(
            String resourceId,
            String sourceUserId,
            String targetUserId,
            String actorUserId,
            String reason) {
        EngagementLock value = persistence.findById(resourceId);
        if (value != null) candidateCommands.lockCandidateForCommand(value.getCandidateId());
      if (value == null || !active(value) || !sourceUserId.equals(value.getOwnerUserId())) {
        return HandoverTransferResult.failure(
                "ENGAGEMENT_LOCK_NOT_TRANSFERABLE", "跟进锁不存在、已失效或所属人已变化");
      }
        var target = directory.activeUser(targetUserId);
        Instant now = clock.instant();
        String clean = required(reason);
      if (persistence.transfer(
              resourceId,
              target.userId(),
              target.homeDepartmentId(),
              target.displayName(),
              EngagementLockStatus.ACTIVE.getCode(),
              expires(now),
              value.getVersion(),
              actorUserId,
              now)
              == 0) {
        return HandoverTransferResult.failure("ENGAGEMENT_LOCK_STATE_CHANGED", "跟进锁状态已变化");
      }
        collaborationRequestPersistenceService.cancelCollaborations(resourceId, actorUserId, "离职交接导致跟进锁转移：" + clean, now);
        String historyId = ids.newId();
        lockHistoryPersistenceService.insertHistory(
                historyId,
                value.getId(),
                value.getCandidateId(),
                "HANDOVER",
                value.getOwnerUserId(),
                target.userId(),
                null,
                clean,
                actorUserId,
                now);
        return HandoverTransferResult.success(historyId);
    }

    private Instant expires(Instant now) {
        return now.plus(lockPolicyPersistenceService.policy().getDurationDays(), ChronoUnit.DAYS);
    }

    private boolean active(EngagementLock v) {
        return EngagementLockStatus.ACTIVE.matches(v.getStatus())
            && v.getExpiresAt().isAfter(clock.instant());
    }

    private boolean sharedCandidate(String candidateId) {
        authorization.currentActor();
        return candidateId != null && candidateLibrary.eligibleCandidateIds(List.of(candidateId)).contains(candidateId);
    }

    private void authorize(EngagementLock v) {
        authorization.requireAccess("recruitment.manage", target(v));
    }

    private static AuthorizationTarget target(EngagementLock v) {
        return new AuthorizationTarget(
                "ENGAGEMENT_LOCK", v.getId(), v.getOwnerUserId(), v.getOwnerDepartmentId(), Set.of());
    }

    private void history(
            EngagementLock v,
            String action,
            String from,
            String to,
            String progress,
            String reason,
            String actor,
            Instant now) {
        if (lockHistoryPersistenceService.insertHistory(
                        ids.newId(),
                        v.getId(),
                        v.getCandidateId(),
                        action,
                        from,
                        to,
                        progress,
                        reason,
                        actor,
                        now)
                != 1) {
            throw new IllegalStateException("engagement lock history insert count mismatch");
        }
    }

    private static LockSummary summary(
            EngagementLock v, boolean canContact, String collaborationStatus) {
        return new LockSummary(
                v.getId(),
                v.getCandidateId(),
                v.getOwnerUserId(),
                v.getOwnerDepartmentId(),
                v.getContactName(),
                v.getAcquiredAt(),
                v.getExpiresAt(),
                canContact,
                collaborationStatus);
    }

    private static String required(String v) {
      if (v == null || v.isBlank() || v.length() > 500) {
        throw invalid("原因必须为 1～500 字");
      }
        return v.strip();
    }

    private static PlatformException invalid(String detail) {
        return new PlatformException(
                HttpStatus.UNPROCESSABLE_CONTENT, "ENGAGEMENT_LOCK_INVALID", detail, false);
    }

    private static PlatformException state() {
        return new PlatformException(
                HttpStatus.CONFLICT, "ENGAGEMENT_LOCK_STATE_CHANGED", "跟进锁状态已变化，请刷新后重试", true);
    }
}

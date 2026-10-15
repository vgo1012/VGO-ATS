package com.ezaigc.hr.recruiting.submission.application.manager;

import com.ezaigc.hr.recruiting.submission.domain.dto.ApplicationClientRestrictionDTO;
import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.demand.application.manager.DemandCatalogApplicationManager;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.require;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.common.web.request.RequiredText.required;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.OfferCommandDTO;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationDecision;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStage;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferTerminationReason;
import com.ezaigc.hr.recruiting.submission.application.assembler.SubmissionAssembler;
import com.ezaigc.hr.recruiting.submission.domain.dto.AcknowledgeSubmissionRiskDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CorrectClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CreateSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.DisableClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.SendSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.SubmissionFeedbackDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.WithdrawSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.domain.entity.Eligibility;
import com.ezaigc.hr.recruiting.submission.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.submission.domain.entity.RiskAcknowledgement;
import com.ezaigc.hr.recruiting.submission.domain.entity.SubmissionDetails;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientRestrictionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientSubmissionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.EligibilityResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.FeedbackResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.RiskAcknowledgementResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.SubmissionDetailsResponse;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionDeliveryChannel;
import com.ezaigc.hr.recruiting.submission.enums.ClientRestrictionStatus;
import com.ezaigc.hr.recruiting.submission.enums.ClientRestrictionIntent;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionDeliveryStatus;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionProcessStatus;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionCurrentPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.RiskAcknowledgementPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SendSnapshotPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SubmissionFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SubmissionIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.talent.api.ResumeSubmissionQueryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户推荐应用服务，负责用例编排、权限校验和事务边界。
 */
@Service
@Transactional
public class SubmissionApplicationManager {

    @Resource
    private PlatformIdGenerator ids;
    @Resource
    private Clock clock;

    @Resource
    private SubmissionPersistenceService persistence;
    @Resource
    private ClientRestrictionCurrentPersistenceService clientRestrictionCurrentPersistenceService;
    @Resource
    private ClientRestrictionPersistenceService clientRestrictionPersistenceService;
    @Resource
    private RiskAcknowledgementPersistenceService riskAcknowledgementPersistenceService;
    @Resource
    private SendSnapshotPersistenceService sendSnapshotPersistenceService;
    @Resource
    private SubmissionFeedbackPersistenceService submissionFeedbackPersistenceService;
    @Resource
    private SubmissionIdempotencyPersistenceService submissionIdempotencyPersistenceService;
    @Resource
    private SubmissionAssembler assembler;
    @Resource
    private AuthorizationService authorization;
    @Resource private CandidateLibraryQueryService candidateLibrary;
    @Resource
    private RecruitmentApplicationManager applications;
    @Resource
    private DemandQueryService demands;
    @Resource
    private DemandCatalogApplicationManager demandCatalog;
    @Resource
    private ActivityOriginRecorder activityOrigins;
    @Resource
    private ResumeSubmissionQueryService resumes;
    @Resource
    private CompanyBlacklistQueryService blacklist;
    @Resource
    private CursorCodec cursorCodec;

    public ClientRestrictionResponse restrictApplication(
            String applicationId, ApplicationClientRestrictionDTO command, String key) {
        authorization.requirePermission("submission.manage");
        var app = applications.requireLockedTerminationAccess(applicationId);
        String clientId = require(command.getClientId(), "clientId");
        demandCatalog.getClient(clientId);
        String detail = required(command.getReasonDetail(), "reasonDetail", 1000);
        String source = required(command.getSourceChannel(), "sourceChannel", 40);
        String evidence = optional(command.getEvidenceReference(), "evidenceReference", 1000);
        Instant occurred = requireSourceOccurredAt(command.getSourceOccurredAt(), "来源时间不能晚于当前时间");
        String id = ids.newId();
        String replay = reserve(authorization.currentActor().getUserId(), key,
                "APPLICATION_CLIENT_RESTRICTION", fingerprint(applicationId, clientId, detail, source,
                        evidence, command.getExpiresAt(), command.getSourceOccurredAt()), id);
        if (replay != null) return assembler.toResponse(requiredRestriction(replay));
        ClientRestriction current = persistence.currentRestriction(clientId, app.getCandidateId());
        var value = new ClientRestriction();
        value.setId(id);
        value.setClientId(clientId);
        value.setCandidateId(app.getCandidateId());
        value.setSourceApplicationId(applicationId);
        value.setEvidenceReference(evidence);
        value.setRecordType(current == null ? "ACTIVATION" : "CORRECTION");
        value.setCorrectsRecordId(current == null ? null : current.getId());
        value.setStatus(ClientRestrictionStatus.ACTIVE.getCode());
        value.setRestrictionIntent(ClientRestrictionIntent.DO_NOT_SUBMIT.getCode());
        value.setExpiresAt(command.getExpiresAt());
        value.setReasonDetail(detail);
        value.setSourceChannel(source);
        value.setSourceOccurredAt(occurred);
        ClientRestriction saved = saveApplicationRestriction(value, current == null ? null : current.getId(), true);
        // 完整原因保留在标签中；流程摘要遵守原有 500 字上限。
        String endReason = detail.length() <= 500 ? detail : detail.substring(0, 475) + "（完整原因见黑名单标签）";
        if (ApplicationStage.OFFER.matches(app.getCurrentStage())) {
            applications.addOffer(applicationId, new OfferCommandDTO(OfferStatus.TERMINATED.getCode(),
                    OfferTerminationReason.COMPANY_WITHDREW.getCode(), ApplicationDecision.REJECT.getCode(),
                    endReason, null, null, null), "blacklist-end-" + id);
        } else {
            applications.decide(applicationId, ApplicationDecision.REJECT.getCode(), endReason, null, "blacklist-end-" + id);
        }
        return assembler.toResponse(saved);
    }

    private ClientRestriction saveApplicationRestriction(ClientRestriction value, String expected, boolean active) {
        Instant now = clock.instant();
        if (active && value.getExpiresAt() != null && !value.getExpiresAt().isAfter(now)) {
            throw PlatformExceptions.unprocessable("CLIENT_RESTRICTION_EXPIRY_INVALID", "限制到期时间必须晚于当前时间");
        }
        String actor = authorization.currentActor().getUserId();
        value.setRecordedByUserId(actor);
        value.setCreateTime(now);
        if (!clientRestrictionPersistenceService.save(value)) {
            throw PlatformExceptions.conflict("CLIENT_RESTRICTION_NOT_CURRENT", "客户限制保存失败，请刷新后重试");
        }
        activityOrigins.record(value.getSourceApplicationId(), "RESTRICTION", value.getId());
        int changed = expected == null
                ? persistence.insertRestrictionProjection(value.getClientId(), value.getCandidateId(), value.getId(), active, value.getExpiresAt(), actor, now)
                : persistence.replaceRestrictionProjection(value.getClientId(), value.getCandidateId(), expected, value.getId(), active, value.getExpiresAt(), actor, now);
        if (changed != 1) throw PlatformExceptions.conflict("CLIENT_RESTRICTION_NOT_CURRENT", "客户限制已被修改，请刷新后重试");
        return value;
    }

    private ClientRestriction reviseApplicationRestriction(ClientRestriction prior, String status, String intent,
            Instant expiresAt, String detail, String source, Instant occurred, boolean active) {
        applications.requireLockedFollowUpAccess(prior.getSourceApplicationId());
        var value = new ClientRestriction();
        value.setId(ids.newId());
        value.setClientId(prior.getClientId());
        value.setCandidateId(prior.getCandidateId());
        value.setSourceApplicationId(prior.getSourceApplicationId());
        value.setEvidenceReference(prior.getEvidenceReference());
        value.setRecordType(active ? "CORRECTION" : "DISABLE");
        value.setStatus(status);
        value.setRestrictionIntent(intent);
        value.setExpiresAt(expiresAt);
        value.setReasonDetail(detail);
        value.setSourceChannel(source);
        value.setSourceOccurredAt(requireSourceOccurredAt(occurred, "来源时间不能晚于当前时间"));
        value.setCorrectsRecordId(prior.getId());
        return saveApplicationRestriction(value, prior.getId(), active);
    }

    public SubmissionDetailsResponse create(CreateSubmissionDTO command, String key) {
        authorization.requirePermission("submission.manage");
        throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "客户推荐功能已停用");
    }

    /**
     * 列出响应列表。
     */
    @Transactional(readOnly = true)
    public CursorPage<ClientSubmissionResponse> listResponses(
            String applicationId,
            String clientId,
            String jdId,
            String delivery,
            String process,
            String cursor,
            Integer limit) {
        return assembler.toResponse(
                list(applicationId, clientId, jdId, delivery, process, cursor, limit));
    }

    @Transactional(readOnly = true)
    public SubmissionDetailsResponse getResponse(String id) {
        return assembler.toResponse(get(id));
    }

    public SubmissionDetailsResponse send(String id, SendSubmissionDTO command, String key) {
        authorization.requirePermission("submission.manage");
        throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "客户推荐功能已停用");
    }

    public FeedbackResponse addFeedback(String id, SubmissionFeedbackDTO command, String key) {
        return assembler.toResponse(
                addFeedback(
                        id,
                        command.getResult(),
                        command.getReasonCategory(),
                        command.getNextAction(),
                        command.getNote(),
                        command.getSourceChannel(),
                        command.getSourceOccurredAt(),
                        command.isExplicitClientBan(),
                        command.getRestrictionExpiresAt(),
                        command.getRestrictionIntent(),
                        key));
    }

    public SubmissionDetailsResponse withdraw(String id, WithdrawSubmissionDTO command, String key) {
        return assembler.toResponse(withdraw(id, command.getReason(), command.getVersion(), key));
    }

    public RiskAcknowledgementResponse acknowledgeRisk(
            String id, AcknowledgeSubmissionRiskDTO command) {
        return assembler.toResponse(acknowledgeRisk(id, command.getRiskCode(), command.getReason()));
    }

    @Transactional(readOnly = true)
    public EligibilityResponse eligibilityResponse(String applicationId) {
        return assembler.toResponse(eligibility(applicationId));
    }

    @Transactional(readOnly = true)
    public List<ClientRestrictionResponse> restrictionResponses(
            String candidateId, String clientId, int limit) {
        return assembler.toRestrictionResponses(restrictions(candidateId, clientId, limit));
    }

    /**
     * 更正限制。
     */
    public ClientRestrictionResponse correctRestriction(
            String id, CorrectClientRestrictionDTO command) {
        return assembler.toResponse(
                correctRestriction(
                        id,
                        command.getRestrictionIntent(),
                        command.getExpiresAt(),
                        command.getReasonDetail(),
                        command.getSourceChannel(),
                        command.getSourceOccurredAt()));
    }

    public ClientRestrictionResponse correctRestriction(String id, CorrectClientRestrictionDTO command, String key) {
        if (key == null) return correctRestriction(id, command);
        String replay = reserveRestrictionRevision(id, key, "CLIENT_RESTRICTION_CORRECTION",
                fingerprint(id, command.getRestrictionIntent(), command.getExpiresAt(), command.getReasonDetail(),
                        command.getSourceChannel(), command.getSourceOccurredAt()));
        return replay == null ? correctRestriction(id, command) : replayRestrictionRevision(replay);
    }

    public ClientRestrictionResponse disableRestriction(String id, DisableClientRestrictionDTO command, String key) {
        if (key == null) return disableRestriction(id, command);
        String replay = reserveRestrictionRevision(id, key, "CLIENT_RESTRICTION_DISABLE", fingerprint(id, command.getReason()));
        return replay == null ? disableRestriction(id, command) : replayRestrictionRevision(replay);
    }

    private String reserveRestrictionRevision(String id, String key, String action, String fingerprint) {
        authorization.requirePermission("submission.manage");
        ClientRestriction prior = requiredRestriction(id);
        if (prior.getSourceApplicationId() != null) applications.requireLockedFollowUpAccess(prior.getSourceApplicationId());
        else requireVisible(prior.getSourceSubmissionId());
        // 同一原事实仅能通过投影 CAS 追加一次修订；幂等键关联原事实并重放其唯一修订。
        return reserve(authorization.currentActor().getUserId(), key, action, fingerprint, id);
    }

    private ClientRestrictionResponse replayRestrictionRevision(String sourceId) {
        ClientRestriction revision = clientRestrictionPersistenceService.revision(sourceId, authorization.currentActor().getUserId());
        if (revision == null) throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原限制修订尚未完成，请重试");
        return assembler.toResponse(revision);
    }

    @Transactional(readOnly = true)
    public List<ClientRestrictionResponse> currentRestrictionResponses(String candidateId, String clientId) {
        if (!sharedCandidate(candidateId)) authorization.requirePermission("submission.read");
        ClientRestriction current = persistence.currentRestriction(require(clientId, "clientId"), require(candidateId, "candidateId"));
        return current == null ? List.of() : List.of(assembler.toResponse(current));
    }

    /**
     * 停用限制。
     */
    public ClientRestrictionResponse disableRestriction(
            String id, DisableClientRestrictionDTO command) {
        return assembler.toResponse(disableRestriction(id, command.getReason()));
    }

    @Transactional
    public SubmissionDetails create(String applicationId, String rawKey) {
        authorization.requirePermission("submission.manage");
        var app = applications.requireSubmissionContext(require(applicationId, "applicationId"));
        if (!RecruitmentType.EXTERNAL.matches(app.recruitmentType())) {
            throw PlatformExceptions.unprocessable("EXTERNAL_APPLICATION_REQUIRED", "只有外派招聘活动可以创建客户推荐");
        }
        var demand = demands.requireSubmissionContextForJdVersion(app.jdVersionId());
        if (demand.clientId() == null) {
            throw PlatformExceptions.unprocessable("CLIENT_BINDING_INCOMPLETE", "请先为 JD 选择客户");
        }
        resumes.requireSubmissionSnapshot(app.candidateId(), app.resumeVersionId());
        var actor = authorization.currentActor();
        String submissionId = ids.newId();
        blacklist.lockCandidateForCommand(app.candidateId());
        String replay =
                reserve(
                        actor.getUserId(),
                        rawKey,
                        "SUBMISSION_CREATE",
                        fingerprint(
                                app.applicationId(),
                                app.candidateId(),
                                demand.clientId(),
                                app.jdVersionId(),
                                app.resumeVersionId()),
                        submissionId);
        if (replay != null) {
            return details(requireVisible(replay));
        }
        blacklist.requireRecruitmentAllowed(app.candidateId());
        Instant now = clock.instant();
        persistence.insertSubmission(
                submissionId,
                app.applicationId(),
                app.candidateId(),
                app.demandId(),
                demand.clientId(),
                demand.projectId(),
                demand.positionId(),
                app.jdId(),
                app.jdVersionId(),
                app.resumeVersionId(),
                app.ownerUserId(),
                app.ownerDepartmentId(),
                actor.getUserId(),
                now);
        return details(persistence.find(submissionId));
    }

    @Transactional(readOnly = true)
    public CursorPage<ClientSubmission> list(
            String applicationId,
            String clientId,
            String jdId,
            String delivery,
            String process,
            String cursor,
            Integer requestedLimit) {
        var actor = authorization.currentActor();
        int limit = PageLimits.resolve(requestedLimit);
        String normalizedApplicationId = CommonUtils.stripToNull(applicationId);
        String normalizedClientId = CommonUtils.stripToNull(clientId);
        String normalizedJdId = CommonUtils.stripToNull(jdId);
        String normalizedDelivery = normalized(delivery);
        String normalizedProcess = normalized(process);
        String scope = CursorScopes.scope(
                "submission.list",
                actor.getUserId(),
                actor.hasCompanyWideScope(),
                actor.getOperationalManagedUserIds(),
                actor.visibleDepartmentIds(),
                actor.getPermissions(),
                normalizedApplicationId,
                normalizedClientId,
                normalizedJdId,
                normalizedDelivery,
                normalizedProcess);
        return VisibleCursorPageCollector.collect(
                StableCursor.decode(cursorCodec, scope, cursor),
                limit,
                200,
                cursorCodec,
                scope,
                scan ->
                        persistence.list(
                                normalizedApplicationId,
                                normalizedClientId,
                                normalizedJdId,
                                normalizedDelivery,
                                normalizedProcess,
                                null,
                                scan == null ? null : scan.getSortTime(),
                                scan == null ? null : scan.getId(),
                                200),
                values -> {
                    Set<String> visible = new HashSet<>(authorization.accessibleObjectIds(
                                actor,
                                "submission.read",
                                values.stream()
                                        .map(
                                                value ->
                                                        new AuthorizationTarget(
                                                                "CLIENT_SUBMISSION",
                                                                value.getId(),
                                                                value.getOwnerUserId(),
                                                                value.getOwnerDepartmentId(),
                                                                Set.of()))
                                        .toList()));
                    Set<String> shared = candidateLibrary.eligibleCandidateIds(values.stream()
                            .map(ClientSubmission::getCandidateId).filter(Objects::nonNull).distinct().toList());
                    values.stream().filter(value -> value.getCandidateId() != null && shared.contains(value.getCandidateId()))
                            .map(ClientSubmission::getId).forEach(visible::add);
                    return visible;
                },
                ClientSubmission::getUpdateTime,
                ClientSubmission::getId);
    }

    @Transactional(readOnly = true)
    public SubmissionDetails get(String id) {
        ClientSubmission value = persistence.find(id);
        if (value == null) throw PlatformExceptions.notFound("SUBMISSION_NOT_FOUND", "客户推荐不存在");
        if (!sharedCandidate(value.getCandidateId())) {
            authorization.requirePermission("submission.read");
            if (!canView(value)) throw PlatformExceptions.forbidden("SUBMISSION_ACCESS_FORBIDDEN", "无权访问该客户推荐");
        }
        return details(value);
    }

    @Transactional
    public SubmissionDetails send(
            String id, String recipient, String channel, String reason, long version, String rawKey) {
        authorization.requirePermission("submission.manage");
        ClientSubmission value = requireLockedVisible(id);
        String cleanRecipient = required(recipient, "recipient", 500),
                cleanChannel = CommonUtils.upperOrEmpty(channel),
                cleanReason = required(reason, "reason", 500);
        if (!SubmissionDeliveryChannel.supports(cleanChannel)) {
            throw PlatformExceptions.unprocessable("SUBMISSION_CHANNEL_INVALID", "发送渠道不受支持");
        }
        var resume =
                resumes.requireSubmissionSnapshot(value.getCandidateId(), value.getResumeVersionId());
        var actor = authorization.currentActor();
        String snapshotId = ids.newId();
        String replay =
                reserve(
                        actor.getUserId(),
                        rawKey,
                        "SUBMISSION_SEND",
                        fingerprint(
                                id, cleanRecipient, cleanChannel, cleanReason, version, resume.contentSha256()),
                        id);
        if (replay != null) {
            return details(requireVisible(replay));
        }
        requireDraft(value);
        blacklist.requireRecruitmentAllowed(value.getCandidateId());
        Instant now = clock.instant();
        if (persistence.markSent(id, version, actor.getUserId(), now) == 0) {
            throw state();
        }
        applications.bindSentClientSubmissionStep(value.getApplicationId(), id);
        if (sendSnapshotPersistenceService.insertSendSnapshot(
                snapshotId,
                id,
                value.getResumeVersionId(),
                cleanRecipient,
                cleanChannel,
                resume.originalFileName(),
                resume.contentSha256(),
                actor.getUserId(),
                now) != 1) {
            throw new IllegalStateException("客户推荐发送快照写入失败");
        }
        return details(persistence.find(id));
    }

    @Transactional
    public Feedback addFeedback(
            String id,
            String result,
            String category,
            String nextAction,
            String note,
            String source,
            Instant occurredAt,
            boolean explicitClientBan,
            Instant restrictionExpiresAt,
            String restrictionIntent,
            String rawKey) {
        authorization.requirePermission("submission.manage");
        ClientSubmission value = requireLockedVisible(id);
        String status = CommonUtils.upperOrEmpty(result);
        if (!SubmissionProcessStatus.supports(status)) {
            throw PlatformExceptions.unprocessable("SUBMISSION_PROCESS_STATUS_INVALID", "客户反馈状态无效");
        }
        String cleanCategory = required(category, "reasonCategory", 60),
                cleanNext = required(nextAction, "nextAction", 500),
                cleanSource = required(source, "sourceChannel", 40);
        Instant when = requireSourceOccurredAt(occurredAt, "客户反馈时间不能晚于当前时间");
        if (explicitClientBan
                && (!SubmissionProcessStatus.REJECTED.matches(status)
                || CommonUtils.stripToNull(note) == null)) {
            throw PlatformExceptions.unprocessable("CLIENT_RESTRICTION_EVIDENCE_REQUIRED", "客户限制只能基于明确拒绝，且必须填写完整原因和来源");
        }
        String cleanIntent = explicitClientBan ? requireRestrictionIntent(restrictionIntent) : null;
        var actor = authorization.currentActor();
        String feedbackId = ids.newId();
        String replay =
                reserve(
                        actor.getUserId(),
                        rawKey,
                        "SUBMISSION_FEEDBACK",
                        fingerprint(
                                id,
                                status,
                                cleanCategory,
                                cleanNext,
                                CommonUtils.stripToNull(note),
                                cleanSource,
                                occurredAt,
                                explicitClientBan,
                                restrictionExpiresAt,
                                CommonUtils.stripToNull(restrictionIntent)),
                        feedbackId);
        if (replay != null) {
            return submissionFeedbackPersistenceService.feedback(id).stream()
                    .filter(v -> v.getId().equals(replay))
                    .findFirst()
                    .orElseThrow(() -> PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原客户反馈尚未完成"));
        }
        requireSent(value);
        if (!SubmissionProcessStatus.REJECTED.matches(status) && !SubmissionProcessStatus.EXPIRED.matches(status)) {
            blacklist.requireRecruitmentAllowed(value.getCandidateId());
        }
        Instant now = clock.instant();
        if (persistence.updateProcess(id, status, value.getVersion(), actor.getUserId(), now) == 0) {
            throw state();
        }
        submissionFeedbackPersistenceService.insertFeedback(
                feedbackId,
                id,
                status,
                cleanCategory,
                cleanNext,
                CommonUtils.stripToNull(note),
                cleanSource,
                when,
                actor.getUserId(),
                now);
        if (explicitClientBan) {
            appendRestriction(
                    value,
                    "ACTIVATION",
                    ClientRestrictionStatus.ACTIVE.getCode(),
                    cleanIntent,
                    restrictionExpiresAt,
                    required(note, "note", 1000),
                    cleanSource,
                    when,
                    null,
                    true);
        }
        return submissionFeedbackPersistenceService.feedback(id).stream()
                .filter(v -> v.getId().equals(feedbackId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public SubmissionDetails withdraw(String id, String reason, long version, String rawKey) {
        authorization.requirePermission("submission.manage");
        ClientSubmission value = requireLockedVisible(id);
        String clean = required(reason, "reason", 1000);
        var actor = authorization.currentActor();
        String replay =
                reserve(actor.getUserId(), rawKey, "SUBMISSION_WITHDRAW", fingerprint(id, clean, version), id);
        if (replay != null) {
            return details(requireVisible(replay));
        }
        // 先识别已成功的同一命令；新命令仍必须满足撤回前置状态。
        if (!SubmissionDeliveryStatus.SENT.matches(value.getDeliveryStatus())
                || !SubmissionProcessStatus.isClientWorkflowActive(value.getProcessStatus())) {
            throw PlatformExceptions.conflict("SUBMISSION_WITHDRAWAL_NOT_ALLOWED", "只有筛选中或面试中的已发送推荐可以撤回");
        }
        Instant now = clock.instant();
        if (persistence.withdraw(id, clean, actor.getUserId(), version, now) == 0) {
            throw state();
        }
        applications.withdrawBoundClientStep(value.getApplicationId(), id, clean);
        return details(persistence.find(id));
    }

    @Transactional
    public RiskAcknowledgement acknowledgeRisk(String id, String riskCode, String reason) {
        authorization.requirePermission("submission.manage");
        ClientSubmission value = requireVisible(id);
        String recordId = ids.newId();
        var actor = authorization.currentActor();
        Instant now = clock.instant();
        riskAcknowledgementPersistenceService.insertRiskAck(
                recordId,
                value.getId(),
                required(riskCode, "riskCode", 60),
                required(reason, "reason", 1000),
                actor.getUserId(),
                now);
        return riskAcknowledgementPersistenceService.riskAcks(id).stream()
                .filter(v -> v.getId().equals(recordId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 更正限制。
     */
    @Transactional
    public ClientRestriction correctRestriction(
            String restrictionId,
            String intent,
            Instant expiresAt,
            String detail,
            String source,
            Instant occurredAt) {
        authorization.requirePermission("submission.manage");
        String cleanIntent = requireRestrictionIntent(intent);
        ClientRestriction prior = requiredRestriction(restrictionId);
        ClientRestriction current =
                persistence.currentRestriction(prior.getClientId(), prior.getCandidateId());
        if (current == null || !current.getId().equals(prior.getId())) {
            throw PlatformExceptions.conflict("CLIENT_RESTRICTION_NOT_CURRENT", "只能更正当前客户限制");
        }
        if (prior.getSourceApplicationId() != null) {
            return reviseApplicationRestriction(prior, ClientRestrictionStatus.CORRECTED.getCode(), cleanIntent,
                    expiresAt, required(detail, "reasonDetail", 1000), required(source, "sourceChannel", 40),
                    occurredAt, true);
        }
        ClientSubmission submission = requireVisible(prior.getSourceSubmissionId());
        return appendRestriction(
                submission,
                "CORRECTION",
                ClientRestrictionStatus.CORRECTED.getCode(),
                cleanIntent,
                expiresAt,
                required(detail, "reasonDetail", 1000),
                required(source, "sourceChannel", 40),
                occurredAt,
                prior.getId(),
                true);
    }

    /**
     * 停用限制。
     */
    @Transactional
    public ClientRestriction disableRestriction(String restrictionId, String reason) {
        authorization.requirePermission("submission.manage");
        ClientRestriction prior = requiredRestriction(restrictionId);
        ClientRestriction current =
                persistence.currentRestriction(prior.getClientId(), prior.getCandidateId());
        if (current == null || !current.getId().equals(prior.getId())) {
            throw PlatformExceptions.conflict("CLIENT_RESTRICTION_NOT_CURRENT", "只能停用当前客户限制");
        }
        if (prior.getSourceApplicationId() != null) {
            return reviseApplicationRestriction(prior, ClientRestrictionStatus.DISABLED.getCode(),
                    ClientRestrictionStatus.DISABLED.getCode(), prior.getExpiresAt(),
                    required(reason, "reason", 1000), "MANUAL", clock.instant(), false);
        }
        ClientSubmission submission = requireVisible(prior.getSourceSubmissionId());
        return appendRestriction(
                submission,
                "DISABLE",
                ClientRestrictionStatus.DISABLED.getCode(),
                ClientRestrictionStatus.DISABLED.getCode(),
                prior.getExpiresAt(),
                required(reason, "reason", 1000),
                "MANUAL",
                clock.instant(),
                prior.getId(),
                false);
    }

    @Transactional(readOnly = true)
    public List<ClientRestriction> restrictions(String candidateId, String clientId, int limit) {
        if (!sharedCandidate(candidateId)) authorization.requirePermission("submission.read");
        return clientRestrictionPersistenceService.restrictions(
                require(candidateId, "candidateId"),
                CommonUtils.stripToNull(clientId),
                Math.clamp(limit, 1, 200));
    }

    @Transactional(readOnly = true)
    public Eligibility eligibility(String applicationId) {
        var app = applications.requireSubmissionContext(applicationId);
        var demand = demands.requireSubmissionContextForJdVersion(app.jdVersionId());
        boolean company = blacklist.isCompanyBlacklisted(app.candidateId()),
                client =
                        clientRestrictionCurrentPersistenceService.hasRestriction(demand.clientId(), app.candidateId(), clock.instant()) > 0;
        return new Eligibility(app.applicationId(), app.candidateId(), demand.clientId(),
                company, client, !company, company ? List.of("COMPANY_BLACKLIST_BLOCKED") : List.of());
    }

    private ClientRestriction appendRestriction(
            ClientSubmission value,
            String type,
            String status,
            String intent,
            Instant expiresAt,
            String detail,
            String source,
            Instant occurredAt,
            String corrects,
            boolean active) {
        // 停用沿用原记录的到期时间留痕；只有生效限制需要校验新期限。
        if (active && expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw PlatformExceptions.unprocessable("CLIENT_RESTRICTION_EXPIRY_INVALID", "限制到期时间必须晚于当前时间");
        }
        Instant when = requireSourceOccurredAt(occurredAt, "来源时间不能晚于当前时间");
        String recordId = ids.newId();
        var actor = authorization.currentActor();
        Instant now = clock.instant();
        clientRestrictionPersistenceService.insertRestriction(
                recordId,
                value.getClientId(),
                value.getCandidateId(),
                value.getId(),
                type,
                status,
                intent,
                expiresAt,
                detail,
                source,
                when,
                corrects,
                actor.getUserId(),
                now);
        if (corrects == null) {
            persistence.projectRestriction(
                    value.getClientId(), value.getCandidateId(), recordId, active, expiresAt, actor.getUserId(), now);
        } else if (persistence.replaceRestrictionProjection(
                value.getClientId(), value.getCandidateId(), corrects, recordId, active, expiresAt, actor.getUserId(), now) != 1) {
            throw PlatformExceptions.conflict("CLIENT_RESTRICTION_NOT_CURRENT", "客户限制已被修改，请刷新后重试");
        }
        return clientRestrictionPersistenceService.restriction(recordId);
    }

    private Instant requireSourceOccurredAt(Instant supplied, String message) {
        Instant now = clock.instant();
        Instant occurred = supplied == null ? now : supplied;
        if (occurred.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("SUBMISSION_FEEDBACK_TIME_INVALID", message);
        }
        return occurred;
    }

    private SubmissionDetails details(ClientSubmission value) {
        if (value == null) {
            throw PlatformExceptions.notFound("SUBMISSION_NOT_FOUND", "客户推荐不存在");
        }
        return new SubmissionDetails(
                value,
                sendSnapshotPersistenceService.snapshot(value.getId()),
                submissionFeedbackPersistenceService.feedback(value.getId()),
                riskAcknowledgementPersistenceService.riskAcks(value.getId()));
    }

    private boolean sharedCandidate(String candidateId) {
        authorization.currentActor();
        return candidateId != null && candidateLibrary.eligibleCandidateIds(List.of(candidateId)).contains(candidateId);
    }

    private ClientSubmission requireVisible(String id) {
        ClientSubmission value = persistence.find(id);
        if (value == null) {
            throw PlatformExceptions.notFound("SUBMISSION_NOT_FOUND", "客户推荐不存在");
        }
        if (!canView(value)) {
            throw PlatformExceptions.forbidden("SUBMISSION_ACCESS_FORBIDDEN", "无权访问该客户推荐");
        }
        return value;
    }

    private ClientSubmission requireLockedVisible(String id) {
        String candidateId = persistence.findCandidateId(id);
        if (candidateId == null) {
            throw PlatformExceptions.notFound("SUBMISSION_NOT_FOUND", "客户推荐不存在");
        }
        blacklist.lockCandidateForCommand(candidateId);
        ClientSubmission value = persistence.lock(id);
        if (value == null) {
            throw PlatformExceptions.notFound("SUBMISSION_NOT_FOUND", "客户推荐不存在");
        }
        if (!canView(value)) {
            throw PlatformExceptions.forbidden("SUBMISSION_ACCESS_FORBIDDEN", "无权访问该客户推荐");
        }
        return value;
    }

    private boolean canView(ClientSubmission value) {
        return canView(authorization.currentActor(), value);
    }

    private boolean canView(
            CurrentActorSnapshot actor, ClientSubmission value) {
        return authorization.canAccess(
                actor,
                "submission.read",
                new AuthorizationTarget(
                        "CLIENT_SUBMISSION",
                        value.getId(),
                        value.getOwnerUserId(),
                        value.getOwnerDepartmentId(),
                        Set.of()));
    }

    private static void requireDraft(ClientSubmission value) {
        if (!SubmissionDeliveryStatus.DRAFT.matches(value.getDeliveryStatus())) {
            throw PlatformExceptions.conflict("SUBMISSION_NOT_DRAFT", "只有草稿推荐可以发送");
        }
    }

    /**
     * 校验并获取发送。
     */
    private static void requireSent(ClientSubmission value) {
        if (!SubmissionDeliveryStatus.SENT.matches(value.getDeliveryStatus())) {
            throw PlatformExceptions.conflict("SUBMISSION_NOT_SENT", "只有已发送推荐可以记录客户反馈");
        }
    }

    private static String requireRestrictionIntent(String intent) {
        if (!ClientRestrictionIntent.supports(intent)) {
            throw PlatformExceptions.unprocessable("CLIENT_RESTRICTION_INTENT_INVALID", "请选择有效的客户禁推意图");
        }
        return intent;
    }

    private ClientRestriction requiredRestriction(String id) {
        ClientRestriction value = clientRestrictionPersistenceService.restriction(id);
        if (value == null) {
            throw PlatformExceptions.notFound("CLIENT_RESTRICTION_NOT_FOUND", "客户限制记录不存在");
        }
        return value;
    }

    private String reserve(String actor, String raw, String action, String request, String target) {
        String key = IdempotencyKey.fromHttpHeader(raw);
        IdempotencyRecord found = submissionIdempotencyPersistenceService.idempotency(actor, key);
        if (found != null) {
            return verify(found, action, request);
        }
        if (persistence.insertIdempotency(actor, key, action, request, target, clock.instant()) == 1) {
            return null;
        }
        found = submissionIdempotencyPersistenceService.idempotencyForUpdate(actor, key);
        if (found == null) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "幂等请求正在处理中");
        }
        return verify(found, action, request);
    }

    private static String verify(IdempotencyRecord value, String action, String request) {
        if (!action.equals(value.getActionCode()) || !request.equals(value.getRequestFingerprint())) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同客户推荐命令");
        }
        return value.getTargetId();
    }

    private static String fingerprint(Object... values) {
        return Sha256Utils.fingerprintObjects(31, values);
    }

    /**
     * 规范化 D。
     */
    private static String normalized(String value) {
        return CommonUtils.stripToNull(value) == null ? null : CommonUtils.upperOrEmpty(value);
    }

    private static PlatformException state() {
        return PlatformExceptions.conflict("SUBMISSION_STATE_CHANGED", "客户推荐状态已变化，请刷新后重试");
    }
}

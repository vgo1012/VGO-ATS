package com.ezaigc.hr.talent.application.manager;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.versionConflict;
import static com.ezaigc.hr.common.web.pagination.CursorPages.page;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.common.web.request.RequiredText.require;
import static com.ezaigc.hr.talent.constant.TalentConstants.CANDIDATE_EMAIL_INPUT_PATTERN;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_CANDIDATE_QUERY_LENGTH;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_DOWNLOAD_TOKEN_PATTERN;
import static com.ezaigc.hr.talent.domain.validation.ResumeUploadRules.isValidSize;
import static com.ezaigc.hr.talent.domain.validation.ResumeUploadRules.normalizeFileName;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.persistence.OptimisticUpdates;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.support.IdentityConflictRecorder;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.AssignIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ImportIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.LinkIntakeBatchJdDTO;
import com.ezaigc.hr.talent.domain.dto.CreateIntakeBatchDTO;
import com.ezaigc.hr.talent.domain.dto.CreateSourceChannelDTO;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateCandidateDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateSourceChannelDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.domain.response.DownloadGrantResponse;
import com.ezaigc.hr.talent.domain.response.DownloadedResumeResponse;
import com.ezaigc.hr.talent.domain.response.IdentityConflictResponse;
import com.ezaigc.hr.talent.domain.response.IntakeBatchResponse;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.domain.response.ResumePreviewResponse;
import com.ezaigc.hr.talent.domain.response.ResumeVersionResponse;
import com.ezaigc.hr.talent.domain.response.SourceChannelResponse;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.DuplicateResolution;
import com.ezaigc.hr.talent.enums.IdentityConflictStatus;
import com.ezaigc.hr.talent.enums.IntakeBatchStatus;
import com.ezaigc.hr.talent.enums.IntakeSourceType;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import com.ezaigc.hr.talent.enums.ResumeIntakeCompletionConflict;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeImportStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeScoreStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.enums.SourceChannelStatus;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import com.ezaigc.hr.talent.service.CandidateMergeHistoryPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.DownloadAuthorizationPersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.IntakeBatchPersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.api.InterviewMaterialAccessService;
import com.ezaigc.hr.talent.domain.response.InterviewMaterialResponse;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import com.ezaigc.hr.talent.enums.ResumeLifecyclePhase;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import tools.jackson.databind.ObjectMapper;

/**
 * 人才接收应用服务，负责用例编排、权限校验和事务边界。
 */
@Service
@Transactional
public class TalentIntakeApplicationManager {
    @Resource private JobContextQuery jobContexts;
    @Resource private ResumeIntakeScoringService intakeScoring;
    @Resource private CandidateResumeVersionService routing;
    @Resource private CandidateRecruitmentStateQuery recruitment;
    @Resource private CandidateScreeningStateQuery screening;
    @Resource private ResumeParsingPersistenceService resumeFacts;
    @Resource private ResumeParsingApplicationManager resumeParsing;
    @Resource private BackgroundActorExecutor backgroundActors;


    private static final Logger LOGGER = LoggerFactory.getLogger(TalentIntakeApplicationManager.class);
    private static final String IDENTITY_CONFLICT_REQUIRES_REVIEW = "IDENTITY_CONFLICT_REQUIRES_REVIEW";
    private static final ObjectMapper MERGE_JSON = new ObjectMapper();

    @Resource
    private TalentIntakeAssembler assembler;

    public CursorPage<SourceChannelResponse> channelResponses(String s, String c, Integer l) {
        return assembler.toChannelResponses(channels(s, c, l));
    }

    /**
     * 创建通知或沟通渠道。
     */
    public SourceChannelResponse createChannel(CreateSourceChannelDTO d, String k) {
        return assembler.toResponse(createChannelCore(d, k));
    }

    public SourceChannelResponse channelResponse(String id) {
        return assembler.toResponse(channel(id));
    }

    /**
     * 更新通知或沟通渠道。
     */
    public SourceChannelResponse updateChannel(String id, UpdateSourceChannelDTO d) {
        return assembler.toResponse(updateChannelCore(id, d));
    }

    /**
     * 停用通知或沟通渠道。
     */
    public SourceChannelResponse disableChannel(String id, TalentVersionReasonDTO d) {
        return assembler.toResponse(disableChannelCore(id, d));
    }

    /** 删除尚未被历史事实引用的渠道。 */
    public void deleteChannel(String id, TalentVersionReasonDTO d) {
        deleteChannelCore(id, d);
    }

    /**
     * 创建批次。
     */
    public IntakeBatchResponse createBatch(CreateIntakeBatchDTO d, String k) {
        return assembler.toResponse(createBatchCore(d, k));
    }

    public IntakeBatchResponse batchResponse(String id) {
        IntakeBatch batch = intakeBatchPersistenceService.findBatchWithProgress(id);
        if (batch == null) {
            throw PlatformExceptions.notFound("INTAKE_BATCH_NOT_FOUND", "收件批次不存在");
        }
        authorization.requireAccess("talent.read", target(batch));
        return assembler.toResponse(batch);
    }

    @Transactional(readOnly = true)
    public CursorPage<IntakeBatchResponse> batchResponses(String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.read");
        int limit = limit(requestedLimit);
        String scope = actorScope("talent.intake-batches", actor);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        return page(intakeBatchPersistenceService.findBatches(actor.getUserId(),
                        actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(),
                        time(c), id(c), limit + 1),
                limit, cursorCodec, scope, IntakeBatch::getCreateTime, IntakeBatch::getId)
                .map(assembler::toResponse);
    }

    /**
     * 上传响应。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResumeIntakeResponse uploadResponse(String id, ResumeUploadDTO f, String k) {
        return intakeResponse(upload(id, f, k));
    }

    /**
     * 完成批次响应。
     */
    public IntakeBatchResponse completeBatchResponse(String id) {
        completeBatch(id);
        return batchResponse(id);
    }

    public CursorPage<ResumeIntakeResponse> intakeResponses(
            String batchId, String s, String importStatus, Boolean duplicateContent, String q, String n,
            Instant from, Instant to, String c, Integer l) {
        CursorPage<ResumeIntake> page = intakes(
                batchId, s, importStatus, duplicateContent, q, n, from, to, c, l);
        populateJdTitles(page.getItems());
        return assembler.toIntakeResponses(page);
    }

    /** 兼容历史待入库收件的人工启动；新上传在收件事务内启动。 */
    public ResumeIntakeResponse importIntake(String id, ImportIntakeDTO command) {
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        return intakeResponse(startImport(intake, command.getVersion(), authorization.currentActor().getUserId(),
                clock.instant()));
    }

    private ResumeIntake startImport(ResumeIntake intake, long version, String actorId, Instant now) {
        if (ResumeIntakeStatus.PARSING.matches(intake.getStatus())
                || ResumeIntakeStatus.CONFIRMED.matches(intake.getStatus())
                || ResumeIntakeStatus.COMPLETED.matches(intake.getStatus())) {
            return intake;
        }
        if (!ResumeIntakeStatus.PENDING_IMPORT.matches(intake.getStatus())
                || !MalwareScanVerdict.CLEAN.matches(intake.getScanStatus())) {
            throw PlatformExceptions.conflict("INTAKE_NOT_IMPORTABLE", "当前收件不能直接入库，请先处理行内提示");
        }
        if (CommonUtils.isBlank(intake.getJdVersionId())) {
            throw PlatformExceptions.conflict("INTAKE_JD_VERSION_REQUIRED", "请先核对并关联 JD，再启动入库");
        }
        if (version != intake.getVersion()) {
            throw versionConflict();
        }
        String id = intake.getId();
        if (persistence.updateIntakeStatus(id, ResumeIntakeStatus.PARSING.getCode(), null,
                version, actorId, now) != 1) {
            throw versionConflict();
        }
        boolean reused = intakeProcessing.insertProcessing(ids.newId(), intake.getContentSha256(),
                intake.getOriginalFileName(), intake.getMediaType(), intake.getObjectKey(),
                actorId, now) == 0;
        var shared = intakeProcessing.findByHash(intake.getContentSha256());
        if (shared == null || persistence.attachProcessing(id, shared.getId(), actorId, now) != 1) {
            throw new IllegalStateException("收件无法关联共享解析处理");
        }
        if (ResumeIntakeProcessingStatus.FAILED_FINAL.matches(shared.getStatus())) {
            intakeProcessing.retry(shared.getId(), actorId, now);
        }
        event("RESUME_INTAKE_IMPORT_STARTED", actorId, "RESUME_INTAKE", id, null, now);
        ResumeIntake result = requireIntake(id);
        result.setDuplicateContent(result.isDuplicateContent() || reused);
        return result;
    }

    public ResumeIntakeResponse assign(String id, AssignIntakeDTO d) {
        return intakeResponse(assignCore(id, d));
    }

    @Transactional(propagation = Propagation.NEVER)
    public ResumeIntakeResponse confirm(String id, ConfirmIntakeDTO d) {
        return intakeResponse(confirmWithoutHeldLocks(id, d));
    }

    /**
     * 解析成功后由后台请求人上下文调用，候选人只在事实已存在后创建。
     *
     * <p>解析态推进先提交；身份冲突在确认事务回滚、释放候选人及收件锁后独立保存，避免外键检查自阻塞。
     */
    @Transactional(propagation = Propagation.NEVER)
    public ResumeIntake autoConfirm(String id, ResumeFacts facts) {
        if (facts == null || facts.candidate() == null || CommonUtils.isBlank(facts.candidate().fullName())) {
            throw PlatformExceptions.conflict("IDENTITY_REVIEW_REQUIRED", "解析结果缺少姓名，需要人工确认身份");
        }
        if (CommonUtils.isBlank(facts.candidate().email())
                && CommonUtils.isBlank(facts.candidate().phone())) {
            throw PlatformExceptions.conflict("IDENTITY_REVIEW_REQUIRED", "解析结果缺少邮箱和手机号，需要人工确认身份");
        }
        ResumeIntake ready = transactionTemplate.execute(ignored -> prepareAutoConfirmation(id));
        if (ready == null) {
            throw new IllegalStateException("auto confirmation preparation returned no intake");
        }
        if (ResumeIntakeStatus.CONFIRMED.matches(ready.getStatus())
                || ResumeIntakeStatus.COMPLETED.matches(ready.getStatus())) {
            return ready;
        }
        return confirmWithoutHeldLocks(id, new ConfirmIntakeDTO(null, facts.candidate().fullName(),
                facts.candidate().email(), facts.candidate().phone(), ready.getVersion()));
    }

    private ResumeIntake confirmWithoutHeldLocks(String id, ConfirmIntakeDTO command) {
        // 只复制确认阶段的模板；保留 manager/传播/只读设置，合并后简历归属读使用新快照。
        TransactionTemplate confirmation = new TransactionTemplate(
                transactionTemplate.getTransactionManager(), transactionTemplate);
        confirmation.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            ResumeIntake confirmed = confirmation.execute(ignored -> confirmCore(id, command));
            if (confirmed == null) {
                throw new IllegalStateException("confirmation returned no intake");
            }
            return confirmed;
        } catch (IdentityConflictDetected conflict) {
            conflictRecorder.record(id, conflict.candidateA, conflict.candidateB, conflict.type, conflict.intakeVersion);
            throw conflict.failure;
        }
    }

    private static final class IdentityConflictDetected extends RuntimeException {
        private final String candidateA;
        private final String candidateB;
        private final String type;
        private final PlatformException failure;
        private final long intakeVersion;

        private IdentityConflictDetected(ResumeIntake intake, String candidateA, String candidateB, String type, String detail) {
            this.candidateA = candidateA;
            this.candidateB = candidateB;
            this.type = type;
            this.failure = PlatformExceptions.conflict(IDENTITY_CONFLICT_REQUIRES_REVIEW, detail);
            this.intakeVersion = intake.getVersion();
        }
    }

    private ResumeIntake prepareAutoConfirmation(String id) {
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        if (ResumeIntakeStatus.CONFIRMED.matches(intake.getStatus())
                || ResumeIntakeStatus.COMPLETED.matches(intake.getStatus())) {
            return intake;
        }
        if (!ResumeIntakeStatus.PARSING.matches(intake.getStatus())) {
            throw PlatformExceptions.conflict(
                    ResumeIntakeCompletionConflict.INTAKE_NOT_PARSING.getCode(), "收件已离开自动处理状态");
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (persistence.advanceIntake(id, ResumeIntakeStatus.PARSING.getCode(),
                ResumeIntakeStatus.READY_FOR_CONFIRMATION.getCode(), null,
                actor.getUserId(), now) != 1) {
            throw PlatformExceptions.conflict(
                    ResumeIntakeCompletionConflict.INTAKE_NOT_PARSING.getCode(), "收件已离开自动处理状态");
        }
        return requireIntake(id);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResumeIntakeResponse retry(String id, TalentVersionReasonDTO d) {
        return intakeResponse(retryDetached(id, d));
    }

    /** 身份信息缺失时只重跑当前收件，原共享成功结果和其他收件保持不变。 */
    public ResumeIntakeResponse reparse(String id, TalentVersionReasonDTO command) {
        String reason = require(command.getReason(), 500);
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        if (command.getVersion() != intake.getVersion()) {
            throw versionConflict();
        }
        if (!ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getStatus())
                || !ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getFailureCode())
                || !MalwareScanVerdict.CLEAN.matches(intake.getScanStatus())
                || intake.getProcessingId() == null || intake.getCandidateId() != null
                || intake.getResumeVersionId() != null) {
            throw PlatformExceptions.conflict("INTAKE_NOT_REPARSABLE", "只有解析缺少身份信息的未入库收件可以重新解析");
        }
        var previous = intakeProcessing.findProcessing(intake.getProcessingId());
        if (previous == null || !ResumeIntakeProcessingStatus.SUCCEEDED.matches(previous.getStatus())) {
            throw PlatformExceptions.conflict("INTAKE_RESULT_NOT_READY", "原解析结果尚未完成，请先刷新解析校验");
        }
        String actorId = authorization.currentActor().getUserId();
        String processingId = ids.newId();
        Instant now = clock.instant();
        if (intakeProcessing.insertReparseProcessing(processingId, intake.getContentSha256(),
                intake.getOriginalFileName(), intake.getMediaType(), intake.getObjectKey(), actorId, now) != 1
                || persistence.reparseIntake(id, command.getVersion(), intake.getProcessingId(),
                        processingId, actorId, now) != 1) {
            throw versionConflict();
        }
        event("RESUME_INTAKE_REPARSE_STARTED", actorId, "RESUME_INTAKE", id, reason, now);
        return intakeResponse(requireIntake(id));
    }

    public ResumeIntakeResponse retryScore(String id, TalentVersionReasonDTO command) {
        String reason = require(command.getReason(), 500);
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        if (command.getVersion() != intake.getVersion()) {
            throw versionConflict();
        }
        if (!ResumeIntakeScoreStatus.FAILED_RETRYABLE.matches(intake.getScoreStatus())) {
            throw PlatformExceptions.conflict("INTAKE_SCORE_NOT_RETRYABLE", "当前收件评分不能重试");
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        boolean existingRun = !CommonUtils.isBlank(intake.getScoreRunId());
        var task = existingRun
                ? intakeScoring.get(intake.getScoreRunId(), actor.getUserId())
                : intakeScoring.enqueue(intake.getJdVersionId(), intake.getResumeVersionId(), actor.getUserId());
        // 收件失败可能只是状态查询失败，不能重复启动仍在运行或已经完成的评分。
        if (existingRun && AsyncTaskStatus.FAILED.matches(task.status()) && task.retryable()) {
            task = intakeScoring.retry(intake.getScoreRunId(), actor.getUserId());
        }
        String status = ResumeIntakeScoreStatus.fromTask(task.status(), task.retryable()).getCode();
        if (persistence.updateScore(id, intake.getVersion(), task.runId(), status, task.errorCode(),
                actor.getUserId(), clock.instant()) == 0) {
            throw versionConflict();
        }
        event("RESUME_INTAKE_SCORE_RETRIED", actor.getUserId(), "RESUME_INTAKE", id,
                reason, clock.instant());
        return intakeResponse(requireIntake(id));
    }

    public ResumeIntakeResponse ignore(String id, TalentVersionReasonDTO d) {
        return intakeResponse(ignoreCore(id, d));
    }

    private ResumeIntakeResponse intakeResponse(ResumeIntake intake) {
        populateJdTitles(List.of(intake));
        return assembler.toResponse(intake);
    }

    private void populateJdTitles(List<ResumeIntake> values) {
        Set<String> ids = values.stream().map(ResumeIntake::getJdVersionId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        var contexts = jobContexts.visibleVersions(ids);
        for (ResumeIntake value : values) {
            var context = contexts.get(value.getJdVersionId());
            value.setJdTitle(context == null ? null : context.getTitle());
            value.setJdCode(context == null ? null : context.getJdCode());
        }
    }

    public CursorPage<CandidateResponse> candidateResponses(String q, String c, Integer l) {
        return candidateResponses(q, c, l, false);
    }

    public CursorPage<CandidateResponse> candidateResponses(
            String q, String c, Integer l, boolean poolOnly) {
        return candidateResponses(q,c,l,poolOnly,null);
    }

    public CursorPage<CandidateResponse> candidateResponses(String q,String c,Integer l,boolean poolOnly,String poolStage) {
        return assembler.toCandidateResponses(candidates(q,c,l,poolOnly,poolStage));
    }

    public CandidateResponse candidateResponse(String id) {
        return assembler.toResponse(candidate(id));
    }

    /**
     * 更新候选人。
     */
    public CandidateResponse updateCandidate(String id, UpdateCandidateDTO d) {
        return assembler.toResponse(updateCandidateCore(id, d));
    }

    /**
     * 删除候选人。
     */
    public void deleteCandidate(String id, TalentVersionReasonDTO d) {
        deleteCandidateCore(id, d);
    }

    public CursorPage<ResumeVersionResponse> resumeVersionResponses(String id, String c, Integer l) {
        return assembler.toResumeVersionResponses(resumeVersions(id, c, l));
    }

    /**
     * 预览响应。
     */
    public ResumePreviewResponse previewResponse(String id) {
        return preview(id);
    }

    /**
     * 校验并创建授权下载响应。
     */
    public DownloadGrantResponse authorizeDownloadResponse(String id) {
        return authorizeDownload(id);
    }

    /**
     * 下载响应。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DownloadedResumeResponse downloadResponse(String t) {
        return download(t);
    }

    public CursorPage<IdentityConflictResponse> conflictResponses(
            String s, List<String> intakeIds, String candidateId, String c, Integer l) {
        return visibleConflictResponses(conflicts(s, intakeIds, candidateId, c, l));
    }

    /**
     * 解析并确定冲突。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IdentityConflictResponse resolveConflict(String id, ResolveIdentityConflictDTO d) {
        return visibleConflictResponses(new CursorPage<>(List.of(resolveConflictCore(id, d)), null, false))
                .getItems().getFirst();
    }

    /** 已入库简历事实变化后，显式启动一轮新的公司默认评分。 */
    public ResumeIntakeResponse recalculateScore(String id, TalentVersionReasonDTO command) {
        String reason = require(command.getReason(), 500);
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        authorization.requirePermission("matching.run");
        if (command.getVersion() != intake.getVersion()) {
            throw versionConflict();
        }
        if (!ResumeIntakeStatus.COMPLETED.matches(intake.getStatus())
                || !ResumeIntakeScoreStatus.SUCCEEDED.matches(intake.getScoreStatus())
                || CommonUtils.isBlank(intake.getJdVersionId())
                || CommonUtils.isBlank(intake.getResumeVersionId())
                || CommonUtils.isBlank(intake.getCandidateId())
                || CommonUtils.isBlank(intake.getScoreRunId())) {
            throw PlatformExceptions.conflict("INTAKE_SCORE_NOT_RECALCULABLE", "当前收件不能重算默认评分");
        }
        String candidateId = intake.getCandidateId();
        String resumeVersionId = intake.getResumeVersionId();
        routing.lockForRouting(candidateId, resumeVersionId);
        Candidate candidate = candidatePersistenceService.findCandidate(candidateId);
        if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
            throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用");
        }
        boolean currentScore = persistence.findCurrentCandidateResumes(List.of(candidateId)).stream()
                .anyMatch(row -> resumeVersionId.equals(row.getResumeVersionId())
                        && intake.getJdVersionId().equals(row.getJdVersionId())
                        && intake.getScoreRunId().equals(row.getDefaultMatchRunId()));
        if (!currentScore) {
            throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "当前简历或默认评分已更新，请刷新后重试");
        }
        if (recruitment.hasActiveRecruitment(resumeVersionId)) {
            throw PlatformExceptions.conflict("RECRUITMENT_ACTIVE", "候选人仍在招聘流程中，不能重新评分");
        }
        if (screening.activeResumeVersionIds(List.of(candidateId)).contains(resumeVersionId)) {
            throw PlatformExceptions.conflict("SCREENING_ACTIVE", "候选人仍在智能筛选中，请先完成或退回");
        }
        var fact = resumeFacts.findCurrentFactSnapshotForUpdate(resumeVersionId);
        if (fact == null) {
            throw PlatformExceptions.conflict("RESUME_FACTS_REQUIRED", "简历当前版本尚无有效事实快照");
        }
        String actorId = authorization.currentActor().getUserId();
        String scoredFactId = intakeScoring.scoredFactSnapshotId(intake.getScoreRunId(),
                intake.getJdVersionId(), resumeVersionId, actorId);
        if (fact.getId().equals(scoredFactId)) {
            throw PlatformExceptions.conflict("INTAKE_SCORE_FACT_UNCHANGED", "简历事实未变化，无需重算默认评分");
        }
        String scoringActorId = intake.getHandledByUserId() == null
                ? intake.getSourceRecruiterUserId() : intake.getHandledByUserId();
        var task = intakeScoring.enqueueRescore(intake.getJdVersionId(), resumeVersionId, scoringActorId,
                "intake-recalculate:" + id + ":" + fact.getId());
        String status = ResumeIntakeScoreStatus.fromTask(task.status(), task.retryable()).getCode();
        Instant now = clock.instant();
        if (persistence.updateScore(id, intake.getVersion(), task.runId(), status, task.errorCode(),
                actorId, now) == 0) {
            throw versionConflict();
        }
        event("RESUME_INTAKE_SCORE_RECALCULATED_REQUESTED", actorId, "RESUME_INTAKE", id,
                reason, now);
        return intakeResponse(requireIntake(id));
    }

    private CursorPage<IdentityConflictResponse> visibleConflictResponses(
            CursorPage<IdentityConflict> page) {
        Set<String> candidateIds = new LinkedHashSet<>();
        for (IdentityConflict conflict : page.getItems()) {
            if (conflict.getCandidateIdA() != null) candidateIds.add(conflict.getCandidateIdA());
            if (conflict.getCandidateIdB() != null) candidateIds.add(conflict.getCandidateIdB());
            if (conflict.getChosenCandidateId() != null) candidateIds.add(conflict.getChosenCandidateId());
        }
        List<Candidate> referenced = candidateIds.isEmpty() ? List.of()
                : List.copyOf(candidatePersistenceService.listByIds(candidateIds));
        CandidateReadDecision readable = candidateReadAccess.evaluate(referenced);
        return page.map(conflict -> {
            IdentityConflictResponse response = assembler.toResponse(conflict);
            if (conflict.getCandidateIdA() != null
                    && !readable.canReadCandidate(conflict.getCandidateIdA())) response.setCandidateIdA(null);
            if (conflict.getCandidateIdB() != null
                    && !readable.canReadCandidate(conflict.getCandidateIdB())) response.setCandidateIdB(null);
            if (conflict.getChosenCandidateId() != null
                    && !readable.canReadCandidate(conflict.getChosenCandidateId())) response.setChosenCandidateId(null);
            return response;
        });
    }

    @Resource
    private IdentityConflictRecorder conflictRecorder;
    @Resource
    private ResumeSearchProjectionRecorder searchProjection;
    @Resource
    private PlatformIdGenerator ids;
    @Resource
    private Clock clock;

    @Resource
    private TalentIntakePersistenceService persistence;

    @Resource
    private InterviewMaterialAccessService interviewMaterialAccess;
    @Resource
    private CandidateIdentityPersistenceService candidateIdentityPersistenceService;
    @Resource
    private CandidateMergeHistoryPersistenceService candidateMergeHistoryPersistenceService;
    @Resource
    private CandidatePersistenceService candidatePersistenceService;
    @Resource
    private DownloadAuthorizationPersistenceService downloadAuthorizationPersistenceService;
    @Resource
    private IdentityConflictPersistenceService identityConflictPersistenceService;
    @Resource
    private IntakeBatchPersistenceService intakeBatchPersistenceService;
    @Resource
    private ResumePersistenceService resumePersistenceService;
    @Resource
    private ResumeIntakeProcessingPersistenceService intakeProcessing;
    @Resource
    private ResumeVersionPersistenceService resumeVersionPersistenceService;
    @Resource
    private SourceChannelPersistenceService sourceChannelPersistenceService;
    @Resource
    private TalentIdempotencyPersistenceService talentIdempotencyPersistenceService;
    @Resource
    private TalentProcessEventPersistenceService talentProcessEventPersistenceService;
    @Resource
    private AuthorizationService authorization;
    @Resource
    private CandidateReadAccess candidateReadAccess;
    @Resource
    private CandidateLibraryQueryService candidateLibrary;
    @Resource
    private ObjectStoragePort storage;
    @Resource
    private MalwareScanPort scanner;
    @Resource
    private CursorCodec cursorCodec;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public CursorPage<SourceChannel> channels(String status, String cursor, Integer requestedLimit) {
        authorization.requirePermission("talent.read");
        var actor = authorization.currentActor();
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String scope = actorScope("talent.channels", actor, normalizedStatus);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                sourceChannelPersistenceService.findChannels(normalizedStatus, time(c), id(c), limit + 1),
                limit,
                cursorCodec,
                scope,
                SourceChannel::getUpdateTime,
                SourceChannel::getId);
    }

    /**
     * 创建渠道核心。
     */
    private SourceChannel createChannelCore(
            CreateSourceChannelDTO command, String rawIdempotencyKey) {
        CurrentActorSnapshot actor = authorization.currentActor();
        requireSuperAdmin(actor);
        String name = require(command.getName(), 120);
        String type = channelType(command.getChannelType());
        Instant now = clock.instant();
        String id = ids.newId();
        String code = sourceChannelCode(id);
        String existingTarget =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "SOURCE_CHANNEL_CREATE",
                        fingerprint(name, type),
                        id,
                        now);
      if (existingTarget != null) {
        return requireChannel(existingTarget);
      }
        sourceChannelPersistenceService.insertChannel(id, code, name, type, actor.getUserId(), now);
        event("SOURCE_CHANNEL_CREATED", actor.getUserId(), "SOURCE_CHANNEL", id, null, now);
        return sourceChannelPersistenceService.findChannel(id);
    }

    @Transactional(readOnly = true)
    public SourceChannel channel(String id) {
        authorization.requirePermission("talent.read");
        return requireOwnedChannel(id);
    }

    /**
     * 更新渠道核心。
     */
    private SourceChannel updateChannelCore(String id, UpdateSourceChannelDTO command) {
        CurrentActorSnapshot actor = authorization.currentActor();
        requireSuperAdmin(actor);
        requireChannel(id);
        Instant now = clock.instant();
      if (sourceChannelPersistenceService.updateChannel(
              id,
              require(command.getName(), 120),
              channelType(command.getChannelType()),
              command.getVersion(),
              actor.getUserId(),
              now)
              == 0) {
        throw versionConflict();
      }
        event("SOURCE_CHANNEL_UPDATED", actor.getUserId(), "SOURCE_CHANNEL", id, null, now);
        return sourceChannelPersistenceService.findChannel(id);
    }

    /**
     * 停用渠道核心。
     */
    private SourceChannel disableChannelCore(String id, TalentVersionReasonDTO command) {
        CurrentActorSnapshot actor = authorization.currentActor();
        requireSuperAdmin(actor);
        requireChannel(id);
        Instant now = clock.instant();
      if (sourceChannelPersistenceService.disableChannel(id, command.getVersion(), actor.getUserId(), now) == 0) {
        throw versionConflict();
      }
        event(
                "SOURCE_CHANNEL_DISABLED",
                actor.getUserId(),
                "SOURCE_CHANNEL",
                id,
                require(command.getReason(), 500),
                now);
        return sourceChannelPersistenceService.findChannel(id);
    }

    private void deleteChannelCore(String id, TalentVersionReasonDTO command) {
        CurrentActorSnapshot actor = authorization.currentActor();
        requireSuperAdmin(actor);
        requireChannel(id);
        String reason = require(command.getReason(), 500);
        Instant now = clock.instant();
        try {
            if (sourceChannelPersistenceService.deleteChannel(id, command.getVersion()) == 0) {
                throw versionConflict();
            }
        } catch (DataIntegrityViolationException exception) {
            throw PlatformExceptions.conflict(
                    "SOURCE_CHANNEL_IN_USE", "来源渠道已被收件、简历或报表事实引用，不能删除");
        }
        event("SOURCE_CHANNEL_DELETED", actor.getUserId(), "SOURCE_CHANNEL", id, reason, now);
    }

    /**
     * 创建批次核心。
     */
    private IntakeBatch createBatchCore(CreateIntakeBatchDTO command, String rawIdempotencyKey) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.manage");
        SourceChannel channel = requireChannel(command.getSourceChannelId());
        String owner = CommonUtils.stripOrElse(command.getDefaultOwnerUserId(), actor.getUserId());
        String department =
                CommonUtils.stripOrElse(command.getOwnerDepartmentId(), actor.getHomeDepartmentId());
        String jdVersionId = command.getJdVersionId();
        if (jdVersionId == null || jdVersionId.isBlank()) {
            throw PlatformExceptions.unprocessable("INTAKE_JD_VERSION_REQUIRED", "批量上传必须选择一个岗位版本");
        }
        String requestFingerprint = fingerprint(channel.getId(), owner, department, jdVersionId);
        IdempotencyRecord existing = talentIdempotencyPersistenceService.findIdempotency(
                actor.getUserId(), IdempotencyKey.fromHttpHeader(rawIdempotencyKey));
        if (existing != null) {
            IntakeBatch replay = requireBatch(verifyIdempotency(
                    existing, "INTAKE_BATCH_CREATE", requestFingerprint));
            authorization.requireAccess("talent.manage", target(replay));
            return replay;
        }
      if (!SourceChannelStatus.ACTIVE.matches(channel.getStatus())) {
        throw PlatformExceptions.unprocessable("SOURCE_CHANNEL_DISABLED", "停用来源渠道不能用于新收件");
      }
        requireAssignable(actor, owner, department);
        jobContexts.requireVersionForNewUse(jdVersionId);
        Instant now = clock.instant();
        String id = ids.newId();
        String existingTarget =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "INTAKE_BATCH_CREATE",
                        requestFingerprint,
                        id,
                        now);
      if (existingTarget != null) {
        IntakeBatch replay = requireBatch(existingTarget);
        authorization.requireAccess("talent.manage", target(replay));
        return replay;
      }
        intakeBatchPersistenceService.insertBatch(id, channel.getId(), actor.getUserId(), owner, department, jdVersionId, now);
        event("INTAKE_BATCH_CREATED", actor.getUserId(), "INTAKE_BATCH", id, null, now);
        return intakeBatchPersistenceService.findBatch(id);
    }

    @Transactional(readOnly = true)
    public IntakeBatch batch(String id) {
        IntakeBatch batch = requireBatch(id);
        authorization.requireAccess("talent.read", target(batch));
        return batch;
    }

    /** 为历史收件批次补关联唯一 JD 版本；同值重试幂等，已关联批次不可改绑。 */
    public IntakeBatch linkBatchJdVersion(String id, LinkIntakeBatchJdDTO command) {
        IntakeBatch batch = requireBatch(id);
        authorization.requireAccess("talent.manage", target(batch));
        String jdVersionId = RequiredIdentifier.require(command.getJdVersionId(), "jdVersionId");
        String reason = require(command.getReason(), 500);
        String existing = batch.getJdVersionId();
        if (jdVersionId.equals(existing)) {
            return batch;
        }
        if (!CommonUtils.isBlank(existing)) {
            throw PlatformExceptions.conflict(
                    "INTAKE_BATCH_JD_ALREADY_LINKED", "收件批次已关联 JD，不允许改绑");
        }
        jobContexts.requireVersionForNewUse(jdVersionId);
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (intakeBatchPersistenceService.linkJdVersion(
                id, jdVersionId, actor.getUserId(), now) == 0) {
            IntakeBatch latest = requireBatch(id);
            if (jdVersionId.equals(latest.getJdVersionId())) {
                return latest;
            }
            throw PlatformExceptions.conflict(
                    "INTAKE_BATCH_JD_LINK_CONFLICT", "收件批次已被其他操作关联，请刷新后核对");
        }
        event(
                "INTAKE_BATCH_JD_LINKED",
                actor.getUserId(),
                "INTAKE_BATCH",
                id,
                reason,
                now);
        return requireBatch(id);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResumeIntake upload(String batchId, ResumeUploadDTO file, String rawIdempotencyKey) {
        if (file == null) {
            throw PlatformExceptions.unprocessable("RESUME_FILE_INVALID", "简历文件不能为空");
        }
        String fileName = safeFileName(file.getOriginalFileName());
        long size = file.getSizeBytes();
      if (!isValidSize(size)) {
        throw PlatformExceptions.unprocessable("RESUME_FILE_SIZE_INVALID", "简历文件必须大于 0 且不超过 10 MB");
        }
        String mediaType = mediaType(fileName);
        // NOT_SUPPORTED 仍会建立空事务同步；先用真实短事务完成全部数据库预检，
        // 避免 MyBatis 将连接绑定到随后耗时的摘要、扫描和对象存储阶段。
        UploadPreflight preflight =
                transactionTemplate.execute(
                        ignored -> {
                            IntakeBatch current = requireBatch(batchId);
                            authorization.requireAccess("talent.manage", target(current));
                            if (!IntakeBatchStatus.isTerminal(current.getStatus())) {
                                requireActiveChannel(current);
                            }
                            return new UploadPreflight(current, authorization.currentActor());
                        });
        if (preflight == null) {
            throw new IllegalStateException("resume upload preflight returned no result");
        }
        IntakeBatch batch = preflight.batch();
        CurrentActorSnapshot actor = preflight.actor();
        String sha256;
        try (InputStream input = file.openStream()) {
            sha256 = digest(input);
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("RESUME_FILE_READ_FAILED", "简历文件读取失败", ex);
        }
        if (IntakeBatchStatus.isTerminal(batch.getStatus())) {
            TransactionTemplate readOnly = new TransactionTemplate(
                    transactionTemplate.getTransactionManager(), transactionTemplate);
            readOnly.setReadOnly(true);
            ResumeIntake replay = readOnly.execute(ignored -> replayCompletedUpload(
                    batchId, actor.getUserId(), rawIdempotencyKey,
                    fingerprint(batchId, fileName, Long.toString(size), sha256)));
            if (replay == null) {
                throw new IllegalStateException("completed upload replay returned no intake");
            }
            return replay;
        }
        Instant now = clock.instant();
        String intakeId = ids.newId();
        MalwareScanVerdict scanVerdict;
        try (InputStream input = file.openStream()) {
            scanVerdict = scanner.scan(fileName, input);
        } catch (IOException ex) {
            LOGGER.warn(
                    "resume-malware-scan-failed intakeId={} batchId={}", intakeId, batch.getId(), SafeLogException.from(ex));
            scanVerdict = MalwareScanVerdict.UNAVAILABLE;
        }
        MalwareScanVerdict verdict = scanVerdict;
        String objectKey =
                (verdict == MalwareScanVerdict.CLEAN ? "resume/" : "quarantine/")
                        + sha256.substring(0, 2)
                        + "/"
                        + sha256;
        ResumeIntakeStatus status =
                verdict == MalwareScanVerdict.CLEAN
                        ? ResumeIntakeStatus.PENDING_IMPORT
                        : verdict == MalwareScanVerdict.MALICIOUS
                                ? ResumeIntakeStatus.QUARANTINED
                                : ResumeIntakeStatus.FAILED_RETRYABLE;
        String failure =
                verdict == MalwareScanVerdict.CLEAN
                        ? null
                        : verdict == MalwareScanVerdict.MALICIOUS
                                ? "MALWARE_OR_SIGNATURE_REJECTED"
                                : "MALWARE_SCANNER_UNAVAILABLE";
        storeIfAbsent(objectKey, file);
        ResumeIntake result =
                transactionTemplate.execute(
                        ignored -> {
                            // 外部 I/O 完成后再次校验批次，避免扫描期间状态变化后仍写入。
                            IntakeBatch currentBatch = requireUploadableBatch(batchId);
                            String existingTarget =
                                    reserveIdempotency(
                                            actor.getUserId(),
                                            rawIdempotencyKey,
                                            "RESUME_INTAKE_UPLOAD",
                                            fingerprint(currentBatch.getId(), fileName, Long.toString(size), sha256),
                                            intakeId,
                                            now);
                          if (existingTarget != null) {
                            ResumeIntake existing = requireIntake(existingTarget);
                            if (!batchId.equals(existing.getBatchId())) {
                                throw PlatformExceptions.conflict("IDEMPOTENCY_TARGET_MISMATCH", "幂等请求对应的收件批次不一致");
                            }
                            authorization.requireAccess("talent.manage", target(existing));
                            if (verdict == MalwareScanVerdict.MALICIOUS) {
                                throw PlatformExceptions.unprocessable(
                                        "MALWARE_OR_SIGNATURE_REJECTED", "文件重新扫描未通过，请核查原收件");
                            }
                            // 旧版本可能已接收文件却未完成浏览器端的第二次入库请求。
                            if (MalwareScanVerdict.CLEAN.matches(existing.getScanStatus())
                                    && ResumeIntakeStatus.PENDING_IMPORT.matches(existing.getStatus())
                                    && !CommonUtils.isBlank(existing.getJdVersionId())) {
                                return startImport(existing, existing.getVersion(), actor.getUserId(), now);
                            }
                            return existing;
                          }
                            // 先锁定父批次再插入带外键的收件行，避免并发上传先持父行共享锁、
                            // 随后同时升级排他锁而触发 MySQL 死锁。
                            OptimisticUpdates.requireOne(persistence.incrementBatch(
                                    currentBatch.getId(),
                                    verdict == MalwareScanVerdict.CLEAN ? 1 : 0,
                                    verdict == MalwareScanVerdict.CLEAN ? 0 : 1,
                                    actor.getUserId(),
                                    now), "INTAKE_BATCH_STATE_CONFLICT", "收件批次状态已变化，请重新确认");
                            if (persistence.insertIntake(
                                    intakeId,
                                    currentBatch.getId(),
                                    currentBatch.getSourceChannelId(),
                                    actor.getUserId(),
                                    currentBatch.getDefaultOwnerUserId(),
                                    currentBatch.getOwnerDepartmentId(),
                                    fileName,
                                    mediaType,
                                    size,
                                    sha256,
                                    objectKey,
                                    verdict.name(),
                                    status.getCode(),
                                    failure,
                                    now) != 1) {
                                throw new IllegalStateException("简历收件记录写入失败");
                            }
                            if (talentProcessEventPersistenceService.insertEvent(
                                    ids.newId(),
                                    "RESUME_INTAKE_CREATED",
                                    actor.getUserId(),
                                    "RESUME_INTAKE",
                                    intakeId,
                                    failure,
                                    now) != 1) {
                                throw new IllegalStateException("简历收件审计记录写入失败");
                            }
                            ResumeIntake stored = persistence.findIntake(intakeId);
                            if (stored == null) {
                                throw new IllegalStateException("简历收件写入后无法回读");
                            }
                            return verdict == MalwareScanVerdict.CLEAN
                                    ? startImport(stored, stored.getVersion(), actor.getUserId(), now)
                                    : stored;
                        });
        if (result == null) {
            throw new IllegalStateException("resume upload transaction returned no result");
        }
        return result;
    }

    private ResumeIntake replayCompletedUpload(
            String batchId, String actorId, String rawIdempotencyKey, String requestFingerprint) {
        IntakeBatch batch = requireBatch(batchId);
        authorization.requireAccess("talent.manage", target(batch));
        if (!IntakeBatchStatus.isTerminal(batch.getStatus())) {
            throw PlatformExceptions.conflict("INTAKE_BATCH_STATE_CONFLICT", "收件批次状态已变化，请重新确认");
        }
        IdempotencyRecord record = talentIdempotencyPersistenceService.findIdempotency(
                actorId, IdempotencyKey.fromHttpHeader(rawIdempotencyKey));
        if (record == null) {
            throw PlatformExceptions.conflict("INTAKE_BATCH_COMPLETED", "已结束批次不能继续上传");
        }
        ResumeIntake intake = requireIntake(verifyIdempotency(
                record, "RESUME_INTAKE_UPLOAD", requestFingerprint));
        if (!batchId.equals(intake.getBatchId())) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_TARGET_MISMATCH", "幂等请求对应的收件批次不一致");
        }
        authorization.requireAccess("talent.manage", target(intake));
        return intake;
    }

    @Getter(AccessLevel.PRIVATE)
    @Accessors(fluent = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class UploadPreflight {
        private final IntakeBatch batch;
        private final CurrentActorSnapshot actor;
    }

    /**
     * 完成批次。
     */
    @Transactional
    public IntakeBatch completeBatch(String id) {
        IntakeBatch batch = requireBatch(id);
        authorization.requireAccess("talent.manage", target(batch));
      if (IntakeBatchStatus.isTerminal(batch.getStatus())) {
        return batch;
      }
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
      if (persistence.completeBatch(id, actor.getUserId(), now) == 0) {
        throw PlatformExceptions.conflict("INTAKE_BATCH_STATE_CONFLICT", "收件批次状态已变化");
      }
        event("INTAKE_BATCH_COMPLETED", actor.getUserId(), "INTAKE_BATCH", id, null, now);
        return requireBatch(id);
    }

    /** 总数沿用列表过滤与可见范围，不受当前页游标影响。 */
    @Transactional(readOnly = true)
    public CursorCount countIntakes(
            String status, String importStatus, Boolean duplicateContent, String query, String name,
            Instant from, Instant to) {
        return countIntakes(null, status, importStatus, duplicateContent, query, name, from, to);
    }

    @Transactional(readOnly = true)
    public CursorCount countIntakes(
            String batchId, String status, String importStatus, Boolean duplicateContent,
            String query, String name, Instant from, Instant to) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.read");
        String normalizedImportStatus = validateIntakeFilters(importStatus, from, to);
        long count = persistence.countIntakes(
                optionalBatchId(batchId), CommonUtils.upperToNull(status), normalizedImportStatus, duplicateContent,
                optional(query, "query", MAX_CANDIDATE_QUERY_LENGTH),
                optional(name, "name", MAX_CANDIDATE_QUERY_LENGTH), from, to,
                actor.getUserId(), actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(), Set.of());
        return new CursorCount(count, null, false);
    }

    private String validateIntakeFilters(String importStatus, Instant from, Instant to) {
        String normalizedImportStatus = CommonUtils.upperToNull(importStatus);
        if (normalizedImportStatus != null && !ResumeIntakeImportStatus.supports(normalizedImportStatus)) {
            throw PlatformExceptions.unprocessable("INTAKE_IMPORT_STATUS_INVALID", "入库状态必须为待入库、解析中或已入库");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw PlatformExceptions.unprocessable("INTAKE_TIME_RANGE_INVALID", "from 必须早于 to");
        }
        return normalizedImportStatus;
    }

    private static String optionalBatchId(String batchId) {
        return batchId == null ? null : RequiredIdentifier.require(batchId, "batchId");
    }

    @Transactional(readOnly = true)
    public CursorPage<ResumeIntake> intakes(
            String status, String query, String name, Instant from, Instant to,
            String cursor, Integer requestedLimit) {
        return intakes(null, status, null, null, query, name, from, to, cursor, requestedLimit);
    }

    @Transactional(readOnly = true)
    public CursorPage<ResumeIntake> intakes(
            String status, String importStatus, Boolean duplicateContent, String query, String name,
            Instant from, Instant to, String cursor, Integer requestedLimit) {
        return intakes(null, status, importStatus, duplicateContent, query, name, from, to,
                cursor, requestedLimit);
    }

    @Transactional(readOnly = true)
    public CursorPage<ResumeIntake> intakes(
            String batchId, String status, String importStatus, Boolean duplicateContent,
            String query, String name, Instant from, Instant to, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.read");
        String normalizedImportStatus = validateIntakeFilters(importStatus, from, to);
        String normalizedBatchId = optionalBatchId(batchId);
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String normalizedQuery = optional(query, "query", MAX_CANDIDATE_QUERY_LENGTH);
        String normalizedName = optional(name, "name", MAX_CANDIDATE_QUERY_LENGTH);
        String scope = actorScope("talent.intakes", actor, normalizedBatchId, normalizedStatus, normalizedImportStatus,
                duplicateContent, normalizedQuery, normalizedName, from, to);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findIntakes(
                        normalizedBatchId,
                        normalizedStatus,
                        normalizedImportStatus,
                        duplicateContent,
                        normalizedQuery,
                        normalizedName,
                        from,
                        to,
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getOperationalManagedUserIds(),
                        Set.of(),
                        time(c),
                        id(c),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                ResumeIntake::getCreateTime,
                ResumeIntake::getId);
    }

    /**
     * 分配核心。
     */
    private ResumeIntake assignCore(String id, AssignIntakeDTO command) {
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        CurrentActorSnapshot actor = authorization.currentActor();
        requireAssignable(actor, command.getOwnerUserId(), command.getOwnerDepartmentId());
        Instant now = clock.instant();
      if (persistence.assignIntake(
              id,
              command.getOwnerUserId(),
              command.getOwnerDepartmentId(),
              command.getVersion(),
              actor.getUserId(),
              now)
              == 0) {
        throw versionConflict();
      }
        event(
                "RESUME_INTAKE_ASSIGNED",
                actor.getUserId(),
                "RESUME_INTAKE",
                id,
                require(command.getReason(), 500),
                now);
        return persistence.findIntake(id);
    }

    private ResumeIntake ignoreCore(String id, TalentVersionReasonDTO command) {
        return terminalIntake(id, ResumeIntakeStatus.IGNORED, command);
    }

    /**
     * 重试核心。
     */
    private ResumeIntake retryDetached(String id, TalentVersionReasonDTO command) {
        String reason = require(command.getReason(), 500);
        RetryPreflight preflight = transactionTemplate.execute(ignored -> {
            ResumeIntake intake = requireIntake(id);
            authorization.requireAccess("talent.manage", target(intake));
            if (!ResumeIntakeStatus.FAILED_RETRYABLE.matches(intake.getStatus())) {
                throw PlatformExceptions.conflict("INTAKE_NOT_RETRYABLE", "只有可重试失败收件可以重试");
            }
            if (command.getVersion() != intake.getVersion()) {
                throw versionConflict();
            }
            return new RetryPreflight(intake, authorization.currentActor().getUserId());
        });
        if (preflight == null) {
            throw new IllegalStateException("resume retry preflight returned no result");
        }
        ResumeIntake intake = preflight.intake();
        if (intake.getResumeVersionId() != null && intake.getProcessingId() != null
                && ResumeIntakeProcessingStatus.SUCCEEDED.matches(intake.getProcessingStatus())) {
            var adoption = backgroundActors.execute(preflight.actorId(), () ->
                    resumeParsing.retryIntakeAdoption(
                            intake.getResumeVersionId(), intake.getProcessingId(), reason));
            if (AsyncTaskStatus.FAILED.matches(adoption.status()) && !adoption.retryable()) {
                throw PlatformExceptions.conflict("INTAKE_ADOPTION_NOT_RETRYABLE", "共享解析事实导入失败且不可重试");
            }
            return requireRetryFinalized(id, command.getVersion(), ResumeIntakeStatus.CONFIRMED,
                    null, preflight.actorId(), reason, "RESUME_INTAKE_ADOPTION_RETRIED");
        }
        MalwareScanVerdict verdict;
        try (InputStream input = storage.open(intake.getObjectKey())) {
            verdict = scanner.scan(intake.getOriginalFileName(), input);
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable(
                    "MALWARE_SCANNER_UNAVAILABLE", "恶意文件扫描服务仍不可用，收件保持可重试状态", ex);
        }
        if (verdict == MalwareScanVerdict.UNAVAILABLE) {
            throw PlatformExceptions.unavailable(
                    "MALWARE_SCANNER_UNAVAILABLE", "恶意文件扫描服务仍不可用，收件保持可重试状态");
        }
        String cleanKey = "resume/" + intake.getContentSha256().substring(0, 2)
                + "/" + intake.getContentSha256();
        if (verdict == MalwareScanVerdict.CLEAN) {
            copyIfAbsent(intake.getObjectKey(), cleanKey);
        }
        MalwareScanVerdict finalVerdict = verdict;
        ResumeIntake result = transactionTemplate.execute(ignored -> {
            ResumeIntake current = requireIntake(id);
            authorization.requireAccess("talent.manage", target(current));
            if (current.getVersion() != command.getVersion()
                    || !ResumeIntakeStatus.FAILED_RETRYABLE.matches(current.getStatus())) {
                throw versionConflict();
            }
            Instant now = clock.instant();
            if (finalVerdict == MalwareScanVerdict.CLEAN) {
                persistence.updateIntakeObjectKey(id, cleanKey, preflight.actorId(), now);
                if (intake.getProcessingId() != null) {
                    intakeProcessing.retry(intake.getProcessingId(), preflight.actorId(), now);
                }
            }
            ResumeIntakeStatus next = finalVerdict == MalwareScanVerdict.CLEAN
                    ? ResumeIntakeStatus.PARSING : ResumeIntakeStatus.QUARANTINED;
            String failure = finalVerdict == MalwareScanVerdict.CLEAN
                    ? null : "MALWARE_OR_SIGNATURE_REJECTED";
            if (persistence.updateIntakeStatus(id, next.getCode(), failure,
                    command.getVersion(), preflight.actorId(), now) == 0) {
                throw versionConflict();
            }
            if (finalVerdict == MalwareScanVerdict.CLEAN && intake.getProcessingId() == null) {
                String proposedId = ids.newId();
                intakeProcessing.insertProcessing(proposedId, intake.getContentSha256(),
                        intake.getOriginalFileName(), intake.getMediaType(), cleanKey,
                        preflight.actorId(), now);
                var shared = intakeProcessing.findByHash(intake.getContentSha256());
                if (shared == null || persistence.attachProcessing(id, shared.getId(),
                        preflight.actorId(), now) != 1) {
                    throw new IllegalStateException("可重试收件无法关联共享解析处理");
                }
                if (ResumeIntakeProcessingStatus.FAILED_FINAL.matches(shared.getStatus())) {
                    intakeProcessing.retry(shared.getId(), preflight.actorId(), now);
                }
            }
            event("RESUME_INTAKE_RETRIED", preflight.actorId(), "RESUME_INTAKE", id, reason, now);
            return requireIntake(id);
        });
        if (result == null) {
            throw new IllegalStateException("resume retry transaction returned no result");
        }
        return result;
    }

    private ResumeIntake requireRetryFinalized(String id, long version,
            ResumeIntakeStatus status, String failure, String actorId, String reason,
            String eventType) {
        ResumeIntake result = transactionTemplate.execute(ignored -> {
            ResumeIntake current = requireIntake(id);
            authorization.requireAccess("talent.manage", target(current));
            if (current.getVersion() != version
                    || !ResumeIntakeStatus.FAILED_RETRYABLE.matches(current.getStatus())) {
                throw versionConflict();
            }
            Instant now = clock.instant();
            if (persistence.updateIntakeStatus(id, status.getCode(), failure, version, actorId, now) == 0) {
                throw versionConflict();
            }
            event(eventType, actorId, "RESUME_INTAKE", id, reason, now);
            return requireIntake(id);
        });
        if (result == null) {
            throw new IllegalStateException("resume retry finalization returned no result");
        }
        return result;
    }

    @Value
    @Accessors(fluent = true)
    private static class RetryPreflight {
        ResumeIntake intake;
        String actorId;
    }

    /**
     * 确认核心。
     */
    private ResumeIntake confirmCore(String id, ConfirmIntakeDTO command) {
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
      if (!ResumeIntakeStatus.READY_FOR_CONFIRMATION.matches(intake.getStatus())
              && !ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getStatus())) {
        throw PlatformExceptions.conflict("INTAKE_NOT_CONFIRMABLE", "当前收件状态不能确认");
      }
        if (identityConflictPersistenceService.findOpenConflictForIntake(id) != null) {
            throw PlatformExceptions.conflict("IDENTITY_CONFLICT_REQUIRES_REVIEW", "身份冲突尚未处理，不能确认入库");
        }
        IdentityConflict decision = identityConflictPersistenceService.findResolvedConflictForIntake(id);
        if (IDENTITY_CONFLICT_REQUIRES_REVIEW.equals(intake.getFailureCode())
                && !validResolvedDecision(id, decision)) {
            throw PlatformExceptions.conflict(IDENTITY_CONFLICT_REQUIRES_REVIEW,
                    "身份冲突缺少有效的人工处理决定，不能确认入库");
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        String name = require(command.getFullName(), 160);
        String email = normalizeEmail(command.getEmail());
        String phone = normalizePhone(command.getPhone());
        Identity emailIdentity = email == null ? null : candidateIdentityPersistenceService.findIdentity("EMAIL", hash(email));
        Identity phoneIdentity = phone == null ? null : candidateIdentityPersistenceService.findIdentity("PHONE", hash(phone));
        Set<String> strong = new LinkedHashSet<>();
      if (emailIdentity != null) {
        strong.add(emailIdentity.getCandidateId());
      }
      if (phoneIdentity != null) {
        strong.add(phoneIdentity.getCandidateId());
      }
      if (command.getCandidateId() != null && !command.getCandidateId().isBlank()) {
        strong.add(command.getCandidateId());
      }
        boolean manuallyResolved = decision != null && DuplicateResolution.supports(decision.getResolution());
        boolean checkSameName = strong.isEmpty() && !manuallyResolved;
        List<Candidate> sameNameCandidates = checkSameName ? sameNameCandidates(name, actor) : List.of();
        Set<String> candidateLocks = new LinkedHashSet<>(strong);
        if (decision != null && DuplicateResolution.reusesExistingCandidate(decision.getResolution())
                && decision.getChosenCandidateId() != null) {
            candidateLocks.add(decision.getChosenCandidateId());
        }
        sameNameCandidates.forEach(value -> candidateLocks.add(value.getId()));
        // 与合并及冲突落证保持候选人→收件锁序；锁后来源变化只退出，不再追加新候选人锁。
        candidateLocks.stream().sorted().forEach(candidatePersistenceService::lockCandidate);
        if (identityConflictPersistenceService.lockIntake(id) == null) {
            throw PlatformExceptions.notFound("RESUME_INTAKE_NOT_FOUND", "简历收件不存在");
        }
        intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        if (intake.getVersion() != command.getVersion()
                || !sameConflictDecision(decision, identityConflictPersistenceService.findResolvedConflictForIntake(id))
                || !sameIdentitySource(emailIdentity, email == null ? null
                    : candidateIdentityPersistenceService.findIdentity("EMAIL", hash(email)))
                || !sameIdentitySource(phoneIdentity, phone == null ? null
                    : candidateIdentityPersistenceService.findIdentity("PHONE", hash(phone)))
                || (checkSameName && !sameNameCandidates.stream().map(Candidate::getId).toList()
                    .equals(sameNameCandidates(name, actor).stream().map(Candidate::getId).toList()))) {
            throw versionConflict();
        }
        if (!ResumeIntakeStatus.READY_FOR_CONFIRMATION.matches(intake.getStatus())
                && !ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getStatus())) {
            throw PlatformExceptions.conflict("INTAKE_NOT_CONFIRMABLE", "当前收件状态不能确认");
        }
        if (identityConflictPersistenceService.findOpenConflictForIntake(id) != null) {
            throw PlatformExceptions.conflict(IDENTITY_CONFLICT_REQUIRES_REVIEW, "身份冲突尚未处理，不能确认入库");
        }
        if (decision != null && DuplicateResolution.reusesExistingCandidate(decision.getResolution())) {
            String chosen = decision.getChosenCandidateId();
            if (chosen == null) {
                throw PlatformExceptions.conflict("IDENTITY_CONFLICT_REQUIRES_REVIEW", "人工身份决定缺少保留候选人，请重新核验");
            }
            String other = strong.stream().filter(candidateId -> !chosen.equals(candidateId))
                    .findFirst().orElse(null);
            if (other != null) {
                throw new IdentityConflictDetected(intake, chosen, other, "STRONG_IDENTITY_MISMATCH",
                        "联系方式已关联另一候选人，请先合并档案或更正联系方式");
            }
            strong.add(chosen);
        } else if (decision != null
                && DuplicateResolution.CREATE_SEPARATE.matches(decision.getResolution())
                && !strong.isEmpty()) {
            throw new IdentityConflictDetected(intake, strong.iterator().next(), null, "STRONG_IDENTITY_MISMATCH",
                    "联系方式已关联已有候选人，不能创建独立档案");
        }
        if (strong.size() > 1) {
            Iterator<String> values = strong.iterator();
            String a = values.next(), b = values.next();
            throw new IdentityConflictDetected(intake, a, b, "STRONG_IDENTITY_MISMATCH",
                    "邮箱、手机号或所选候选人指向不同身份，已进入人工冲突队列");
        }
        String candidateId = strong.stream().findFirst().orElse(null);
        Instant now = clock.instant();
        Candidate candidate = candidateId == null ? null : requireCurrentCandidate(candidateId);
        if (candidate != null && !sameName(candidate.getFullName(), name) && !manuallyResolved) {
            throw new IdentityConflictDetected(intake, candidate.getId(), null, "CONTACT_NAME_MISMATCH",
                    "联系方式命中已有候选人但姓名不同，已进入人工冲突队列");
        }
        if (candidate == null && !manuallyResolved) {
            if (!sameNameCandidates.isEmpty()) {
                throw new IdentityConflictDetected(intake, sameNameCandidates.getFirst().getId(), null, "NAME_MATCH_CONTACT_MISMATCH",
                        "存在同名但身份信息不一致的候选人，已进入人工冲突队列");
            }
        }
        if (candidate != null && !authorization.canAccess("talent.manage", target(candidate))) {
            throw new IdentityConflictDetected(intake, candidate.getId(), null, "CANDIDATE_ACCESS_REQUIRED",
                    "联系方式命中无权处理的已有候选人，请联系有权限的负责人核验");
        }
        if (candidate == null) {
            candidateId = ids.newId();
            candidatePersistenceService.insertCandidate(
                    candidateId,
                    name,
                    email,
                    phone,
                    intake.getOwnerUserId(),
                    intake.getOwnerDepartmentId(),
                    actor.getUserId(),
                    now);
            candidate = candidatePersistenceService.findCandidate(candidateId);
        }
        authorization.requireAccess("talent.manage", target(candidate));
      if (email != null && emailIdentity == null) {
        insertIdentity(candidateId, "EMAIL", email, now);
      }
      if (phone != null && phoneIdentity == null) {
        insertIdentity(candidateId, "PHONE", phone, now);
      }
        Resume resume = resumePersistenceService.findResumeForCandidate(candidateId);
        String resumeId;
        int versionNo;
        if (resume == null) {
            resumeId = ids.newId();
            versionNo = 1;
            resumePersistenceService.insertResume(resumeId, candidateId, now);
        } else {
            resumeId = resume.getId();
            ResumeVersion existingVersion = resumeVersionPersistenceService.findResumeContent(
                    resumeId, intake.getContentSha256());
            if (existingVersion != null) {
                if (persistence.confirmIntake(id, candidateId, existingVersion.getId(),
                        command.getVersion(), actor.getUserId(), now) == 0) {
                    throw versionConflict();
                }
                event("RESUME_INTAKE_CONTENT_REUSED", actor.getUserId(), "RESUME_VERSION",
                        existingVersion.getId(), null, now);
                return persistence.findIntake(id);
            }
            versionNo = persistence.findMaxResumeVersion(resumeId) + 1;
            resumePersistenceService.updateResumeVersion(resumeId, versionNo);
        }
        String versionId = ids.newId();
        resumeVersionPersistenceService.insertResumeVersion(
                versionId,
                resumeId,
                id,
                versionNo,
                intake.getOriginalFileName(),
                intake.getMediaType(),
                intake.getSizeBytes(),
                intake.getContentSha256(),
                intake.getObjectKey(),
                actor.getUserId(),
                now);
      if (persistence.confirmIntake(
              id, candidateId, versionId, command.getVersion(), actor.getUserId(), now)
              == 0) {
        throw versionConflict();
      }
        event("RESUME_INTAKE_CONFIRMED", actor.getUserId(), "RESUME_VERSION", versionId, null, now);
        searchProjection.candidateChanged(candidateId, "available:" + versionId, actor.getUserId(),
                candidate.getOwnerUserId(), candidate.getOwnerDepartmentId());
        return persistence.findIntake(id);
    }

    private List<Candidate> sameNameCandidates(String name, CurrentActorSnapshot actor) {
        return persistence.findCandidatesByExactName(name, actor.getUserId(), actor.hasCompanyWideScope(),
                actor.getOperationalManagedUserIds(), Set.of());
    }

    private static boolean sameIdentitySource(Identity first, Identity second) {
        if (first == null || second == null) return first == second;
        return Objects.equals(first.getId(), second.getId())
                && Objects.equals(first.getCandidateId(), second.getCandidateId())
                && Objects.equals(first.getIdentityType(), second.getIdentityType())
                && Objects.equals(first.getNormalizedValueHash(), second.getNormalizedValueHash());
    }

    private static boolean sameConflictDecision(IdentityConflict first, IdentityConflict second) {
        if (first == null || second == null) return first == second;
        return Objects.equals(first.getId(), second.getId())
                && Objects.equals(first.getStatus(), second.getStatus())
                && Objects.equals(first.getResolution(), second.getResolution())
                && Objects.equals(first.getChosenCandidateId(), second.getChosenCandidateId())
                && Objects.equals(first.getCandidateIdA(), second.getCandidateIdA())
                && Objects.equals(first.getCandidateIdB(), second.getCandidateIdB())
                && Objects.equals(first.getVersion(), second.getVersion());
    }

    private static boolean validResolvedDecision(String intakeId, IdentityConflict decision) {
        if (decision == null
                || !intakeId.equals(decision.getIntakeId())
                || !IdentityConflictStatus.RESOLVED.matches(decision.getStatus())
                || !DuplicateResolution.supports(decision.getResolution())) {
            return false;
        }
        String chosen = CommonUtils.stripToNull(decision.getChosenCandidateId());
        if (DuplicateResolution.CREATE_SEPARATE.matches(decision.getResolution())) {
            return chosen == null;
        }
        if (DuplicateResolution.MERGE.matches(decision.getResolution())
                && decision.getCandidateIdB() == null) {
            return false;
        }
        return chosen != null
                && (chosen.equals(decision.getCandidateIdA())
                    || chosen.equals(decision.getCandidateIdB()));
    }

    @Transactional(readOnly = true)
    public CursorPage<Candidate> candidates(String query, String cursor, Integer requestedLimit) {
        return candidates(query, cursor, requestedLimit, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<Candidate> candidates(
            String query, String cursor, Integer requestedLimit, boolean poolOnly) {
        return candidates(query,cursor,requestedLimit,poolOnly,null);
    }

    @Transactional(readOnly = true)
    public CursorPage<Candidate> candidates(String query,String cursor,Integer requestedLimit,boolean poolOnly,String poolStage) {
        if (poolStage != null && (!poolOnly || !ResumeLifecyclePhase.isPersonalPool(poolStage)))
            throw PlatformExceptions.badRequest("INVALID_POOL_STAGE","简历分类需用于个人池");
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.read");
        int limit = limit(requestedLimit);
        String normalizedQuery = optional(query, "query", MAX_CANDIDATE_QUERY_LENGTH);
        String scope = actorScope("talent.candidates", actor, normalizedQuery + ":poolOnly=" + poolOnly + ":poolStage=" + poolStage);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        if (!poolOnly) {
            return page(
                    persistence.findCandidates(
                            normalizedQuery, actor.getUserId(), actor.hasCompanyWideScope(),
                            actor.getOperationalManagedUserIds(), Set.of(), false, time(c), id(c), limit + 1),
                    limit,
                    cursorCodec,
                    scope,
                    Candidate::getUpdateTime,
                    Candidate::getId);
        }
        var visible = new ArrayList<Candidate>(limit + 1);
        Instant scanTime = time(c);
        String scanId = id(c);
        int scanLimit = Math.max(101, limit + 1);
        while (visible.size() <= limit) {
            var rows = persistence.findCandidates(
                    normalizedQuery, actor.getUserId(), actor.hasCompanyWideScope(),
                    actor.getOperationalManagedUserIds(), Set.of(), true, scanTime, scanId, scanLimit);
            if (rows.isEmpty()) break;
            var candidateIds = rows.stream().map(Candidate::getId).toList();
            var personalPoolIds = poolStage == null ? candidateLibrary.personalPoolCandidateIds(candidateIds)
                    : candidateLibrary.personalPoolCandidateIds(candidateIds,null,poolStage);
            rows.stream()
                    .filter(row -> personalPoolIds.contains(row.getId()))
                    .forEach(visible::add);
            if (visible.size() > limit || rows.size() < scanLimit) break;
            var last = rows.getLast();
            scanTime = last.getUpdateTime();
            scanId = last.getId();
        }
        return page(
                visible,
                limit,
                cursorCodec,
                scope,
                Candidate::getUpdateTime,
                Candidate::getId);
    }

    /** 按列表相同实时权限与阶段分批计数，每次最多校验 200 位候选人。 */
    @Transactional(readOnly = true)
    public CursorCount countCandidates(String query, String cursor, boolean poolOnly, String poolStage) {
        if (poolStage != null && (!poolOnly || !ResumeLifecyclePhase.isPersonalPool(poolStage))) {
            throw PlatformExceptions.badRequest("INVALID_POOL_STAGE", "简历分类需用于个人池");
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.read");
        String normalizedQuery = optional(query, "query", MAX_CANDIDATE_QUERY_LENGTH);
        String scope = actorScope("talent.candidates.count", actor,
                normalizedQuery + ":poolOnly=" + poolOnly + ":poolStage=" + poolStage);
        StableCursor scanCursor = StableCursor.decode(cursorCodec, scope, cursor);
        int batchSize = 200;
        var fetched = persistence.findCandidates(
                normalizedQuery, actor.getUserId(), actor.hasCompanyWideScope(),
                actor.getOperationalManagedUserIds(), Set.of(), poolOnly,
                time(scanCursor), id(scanCursor), batchSize + 1);
        boolean hasMore = fetched.size() > batchSize;
        var rows = fetched.subList(0, Math.min(batchSize, fetched.size()));
        long count = rows.size();
        if (poolOnly && !rows.isEmpty()) {
            var candidateIds = rows.stream().map(Candidate::getId).toList();
            var personalPoolIds = poolStage == null
                    ? candidateLibrary.personalPoolCandidateIds(candidateIds)
                    : candidateLibrary.personalPoolCandidateIds(candidateIds, null, poolStage);
            count = rows.stream().filter(row -> personalPoolIds.contains(row.getId())).count();
        }
        // 游标按已扫描原始记录推进；被实时规则排除的批次仍需继续计数。
        String nextCursor = hasMore
                ? new StableCursor(rows.getLast().getUpdateTime(), rows.getLast().getId())
                        .encode(cursorCodec, scope)
                : null;
        return new CursorCount(count, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public Candidate candidate(String id) {
        Candidate candidate = requireCandidate(id);
        candidateReadAccess.requireCandidate(candidate);
        return candidate;
    }

    /**
     * 更新候选人核心。
     */
    private Candidate updateCandidateCore(String id, UpdateCandidateDTO command) {
        Candidate value = requireCurrentCandidate(id);
        authorization.requireAccess("talent.manage", target(value));
        CurrentActorSnapshot actor = authorization.currentActor();
        String owner = CommonUtils.stripOrElse(command.getOwnerUserId(), value.getOwnerUserId()),
                department =
                        CommonUtils.stripOrElse(command.getOwnerDepartmentId(), value.getOwnerDepartmentId());
        requireAssignable(actor, owner, department);
        String email = normalizeEmail(command.getEmail()), phone = normalizePhone(command.getPhone());
        requireIdentityAvailable(id, "EMAIL", email);
        requireIdentityAvailable(id, "PHONE", phone);
        Instant now = clock.instant();
      if (candidatePersistenceService.updateCandidate(
              id,
              require(command.getFullName(), 160),
              email,
              phone,
              owner,
              department,
              command.getVersion(),
              actor.getUserId(),
              now)
              == 0) {
        throw versionConflict();
      }
        syncPrimaryIdentity(id, "EMAIL", normalizeEmail(value.getPrimaryEmail()), email, now);
        syncPrimaryIdentity(id, "PHONE", normalizePhone(value.getPrimaryPhone()), phone, now);
        event("CANDIDATE_UPDATED", actor.getUserId(), "CANDIDATE", id, null, now);
        Candidate updated = candidatePersistenceService.findCandidate(id);
        searchProjection.candidateChanged(id, "updated:" + updated.getVersion(), actor.getUserId(),
                updated.getOwnerUserId(), updated.getOwnerDepartmentId());
        return updated;
    }

    /**
     * 删除候选人核心。
     */
    private void deleteCandidateCore(String id, TalentVersionReasonDTO command) {
        Candidate value = requireCurrentCandidate(id);
        authorization.requireAccess("talent.manage", target(value));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
      if (candidatePersistenceService.deleteCandidate(id, command.getVersion(), actor.getUserId(), now) == 0) {
        throw versionConflict();
      }
        candidateIdentityPersistenceService.deleteIdentities(id);
        event(
                "CANDIDATE_DELETED",
                actor.getUserId(),
                "CANDIDATE",
                id,
                require(command.getReason(), 500),
                now);
        searchProjection.candidateChanged(id, "deleted:" + value.getVersion(), actor.getUserId(),
                value.getOwnerUserId(), value.getOwnerDepartmentId());
    }

    @Transactional(readOnly = true)
    public CursorPage<ResumeVersion> resumeVersions(
            String candidateId, String cursor, Integer requestedLimit) {
        Candidate candidate = requireCandidate(candidateId);
        candidateReadAccess.requireAllResumes(candidate);
        int limit = limit(requestedLimit);
        // 合并会重编号而保留创建时间，游标必须与版本号倒序一致；新scope拒绝旧时间游标。
        String scope = CursorScopes.scope("talent.resume-versions", candidateId, "version-desc-v2");
        Integer cursorVersionNo = null;
        String cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                List<String> position = cursorCodec.decode(cursor, scope, 2);
                cursorVersionNo = Integer.parseInt(position.getFirst());
                cursorId = position.getLast();
                if (cursorVersionNo < 1 || cursorId.length() != RequiredIdentifier.IDENTIFIER_LENGTH) {
                    throw new IllegalArgumentException("invalid version cursor");
                }
            } catch (IllegalArgumentException invalid) {
                throw PlatformExceptions.badRequest("CURSOR_INVALID", "分页游标无效，请刷新列表");
            }
        }
        List<ResumeVersion> rows = persistence.findResumeVersions(
                candidateId, cursorVersionNo, cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ResumeVersion> items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        String nextCursor = hasMore
                ? cursorCodec.encode(scope, List.of(
                        Integer.toString(items.getLast().getVersionNo()), items.getLast().getId()))
                : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    Candidate candidateResume(String candidateId, String resumeVersionId) {
        Candidate candidate = requireCandidate(candidateId);
        candidateReadAccess.requireResume(candidate, resumeVersionId);
        return candidate;
    }

    @Transactional(readOnly = true)
    public ResumePreviewResponse preview(String id) {
        ResumeVersion version = requireReadableResumeVersion(id);
        return new ResumePreviewResponse(
                version.getId(),
                version.getOriginalFileName(),
                version.getMediaType(),
                version.getSizeBytes(),
                version.getContentSha256(),
                version.getStatus(),
                false);
    }

    @Transactional(readOnly = true)
    public CursorPage<IdentityConflict> conflicts(
            String status, List<String> intakeIds, String candidateId, String cursor, Integer requestedLimit) {
        CurrentActorSnapshot actor = authorization.currentActor();
        authorization.requirePermission("talent.manage");
        List<String> normalizedIntakeIds = intakeIds == null
                ? List.of()
                : intakeIds.stream().map(value -> RequiredIdentifier.require(value, "intakeIds"))
                    .distinct().toList();
        if (normalizedIntakeIds.size() > 100) {
            throw PlatformExceptions.unprocessable("TOO_MANY_INTAKES", "收件最多100项");
        }
        int limit = limit(requestedLimit);
        String normalizedStatus = CommonUtils.upperToNull(status);
        String normalizedCandidateId = candidateId == null ? null
                : RequiredIdentifier.require(candidateId, "candidateId");
        String scope = actorScope("talent.conflicts", actor, normalizedStatus, normalizedIntakeIds,
                normalizedCandidateId);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        return page(
                persistence.findConflicts(
                        normalizedStatus,
                        normalizedIntakeIds,
                        normalizedCandidateId,
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getOperationalManagedUserIds(),
                        Set.of(),
                        time(c),
                        id(c),
                        limit + 1),
                limit,
                cursorCodec,
                scope,
                IdentityConflict::getCreateTime,
                IdentityConflict::getId);
    }

    @Transactional(readOnly = true)
    public InterviewMaterialResponse interviewMaterials(String interviewId) {
        var access = interviewMaterialAccess.requireMaterialAccess(interviewId);
        var candidate = requireCandidate(access.candidateId());
        var version = requireInterviewResume(access.candidateId(), access.resumeVersionId());
        return new InterviewMaterialResponse(access.interviewId(), candidate.getId(), candidate.getFullName(),
                access.jdId(), access.jdVersionId(), access.jdVersionNo(), access.jdTitle(), access.jdCode(), access.jdRawText(),
                version.getId(), version.getVersionNo(), version.getOriginalFileName(), version.getMediaType(),
                version.getSizeBytes());
    }

    /** 查看本次上传的原始文件，不要求收件已生成候选人或简历版本。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DownloadedResumeResponse downloadIntakeFile(String intakeId) {
        var intake = requireIntake(intakeId);
        authorization.requireAccess("talent.read", target(intake));
        if (!MalwareScanVerdict.CLEAN.matches(intake.getScanStatus())
                || ResumeIntakeStatus.QUARANTINED.matches(intake.getStatus())
                || ResumeIntakeStatus.REJECTED.matches(intake.getStatus())) {
            throw PlatformExceptions.conflict("INTAKE_FILE_UNAVAILABLE", "收件原始文件未通过安全扫描或已被隔离、拒绝");
        }
        InputStream stream;
        try {
            stream = storage.open(intake.getObjectKey());
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("OBJECT_STORAGE_READ_FAILED", "简历对象读取失败", ex);
        }
        try {
            transactionTemplate.executeWithoutResult(ignored -> event("INTAKE_FILE_DOWNLOADED",
                    authorization.currentActor().getUserId(), "RESUME_INTAKE", intakeId,
                    null, clock.instant()));
            return new DownloadedResumeResponse(intake.getOriginalFileName(), intake.getMediaType(),
                    intake.getSizeBytes(), stream);
        } catch (RuntimeException ex) {
            closeDownloadStream(stream, intakeId, intake.getResumeVersionId(), ex);
            throw ex;
        }
    }

    /** 每次读取重新校验面试授权，只读取应聘活动固定的简历版本。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DownloadedResumeResponse downloadInterviewResume(String interviewId) {
        var access = interviewMaterialAccess.requireMaterialAccess(interviewId);
        var version = requireInterviewResume(access.candidateId(), access.resumeVersionId());
        InputStream stream;
        try {
            stream = storage.open(version.getObjectKey());
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("OBJECT_STORAGE_READ_FAILED", "简历对象读取失败", ex);
        }
        try {
            transactionTemplate.executeWithoutResult(ignored -> event("INTERVIEW_RESUME_DOWNLOADED",
                    authorization.currentActor().getUserId(), "RESUME_VERSION", version.getId(),
                    "interviewId=" + interviewId, clock.instant()));
            return new DownloadedResumeResponse(version.getOriginalFileName(), version.getMediaType(),
                    version.getSizeBytes(), stream);
        } catch (RuntimeException ex) {
            closeDownloadStream(stream, interviewId, version.getId(), ex);
            throw ex;
        }
    }

    private ResumeVersion requireInterviewResume(String candidateId, String resumeVersionId) {
        var version = persistence.findCandidateResumeVersion(candidateId, resumeVersionId);
        if (version == null || !ResumeVersionStatus.AVAILABLE.matches(version.getStatus())) {
            throw PlatformExceptions.notFound("INTERVIEW_RESUME_UNAVAILABLE", "本次面试绑定的简历版本不可用");
        }
        return version;
    }

    /**
     * 校验并创建授权下载。
     */
    @Transactional
    public DownloadGrantResponse authorizeDownload(String id) {
        requireReadableResumeVersion(id);
        CurrentActorSnapshot actor = authorization.currentActor();
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        Instant now = clock.instant(), expires = now.plus(5, ChronoUnit.MINUTES);
        downloadAuthorizationPersistenceService.insertDownloadAuthorization(
                ids.newId(), hash(token), id, actor.getUserId(), expires, now);
        event("RESUME_DOWNLOAD_AUTHORIZED", actor.getUserId(), "RESUME_VERSION", id, null, now);
        return new DownloadGrantResponse(token, expires);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DownloadedResumeResponse download(String token) {
        String tokenHash = hash(requireDownloadToken(token));
        CurrentActorSnapshot actor = authorization.currentActor();
        var grant = downloadAuthorizationPersistenceService.findDownloadAuthorization(tokenHash);
        Instant now = clock.instant();
      if (grant == null
              || !actor.getUserId().equals(grant.getAuthorizedUserId())
              || !grant.getExpiresAt().isAfter(now)
              || grant.getConsumedAt() != null) {
        throw new PlatformException(
                HttpStatus.FORBIDDEN, "DOWNLOAD_AUTHORIZATION_INVALID", "下载授权无效或已过期", false);
      }
        ResumeVersion version = requireReadableResumeVersion(grant.getResumeVersionId());
        InputStream stream;
        try {
            // 对象存储访问不得占用数据库连接；令牌在流成功打开后再进入短事务消费。
            stream = storage.open(version.getObjectKey());
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("OBJECT_STORAGE_READ_FAILED", "简历对象读取失败", ex);
        }
        try {
            transactionTemplate.executeWithoutResult(
                    ignored -> {
                        // 存储读取和事务开启可能跨过有效期，消费及审计使用此刻的时间。
                        Instant consumedAt = clock.instant();
                        if (downloadAuthorizationPersistenceService.consumeDownloadAuthorization(
                                grant.getId(), consumedAt)
                                == 0) {
                            throw PlatformExceptions.conflict(
                                    "DOWNLOAD_AUTHORIZATION_CONSUMED", "下载授权已使用");
                        }
                        event(
                                "RESUME_DOWNLOADED",
                                actor.getUserId(),
                                "RESUME_VERSION",
                                version.getId(),
                                null,
                                consumedAt);
                    });
            return new DownloadedResumeResponse(
                    version.getOriginalFileName(), version.getMediaType(), version.getSizeBytes(), stream);
        } catch (RuntimeException ex) {
            closeDownloadStream(stream, grant.getId(), version.getId(), ex);
            throw ex;
        }
    }

    /**
     * 解析并确定冲突核心。
     */
    private IdentityConflict resolveConflictCore(String id, ResolveIdentityConflictDTO command) {
        authorization.requirePermission("talent.manage");
        IdentityConflict value = identityConflictPersistenceService.findConflict(id);
      if (value == null) {
        throw PlatformExceptions.notFound("IDENTITY_CONFLICT_NOT_FOUND", "身份冲突不存在");
      }
        ResumeIntake intake = requireIntake(value.getIntakeId());
        authorization.requireAccess("talent.manage", target(intake));
        authorization.requireAccess("talent.manage", target(requireCandidate(value.getCandidateIdA())));
        if (value.getCandidateIdB() != null) {
            authorization.requireAccess("talent.manage", target(requireCandidate(value.getCandidateIdB())));
        }
        String resolution = resolution(command.getResolution());
        String chosen = CommonUtils.stripToNull(command.getChosenCandidateId());
        String reason = require(command.getReason(), 500);
        if (DuplicateResolution.CREATE_SEPARATE.matches(resolution)) {
          if (chosen != null) {
            throw PlatformExceptions.unprocessable("IDENTITY_RESOLUTION_CANDIDATE_FORBIDDEN", "创建独立候选人时不能指定保留候选人");
          }
        } else {
          if (chosen == null
                  || (!chosen.equals(value.getCandidateIdA()) && !chosen.equals(value.getCandidateIdB()))) {
            throw PlatformExceptions.unprocessable("IDENTITY_RESOLUTION_CANDIDATE_INVALID", "必须从冲突候选人中选择保留候选人");
          }
            requireCandidate(chosen);
        }
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
        if (DuplicateResolution.MERGE.matches(resolution)) {
          if (value.getCandidateIdB() == null) {
            throw PlatformExceptions.unprocessable("IDENTITY_MERGE_REQUIRES_TWO_CANDIDATES", "当前冲突没有两个可合并候选人");
          }
            String merged =
                    chosen.equals(value.getCandidateIdA())
                            ? value.getCandidateIdB()
                            : value.getCandidateIdA();
            mergeCandidates(value, chosen, merged, actor, reason, now);
        }
      if (identityConflictPersistenceService.resolveConflict(id, resolution, chosen, reason, actor.getUserId(), now) == 0) {
        throw PlatformExceptions.conflict("IDENTITY_CONFLICT_RESOLVED", "身份冲突已处理");
      }
        if (ResumeIntakeStatus.IDENTITY_REVIEW_REQUIRED.matches(intake.getStatus())
                && IDENTITY_CONFLICT_REQUIRES_REVIEW.equals(intake.getFailureCode())
                && identityConflictPersistenceService.findOpenConflictForIntake(intake.getId()) == null
                && persistence.updateIntakeStatus(intake.getId(), ResumeIntakeStatus.READY_FOR_CONFIRMATION.getCode(),
                    null, intake.getVersion(), actor.getUserId(), now) == 0) {
            throw versionConflict();
        }
        event("IDENTITY_CONFLICT_RESOLVED", actor.getUserId(), "IDENTITY_CONFLICT", id, reason, now);
        return identityConflictPersistenceService.findConflict(id);
    }

    /**
     * 合并候选人列表。
     */
    private void mergeCandidates(
            IdentityConflict conflictValue,
            String survivorId,
            String mergedId,
            CurrentActorSnapshot actor,
            String reason,
            Instant now) {
        // 所有新业务引用 writer 先锁 C；双方按固定顺序锁定，避免反向合并互锁。
        for (String id : List.of(survivorId, mergedId).stream().distinct().sorted().toList()) {
            candidatePersistenceService.lockCandidate(id);
        }
        Candidate survivor = requireCurrentCandidate(survivorId), merged = requireCurrentCandidate(mergedId);
        authorization.requireAccess("talent.manage", target(survivor));
        authorization.requireAccess("talent.manage", target(merged));
        requireMergeMetadataSafe(survivor);
        requireMergeMetadataSafe(merged);
        for (String id : List.of(survivorId, mergedId)) {
            if (candidatePersistenceService.hasMergeProtectedHistory(id)
                    || recruitment.hasMergeProtectedHistory(id)
                    || screening.hasMergeProtectedHistory(id)) {
                throw PlatformExceptions.conflict("CANDIDATE_MERGE_HAS_BUSINESS_HISTORY",
                        "候选人存在招聘、风险、生命周期或评分历史，需受控迁移关联后才能合并");
            }
        }
        Resume survivorResume = resumePersistenceService.findResumeForCandidate(survivorId),
                mergedResume = resumePersistenceService.findResumeForCandidate(mergedId);
        persistence.deleteDuplicateMergedIdentities(survivorId, mergedId);
        candidateIdentityPersistenceService.moveIdentities(survivorId, mergedId, actor.getUserId(), now);
        if (mergedResume != null) {
            if (survivorResume == null) {
                resumePersistenceService.moveResumeToCandidate(survivorId, mergedId, actor.getUserId(), now);
            } else {
                int offset = persistence.findMaxResumeVersion(survivorResume.getId());
                persistence.moveResumeVersions(survivorResume.getId(), mergedResume.getId(), offset);
                int current = persistence.findMaxResumeVersion(survivorResume.getId());
                resumePersistenceService.updateResumeVersion(survivorResume.getId(), current);
                resumePersistenceService.deleteResume(mergedResume.getId());
            }
        }
        persistence.redirectCandidateIntakes(survivorId, mergedId, actor.getUserId(), now);
      if (candidatePersistenceService.markCandidateMerged(mergedId, merged.getVersion(), actor.getUserId(), now) == 0) {
        throw versionConflict();
      }
        candidateMergeHistoryPersistenceService.insertMergeHistory(
                ids.newId(),
                survivorId,
                mergedId,
                conflictValue.getId(),
                survivor.getVersion(),
                merged.getVersion(),
                actor.getUserId(),
                reason,
                now);
        event("CANDIDATE_MERGED", actor.getUserId(), "CANDIDATE", survivorId, reason, now);
        String searchChange = "merge:" + conflictValue.getId();
        searchProjection.candidateChanged(survivorId, searchChange, actor.getUserId(),
                survivor.getOwnerUserId(), survivor.getOwnerDepartmentId());
        searchProjection.candidateChanged(mergedId, searchChange, actor.getUserId(),
                merged.getOwnerUserId(), merged.getOwnerDepartmentId());
    }

    private ResumeIntake terminalIntake(
            String id, ResumeIntakeStatus status, TalentVersionReasonDTO command) {
        ResumeIntake intake = requireIntake(id);
        authorization.requireAccess("talent.manage", target(intake));
        CurrentActorSnapshot actor = authorization.currentActor();
        Instant now = clock.instant();
      if (persistence.updateIntakeStatus(
              id, status.getCode(), null, command.getVersion(), actor.getUserId(), now)
              == 0) {
        throw versionConflict();
      }
        event(
                "RESUME_INTAKE_" + status.getCode(),
                actor.getUserId(),
                "RESUME_INTAKE",
                id,
                require(command.getReason(), 500),
                now);
        return persistence.findIntake(id);
    }

    /**
     * 新增身份。
     */
    private void insertIdentity(String candidateId, String type, String value, Instant now) {
      if (value != null) {
        candidateIdentityPersistenceService.insertIdentity(
                ids.newId(),
                candidateId,
                type,
                hash(value),
                value.substring(Math.max(0, value.length() - 4)),
                now);
      }
    }

    private void syncPrimaryIdentity(
            String candidateId, String type, String previous, String current, Instant now) {
        if (previous != null && !Objects.equals(previous, current)) {
            candidateIdentityPersistenceService.deleteIdentity(candidateId, type, hash(previous));
        }
        if (current == null) {
            return;
        }
        Identity existing = candidateIdentityPersistenceService.findIdentity(type, hash(current));
        if (existing == null) {
            insertIdentity(candidateId, type, current, now);
        } else if (!candidateId.equals(existing.getCandidateId())) {
            throw PlatformExceptions.conflict("CANDIDATE_IDENTITY_DUPLICATE", "邮箱或手机号已属于其他候选人");
        }
    }

    private void requireIdentityAvailable(String candidateId, String type, String value) {
      if (value == null) {
        return;
      }
        Identity existing = candidateIdentityPersistenceService.findIdentity(type, hash(value));
      if (existing != null && !candidateId.equals(existing.getCandidateId())) {
        throw PlatformExceptions.conflict("CANDIDATE_IDENTITY_DUPLICATE", "邮箱或手机号已属于其他候选人");
      }
    }

    private void requireAssignable(CurrentActorSnapshot actor, String owner, String department) {
      if (actor.hasCompanyWideScope()) {
        return;
      }
      if (!actor.getUserId().equals(owner) && !actor.getManagedUserIds().contains(owner)) {
        throw new PlatformException(
                HttpStatus.FORBIDDEN, "OWNER_ASSIGNMENT_FORBIDDEN", "只能分配给本人或管理范围内人员", false);
      }
      if (!actor.affiliatedDepartmentIds().contains(department)) {
        throw new PlatformException(
                HttpStatus.FORBIDDEN, "OWNER_DEPARTMENT_FORBIDDEN", "归属部门超出可见范围", false);
      }
    }

    private AuthorizationTarget target(IntakeBatch v) {
        return new AuthorizationTarget(
                "INTAKE_BATCH",
                v.getId(),
                v.getDefaultOwnerUserId(),
                v.getOwnerDepartmentId(),
                Set.of());
    }

    private AuthorizationTarget target(ResumeIntake v) {
        return new AuthorizationTarget(
                "RESUME_INTAKE",
                v.getId(),
                v.getOwnerUserId(),
                v.getOwnerDepartmentId(),
                Set.of());
    }

    private AuthorizationTarget target(Candidate v) {
        return new AuthorizationTarget(
                "CANDIDATE", v.getId(), v.getOwnerUserId(), v.getOwnerDepartmentId(), Set.of());
    }

    /**
     * 校验并获取通知或沟通渠道。
     */
    private SourceChannel requireChannel(String id) {
        SourceChannel v = sourceChannelPersistenceService.findChannel(id);
      if (v == null) {
        throw PlatformExceptions.notFound("SOURCE_CHANNEL_NOT_FOUND", "来源渠道不存在");
      }
        return v;
    }

    private SourceChannel requireOwnedChannel(String id) {
        return requireChannel(id);
    }

    private static void requireSuperAdmin(CurrentActorSnapshot actor) {
        if (!actor.isSuperAdmin()) {
            throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "仅超级管理员可维护来源渠道");
        }
    }

    private IntakeBatch requireBatch(String id) {
        IntakeBatch v = intakeBatchPersistenceService.findBatch(id);
      if (v == null) {
        throw PlatformExceptions.notFound("INTAKE_BATCH_NOT_FOUND", "收件批次不存在");
      }
        return v;
    }

    private IntakeBatch requireUploadableBatch(String id) {
        IntakeBatch batch = requireBatch(id);
        authorization.requireAccess("talent.manage", target(batch));
        if (IntakeBatchStatus.isTerminal(batch.getStatus())) {
            throw PlatformExceptions.conflict("INTAKE_BATCH_COMPLETED", "已结束批次不能继续上传");
        }
        requireActiveChannel(batch);
        return batch;
    }

    private void requireActiveChannel(IntakeBatch batch) {
        SourceChannel channel = requireChannel(batch.getSourceChannelId());
        if (!SourceChannelStatus.ACTIVE.matches(channel.getStatus())) {
            throw PlatformExceptions.unprocessable("SOURCE_CHANNEL_DISABLED", "停用来源渠道不能继续上传");
        }
    }

    private ResumeIntake requireIntake(String id) {
        ResumeIntake v = persistence.findIntake(id);
      if (v == null) {
        throw PlatformExceptions.notFound("RESUME_INTAKE_NOT_FOUND", "简历收件不存在");
      }
        return v;
    }

    /**
     * 校验并获取候选人。
     */
    private Candidate requireCandidate(String id) {
        Candidate v = candidatePersistenceService.findCandidate(id);
        if (v != null && CandidateStatus.DELETED.matches(v.getStatus())) {
            String survivor = candidateMergeHistoryPersistenceService.findMergeSurvivor(id);
          if (survivor != null) {
            v = candidatePersistenceService.findCandidate(survivor);
          }
        }
      if (v == null || CandidateStatus.DELETED.matches(v.getStatus())) {
        throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在");
      }
        return v;
    }

    private Candidate requireCurrentCandidate(String id) {
        Candidate value = candidatePersistenceService.findCandidateForUpdate(id);
        if (value == null || !CandidateStatus.ACTIVE.matches(value.getStatus())) {
            throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用，请重新核对档案");
        }
        return value;
    }

    private void requireMergeMetadataSafe(Candidate candidate) {
        if (candidate.getExt() == null || candidate.getExt().isBlank()) return;
        try {
            var metadata = MERGE_JSON.readTree(candidate.getExt());
            if (metadata == null || !metadata.isObject()) {
                throw PlatformExceptions.conflict("CANDIDATE_MERGE_METADATA_INVALID", "候选人扩展事实无法核对，请先人工修复");
            }
            for (String key : List.of("screenedOutResumeVersionId", "screenedOutAt", "reclaimedResumeVersionId",
                    "reclaimedScoreBindingId", "reclaimedAt", "onboardingReturnApplicationResumeVersionId")) {
                if (metadata.hasNonNull(key)) {
                    throw PlatformExceptions.conflict("CANDIDATE_MERGE_HAS_BUSINESS_HISTORY",
                            "候选人存在人工共享或重新领取事实，需受控迁移关联后才能合并");
                }
            }
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw PlatformExceptions.conflict("CANDIDATE_MERGE_METADATA_INVALID", "候选人扩展事实无法核对，请先人工修复");
        }
    }

    /**
     * 校验并获取简历版本。
     */
    private ResumeVersion requireResumeVersion(String id) {
        ResumeVersion v = resumeVersionPersistenceService.findResumeVersion(id);
      if (v == null) {
        throw PlatformExceptions.notFound("RESUME_VERSION_NOT_FOUND", "简历版本不存在");
      }
        return v;
    }

    private ResumeVersion requireReadableResumeVersion(String id) {
        ResumeVersion version = requireResumeVersion(id);
        Resume resume = resumePersistenceService.findResume(version.getResumeId());
        if (resume == null) {
            throw PlatformExceptions.notFound("RESUME_VERSION_NOT_FOUND", "简历版本不存在");
        }
        candidateResume(resume.getCandidateId(), id);
        return version;
    }

    private void event(
            String event, String actorId, String type, String targetId, String reason, Instant now) {
        talentProcessEventPersistenceService.insertEvent(ids.newId(), event, actorId, type, targetId, reason, now);
    }

    private void closeDownloadStream(
            InputStream stream, String grantId, String resumeVersionId, RuntimeException originalFailure) {
        try {
            stream.close();
        } catch (IOException | RuntimeException closeFailure) {
            originalFailure.addSuppressed(closeFailure);
            LOGGER.warn(
                    "关闭失败的简历下载流异常, grantId={}, resumeVersionId={}",
                    grantId,
                    resumeVersionId,
                    SafeLogException.from(closeFailure));
        }
    }

    private void storeIfAbsent(String objectKey, ResumeUploadDTO file) {
        try {
          if (storage.exists(objectKey)) {
            return;
          }
            try (InputStream input = file.openStream()) {
                storage.put(objectKey, input);
            }
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("OBJECT_STORAGE_WRITE_FAILED", "隔离对象保存失败", ex);
        }
    }

    private void copyIfAbsent(String sourceKey, String targetKey) {
        try {
          if (storage.exists(targetKey)) {
            return;
          }
            try (InputStream input = storage.open(sourceKey)) {
                storage.put(targetKey, input);
            }
        } catch (IOException ex) {
            throw PlatformExceptions.unavailable("OBJECT_STORAGE_WRITE_FAILED", "扫描通过对象转存失败", ex);
        }
    }

    private static String digest(InputStream input) throws IOException {
        return Sha256Utils.hash(input);
    }

    private static String hash(String value) {
        return Sha256Utils.hashUtf8(value);
    }

    /**
     * 在任何鉴权、哈希或持久化操作前拒绝非生成端格式的下载令牌。
     */
    private static String requireDownloadToken(String value) {
        if (value == null || !RESUME_DOWNLOAD_TOKEN_PATTERN.matcher(value).matches()) {
            throw PlatformExceptions.badRequest("DOWNLOAD_TOKEN_INVALID", "下载令牌格式无效");
        }
        return value;
    }

    private String reserveIdempotency(
            String actorId,
            String rawKey,
            String action,
            String fingerprint,
            String targetId,
            Instant now) {
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        IdempotencyRecord existing = talentIdempotencyPersistenceService.findIdempotency(actorId, key);
      if (existing != null) {
        return verifyIdempotency(existing, action, fingerprint);
      }
      if (persistence.insertIdempotency(actorId, key, action, fingerprint, targetId, now) == 1) {
        return null;
      }
        existing = talentIdempotencyPersistenceService.findIdempotency(actorId, key);
      if (existing == null) {
        throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "幂等请求正在处理中，请稍后重试");
      }
        return verifyIdempotency(existing, action, fingerprint);
    }

    private static String verifyIdempotency(
            IdempotencyRecord existing, String action, String fingerprint) {
      if (!action.equals(existing.getActionCode())
              || !fingerprint.equals(existing.getRequestFingerprint())) {
        throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
      }
        return existing.getTargetId();
    }

    private static String fingerprint(String... values) {
        return hash(String.join("\u001f", values));
    }

    private static String safeFileName(String value) {
        String name = normalizeFileName(value);
      if (name == null) {
        throw PlatformExceptions.unprocessable("RESUME_FILE_NAME_INVALID", "简历文件名无效");
      }
        return name;
    }

    private static String mediaType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
      if (n.endsWith(".pdf")) {
        return "application/pdf";
      }
      if (n.endsWith(".docx")) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      }
      if (n.endsWith(".doc")) {
        return "application/msword";
      }
        throw PlatformExceptions.unprocessable("RESUME_FILE_TYPE_INVALID", "只允许 PDF、DOCX 或 DOC 简历");
    }

    /**
     * 规范化邮箱地址。
     */
    private static String normalizeEmail(String v) {
        String x = CommonUtils.stripToNull(v);
      if (x == null) {
        return null;
      }
        x = x.toLowerCase(Locale.ROOT);
      if (!x.matches(CANDIDATE_EMAIL_INPUT_PATTERN)) {
        throw PlatformExceptions.unprocessable("CANDIDATE_EMAIL_INVALID", "候选人邮箱格式无效");
      }
        return x;
    }

    /**
     * 规范化联系电话。
     */
    private static String normalizePhone(String v) {
        String x = CommonUtils.stripToNull(v);
      if (x == null) {
        return null;
      }
        x = x.replaceAll("[^0-9+]", "");
      if (x.length() < 7 || x.length() > 20) {
        throw PlatformExceptions.unprocessable("CANDIDATE_PHONE_INVALID", "候选人手机号格式无效");
      }
        return x;
    }

    private static boolean sameName(String left, String right) {
        return left.strip()
                .replaceAll("\\s+", "")
                .equalsIgnoreCase(right.strip().replaceAll("\\s+", ""));
    }

    static String sourceChannelCode(String id) {
        return "SC_" + id;
    }

    private static String channelType(String v) {
        String x = CommonUtils.upperOrEmpty(v);
      if (!IntakeSourceType.supports(x)) {
        throw PlatformExceptions.unprocessable("SOURCE_CHANNEL_TYPE_INVALID", "来源类型无效");
      }
        return x;
    }

    private static String resolution(String v) {
        String x = CommonUtils.upperOrEmpty(v);
      if (!DuplicateResolution.supports(x)) {
        throw PlatformExceptions.unprocessable("IDENTITY_RESOLUTION_INVALID", "身份冲突处理方式无效");
      }
        return x;
    }

    /**
     * 使用人才模块的默认值和上限解析分页大小。
     */
    private static int limit(Integer requested) {
        return PageLimits.resolve(requested, 20, 100);
    }

    private static Instant time(StableCursor c) {
        return c == null ? null : c.getSortTime();
    }

    private static String id(StableCursor c) {
        return c == null ? null : c.getId();
    }

    private static String actorScope(
            String operation, CurrentActorSnapshot actor, Object... filters) {
        Object[] values = new Object[filters.length + 5];
        values[0] = actor.getUserId();
        values[1] = actor.hasCompanyWideScope();
        values[2] = actor.getOperationalManagedUserIds();
        values[3] = Set.of();
        values[4] = actor.getPermissions();
        System.arraycopy(filters, 0, values, 5, filters.length);
        return CursorScopes.scope(operation, values);
    }

}

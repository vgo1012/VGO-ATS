package com.ezaigc.hr.recruiting.demand.application.manager;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_PROMPT_VERSION;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_TASK_TYPE;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_RESOURCE_TYPE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskErrorCode;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.application.assembler.JdParsingAssembler;
import com.ezaigc.hr.recruiting.demand.application.support.JdParsingAdmissionPolicy;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelResult;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelUnavailableException;
import com.ezaigc.hr.common.infrastructure.ai.AiQuotaExceededException;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ParseRequest;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdAiModelRunEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.response.JdFactsResponse;
import com.ezaigc.hr.recruiting.demand.enums.JdAiModelRunStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdFactSourceType;
import com.ezaigc.hr.recruiting.demand.service.JdAiModelRunPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.validator.JdParseContractValidator;
import com.ezaigc.hr.recruiting.demand.service.validator.JdParseContractValidator.ContractViolation;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JdParsingApplicationManager {
    @Resource private SharedJdReadApplicationManager sharedJdRead;

    private static final Logger LOGGER = LoggerFactory.getLogger(JdParsingApplicationManager.class);
    @Resource
    private JdParsingAdmissionPolicy admission;

    @Resource
    private JdFactPersistenceManager factPersistence;

    @Resource
    private JdParsingAssembler assembler;

    @Resource
    private PlatformIdGenerator ids;

    @Resource
    private Clock clock;

    @Resource
    private ObjectMapper json;

    @Resource
    private JdVersionPersistenceService jdVersionPersistenceService;

    @Resource
    private JdEntityPersistenceService jdEntityPersistenceService;

    @Resource
    private DemandCatalogPersistenceService catalogPersistenceService;

    @Resource
    private DemandQueryService queries;

    @Resource
    private AuthorizationService authorization;

    @Resource
    private AsyncTaskPort tasks;

    @Resource
    private JdParseModelPort model;

    @Resource
    private JdParseContractValidator validator;

    @Resource
    private RawAiArchivePort archive;

    @Resource
    private JdParsingPersistenceService persistence;

    @Resource
    private JdAiModelRunPersistenceService jdAiModelRunPersistenceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Transactional
    public TaskSnapshot enqueue(String versionId, String idempotencyKey) {
        JdVersionEntity version = requireVersion(versionId);
        JdVersionSnapshot context = admission.requireUserAccess(versionId);
        var actor = authorization.currentActor();
        return tasks.enqueue(
                new EnqueueTask(
                        ids.newId(),
                        JD_PARSE_TASK_TYPE,
                        JD_PARSE_RESOURCE_TYPE,
                        versionId,
                        actor.getUserId(),
                        context.getOwnerUserId(),
                        context.getOwnerDepartmentId(),
                        "demand.read",
                        "demand.manage",
                        idempotencyKey,
                        fingerprint(version.getRawText(), JD_FACT_SCHEMA_VERSION, JD_PARSE_PROMPT_VERSION),
                        sourceStillParsing(matchingSourceVersion(version)) ? 10 : 3,
                        clock.instant()));
    }

    @Transactional(readOnly = true)
    public JdFactsResponse facts(String versionId) {
        JdVersionSnapshot context = queries.requireJdVersion(versionId);
        authorization.currentActor();
        try {
            authorization.requireAccess("demand.read", target(context));
        } catch (PlatformException denied) {
            if (denied.status() != HttpStatus.FORBIDDEN || !sharedJdRead.isShared(versionId)) throw denied;
        }
        JdFactSnapshotEntity snapshot = persistence.findCurrent(versionId);
        if (snapshot == null) {
            throw new PlatformException(HttpStatus.NOT_FOUND, "JD_FACTS_NOT_READY", "JD 事实尚未生成", true);
        }
        return assembler.toResponse(snapshot);
    }

    /**
     * 处理当前任务或业务数据。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(TaskSnapshot task) {
        if (tasks.shouldStopProcessing(task)) {
            tasks.cancelled(task);
            return;
        }
        JdVersionEntity version = jdVersionPersistenceService.findJdVersion(task.resourceId());
        if (version == null) {
            tasks.fail(task, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
            return;
        }
        JdVersionEntity sourceVersion = matchingSourceVersion(version);
        if (sourceVersion != null) {
            String sourceVersionId = sourceVersion.getId();
            JdFactSnapshotEntity sourceFacts = persistence.findCurrent(sourceVersionId);
            if (sourceFacts == null && task.attemptCount() <= 6 && sourceStillParsing(sourceVersion)) {
                transactionTemplate.executeWithoutResult(ignored -> tasks.failAndRetryAt(task,
                        "JD_SOURCE_FACTS_PENDING", "等待来源 JD 解析完成", clock.instant().plusSeconds(30)));
                return;
            }
            if (sourceFacts != null && JdFactSourceType.AI_PARSE.matches(sourceFacts.getSourceType())
                    && JD_FACT_SCHEMA_VERSION.equals(sourceFacts.getSchemaVersion())
                    && !CommonUtils.isBlank(sourceFacts.getModelRunId())) {
                JdAiModelRunEntity sourceRun = jdAiModelRunPersistenceService.getById(sourceFacts.getModelRunId());
                if (sourceRun != null && sourceVersionId.equals(sourceRun.getJdVersionId())
                        && JdAiModelRunStatus.SUCCEEDED.matches(sourceRun.getStatus())
                        && JD_FACT_SCHEMA_VERSION.equals(sourceRun.getSchemaVersion())
                        && JD_PARSE_PROMPT_VERSION.equals(sourceRun.getPromptVersion())
                        && !CommonUtils.isBlank(sourceRun.getModelProvider())
                        && !CommonUtils.isBlank(sourceRun.getModelVersion())
                        && !CommonUtils.isBlank(sourceRun.getRawOutputObjectKey())
                        && !CommonUtils.isBlank(sourceRun.getRawOutputHash())
                        && sourceRun.getRawOutputSize() != null) {
                    reuseSourceFacts(task, version, sourceFacts, sourceRun);
                    return;
                }
            }
        }
        String runId = ids.newId();
        try {
            startModelRun(runId, version.getId(), task);
        } catch (PlatformException ex) {
            transactionTemplate.executeWithoutResult(ignored -> tasks.failUnhandled(task, ex.code(), ex.getMessage(), ex.recoverable()));
            return;
        }
        ModelResult result;
        try {
            result =
                    model.parse(
                            new ParseRequest(
                                    JD_FACT_SCHEMA_VERSION,
                                    JD_PARSE_PROMPT_VERSION,
                                    version.getTitle(),
                                    version.getRawText()));
        } catch (ModelUnavailableException | AiQuotaExceededException ex) {
            boolean quotaExceeded = ex instanceof AiQuotaExceededException;
            LOGGER.warn(
                    "jd-model-unavailable taskId={} jdVersionId={} modelRunId={}",
                    task.id(),
                    version.getId(),
                    runId,
                    SafeLogException.from(ex));
            failRunAndTask(
                    task,
                    actualErrorCode ->
                            fail(
                                    runId,
                                    AiOutputValidationStatus.MODEL_UNAVAILABLE,
                                    actualErrorCode,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0,
                                    0,
                                    0),
                    quotaExceeded ? AiQuotaExceededException.CODE : "MODEL_UNAVAILABLE",
                    quotaExceeded ? AiQuotaExceededException.USER_MESSAGE : "JD 解析模型尚未启用或暂时不可用");
            return;
        } catch (RuntimeException ex) {
            LOGGER.error(
                    "jd-model-call-failed taskId={} jdVersionId={} modelRunId={}",
                    task.id(),
                    version.getId(),
                    runId,
                    SafeLogException.from(ex));
            failRunAndTask(
                    task,
                    actualErrorCode ->
                            fail(
                                    runId,
                                    AiOutputValidationStatus.MODEL_UNAVAILABLE,
                                    actualErrorCode,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0,
                                    0,
                                    0),
                    "MODEL_CALL_FAILED",
                    "JD 解析模型调用失败，请稍后重试");
            return;
        }
        if (result == null
                || CommonUtils.isBlank(result.provider())
                || CommonUtils.isBlank(result.modelVersion())
                || CommonUtils.isBlank(result.rawJson())) {
            failRunAndTask(
                    task,
                    actualErrorCode ->
                            fail(
                                    runId,
                                    AiOutputValidationStatus.SCHEMA_INVALID,
                                    actualErrorCode,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0,
                                    0,
                                    0),
                    "MODEL_RESULT_EMPTY",
                    "模型未返回有效结果");
            return;
        }
        ArchiveResult archived;
        try {
            archived =
                    archive.archive(
                            "ai-raw/jd/" + version.getId() + "/" + runId + ".json.gz.enc", result.rawJson());
        } catch (IOException ex) {
            LOGGER.error(
                    "jd-model-archive-failed taskId={} jdVersionId={} modelRunId={}",
                    task.id(),
                    version.getId(),
                    runId,
                    SafeLogException.from(ex));
            failRunAndTask(
                    task,
                    actualErrorCode ->
                            fail(
                                    runId,
                                    AiOutputValidationStatus.PENDING,
                                    actualErrorCode,
                                    result,
                                    null),
                    "AI_RAW_ARCHIVE_FAILED",
                    "模型原始输出归档失败，请稍后重试");
            return;
        }
        JsonNode facts;
        try {
            facts = validator.validateModel(result.rawJson(), version.getRawText());
        } catch (ContractViolation ex) {
            failRunAndTask(
                    task,
                    actualErrorCode -> fail(runId, ex.validationStatus(), actualErrorCode, result, archived),
                    ex.errorCode(),
                    ex.safeDetail());
            return;
        }
        if (tasks.shouldStopProcessing(task)) {
            transactionTemplate.executeWithoutResult(
                    ignored -> {
                        fail(
                                runId,
                                AiOutputValidationStatus.VALID,
                                "TASK_CANCELLED",
                                result,
                                archived);
                        tasks.cancelled(task);
                    });
            return;
        }
        try {
            String factsJson = json.writeValueAsString(facts);
            transactionTemplate.executeWithoutResult(
                    ignored -> {
                        queries.requireJdVersionForNewUse(version.getId());
                        requireSingleWrite(
                                persistence.completeModelRun(
                                        runId,
                                        result.provider(),
                                        result.modelVersion(),
                                        archived.objectKey(),
                                        archived.sha256(),
                                        archived.sizeBytes(),
                                        archived.encryptionVersion(),
                                        result.inputTokens(),
                                        result.outputTokens(),
                                        result.durationMillis(),
                                        clock.instant()),
                                "完成 JD 模型运行",
                                runId);
                        JdFactSnapshotEntity snapshot =
                                factPersistence.store(
                                        version.getId(), runId, JD_FACT_SCHEMA_VERSION, factsJson);
                        if (snapshot == null || CommonUtils.isBlank(snapshot.getId())) {
                            throw new IllegalStateException("JD 事实快照持久化后无法回读");
                        }
                        tasks.succeed(task, snapshot.getId());
                    });
        } catch (PlatformException ex) {
            failRunAndTask(task,
                    actualErrorCode -> fail(runId, AiOutputValidationStatus.VALID, actualErrorCode, result, archived),
                    ex.code(), ex.getMessage(), ex.recoverable());
            return;
        } catch (RuntimeException ex) {
            LOGGER.error(
                    "jd-fact-persistence-failed taskId={} jdVersionId={} modelRunId={}",
                    task.id(),
                    version.getId(),
                    runId,
                    SafeLogException.from(ex));
            failRunAndTask(
                    task,
                    actualErrorCode ->
                            fail(
                                    runId,
                                    AiOutputValidationStatus.VALID,
                                    actualErrorCode,
                                    result,
                                    archived),
                    "FACT_PERSISTENCE_FAILED",
                    "JD 事实快照持久化失败，请重试");
            return;
        }
    }

    private JdVersionEntity matchingSourceVersion(JdVersionEntity version) {
        if (version.getJdId() == null) return null;
        JdEntity jd = jdEntityPersistenceService.getById(version.getJdId());
        String sourceVersionId = jd == null ? null : CommonUtils.stripToNull(jd.getAppliedFromJdVersionId());
        if (sourceVersionId == null) return null;
        JdVersionEntity source = jdVersionPersistenceService.findJdVersion(sourceVersionId);
        return source != null && !CommonUtils.isBlank(version.getTitle())
                && !CommonUtils.isBlank(version.getRawText())
                && Objects.equals(source.getTitle(), version.getTitle())
                && Objects.equals(source.getRawText(), version.getRawText()) ? source : null;
    }

    private boolean sourceStillParsing(JdVersionEntity source) {
        if (source == null) return false;
        JdEntity sourceJd = catalogPersistenceService.findJd(source.getJdId());
        return sourceJd != null && Objects.equals(sourceJd.getCurrentVersionNo(), source.getVersionNo())
                && (AsyncTaskStatus.QUEUED.matches(sourceJd.getCurrentParseStatus())
                    || AsyncTaskStatus.RUNNING.matches(sourceJd.getCurrentParseStatus()));
    }

    private void reuseSourceFacts(TaskSnapshot task, JdVersionEntity version,
            JdFactSnapshotEntity sourceFacts, JdAiModelRunEntity sourceRun) {
        String runId = ids.newId();
        try {
            startModelRun(runId, version.getId(), task);
        } catch (PlatformException ex) {
            transactionTemplate.executeWithoutResult(ignored -> tasks.failUnhandled(task, ex.code(), ex.getMessage(), ex.recoverable()));
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(ignored -> {
                queries.requireJdVersionForNewUse(version.getId());
                requireSingleWrite(persistence.completeModelRun(runId, sourceRun.getModelProvider(),
                        sourceRun.getModelVersion(), sourceRun.getRawOutputObjectKey(),
                        sourceRun.getRawOutputHash(), sourceRun.getRawOutputSize(),
                        sourceRun.getEncryptionVersion(), 0, 0, 0, clock.instant()),
                        "完成复用的 JD 模型运行", runId);
                JdFactSnapshotEntity snapshot = factPersistence.store(version.getId(), runId,
                        JD_FACT_SCHEMA_VERSION, sourceFacts.getFactsJson(), sourceFacts.getId());
                tasks.succeed(task, snapshot.getId());
            });
        } catch (RuntimeException ex) {
            LOGGER.error("jd-source-fact-reuse-failed taskId={} jdVersionId={}", task.id(), version.getId(), SafeLogException.from(ex));
            failRunAndTask(task,
                    errorCode -> fail(runId, AiOutputValidationStatus.VALID, errorCode, null, null),
                    "FACT_PERSISTENCE_FAILED", "来源 JD 事实复用失败，请重试");
        }
    }

    /** 在外部模型调用前以短事务创建可恢复的运行记录。 */
    private void startModelRun(String runId, String versionId, TaskSnapshot task) {
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    queries.requireJdVersionForNewUse(versionId);
                    if (!tasks.renewLease(task)) {
                        throw PlatformExceptions.conflict(AsyncTaskErrorCode.TASK_STATE_CONFLICT.getCode(), "任务租约已失效或已取消");
                    }
                    Instant now = clock.instant();
                    if (task.attemptCount() > 1) {
                        jdAiModelRunPersistenceService.terminateWithoutWorker(task.id(), versionId, "TASK_LEASE_EXPIRED",
                                now, PlatformActorConstants.SYSTEM_ACTOR_ID);
                    }
                    requireSingleWrite(
                            jdAiModelRunPersistenceService.insertModelRun(
                                    runId, versionId, task.id(), JD_FACT_SCHEMA_VERSION, JD_PARSE_PROMPT_VERSION, now),
                            "创建 JD 模型运行",
                            runId);
                });
    }

    /** 先锁定共享终态，再按实际失败或取消原因记录模型诊断；任一写入失败都整体回滚。 */
    private void failRunAndTask(
            TaskSnapshot task, Consumer<String> runFailure, String errorCode, String safeDetail) {
        failRunAndTask(task, runFailure, errorCode, safeDetail, true);
    }

    private void failRunAndTask(
            TaskSnapshot task, Consumer<String> runFailure, String errorCode, String safeDetail, boolean retryable) {
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    TaskSnapshot outcome = tasks.fail(task, errorCode, safeDetail, retryable);
                    runFailure.accept(AsyncTaskStatus.CANCELLED.matches(outcome.status())
                            ? "TASK_CANCELLED" : errorCode);
                });
    }

    private void fail(
            String runId,
            AiOutputValidationStatus validation,
            String code,
            ModelResult result,
            ArchiveResult archive) {
        requireSingleWrite(
                persistence.failModelRun(
                        runId,
                        validation.getCode(),
                        code,
                        result == null ? null : result.provider(),
                        result == null ? null : result.modelVersion(),
                        archive == null ? null : archive.objectKey(),
                        archive == null ? null : archive.sha256(),
                        archive == null ? null : archive.sizeBytes(),
                        archive == null ? null : archive.encryptionVersion(),
                        result == null ? 0 : result.inputTokens(),
                        result == null ? 0 : result.outputTokens(),
                        result == null ? 0 : result.durationMillis(),
                        clock.instant()),
                "标记 JD 模型运行失败",
                runId);
    }

    private void fail(
            String runId,
            AiOutputValidationStatus validation,
            String code,
            String provider,
            String version,
            String objectKey,
            String hash,
            int input,
            int output,
            long duration) {
        requireSingleWrite(
                persistence.failModelRun(
                        runId,
                        validation.getCode(),
                        code,
                        provider,
                        version,
                        objectKey,
                        hash,
                        null,
                        null,
                        input,
                        output,
                        duration,
                        clock.instant()),
                "标记 JD 模型运行失败",
                runId);
    }

    private static void requireSingleWrite(int affectedRows, String operation, String runId) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    operation + "未影响唯一记录, modelRunId=" + runId + ", affectedRows=" + affectedRows);
        }
    }

    /**
     * 校验并获取技术乐观锁版本号。
     */
    private JdVersionEntity requireVersion(String id) {
        JdVersionEntity version = jdVersionPersistenceService.findJdVersion(id);
        if (version == null) {
            throw new PlatformException(HttpStatus.NOT_FOUND, "JD_VERSION_NOT_FOUND", "JD 版本不存在", false);
        }
        return version;
    }

    private AuthorizationTarget target(JdVersionSnapshot v) {
        return new AuthorizationTarget(
                v.getDemandId() == null ? "JD" : "DEMAND",
                v.getDemandId() == null ? v.getJdId() : v.getDemandId(),
                v.getOwnerUserId(),
                v.getOwnerDepartmentId(), v.getCollaboratorUserIds());
    }

    private static String fingerprint(String... values) {
        return Sha256Utils.fingerprintStrings("", 0, values);
    }
}

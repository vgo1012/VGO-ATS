package com.ezaigc.hr.intelligence.matching.application.manager;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHTS;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskErrorCode;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchRunDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.PreviewMatchDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.BaseEvaluation;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.CriterionEvaluation;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingHighlight;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import com.ezaigc.hr.intelligence.matching.domain.response.BaseEvaluationResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPreviewResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchResultPageResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchResultResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchRunRunResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchRunTaskResponse;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.enums.AiScoringErrorCode;
import com.ezaigc.hr.intelligence.matching.domain.service.PersonalScreeningSettings;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionMatchStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchResultSortMode;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunActionCode;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunPurpose;
import com.ezaigc.hr.intelligence.matching.service.BaseEvaluationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.CandidateSelectionSnapshotPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.CriterionEvaluationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingHighlightPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.ResumeFactBatch;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;

/**
 * 匹配运行应用服务，负责用例编排、权限校验和事务边界。
 */
@Service
public class MatchRunApplicationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchRunApplicationManager.class);

    @Resource
    private ControlledCriterionPersistenceService controlledCriterionPersistenceService;
    @Resource
    private ControlledCriterionPlanPersistenceService controlledCriterionPlanPersistenceService;
    @Resource
    private CriterionEvaluationPersistenceService criterionEvaluationPersistenceService;
    @Resource
    private MatchPlanVersionPersistenceService matchPlanVersionPersistenceService;
    @Resource
    private MatchingIdempotencyPersistenceService matchingIdempotencyPersistenceService;
    @Resource
    private PersonalPreferencePersistenceService personalPreferencePersistenceService;
    @Resource
    private WeightProfilePersistenceService weightProfilePersistenceService;
    @Resource
    private WeightProfileVersionPersistenceService weightProfileVersionPersistenceService;
    @Resource
    private JdFactQueryService jdFacts;
    @Resource
    private ResumeFactQueryService resumeFacts;
    @Resource private CandidateDirectoryService candidateDirectory;
    @Resource
    private AiMatchingEvaluationManager engine;
    @Resource
    private AsyncTaskPort tasks;
    @Resource
    private AuthorizationService authorization;
    @Resource
    @Lazy
    private CandidateLibraryQueryService candidateLibrary;
    @Resource
    private PlatformIdGenerator ids;
    @Resource
    private Clock clock;
    @Resource
    private ObjectMapper json;

    @Resource
    private MatchRunPersistenceService persistence;
    @Resource
    private BaseEvaluationPersistenceService baseEvaluationPersistenceService;
    @Resource
    private CandidateSelectionSnapshotPersistenceService candidateSelectionSnapshotPersistenceService;
    @Resource
    private MatchRunAttemptPersistenceService matchRunAttemptPersistenceService;
    @Resource
    private MatchRunItemOutcomePersistenceService matchRunItemOutcomePersistenceService;
    @Resource
    private MatchingHighlightPersistenceService matchingHighlightPersistenceService;
    @Resource
    private SelectionItemPersistenceService selectionItemPersistenceService;
    @Resource
    private ScreeningAdmissionPersistenceService screeningAdmissionPersistenceService;
    @Resource
    private WeightedResultPersistenceService weightedResultPersistenceService;
    @Resource
    private CursorCodec cursorCodec;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 任务类型。
     */
    public static final String TASK_TYPE = MatchingConstants.MATCH_RUN_TASK_TYPE;

    /** 联合解析调用前先确认当前 JD 具备正式评分配置，避免无效模型消耗。 */
    @Transactional
    public JdFactSnapshotData requireIntakeScoringReady(String jdVersionId) {
        authorization.requirePermission("matching.run");
        JdFactSnapshotData jd = jdFacts.currentFacts(jdVersionId);
        if (matchPlanVersionPersistenceService.findActiveMatchPlanVersion(jdVersionId) == null) {
            throw PlatformExceptions.conflict("MATCH_PLAN_NOT_ACTIVE", "公司正式评分配置尚未就绪");
        }
        return jd;
    }

    /** 自动导入只使用公司统一基线；个人偏好仅由后续自定义筛选使用。 */
    @Transactional
    public MatchRun createForIntake(String jdVersionId, String resumeVersionId) {
        authorization.requirePermission("matching.run");
        CurrentActorSnapshot actor = authorization.currentActor();
        // 在幂等回读之前重新校验两侧事实的对象权限，不能用旧运行绕过权限收窄。
        JdFactSnapshotData jd = jdFacts.currentFacts(jdVersionId);
        ResumeFactSnapshotData resume = resumeFacts.currentFactBatch(List.of(resumeVersionId)).get(resumeVersionId);
        MatchPlanVersion plan = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(jdVersionId);
        if (plan == null) {
            throw PlatformExceptions.conflict("MATCH_PLAN_NOT_ACTIVE", "公司正式评分配置尚未就绪");
        }
        WeightProfileVersion weights = requireWeights(DefaultScoringConstants.WEIGHT_VERSION_ID, actor.getUserId());
        String sort = PersonalScreeningSettings.defaultSort(weights);
        String key = "intake-score:" + hash(actor.getUserId(), jdVersionId, jd.getSnapshotId(),
                resumeVersionId, resume.getSnapshotId(), plan.getId(),
                weights.getId(), sort, AiScoringConstants.ALGORITHM_VERSION);
        return create(new CreateMatchRunDTO(jdVersionId, List.of(resumeVersionId),
                weights.getId(), null, List.of(), sort, MatchRunPurpose.COMPANY_DEFAULT), key);
    }

    /** 显式重评必须产生新运行；调用方提供按重评项唯一的幂等键，不能复用自动收件评分键。 */
    @Transactional
    public MatchRun createForRescore(String jdVersionId, String resumeVersionId, String operationKey) {
        return create(new CreateMatchRunDTO(jdVersionId, List.of(resumeVersionId),
                DefaultScoringConstants.WEIGHT_VERSION_ID, null, List.of(), null,
                MatchRunPurpose.COMPANY_DEFAULT), "candidate-rescore:" + operationKey);
    }

    @Transactional
    public MatchRun create(CreateMatchRunDTO command, String idempotencyKey) {
        authorization.requirePermission("matching.run");
        CurrentActorSnapshot actor = authorization.currentActor();
        List<String> resumeVersionIds = unique(command.getResumeVersionIds());
        if (resumeVersionIds.isEmpty() || resumeVersionIds.size() > MAX_MATCH_RUN_RESUMES) {
            throw invalid("候选简历集合必须为 1～1000 项");
        }
        MatchRunPurpose purpose = command.getPurpose() == null ? MatchRunPurpose.CUSTOM : command.getPurpose();
        String sort = normalizeSort(command.getSortMode());
        String legacyRequestHash =
                hash(
                        command.getJdVersionId(),
                        String.join(",", resumeVersionIds),
                        String.valueOf(command.getWeightProfileVersionId()),
                        String.valueOf(command.getPersonalPreferenceVersionId()),
                        String.valueOf(command.getSelectedCriterionIds()),
                        purpose.getCode(),
                        sort);
        // 缺省排序与临时覆盖是不同意图；指纹仍在读取当前配置之前计算。
        String requestHash = hash("match-run-create-v2", legacyRequestHash,
                command.getSortMode() == null ? "DEFAULT" : "EXPLICIT");
        var replay = matchingIdempotencyPersistenceService.findIdempotency(actor.getUserId(), idempotencyKey);
        if (replay != null) {
            boolean legacyReplay = legacyRequestHash.equals(replay.getRequestFingerprint());
            if (!MatchRunActionCode.CREATE_MATCH_RUN.matches(replay.getActionCode())
                    || (!requestHash.equals(replay.getRequestFingerprint()) && !legacyReplay)) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同匹配请求");
            }
            MatchRun existing = requireRun(replay.getTargetId());
            // 旧格式未保存缺省标志；兼容旧重试，但显式排序不能返回与请求相反的冻结结果。
            if (legacyReplay && command.getSortMode() != null && !sort.equals(existing.getSortMode())) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同匹配请求");
            }
            if (legacyReplay && command.getSortMode() == null
                    && MatchResultSortMode.REFERENCE_SCORE.matches(existing.getSortMode())) {
                // 仅用运行引用的冻结版本判断旧意图，不读取当前方案或重新检查模板状态。
                WeightProfileVersion frozenWeights = existing.getWeightProfileVersionId() == null ? null
                        : weightProfileVersionPersistenceService.findWeightProfileVersion(existing.getWeightProfileVersionId());
                if (frozenWeights == null) {
                    throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "历史匹配缺少冻结排序配置，请读取原运行并使用新的 Idempotency-Key");
                }
                String frozenSort;
                try {
                    frozenSort = PersonalScreeningSettings.defaultSort(frozenWeights);
                } catch (RuntimeException invalidSettings) {
                    // 损坏的历史配置不足以恢复意图；异常范围仅限解析，不包含持久化查询。
                    throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "历史匹配的冻结排序配置无效，请读取原运行并使用新的 Idempotency-Key");
                }
                if (!existing.getSortMode().equals(frozenSort)) {
                    throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同匹配请求");
                }
            }
            return existing;
        }
        MatchPlanVersion plan = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(command.getJdVersionId());
        if (plan == null) {
            throw PlatformExceptions.conflict("MATCH_PLAN_NOT_ACTIVE", "JD 尚无已激活的正式评分配置");
        }
        JdFactSnapshotData jd = jdFacts.currentFacts(command.getJdVersionId());
        PersonalPreference preference = purpose == MatchRunPurpose.COMPANY_DEFAULT
                ? null
                : command.getPersonalPreferenceVersionId() == null
                        ? personalPreferencePersistenceService.findLatestPersonalPreference(actor.getUserId(), plan.getId())
                        : personalPreferencePersistenceService.findPersonalPreference(command.getPersonalPreferenceVersionId());
        if (preference != null
                && (!actor.getUserId().equals(preference.getOwnerUserId())
                || !plan.getId().equals(preference.getPlanVersionId()))) {
            throw invalid("个人偏好版本不属于当前用户或评分方案");
        }
        String weightId = purpose == MatchRunPurpose.COMPANY_DEFAULT
                ? DefaultScoringConstants.WEIGHT_VERSION_ID
                : command.getWeightProfileVersionId() != null
                        ? command.getWeightProfileVersionId()
                        : preference == null ? null : preference.getWeightProfileVersionId();
        WeightProfileVersion weights = requireWeights(weightId, actor.getUserId());
        if (command.getSortMode() == null) sort = PersonalScreeningSettings.defaultSort(weights);
        List<ControlledCriterion> criteria =
                controlledCriterionPersistenceService.findCriteria(plan.getControlledCriterionPlanVersionId());
        Set<String> selected = purpose == MatchRunPurpose.COMPANY_DEFAULT
                ? Set.of()
                : selected(command.getSelectedCriterionIds(), preference);
        validateSelected(criteria, selected);
        ControlledCriterionPlan criterionPlan =
                controlledCriterionPlanPersistenceService.findCriterionPlan(plan.getControlledCriterionPlanVersionId());
        ResumeFactBatch currentFactBatch = resumeFacts.currentFactBatch(resumeVersionIds);
        List<ResumeFactSnapshotData> resumes =
                resumeVersionIds.stream().map(currentFactBatch::get).toList();
        // 新选择事实阻断破坏性合并；按候选人排序先取得全部 C 锁。
        resumes.stream().map(ResumeFactSnapshotData::getCandidateId).distinct().sorted()
                .forEach(candidateDirectory::lockActive);
        if (purpose == MatchRunPurpose.CUSTOM) {
            requireScreeningAdmissions(command.getJdVersionId(), resumes, actor);
        }
        String selectionId = ids.newId();
        Instant now = clock.instant();
        String selectionHash =
                hash(
                        resumes.stream()
                                .map(v -> v.getResumeVersionId() + ":" + v.getSnapshotId())
                                .toArray(String[]::new));
        candidateSelectionSnapshotPersistenceService.insertSelection(
                selectionId, command.getJdVersionId(), actor.getUserId(), resumes.size(), selectionHash, now);
        int sequence = 1;
        List<SelectionItem> selectionItems = new ArrayList<>(resumes.size());
        for (var resume : resumes) {
            selectionItems.add(
                    new SelectionItem(
                            selectionId,
                            sequence++,
                            resume.getCandidateId(),
                            resume.getResumeVersionId(),
                            resume.getSnapshotId()));
        }
        if (!selectionItemPersistenceService.insertSelectionItems(selectionItems)) {
            throw new IllegalStateException("candidate selection batch insert failed");
        }
        String runId = ids.newId(), taskId = ids.newId();
        persistence.insertRun(
                runId,
                taskId,
                command.getJdVersionId(),
                jd.getSnapshotId(),
                plan.getId(),
                criterionPlan.getId(),
                weights.getId(),
                preference == null ? null : preference.getId(),
                purpose.getCode(),
                selectionId,
                toJson(selected),
                sort,
                resumes.size(),
                actor.getUserId(),
                actor.getHomeDepartmentId(),
                now);
        tasks.enqueue(
                new EnqueueTask(
                        taskId,
                        TASK_TYPE,
                        "MATCH_RUN",
                        runId,
                        actor.getUserId(),
                        actor.getUserId(),
                        actor.getHomeDepartmentId(),
                        "matching.run",
                        "matching.run",
                        idempotencyKey,
                        requestHash,
                        3,
                        now));
        matchingIdempotencyPersistenceService.insertIdempotency(
                actor.getUserId(), idempotencyKey, "CREATE_MATCH_RUN", requestHash, runId, now);
        return requireRun(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(TaskSnapshot task) {
        MatchRun run = persistence.findRunByTask(task.id());
        if (run == null) {
            tasks.fail(task, "MATCH_RUN_NOT_FOUND", "匹配任务缺少运行记录", false);
            return;
        }
        Instant started = clock.instant();
        startAttempt(run, task, started);
        int succeeded = 0;
        int failed = 0;
        boolean cancelled = false;
        try {
            if (!AiScoringConstants.ALGORITHM_VERSION.equals(run.getScoringVersion())) {
                throw PlatformExceptions.conflict(AiScoringErrorCode.MATCH_LEGACY_RUN_REQUIRES_NEW_SCORING.getCode(),
                        "此任务使用历史规则评分，请新建 AI 评分任务，历史结果不会被覆盖");
            }
            succeeded = matchRunItemOutcomePersistenceService.countSuccessfulOutcomes(run.getId());
            JdFactSnapshotData jd = jdFacts.factsBySnapshotId(run.getJdFactSnapshotId());
            MatchPlanVersion plan = matchPlanVersionPersistenceService.findMatchPlanVersion(run.getPlanVersionId());
            WeightProfileVersion weights =
                    weightProfileVersionPersistenceService.findWeightProfileVersion(run.getWeightProfileVersionId());
            List<ControlledCriterion> criteria = controlledCriterionPersistenceService.findCriteria(run.getCriterionPlanVersionId());
            ControlledCriterionPlan criterionPlan =
                    controlledCriterionPlanPersistenceService.findCriterionPlan(run.getCriterionPlanVersionId());
            Set<String> selected = readSet(run.getSelectedCriterionIdsJson());
            List<SelectionItem> items = selectionItemPersistenceService.findSelectionItems(run.getSelectionSnapshotId());
            if (items.size() != run.getTotalCount()) {
                throw new IllegalStateException(
                        "match selection item count mismatch: expected "
                                + run.getTotalCount()
                                + ", actual "
                                + items.size());
            }
            Set<String> completed = Set.copyOf(matchRunItemOutcomePersistenceService.findSuccessfulResumeVersionIds(run.getId()));
            ResumeFactBatch snapshotFactBatch =
                    resumeFacts.snapshotFactBatch(
                            items.stream()
                                    .filter(item -> !completed.contains(item.getResumeVersionId()))
                                    .map(SelectionItem::getResumeFactSnapshotId)
                                    .toList());
            for (SelectionItem item : items) {
                if (completed.contains(item.getResumeVersionId())) {
                    continue;
                }
                if (tasks.shouldStopProcessing(task)) {
                    cancelled = true;
                    break;
                }
                int progress = Math.min(99, Math.max(1, (succeeded + failed + 1) * 95 / items.size()));
                try {
                    ResumeFactSnapshotData resume =
                            snapshotFactBatch.get(item.getResumeFactSnapshotId());
                    MatchEvaluationDTO evaluation =
                            engine.evaluate(
                                    jd,
                                    resume,
                                    plan,
                                    weights,
                                    criteria,
                                    selected,
                                    criterionPlan.getPlanMaxBonusPoints());
                    transactionTemplate.executeWithoutResult(
                            ignored -> {
                                candidateDirectory.lockActive(item.getCandidateId());
                                tasks.updateProgress(task, progress);
                                persistSuccessfulItem(
                                        task, run, item, evaluation, jd, resume);
                            });
                    succeeded++;
                } catch (RuntimeException ex) {
                    if (isStoppedTaskConflict(ex, task)) {
                        cancelled = true;
                        break;
                    }
                    if (isAiAssessmentContention(ex)) throw ex;
                    LOGGER.error(
                            "match-run-item-failed taskId={} runId={} candidateId={} resumeVersionId={}",
                            task.id(),
                            run.getId(),
                            item.getCandidateId(),
                            item.getResumeVersionId(),
                            SafeLogException.from(ex));
                    String itemError = ex instanceof PlatformException problem ? problem.code() : "MATCH_ITEM_FAILED";
                    transactionTemplate.executeWithoutResult(
                            ignored -> {
                                candidateDirectory.lockActive(item.getCandidateId());
                                tasks.updateProgress(task, progress);
                                int affected =
                                        persistence.upsertItemOutcome(
                                                run.getId(),
                                                item.getCandidateId(),
                                                item.getResumeVersionId(),
                                                MatchRunItemOutcomeStatus.FAILED.getCode(),
                                                itemError,
                                                task.attemptCount(),
                                                clock.instant());
                                requireAffectedRows(affected, "failed match item outcome");
                            });
                    failed++;
                }
            }
            if (!cancelled && tasks.shouldStopProcessing(task)) {
                cancelled = true;
            }
            if (!cancelled) {
                transactionTemplate.executeWithoutResult(ignored -> {
                    requireOwnedLease(task);
                    rank(run);
                });
            }
        } catch (RuntimeException ex) {
            if (isStoppedTaskConflict(ex, task)) {
                finishCancelledRunAndTask(task, run, succeeded, failed, clock.instant());
                return;
            }
            if (isAiAssessmentContention(ex)) {
                deferAiAssessment(task, run, succeeded, failed, (PlatformException) ex);
                return;
            }
            LOGGER.error(
                    "match-run-failed taskId={} runId={} succeededCount={} failedCount={}",
                    task.id(),
                    run.getId(),
                    succeeded,
                    failed,
                    SafeLogException.from(ex));
            Instant now = clock.instant();
            int remaining = Math.max(0, run.getTotalCount() - succeeded);
            MatchRunStatus status = succeeded > 0 ? MatchRunStatus.PARTIAL : MatchRunStatus.FAILED;
            finishRunAndTask(
                    task,
                    run,
                    status,
                    succeeded,
                    Math.max(failed, remaining),
                    MatchRunAttemptStatus.FAILED,
                    "MATCH_RUN_FAILED",
                    succeeded > 0 ? "匹配运行中断，已保留成功结果，可重试剩余项" : "匹配运行失败，可重试",
                    true,
                    now);
            return;
        }
        Instant now = clock.instant();
        if (cancelled) {
            finishCancelledRunAndTask(task, run, succeeded, failed, now);
            return;
        }
        MatchRunStatus status =
                failed == 0
                        ? MatchRunStatus.SUCCEEDED
                        : succeeded > 0 ? MatchRunStatus.PARTIAL : MatchRunStatus.FAILED;
        String error = failed == 0 ? null : "MATCH_ITEMS_FAILED";
        finishRunAndTask(
                task,
                run,
                status,
                succeeded,
                failed,
                status == MatchRunStatus.SUCCEEDED
                        ? MatchRunAttemptStatus.SUCCEEDED
                        : MatchRunAttemptStatus.FAILED,
                error,
                succeeded > 0 ? "部分候选人匹配失败，可仅重试失败项" : "候选人匹配均失败，可重试",
                true,
                now);
    }

    private static boolean isAiAssessmentContention(RuntimeException failure) {
        return failure instanceof PlatformException problem
                && (AiScoringErrorCode.MATCH_AI_ASSESSMENT_BUSY.matches(problem.code())
                    || AiScoringErrorCode.MATCH_AI_ASSESSMENT_LEASE_LOST.matches(problem.code()));
    }

    /** 缓存竞争不是候选人评分失败；保留已完成项，等待租约结束后由有界任务重试。 */
    private void deferAiAssessment(TaskSnapshot task, MatchRun run, int succeeded, int failed, PlatformException failure) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(ignored -> {
            TaskSnapshot next = tasks.failAndRetryAt(task, failure.code(),
                    "正在等待同一简历的 AI 评分结果，将自动重试", now.plusSeconds(AiScoringConstants.CLAIM_LEASE_SECONDS + 5));
            Objects.requireNonNull(next, "AI scoring retry result must not be null");
            boolean cancelled = AsyncTaskStatus.CANCELLED.matches(next.status());
            if (cancelled && alreadyCancelled(run, task)) return;
            boolean queued = AsyncTaskStatus.QUEUED.matches(next.status());
            MatchRunStatus status = cancelled ? MatchRunStatus.CANCELLED : queued ? MatchRunStatus.QUEUED
                    : succeeded > 0 ? MatchRunStatus.PARTIAL : MatchRunStatus.FAILED;
            requireSingleAffectedRow(persistence.finishRun(run.getId(), status, succeeded,
                    queued || cancelled ? failed : Math.max(failed, run.getTotalCount() - succeeded),
                    cancelled ? null : failure.code(), now), "AI scoring deferred run");
            requireSingleAffectedRow(matchRunAttemptPersistenceService.finishAttempt(run.getId(), task.attemptCount(),
                    cancelled ? MatchRunAttemptStatus.CANCELLED : MatchRunAttemptStatus.FAILED,
                    cancelled ? null : failure.code(), now), "AI scoring deferred attempt");
        });
    }

    @Transactional(readOnly = true)
    public MatchRun get(String id) {
        MatchRun run = requireRun(id);
        if (!canReadWholeRun(run) && visibleCandidateIds(run).isEmpty()) authorize(run);
        return run;
    }

    @Transactional(readOnly = true)
    public MatchResultPageResponse results(String id, String cursor, int limit) {
        MatchRun run = requireRun(id);
        CurrentActorSnapshot actor = authorization.currentActor();
        int boundedLimit = PageLimits.resolve(limit);
        String scope = resultCursorScope(run, actor.getUserId());
        int after = decodeCursor(scope, cursor);
        List<WeightedResult> loaded;
        if (canReadWholeRun(run)) {
            loaded = weightedResultPersistenceService.findResultsAfter(id, after, boundedLimit + 1);
        } else {
            Set<String> visible = visibleCandidateIds(run);
            if (visible.isEmpty()) authorize(run);
            // 单次运行至多 1000 项，先按实时共享资格裁剪再分页，不能放出同批私有候选人。
            loaded = weightedResultPersistenceService.findResultsUnsorted(id).stream()
                    .filter(value -> visible.contains(value.getCandidateId()))
                    .filter(value -> value.getStableRank() != null && value.getStableRank() > after)
                    .sorted(Comparator.comparing(WeightedResult::getStableRank)
                            .thenComparing(WeightedResult::getCandidateId))
                    .limit(boundedLimit + 1L)
                    .toList();
        }
        boolean hasMore = loaded.size() > boundedLimit;
        List<WeightedResult> page = loaded.stream().limit(boundedLimit).toList();
        String next =
                hasMore && !page.isEmpty()
                        ? encodeCursor(scope, page.getLast().getStableRank())
                        : null;
        return new MatchResultPageResponse(page.stream().map(this::view).toList(), next, hasMore);
    }

    @Transactional
    public TaskSnapshot cancel(String id, String reason) {
        MatchRun run = requireRun(id);
        authorize(run);
        TaskSnapshot task = tasks.cancel(run.getTaskId(), required(reason));
        if (MatchRunStatus.CANCELLED.matches(task.status())) {
            persistence.cancelQueuedRun(run.getId(), clock.instant());
        }
        return task;
    }

    @Transactional
    public TaskSnapshot retry(String id, String reason) {
        MatchRun run = requireRun(id);
        authorize(run);
        return tasks.retryFailed(run.getTaskId(), required(reason));
    }

    @Transactional
    public MatchEvaluationDTO preview(PreviewMatchDTO command) {
        authorization.requirePermission("matching.run");
        MatchPlanVersion plan = matchPlanVersionPersistenceService.findActiveMatchPlanVersion(command.getJdVersionId());
        if (plan == null) {
            throw PlatformExceptions.conflict("MATCH_PLAN_NOT_ACTIVE", "JD 尚无已激活的正式评分配置");
        }
        WeightProfileVersion weights = requireWeights(command.getWeightProfileVersionId(), authorization.currentActor().getUserId());
        ControlledCriterionPlan criterionPlan =
                controlledCriterionPlanPersistenceService.findCriterionPlan(plan.getControlledCriterionPlanVersionId());
        return engine.preview(
                jdFacts.currentFacts(command.getJdVersionId()),
                resumeFacts.currentFacts(command.getResumeVersionId()),
                plan,
                weights,
                controlledCriterionPersistenceService.findCriteria(plan.getControlledCriterionPlanVersionId()),
                Set.copyOf(
                        command.getSelectedCriterionIds() == null
                                ? List.of()
                                : command.getSelectedCriterionIds()),
                criterionPlan.getPlanMaxBonusPoints());
    }

    private WeightProfileVersion requireWeights(String requestedId, String actorId) {
        String id = requestedId == null ? DefaultScoringConstants.WEIGHT_VERSION_ID : requestedId;
        WeightProfileVersion weights = weightProfileVersionPersistenceService.findWeightProfileVersion(id);
        if (weights == null) throw invalid("五维权重版本不存在");
        if (!DefaultScoringConstants.WEIGHT_VERSION_ID.equals(id)) {
            WeightProfile profile = weightProfilePersistenceService.findWeightProfile(weights.getProfileId());
            if (profile == null || !actorId.equals(profile.getOwnerUserId())) {
                throw new PlatformException(HttpStatus.FORBIDDEN, "WEIGHT_PROFILE_FORBIDDEN", "只能使用公司默认或本人的权重版本", false);
            }
        }
        new ProfessionalWeights(
                weights.getSkillsWeight(), weights.getRoleWeight(), weights.getYearsWeight(),
                weights.getResponsibilityWeight(), weights.getIndustryProjectWeight());
        return weights;
    }

    @Transactional(readOnly = true)
    public BaseEvaluationResponse base(String id) {
        BaseEvaluation value = baseEvaluationPersistenceService.findBase(id);
        if (value == null) {
            throw PlatformExceptions.notFound("BASE_EVALUATION_NOT_FOUND", "基础评估不存在");
        }
        MatchRun run = persistence.findRunByBase(id);
        if (run == null) {
            throw PlatformExceptions.notFound("BASE_EVALUATION_NOT_FOUND", "基础评估未关联可见运行");
        }
        if (!canReadWholeRun(run)) {
            List<String> candidates = weightedResultPersistenceService.findResultsUnsorted(run.getId()).stream()
                    .filter(result -> id.equals(result.getBaseEvaluationId()))
                    .map(WeightedResult::getCandidateId).distinct().toList();
            List<String> owned = persistence.findOwnedPendingResultCandidateIds(run.getId(),
                    authorization.currentActor().getUserId());
            if (candidates.isEmpty() || (candidateLibrary.eligibleCandidateIds(candidates).isEmpty()
                    && (owned == null || candidates.stream().noneMatch(owned::contains)))) {
                authorize(run);
            }
        }
        return new BaseEvaluationResponse(
                value.getId(),
                value.getJdFactSnapshotId(),
                value.getResumeFactSnapshotId(),
                value.getProfessionalScore(),
                value.getBonusScore(),
                value.getReferenceScore(),
                value.getMustTier(),
                readTree(value.getDimensionsJson()),
                readTree(value.getCriteriaJson()),
                value.getAlgorithmVersion(),
                value.getCreateTime());
    }

    private BaseEvaluation persistBase(
            MatchRun run,
            MatchEvaluationDTO value,
            JdFactSnapshotData jd,
            ResumeFactSnapshotData resume) {
        String criterionHash = hash(run.getSelectedCriterionIdsJson());
        String cacheKey =
                hash(
                        value.getJdFactSnapshotId(),
                        value.getResumeFactSnapshotId(),
                        run.getPlanVersionId(),
                        run.getWeightProfileVersionId(),
                        criterionHash,
                        value.getEvaluatedOn().toString(),
                        AiScoringConstants.ALGORITHM_VERSION,
                        value.getAiAssessment() == null ? "" : value.getAiAssessment().getAssessmentId());
        BaseEvaluation existing = baseEvaluationPersistenceService.findBaseByCache(cacheKey);
        if (existing != null) {
            return existing;
        }
        String id = ids.newId();
        Instant now = clock.instant();
        int inserted = persistence.insertBase(
                id,
                cacheKey,
                value.getJdFactSnapshotId(),
                value.getResumeFactSnapshotId(),
                run.getPlanVersionId(),
                run.getWeightProfileVersionId(),
                criterionHash,
                value.getEvaluatedOn(),
                value.getProfessionalScore(),
                value.getBonusScore(),
                value.getReferenceScore(),
                value.getMustTier(),
                value.getSelectedBonusCap(),
                toJson(value.getDimensions()),
                toJson(value.getCriteria()),
                now);
        if (inserted != 1) {
            throw new IllegalStateException("base evaluation insert count mismatch");
        }
        List<MatchingHighlight> highlights = new ArrayList<>(MAX_MATCH_HIGHLIGHTS);
        int sequence = 1;
        for (var dimension : value.getDimensions()) {
            for (String highlight : dimension.getHighlights()) {
                MatchingHighlight entity = new MatchingHighlight();
                entity.setId(ids.newId());
                entity.setBaseEvaluationId(id);
                entity.setDimensionCode(dimension.getCode());
                entity.setHighlightText(highlight);
                entity.setSequenceNo(sequence++);
                highlights.add(entity);
            }
        }
        if (!matchingHighlightPersistenceService.insertHighlightBatch(highlights)) {
            throw new IllegalStateException("matching highlight batch insert failed");
        }
        List<CriterionEvaluation> criterionEvaluations =
                new ArrayList<>(MAX_SELECTED_CRITERIA);
        for (var criterion : value.getCriteria()) {
            CriterionEvaluation entity = new CriterionEvaluation();
            entity.setId(ids.newId());
            entity.setBaseEvaluationId(id);
            entity.setDefinitionId(criterion.getDefinitionId());
            entity.setCriterionCode(criterion.getCode());
            entity.setLevel(criterion.getLevel());
            entity.setEvaluationStatus(criterion.getStatus());
            entity.setSelected(criterion.isSelected());
            entity.setConfiguredBonus(criterion.getConfiguredBonus());
            entity.setAwardedBonus(criterion.getAwardedBonus());
            entity.setActualJson(toJson(criterion.getActual()));
            entity.setExpectedJson(toJson(criterion.getExpected()));
            criterionEvaluations.add(entity);
        }
        if (!criterionEvaluationPersistenceService.insertCriterionEvaluationBatch(
                criterionEvaluations)) {
            throw new IllegalStateException("criterion evaluation batch insert failed");
        }
        BaseEvaluation persisted = baseEvaluationPersistenceService.findBaseByCache(cacheKey);
        if (persisted == null) {
            throw new IllegalStateException("inserted base evaluation cannot be reloaded");
        }
        return persisted;
    }

    /** 单候选人的全部派生数据与成功结果必须在同一个短事务内提交。 */
    private void persistSuccessfulItem(
            TaskSnapshot task,
            MatchRun run,
            SelectionItem item,
            MatchEvaluationDTO evaluation,
            JdFactSnapshotData jd,
            ResumeFactSnapshotData resume) {
        BaseEvaluation base = persistBase(run, evaluation, jd, resume);
        int resultAffected =
                persistence.upsertResult(
                        ids.newId(),
                        run.getId(),
                        base.getId(),
                        resume.getCandidateId(),
                        resume.getResumeVersionId(),
                        evaluation.getProfessionalScore(),
                        evaluation.getBonusScore(),
                        evaluation.getReferenceScore(),
                        evaluation.getMustTier(),
                        toJson(evaluation),
                        clock.instant());
        requireAffectedRows(resultAffected, "weighted match result");
        int outcomeAffected =
                persistence.upsertItemOutcome(
                        run.getId(),
                        item.getCandidateId(),
                        item.getResumeVersionId(),
                        MatchRunItemOutcomeStatus.SUCCEEDED.getCode(),
                        null,
                        task.attemptCount(),
                        clock.instant());
        requireAffectedRows(outcomeAffected, "successful match item outcome");
    }

    /** 运行状态与对应尝试记录必须原子进入执行中，避免留下孤立的 RUNNING 记录。 */
    private void startAttempt(MatchRun run, TaskSnapshot task, Instant started) {
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    requireOwnedLease(task);
                    matchRunAttemptPersistenceService.failPreviousAttempts(run.getId(), task.attemptCount(),
                            started, PlatformActorConstants.SYSTEM_ACTOR_ID);
                    requireSingleAffectedRow(
                            persistence.startRun(run.getId(), started), "match run start");
                    requireSingleAffectedRow(
                            matchRunAttemptPersistenceService.insertAttempt(
                                    ids.newId(), run.getId(), task.attemptCount(), started),
                            "match run attempt insert");
                });
    }

    /** 运行、尝试和共享异步任务使用同一事务完成，杜绝跨表终态分裂。 */
    private void finishRunAndTask(
            TaskSnapshot task,
            MatchRun run,
            MatchRunStatus status,
            int succeeded,
            int failed,
            MatchRunAttemptStatus attemptStatus,
            String errorCode,
            String safeErrorDetail,
            boolean retryable,
            Instant now) {
        try {
            transactionTemplate.executeWithoutResult(
                    ignored -> {
                        TaskSnapshot finalizedTask =
                                errorCode == null
                                        ? tasks.succeed(task, run.getId())
                                        : tasks.fail(
                                                task,
                                                errorCode,
                                                safeErrorDetail,
                                                retryable);
                        Objects.requireNonNull(finalizedTask, "task finalization result must not be null");
                        boolean cancellationWon =
                                AsyncTaskStatus.CANCELLED.matches(finalizedTask.status());
                        if (cancellationWon && alreadyCancelled(run, task)) return;
                        MatchRunStatus finalRunStatus =
                                cancellationWon ? MatchRunStatus.CANCELLED : status;
                        MatchRunAttemptStatus finalAttemptStatus =
                                cancellationWon
                                        ? MatchRunAttemptStatus.CANCELLED
                                        : attemptStatus;
                        String finalErrorCode = cancellationWon ? null : errorCode;
                        requireSingleAffectedRow(
                                persistence.finishRun(
                                        run.getId(),
                                        finalRunStatus,
                                        succeeded,
                                        failed,
                                        finalErrorCode,
                                        now),
                                "match run finish");
                        requireSingleAffectedRow(
                                matchRunAttemptPersistenceService.finishAttempt(
                                        run.getId(),
                                        task.attemptCount(),
                                        finalAttemptStatus,
                                        finalErrorCode,
                                        now),
                                "match run attempt finish");
                    });
        } catch (PlatformException ex) {
            if (!AsyncTaskErrorCode.TASK_STATE_CONFLICT.matches(ex.code())
                    || !tasks.shouldStopProcessing(task)) {
                throw ex;
            }
            // 取消请求在成功 CAS 前胜出：成功事务已回滚，改为提交一致的取消终态。
            finishCancelledRunAndTask(task, run, succeeded, failed, now);
        }
    }

    /** 取消终态与共享任务取消确认同事务提交。 */
    private void finishCancelledRunAndTask(
            TaskSnapshot task, MatchRun run, int succeeded, int failed, Instant now) {
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    Objects.requireNonNull(
                            tasks.cancelled(task),
                            "task cancellation result must not be null");
                    if (alreadyCancelled(run, task)) return;
                    requireSingleAffectedRow(
                            persistence.finishRun(
                                    run.getId(),
                                    MatchRunStatus.CANCELLED,
                                    succeeded,
                                    failed,
                                    null,
                                    now),
                            "cancelled match run finish");
                    requireSingleAffectedRow(
                            matchRunAttemptPersistenceService.finishAttempt(
                                    run.getId(),
                                    task.attemptCount(),
                                    MatchRunAttemptStatus.CANCELLED,
                                    null,
                                    now),
                            "cancelled match run attempt finish");
                });
    }

    private static void requireSingleAffectedRow(int affected, String operation) {
        if (affected != 1) {
            throw new IllegalStateException(operation + " affected unexpected row count: " + affected);
        }
    }

    /** 续租加入当前短事务并持有任务行锁；不能只读一次状态后继续写业务数据。 */
    private void requireOwnedLease(TaskSnapshot task) {
        if (!tasks.renewLease(task)) {
            throw PlatformExceptions.conflict(AsyncTaskErrorCode.TASK_STATE_CONFLICT.getCode(), "任务租约已失效或已取消");
        }
    }

    /** 取消及失租冲突停止处理；普通计算错误仍按条目失败处理。 */
    private boolean isStoppedTaskConflict(RuntimeException failure, TaskSnapshot task) {
        return failure instanceof PlatformException conflict
                && AsyncTaskErrorCode.TASK_STATE_CONFLICT.matches(conflict.code())
                && tasks.shouldStopProcessing(task);
    }

    /** 租约回收可能已经完成取消；锁定业务记录后保留该终态，避免重复结束尝试。 */
    private boolean alreadyCancelled(MatchRun run, TaskSnapshot task) {
        MatchRun latest = persistence.findBoundRunForUpdate(run.getId(), task.id());
        return latest != null && MatchRunStatus.CANCELLED.matches(latest.getStatus());
    }

    private static void requireAffectedRows(int affected, String operation) {
        if (affected < 1) {
            throw new IllegalStateException(operation + " did not affect any row");
        }
    }

    private void rank(MatchRun run) {
        List<WeightedResult> values = new ArrayList<>(weightedResultPersistenceService.findResultsUnsorted(run.getId()));
        values.sort(resultComparator(run.getSortMode()));
        Instant now = clock.instant();
        int rank = 1;
        for (var value : values) {
            value.setStableRank(rank++);
        }
        if (!values.isEmpty()
                && weightedResultPersistenceService.updateRanks(run.getId(), values, now) != values.size()) {
            throw new IllegalStateException("weighted result rank update count mismatch");
        }
    }

    static Comparator<WeightedResult> resultComparator(String mode) {
        return Comparator.comparingInt((WeightedResult v) -> tier(v.getMustTier()))
                .thenComparing(score(mode))
                .reversed()
                .thenComparing(WeightedResult::getCandidateId);
    }

    private static Comparator<WeightedResult> score(String mode) {
        return switch (MatchResultSortMode.fromCode(mode)) {
            case PROFESSIONAL_SCORE -> Comparator.comparing(WeightedResult::getProfessionalScore);
            case BONUS_SCORE -> Comparator.comparingInt(WeightedResult::getBonusScore);
            case REFERENCE_SCORE -> Comparator.comparing(WeightedResult::getReferenceScore);
        };
    }

    private static int tier(String value) {
        return switch (CriterionMatchStatus.fromCode(value)) {
            case PASS -> 3;
            case UNKNOWN -> 2;
            case FAIL -> 1;
        };
    }

    private MatchResultResponse view(WeightedResult value) {
        return new MatchResultResponse(
                value.getId(),
                value.getBaseEvaluationId(),
                value.getCandidateId(),
                value.getResumeVersionId(),
                value.getProfessionalScore(),
                value.getBonusScore(),
                value.getReferenceScore(),
                value.getMustTier(),
                value.getStableRank(),
                readTree(value.getResultJson()));
    }

    private void authorize(MatchRun run) {
        authorization.requireAccess(
                "matching.run",
                new AuthorizationTarget(
                        "MATCH_RUN", run.getId(), run.getOwnerUserId(), run.getOwnerDepartmentId(), Set.of()));
    }

    private boolean canReadWholeRun(MatchRun run) {
        return authorization.canAccess(authorization.currentActor(), "matching.run",
                new AuthorizationTarget("MATCH_RUN", run.getId(), run.getOwnerUserId(),
                        run.getOwnerDepartmentId(), Set.of()));
    }

    private Set<String> sharedCandidateIds(MatchRun run) {
        List<String> candidates = selectionItemPersistenceService.findSelectionItems(run.getSelectionSnapshotId())
                .stream().map(SelectionItem::getCandidateId).distinct().toList();
        return candidates.isEmpty() ? Set.of() : candidateLibrary.eligibleCandidateIds(candidates);
    }

    private Set<String> visibleCandidateIds(MatchRun run) {
        Set<String> visible = new LinkedHashSet<>(sharedCandidateIds(run));
        List<String> owned = persistence.findOwnedPendingResultCandidateIds(run.getId(),
                authorization.currentActor().getUserId());
        if (owned != null) visible.addAll(owned);
        return visible;
    }

    private MatchRun requireRun(String id) {
        MatchRun value = persistence.findRun(id);
        if (value == null) {
            throw PlatformExceptions.notFound("MATCH_RUN_NOT_FOUND", "匹配运行不存在");
        }
        return value;
    }

    private Set<String> selected(List<String> command, PersonalPreference preference) {
        if (command != null) {
            return Set.copyOf(command);
        }
        if (preference == null) {
            return Set.of();
        }
        return readSet(preference.getSelectedDefinitionIdsJson());
    }

    private void requireScreeningAdmissions(
            String jdVersionId, List<ResumeFactSnapshotData> resumes, CurrentActorSnapshot actor) {
        var admitted = screeningAdmissionPersistenceService.findScreeningCandidates(
                resumes.stream().map(ResumeFactSnapshotData::getCandidateId).distinct().toList(),
                actor.getUserId(),
                actor.hasCompanyWideScope(),
                actor.getOperationalManagedUserIds(),
                actor.visibleDepartmentIdsFor("MATCH_RUN"),
                false);
        Set<String> admittedKeys = admitted.stream()
                .filter(value -> value.getAdmissionId() != null)
                .map(value -> value.getCandidateId() + ":" + value.getResumeVersionId() + ":"
                        + value.getJdVersionId())
                .collect(Collectors.toSet());
        boolean missing = resumes.stream().anyMatch(value -> !admittedKeys.contains(
                value.getCandidateId() + ":" + value.getResumeVersionId() + ":" + jdVersionId));
        if (missing) {
            throw PlatformExceptions.conflict(
                    "SCREENING_ADMISSION_REQUIRED", "自定义评分只能使用已明确加入智能筛选的简历和岗位版本");
        }
    }

    /**
     * 校验是否已选中。
     */
    private static void validateSelected(List<ControlledCriterion> criteria, Set<String> selected) {
        Set<String> allowed =
                criteria.stream()
                        .filter(
                            v ->
                                ControlledCriterionLevel.PREFER.matches(v.getLevel())
                                    && v.isSelectableByHr())
                        .map(ControlledCriterion::getId)
                        .collect(Collectors.toSet());
        if (!allowed.containsAll(selected)) {
            throw invalid("个人附加条件选择不属于当前正式方案");
        }
    }

    private Set<String> readSet(String value) {
        try {
            return Set.copyOf(json.readValue(value, new TypeReference<List<String>>() {
            }));
        } catch (Exception ex) {
            throw new IllegalStateException("invalid persisted criterion selection", ex);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("invalid persisted evaluation json", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("json serialization failed", ex);
        }
    }

    private static List<String> unique(List<String> values) {
        return values == null
                ? List.of()
                : List.copyOf(
                        new LinkedHashSet<>(values.stream().filter(v -> v != null && !v.isBlank()).toList()));
    }

    /**
     * 规范化排序。
     */
    private static String normalizeSort(String value) {
        String result = value == null ? MatchResultSortMode.REFERENCE_SCORE.getCode() : CommonUtils.upperOrEmpty(value);
        try {
            MatchResultSortMode.fromCode(result);
        } catch (IllegalArgumentException exception) {
            throw invalid("排序方式非法");
        }
        return result;
    }

    /**
     * 编码游标。
     */
    String encodeCursor(String scope, int rank) {
        return cursorCodec.encode(scope, List.of(String.valueOf(rank)));
    }

    /**
     * 解码游标。
     */
    int decodeCursor(String scope, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int rank = Integer.parseInt(cursorCodec.decode(cursor, scope, 1).getFirst());
            if (rank < 1) {
                throw new IllegalArgumentException();
            }
            return rank;
        } catch (RuntimeException ex) {
            throw new PlatformException(
                    HttpStatus.CONFLICT, "MATCH_CURSOR_SCOPE_MISMATCH", "结果游标不属于当前 MatchRun 或已失效", true);
        }
    }

    private static String resultCursorScope(MatchRun run, String actorUserId) {
        return CursorScopes.scope(
                "matching.results",
                actorUserId,
                run.getId(),
                run.getJdVersionId(),
                run.getJdFactSnapshotId(),
                run.getPlanVersionId(),
                run.getCriterionPlanVersionId(),
                run.getWeightProfileVersionId(),
                run.getPersonalPreferenceVersionId(),
                run.getSelectionSnapshotId(),
                run.getSortMode());
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw invalid("原因必须为 1～500 字");
        }
        return value.strip();
    }

    private static String hash(String... values) {
        return Sha256Utils.fingerprintStrings("null", 0, values);
    }

    private static PlatformException invalid(String detail) {
        return new PlatformException(
                HttpStatus.UNPROCESSABLE_CONTENT, "MATCH_RUN_INVALID", detail, false);
    }

    /**
     * 创建响应。
     */
    @Transactional
    public MatchRunRunResponse createResponse(CreateMatchRunDTO command, String idempotencyKey) {
        return MatchRunRunResponse.from(create(command, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public MatchRunRunResponse getResponse(String id) {
        return MatchRunRunResponse.from(get(id));
    }

    @Transactional
    public MatchRunTaskResponse cancelResponse(String id, String reason) {
        return MatchRunTaskResponse.from(cancel(id, reason));
    }

    /**
     * 重试响应。
     */
    @Transactional
    public MatchRunTaskResponse retryResponse(String id, String reason) {
        return MatchRunTaskResponse.from(retry(id, reason));
    }

    /**
     * 预览响应。
     */
    @Transactional
    public MatchPreviewResponse previewResponse(PreviewMatchDTO command) {
        MatchEvaluationDTO value = preview(command);
        MatchPreviewResponse response = new MatchPreviewResponse(
                value.getJdFactSnapshotId(),
                value.getResumeFactSnapshotId(),
                value.getCandidateId(),
                value.getResumeVersionId(),
                value.getEvaluatedOn(),
                value.getProfessionalScore(),
                value.getBonusScore(),
                value.getReferenceScore(),
                value.getMustTier(),
                value.getSelectedBonusCap(),
                value.getDimensions(),
                value.getCriteria(), value.getAlgorithmVersion(), value.getAiAssessment());
        return response;
    }
}

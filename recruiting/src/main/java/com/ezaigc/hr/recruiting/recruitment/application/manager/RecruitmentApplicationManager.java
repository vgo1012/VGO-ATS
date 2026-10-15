package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.activity.application.support.ActivityRequestContext;
import static com.ezaigc.hr.common.web.request.RequiredIdentifier.require;
import static com.ezaigc.hr.common.web.request.RequiredText.required;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.SUBMISSION_WITHDRAWAL_PAGE_SIZE;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.APPLICATION_TERMINATION_PAGE_SIZE;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_COLLABORATORS_PER_APPLICATION;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_INTENTION_LOCATIONS_STORAGE_LENGTH;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_INTENTION_LOCATION_LENGTH;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_LOCATIONS_PER_INTENTION;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.api.HandoverTransferResult;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.api.ActiveWorkflowSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewNotificationApplicationManager;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationType;
import com.ezaigc.hr.recruiting.interview.enums.InterviewStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewType;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.api.ActiveMatchPlanQueryService;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.CreateApplicationDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationStageCountResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.InterviewContext;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.OfferCommandDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.SubmissionApplicationContext;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationDetails;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUp;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Intention;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingEvent;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItem;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationCommunicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDecisionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDetailsResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationFollowUpResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationIntentionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationWorkflowStepResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OfferStatusResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingEventResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingProcessResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.WorkItemResponse;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationDecision;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStage;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationInterviewType;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationLifecycle;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStepResult;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationStepStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.CandidateReplyResult;
import com.ezaigc.hr.recruiting.recruitment.enums.CommunicationChannel;
import com.ezaigc.hr.recruiting.recruitment.enums.CommunicationResult;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.OfferTerminationReason;
import com.ezaigc.hr.recruiting.recruitment.enums.OnboardingReturnReason;
import com.ezaigc.hr.recruiting.recruitment.enums.OnboardingProcessStatus;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.CommunicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.IntentionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingEventPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingProcessPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionValidationAdapter;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.talent.api.CandidateOnboardingReturnService;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.ResumeLifecycleService;
import com.ezaigc.hr.talent.api.EducationApplicationAccessService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import java.util.stream.Collectors;

/**
 * 招聘应用服务，负责用例编排、权限校验和事务边界。
 */
@Service
public class RecruitmentApplicationManager
        implements HandoverResourceContributor,
        EducationApplicationAccessService {
  @Resource private ActivityOriginRecorder activityOrigins;


    @Resource
    private ApplicationAssembler assembler;

    /**
     * 按创建日期统计整个授权集合，不受列表分页数量影响。
     */
    @Transactional(readOnly = true)
    public List<ApplicationStageCountResponse> stageCounts(
            Instant from, Instant to, String jdId, String jdVersionId) {
        authorization.requirePermission("recruitment.manage");
        if (from == null || to == null || !from.isBefore(to)
                || to.isAfter(from.plusSeconds(366L * 86400))) {
            throw PlatformExceptions.unprocessable("INVALID_DATE_RANGE", "统计时间范围必须大于零且不超过366天");
        }
        var actor = authorization.currentActor();
        Map<String, ApplicationStageCountDTO> counts = new LinkedHashMap<>();
        var query = new ApplicationStageCountQuery(from, to, CommonUtils.stripToNull(jdId),
                CommonUtils.stripToNull(jdVersionId), actor.hasCompanyWideScope(), actor.getUserId(),
                actor.getManagedUserIds(), actor.visibleDepartmentIdsFor("APPLICATION"), null);
        mergeStageCounts(counts, persistence.countStages(query));
        if (!query.isAllScope()) {
            String afterId = null;
            while (true) {
                var grants = authorization.grantedObjectIds("recruitment.manage", "APPLICATION", afterId);
                if (grants.isEmpty()) break;
                mergeStageCounts(counts, persistence.countStages(new ApplicationStageCountQuery(from, to,
                        query.getJdId(), query.getJdVersionId(), false, query.getActorId(),
                        query.getManagedUserIds(), query.getDepartmentIds(), grants)));
                if (grants.size() < 200) break;
                afterId = grants.getLast();
            }
        }
        return counts.values().stream().sorted(Comparator.comparing(ApplicationStageCountDTO::getFirstApplicationId))
                .map(row -> new ApplicationStageCountResponse(row.getStage(), row.getCount())).toList();
    }

    private static void mergeStageCounts(Map<String, ApplicationStageCountDTO> counts, List<ApplicationStageCountDTO> rows) {
        rows.forEach(row -> counts.merge(row.getStage(), row, (left, right) -> new ApplicationStageCountDTO(
                left.getStage(), left.getCount() + right.getCount(),
                left.getFirstApplicationId().compareTo(right.getFirstApplicationId()) <= 0
                        ? left.getFirstApplicationId() : right.getFirstApplicationId())));
    }

    /**
     * 创建应聘流程。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDetailsResponse createApplication(
            CreateApplicationDTO command, String idempotencyKey) {
        return applicationDetailsView(create(command, idempotencyKey));
    }

    /**
     * 批量推荐保留旧字段兼容；新活动只进入待 HR 跟进，不预选执行路径。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDetailsResponse createApplicationWithPath(
            CreateApplicationDTO command, List<WorkflowStepCode> selectedCodes, String key) {
        ApplicationDetails created = create(command, key, selectedCodes);
        if (selectedCodes != null && !created.getApplication().isProgressiveFollowUp()
                && ApplicationStage.PATH_SELECTION.matches(created.getApplication().getCurrentStage())) {
            Set<String> requested = selectedCodes.stream().map(WorkflowStepCode::getCode).collect(Collectors.toSet());
            Set<String> available = created.getWorkflowSteps().stream().map(WorkflowStep::getStepCode).collect(Collectors.toSet());
            if (requested.size() != selectedCodes.size() || !available.containsAll(requested)) {
                throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "所选公共路径包含重复或不属于当前流程的步骤");
            }
            List<String> stepIds = created.getWorkflowSteps().stream().filter(step -> requested.contains(step.getStepCode())).map(WorkflowStep::getId)
                    .toList();
            created = selectPath(created.getApplication().getId(), stepIds, command.getReason(), created.getApplication().getVersion(), false);
        }
        return applicationDetailsView(created);
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationResponse> applicationResponses(
            String jdId,
            String jdVersionId,
            String stage,
            String stepCode,
            String ownerUserId,
            String cursor,
            Integer limit) {
        return applicationViews(list(jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, limit));
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationResponse> applicationResponses(String jdId, String jdVersionId, String stage,
            String stepCode, String ownerUserId, String cursor, Integer limit, String recruitmentType, String clientId,
            String lifecycle) {
        return applicationViews(list(jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, limit,
                recruitmentType, clientId, lifecycle));
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationResponse> applicationResponses(String jdId, String jdVersionId, String stage,
            String stepCode, String ownerUserId, String cursor, Integer limit, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter) {
        return applicationResponses(jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, limit,
                recruitmentType, clientId, lifecycle, candidateFilter, null, null, null);
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationResponse> applicationResponses(String jdId, String jdVersionId, String stage,
            String stepCode, String ownerUserId, String cursor, Integer limit, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter, Instant from, Instant to, String interviewType) {
        return applicationResponses(jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, limit,
                recruitmentType, clientId, lifecycle, candidateFilter, from, to, interviewType, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationResponse> applicationResponses(String jdId, String jdVersionId, String stage,
            String stepCode, String ownerUserId, String cursor, Integer limit, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter, Instant from, Instant to,
            String interviewType, boolean onboarding) {
        return applicationViews(list(jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, limit,
                recruitmentType, clientId, lifecycle, candidateFilter, from, to, interviewType, onboarding));
    }

    @Transactional(readOnly = true)
    public CursorCount countApplications(String jdId, String jdVersionId, String stage,
            String stepCode, String ownerUserId, String cursor, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter, Instant from, Instant to,
            String interviewType, boolean onboarding) {
        CursorPage<Application> page = list(
                jdId, jdVersionId, stage, stepCode, ownerUserId, cursor, 100,
                recruitmentType, clientId, lifecycle, candidateFilter, from, to, interviewType, onboarding);
        return new CursorCount(page.getItems().size(), page.getNextCursor(), page.isHasMore());
    }

    @Transactional(readOnly = true)
    public ApplicationDetailsResponse applicationResponse(String id) {
        return applicationDetailsView(get(id));
    }

    /**
     * 更新协作人列表响应。
     */
    @Transactional
    public ApplicationDetailsResponse updateCollaboratorsResponse(
            String id, List<String> userIds, long version, String reason) {
        return assembler.toResponse(updateCollaborators(id, userIds, version, reason));
    }

    /**
     * 转交响应。
     */
    @Transactional
    public ApplicationDetailsResponse transferResponse(
            String id, String targetUserId, String reason, long version, String transferType) {
        return assembler.toResponse(transfer(id, targetUserId, reason, version, transferType));
    }

    @Transactional
    public ApplicationDecisionResponse decideResponse(
            String id, String decision, String reason, String submissionId, String idempotencyKey) {
        return assembler.toResponse(decide(id, decision, reason, submissionId, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<ApplicationWorkflowStepResponse> stepResponses(String id) {
        return assembler.toStepResponses(steps(id));
    }

    @Transactional
    public ApplicationDetailsResponse selectPathResponse(
            String id, List<String> selectedStepIds, String reason, long version, boolean amendment) {
        return assembler.toResponse(selectPath(id, selectedStepIds, reason, version, amendment));
    }

    /**
     * 启动步骤响应。
     */
    @Transactional
    public ApplicationWorkflowStepResponse startStepResponse(String id, String stepId, long version) {
        return assembler.toResponse(startStep(id, stepId, version));
    }

    @Transactional
    public ApplicationFollowUpResponse followUpResponse(
            String id,
            String stepId,
            String channel,
            String summary,
            String nextPlan,
            Instant contactedAt) {
        return assembler.toResponse(followUp(id, stepId, channel, summary, nextPlan, contactedAt));
    }

    @Transactional(readOnly = true)
    public List<ApplicationFollowUpResponse> followUpResponses(String id, String stepId) {
        return assembler.toFollowUpResponses(followUps(id, stepId));
    }

    /**
     * 完成步骤响应。
     */
    @Transactional
    public ApplicationDetailsResponse completeStepResponse(
            String id, String stepId, String result, String reason, String submissionId, long version) {
        return assembler.toResponse(completeStep(id, stepId, result, reason, submissionId, version));
    }

    @Transactional
    public ApplicationDetailsResponse completeStepResponse(String id, String stepId, String result, String reason,
            String submissionId, long version, String channel, String nextPlan) {
        return assembler.toResponse(completeStepInternal(id, stepId, result, reason, submissionId, version, channel, nextPlan, true));
    }

    @Transactional
    public ApplicationDetailsResponse skipStepResponse(
            String id, String stepId, String reason, long version) {
        return assembler.toResponse(skipStep(id, stepId, reason, version));
    }

    /**
     * 更正决定响应。
     */
    @Transactional
    public ApplicationDecisionResponse correctDecisionResponse(
            String id,
            String decisionId,
            String decision,
            String reason,
            String submissionId,
            String idempotencyKey) {
        return assembler.toResponse(
                correctDecision(id, decisionId, decision, reason, submissionId, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<ApplicationCommunicationResponse> communicationResponses(String id) {
        return assembler.toCommunicationResponses(communications(id));
    }

    @Transactional
    public ApplicationCommunicationResponse communicateResponse(
            String id,
            String channel,
            String summary,
            String result,
            String nextPlan,
            Instant communicatedAt, String idempotencyKey) {
        return assembler.toResponse(
                communicate(id, channel, summary, result, nextPlan, communicatedAt, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<ApplicationIntentionResponse> intentionResponses(String id) {
        return assembler.toIntentionResponses(intentions(id));
    }

    @Transactional
    public ApplicationIntentionResponse addIntentionResponse(
            String id,
            BigDecimal minimumSalary,
            BigDecimal maximumSalary,
            String period,
            String availability,
            List<String> locations,
            String reason) {
        return assembler.toResponse(
                addIntention(id, minimumSalary, maximumSalary, period, availability, locations, reason));
    }

    @Transactional(readOnly = true)
    public List<OfferStatusResponse> offerResponses(String id) {
        return assembler.toOfferResponses(offers(id));
    }

    @Transactional
    public OfferStatusResponse addOfferResponse(
            String id, OfferCommandDTO command, String idempotencyKey) {
        return assembler.toResponse(addOffer(id, command, idempotencyKey));
    }

    @Transactional
    public OfferStatusResponse correctOfferResponse(
            String id, String recordId, OfferCommandDTO command, String idempotencyKey) {
        return assembler.toResponse(correctOffer(id, recordId, command, idempotencyKey));
    }

    @Transactional
    public OnboardingEventResponse onboardResponse(
            String id,
            Instant onboardedAt,
            String sourceReference,
            String reason,
            String idempotencyKey) {
        return assembler.toResponse(onboard(id, onboardedAt, sourceReference, reason, idempotencyKey));
    }

    @Transactional
    public OnboardingProcessResponse hireResponse(
            String id, String offerStatus, String reason, String idempotencyKey) {
        OnboardingProcess process = hire(id, offerStatus, reason, idempotencyKey);
        return assembler.toResponse(process, onboardingElapsedSeconds(process));
    }

    @Transactional
    public OnboardingProcessResponse reviewOnboardingResponse(
            String id, Instant onboardedAt, String reason, String idempotencyKey) {
        OnboardingProcess process = reviewOnboarding(id, onboardedAt, reason, idempotencyKey);
        return assembler.toResponse(process, onboardingElapsedSeconds(process));
    }

    @Transactional
    public OfferStatusResponse returnOnboardingToSharedResponse(
            String id, String reasonCategory, String reasonDetail, String idempotencyKey) {
        return assembler.toResponse(
                returnOnboardingToShared(id, reasonCategory, reasonDetail, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<WorkItemResponse> workItemResponses(boolean management, int limit) {
        return assembler.toWorkItemResponses(workbench(management, limit));
    }

    private CursorPage<ApplicationResponse> applicationViews(CursorPage<Application> page) {
        List<String> versionIds = page.getItems().stream()
                .map(Application::getJdVersionId).distinct().toList();
        Map<String, JdClientSummary> clients = versionIds.isEmpty() ? Map.of()
                : demands.clientSummariesForVersions(versionIds);
        List<String> processingIds = page.getItems().stream()
                .filter(application -> ApplicationStage.IN_PROCESS.matches(application.getCurrentStage()))
                .map(Application::getId).toList();
        Map<String, WorkflowStep> currentSteps = new LinkedHashMap<>();
        if (!processingIds.isEmpty()) {
            recruitmentWorkflowStepPersistenceService.findActiveSteps(processingIds)
                    .forEach(step -> currentSteps.putIfAbsent(step.getApplicationId(), step));
        }
        Map<String, String> interviewTypes = currentSteps.isEmpty() ? Map.of()
                : interviewPersistenceService.latestTypesByStepIds(currentSteps.values().stream()
                        .map(WorkflowStep::getId).collect(Collectors.toSet()));
        Set<String> activeInterviewApplicationIds = page.getItems().stream()
                .filter(application -> !ApplicationStage.isTerminal(application.getCurrentStage()))
                .filter(application -> application.getTerminalAt() == null)
                .map(Application::getId).collect(Collectors.toSet());
        Map<String, List<Integer>> activeInterviewRounds = activeInterviewApplicationIds.isEmpty() ? Map.of()
                : interviewPersistenceService.activeRoundNosByApplicationIds(activeInterviewApplicationIds);
        Map<String, Integer> latestCompletedInterviewRounds = activeInterviewApplicationIds.isEmpty() ? Map.of()
                : interviewPersistenceService.latestCompletedRoundNosByApplicationIds(activeInterviewApplicationIds);
        Map<String, OnboardingProcess> onboardingProcesses = onboardingProcessPersistenceService
                .findProcesses(page.getItems().stream().map(Application::getId).toList()).stream()
                .collect(Collectors.toMap(OnboardingProcess::getApplicationId, value -> value));
        return new CursorPage<>(page.getItems().stream()
                .map(application -> {
                    var response = assembler.toResponse(application);
                    WorkflowStep currentStep = currentSteps.get(application.getId());
                    applyCurrentStep(response, application, currentStep);
                    if (currentStep != null && response.getCurrentStepCode() != null) {
                        response.setCurrentInterviewType(interviewTypes.get(currentStep.getId()));
                    }
                    applyActiveInterviewRounds(response, application, activeInterviewRounds);
                    applyLatestCompletedInterviewRound(response, application, latestCompletedInterviewRounds);
                    applyOnboardingProcess(response, onboardingProcesses.get(application.getId()));
                    var client = clients.get(application.getJdVersionId());
                    if (client != null) {
                        response.setClientId(client.getClientId());
                        response.setClientName(client.getClientName());
                    }
                    return response;
                })
                .toList(), page.getNextCursor(), page.isHasMore());
    }

    private ApplicationDetailsResponse applicationDetailsView(ApplicationDetails details) {
        var response = assembler.toResponse(details);
        WorkflowStep currentStep = details.getWorkflowSteps().stream()
                .filter(step -> ApplicationStepStatus.isActive(step.getStatus()))
                .min(Comparator.comparingInt(WorkflowStep::getSequenceNo)
                        .thenComparing(WorkflowStep::getId))
                .orElse(null);
        applyCurrentStep(response.getApplication(), details.getApplication(), currentStep);
        if (currentStep != null && response.getApplication().getCurrentStepCode() != null) {
            response.getApplication().setCurrentInterviewType(interviewPersistenceService
                    .latestTypesByStepIds(Set.of(currentStep.getId())).get(currentStep.getId()));
        }
        Application application = details.getApplication();
        Map<String, List<Integer>> activeInterviewRounds =
                ApplicationStage.isTerminal(application.getCurrentStage()) || application.getTerminalAt() != null
                        ? Map.of()
                        : interviewPersistenceService.activeRoundNosByApplicationIds(Set.of(application.getId()));
        applyActiveInterviewRounds(response.getApplication(), application, activeInterviewRounds);
        Map<String, Integer> latestCompletedInterviewRounds =
                ApplicationStage.isTerminal(application.getCurrentStage()) || application.getTerminalAt() != null
                        ? Map.of()
                        : interviewPersistenceService.latestCompletedRoundNosByApplicationIds(Set.of(application.getId()));
        applyLatestCompletedInterviewRound(response.getApplication(), application, latestCompletedInterviewRounds);
        applyOnboardingProcess(
                response.getApplication(), onboardingProcessPersistenceService.findProcess(application.getId()));
        var jd = demands.requireJdVersion(details.getApplication().getJdVersionId());
        if (jd != null) {
            response.getApplication().setClientId(jd.getClientId());
            response.getApplication().setClientName(jd.getClientName());
        }
        return response;
    }

    static void applyCurrentStep(
            ApplicationResponse response, Application application, WorkflowStep currentStep) {
        if (!ApplicationStage.IN_PROCESS.matches(application.getCurrentStage())
                || application.getTerminalAt() != null
                || currentStep == null
                || !ApplicationStepStatus.isActive(currentStep.getStatus())) {
            return;
        }
        response.setCurrentStepCode(currentStep.getStepCode());
        response.setCurrentStepRoundNo(currentStep.getRoundNo());
        response.setCurrentStepStatus(currentStep.getStatus());
    }

    static void applyActiveInterviewRounds(
            ApplicationResponse response,
            Application application,
            Map<String, List<Integer>> roundsByApplication) {
        if (ApplicationStage.isTerminal(application.getCurrentStage()) || application.getTerminalAt() != null) {
            response.setActiveInterviewRoundNos(List.of());
            return;
        }
        response.setActiveInterviewRoundNos(
                roundsByApplication.getOrDefault(application.getId(), List.of()));
    }

    static void applyLatestCompletedInterviewRound(
            ApplicationResponse response,
            Application application,
            Map<String, Integer> roundsByApplication) {
        if (ApplicationStage.isTerminal(application.getCurrentStage()) || application.getTerminalAt() != null) {
            response.setLatestCompletedInterviewRoundNo(null);
            return;
        }
        response.setLatestCompletedInterviewRoundNo(roundsByApplication.get(application.getId()));
    }

    private void applyOnboardingProcess(
            ApplicationResponse response, OnboardingProcess process) {
        if (process == null) {
            return;
        }
        response.setOnboardingStatus(process.getStatus());
        response.setOnboardingStartedAt(process.getStartedAt());
        response.setActualOnboardedAt(process.getActualOnboardedAt());
        response.setOnboardingElapsedSeconds(onboardingElapsedSeconds(process));
    }

    private long onboardingElapsedSeconds(OnboardingProcess process) {
        Instant end = process.getActualOnboardedAt() == null ? clock.instant() : process.getActualOnboardedAt();
        return Math.max(0, Duration.between(process.getStartedAt(), end).getSeconds());
    }

    /**
     * 同一活动锁串行化预约、下一步选择与终止；保留负责人及处理权限边界。
     */
    @Transactional
    public Application requireLockedFollowUpApplication(String id) {
        var app = requireLockedAuthorized(id);
        requireOpen(app);
        requireProcessingAllowed(app);
        return app;
    }

    @Transactional
    public Application requireLockedFollowUpCancellation(String id) {
        return requireLockedProcessingApplication(id);
    }

    /** 幂等结果确认仍检查当前处理权限，但不要求活动仍允许发起新沟通。 */
    @Transactional
    public Application requireLockedFollowUpAccess(String id) {
        return requireLockedProcessingApplication(id);
    }

    /** 终止入口沿用 Offer 的 JD → Application 锁序，并校验当前处理权限。 */
    @Transactional
    public Application requireLockedTerminationAccess(String id) {
        var app = requireHeadcountLockedApplication(id);
        return app;
    }

    @Transactional(readOnly = true)
    public Application requireFollowUpApplication(String id) {
        return requireReadable(id);
    }

    /** 批量重新校验跟进计划当前页关联的招聘活动，避免逐条权限查询。 */
    @Transactional(readOnly = true)
    public Map<String, Application> requireFollowUpApplications(Collection<String> ids) {
        if (ids == null) {
            throw PlatformExceptions.badRequest(
                    "FOLLOW_UP_APPLICATION_IDS_INVALID", "招聘活动编号集合不能为空");
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<String> requestedIds = ids.stream()
                .map(id -> require(id, "applicationId"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.size() > PageLimits.MAXIMUM) {
            throw PlatformExceptions.badRequest(
                    "FOLLOW_UP_APPLICATION_IDS_LIMIT_EXCEEDED", "单次最多校验 100 个招聘活动");
        }

        authorization.currentActor();
        List<Application> applications = persistence.listByIds(requestedIds);
        Map<String, Application> applicationsById = applications.stream()
                .filter(application -> requestedIds.contains(application.getId()))
                .collect(Collectors.toMap(
                        Application::getId,
                        application -> application,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (!applicationsById.keySet().containsAll(requestedIds)) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        Set<String> accessibleIds = accessibleApplicationIds(
                authorization.currentActor(), new ArrayList<>(applicationsById.values()));
        if (!accessibleIds.containsAll(requestedIds)) {
            throw PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "当前账号无权访问该业务对象");
        }
        return Map.copyOf(applicationsById);
    }

    @Transactional(readOnly = true)
    public Set<String> visibleFollowUpApplicationIds(List<Application> apps) {
        authorization.currentActor();
        return accessibleApplicationIds(authorization.currentActor(), apps);
    }

    @Transactional(readOnly = true)
    public int interviewRoundNumber(String applicationId, String stepId, int fallback) {
        var step = requireStep(applicationId, stepId);
        return step.getRoundNo() == null ? fallback : step.getRoundNo();
    }

    /**
     * 只选择此刻需要执行的动作；模板定义表示能力，不预定后续面试轮数。
     */
    @Transactional
    public ApplicationDetailsResponse nextActionResponse(String id, String stepCode, String reason, long version) {
        return nextActionResponse(id, stepCode, reason, version, null);
    }

    @Transactional
    public ApplicationDetailsResponse nextActionResponse(String id, String stepCode, String reason, long version, Integer roundNo) {
        lockCandidateForApplication(id);
        // hire 同事务内可能已有旧读视图；复用当前协作人，保持查看、终止、处理权限的检查顺序。
        Application app = persistence.lockApplication(id);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        List<String> collaboratorIds = applicationCollaboratorPersistenceService.findCollaboratorsForUpdate(app.getId());
        authorize(app, collaboratorIds);
        requireOpen(app);
        requireProcessingAllowed(app.getOwnerUserId(), Set.copyOf(collaboratorIds));
        requireVersion(app, version);
        var code = workflowStepCode(stepCode);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        if (code == WorkflowStepCode.CLIENT_SUBMISSION) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "客户推荐功能已停用，请选择沟通、面试或人工决定");
        }
        if (code == WorkflowStepCode.OFFER) {
            throw PlatformExceptions.unprocessable("DECISION_REQUIRED", "Offer 必须通过人工招聘决定进入");
        }
        if (ApplicationStage.OFFER.matches(app.getCurrentStage())
                || (ApplicationStage.DECISION.matches(app.getCurrentStage()) && app.isProgressiveFollowUp())) {
            throw PlatformExceptions.conflict("APPLICATION_PATH_DECIDED", "当前已进入人工决定或 Offer，请完成对应操作");
        }
        var steps = recruitmentWorkflowStepPersistenceService.lockSteps(id);
        var activeSteps = steps.stream().filter(step -> ApplicationStepStatus.isActive(step.getStatus())).toList();
        int selectedRound = 0;
        boolean appendingScheduledInterview = false;
        if (code.getCode().startsWith("INTERVIEW_ROUND_")) {
            List<Interview> arranged = interviewPersistenceService.lockArrangedApplicationInterviews(id);
            int highestRound = arranged.stream().mapToInt(Interview::getRoundNo).max().orElse(0);
            boolean firstArranged = arranged.stream().anyMatch(interview -> interview.getRoundNo() == 1);
            selectedRound = roundNo == null ? (firstArranged ? highestRound + 1 : 1) : roundNo;
            if (selectedRound < 1 || (!firstArranged && selectedRound != 1)
                    || (firstArranged && selectedRound <= highestRound)) {
                throw PlatformExceptions.unprocessable("INTERVIEW_ROUND_INVALID", "必须先安排一面，后续只能选择更高轮次");
            }
            Set<String> scheduledStepIds = arranged.stream().map(Interview::getWorkflowStepId).collect(Collectors.toSet());
            appendingScheduledInterview = !activeSteps.isEmpty() && activeSteps.stream().allMatch(step ->
                    workflowStepCode(step.getStepCode()).isInterviewStep() && scheduledStepIds.contains(step.getId()));
        } else if (roundNo != null) {
            throw PlatformExceptions.unprocessable("INTERVIEW_ROUND_INVALID", "仅面试轮次支持指定轮数");
        }
        boolean replacingDuplicateSubmission = activeSteps.size() == 1
                && WorkflowStepCode.CLIENT_SUBMISSION.matches(activeSteps.getFirst().getStepCode());
        boolean replacingAutomaticDecision = !app.isProgressiveFollowUp()
                && ApplicationStage.DECISION.matches(app.getCurrentStage())
                && activeSteps.size() == 1 && WorkflowStepCode.DECISION.matches(activeSteps.getFirst().getStepCode())
                && decisionPersistenceService.findDecisions(id).isEmpty()
                && offerStatusRecordPersistenceService.findOffers(id).isEmpty();
        if (!activeSteps.isEmpty() && !replacingAutomaticDecision && !replacingDuplicateSubmission && !appendingScheduledInterview) {
            throw PlatformExceptions.conflict("APPLICATION_ACTION_ACTIVE", "请先记录结果并完成当前动作，再选择下一步");
        }
        if (ApplicationStage.DECISION.matches(app.getCurrentStage()) && !replacingAutomaticDecision) {
            throw PlatformExceptions.conflict("APPLICATION_PATH_DECIDED", "已存在人工决定事实，不能重新选择下一步");
        }
        var definition = steps.stream().filter(step -> code.matches(step.getStepCode())).findFirst().orElse(null);
        if (RecruitmentType.INTERNAL.matches(app.getRecruitmentType())
                && (code == WorkflowStepCode.CLIENT_SUBMISSION || code == WorkflowStepCode.CLIENT_AI_INTERVIEW
                || code == WorkflowStepCode.PRE_SUBMISSION_INTERNAL_INTERVIEW)) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "内部招聘不支持甲方动作");
        }
        String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), "HR 选择下一步");
        Instant now = clock.instant();
        String actor = authorization.currentActor().getUserId();
        // 旧路径自动决定或停用的客户推荐必须追加撤回事实，不能抹去已开始的历史。
        if (replacingAutomaticDecision || replacingDuplicateSubmission) {
            var replacedStep = activeSteps.getFirst();
            if (recruitmentWorkflowStepPersistenceService.completeStep(replacedStep.getId(),
                    ApplicationStepStatus.WITHDRAWN.getCode(), clean, replacedStep.getClientSubmissionId(),
                    replacedStep.getVersion(), actor, now) != 1) {
                throw state();
            }
        }
        int sequence = steps.stream().mapToInt(WorkflowStep::getSequenceNo).max().orElse(0) + 1;
        WorkflowStep selected = steps.stream().filter(step -> code.matches(step.getStepCode())
                        && (ApplicationStepStatus.AVAILABLE.matches(step.getStatus()) || ApplicationStepStatus.NOT_SELECTED.matches(step.getStatus())))
                .findFirst().orElse(null);
        boolean isNew = selected == null;
        if (isNew) {
            selected = new WorkflowStep();
            selected.setId(ids.newId());
            selected.setApplicationId(id);
            selected.setTemplateVersionId(app.getWorkflowTemplateVersionId());
            selected.setStepDefinitionId(definition == null ? null : definition.getStepDefinitionId());
            selected.setStepCode(code.getCode());
            selected.setCandidateOptional(true);
            selected.setDefaultFollowUpChannel(definition == null ? null : definition.getDefaultFollowUpChannel());
            selected.setCreateId(actor);
            selected.setCreateTime(now);
        }
        selected.setSequenceNo(sequence);
        selected.setStatus(ApplicationStepStatus.IN_PROGRESS.getCode());
        selected.setStartedAt(now);
        selected.setOwnerUserId(app.getOwnerUserId());
        selected.setDueAt(null);
        selected.setUpdateId(actor);
        selected.setUpdateTime(now);
        if (code.isInterviewStep()) {
            int round = (int) steps.stream().filter(step -> (code.getCode().startsWith("INTERVIEW_ROUND_")
                    ? step.getStepCode().startsWith("INTERVIEW_ROUND_") : code.matches(step.getStepCode()))
                    && ApplicationStepStatus.isFinished(step.getStatus())).count() + 1;
            selected.setRoundNo(selectedRound > 0 ? selectedRound : round);
        }
        if (!(isNew ? recruitmentWorkflowStepPersistenceService.save(selected)
                : recruitmentWorkflowStepPersistenceService.updateById(selected))) {
            throw state();
        }
        if (code == WorkflowStepCode.DECISION) {
            // Offer 始终排在实际人工决定之后，不能沿用旧模板中早于动态轮次的位置。
            var offer = steps.stream().filter(step -> WorkflowStepCode.OFFER.matches(step.getStepCode())
                            && (ApplicationStepStatus.AVAILABLE.matches(step.getStatus()) || ApplicationStepStatus.NOT_SELECTED.matches(step.getStatus())))
                    .findFirst().orElse(null);
            if (offer != null) {
                offer.setSequenceNo(sequence + 1);
                offer.setUpdateId(actor);
                offer.setUpdateTime(now);
                if (!recruitmentWorkflowStepPersistenceService.updateById(offer)) {
                    throw state();
                }
            }
        }
        if (persistence.enableProgressiveStage(id, stageFor(code.getCode()), version, actor, now) != 1) {
            throw state();
        }
        requireSingleWrite(persistence.insertPathChange(activitySource(id, "PATH", ids.newId()), id, "NEXT_ACTION",
                JsonText.write(activeSteps.stream().map(WorkflowStep::getId).toList()),
                JsonText.write(List.of(selected.getId())), clean, version, actor, now), "下一步选择历史写入失败");
        requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(id, "STAGE", ids.newId()), id,
                app.getCurrentStage(), stageFor(code.getCode()), clean, actor, now), "下一步阶段历史写入失败");
        return applicationDetailsView(details(persistence.findApplication(id), collaboratorIds));
    }

    /**
     * ID 列表。
     */
    @Resource
    private PlatformIdGenerator ids;
    /**
     * 时钟。
     */
    @Resource
    private Clock clock;

    /**
     * 跟进归属锁定列表。
     */
    @Resource
    private EngagementLockApplicationManager engagementLocks;

    /**
     * 持久化。
     */
    @Resource
    private ApplicationPersistenceService persistence;
    @Resource
    private ApplicationCollaboratorPersistenceService applicationCollaboratorPersistenceService;
    @Resource
    private CommunicationPersistenceService communicationPersistenceService;
    @Resource
    private DecisionPersistenceService decisionPersistenceService;
    @Resource
    private FollowUpPersistenceService followUpPersistenceService;
    @Resource
    private IntentionPersistenceService intentionPersistenceService;
    @Resource
    private OfferStatusRecordPersistenceService offerStatusRecordPersistenceService;
    @Resource
    private OnboardingEventPersistenceService onboardingEventPersistenceService;
    @Resource
    private OnboardingProcessPersistenceService onboardingProcessPersistenceService;
    @Resource
    private RecruitmentIdempotencyPersistenceService recruitmentIdempotencyPersistenceService;
    @Resource
    private RecruitmentOwnershipHistoryPersistenceService recruitmentOwnershipHistoryPersistenceService;
    @Resource
    private FollowUpPlanLifecycleManager followUpPlanLifecycle;
    @Resource
    private RecruitmentWorkflowStepPersistenceService recruitmentWorkflowStepPersistenceService;
    @Resource
    private InterviewPersistenceService interviewPersistenceService;
    @Resource
    private InterviewNotificationApplicationManager interviewNotificationManager;
    @Resource
    private StageHistoryPersistenceService stageHistoryPersistenceService;
    /**
     * 授权。
     */
    @Resource
    private AuthorizationService authorization;
    @Resource private CandidateLibraryQueryService candidateLibrary;
    /**
     * 目录。
     */
    @Resource
    private IdentityDirectoryService directory;
    /**
     * 候选人列表。
     */
    @Resource
    private CandidateDirectoryService candidates;
    @Resource
    private CompanyBlacklistQueryService blacklist;
    @Resource
    private CandidateOnboardingReturnService candidateOnboardingReturns;
    @Resource
    private CandidateAccessPolicy candidateAccess;
    @Resource
    private ApplicationCandidateFilter applicationCandidateFilter;
    /**
     * 简历列表。
     */
    @Resource
    private CandidateResumeVersionService resumes;
    @Resource private ResumeLifecycleService lifecycle;
    @Resource private CandidateScreeningTransitionService screeningTransitions;
    /**
     * 招聘需求列表。
     */
    @Resource
    private DemandQueryService demands;
    /**
     * 匹配方案列表。
     */
    @Resource
    private ActiveMatchPlanQueryService matchPlans;
    /**
     * 黑名单。
     */
    /**
     * 客户推荐校验器。
     */
    @Resource
    private ClientSubmissionValidationAdapter submissionValidator;
    @Resource
    private CursorCodec cursorCodec;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationDetails create(CreateApplicationDTO command, String rawIdempotencyKey) {
        return create(command, rawIdempotencyKey, null);
    }

    private ApplicationDetails create(CreateApplicationDTO command, String rawIdempotencyKey, List<WorkflowStepCode> selectedCodes) {
        authorization.requirePermission("recruitment.manage");
        String candidateId = require(command.getCandidateId(), "candidateId"),
                jdVersionId = require(command.getJdVersionId(), "jdVersionId"),
                resumeVersionId = require(command.getResumeVersionId(), "resumeVersionId");
        var actor = authorization.currentActor();
        String applicationId = ids.newId();
        blacklist.lockCandidateForCommand(candidateId);
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "APPLICATION_CREATE",
                        selectedCodes == null ? fingerprint(
                                candidateId,
                                jdVersionId,
                                resumeVersionId,
                                CommonUtils.stripToNull(command.getOwnerUserId()),
                                command.getReason()) : fingerprint(candidateId, jdVersionId, resumeVersionId,
                                CommonUtils.stripToNull(command.getOwnerUserId()), command.getReason(),
                                selectedCodes.stream().map(WorkflowStepCode::getCode).sorted().collect(Collectors.joining(","))),
                        applicationId,
                        clock.instant());
        if (replay != null) {
            return details(requireAuthorized(replay));
        }
        candidateAccess.requireResumeReadable(candidateId, resumeVersionId);
        resumes.lockForRouting(candidateId, resumeVersionId);
        blacklist.requireRecruitmentAllowed(candidateId);
        candidates.requireActive(candidateId);
        resumes.requireAvailable(candidateId, resumeVersionId);
        resumes.requireCurrentScore(candidateId, resumeVersionId, jdVersionId, null);
        JdVersionSnapshot jd = demands.requireJdVersionForNewUse(jdVersionId);
        authorizeDemand(jd);
        requireAvailableHeadcount(jd);
        if (RecruitmentType.EXTERNAL.matches(jd.getRecruitmentType()) && jd.getClientId() == null) {
            throw PlatformExceptions.unprocessable("EXTERNAL_CLIENT_REQUIRED", "外部招聘必须先在 JD 中指定真实甲方公司");
        }
        if (jd.isDeleted()) {
            throw PlatformExceptions.unprocessable("JD_DELETED", "该 JD 已删除，请选择其他 JD");
        }
        // 当前简历归属人可以将自己的简历推荐到已授权查看的岗位；后续流程仍按流程归属处理。
        if (!processingAllowed(jd.getOwnerUserId(), jd.getCollaboratorUserIds())
                && !candidates.ownedCandidateIds(List.of(candidateId), actor.getUserId()).contains(candidateId)) {
            requireProcessingAllowed(jd.getOwnerUserId(), jd.getCollaboratorUserIds());
        }
        if (persistence.findActiveForCandidateAndJd(candidateId, jd.getJdId()) != null) {
            throw PlatformExceptions.conflict("ACTIVE_APPLICATION_EXISTS", "该候选人在此 JD 已存在有效招聘活动");
        }
        ActiveWorkflowSnapshot workflow = demands.requireActiveWorkflow(jdVersionId);
        var matchPlan = matchPlans.requireActive(jdVersionId);
        var owner =
                command.getOwnerUserId() == null || command.getOwnerUserId().isBlank()
                        ? directory.activeUser(actor.getUserId())
                        : directory.activeUser(command.getOwnerUserId());
        if (!actor.hasCompanyWideScope()
                && !owner.userId().equals(actor.getUserId())
                && !actor.getManagedUserIds().contains(owner.userId())) {
            throw PlatformExceptions.forbidden("APPLICATION_ASSIGN_FORBIDDEN", "只能分配给本人或管理范围内成员");
        }
        Instant now = clock.instant();
        try {
            requireSingleWrite(
                    persistence.insertApplication(
                            applicationId,
                            candidateId,
                            jd.getDemandId(),
                            jd.getJdId(),
                            jdVersionId,
                            resumeVersionId,
                            workflow.getTemplateVersionId(),
                            matchPlan.matchPlanVersionId(),
                            workflow.getRecruitmentType(),
                            owner.userId(),
                            owner.homeDepartmentId(),
                            actor.getUserId(),
                            now),
                    "Application 主记录写入失败");
        } catch (DuplicateKeyException ex) {
            throw PlatformExceptions.conflict("ACTIVE_APPLICATION_EXISTS", "该候选人在此 JD 已存在有效招聘活动");
        }
        screeningTransitions.complete(candidateId, resumeVersionId, actor.getUserId(), now);
        lifecycle.routed(candidateId,resumeVersionId,"RECRUITMENT_CREATED");
        insertApplicationSteps(applicationId, workflow, now);
        requireSingleWrite(
                stageHistoryPersistenceService.insertStageHistory(
                activitySource(applicationId, "STAGE", ids.newId()),
                        applicationId,
                        null,
                        ApplicationStage.PATH_SELECTION.getCode(),
                        "基于已激活流程与正式评分版本创建",
                        actor.getUserId(),
                        now),
                "Application 阶段历史写入失败");
        requireSingleWrite(
                recruitmentOwnershipHistoryPersistenceService.insertOwnership(
                        ids.newId(),
                        applicationId,
                        "ASSIGN",
                        null,
                        owner.userId(),
                        required(command.getReason(), "reason", 500),
                        actor.getUserId(),
                        now),
                "Application 负责人历史写入失败");
        return details(persistence.findApplication(applicationId));
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(
            String jdId,
            String jdVersionId,
            String stage,
            String workflowStepCode,
            String requestedOwnerUserId,
            String cursor,
            Integer requestedLimit) {
        return list(jdId, jdVersionId, stage, workflowStepCode, requestedOwnerUserId, cursor, requestedLimit, null, null);
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(String jdId, String jdVersionId, String stage, String workflowStepCode,
            String requestedOwnerUserId, String cursor, Integer requestedLimit, String recruitmentType, String clientId) {
        return list(jdId, jdVersionId, stage, workflowStepCode, requestedOwnerUserId, cursor, requestedLimit,
                recruitmentType, clientId, "ALL");
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(String jdId, String jdVersionId, String stage, String workflowStepCode,
            String requestedOwnerUserId, String cursor, Integer requestedLimit, String recruitmentType, String clientId,
            String lifecycle) {
        return list(jdId, jdVersionId, stage, workflowStepCode, requestedOwnerUserId, cursor, requestedLimit,
                recruitmentType, clientId, lifecycle, null);
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(String jdId, String jdVersionId, String stage, String workflowStepCode,
            String requestedOwnerUserId, String cursor, Integer requestedLimit, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter) {
        return list(jdId, jdVersionId, stage, workflowStepCode, requestedOwnerUserId, cursor, requestedLimit,
                recruitmentType, clientId, lifecycle, candidateFilter, null, null, null);
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(String jdId, String jdVersionId, String stage,
            String workflowStepCode, String requestedOwnerUserId, String cursor, Integer requestedLimit, String recruitmentType, String clientId,
            String lifecycle, ApplicationCandidateFilterDTO candidateFilter, Instant from, Instant to, String interviewType) {
        return list(jdId, jdVersionId, stage, workflowStepCode, requestedOwnerUserId, cursor, requestedLimit,
                recruitmentType, clientId, lifecycle, candidateFilter, from, to, interviewType, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<Application> list(String jdId, String jdVersionId, String stage,
            String workflowStepCode, String requestedOwnerUserId, String cursor, Integer requestedLimit,
            String recruitmentType, String clientId, String lifecycle,
            ApplicationCandidateFilterDTO candidateFilter, Instant from, Instant to, String interviewType,
            boolean onboarding) {
        authorization.requirePermission("recruitment.manage");
        if (from != null && to != null && !from.isBefore(to)) {
            throw PlatformExceptions.unprocessable("APPLICATION_TIME_RANGE_INVALID", "开始时间必须早于结束时间");
        }
        if (interviewType != null && !ApplicationInterviewType.isValid(interviewType)) {
            throw PlatformExceptions.unprocessable("INTERVIEW_TYPE_INVALID", "面试类型无效");
        }
        boolean filterCandidates = candidateFilter != null && candidateFilter.isActive();
        if (filterCandidates && !ApplicationStage.ONBOARDED.matches(stage) && !onboarding) {
            throw PlatformExceptions.unprocessable("APPLICATION_CANDIDATE_FILTER_SCOPE_INVALID", "候选人条件仅适用于已入职记录");
        }
        String normalizedLifecycle = CommonUtils.stripToNull(lifecycle);
        if (normalizedLifecycle == null) {
            normalizedLifecycle = "ALL";
        }
        if (!ApplicationLifecycle.isValid(normalizedLifecycle)) {
            throw PlatformExceptions.unprocessable("APPLICATION_LIFECYCLE_INVALID", "应聘记录范围筛选值无效");
        }
        final String effectiveLifecycle = normalizedLifecycle;
        if (recruitmentType != null && !RecruitmentType.supports(recruitmentType)) {
            throw PlatformExceptions.unprocessable("RECRUITMENT_TYPE_INVALID", "招聘类型无效");
        }
        int limit = PageLimits.resolve(requestedLimit);
        var actor = authorization.currentActor();
        String normalizedJdId = CommonUtils.stripToNull(jdId);
        String normalizedJdVersionId = CommonUtils.stripToNull(jdVersionId);
        String normalizedStage = CommonUtils.stripToNull(stage);
        if (normalizedStage != null && !ApplicationStage.isValid(normalizedStage)) {
            throw PlatformExceptions.unprocessable("APPLICATION_STAGE_INVALID", "Application 阶段筛选值无效");
        }
        if (onboarding && !ApplicationStage.ONBOARDED.matches(normalizedStage)) {
            throw PlatformExceptions.unprocessable(
                    "ONBOARDING_FILTER_SCOPE_INVALID", "入职流程范围必须与 ONBOARDED 阶段筛选共同使用");
        }
        String normalizedStep = CommonUtils.stripToNull(workflowStepCode);
        String requestedOwner = CommonUtils.stripToNull(requestedOwnerUserId);
        boolean leadershipScope = ApplicationStage.ONBOARDED.matches(normalizedStage) || onboarding;
        Set<String> visibleManagedUserIds = leadershipScope
                ? actor.getManagedUserIds() : actor.getOperationalManagedUserIds();
        CurrentActorSnapshot visibilityActor = leadershipScope ? actor : actor.operationalScope();
        String owner;
        if (actor.hasCompanyWideScope()) {
            owner = requestedOwner;
        } else if (leadershipScope && actor.getRoles().contains("RECRUITMENT_MANAGER")) {
            if (requestedOwner != null
                    && !requestedOwner.equals(actor.getUserId())
                    && !visibleManagedUserIds.contains(requestedOwner)) {
                throw PlatformExceptions.forbidden("APPLICATION_FILTER_FORBIDDEN", "只能筛选本人或管理范围内成员");
            }
            owner = requestedOwner;
        } else {
            if (requestedOwner != null && !requestedOwner.equals(actor.getUserId())) {
                throw PlatformExceptions.forbidden("APPLICATION_FILTER_FORBIDDEN", "日常招聘流程只能筛选本人负责的数据");
            }
            owner = actor.getUserId();
        }
        String scope = CursorScopes.scope(
                "recruitment.applications",
                actor.getUserId(),
                actor.hasCompanyWideScope(),
                visibleManagedUserIds,
                actor.visibleDepartmentIds(),
                actor.getPermissions(),
                normalizedJdId,
                normalizedJdVersionId,
                normalizedStage,
                normalizedStep,
                owner, recruitmentType, clientId, effectiveLifecycle, filterCandidates ? candidateFilter : null,
                from, to, interviewType, onboarding);
        StableCursor c = StableCursor.decode(cursorCodec, scope, cursor);
        return VisibleCursorPageCollector.collect(
                c,
                limit,
                200,
                cursorCodec,
                scope,
                scan -> {
                    if (onboarding) {
                        return persistence.findApplications(
                                normalizedJdId,
                                normalizedJdVersionId,
                                owner,
                                normalizedStage,
                                normalizedStep,
                                effectiveLifecycle,
                                from, to, interviewType,
                                onboarding,
                                scan == null ? null : scan.getSortTime(),
                                scan == null ? null : scan.getId(),
                                200);
                    }
                    return persistence.findApplications(
                            normalizedJdId,
                            normalizedJdVersionId,
                            owner,
                            normalizedStage,
                            normalizedStep,
                            effectiveLifecycle,
                            from,
                            to,
                            interviewType,
                            scan == null ? null : scan.getSortTime(),
                            scan == null ? null : scan.getId(),
                            200);
                },
                applications -> {
                    Set<String> visible = accessibleApplicationIds(visibilityActor, applications);
                    var visibleApplications = applications.stream().filter(app -> visible.contains(app.getId())).toList();
                    Set<String> matching = filterCandidates
                            ? applicationCandidateFilter.matchingIds(visibleApplications, candidateFilter) : visible;
                    var filtered = visibleApplications.stream().filter(app -> matching.contains(app.getId()))
                            .filter(app -> recruitmentType == null || recruitmentType.equals(app.getRecruitmentType())).toList();
                    var clients = clientId == null ? Map.<String, String>of()
                            : demands.clientIdsForVersions(filtered.stream().map(Application::getJdVersionId).distinct().toList());
                    return filtered.stream()
                            .filter(app -> clientId == null || clientId.equals(clients.get(app.getJdVersionId())))
                            .map(Application::getId).collect(Collectors.toSet());
                },
                Application::getUpdateTime,
                Application::getId);
    }

    @Transactional(readOnly = true)
    public ApplicationDetails get(String applicationId) {
        Application app = persistence.findApplication(applicationId);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        return details(app);
    }

    @Transactional(readOnly = true)
    public boolean canProcessJdVersion(String jdVersionId) {
        authorization.requirePermission("recruitment.manage");
        JdVersionSnapshot jd = demands.requireJdVersion(require(jdVersionId, "jdVersionId"));
        authorizeDemand(jd);
        return !jd.isDeleted() && processingAllowed(jd.getOwnerUserId(), jd.getCollaboratorUserIds());
    }

    /**
     * 更新协作人列表。
     */
    @Transactional
    public ApplicationDetails updateCollaborators(
            String applicationId, List<String> collaboratorIds, long version, String reason) {
        Application app = requireLockedAuthorized(applicationId);
        requireVersion(app, version);
        String clean = required(reason, "reason", 500);
        var actor = authorization.currentActor();
        List<String> unique = normalizeCollaboratorIds(collaboratorIds);
        directory.activeUsers(Set.copyOf(unique));
        List<String> before = applicationCollaboratorPersistenceService.findCollaborators(app.getId());
        applicationCollaboratorPersistenceService.deleteCollaborators(app.getId());
        Instant now = clock.instant();
        if (!applicationCollaboratorPersistenceService.insertCollaborators(
                app.getId(), unique, actor.getUserId(), now)) {
            throw new IllegalStateException("应聘流程协作人批量写入失败");
        }
        if (persistence.updateStage(
                app.getId(), app.getCurrentStage(), version, actor.getUserId(), now)
                == 0) {
            throw state();
        }
        requireSingleWrite(
                persistence.insertCollaboratorChange(
                        ids.newId(),
                        app.getId(),
                        JsonText.write(before),
                        JsonText.write(unique),
                        clean,
                        version,
                        actor.getUserId(),
                        now),
                "协作人变更记录写入失败");
        return details(persistence.findApplication(app.getId()));
    }

    @Transactional
    public ApplicationDetails selectPath(
            String applicationId,
            List<String> requestedIds,
            String reason,
            long version,
            boolean amendment) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        if (app.isProgressiveFollowUp()) {
            throw PlatformExceptions.conflict("PROGRESSIVE_FOLLOW_UP_REQUIRED", "请只选择当前需要执行的下一步");
        }
        requireVersion(app, version);
        List<WorkflowStep> steps = recruitmentWorkflowStepPersistenceService.findSteps(app.getId());
        boolean selectedBefore =
                steps.stream().anyMatch(s -> !ApplicationStepStatus.AVAILABLE.matches(s.getStatus()));
        if (amendment && !selectedBefore) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_NOT_SELECTED", "尚未完成首次路径选择");
        }
        if (!amendment && selectedBefore) {
            throw PlatformExceptions.conflict("APPLICATION_PATH_ALREADY_SELECTED", "实际路径已选择，请使用路径修改");
        }
        List<WorkflowStep> mutableSteps = mutablePathSteps(app, steps);
        if (requestedIds != null && requestedIds.size() > MAX_WORKFLOW_STEP_COUNT) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "选择步骤数量超出流程上限");
        }
        Set<String> known =
                steps.stream().map(WorkflowStep::getId).collect(Collectors.toSet());
        Set<String> requested = requestedIds == null ? Set.of() : new LinkedHashSet<>(requestedIds);
        if (!known.containsAll(requested)) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_STEP_INVALID", "选择包含不属于该 Application 的步骤");
        }
        LinkedHashSet<String> chosen = new LinkedHashSet<>();
        steps.stream().filter(s -> ApplicationStepStatus.isFinished(s.getStatus()))
                .map(WorkflowStep::getId).forEach(chosen::add);
        Set<String> mutableIds = mutableSteps.stream().map(WorkflowStep::getId)
                .collect(Collectors.toSet());
        if (requested.stream().anyMatch(id -> !chosen.contains(id) && !mutableIds.contains(id))) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_ORDER_INVALID", "不能在已完成步骤之前补选未执行步骤");
        }
        for (var step : mutableSteps) {
            if (!WorkflowStepCode.CLIENT_SUBMISSION.matches(step.getStepCode())
                    && (!step.isCandidateOptional() || requested.contains(step.getId()))) {
                chosen.add(step.getId());
            }
        }
        List<WorkflowStep> selected = mutableSteps.stream().filter(s -> chosen.contains(s.getId())).toList();
        if (selected.isEmpty()) {
            throw PlatformExceptions.unprocessable("APPLICATION_PATH_EMPTY", "实际路径不能为空");
        }
        String clean = required(reason, "reason", 500);
        var actor = authorization.currentActor();
        Instant now = clock.instant();
        Set<String> before =
                steps.stream()
                        .filter(s -> selectedBefore && !ApplicationStepStatus.NOT_SELECTED.matches(s.getStatus()))
                        .map(WorkflowStep::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> selectedIds = selected.stream().map(WorkflowStep::getId).toList();
        List<String> unselectedIds = mutableSteps.stream().filter(s -> !chosen.contains(s.getId()))
                .map(WorkflowStep::getId).toList();
        // 只分组更新未开始的后续步骤；已完成/撤回事实不参加写入。
        if (recruitmentWorkflowStepPersistenceService.updateUnstartedSelection(
                app.getId(), selectedIds, true, actor.getUserId(), now) != selectedIds.size()
                || recruitmentWorkflowStepPersistenceService.updateUnstartedSelection(
                app.getId(), unselectedIds, false, actor.getUserId(), now) != unselectedIds.size()) {
            throw state();
        }
        WorkflowStep first = selected.getFirst();
        if (recruitmentWorkflowStepPersistenceService.selectStep(first.getId(),
                ApplicationStepStatus.IN_PROGRESS.getCode(), app.getOwnerUserId(),
                dueAt(first.getStepCode(), now), actor.getUserId(), now) != 1) {
            throw state();
        }
        String nextStage = stageFor(first.getStepCode());
        if (persistence.updateStage(
                app.getId(),
                nextStage,
                version,
                actor.getUserId(),
                now)
                == 0) {
            throw state();
        }
        requireSingleWrite(
                persistence.insertPathChange(
                        activitySource(app.getId(), "PATH", ids.newId()),
                        app.getId(),
                        amendment ? "AMENDMENT" : "SELECTION",
                        JsonText.write(before),
                        JsonText.write(chosen),
                        clean,
                        version,
                        actor.getUserId(),
                        now),
                "招聘路径变更记录写入失败");
        requireSingleWrite(
                stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                        app.getId(),
                        app.getCurrentStage(),
                        nextStage,
                        clean,
                        actor.getUserId(),
                        now),
                "Application 阶段历史写入失败");
        return details(persistence.findApplication(app.getId()));
    }

    /**
     * 模板顺序中已完成的事实不可回退；只能选择其后的未开始步骤。
     */
    private static List<WorkflowStep> mutablePathSteps(Application app, List<WorkflowStep> steps) {
        if (ApplicationStage.OFFER.matches(app.getCurrentStage()) || steps.stream().anyMatch(s ->
                ApplicationStepStatus.isFinished(s.getStatus())
                        && (WorkflowStepCode.DECISION.matches(s.getStepCode())
                        || WorkflowStepCode.OFFER.matches(s.getStepCode())))) {
            throw PlatformExceptions.conflict("APPLICATION_PATH_DECIDED", "已作出决定，请通过决定更正或 Offer 专用操作处理");
        }
        if (steps.stream().anyMatch(s -> ApplicationStepStatus.IN_PROGRESS.matches(s.getStatus())
                || (s.getStartedAt() != null && !ApplicationStepStatus.isFinished(s.getStatus())))) {
            throw PlatformExceptions.conflict("APPLICATION_PATH_ALREADY_STARTED", "当前步骤仍在执行，请先记录跟进并完成或撤回该步骤");
        }
        int lastFinishedSequence = steps.stream().filter(s -> ApplicationStepStatus.isFinished(s.getStatus()))
                .mapToInt(WorkflowStep::getSequenceNo).max().orElse(0);
        return steps.stream().filter(s -> s.getSequenceNo() > lastFinishedSequence)
                .filter(s -> !ApplicationStepStatus.isFinished(s.getStatus())).toList();
    }

    private static List<String> normalizeCollaboratorIds(List<String> collaboratorIds) {
        if (collaboratorIds == null || collaboratorIds.isEmpty()) {
            return List.of();
        }
        if (collaboratorIds.size() > MAX_COLLABORATORS_PER_APPLICATION) {
            throw PlatformExceptions.unprocessable("COLLABORATOR_LIMIT", "协作人数量超过限制");
        }
        return collaboratorIds.stream()
                .map(userId -> require(userId, "collaboratorUserId"))
                .distinct()
                .toList();
    }

    private void insertApplicationSteps(
            String applicationId, ActiveWorkflowSnapshot workflow, Instant now) {
        List<WorkflowStep> steps = new ArrayList<>(workflow.getSteps().size());
        int sequenceNo = 0;
        for (ActiveWorkflowSnapshot.Step definition : workflow.getSteps()) {
            if (WorkflowStepCode.CLIENT_SUBMISSION.matches(definition.stepCode())) {
                continue;
            }
            WorkflowStep step = new WorkflowStep();
            step.setId(ids.newId());
            step.setApplicationId(applicationId);
            step.setTemplateVersionId(workflow.getTemplateVersionId());
            step.setStepDefinitionId(definition.stepDefinitionId());
            step.setStepCode(definition.stepCode());
            step.setSequenceNo(++sequenceNo);
            step.setCandidateOptional(definition.candidateOptional());
            step.setDefaultFollowUpChannel(definition.defaultFollowUpChannel());
            step.setStatus(ApplicationStepStatus.AVAILABLE.getCode());
            step.setVersion(0L);
            step.setCreateTime(now);
            step.setUpdateTime(now);
            steps.add(step);
        }
        if (!recruitmentWorkflowStepPersistenceService.insertSteps(steps)) {
            throw new IllegalStateException("Application 流程步骤批量写入失败");
        }
    }

    private static void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 启动步骤。
     */
    @Transactional
    public WorkflowStep startStep(String applicationId, String stepId, long version) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        WorkflowStep step = requireStep(app.getId(), stepId);
        if (ApplicationStepStatus.IN_PROGRESS.matches(step.getStatus()) && step.getVersion() == version) {
            return step;
        }
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        if (recruitmentWorkflowStepPersistenceService.startStep(
                step.getId(), version, authorization.currentActor().getUserId(), clock.instant())
                == 0) {
            throw state();
        }
        return requireStep(app.getId(), step.getId());
    }

    @Transactional
    public FollowUp followUp(
            String applicationId,
            String stepId,
            String channel,
            String summary,
            String nextPlan,
            Instant contactedAt) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        WorkflowStep step = requireStep(app.getId(), stepId);
        if (!ApplicationStepStatus.isActive(step.getStatus())) {
            throw PlatformExceptions.conflict("WORKFLOW_STEP_NOT_ACTIVE", "只能给待处理或进行中的步骤添加跟进");
        }
        String cleanChannel = CommonUtils.upperOrEmpty(channel);
        if (!CommunicationChannel.supports(cleanChannel)) {
            throw PlatformExceptions.unprocessable("FOLLOW_UP_CHANNEL_INVALID", "跟进渠道不受支持");
        }
        Instant when = contactedAt == null ? clock.instant() : contactedAt;
        if (when.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("FOLLOW_UP_TIME_INVALID", "跟进时间不能晚于当前时间");
        }
        String id = ids.newId(),
                text = required(summary, "summary", 1000),
                plan = required(nextPlan, "nextPlan", 500);
        var actor = authorization.currentActor();
        requireSingleWrite(
                followUpPersistenceService.insertFollowUp(
                activitySource(app.getId(), "FOLLOW_UP", id),
                        app.getId(),
                        step.getId(),
                        cleanChannel,
                        text,
                        plan,
                        when,
                        actor.getUserId(),
                        clock.instant()),
                "招聘跟进记录写入失败");
        if (ApplicationStepStatus.PENDING.matches(step.getStatus())
                && recruitmentWorkflowStepPersistenceService.startStep(step.getId(), step.getVersion(), actor.getUserId(), clock.instant()) != 1) {
            throw state();
        }
        return followUpPersistenceService.findFollowUps(step.getId()).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 完成预约时只能同步 HR 明确确认且仍为当前的候选人沟通步骤。
     */
    @Transactional
    public void recordPlannedCommunication(String applicationId, String channel, String summary, String result,
            String nextPlan, String stepId, Long stepVersion) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        if ((stepId == null) != (stepVersion == null)) {
            throw PlatformExceptions.unprocessable("FOLLOW_UP_STEP_REFERENCE_INVALID", "沟通步骤及版本必须同时提供");
        }
        WorkflowStep step = stepId == null ? null : requireStep(app.getId(), stepId);
        if (step != null && (!WorkflowStepCode.CANDIDATE_COMMUNICATION.matches(step.getStepCode())
                || !ApplicationStepStatus.isActive(step.getStatus()) || step.getVersion() != stepVersion)) {
            throw PlatformExceptions.conflict("FOLLOW_UP_STEP_CHANGED", "关联的候选人沟通步骤已变化，请刷新后确认");
        }
        writeCommunicationFacts(app, step, channel, summary, result, nextPlan);
        if (step != null) {
            if (CommunicationResult.CONTINUE.matches(result)) {
                completeStepInternal(applicationId, stepId, ApplicationStepResult.PASSED.getCode(), summary, null,
                        stepVersion, channel, nextPlan, false);
            } else if (ApplicationStepStatus.PENDING.matches(step.getStatus())
                    && recruitmentWorkflowStepPersistenceService.startStep(stepId, stepVersion,
                    authorization.currentActor().getUserId(), clock.instant()) != 1) {
                throw state();
            }
        }
    }

    private void writeCommunicationFacts(Application app, WorkflowStep step, String channel, String summary,
            String result, String nextPlan) {
        String cleanChannel = CommonUtils.upperOrEmpty(channel);
        if (!CommunicationChannel.supports(cleanChannel)) {
            throw PlatformExceptions.unprocessable("COMMUNICATION_CHANNEL_INVALID", "沟通渠道不受支持");
        }
        if (!CommunicationResult.supports(result)) {
            throw PlatformExceptions.unprocessable("COMMUNICATION_RESULT_INVALID", "沟通结果不受支持");
        }
        String text = Objects.requireNonNullElse(optional(summary, "reason", 500), "未填写沟通备注");
        String plan = Objects.requireNonNullElse(optional(nextPlan, "nextPlan", 500), "待 HR 确定下一步");
        var actor = authorization.currentActor();
        Instant now = clock.instant();
        requireSingleWrite(communicationPersistenceService.insertCommunication(
                activitySource(app.getId(), "COMMUNICATION", ids.newId()), app.getId(), cleanChannel,
                text, result, plan, now, actor.getUserId(), now), "候选人沟通记录写入失败");
        if (step != null) {
            requireSingleWrite(followUpPersistenceService.insertFollowUp(
                activitySource(app.getId(), "FOLLOW_UP", ids.newId()), app.getId(), step.getId(),
                    cleanChannel, text, plan, now, actor.getUserId(), now), "招聘跟进记录写入失败");
        }
        if (CandidateReplyResult.supports(result)) {
            recordProgressIfLocked(app.getCandidateId(), "CANDIDATE_REPLY", "记录候选人有效回复");
        }
    }

    @Transactional
    public Communication communicate(
            String applicationId,
            String channel,
            String summary,
            String result,
            String nextPlan,
            Instant communicatedAt, String rawKey) {
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        Application app = requireLockedAuthorized(applicationId);
        requireProcessingAllowed(app);
        String cleanChannel = channel == null || channel.isBlank() ? CommunicationChannel.OTHER.getCode() : CommonUtils.upperOrEmpty(channel);
        if (!CommunicationChannel.supports(cleanChannel)) {
            throw PlatformExceptions.unprocessable("COMMUNICATION_CHANNEL_INVALID", "沟通渠道不受支持");
        }
        String cleanResult = CommonUtils.upperOrEmpty(result);
        if (!CommunicationResult.supports(cleanResult)) {
            throw PlatformExceptions.unprocessable("COMMUNICATION_RESULT_INVALID", "沟通结果不受支持");
        }
        String cleanSummary = Objects.requireNonNullElse(optional(summary, "summary", 1000), "未填写沟通备注");
        String cleanNextPlan = Objects.requireNonNullElse(optional(nextPlan, "nextPlan", 500), "待 HR 确定下一步");
        String actorId = authorization.currentActor().getUserId();
        String action = "COMMUNICATION_RECORD";
        String requestFingerprint = fingerprint(applicationId, cleanChannel, cleanSummary, cleanResult,
                cleanNextPlan, communicatedAt);
        String id = ids.newId();
        // 不锁不存在键的间隙；唯一键竞争失败后使用当前读取得赢家，避免 RR 旧快照。
        IdempotencyRecord replay = recruitmentIdempotencyPersistenceService.findIdempotency(actorId, key);
        if (replay == null && persistence.insertIdempotency(actorId, key, action, requestFingerprint, id, clock.instant()) != 1) {
            replay = recruitmentIdempotencyPersistenceService.findIdempotencyForUpdate(actorId, key);
            if (replay == null) throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "沟通记录正在保存，请重试");
        }
        if (replay != null) {
            if (!actorId.equals(replay.getActorUserId())) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "幂等记录操作人不匹配");
            }
            return requireCommunication(applicationId, verifyIdempotency(replay, action, requestFingerprint));
        }
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        requireOpen(app);
        requireProcessingAllowed(app);
        Instant when = communicatedAt == null ? clock.instant() : communicatedAt;
        if (when.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("COMMUNICATION_TIME_INVALID", "沟通时间不能晚于当前时间");
        }
        var actor = authorization.currentActor();
        requireSingleWrite(
                communicationPersistenceService.insertCommunication(
                activitySource(app.getId(), "COMMUNICATION", id),
                        app.getId(),
                        cleanChannel,
                        cleanSummary,
                        cleanResult,
                        cleanNextPlan,
                        when,
                        actor.getUserId(),
                        clock.instant()),
                "候选人沟通记录写入失败");
        if (CandidateReplyResult.supports(cleanResult)) {
            recordProgressIfLocked(app.getCandidateId(), "CANDIDATE_REPLY", "记录候选人有效回复");
        }
        return requireCommunication(applicationId, id);
    }

    private Communication requireCommunication(String applicationId, String communicationId) {
        Communication communication = communicationPersistenceService.findByIdForUpdate(communicationId);
        if (communication == null || !applicationId.equals(communication.getApplicationId())) {
            throw PlatformExceptions.conflict("COMMUNICATION_RESULT_MISSING", "原沟通记录不存在或与活动不一致，请核对");
        }
        return communication;
    }

    @Transactional(readOnly = true)
    public List<Communication> communications(String applicationId) {
        requireReadable(applicationId);
        return communicationPersistenceService.findCommunications(applicationId);
    }

    @Transactional
    public Intention addIntention(
            String applicationId,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryPeriod,
            String availability,
            List<String> locations,
            String reason) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw PlatformExceptions.unprocessable("INTENTION_SALARY_INVALID", "期望薪资下限不能高于上限");
        }
        String joined = normalizeIntentionLocations(locations);
        String id = ids.newId();
        var actor = authorization.currentActor();
        requireSingleWrite(
                intentionPersistenceService.insertIntention(
                        id,
                        app.getCandidateId(),
                        app.getId(),
                        salaryMin,
                        salaryMax,
                        CommonUtils.stripToNull(salaryPeriod),
                        CommonUtils.stripToNull(availability),
                        joined,
                        required(reason, "reason", 500),
                        actor.getUserId(),
                        clock.instant()),
                "候选人意向记录写入失败");
        return intentionPersistenceService.findIntentions(app.getId()).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static String normalizeIntentionLocations(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        if (locations.size() > MAX_LOCATIONS_PER_INTENTION) {
            throw PlatformExceptions.unprocessable("INTENTION_LOCATION_LIMIT", "意向地点数量超过限制");
        }
        List<String> normalized = new ArrayList<>(locations.size());
        for (String rawLocation : locations) {
            if (rawLocation == null) {
                throw PlatformExceptions.unprocessable("INTENTION_LOCATION_INVALID", "意向地点不能为空");
            }
            String location = rawLocation.strip();
            if (location.isEmpty() || location.length() > MAX_INTENTION_LOCATION_LENGTH) {
                throw PlatformExceptions.unprocessable("INTENTION_LOCATION_INVALID", "意向地点格式无效");
            }
            normalized.add(location);
        }
        String joined = String.join(" / ", normalized);
        if (joined.length() > MAX_INTENTION_LOCATIONS_STORAGE_LENGTH) {
            throw PlatformExceptions.unprocessable("INTENTION_LOCATION_INVALID", "意向地点总长度超过限制");
        }
        return joined;
    }

    @Transactional(readOnly = true)
    public List<Intention> intentions(String applicationId) {
        requireReadable(applicationId);
        return intentionPersistenceService.findIntentions(applicationId);
    }

    /**
     * 完成步骤。
     */
    @Transactional
    public ApplicationDetails completeStep(
            String applicationId,
            String stepId,
            String result,
            String reason,
            String clientSubmissionId,
            long version) {
        return completeStepInternal(applicationId, stepId, result, reason, clientSubmissionId, version, null, null, true);
    }

    private ApplicationDetails completeStepInternal(String applicationId, String stepId, String result, String reason,
            String clientSubmissionId, long version, String channel, String nextPlan, boolean syncCommunication) {
        Application app = requireLockedAuthorized(applicationId);
        requireOpen(app);
        requireProcessingAllowed(app);
        WorkflowStep step = requireStep(app.getId(), stepId);
        requireGenericStepCommand(step);
        String cleanResult = CommonUtils.upperOrEmpty(result);
        if (!ApplicationStepResult.supports(cleanResult)) {
            throw PlatformExceptions.unprocessable("WORKFLOW_STEP_RESULT_INVALID", "步骤结果必须为 PASSED、FAILED 或 WITHDRAWN");
        }
        // FAILED/WITHDRAWN 也会调用 advance 返回选路或激活下一步，并不终结招聘活动。
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        String submissionId = CommonUtils.stripToNull(clientSubmissionId);
        if (submissionId == null && RecruitmentType.EXTERNAL.matches(app.getRecruitmentType())
                && isClientStep(step.getStepCode())) {
            submissionId = step.getClientSubmissionId();
        }
        Interview scheduledInterview = workflowStepCode(step.getStepCode()).isInterviewStep()
                ? interviewPersistenceService.lockLatestByStep(step.getId()) : null;
        boolean labelOnlyInterview = scheduledInterview != null && (InterviewType.INTERNAL.matches(scheduledInterview.getInterviewType())
                || InterviewType.EXTERNAL.matches(scheduledInterview.getInterviewType()));
        // 内部/外部标签不绑定客户；历史甲方面试继续使用原推荐事实。
        if (!labelOnlyInterview && RecruitmentType.EXTERNAL.matches(app.getRecruitmentType()) && isClientStep(step.getStepCode())) {
            if (submissionId != null) {
                requireMatchingSubmission(app, submissionId);
            }
            if (step.getClientSubmissionId() != null
                    && !Objects.equals(step.getClientSubmissionId(), submissionId)) {
                throw PlatformExceptions.conflict("WORKFLOW_STEP_SUBMISSION_MISMATCH", "完成步骤必须使用已绑定的客户推荐，不能覆盖原关联");
            }
        } else if (submissionId != null) {
            throw PlatformExceptions.unprocessable("CLIENT_SUBMISSION_NOT_ALLOWED", "非甲方步骤不得绑定客户推荐");
        }
        WorkflowStepCode code = workflowStepCode(step.getStepCode());
        boolean internalInterview = labelOnlyInterview || code == WorkflowStepCode.PRE_SUBMISSION_INTERNAL_INTERVIEW
                || (RecruitmentType.INTERNAL.matches(app.getRecruitmentType()) && code.isInterviewStep());
        if (internalInterview && !ApplicationStepResult.WITHDRAWN.matches(cleanResult)) {
            Interview interview = scheduledInterview;
            if (interview == null || !InterviewStatus.COMPLETED.matches(interview.getStatus())) {
                throw PlatformExceptions.unprocessable("INTERNAL_INTERVIEW_FEEDBACK_REQUIRED",
                        "请先由招聘活动处理人提交本轮正式面评，再完成内部面试步骤；候选人退出可记录退出结果");
            }
        }
        String clean = Objects.requireNonNullElse(optional(reason, "reason", 500),
                code == WorkflowStepCode.CANDIDATE_COMMUNICATION ? "未填写沟通备注" : "HR 记录步骤结果");
        String actorId = authorization.currentActor().getUserId();
        Instant now = clock.instant();
        if (recruitmentWorkflowStepPersistenceService.completeStep(
                step.getId(), cleanResult, clean, submissionId, version, actorId, now)
                == 0) {
            throw state();
        }
        activitySource(app.getId(), "WORKFLOW_STEP", step.getId());
        advance(app, clean, now);
        if (syncCommunication && code == WorkflowStepCode.CANDIDATE_COMMUNICATION) {
            writeCommunicationFacts(app, step, channel == null || channel.isBlank() ? CommunicationChannel.OTHER.getCode() : channel,
                    clean, ApplicationStepResult.PASSED.matches(cleanResult) ? CommunicationResult.CONTINUE.getCode()
                            : CommunicationResult.DECLINED.getCode(), nextPlan);
        }
        recordProgressIfLocked(app.getCandidateId(), "APPLICATION_ADVANCED", clean);
        return details(persistence.findApplication(app.getId()));
    }

    @Transactional
    public ApplicationDetails skipStep(
            String applicationId, String stepId, String reason, long version) {
        Application app = requireLockedAuthorized(applicationId);
        if (!authorization.currentActor().isSuperAdmin()) {
            throw PlatformExceptions.forbidden("WORKFLOW_STEP_CORRECTION_REQUIRED", "SKIPPED 仅限超级管理员纠正错录");
        }
        // 纠错仍会激活后续步骤，管理员权限不能绕过终止状态和黑名单的推进约束。
        requireOpen(app);
        requireProcessingAllowed(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        WorkflowStep step = requireStep(app.getId(), stepId);
        requireGenericStepCommand(step);
        String clean = required(reason, "reason", 500);
        String actorId = authorization.currentActor().getUserId();
        Instant now = clock.instant();
        if (recruitmentWorkflowStepPersistenceService.skipStep(step.getId(), clean, version, actorId, now) == 0) {
            throw state();
        }
        advance(app, clean, now);
        return details(persistence.findApplication(app.getId()));
    }

    @Transactional
    public Decision decide(
            String applicationId,
            String decision,
            String reason,
            String clientSubmissionId,
            String rawIdempotencyKey) {
        Application app = requireLockedProcessingApplication(applicationId);
        String value = CommonUtils.upperOrEmpty(decision);
        String clean = required(reason, "reason", 500);
        var actor = authorization.currentActor();
        String recordId = ids.newId();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "APPLICATION_DECIDE",
                        fingerprint(app.getId(), value, clean, CommonUtils.stripToNull(clientSubmissionId)),
                        recordId,
                        clock.instant());
        if (replay != null) {
            Decision found = decisionPersistenceService.findDecision(app.getId(), replay);
            if (found == null) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原决定请求尚未完成，请稍后重试");
            }
            return found;
        }
        requireOpen(app);
        if (!ApplicationDecision.supports(value)) {
            throw PlatformExceptions.unprocessable("APPLICATION_DECISION_INVALID", "决定必须为 PASS、REJECT 或 WITHDRAW");
        }
        if (ApplicationDecision.PASS.matches(value)) {
            blacklist.requireRecruitmentAllowed(app.getCandidateId());
        }
        List<WorkflowStep> steps = recruitmentWorkflowStepPersistenceService.findSteps(app.getId());
        WorkflowStep step =
                steps.stream()
                        .filter(s -> WorkflowStepCode.DECISION.matches(s.getStepCode()))
                        .findFirst()
                        .orElseThrow(() -> PlatformExceptions.unprocessable("DECISION_STEP_MISSING", "流程中没有人工决定步骤"));
        boolean earlyTermination = ApplicationDecision.isNegative(value)
                && ApplicationStepStatus.AVAILABLE.matches(step.getStatus());
        if (!ApplicationStepStatus.isActive(step.getStatus()) && !earlyTermination) {
            throw PlatformExceptions.conflict("DECISION_STEP_NOT_ACTIVE", "人工决定步骤尚未到达或已经完成");
        }
        if (ApplicationStage.OFFER.matches(app.getCurrentStage())) {
            throw PlatformExceptions.conflict("OFFER_TERMINATION_REQUIRED", "Offer 阶段请通过 Offer 终止操作追加终止事实");
        }
        if (RecruitmentType.EXTERNAL.matches(app.getRecruitmentType())
                && ApplicationDecision.PASS.matches(value) && CommonUtils.stripToNull(clientSubmissionId) != null) {
            requireMatchingSubmission(app, clientSubmissionId);
        }
        Instant now = clock.instant();
        // 提前终止仍完成正式决定步骤，不能用普通步骤结果替代人工决定事实。
        if (earlyTermination && recruitmentWorkflowStepPersistenceService.selectStep(
                step.getId(), ApplicationStepStatus.IN_PROGRESS.getCode(), app.getOwnerUserId(),
                dueAt(WorkflowStepCode.DECISION.getCode(), now), actor.getUserId(), now) != 1) {
            throw state();
        }
        requireSingleWrite(decisionPersistenceService.insertDecision(
                activitySource(app.getId(), "DECISION", recordId),
                app.getId(),
                "DECISION",
                value,
                clean,
                null,
                CommonUtils.stripToNull(clientSubmissionId),
                actor.getUserId(),
                now), "人工决定记录写入失败");
        if (recruitmentWorkflowStepPersistenceService.finishDecisionStep(
                step.getId(),
                ApplicationDecision.PASS.matches(value)
                        ? ApplicationStepStatus.PASSED.name()
                        : ApplicationStepStatus.FAILED.name(),
                clean,
                CommonUtils.stripToNull(clientSubmissionId),
                actor.getUserId(),
                now) != 1) {
            throw state();
        }
        if (ApplicationDecision.isNegative(value)) {
            long activeSteps = steps.stream()
                    .filter(candidate -> !step.getId().equals(candidate.getId()))
                    .filter(candidate -> ApplicationStepStatus.isActive(candidate.getStatus()))
                    .count();
            // 根记录已锁定；批量收束当前待办，并校验受影响行数以拒绝不完整终止。
            if (recruitmentWorkflowStepPersistenceService.withdrawActiveSteps(
                    app.getId(), clean, actor.getUserId(), now) != activeSteps) {
                throw state();
            }
        }
        applyDecision(app, value, clean, actor.getUserId(), now);
        return decisionPersistenceService.findDecision(app.getId(), recordId);
    }

    /**
     * 更正人工决定。
     */
    @Transactional
    public Decision correctDecision(
            String applicationId,
            String decisionId,
            String decision,
            String reason,
            String clientSubmissionId,
            String rawIdempotencyKey) {
        Application app = requireLockedAuthorized(applicationId);
        requireProcessingAllowed(app);
        String value = CommonUtils.upperOrEmpty(decision);
        String clean = required(reason, "reason", 500);
        var actor = authorization.currentActor();
        String id = ids.newId();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "APPLICATION_DECISION_CORRECT",
                        fingerprint(app.getId(), decisionId, value, clean, CommonUtils.stripToNull(clientSubmissionId)),
                        id,
                        clock.instant());
        if (replay != null) {
            Decision found = decisionPersistenceService.findDecision(app.getId(), replay);
            if (found == null) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原决定更正请求尚未完成，请稍后重试");
            }
            return found;
        }
        requireOpen(app);
        OfferStatusRecord offer = offerStatusRecordPersistenceService.latestOffer(app.getId());
        if (ApplicationStage.OFFER.matches(app.getCurrentStage())
                && (offer == null || !OfferStatus.NOT_STARTED.matches(offer.getStatus()))) {
            throw PlatformExceptions.conflict(
                    "DECISION_CORRECTION_REOPEN_REQUIRED", "Offer 已开始，历史流程不可更正；请重新选择 JD 创建新流程");
        }
        if (!ApplicationStage.allowsDecisionCorrection(app.getCurrentStage())) {
            throw PlatformExceptions.conflict("DECISION_CORRECTION_REOPEN_REQUIRED", "当前阶段不能直接更正决定");
        }
        Decision prior = decisionPersistenceService.findDecision(app.getId(), decisionId);
        if (prior == null) {
            throw PlatformExceptions.notFound("DECISION_NOT_FOUND", "决定记录不存在");
        }
        if (!ApplicationDecision.supports(value)) {
            throw PlatformExceptions.unprocessable("APPLICATION_DECISION_INVALID", "更正决定无效");
        }
        if (ApplicationDecision.PASS.matches(value)) {
            blacklist.requireRecruitmentAllowed(app.getCandidateId());
        }
        if (RecruitmentType.EXTERNAL.matches(app.getRecruitmentType())
                && ApplicationDecision.PASS.matches(value) && CommonUtils.stripToNull(clientSubmissionId) != null) {
            requireMatchingSubmission(app, clientSubmissionId);
        }
        Instant now = clock.instant();
        // 更正事实必须先持久化；任何缺失写入都回滚幂等占位和当前状态变更。
        requireSingleWrite(decisionPersistenceService.insertDecision(
                activitySource(app.getId(), "DECISION", id),
                app.getId(),
                "CORRECTION",
                value,
                clean,
                prior.getId(),
                CommonUtils.stripToNull(clientSubmissionId),
                actor.getUserId(),
                now), "人工决定更正记录写入失败");
        finishActiveDecisionOnCorrection(app, value, clean,
                CommonUtils.stripToNull(clientSubmissionId), actor.getUserId(), now);
        applyDecisionCorrection(app, value, clean, actor.getUserId(), now);
        return decisionPersistenceService.findDecision(app.getId(), id);
    }

    @Transactional
    public ApplicationDetails transfer(
            String applicationId, String targetUserId, String reason, long version, String type) {
        Application app = requireLockedAuthorized(applicationId);
        requireVersion(app, version);
        var actor = authorization.currentActor();
        var target = directory.activeUser(targetUserId);
        if (!actor.hasCompanyWideScope()
                && !actor.getUserId().equals(app.getOwnerUserId())
                && !actor.getManagedUserIds().contains(app.getOwnerUserId())) {
            throw PlatformExceptions.forbidden("APPLICATION_TRANSFER_FORBIDDEN", "无权转交该招聘活动");
        }
        String clean = required(reason, "reason", 500);
        Instant now = clock.instant();
        if (persistence.updateOwner(
                app.getId(),
                target.userId(),
                target.homeDepartmentId(),
                version,
                actor.getUserId(),
                now)
                == 0) {
            throw state();
        }
        requireSingleWrite(recruitmentOwnershipHistoryPersistenceService.insertOwnership(
                ids.newId(),
                app.getId(),
                type,
                app.getOwnerUserId(),
                target.userId(),
                clean,
                actor.getUserId(),
                now), "Application 负责人历史写入失败");
        return details(persistence.findApplication(app.getId()));
    }

    @Transactional
    public OfferStatusRecord addOffer(
            String applicationId, OfferCommandDTO command, String rawIdempotencyKey) {
        Application app = requireHeadcountLockedApplication(applicationId);
        String status = CommonUtils.upperOrEmpty(command.getStatus());
        String reason = required(command.getReason(), "reason", 500);
        var actor = authorization.currentActor();
        String id = ids.newId();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "OFFER_ADVANCE",
                        fingerprint(
                                app.getId(),
                                status,
                                CommonUtils.stripToNull(command.getTerminationReason()),
                                CommonUtils.stripToNull(command.getApplicationOutcome()),
                                reason,
                                String.valueOf(command.getOccurredAt()),
                                CommonUtils.stripToNull(command.getSourceReference()),
                                CommonUtils.stripToNull(command.getSourceStatus())),
                        id,
                        clock.instant());
        if (replay != null) {
            OfferStatusRecord found = offerStatusRecordPersistenceService.findOffer(app.getId(), replay);
            if (found == null) {
                found = offerStatusRecordPersistenceService.findOfferForUpdate(app.getId(), replay);
            }
            if (found == null) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原 Offer 请求尚未完成，请稍后重试");
            }
            return found;
        }
        if (ApplicationStage.isTerminal(app.getCurrentStage())) {
            throw PlatformExceptions.conflict(
                    "APPLICATION_ARCHIVED", "已终止或入职的 Application 仅保留为历史；请重新选择 JD 创建新流程");
        }
        if (!OfferStatus.TERMINATED.matches(status)) {
            blacklist.requireRecruitmentAllowed(app.getCandidateId());
        }
        requireOpen(app);
        if (!ApplicationStage.OFFER.matches(app.getCurrentStage())) {
            throw PlatformExceptions.conflict("APPLICATION_NOT_IN_OFFER", "Application 尚未进入 Offer 阶段");
        }
        if (OfferStatus.ONBOARDED.matches(status)) {
            throw PlatformExceptions.unprocessable("ONBOARDING_EVENT_REQUIRED", "ONBOARDED 只能由真实入职事件生成");
        }
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        OfferStatus from =
                latest == null ? OfferStatus.NOT_STARTED : OfferStatus.fromCode(latest.getStatus());
        OfferStatus target;
        try {
            target = OfferStatus.fromCode(status);
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.unprocessable("OFFER_STATUS_INVALID", "Offer 状态无效");
        }
        if (!from.canTransitionTo(target)) {
            throw PlatformExceptions.conflict(
                    "OFFER_TRANSITION_INVALID",
                    "Offer 状态不能从 " + from.getCode() + " 推进到 " + status);
        }
        if (occupiesHeadcount(target) && !occupiesHeadcount(from)) {
            requireAvailableHeadcount(demands.lockJdForHeadcount(app.getJdVersionId()));
        }
        String outcome =
                terminationOutcome(status, command.getTerminationReason(), command.getApplicationOutcome());
        Instant occurred = command.getOccurredAt() == null ? clock.instant() : command.getOccurredAt();
        if (latest != null && occurred.isBefore(latest.getOccurredAt())) {
            throw PlatformExceptions.unprocessable("OFFER_TIME_NOT_MONOTONIC", "Offer 发生时间不能早于当前有效记录");
        }
        if (occurred.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("OFFER_TIME_INVALID", "Offer 发生时间不能晚于当前时间");
        }
        requireSingleWrite(offerStatusRecordPersistenceService.insertOffer(
                activitySource(app.getId(), "OFFER", id),
                app.getId(),
                "STATUS",
                status,
                CommonUtils.stripToNull(command.getTerminationReason()),
                outcome,
                reason,
                null,
                null,
                "MANUAL_BEISEN_REFERENCE",
                CommonUtils.stripToNull(command.getSourceReference()),
                CommonUtils.stripToNull(command.getSourceStatus()),
                occurred,
                actor.getUserId(),
                clock.instant()), "Offer 状态记录写入失败");
        if (OfferStatus.IN_PROGRESS.matches(status)) {
            persistence.markOfferInProgress(
                    app.getId(),
                    app.getOwnerUserId(),
                    dueAt(WorkflowStepCode.OFFER.getCode(), occurred),
                    actor.getUserId(),
                    occurred);
        }
        if (OfferStatus.TERMINATED.matches(status)) {
            recruitmentWorkflowStepPersistenceService.finishOfferStep(
                    app.getId(),
                    ApplicationStepStatus.FAILED.getCode(),
                    reason,
                    actor.getUserId(),
                    occurred);
            cancelScheduledApplicationInterviews(app.getId(), reason, actor.getUserId(), clock.instant());
            if (persistence.updateOutcome(
                    app.getId(),
                    ApplicationStage.TERMINATED.getCode(),
                    outcome,
                    occurred,
                    null,
                    app.getVersion(),
                    actor.getUserId(),
                    clock.instant())
                    == 0) {
                throw state();
            }
            requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                    app.getId(),
                    app.getCurrentStage(),
                    ApplicationStage.TERMINATED.getCode(),
                    reason,
                    actor.getUserId(),
                    occurred), "Application 阶段历史写入失败");
        }
        recordProgressIfLocked(app.getCandidateId(), "OFFER_ADVANCED", reason);
        return offerStatusRecordPersistenceService.findOffer(app.getId(), id);
    }

    @Transactional
    public OfferStatusRecord correctOffer(
            String applicationId, String recordId, OfferCommandDTO command, String rawIdempotencyKey) {
        Application app = requireHeadcountLockedApplication(applicationId);
        String status = CommonUtils.upperOrEmpty(command.getStatus());
        String reason = required(command.getReason(), "reason", 500);
        String sourceReference = CommonUtils.stripToNull(command.getSourceReference());
        String sourceStatus = CommonUtils.stripToNull(command.getSourceStatus());
        var actor = authorization.currentActor();
        String id = ids.newId();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "OFFER_CORRECT",
                        fingerprint(
                                app.getId(),
                                recordId,
                                status,
                                CommonUtils.stripToNull(command.getTerminationReason()),
                                CommonUtils.stripToNull(command.getApplicationOutcome()),
                                reason,
                                String.valueOf(command.getOccurredAt()),
                                sourceReference,
                                sourceStatus),
                        id,
                        clock.instant());
        if (replay != null) {
            OfferStatusRecord found = offerStatusRecordPersistenceService.findOffer(app.getId(), replay);
            if (found == null) {
                found = offerStatusRecordPersistenceService.findOfferForUpdate(app.getId(), replay);
            }
            if (found == null) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原 Offer 更正请求尚未完成，请稍后重试");
            }
            return found;
        }
        if (ApplicationStage.isTerminal(app.getCurrentStage())) {
            throw PlatformExceptions.conflict(
                    "APPLICATION_ARCHIVED", "已终止或入职的 Application 仅保留为历史；请重新选择 JD 创建新流程");
        }
        if (!OfferStatus.TERMINATED.matches(status)) {
            blacklist.requireRecruitmentAllowed(app.getCandidateId());
        }
        OfferStatusRecord prior = offerStatusRecordPersistenceService.findOffer(app.getId(), recordId);
        if (prior == null) {
            throw PlatformExceptions.notFound("OFFER_RECORD_NOT_FOUND", "Offer 记录不存在");
        }
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        if (latest == null || !latest.getId().equals(prior.getId())) {
            throw PlatformExceptions.conflict("OFFER_CORRECTION_NOT_LATEST", "只能更正当前生效的最新 Offer 记录");
        }
        OfferStatus corrected;
        try {
            corrected = OfferStatus.fromCode(status);
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.unprocessable("OFFER_STATUS_INVALID", "Offer 更正状态无效");
        }
        if (!corrected.isCorrectionTarget()) {
            throw PlatformExceptions.unprocessable("OFFER_STATUS_INVALID", "Offer 更正状态无效");
        }
        OfferStatus previousStatus = OfferStatus.fromCode(prior.getStatus());
        if (occupiesHeadcount(corrected) && !occupiesHeadcount(previousStatus)) {
            requireAvailableHeadcount(demands.lockJdForHeadcount(app.getJdVersionId()));
        }
        Instant occurred = command.getOccurredAt() == null ? clock.instant() : command.getOccurredAt();
        if (occurred.isBefore(prior.getOccurredAt())
                || occurred.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("OFFER_TIME_INVALID", "更正时间不能早于被更正记录且不能晚于当前时间");
        }
        String outcome =
                terminationOutcome(status, command.getTerminationReason(), command.getApplicationOutcome());
        requireSingleWrite(offerStatusRecordPersistenceService.insertOffer(
                activitySource(app.getId(), "OFFER", id),
                app.getId(),
                "CORRECTION",
                status,
                CommonUtils.stripToNull(command.getTerminationReason()),
                outcome,
                reason,
                prior.getId(),
                null,
                "MANUAL_BEISEN_REFERENCE",
                sourceReference,
                sourceStatus,
                occurred,
                actor.getUserId(),
                clock.instant()), "Offer 状态记录写入失败");
        if (corrected.keepsApplicationInOffer()) {
            persistence.markOfferInProgress(
                    app.getId(),
                    app.getOwnerUserId(),
                    dueAt(WorkflowStepCode.OFFER.getCode(), occurred),
                    actor.getUserId(),
                    occurred);
        }
        if (OfferStatus.TERMINATED.matches(status)) {
            recruitmentWorkflowStepPersistenceService.finishOfferStep(
                    app.getId(),
                    ApplicationStepStatus.FAILED.getCode(),
                    reason,
                    actor.getUserId(),
                    occurred);
            cancelScheduledApplicationInterviews(app.getId(), reason, actor.getUserId(), clock.instant());
            if (persistence.updateOutcome(
                    app.getId(),
                    ApplicationStage.TERMINATED.getCode(),
                    outcome,
                    occurred,
                    null,
                    app.getVersion(),
                    actor.getUserId(),
                    clock.instant())
                    == 0) {
                throw state();
            }
            requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                    app.getId(),
                    app.getCurrentStage(),
                    ApplicationStage.TERMINATED.getCode(),
                    reason,
                    actor.getUserId(),
                    occurred), "Application 阶段历史写入失败");
        }
        return offerStatusRecordPersistenceService.findOffer(app.getId(), id);
    }

    @Transactional
    public OnboardingEvent onboard(
            String applicationId,
            Instant onboardedAt,
            String sourceReference,
            String reason,
            String rawIdempotencyKey) {
        Application app = requireHeadcountLockedApplication(applicationId);
        String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), "");
        var actor = authorization.currentActor();
        String eventId = ids.newId();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "APPLICATION_ONBOARD",
                        fingerprint(app.getId(), String.valueOf(onboardedAt), CommonUtils.stripToNull(sourceReference), clean),
                        eventId,
                        clock.instant());
        if (replay != null) {
            OnboardingEvent found = onboardingEventPersistenceService.findOnboardingById(app.getId(), replay);
            if (found == null) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原入职请求尚未完成，请稍后重试");
            }
            return found;
        }
        requireOpen(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        if (latest == null || !OfferStatus.fromCode(latest.getStatus()).allowsOnboarding()) {
            throw PlatformExceptions.conflict("OFFER_NOT_ISSUED", "只有已发放 Offer 才能记录真实入职");
        }
        Instant occurred = onboardedAt == null ? clock.instant() : onboardedAt;
        if (occurred.isBefore(latest.getOccurredAt())
                || occurred.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable("ONBOARDING_TIME_INVALID", "入职时间必须不早于 Offer 发放时间且不能晚于当前时间");
        }
        requireAvailableHeadcount(demands.lockJdForHeadcount(app.getJdVersionId()));
        OnboardingProcess process = onboardingProcessPersistenceService.lockProcess(app.getId());
        if (process == null) {
            requireSingleWrite(
                    onboardingProcessPersistenceService.insertProcess(
                            app.getId(), occurred, actor.getUserId(), clock.instant()),
                    "入职流程创建失败");
            process = onboardingProcessPersistenceService.lockProcess(app.getId());
        }
        if (!OnboardingProcessStatus.IN_PROGRESS.matches(process.getStatus())) {
            throw PlatformExceptions.conflict("ONBOARDING_PROCESS_NOT_ACTIVE", "当前记录不在可确认的入职中状态");
        }
        if (occurred.isBefore(process.getStartedAt())) {
            throw PlatformExceptions.unprocessable(
                    "ONBOARDING_TIME_BEFORE_PROCESS", "实际入职时间不能早于进入入职流程的时间");
        }
        // 入职事件、Offer 历史和阶段历史必须一并成功，不能只有状态投影而缺少真实事实。
        try {
            requireSingleWrite(onboardingEventPersistenceService.insertOnboarding(
                    activitySource(app.getId(), "ONBOARDING", eventId),
                    app.getId(),
                    occurred,
                    "MANUAL_BEISEN_REFERENCE",
                    CommonUtils.stripToNull(sourceReference),
                    clean,
                    actor.getUserId(),
                    clock.instant()), "入职事件写入失败");
            requireSingleWrite(offerStatusRecordPersistenceService.insertOffer(
                    activitySource(app.getId(), "OFFER", ids.newId()),
                    app.getId(),
                    "STATUS",
                    OfferStatus.ONBOARDED.getCode(),
                    null,
                    ApplicationDecision.PASS.getCode(),
                    clean,
                    null,
                    null,
                    "MANUAL_BEISEN_REFERENCE",
                    CommonUtils.stripToNull(sourceReference),
                    null,
                    occurred,
                    actor.getUserId(),
                    clock.instant()), "Offer 状态记录写入失败");
        } catch (DuplicateKeyException ex) {
            throw PlatformExceptions.conflict(
                    "ONBOARDING_SOURCE_EVENT_EXISTS", "该入职来源引用已被其他 Offer 或入职事件使用");
        }
        recruitmentWorkflowStepPersistenceService.finishOfferStep(
                app.getId(),
                ApplicationStepStatus.PASSED.getCode(),
                clean,
                actor.getUserId(),
                occurred);
        if (persistence.updateOutcome(
                app.getId(),
                ApplicationStage.ONBOARDED.getCode(),
                ApplicationDecision.PASS.getCode(),
                occurred,
                null,
                app.getVersion(),
                actor.getUserId(),
                clock.instant())
                == 0) {
            throw state();
        }
        requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                app.getId(),
                app.getCurrentStage(),
                ApplicationStage.ONBOARDED.getCode(),
                clean,
                actor.getUserId(),
                occurred), "Application 阶段历史写入失败");
        requireSingleWrite(
                onboardingProcessPersistenceService.markOnboarded(
                        app.getId(), occurred, actor.getUserId(), process.getVersion(), clock.instant()),
                "入职流程状态更新失败");
        followUpPlanLifecycle.cancelForApplication(app.getId(), clean, actor.getUserId(), clock.instant());
        return onboardingEventPersistenceService.findOnboarding(app.getId());
    }

    /** 一次确认完成人工录用、Offer 事实并进入入职流程，实际入职由后续时间确认产生。 */
    @Transactional
    public OnboardingProcess hire(
            String applicationId,
            String selectedOfferStatus,
            String reason,
            String rawIdempotencyKey) {
        Application app = requireHeadcountLockedApplication(applicationId);
        String status = CommonUtils.upperOrEmpty(selectedOfferStatus);
        if (!OfferStatus.IN_PROGRESS.matches(status) && !OfferStatus.ISSUED.matches(status)) {
            throw PlatformExceptions.unprocessable(
                    "HIRE_OFFER_STATUS_INVALID", "确认录用时 Offer 进展只能选择处理中或已发出");
        }
        String clean = required(reason, "reason", 500);
        var actor = authorization.currentActor();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "APPLICATION_HIRE",
                        fingerprint(app.getId(), status, clean),
                        app.getId(),
                        clock.instant());
        if (replay != null) {
            OnboardingProcess found = onboardingProcessPersistenceService.findProcess(replay);
            if (found == null) {
                found = onboardingProcessPersistenceService.lockProcess(replay);
            }
            if (found == null) {
                throw PlatformExceptions.conflict(
                        "IDEMPOTENCY_REPLAY_INCOMPLETE", "原确认录用请求尚未完成，请稍后重试");
            }
            return found;
        }
        requireOpen(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        if (!ApplicationStage.DECISION.matches(app.getCurrentStage())
                && !ApplicationStage.OFFER.matches(app.getCurrentStage())) {
            nextActionResponse(
                    app.getId(), WorkflowStepCode.DECISION.getCode(), clean, app.getVersion(), null);
        }
        Application current = persistence.lockApplication(app.getId());
        if (ApplicationStage.DECISION.matches(current.getCurrentStage())) {
            decide(
                    current.getId(),
                    ApplicationDecision.PASS.getCode(),
                    clean,
                    null,
                    derivedIdempotencyKey(rawIdempotencyKey, "decision"));
        }
        current = persistence.lockApplication(app.getId());
        if (!ApplicationStage.OFFER.matches(current.getCurrentStage())) {
            throw PlatformExceptions.conflict("APPLICATION_NOT_IN_OFFER", "招聘活动未进入可确认录用的 Offer 阶段");
        }
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        OfferStatus latestStatus = latest == null
                ? OfferStatus.NOT_STARTED
                : OfferStatus.fromCode(latest.getStatus());
        if (latestStatus == OfferStatus.NOT_STARTED
                || (latestStatus == OfferStatus.IN_PROGRESS && OfferStatus.ISSUED.matches(status))) {
            addOffer(
                    app.getId(),
                    new OfferCommandDTO(status, null, null, clean, clock.instant(), null, null),
                    derivedIdempotencyKey(rawIdempotencyKey, "offer-selected"));
            latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
            latestStatus = OfferStatus.fromCode(latest.getStatus());
        }
        if (latestStatus == OfferStatus.IN_PROGRESS) {
            addOffer(
                    app.getId(),
                    new OfferCommandDTO(
                            OfferStatus.ISSUED.getCode(),
                            null,
                            null,
                            clean,
                            clock.instant(),
                            null,
                            null),
                    derivedIdempotencyKey(rawIdempotencyKey, "offer-issued"));
            latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
            latestStatus = OfferStatus.fromCode(latest.getStatus());
        }
        if (!latestStatus.allowsOnboarding()) {
            throw PlatformExceptions.conflict("OFFER_NOT_ISSUED", "只有已发出的 Offer 可以确认录用");
        }
        Instant startedAt = clock.instant();
        try {
            requireSingleWrite(
                    onboardingProcessPersistenceService.insertProcess(
                            app.getId(), startedAt, actor.getUserId(), startedAt),
                    "入职流程创建失败");
        } catch (DuplicateKeyException exception) {
            throw PlatformExceptions.conflict("ONBOARDING_PROCESS_EXISTS", "该招聘活动已经进入入职流程");
        }
        followUpPlanLifecycle.cancelForApplication(
                app.getId(), clean, actor.getUserId(), startedAt);
        return onboardingProcessPersistenceService.findProcess(app.getId());
    }

    /** 首次确认产生真实入职事实；后续复核只追加更正并更新有效时间投影。 */
    @Transactional
    public OnboardingProcess reviewOnboarding(
            String applicationId,
            Instant onboardedAt,
            String reason,
            String rawIdempotencyKey) {
        Application app = requireHeadcountLockedApplication(applicationId);
        Instant reviewedAt = Objects.requireNonNull(onboardedAt, "onboardedAt");
        String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), "确认实际入职时间");
        var actor = authorization.currentActor();
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "ONBOARDING_REVIEW",
                        fingerprint(app.getId(), reviewedAt.toString(), clean),
                        app.getId(),
                        clock.instant());
        if (replay != null) {
            OnboardingProcess found = onboardingProcessPersistenceService.findProcess(replay);
            if (found == null) {
                throw PlatformExceptions.conflict(
                        "IDEMPOTENCY_REPLAY_INCOMPLETE", "原入职时间复核请求尚未完成，请稍后重试");
            }
            return found;
        }
        OnboardingProcess process = onboardingProcessPersistenceService.lockProcess(app.getId());
        if (process == null || OnboardingProcessStatus.RETURNED.matches(process.getStatus())) {
            throw PlatformExceptions.conflict("ONBOARDING_PROCESS_REQUIRED", "只有入职流程中的记录可以确认入职时间");
        }
        if (reviewedAt.isBefore(process.getStartedAt())
                || reviewedAt.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable(
                    "ONBOARDING_TIME_INVALID", "实际入职时间必须不早于进入入职流程的时间且不能晚于当前时间");
        }
        if (OnboardingProcessStatus.IN_PROGRESS.matches(process.getStatus())) {
            onboard(
                    app.getId(),
                    reviewedAt,
                    null,
                    clean,
                    derivedIdempotencyKey(rawIdempotencyKey, "actual-onboarding"));
            return onboardingProcessPersistenceService.findProcess(app.getId());
        }
        if (!ApplicationStage.ONBOARDED.matches(app.getCurrentStage())) {
            throw PlatformExceptions.conflict("APPLICATION_NOT_ONBOARDED", "已入职流程状态与招聘活动阶段不一致");
        }
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        if (latest == null || !OfferStatus.ONBOARDED.matches(latest.getStatus())) {
            throw PlatformExceptions.conflict("ONBOARDING_FACT_REQUIRED", "缺少当前生效的入职事实");
        }
        OfferStatusRecord issued = offerStatusRecordPersistenceService.findOffers(app.getId()).stream()
                .filter(value -> OfferStatus.fromCode(value.getStatus()).allowsOnboarding())
                .reduce((left, right) -> right)
                .orElseThrow(() -> PlatformExceptions.conflict("OFFER_NOT_ISSUED", "缺少已发出的 Offer 事实"));
        if (reviewedAt.isBefore(issued.getOccurredAt())
                || reviewedAt.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
            throw PlatformExceptions.unprocessable(
                    "ONBOARDING_TIME_INVALID", "入职时间必须不早于 Offer 发放时间且不能晚于当前时间");
        }
        Instant now = clock.instant();
        String recordId = ids.newId();
        requireSingleWrite(
                offerStatusRecordPersistenceService.insertOffer(
                        activitySource(app.getId(), "OFFER", recordId),
                        app.getId(),
                        "CORRECTION",
                        OfferStatus.ONBOARDED.getCode(),
                        null,
                        ApplicationDecision.PASS.getCode(),
                        clean,
                        latest.getId(),
                        null,
                        "MANUAL_ONBOARDING_REVIEW",
                        null,
                        "ONBOARDING_TIME_REVIEWED",
                        reviewedAt,
                        actor.getUserId(),
                        now),
                "入职时间复核记录写入失败");
        if (persistence.updateOutcome(
                        app.getId(),
                        ApplicationStage.ONBOARDED.getCode(),
                        ApplicationDecision.PASS.getCode(),
                        reviewedAt,
                        null,
                        app.getVersion(),
                        actor.getUserId(),
                        now)
                == 0) {
            throw state();
        }
        requireSingleWrite(
                onboardingProcessPersistenceService.markOnboarded(
                        app.getId(), reviewedAt, actor.getUserId(), process.getVersion(), now),
                "入职流程有效时间更新失败");
        return onboardingProcessPersistenceService.findProcess(app.getId());
    }

    /** 结束入职流程并把候选人当前简历转入共享库。 */
    @Transactional
    public OfferStatusRecord returnOnboardingToShared(
            String applicationId,
            String reasonCategory,
            String reasonDetail,
            String rawIdempotencyKey) {
        Application snapshot = requireAuthorized(applicationId);
        requireProcessingAllowed(snapshot);
        OnboardingReturnReason category;
        try {
            category = OnboardingReturnReason.fromNullableCode(reasonCategory);
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.unprocessable("ONBOARDING_RETURN_REASON_INVALID", "入职库退回原因无效");
        }
        String detail = optional(reasonDetail, "reasonDetail", 460);
        String clean = category.getDesc() + (detail == null ? "" : "：" + detail);
        var actor = authorization.currentActor();
        String recordId = ids.newId();
        // 与正向命令统一 C → 幂等键；退回及其历史回放不做黑名单阻断。
        blacklist.lockCandidateForCommand(snapshot.getCandidateId());
        String replay =
                reserveIdempotency(
                        actor.getUserId(),
                        rawIdempotencyKey,
                        "ONBOARDING_RETURN_TO_SHARED",
                        fingerprint(snapshot.getId(), category.getCode(), detail),
                        recordId,
                        clock.instant());
        if (replay != null) {
            OfferStatusRecord found =
                    offerStatusRecordPersistenceService.findOffer(snapshot.getId(), replay);
            if (found == null) {
                throw PlatformExceptions.conflict(
                        "IDEMPOTENCY_REPLAY_INCOMPLETE", "原退回共享简历库请求尚未完成，请稍后重试");
            }
            return found;
        }
        candidateOnboardingReturns.returnToShared(
                snapshot.getCandidateId(),
                snapshot.getResumeVersionId(),
                clean,
                actor.getUserId());
        Application app = requireLockedProcessingApplication(applicationId);
        OnboardingProcess process = onboardingProcessPersistenceService.lockProcess(app.getId());
        if (process == null || OnboardingProcessStatus.RETURNED.matches(process.getStatus())) {
            throw PlatformExceptions.conflict("APPLICATION_NOT_ONBOARDED", "只有入职流程中的记录可以退回共享简历库");
        }
        OfferStatusRecord latest = offerStatusRecordPersistenceService.latestOfferForUpdate(app.getId());
        if (latest == null || (!OfferStatus.ONBOARDED.matches(latest.getStatus())
                && !OfferStatus.fromCode(latest.getStatus()).allowsOnboarding())) {
            throw PlatformExceptions.conflict("ONBOARDING_FACT_REQUIRED", "缺少当前生效的入职事实");
        }
        Instant now = clock.instant();
        String outcome = category.outcome().getCode();
        requireSingleWrite(
                offerStatusRecordPersistenceService.insertOffer(
                        activitySource(app.getId(), "OFFER", recordId),
                        app.getId(),
                        "STATUS",
                        OfferStatus.TERMINATED.getCode(),
                        OfferTerminationReason.OTHER.getCode(),
                        outcome,
                        clean,
                        null,
                        null,
                        "MANUAL_ONBOARDING_RETURN",
                        null,
                        "ONBOARDING_LIBRARY_RETURN",
                        now,
                        actor.getUserId(),
                        now),
                "入职库退回记录写入失败");
        if (persistence.updateOutcome(
                        app.getId(),
                        ApplicationStage.TERMINATED.getCode(),
                        outcome,
                        now,
                        null,
                        app.getVersion(),
                        actor.getUserId(),
                        now)
                == 0) {
            throw state();
        }
        requireSingleWrite(
                stageHistoryPersistenceService.insertStageHistory(
                        activitySource(app.getId(), "STAGE", ids.newId()),
                        app.getId(),
                        app.getCurrentStage(),
                        ApplicationStage.TERMINATED.getCode(),
                        clean,
                        actor.getUserId(),
                        now),
                "Application 阶段历史写入失败");
        requireSingleWrite(
                onboardingProcessPersistenceService.markReturned(
                        app.getId(), actor.getUserId(), process.getVersion(), now),
                "入职流程退回状态更新失败");
        return offerStatusRecordPersistenceService.findOffer(app.getId(), recordId);
    }

    @Transactional(readOnly = true)
    public List<WorkItem> workbench(boolean management, int limit) {
        authorization.requirePermission("recruitment.manage");
        var actor = authorization.currentActor();
        if (management && !actor.hasCompanyWideScope()
                && !actor.getRoles().contains("RECRUITMENT_MANAGER")) {
            throw PlatformExceptions.forbidden("MANAGEMENT_WORKBENCH_FORBIDDEN", "仅招聘负责人可查看管理工作台");
        }
        boolean scopeAll = management && actor.hasCompanyWideScope();
        Set<String> managedUserIds = actor.getOperationalManagedUserIds();
        Instant now = clock.instant();
        var rows = persistence.findWorkItems(
                actor.getUserId(), scopeAll, managedUserIds, Math.clamp(limit, 1, 200));
        if (rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(
                        row -> {
                            Instant due = dueAt(row.getStepCode(), row.getTaskAnchorAt());
                            return new WorkItem(
                                    row.getApplicationId(),
                                    row.getCandidateId(),
                                    row.getJdId(),
                                    row.getJdVersionId(),
                                    row.getStepId(),
                                    row.getStepCode(),
                                    row.getStepStatus(),
                                    row.getOwnerUserId(),
                                    due,
                                    due.isBefore(now),
                                    row.getCurrentStage(),
                                    row.getOutcome(),
                                    row.getApplicationCreatedAt(),
                                    Duration.between(row.getApplicationCreatedAt(), now).toSeconds());
                        })
                .toList();
    }

    public List<OfferStatusRecord> offers(String applicationId) {
        requireReadable(applicationId);
        return offerStatusRecordPersistenceService.findOffers(applicationId);
    }

    public List<WorkflowStep> steps(String applicationId) {
        requireReadable(applicationId);
        return recruitmentWorkflowStepPersistenceService.findSteps(applicationId);
    }

    public List<FollowUp> followUps(String applicationId, String stepId) {
        requireReadable(applicationId);
        requireStep(applicationId, stepId);
        return followUpPersistenceService.findFollowUps(stepId);
    }

    @Transactional(readOnly = true)
    public InterviewContext requireInterviewContext(String applicationId, String workflowStepId) {
        Application app = requireAuthorized(applicationId);
        requireOpen(app);
        WorkflowStep step = requireStep(app.getId(), workflowStepId);
        return interviewContext(app, step);
    }

    /**
     * 排期前锁定活动及步骤，使用当前状态、权限和负责人快照。
     */
    @Transactional
    public InterviewContext requireLockedInterviewContext(String applicationId, String workflowStepId) {
        Application app = requireLockedOpenProcessingApplication(applicationId);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        WorkflowStep step = recruitmentWorkflowStepPersistenceService.lockStep(app.getId(), workflowStepId);
        if (step == null) {
            throw PlatformExceptions.notFound("WORKFLOW_STEP_NOT_FOUND", "流程步骤不存在");
        }
        interviewContext(app, step);
        requireScheduledRound(app, step);
        if (ApplicationStepStatus.PENDING.matches(step.getStatus())) {
            if (recruitmentWorkflowStepPersistenceService.startStep(step.getId(), step.getVersion(),
                    authorization.currentActor().getUserId(), clock.instant()) != 1) {
                throw state();
            }
            step.setStatus(ApplicationStepStatus.IN_PROGRESS.getCode());
        }
        return interviewContext(app, step);
    }

    /** 两种面试排期入口共用当前权限事实，保持授权、终态、办理权限的检查顺序。 */
    private Application requireLockedOpenProcessingApplication(String applicationId) {
        lockCandidateForApplication(applicationId);
        Application app = persistence.lockApplication(applicationId);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        // 已有普通读视图时，锁后的排期权限使用同一份当前协作人事实。
        List<String> collaboratorIds = applicationCollaboratorPersistenceService.findCollaboratorsForUpdate(app.getId());
        authorize(app, collaboratorIds);
        requireOpen(app);
        requireProcessingAllowed(app.getOwnerUserId(), Set.copyOf(collaboratorIds));
        return app;
    }

    private void requireScheduledRound(Application app, WorkflowStep step) {
        int round = step.getRoundNo() != null ? step.getRoundNo()
                : step.getStepCode().startsWith("INTERVIEW_ROUND_")
                ? Integer.parseInt(step.getStepCode().substring("INTERVIEW_ROUND_".length())) : 0;
        if (round > 1 && step.getStepCode().startsWith("INTERVIEW_ROUND_")) {
            List<Interview> arranged = interviewPersistenceService.lockArrangedApplicationInterviews(app.getId());
            if (arranged.stream().noneMatch(interview -> interview.getRoundNo() == 1)
                    || arranged.stream().anyMatch(interview -> !step.getId().equals(interview.getWorkflowStepId())
                    && interview.getRoundNo() >= round)) {
                throw PlatformExceptions.unprocessable("INTERVIEW_ROUND_INVALID", "必须先安排一面，后续只能安排更高轮次");
            }
        }
    }

    private InterviewContext interviewContext(Application app, WorkflowStep step) {
        if (!ApplicationStepStatus.isActive(step.getStatus())) {
            throw PlatformExceptions.conflict("INTERVIEW_STEP_NOT_ACTIVE", "只能为实际路径中待处理或进行中的面试步骤安排面试");
        }
        if (!workflowStepCode(step.getStepCode()).isInterviewStep()) {
            throw PlatformExceptions.unprocessable("INTERVIEW_STEP_INVALID", "所选流程步骤不是面试步骤");
        }
        return new InterviewContext(
                app.getId(),
                app.getCandidateId(),
                app.getJdId(),
                app.getJdVersionId(),
                app.getWorkflowTemplateVersionId(),
                app.getRecruitmentType(),
                step.getId(),
                step.getStepCode(),
                step.getStatus(),
                app.getOwnerUserId(),
                app.getOwnerDepartmentId(),
                Set.copyOf(applicationCollaboratorPersistenceService.findCollaborators(app.getId())));
    }

    @Transactional(readOnly = true)
    public SubmissionApplicationContext requireSubmissionContext(String applicationId) {
        Application app = requireAuthorized(applicationId);
        requireOpen(app);
        return new SubmissionApplicationContext(
                app.getId(),
                app.getCandidateId(),
                app.getDemandId(),
                app.getJdId(),
                app.getJdVersionId(),
                app.getResumeVersionId(),
                app.getRecruitmentType(),
                app.getCurrentStage(),
                app.getOwnerUserId(),
                app.getOwnerDepartmentId(),
                Set.copyOf(applicationCollaboratorPersistenceService.findCollaborators(app.getId())));
    }

    /**
     * 面试排期在同一事务中绑定活动步骤；调用方已先锁定并校验推荐，锁顺序与撤回保持一致。
     */
    @Transactional
    public InterviewContext bindClientInterviewStep(String applicationId, String stepId, String submissionId) {
        Application app = requireLockedOpenProcessingApplication(applicationId);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        WorkflowStep step = recruitmentWorkflowStepPersistenceService.lockStep(app.getId(), stepId);
        if (step == null || !workflowStepCode(step.getStepCode()).isInterviewStep()
                || !isClientStep(step.getStepCode()) || !ApplicationStepStatus.isActive(step.getStatus())) {
            throw PlatformExceptions.conflict("INTERVIEW_STEP_NOT_ACTIVE", "甲方面试必须绑定当前活动的甲方步骤");
        }
        requireScheduledRound(app, step);
        requireMatchingSubmission(app, submissionId);
        bindClientStep(step, submissionId);
        return interviewContext(app, step);
    }

    /**
     * 发送事实与当前客户推荐步骤同步绑定；未采用该步骤的直接推荐不改变选路。
     */
    @Transactional
    public void bindSentClientSubmissionStep(String applicationId, String submissionId) {
        Application app = requireLockedAuthorized(applicationId);
        requireProcessingAllowed(app);
        requireOpen(app);
        blacklist.requireRecruitmentAllowed(app.getCandidateId());
        WorkflowStep step = recruitmentWorkflowStepPersistenceService.lockSteps(app.getId()).stream()
                .filter(value -> WorkflowStepCode.CLIENT_SUBMISSION.matches(value.getStepCode())
                        && ApplicationStepStatus.isActive(value.getStatus()))
                .findFirst().orElse(null);
        if (step != null) {
            requireMatchingSubmission(app, submissionId);
            bindClientStep(step, submissionId);
        }
    }

    private void bindClientStep(WorkflowStep step, String submissionId) {
        if (step.getClientSubmissionId() != null) {
            if (!submissionId.equals(step.getClientSubmissionId())) {
                throw PlatformExceptions.conflict("INTERVIEW_SUBMISSION_ALREADY_BOUND", "当前步骤已绑定其他推荐，必须先撤回并重新选路");
            }
            return;
        }
        if (recruitmentWorkflowStepPersistenceService.bindClientSubmission(
                step.getId(), submissionId, step.getVersion(), authorization.currentActor().getUserId(), clock.instant()) != 1) {
            throw state();
        }
    }

    /**
     * 同时撤回活动甲方步骤和关联待进行面试，保留已完成的推荐、步骤及面评事实。
     */
    @Transactional
    public void withdrawBoundClientStep(String applicationId, String submissionId, String reason) {
        Application app = requireLockedProcessingApplication(applicationId);
        String clean = required(reason, "reason", 500);
        // 活动终态不妨碍收尾仍在筛选/面试中的独立推荐；不得因此改写步骤或重新开启活动。
        if (app.getTerminalAt() != null) {
            return;
        }
        List<WorkflowStep> steps = recruitmentWorkflowStepPersistenceService.lockSteps(app.getId());
        Set<String> interviewStepIds = withdrawSubmissionInterviews(app.getId(), submissionId, clean);
        WorkflowStep step =
                steps.stream()
                        .filter(
                                s ->
                                        (submissionId.equals(s.getClientSubmissionId())
                                                || (s.getClientSubmissionId() == null && interviewStepIds.contains(s.getId())))
                                                && isClientStep(s.getStepCode())
                                                // 已完成的推荐/面试保留原事实，只撤回当前活动步骤。
                                                && ApplicationStepStatus.isActive(s.getStatus()))
                        .findFirst()
                        .orElse(null);
        if (step == null) {
            return;
        }
        Instant now = clock.instant();
        if (step.getClientSubmissionId() == null
                && recruitmentWorkflowStepPersistenceService.bindClientSubmission(
                step.getId(), submissionId, step.getVersion(), authorization.currentActor().getUserId(), now) != 1) {
            throw state();
        }
        if (recruitmentWorkflowStepPersistenceService.withdrawBoundClientStep(
                step.getId(), clean, authorization.currentActor().getUserId(), now)
                == 0) {
            throw state();
        }
        // 阶段版本与变更前值必须来自锁后行，避免普通读快照的旧版本导致撤回失败。
        Application current = app;
        if (!ApplicationStage.PATH_SELECTION.matches(current.getCurrentStage())) {
            if (persistence.updateStage(
                    current.getId(),
                    ApplicationStage.PATH_SELECTION.getCode(),
                    current.getVersion(),
                    authorization.currentActor().getUserId(),
                    now)
                    == 0) {
                throw state();
            }
            requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(current.getId(), "STAGE", ids.newId()),
                    current.getId(),
                    current.getCurrentStage(),
                    ApplicationStage.PATH_SELECTION.getCode(),
                    clean,
                    authorization.currentActor().getUserId(),
                    now), "Application 阶段历史写入失败");
        }
    }

    private Set<String> withdrawSubmissionInterviews(String applicationId, String submissionId, String reason) {
        Set<String> stepIds = new LinkedHashSet<>();
        String afterId = null;
        String actorId = authorization.currentActor().getUserId();
        Instant now = clock.instant();
        while (true) {
            List<Interview> interviews = interviewPersistenceService.lockSubmissionInterviews(applicationId, submissionId, afterId);
            List<Interview> cancelledInterviews = new ArrayList<>();
            for (Interview interview : interviews) {
                stepIds.add(interview.getWorkflowStepId());
                if (InterviewStatus.SCHEDULED.matches(interview.getStatus())) {
                    if (interviewPersistenceService.cancel(interview.getId(), interview.getVersion(), actorId, now) != 1) {
                        throw state();
                    }
                    requireSingleWrite(interviewPersistenceService.insertHistory(
                            activitySource(applicationId, "INTERVIEW_SCHEDULE", ids.newId()), interview.getId(), "CANCEL",
                            JsonText.write(Map.of("status", interview.getStatus(), "clientSubmissionId", submissionId)),
                            JsonText.write(Map.of("status", InterviewStatus.CANCELLED.getCode(), "clientSubmissionId", submissionId)),
                            reason, actorId, now), "推荐撤回时面试取消历史写入失败");
                    cancelledInterviews.add(interview);
                }
            }
            if (!cancelledInterviews.isEmpty()) {
                interviewNotificationManager.createNotificationsBatch(cancelledInterviews, InterviewNotificationType.INTERVIEW_CANCEL, actorId);
            }
            if (interviews.size() < SUBMISSION_WITHDRAWAL_PAGE_SIZE) {
                return stepIds;
            }
            afterId = interviews.getLast().getId();
        }
    }

    /**
     * 调用方已锁定 Application；按相同的父记录到面试锁序取消剩余排期，保留完成事实。
     */
    private void cancelScheduledApplicationInterviews(String applicationId, String reason, String actorId, Instant now) {
        followUpPlanLifecycle.cancelForApplication(applicationId, reason, actorId, now);
        String afterId = null;
        while (true) {
            List<Interview> interviews = interviewPersistenceService.lockScheduledApplicationInterviews(applicationId, afterId);
            for (Interview interview : interviews) {
                if (interviewPersistenceService.cancel(interview.getId(), interview.getVersion(), actorId, now) != 1) {
                    throw state();
                }
                requireSingleWrite(interviewPersistenceService.insertHistory(
                        activitySource(applicationId, "INTERVIEW_SCHEDULE", ids.newId()), interview.getId(), "CANCEL",
                        JsonText.write(Map.of("status", interview.getStatus(), "applicationId", applicationId)),
                        JsonText.write(Map.of("status", InterviewStatus.CANCELLED.getCode(), "applicationId", applicationId)),
                        reason, actorId, now), "招聘活动终止时面试取消历史写入失败");
            }
            if (!interviews.isEmpty()) {
                interviewNotificationManager.createNotificationsBatch(interviews, InterviewNotificationType.INTERVIEW_CANCEL, actorId);
            }
            if (interviews.size() < APPLICATION_TERMINATION_PAGE_SIZE) {
                return;
            }
            afterId = interviews.getLast().getId();
        }
    }

    /**
     * 校验并获取教育经历访问。
     */
    @Transactional(readOnly = true)
    public EducationAccess requireEducationAccess(String applicationId, String candidateId) {
        Application app = requireAuthorized(applicationId);
        if (!app.getCandidateId().equals(candidateId)) {
            throw PlatformExceptions.unprocessable("APPLICATION_CANDIDATE_MISMATCH", "学历记录候选人与招聘活动不一致");
        }
        return new EducationAccess(
                app.getId(), app.getCandidateId(), app.getOwnerUserId(), app.getOwnerDepartmentId());
    }

    private void advance(Application app, String reason, Instant now) {
        // 已安排的其他轮次保留进行中；全部当前动作结束后才交还 HR 选路。
        List<WorkflowStep> activeSteps = recruitmentWorkflowStepPersistenceService.findActiveSteps(List.of(app.getId()));
        String nextStage = activeSteps.isEmpty() ? ApplicationStage.PATH_SELECTION.getCode()
                : stageFor(activeSteps.getFirst().getStepCode());
        if (persistence.enableProgressiveStage(app.getId(), nextStage,
                app.getVersion(), authorization.currentActor().getUserId(), now) != 1) {
            throw state();
        }
        requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()), app.getId(),
                app.getCurrentStage(), nextStage, reason,
                authorization.currentActor().getUserId(), now), "逐步跟进阶段历史写入失败");
    }

    /**
     * 应用人工决定。
     */
    private void applyDecision(
            Application app, String decision, String reason, String actor, Instant now) {
        if (ApplicationDecision.PASS.matches(decision)) {
            if (persistence.updateOutcome(
                    app.getId(),
                    ApplicationStage.OFFER.getCode(),
                    ApplicationDecision.PASS.getCode(),
                    null,
                    1,
                    app.getVersion(),
                    actor,
                    now)
                    == 0) {
                throw state();
            }
            recruitmentWorkflowStepPersistenceService.activateOfferStep(
                    app.getId(),
                    app.getOwnerUserId(),
                    dueAt(WorkflowStepCode.OFFER.getCode(), now),
                    actor,
                    now);
            requireSingleWrite(offerStatusRecordPersistenceService.insertOffer(
                activitySource(app.getId(), "OFFER", ids.newId()),
                    app.getId(),
                    "STATUS",
                    OfferStatus.NOT_STARTED.getCode(),
                    null,
                    null,
                    "人工决定通过，进入 Offer",
                    null,
                    null,
                    "MANUAL_BEISEN_REFERENCE",
                    null,
                    null,
                    now,
                    actor,
                    now), "Offer 状态记录写入失败");
            requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                    app.getId(),
                    app.getCurrentStage(),
                    ApplicationStage.OFFER.getCode(),
                    reason,
                    actor,
                    now), "Application 阶段历史写入失败");
        } else {
            recruitmentWorkflowStepPersistenceService.closeUnreachedSteps(app.getId(), actor, now);
            cancelScheduledApplicationInterviews(app.getId(), reason, actor, now);
            if (persistence.updateOutcome(
                    app.getId(),
                    ApplicationStage.TERMINATED.getCode(),
                    decision,
                    now,
                    null,
                    app.getVersion(),
                    actor,
                    now)
                    == 0) {
                throw state();
            }
            requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                    app.getId(),
                    app.getCurrentStage(),
                    ApplicationStage.TERMINATED.getCode(),
                    reason,
                    actor,
                    now), "Application 阶段历史写入失败");
        }
    }

    /** 当前活动的决定待办与更正事实原子完成；Offer 阶段的已完成决定不可覆盖。 */
    private void finishActiveDecisionOnCorrection(
            Application app, String decision, String reason, String submissionId, String actor, Instant now) {
        if (!ApplicationStage.DECISION.matches(app.getCurrentStage())) {
            return;
        }
        WorkflowStep step = recruitmentWorkflowStepPersistenceService.findSteps(app.getId()).stream()
                .filter(candidate -> WorkflowStepCode.DECISION.matches(candidate.getStepCode()))
                .findFirst()
                .orElseThrow(() -> PlatformExceptions.unprocessable("DECISION_STEP_MISSING", "流程中没有人工决定步骤"));
        if (!ApplicationStepStatus.isActive(step.getStatus())) {
            throw PlatformExceptions.conflict("DECISION_STEP_NOT_ACTIVE", "人工决定步骤尚未到达或已经完成");
        }
        if (recruitmentWorkflowStepPersistenceService.finishDecisionStep(step.getId(),
                ApplicationDecision.PASS.matches(decision) ? ApplicationStepStatus.PASSED.getCode()
                        : ApplicationStepStatus.FAILED.getCode(),
                reason, submissionId, actor, now) != 1) {
            throw state();
        }
    }

    /**
     * 应用决定更正。
     */
    private void applyDecisionCorrection(
            Application app, String decision, String reason, String actor, Instant now) {
        Application current = persistence.findApplication(app.getId());
        if (ApplicationDecision.PASS.matches(decision)) {
            if (ApplicationStage.DECISION.matches(current.getCurrentStage())) {
                applyDecision(current, decision, reason, actor, now);
            }
            return;
        }
        recruitmentWorkflowStepPersistenceService.closeUnreachedSteps(app.getId(), actor, now);
        cancelScheduledApplicationInterviews(app.getId(), reason, actor, now);
        if (persistence.updateOutcome(
                app.getId(),
                ApplicationStage.TERMINATED.getCode(),
                decision,
                now,
                null,
                current.getVersion(),
                actor,
                now)
                == 0) {
            throw state();
        }
        OfferStatusRecord offer = offerStatusRecordPersistenceService.latestOffer(app.getId());
        if (offer != null) {
            requireSingleWrite(offerStatusRecordPersistenceService.insertOffer(
                activitySource(app.getId(), "OFFER", ids.newId()),
                    app.getId(),
                    "STATUS",
                    OfferStatus.TERMINATED.getCode(),
                    "OTHER",
                    decision,
                    "人工决定更正导致 Offer 终止：" + reason,
                    null,
                    null,
                    "MANUAL_BEISEN_REFERENCE",
                    null,
                    null,
                    now,
                    actor,
                    now), "Offer 状态记录写入失败");
        }
        recruitmentWorkflowStepPersistenceService.finishOfferStep(
                app.getId(), ApplicationStepStatus.FAILED.getCode(), reason, actor, now);
        requireSingleWrite(stageHistoryPersistenceService.insertStageHistory(
                activitySource(app.getId(), "STAGE", ids.newId()),
                app.getId(),
                current.getCurrentStage(),
                ApplicationStage.TERMINATED.getCode(),
                reason,
                actor,
                now), "Application 阶段历史写入失败");
    }

    private ApplicationDetails details(Application app) {
        return details(app, applicationCollaboratorPersistenceService.findCollaborators(app.getId()));
    }

    private ApplicationDetails details(Application app, List<String> collaborators) {
        if (!sharedCandidate(app.getCandidateId())) authorize(app, collaborators);
        Instant end = app.getTerminalAt() == null ? clock.instant() : app.getTerminalAt();
        return new ApplicationDetails(
                app,
                collaborators,
                recruitmentWorkflowStepPersistenceService.findSteps(app.getId()),
                decisionPersistenceService.findDecisions(app.getId()),
                offerStatusRecordPersistenceService.findOffers(app.getId()),
                Math.max(0, Duration.between(app.getCreateTime(), end).toSeconds()));
    }

    private boolean sharedCandidate(String candidateId) {
        authorization.currentActor();
        return candidateId != null && candidateLibrary.eligibleCandidateIds(List.of(candidateId)).contains(candidateId);
    }

    private Application requireReadable(String id) {
        Application app = persistence.findApplication(id);
        if (app == null) throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        if (!sharedCandidate(app.getCandidateId())) authorize(app);
        return app;
    }

    private Application requireAuthorized(String id) {
        Application app = persistence.findApplication(id);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        authorize(app);
        return app;
    }

    private Application requireLockedAuthorized(String id) {
        lockCandidateForApplication(id);
        Application app = persistence.lockApplication(id);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        authorize(app);
        return app;
    }

    private WorkflowStep requireStep(String applicationId, String stepId) {
        WorkflowStep step = recruitmentWorkflowStepPersistenceService.findStep(applicationId, stepId);
        if (step == null) {
            throw PlatformExceptions.notFound("WORKFLOW_STEP_NOT_FOUND", "流程步骤不存在");
        }
        return step;
    }

    private void authorize(Application app) {
        authorize(app, applicationCollaboratorPersistenceService.findCollaborators(app.getId()));
    }

    private void authorize(Application app, List<String> collaborators) {
        authorization.requireAccess(
                "recruitment.manage",
                new AuthorizationTarget(
                        "APPLICATION",
                        app.getId(),
                        app.getOwnerUserId(),
                        app.getOwnerDepartmentId(),
                        Set.copyOf(collaborators)));
    }

    private Set<String> accessibleApplicationIds(
            CurrentActorSnapshot actor, List<Application> applications) {
        Set<String> applicationIds = applications.stream()
                .map(Application::getId)
                .collect(Collectors.toSet());
        Map<String, Set<String>> collaborators =
                applicationCollaboratorPersistenceService.findCollaboratorsByApplicationIds(applicationIds).stream()
                        .collect(Collectors.groupingBy(
                                ApplicationCollaborator::getApplicationId,
                                Collectors.mapping(
                                        ApplicationCollaborator::getUserId,
                                        Collectors.toSet())));
        List<AuthorizationTarget> targets = applications.stream()
                .map(application -> new AuthorizationTarget(
                        "APPLICATION",
                        application.getId(),
                        application.getOwnerUserId(),
                        application.getOwnerDepartmentId(),
                        collaborators.getOrDefault(application.getId(), Set.of())))
                .toList();
        Set<String> visible = new LinkedHashSet<>(authorization.accessibleObjectIds(actor, "recruitment.manage", targets));
        Set<String> shared = candidateLibrary.eligibleCandidateIds(applications.stream()
                .map(Application::getCandidateId).filter(Objects::nonNull).distinct().toList());
        applications.stream().filter(app -> app.getCandidateId() != null && shared.contains(app.getCandidateId())).map(Application::getId).forEach(visible::add);
        return Set.copyOf(visible);
    }

    /**
     * 校验并创建授权招聘需求。
     */
    private void authorizeDemand(JdVersionSnapshot jd) {
        authorization.requireAccess(
                "demand.read",
                new AuthorizationTarget(
                        "JD_VERSION",
                        jd.getJdVersionId(),
                        jd.getOwnerUserId(),
                        jd.getOwnerDepartmentId(),
                        jd.getCollaboratorUserIds()));
    }

    private void requireProcessingAllowed(Application app) {
        requireProcessingAllowed(
                app.getOwnerUserId(),
                Set.copyOf(applicationCollaboratorPersistenceService.findCollaborators(app.getId())));
    }

    private void requireProcessingAllowed(String ownerUserId, Set<String> collaboratorUserIds) {
        if (!processingAllowed(ownerUserId, collaboratorUserIds)) {
            throw PlatformExceptions.forbidden(
                    "RECRUITMENT_PROCESSING_FORBIDDEN",
                    "仅归属 HR 或已添加的流程协作人可以处理；管理查看权限不包含代处理权限");
        }
    }

    private boolean processingAllowed(String ownerUserId, Set<String> collaboratorUserIds) {
        String actorId = authorization.currentActor().getUserId();
        return actorId.equals(ownerUserId)
                || (collaboratorUserIds != null && collaboratorUserIds.contains(actorId));
    }

    private void requireMatchingSubmission(Application app, String submissionId) {
        String id = CommonUtils.stripToNull(submissionId);
        if (id == null) {
            throw PlatformExceptions.unprocessable("CLIENT_SUBMISSION_REQUIRED", "甲方步骤必须精确绑定已发送的客户推荐");
        }
        var demand = demands.requireSubmissionContextForJdVersion(app.getJdVersionId());
        submissionValidator.requireSentAndMatching(
                id,
                app.getId(),
                app.getCandidateId(),
                demand.clientId(),
                app.getJdId(),
                app.getJdVersionId(),
                app.getResumeVersionId());
    }

    /**
     * 决定和 Offer 必须走专用命令，连同业务事实、状态投影和审计一起更新。
     */
    private static void requireGenericStepCommand(WorkflowStep step) {
        WorkflowStepCode code = workflowStepCode(step.getStepCode());
        if (code == WorkflowStepCode.DECISION || code == WorkflowStepCode.OFFER) {
            throw PlatformExceptions.unprocessable(
                    "WORKFLOW_STEP_DEDICATED_COMMAND_REQUIRED",
                    "人工决定和 Offer 步骤必须通过各自的业务操作完成或更正");
        }
    }

    private static boolean isClientStep(String code) {
        return workflowStepCode(code).isClientStep();
    }

    private static WorkflowStepCode workflowStepCode(String code) {
        try {
            return WorkflowStepCode.fromCode(code);
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.unprocessable("WORKFLOW_STEP_CODE_INVALID", "流程步骤编码无效");
        }
    }

    /**
     * 校验并获取打开。
     */
    private static void requireOpen(Application app) {
        if (app.getTerminalAt() != null) {
            throw PlatformExceptions.conflict(
                    "APPLICATION_TERMINATED", "招聘活动已终止并归档；请重新选择 JD 创建新流程");
        }
    }

    private void recordProgressIfLocked(String candidateId, String type, String reason) {
        var lock = engagementLocks.activeLock(candidateId);
        if (lock != null && lock.isCanContact()) {
            engagementLocks.recordProgress(candidateId, type, reason);
        }
    }

    /**
     * 校验并获取技术乐观锁版本号。
     */
    private static void requireVersion(Application app, long version) {
        if (app.getVersion() != version) {
            throw state();
        }
    }

    private static String stageFor(String code) {
        return WorkflowStepCode.DECISION.matches(code)
                ? ApplicationStage.DECISION.name()
                : WorkflowStepCode.OFFER.matches(code)
                        ? ApplicationStage.OFFER.name()
                        : ApplicationStage.IN_PROCESS.name();
    }

    private static Instant dueAt(String code, Instant now) {
        long hours =
                WorkflowStepCode.CANDIDATE_COMMUNICATION.matches(code)
                        ? 72
                        : (code.startsWith("INTERVIEW") || code.contains("AI_INTERVIEW"))
                                ? 24
                                : (code.startsWith("CLIENT"))
                                        ? 72
                                        : WorkflowStepCode.OFFER.matches(code) ? 72 : 24;
        return now.plus(hours, ChronoUnit.HOURS);
    }

    private static String terminationOutcome(
            String status, String terminationReason, String explicit) {
        if (!OfferStatus.TERMINATED.matches(status)) {
            return null;
        }
        OfferTerminationReason reason;
        try {
            reason = OfferTerminationReason.fromCode(CommonUtils.upperOrEmpty(terminationReason));
        } catch (IllegalArgumentException exception) {
            throw PlatformExceptions.unprocessable(
                    "OFFER_TERMINATION_REASON_REQUIRED", "终止 Offer 必须给出受控终止原因");
        }
        return switch (reason) {
            case CANDIDATE_DECLINED, EXPIRED, COMPANY_WITHDREW -> reason.defaultDecision().getCode();
            case OTHER -> {
                String value = CommonUtils.upperOrEmpty(explicit);
                if (!ApplicationDecision.isNegative(value)) {
                    throw PlatformExceptions.unprocessable("OFFER_TERMINATION_OUTCOME_REQUIRED", "OTHER 终止原因必须明确 Application 结果");
                }
                yield value;
            }
        };
    }

    private static String derivedIdempotencyKey(String rawKey, String step) {
        return Sha256Utils.hashUtf8(IdempotencyKey.fromHttpHeader(rawKey) + ":" + step);
    }

    private String reserveIdempotency(
            String actorId,
            String rawKey,
            String action,
            String requestFingerprint,
            String targetId,
            Instant now) {
        String key = IdempotencyKey.fromHttpHeader(rawKey);
        IdempotencyRecord existing = recruitmentIdempotencyPersistenceService.findIdempotency(actorId, key);
        if (existing != null) {
            return verifyIdempotency(existing, action, requestFingerprint);
        }
        if (persistence.insertIdempotency(actorId, key, action, requestFingerprint, targetId, now) == 1) {
            return null;
        }
        existing = recruitmentIdempotencyPersistenceService.findIdempotencyForUpdate(actorId, key);
        if (existing == null) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "幂等请求正在处理中，请稍后重试");
        }
        return verifyIdempotency(existing, action, requestFingerprint);
    }

    private static String verifyIdempotency(
            IdempotencyRecord existing, String action, String requestFingerprint) {
        if (!action.equals(existing.getActionCode())
                || !requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同招聘命令");
        }
        return existing.getTargetId();
    }

    private static String fingerprint(Object... values) {
        return Sha256Utils.fingerprintObjects(31, values);
    }

    private void requireAvailableHeadcount(JdVersionSnapshot jd) {
        if (jd.getHeadcount() == null) {
            throw PlatformExceptions.conflict(
                    "JD_HEADCOUNT_REQUIRED", "该 JD 尚未填写 HC，请先到 JD 编辑页补充后再新增招聘流程");
        }
        int occupied = offerStatusRecordPersistenceService.countOccupiedByJd(jd.getJdId());
        if (occupied >= jd.getHeadcount()) {
            throw PlatformExceptions.conflict(
                    "JD_HEADCOUNT_FULL", "该 JD 的 HC 已满，请先到 JD 编辑页调整 HC 后再继续");
        }
    }

    /** HC 变更统一先锁 JD、再锁 Application，避免同一 JD 的并发入职形成交叉锁。 */
    private Application requireHeadcountLockedApplication(String applicationId) {
        lockCandidateForApplication(applicationId);
        String jdVersionId = persistence.findJdVersionId(applicationId);
        if (jdVersionId == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        demands.lockJdForHeadcount(jdVersionId);
        return requireLockedProcessingApplicationAfterCandidate(applicationId);
    }

    /** Application 锁后用同一当前协作人集合判断对象访问与处理权限。 */
    private Application requireLockedProcessingApplication(String applicationId) {
        lockCandidateForApplication(applicationId);
        return requireLockedProcessingApplicationAfterCandidate(applicationId);
    }

    private void lockCandidateForApplication(String applicationId) {
        String candidateId = persistence.findCandidateId(applicationId);
        if (candidateId == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        blacklist.lockCandidateForCommand(candidateId);
    }

    private Application requireLockedProcessingApplicationAfterCandidate(String applicationId) {
        Application app = persistence.lockApplication(applicationId);
        if (app == null) {
            throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
        }
        List<String> collaboratorIds = applicationCollaboratorPersistenceService.findCollaboratorsForUpdate(app.getId());
        authorize(app, collaboratorIds);
        requireProcessingAllowed(app.getOwnerUserId(), Set.copyOf(collaboratorIds));
        return app;
    }

    private static boolean occupiesHeadcount(OfferStatus status) {
        return status == OfferStatus.ONBOARDED;
    }

    private static PlatformException state() {
        return PlatformExceptions.conflict("APPLICATION_STATE_CHANGED", "招聘活动状态已变化，请刷新后重试");
    }

    public String resourceType() {
        return "APPLICATION";
    }

    public List<HandoverResource> preview(String sourceUserId) {
        return persistence.findApplications(null, null, sourceUserId, null, null, "ALL", null, null, null, null, null, 101).stream()
                .filter(a -> a.getTerminalAt() == null)
                .map(
                        a ->
                                new HandoverResource(
                                        resourceType(), a.getId(), "Application " + a.getId(), true, true, null))
                .toList();
    }

    @Transactional
    public HandoverTransferResult transfer(
            String resourceId,
            String sourceUserId,
            String targetUserId,
            String actorUserId,
            String reason) {
        Application app = persistence.lockApplication(resourceId);
        if (app == null || app.getTerminalAt() != null || !sourceUserId.equals(app.getOwnerUserId())) {
            return HandoverTransferResult.failure("APPLICATION_NOT_TRANSFERABLE", "招聘活动不存在、已终止或负责人已变化");
        }
        var target = directory.activeUser(targetUserId);
        Instant now = clock.instant();
        if (persistence.updateOwner(
                app.getId(),
                target.userId(),
                target.homeDepartmentId(),
                app.getVersion(),
                actorUserId,
                now)
                == 0) {
            return HandoverTransferResult.failure("APPLICATION_STATE_CHANGED", "招聘活动状态已变化");
        }
        String historyId = ids.newId();
        // 负责人已更新后，历史写入失败必须抛异常回滚，不能返回交接失败结果后提交事务。
        requireSingleWrite(recruitmentOwnershipHistoryPersistenceService.insertOwnership(
                historyId,
                app.getId(),
                "HANDOVER",
                sourceUserId,
                target.userId(),
                required(reason, "reason", 500),
                actorUserId,
                now), "Application 负责人历史写入失败");
        return HandoverTransferResult.success(historyId);
    }

  private String activitySource(String applicationId, String sourceType, String sourceId) {
    if (ActivityRequestContext.isPresent()) activityOrigins.record(applicationId, sourceType, sourceId);
    return sourceId;
  }
}

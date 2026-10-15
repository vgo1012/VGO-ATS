package com.ezaigc.hr.recruiting.interview.application.manager;

import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.activity.application.support.ActivityRequestContext;
import static com.ezaigc.hr.common.web.request.RequiredIdentifier.require;
import static com.ezaigc.hr.common.web.request.RequiredText.required;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_PARTICIPANTS_PER_INTERVIEW;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_MY_INTERVIEW_TASKS;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_FEEDBACK_VERSIONS_PER_BATCH;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarReconciliationDTO;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewJobOptionResponse;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.DeliveryResult;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.NotificationMessage;
import com.ezaigc.hr.recruiting.interview.domain.dto.CreateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewFeedbackDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.RescheduleInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.UpdateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackView;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewDetails;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewTask;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewDetailsResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewScheduleHistoryResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackBatchResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewNotificationResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewTaskResponse;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackKind;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackVerdict;
import com.ezaigc.hr.recruiting.interview.enums.InterviewMode;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationChannel;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationAttemptStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationType;
import com.ezaigc.hr.recruiting.interview.enums.InterviewStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewType;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationAttemptPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ScheduleHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionInterviewValidationAdapter;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import java.util.stream.Collectors;

/** 面试应用服务，负责用例编排、权限校验和事务边界。 */
@Service
@Transactional
public class InterviewApplicationManager {
  @Resource private ActivityOriginRecorder activityOrigins;


  private static final Logger LOGGER = LoggerFactory.getLogger(InterviewApplicationManager.class);
  private static final Duration EXTERNAL_DELIVERY_LEASE = Duration.ofMinutes(5);
  private static final int EXTERNAL_MESSAGE_ID_MAX_LENGTH = 200;
  private static final int EXTERNAL_ERROR_CODE_MAX_LENGTH = 80;
  private static final int EXTERNAL_ERROR_DETAIL_MAX_LENGTH = 500;
  private static final String DELIVERY_EXCEPTION_CODE = "DELIVERY_EXCEPTION";
  private static final String DELIVERY_EXCEPTION_DETAIL = "外部通知渠道调用异常";
  private static final String DELIVERY_RESULT_INVALID_CODE = "DELIVERY_RESULT_INVALID";
  private static final String DELIVERY_RESULT_INVALID_DETAIL = "外部通知渠道未返回有效结果";
  private static final String DELIVERY_REJECTED_CODE = "DELIVERY_REJECTED";
  private static final String DELIVERY_REJECTED_DETAIL = "外部通知渠道未确认发送";

  @Resource private InterviewAssembler assembler;

  public InterviewDetailsResponse create(CreateInterviewDTO command, String key) {
    return interviewDetailsView(createDetails(command, key));
  }

  /** 列出响应列表。 */
  @Transactional(readOnly = true)
  public CursorPage<InterviewResponse> listResponses(
      String jdId,
      String jdVersionId,
      String stepCode,
      Integer roundNo,
      String ownerUserId,
      String status,
      String result,
      Instant from,
      Instant to,
      String cursor,
      Integer limit) {
    return listResponses(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to, cursor, limit, null, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewResponse> listResponses(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor, Integer limit,
      String interviewType, String clientId) {
    return listResponses(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to,
        cursor, limit, interviewType, clientId, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewResponse> listResponses(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor, Integer limit,
      String interviewType, String clientId, String applicationId) {
    return listResponses(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to,
        cursor, limit, interviewType, clientId, applicationId, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewResponse> listResponses(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor, Integer limit,
      String interviewType, String clientId, String applicationId, String workflowStepId) {
    var page = list(jdId,jdVersionId,stepCode,roundNo,ownerUserId,status,result,from,to,cursor,limit,interviewType,clientId,
        applicationId, workflowStepId);
    if (page.getItems().isEmpty()) return page.map(assembler::toResponse);
    var summaries = demands.clientSummariesForVersions(page.getItems().stream()
        .map(Interview::getJdVersionId).collect(Collectors.toSet()));
    var names = candidates.namesForAuthorizedCandidates(page.getItems().stream()
        .map(Interview::getCandidateId).collect(Collectors.toSet()));
    return page.map(interview -> {
      var response = interviewView(interview, summaries.get(interview.getJdVersionId()));
      response.setCandidateName(names.get(interview.getCandidateId()));
      return response;
    });
  }

  private InterviewDetailsResponse interviewDetailsView(InterviewDetails details) {
    var response = assembler.toResponse(details);
    if (externalCalendars != null) {
      response.setExternalCalendar(externalCalendars.response(details.getInterview().getId()));
    }
    if (invitationOnly(authorization.currentActor())) response.getInterview().setInitialComment(null);
    var jd = demands.requireJdVersion(details.getInterview().getJdVersionId());
    if (jd != null) { response.getInterview().setClientId(jd.getClientId()); response.getInterview().setClientName(jd.getClientName()); }
    return response;
  }

  private InterviewResponse interviewView(Interview interview, JdClientSummary client) {
    var response = assembler.toResponse(interview);
    if (invitationOnly(authorization.currentActor())) response.setInitialComment(null);
    if (client != null) {
      response.setClientId(client.getClientId()); response.setClientName(client.getClientName());
      response.setJdTitle(client.getJdTitle()); response.setJdCode(client.getJdCode());
    }
    return response;
  }

  @Transactional(readOnly = true)
  public InterviewDetailsResponse getResponse(String id) {
    return interviewDetailsView(get(id));
  }

  /** 更新指定业务记录。 */
  public InterviewDetailsResponse update(String id, UpdateInterviewDTO command, String key) {
    return interviewDetailsView(updateDetails(id, command, key));
  }

  public InterviewDetailsResponse reschedule(
      String id, RescheduleInterviewDTO command, String key) {
    return interviewDetailsView(rescheduleDetails(id, command, key));
  }

  public InterviewDetailsResponse cancelResponse(
      String id, long version, String reason, String key) {
    return interviewDetailsView(cancel(id, version, reason, key));
  }

  /** 保存反馈。 */
  public InterviewFeedbackResponse saveFeedback(
      String id, InterviewFeedbackDTO command, String key) {
    return assembler.toResponse(saveFeedbackView(id, command, key));
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewFeedbackResponse> feedbackResponses(String id, String cursor, Integer limit) {
    return feedback(id, cursor, limit).map(assembler::toResponse);
  }

  /** 批量分页读取多个面试的可见面评。 */
  @Transactional(readOnly = true)
  public InterviewFeedbackBatchResponse feedbackBatchResponses(
      List<String> interviewIds, String cursor, Integer requestedLimit) {
    if (interviewIds == null || interviewIds.isEmpty() || interviewIds.size() > PageLimits.MAXIMUM) {
      throw PlatformExceptions.badRequest("INTERVIEW_IDS_INVALID", "面试 ID 数量必须为 1 至 100 个");
    }
    List<String> requestedIds = List.copyOf(new TreeSet<>(interviewIds.stream()
        .map(id -> require(id, "interviewIds"))
        .toList()));
    CurrentActorSnapshot actor = authorization.currentActor();
    List<Interview> loaded = persistence.listByIds(requestedIds);
    Map<String, Interview> interviewsById = loaded.stream()
        .filter(value -> requestedIds.contains(value.getId()))
        .collect(Collectors.toMap(Interview::getId, value -> value, (left, right) -> left));
    List<Interview> orderedInterviews = requestedIds.stream()
        .map(interviewsById::get)
        .filter(Objects::nonNull)
        .toList();
    Set<String> visibleIds = orderedInterviews.isEmpty()
        ? Set.of()
        : visibleInterviewIds(actor, orderedInterviews);
    List<String> orderedVisibleIds = requestedIds.stream().filter(visibleIds::contains).toList();
    String scope = CursorScopes.scope(
        "interview.feedback-batch",
        requestedIds,
        actor.getUserId(),
        actor.hasCompanyWideScope(),
        actor.getOperationalManagedUserIds(),
        actor.visibleDepartmentIds(),
        actor.getPermissions(),
        orderedVisibleIds);
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    int limit = PageLimits.resolve(requestedLimit);
    Set<String> sharedIds = sharedInterviewIds(orderedInterviews);
    List<Feedback> rows = orderedVisibleIds.isEmpty()
        ? List.of()
        : sharedIds.isEmpty() ? interviewFeedbackPersistenceService.findVisibleFeedbackByInterviewIds(
            Set.copyOf(orderedVisibleIds), actor.getUserId(),
            decoded == null ? null : decoded.getSortTime(),
            decoded == null ? null : decoded.getId(), limit + 1)
        : interviewFeedbackPersistenceService.findVisibleFeedbackByInterviewIds(
            Set.copyOf(orderedVisibleIds), actor.getUserId(), sharedIds,
            decoded == null ? null : decoded.getSortTime(),
            decoded == null ? null : decoded.getId(), limit + 1);
    CursorPage<Feedback> page = CursorPages.page(
        rows, limit, cursorCodec, scope, Feedback::getCreateTime, Feedback::getId);
    List<InterviewFeedbackResponse> items = visibleFeedback(page.getItems()).stream()
        .map(assembler::toResponse)
        .toList();
    return new InterviewFeedbackBatchResponse(
        orderedVisibleIds, items, page.getNextCursor(), page.isHasMore());
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewScheduleHistoryResponse> historyResponses(String id, String cursor, Integer requestedLimit) {
    Interview interview = requireVisible(id);
    int limit = PageLimits.resolve(requestedLimit);
    String scope = historyScope("schedule-history", interview.getId());
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    List<ScheduleHistory> rows = scheduleHistoryPersistenceService.findHistory(interview.getId(),
        decoded == null ? null : decoded.getSortTime(), decoded == null ? null : decoded.getId(), limit + 1);
    return CursorPages.page(rows, limit, cursorCodec, scope, ScheduleHistory::getOccurredAt, ScheduleHistory::getId)
        .map(assembler::toResponse);
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewNotificationResponse> notificationResponses(String id, String cursor, Integer requestedLimit) {
    Interview interview = requireVisible(id);
    int limit = PageLimits.resolve(requestedLimit);
    String scope = historyScope("notifications", interview.getId());
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    List<NotificationIntent> rows = notificationIntentPersistenceService.findNotifications(interview.getId(),
        decoded == null ? null : decoded.getSortTime(), decoded == null ? null : decoded.getId(), limit + 1);
    return CursorPages.page(rows, limit, cursorCodec, scope, NotificationIntent::getCreateTime, NotificationIntent::getId)
        .map(assembler::toResponse);
  }

  /** 记录人工发送响应。 */
  public InterviewNotificationResponse recordManualSendResponse(
      String id, String channel, String externalId, Instant sentAt, String key) {
    return assembler.toResponse(recordManualSend(id, channel, externalId, sentAt, key));
  }

  /** 重试通知响应。 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public InterviewNotificationResponse retryNotificationResponse(String id, String key) {
    return assembler.toResponse(retryNotification(id, key));
  }

  @Transactional(readOnly = true)
  public List<InterviewTaskResponse> myTaskResponses(int limit) {
    var responses = assembler.toTaskResponses(myTasks(limit));
    if (responses.isEmpty()) return responses;
    var names = candidates.namesForAuthorizedCandidates(responses.stream()
        .map(InterviewTaskResponse::getCandidateId).collect(Collectors.toSet()));
    responses.forEach(response -> response.setCandidateName(names.get(response.getCandidateId())));
    return responses;
  }

  @Resource private Clock clock;
  @Resource private DemandQueryService demands;
  @Resource private CandidateDirectoryService candidates;
  @Resource private PlatformIdGenerator ids;

  @Resource private InterviewPersistenceService persistence;
  @Resource private FeedbackVersionPersistenceService feedbackVersionPersistenceService;
  @Resource private InterviewFeedbackPersistenceService interviewFeedbackPersistenceService;
  @Resource private InterviewIdempotencyPersistenceService interviewIdempotencyPersistenceService;
  @Resource private NotificationAttemptPersistenceService notificationAttemptPersistenceService;
  @Resource private NotificationIntentPersistenceService notificationIntentPersistenceService;
  @Resource private ParticipantPersistenceService participantPersistenceService;
  @Resource private ScheduleHistoryPersistenceService scheduleHistoryPersistenceService;
  @Resource private AuthorizationService authorization;
  @Resource private CandidateLibraryQueryService candidateLibrary;
  @Resource private IdentityDirectoryService directory;
  @Resource private RecruitmentApplicationManager applications;
  @Resource private CompanyBlacklistQueryService blacklist;
  @Resource private ApplicationPersistenceService applicationPersistenceService;
  @Resource private ClientSubmissionInterviewValidationAdapter submissionValidator;
  @Resource private NotificationDeliveryPort delivery;
  @Resource private EngagementLockApplicationManager engagementLocks;
  @Resource private CursorCodec cursorCodec;
  @Resource private TransactionTemplate transactionTemplate;
  @Resource private InterviewExternalCalendarApplicationManager externalCalendars;

  /** 创建详情。 */
  @Transactional
  public InterviewDetails createDetails(CreateInterviewDTO command, String rawKey) {
    authorization.requirePermission("interview.schedule");
    var context =
        applications.requireInterviewContext(
            require(command.getApplicationId(), "applicationId"),
            require(command.getWorkflowStepId(), "workflowStepId"));
    var actor = authorization.currentActor();
    String interviewId = ids.newId();
    String request =
        fingerprint(
            context.applicationId(),
            context.workflowStepId(),
            command.getStartAt(),
            command.getEndAt(),
            command.getTimezoneId(),
            command.getMeetingMode(),
            command.getLocationText(),
            command.getMeetingUrl(),
            command.getParticipantUserIds(),
            command.getClientSubmissionId(),
            command.getReason(),
            command.getInterviewType(),
            command.getInitialComment(),
            command.getExternalCalendar());
    blacklist.lockCandidateForCommand(context.candidateId());
    String replay = reserve(actor.getUserId(), rawKey, "INTERVIEW_CREATE", request, interviewId);
    if (replay != null) {
      return details(requireVisible(replay));
    }
    blacklist.requireRecruitmentAllowed(context.candidateId());
    validateSchedule(
        command.getStartAt(),
        command.getEndAt(),
        command.getTimezoneId(),
        command.getMeetingMode(),
        command.getLocationText(),
        command.getMeetingUrl());
    if (persistence.countActiveByStep(context.workflowStepId()) > 0) {
      throw PlatformExceptions.conflict("ACTIVE_INTERVIEW_EXISTS", "该实际流程步骤已有待进行面试，请改期而不是重复创建");
    }
    String type = CommonUtils.stripToNull(command.getInterviewType());
    if (type == null) {
      type = interviewType(context.recruitmentType(), context.stepCode());
    } else if (!InterviewType.INTERNAL.matches(type) && !InterviewType.EXTERNAL.matches(type)) {
      throw PlatformExceptions.unprocessable("INTERVIEW_TYPE_INVALID", "面试类型必须为内部面试或外部面试");
    }
    if (!context.stepCode().startsWith("INTERVIEW_ROUND_")
        && !type.equals(interviewType(context.recruitmentType(), context.stepCode()))) {
      throw PlatformExceptions.unprocessable("INTERVIEW_TYPE_INVALID", "历史专用面试步骤不能通过标签改变业务类型");
    }
    String initialComment = optional(command.getInitialComment(), "initialComment", 1000);
    String submission = CommonUtils.stripToNull(command.getClientSubmissionId());
    if (submission != null && (InterviewType.EXTERNAL_CLIENT.matches(type) || InterviewType.EXTERNAL_CLIENT_AI.matches(type))) {
      submissionValidator.requireSentAndMatching(
          submission, context.applicationId(), context.jdId(), context.jdVersionId());
      context = applications.bindClientInterviewStep(context.applicationId(), context.workflowStepId(), submission);
    } else if (submission != null) {
      throw PlatformExceptions.unprocessable("CLIENT_SUBMISSION_NOT_ALLOWED", "内部面试不得绑定客户推荐");
    } else {
      context = applications.requireLockedInterviewContext(context.applicationId(), context.workflowStepId());
    }
    // 所有面试类型都在 Application/步骤锁内复查，避免并发排期、步骤完成或负责人转交后的旧快照写入。
    if (persistence.lockScheduledByStep(context.workflowStepId()) != null) {
      throw PlatformExceptions.conflict("ACTIVE_INTERVIEW_EXISTS", "该实际流程步骤已有待进行面试，请改期而不是重复创建");
    }
    List<String> participants = participants(command.getParticipantUserIds());
    if (InterviewType.INTERNAL.matches(type) && participants.isEmpty()) {
      throw PlatformExceptions.unprocessable("INTERVIEWER_REQUIRED", "内部面试至少选择一名面试官");
    }
    directory.activeUsers(Set.copyOf(participants));
    String mode = CommonUtils.upperOrEmpty(command.getMeetingMode());
    Instant now = clock.instant();
    persistence.insertInterview(
        interviewId,
        context.applicationId(),
        context.candidateId(),
        context.jdId(),
        context.jdVersionId(),
        context.workflowTemplateVersionId(),
        context.workflowStepId(),
        context.stepCode(),
        type,
        Math.max(roundNo(context.stepCode()), applications.interviewRoundNumber(context.applicationId(), context.workflowStepId(), roundNo(context.stepCode()))),
        submission,
        command.getStartAt(),
        command.getEndAt(),
        command.getTimezoneId(),
        mode,
        CommonUtils.stripToNull(command.getLocationText()),
        CommonUtils.stripToNull(command.getMeetingUrl()),
        "NONE",
        context.ownerUserId(),
        context.ownerDepartmentId(),
        context.ownerUserId(),
        actor.getUserId(),
        now);
    if (!participantPersistenceService.insertParticipants(
        interviewId, participants, "INTERVIEWER", now)) {
      throw new IllegalStateException("面试参与人批量写入失败");
    }
    Interview saved = persistence.findInterview(interviewId);
    if (initialComment != null) {
      saved.setInitialComment(initialComment);
      if (!persistence.updateById(saved)) {
        throw new IllegalStateException("排期面评文字保存失败");
      }
    }
    if (persistence.insertHistory(
                activitySource(saved.getApplicationId(), "INTERVIEW_SCHEDULE", ids.newId()),
        interviewId,
        "CREATE",
        null,
        snapshot(saved),
        Objects.requireNonNullElse(optional(command.getReason(), "reason", 500), "未填写安排说明"),
        actor.getUserId(),
        now) != 1) {
      throw new IllegalStateException("面试排期历史写入失败");
    }
    recordProgress(saved.getCandidateId(), "INTERVIEW_SCHEDULED", "已安排面试");
    if (externalCalendars != null) {
      externalCalendars.configure(saved, participants, command.getExternalCalendar(), actor.getUserId());
    }
    return details(saved);
  }

  @Transactional(readOnly = true)
  public CursorPage<Interview> list(String jdId, String jdVersionId, String stepCode,
      Integer roundNo, String ownerUserId, String status, String result, String cursor, Integer limit) {
    return list(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, null, null, cursor, limit);
  }

  @Transactional(readOnly = true)
  public CursorPage<Interview> list(
      String jdId,
      String jdVersionId,
      String stepCode,
      Integer roundNo,
      String ownerUserId,
      String status,
      String result,
      Instant from,
      Instant to,
      String cursor,
      Integer requestedLimit) {
    return list(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to, cursor, requestedLimit, null, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<Interview> list(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor,
      Integer requestedLimit, String interviewType, String clientId) {
    return list(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to, cursor,
        requestedLimit, interviewType, clientId, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<Interview> list(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor,
      Integer requestedLimit, String interviewType, String clientId, String applicationId) {
    return list(jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to, cursor,
        requestedLimit, interviewType, clientId, applicationId, null);
  }

  @Transactional(readOnly = true)
  public CursorPage<Interview> list(String jdId, String jdVersionId, String stepCode, Integer roundNo,
      String ownerUserId, String status, String result, Instant from, Instant to, String cursor,
      Integer requestedLimit, String interviewType, String clientId, String applicationId, String workflowStepId) {
    if (interviewType != null && !InterviewType.supportsFilter(interviewType))
      throw PlatformExceptions.unprocessable("INTERVIEW_TYPE_INVALID", "面试类型无效");
    var actor = authorization.currentActor();
    if (from != null && to != null && !from.isBefore(to)) {
      throw PlatformExceptions.badRequest("INTERVIEW_TIME_RANGE_INVALID", "开始日期必须早于结束日期");
    }
    int limit = PageLimits.resolve(requestedLimit);
    String normalizedJdId = CommonUtils.stripToNull(jdId);
    String normalizedJdVersionId = CommonUtils.stripToNull(jdVersionId);
    String normalizedStep = CommonUtils.stripToNull(stepCode);
    String normalizedOwner = CommonUtils.stripToNull(ownerUserId);
    String normalizedStatus = CommonUtils.stripToNull(status);
    String normalizedResult = CommonUtils.stripToNull(result);
    String normalizedApplicationId = CommonUtils.stripToNull(applicationId);
    String normalizedWorkflowStepId = CommonUtils.stripToNull(workflowStepId);
    String scope = CursorScopes.scope(
        "interview.list",
        actor.getUserId(),
        actor.hasCompanyWideScope(),
        actor.getOperationalManagedUserIds(),
        actor.visibleDepartmentIds(),
        actor.getPermissions(),
        normalizedJdId,
        normalizedJdVersionId,
        normalizedStep,
        roundNo,
        normalizedOwner,
        normalizedStatus,
        normalizedResult, from, to, interviewType, clientId, normalizedApplicationId, normalizedWorkflowStepId);
    return VisibleCursorPageCollector.collect(
        StableCursor.decode(cursorCodec, scope, cursor),
        limit,
        200,
        cursorCodec,
        scope,
        scan ->
            persistence.findInterviews(
                normalizedJdId,
                normalizedJdVersionId,
                normalizedStep,
                roundNo,
                normalizedOwner,
                normalizedStatus,
                normalizedResult,
                normalizedApplicationId,
                from,
                to,
                scan == null ? null : scan.getSortTime(),
                scan == null ? null : scan.getId(),
                normalizedWorkflowStepId,
                interviewType,
                clientId,
                200),
        values -> visibleInterviewIds(actor.operationalScope(), values),
        Interview::getScheduledStartAt,
        Interview::getId);
  }

  @Transactional(readOnly = true)
  public InterviewDetails get(String id) {
    return details(requireVisible(id));
  }

  /** 更新详情。 */
  @Transactional
  public InterviewDetails updateDetails(String id, UpdateInterviewDTO command, String rawKey) {
    Interview before = requireLockedInterview(id);
    requireScheduleAccess(before);
    String clean = Objects.requireNonNullElse(optional(command.getReason(), "reason", 500), "未填写安排说明");
    var actor = authorization.currentActor();
    String replay =
        reserve(
            actor.getUserId(),
            rawKey,
            "INTERVIEW_UPDATE",
            fingerprint(
                id,
                command.getStartAt(),
                command.getEndAt(),
                command.getTimezoneId(),
                command.getMeetingMode(),
                command.getLocationText(),
                command.getMeetingUrl(),
                command.getParticipantUserIds(),
                command.getCandidateNotificationChannel(),
                command.getNextResponsibleUserId(),
                command.getVersion(),
                clean,
                command.getExternalCalendar()),
            id);
    if (replay != null) {
      return details(requireVisible(replay));
    }
    requireScheduled(before);
    blacklist.requireRecruitmentAllowed(before.getCandidateId());
    validateSchedule(
        command.getStartAt(),
        command.getEndAt(),
        command.getTimezoneId(),
        command.getMeetingMode(),
        command.getLocationText(),
        command.getMeetingUrl());
    List<String> people = participants(command.getParticipantUserIds());
    if (InterviewType.INTERNAL.matches(before.getInterviewType()) && people.isEmpty()) {
      throw PlatformExceptions.unprocessable("INTERVIEWER_REQUIRED", "内部面试至少选择一名面试官");
    }
    String next = require(command.getNextResponsibleUserId(), "nextResponsibleUserId");
    Set<String> usersToValidate = new LinkedHashSet<>(people);
    usersToValidate.add(next);
    directory.activeUsers(usersToValidate);
    Instant now = clock.instant();
    if (persistence.updateSchedule(
            before.getId(),
            command.getStartAt(),
            command.getEndAt(),
            command.getTimezoneId(),
            CommonUtils.upperOrEmpty(command.getMeetingMode()),
            CommonUtils.stripToNull(command.getLocationText()),
            CommonUtils.stripToNull(command.getMeetingUrl()),
            CommonUtils.upperOrEmpty(command.getCandidateNotificationChannel()),
            next,
            command.getVersion(),
            actor.getUserId(),
            now)
        == 0) {
      throw state();
    }
    participantPersistenceService.deleteParticipants(before.getId());
    if (!participantPersistenceService.insertParticipants(
        before.getId(), people, "INTERVIEWER", now)) {
      throw new IllegalStateException("面试参与人批量写入失败");
    }
    Interview after = persistence.findInterview(before.getId());
    if (persistence.insertHistory(
                activitySource(after.getApplicationId(), "INTERVIEW_SCHEDULE", ids.newId()),
        after.getId(),
        "UPDATE",
        snapshot(before),
        snapshot(after),
        clean,
        actor.getUserId(),
        now) != 1) {
      throw new IllegalStateException("面试排期历史写入失败");
    }
    if (externalCalendars != null) {
      externalCalendars.configure(after, people, command.getExternalCalendar(), actor.getUserId());
    }
    return details(after);
  }

  @Transactional
  public InterviewDetails rescheduleDetails(
      String id, RescheduleInterviewDTO command, String rawKey) {
    Interview before = requireLockedInterview(id);
    requireScheduleAccess(before);
    String clean = Objects.requireNonNullElse(optional(command.getReason(), "reason", 500), "未填写安排说明");
    var actor = authorization.currentActor();
    String replay =
        reserve(
            actor.getUserId(),
            rawKey,
            "INTERVIEW_RESCHEDULE",
            fingerprint(
                id,
                command.getStartAt(),
                command.getEndAt(),
                command.getTimezoneId(),
                command.getVersion(),
                clean),
            id);
    if (replay != null) {
      return details(requireVisible(replay));
    }
    requireScheduled(before);
    blacklist.requireRecruitmentAllowed(before.getCandidateId());
    validateSchedule(
        command.getStartAt(),
        command.getEndAt(),
        command.getTimezoneId(),
        before.getMeetingMode(),
        before.getLocationText(),
        before.getMeetingUrl());
    Instant now = clock.instant();
    if (persistence.updateSchedule(
            before.getId(),
            command.getStartAt(),
            command.getEndAt(),
            command.getTimezoneId(),
            before.getMeetingMode(),
            before.getLocationText(),
            before.getMeetingUrl(),
            before.getCandidateNotificationChannel(),
            before.getNextResponsibleUserId(),
            command.getVersion(),
            actor.getUserId(),
            now)
        == 0) {
      throw state();
    }
    Interview after = persistence.findInterview(before.getId());
    if (persistence.insertHistory(
                activitySource(after.getApplicationId(), "INTERVIEW_SCHEDULE", ids.newId()),
        after.getId(),
        "RESCHEDULE",
        snapshot(before),
        snapshot(after),
        clean,
        actor.getUserId(),
        now) != 1) {
      throw new IllegalStateException("面试排期历史写入失败");
    }
    recordProgress(after.getCandidateId(), "INTERVIEW_SCHEDULED", "面试已改期");
    if (externalCalendars != null) {
      externalCalendars.configure(
          after,
          participantPersistenceService.findParticipants(after.getId()).stream()
              .map(Participant::getUserId).toList(),
          null,
          actor.getUserId());
    }
    return details(after);
  }

  @Transactional
  public InterviewDetails cancel(String id, long version, String reason, String rawKey) {
    Interview observed = persistence.findInterview(id);
    if (observed == null) {
      throw PlatformExceptions.notFound("INTERVIEW_NOT_FOUND", "面试不存在");
    }
    requireScheduleAccess(observed);
    // 状态更新会修改申请关联索引；与撤回按活动→面试加锁，并在锁后复查权限和状态。
    applications.requireLockedFollowUpCancellation(observed.getApplicationId());
    Interview before = requireLockedInterview(id);
    requireScheduleAccess(before);
    String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), "HR 取消面试安排");
    var actor = authorization.currentActor();
    String replay =
        reserve(actor.getUserId(), rawKey, "INTERVIEW_CANCEL", fingerprint(id, version, clean), id);
    if (replay != null) {
      return details(requireVisible(replay));
    }
    requireScheduled(before);
    Instant now = clock.instant();
    if (persistence.cancel(before.getId(), version, actor.getUserId(), now) == 0) {
      throw state();
    }
    Interview after = persistence.findInterview(before.getId());
    if (persistence.insertHistory(
                activitySource(after.getApplicationId(), "INTERVIEW_SCHEDULE", ids.newId()),
        after.getId(),
        "CANCEL",
        snapshot(before),
        snapshot(after),
        clean,
        actor.getUserId(),
        now) != 1) {
      throw new IllegalStateException("面试排期历史写入失败");
    }
    if (externalCalendars != null) {
      externalCalendars.cancel(after.getId(), actor.getUserId());
    }
    return details(after);
  }

  @Transactional
  public InterviewDetailsResponse retryExternalCalendar(String id) {
    Interview interview = requireLockedInterview(id);
    requireScheduleAccess(interview);
    if (!InterviewStatus.CANCELLED.matches(interview.getStatus())) {
      blacklist.requireRecruitmentAllowed(interview.getCandidateId());
    }
    externalCalendars.retry(interview.getId(), authorization.currentActor().getUserId());
    return interviewDetailsView(details(interview));
  }

  /** 远端 ID 无法可靠证明归属时，仅管理员在面试对象授权内核对恢复。 */
  @Transactional
  public InterviewDetailsResponse reconcileExternalCalendar(String id, InterviewExternalCalendarReconciliationDTO command) {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) {
      throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
    }
    Interview interview = requireLockedInterview(id);
    requireScheduleAccess(interview);
    if (!InterviewStatus.CANCELLED.matches(interview.getStatus())) {
      blacklist.requireRecruitmentAllowed(interview.getCandidateId());
    }
    externalCalendars.reconcile(interview.getId(), command, actor.getUserId());
    return interviewDetailsView(details(interview));
  }

  /** 保存反馈查看。 */
  @Transactional
  public FeedbackView saveFeedbackView(
      String interviewId, InterviewFeedbackDTO command, String rawKey) {
    Interview observed = requireVisible(interviewId);
    blacklist.lockCandidateForCommand(observed.getCandidateId());
    // 活动关联不可修改；按活动→面试加锁，与撤回/结束流程一致，避免主键与二级索引锁反转。
    // 面试官名单仅记录参与事实；内部面评由当前招聘活动处理人录入。
    if (applicationPersistenceService.lockApplication(observed.getApplicationId()) == null) {
      throw PlatformExceptions.notFound("APPLICATION_NOT_FOUND", "招聘活动不存在");
    }
    Interview interview = requireLockedVisible(interviewId);
    String kind =
        (InterviewType.INTERNAL.matches(interview.getInterviewType())
                || InterviewType.EXTERNAL.matches(interview.getInterviewType()))
            ? InterviewFeedbackKind.INTERNAL.name()
            : InterviewFeedbackKind.EXTERNAL_RECORDED.name();
    var actor = authorization.currentActor();
    if (InterviewFeedbackKind.INTERNAL.matches(kind)) {
      applications.requireLockedFollowUpAccess(interview.getApplicationId());
    } else {
      authorization.requirePermission("interview.external-feedback.record");
      requireScope(interview);
      if (CommonUtils.stripToNull(command.getExternalFeedbackSource()) == null) {
        throw PlatformExceptions.unprocessable("EXTERNAL_FEEDBACK_SOURCE_REQUIRED", "甲方外部反馈代录必须填写实际来源");
      }
    }
    String verdict = CommonUtils.upperOrEmpty(command.getVerdict());
    if (!InterviewFeedbackVerdict.supports(verdict)) {
      throw PlatformExceptions.unprocessable("INTERVIEW_VERDICT_INVALID", "面评结果必须为 PASS 或 FAIL");
    }
    String status = CommonUtils.upperOrEmpty(command.getStatus());
    if (!InterviewFeedbackStatus.SUBMITTED.matches(status)) {
      throw PlatformExceptions.unprocessable("INTERVIEW_FEEDBACK_STATUS_INVALID", "面评只允许正式提交");
    }
    String reason = required(command.getChangeReason(), "changeReason", 500);
    Instant occurred =
        command.getFeedbackOccurredAt() == null ? clock.instant() : command.getFeedbackOccurredAt();
    if (occurred.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
      throw PlatformExceptions.unprocessable("INTERVIEW_FEEDBACK_TIME_INVALID", "反馈时间不能晚于当前时间");
    }
    Feedback existing =
        interviewFeedbackPersistenceService.findFeedbackByAuthor(interview.getId(), actor.getUserId(), kind);
    String feedbackId = existing == null ? ids.newId() : existing.getId();
    int nextVersion = existing == null ? 1 : existing.getCurrentVersionNo() + 1;
    String replay =
        reserve(
            actor.getUserId(),
            rawKey,
            "INTERVIEW_FEEDBACK_SAVE",
            fingerprint(
                interview.getId(),
                kind,
                verdict,
                CommonUtils.stripToNull(command.getShortComment()),
                CommonUtils.stripToNull(command.getExternalFeedbackSource()),
                command.getFeedbackOccurredAt(),
                status,
                reason),
            feedbackId);
    if (replay != null) {
      Feedback saved = interviewFeedbackPersistenceService.findFeedbackByAuthor(interview.getId(), actor.getUserId(), kind);
      return view(saved);
    }
    // 终态后仍允许有权调用者重放已成功命令；新请求不得写入，并由当前事务回滚幂等预留。
    if (InterviewStatus.CANCELLED.matches(interview.getStatus())) {
      throw PlatformExceptions.conflict("INTERVIEW_CANCELLED", "已取消面试不能提交面评");
    }
    if (InterviewStatus.COMPLETED.matches(interview.getStatus())) {
      throw PlatformExceptions.conflict("INTERVIEW_COMPLETED", "已完成面试不能新增或修改面评");
    }
    if (existing != null && InterviewFeedbackStatus.SUBMITTED.matches(existing.getStatus())) {
      throw PlatformExceptions.unprocessable("INTERVIEW_FEEDBACK_ALREADY_SUBMITTED",
          "面评已正式提交，不能再次修改");
    }
    // FAIL 正式面评也会完成面试并写招聘进度，属于新的非终态业务完成。
    blacklist.requireRecruitmentAllowed(interview.getCandidateId());
    Instant now = clock.instant();
    if (existing == null) {
      if (interviewFeedbackPersistenceService.insertFeedback(
              feedbackId, interview.getId(), actor.getUserId(), kind, status, now) != 1) {
        throw new IllegalStateException("面评记录写入失败");
      }
    } else if (interviewFeedbackPersistenceService.advanceFeedback(
            existing.getId(), status, existing.getCurrentVersionNo(), actor.getUserId(), now)
        == 0) {
      throw state();
    }
    if (feedbackVersionPersistenceService.insertFeedbackVersion(
                activitySource(interview.getApplicationId(), "INTERVIEW_FEEDBACK", ids.newId()),
        feedbackId,
        nextVersion,
        verdict,
        CommonUtils.stripToNull(command.getShortComment()),
        CommonUtils.stripToNull(command.getExternalFeedbackSource()),
        occurred,
        reason,
        actor.getUserId(),
        now) != 1) {
      throw new IllegalStateException("面评版本记录写入失败");
    }
    Feedback saved = interviewFeedbackPersistenceService.findFeedbackByAuthor(interview.getId(), actor.getUserId(), kind);
    if (InterviewStatus.SCHEDULED.matches(interview.getStatus())
        && InterviewFeedbackStatus.SUBMITTED.matches(status)) {
      if (persistence.complete(interview.getId(), actor.getUserId(), now) != 1) {
        throw state();
      }
      recordProgress(interview.getCandidateId(), "INTERVIEW_COMPLETED", "面试反馈已正式提交");
    }
    return view(saved);
  }

  @Transactional(readOnly = true)
  public CursorPage<FeedbackView> feedback(String interviewId, String cursor, Integer requestedLimit) {
    Interview interview = requireVisible(interviewId);
    int limit = PageLimits.resolve(requestedLimit);
    String scope = historyScope("feedback", interview.getId());
    StableCursor decoded = StableCursor.decode(cursorCodec, scope, cursor);
    List<Feedback> rows = sharedCandidate(interview.getCandidateId())
        ? interviewFeedbackPersistenceService.findVisibleFeedbackByInterviewIds(Set.of(interview.getId()),
            authorization.currentActor().getUserId(), Set.of(interview.getId()),
            decoded == null ? null : decoded.getSortTime(), decoded == null ? null : decoded.getId(), limit + 1)
        : interviewFeedbackPersistenceService.findVisibleFeedback(interview.getId(),
        authorization.currentActor().getUserId(), decoded == null ? null : decoded.getSortTime(),
        decoded == null ? null : decoded.getId(), limit + 1);
    CursorPage<Feedback> page = CursorPages.page(rows, limit, cursorCodec, scope, Feedback::getCreateTime, Feedback::getId);
    return new CursorPage<>(visibleFeedback(page.getItems()), page.getNextCursor(), page.isHasMore());
  }

  /** 记录人工发送。 */
  @Transactional
  public NotificationIntent recordManualSend(
      String notificationId, String channel, String externalId, Instant sentAt, String rawKey) {
    NotificationIntent intent = requireNotification(notificationId);
    Interview interview = requireLockedVisible(intent.getInterviewId());
    requireScheduleAccess(interview);
    String used = CommonUtils.upperOrEmpty(channel);
    if (!InterviewNotificationChannel.supports(used)) {
      throw PlatformExceptions.unprocessable("NOTIFICATION_CHANNEL_INVALID", "通知渠道不受支持");
    }
    var actor = authorization.currentActor();
    String replay =
        reserve(
            actor.getUserId(),
            rawKey,
            "NOTIFICATION_MANUAL_SEND",
            fingerprint(notificationId, used, CommonUtils.stripToNull(externalId), sentAt),
            notificationId);
    if (replay != null) {
      return notificationIntentPersistenceService.findNotification(replay);
    }
    requireNotificationApplicable(interview, intent);
    if (!InterviewNotificationType.INTERVIEW_CANCEL.matches(intent.getNotificationType())) {
      blacklist.requireRecruitmentAllowed(interview.getCandidateId());
    }
    Instant when = sentAt == null ? clock.instant() : sentAt;
    if (when.isAfter(clock.instant().plus(5, ChronoUnit.MINUTES))) {
      throw PlatformExceptions.unprocessable("NOTIFICATION_TIME_INVALID", "发送时间不能晚于当前时间");
    }
    if (notificationIntentPersistenceService.updateNotification(
            intent.getId(),
            used,
            InterviewNotificationStatus.SENT_MANUAL.getCode(),
            CommonUtils.stripToNull(externalId),
            null,
            null,
            0,
            intent.getVersion(),
            actor.getUserId(),
            when)
        == 0) {
      throw state();
    }
    if (notificationAttemptPersistenceService.insertNotificationAttempt(
            ids.newId(),
            intent.getId(),
            "MANUAL_SEND",
            used,
            InterviewNotificationAttemptStatus.SENT.getCode(),
            CommonUtils.stripToNull(externalId),
            null,
            null,
            actor.getUserId(),
            when)
        != 1) {
      throw new IllegalStateException("通知发送尝试记录写入失败");
    }
    return notificationIntentPersistenceService.findNotification(intent.getId());
  }

  /** 重试通知。 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public NotificationIntent retryNotification(String notificationId, String rawKey) {
    NotificationRetryPreparation preparation =
        requireTransactionResult(
            transactionTemplate.execute(
                ignored -> prepareNotificationRetry(notificationId, rawKey)));
    if (preparation.replayedIntent() != null) {
      return preparation.replayedIntent();
    }
    DeliveryResult result = deliverNotification(preparation);
    return requireTransactionResult(
        transactionTemplate.execute(
            ignored -> completeNotificationRetry(preparation, result)));
  }

  /**
   * 在短事务中锁定通知并持久化发送占用。事务提交后才允许调用外部渠道，避免网络等待期间占用连接和行锁。
   */
  private NotificationRetryPreparation prepareNotificationRetry(
      String notificationId, String rawKey) {
    lockCandidateForNotification(notificationId);
    NotificationIntent intent = persistence.lockNotification(notificationId);
    if (intent == null) {
      throw PlatformExceptions.notFound("NOTIFICATION_NOT_FOUND", "通知不存在");
    }
    Interview interview = requireLockedVisible(intent.getInterviewId());
    requireScheduleAccess(interview);
    var actor = authorization.currentActor();
    String replay =
        reserve(
            actor.getUserId(),
            rawKey,
            "NOTIFICATION_RETRY",
            fingerprint(intent.getId()),
            intent.getId());
    if (replay != null) {
      NotificationIntent replayed = notificationIntentPersistenceService.findNotification(replay);
      if (replayed == null) {
        throw PlatformExceptions.conflict(
            "IDEMPOTENCY_REPLAY_INCOMPLETE", "原通知重试请求尚未完成，请稍后重试");
      }
      return NotificationRetryPreparation.replay(replayed);
    }
    requireNotificationApplicable(interview, intent);
    if (!InterviewNotificationType.INTERVIEW_CANCEL.matches(intent.getNotificationType())) {
      blacklist.requireRecruitmentAllowed(interview.getCandidateId());
    }
    Instant now = clock.instant();
    requireRetryableNotification(intent, now);
    if (notificationIntentPersistenceService.updateNotification(
            intent.getId(),
            intent.getChannel(),
            InterviewNotificationStatus.SENDING_EXTERNAL.getCode(),
            null,
            null,
            null,
            1,
            intent.getVersion(),
            actor.getUserId(),
            now)
        == 0) {
      throw state();
    }
    return NotificationRetryPreparation.claimed(intent, actor.getUserId());
  }

  /** 外部网络调用只接收已提交的发送占用，并使用 notificationId 作为渠道侧幂等键。 */
  private DeliveryResult deliverNotification(NotificationRetryPreparation preparation) {
    try {
      DeliveryResult result =
          delivery.deliver(
              new NotificationMessage(
                  preparation.notificationId(),
                  preparation.channel(),
                  preparation.recipientReference(),
                  preparation.subject(),
                  preparation.body()));
      if (result == null) {
        LOGGER.error(
            "external-notification-invalid-result notificationId={} channel={}",
            preparation.notificationId(),
            preparation.channel());
        return failedDelivery(DELIVERY_RESULT_INVALID_CODE, DELIVERY_RESULT_INVALID_DETAIL);
      }
      return normalizeDeliveryResult(result);
    } catch (RuntimeException ex) {
      LOGGER.error(
          "external-notification-delivery-exception notificationId={} channel={}",
          preparation.notificationId(),
          preparation.channel(),
          SafeLogException.from(ex));
      return failedDelivery(DELIVERY_EXCEPTION_CODE, DELIVERY_EXCEPTION_DETAIL);
    }
  }

  /** 在新的短事务中锁定占用记录并原子保存最终状态和发送审计。 */
  private NotificationIntent completeNotificationRetry(
      NotificationRetryPreparation preparation, DeliveryResult result) {
    NotificationIntent current = persistence.lockNotification(preparation.notificationId());
    if (current == null) {
      throw PlatformExceptions.notFound("NOTIFICATION_NOT_FOUND", "通知不存在");
    }
    if (!InterviewNotificationStatus.SENDING_EXTERNAL.matches(current.getStatus())
        || current.getVersion() != preparation.claimedVersion()) {
      throw state();
    }
    Instant now = clock.instant();
    InterviewNotificationStatus status =
        result.delivered()
            ? InterviewNotificationStatus.SENT_EXTERNAL
            : InterviewNotificationStatus.FAILED_EXTERNAL;
    if (notificationIntentPersistenceService.updateNotification(
            current.getId(),
            current.getChannel(),
            status.getCode(),
            result.externalMessageId(),
            result.errorCode(),
            result.errorDetail(),
            0,
            current.getVersion(),
            preparation.actorUserId(),
            now)
        == 0) {
      throw state();
    }
    if (notificationAttemptPersistenceService.insertNotificationAttempt(
            ids.newId(),
            current.getId(),
            "EXTERNAL_RETRY",
            current.getChannel(),
            (result.delivered()
                    ? InterviewNotificationAttemptStatus.SENT
                    : InterviewNotificationAttemptStatus.FAILED)
                .getCode(),
            result.externalMessageId(),
            result.errorCode(),
            result.errorDetail(),
            preparation.actorUserId(),
            now)
        != 1) {
      throw new IllegalStateException("通知发送尝试记录写入失败");
    }
    if (result.delivered()) {
      LOGGER.info(
          "external-notification-delivered notificationId={} channel={}",
          current.getId(),
          current.getChannel());
    } else {
      LOGGER.warn(
          "external-notification-delivery-failed notificationId={} channel={}",
          current.getId(),
          current.getChannel());
    }
    NotificationIntent completed =
        notificationIntentPersistenceService.findNotification(current.getId());
    if (completed == null) {
      throw new IllegalStateException("通知发送结果已提交但无法重新读取");
    }
    return completed;
  }

  @Transactional(readOnly = true)
  public List<InterviewTask> myTasks(int requestedLimit) {
    var actor = authorization.currentActor();
    authorization.requirePermission("recruitment.manage");
    Instant now = clock.instant();
    List<Interview> interviews = persistence.findMyInterviewTasks(
        actor.getUserId(), Math.clamp(requestedLimit, 1, MAX_MY_INTERVIEW_TASKS));
    Set<String> submittedInterviewIds = interviewFeedbackPersistenceService.findSubmittedInterviewIds(
        interviews.stream().map(Interview::getId).collect(Collectors.toSet()),
        actor.getUserId());
    return interviews.stream()
        .map(
            interview -> {
              boolean submitted = submittedInterviewIds.contains(interview.getId());
              Instant due = interview.getScheduledEndAt().plus(24, ChronoUnit.HOURS);
              return new InterviewTask(
                  interview.getId(),
                  interview.getApplicationId(),
                  interview.getCandidateId(),
                  interview.getJdId(),
                  interview.getStepCode(),
                  interview.getScheduledStartAt(),
                  interview.getScheduledEndAt(),
                  interview.getTimezoneId(),
                  interview.getMeetingMode(),
                  interview.getLocationText(),
                  interview.getMeetingUrl(),
                  due,
                  !submitted && due.isBefore(now),
                  submitted);
            })
        .toList();
  }

  private InterviewDetails details(Interview interview) {
    return new InterviewDetails(
        interview,
        participantPersistenceService.findParticipants(interview.getId()));
  }

  @Transactional(readOnly = true)
  public CursorPage<InterviewJobOptionResponse> invitedJobOptions(
      String query, String jdId, String cursor, Integer requestedLimit) {
    authorization.requirePermission("recruitment.manage");
    var actor=authorization.currentActor();
    String normalizedQuery=CommonUtils.stripToNull(query);
    String normalizedJd=CommonUtils.stripToNull(jdId);
    int limit=PageLimits.resolve(requestedLimit);
    String scope=CursorScopes.scope("interview.invited-jobs",actor.getUserId(),normalizedQuery,normalizedJd);
    var after=StableCursor.decode(cursorCodec,scope,cursor);
    var rows=persistence.findInvitedJobOptions(actor.getUserId(),normalizedQuery,normalizedJd,
        after==null?null:after.getSortTime(),after==null?null:after.getId(),limit+1);
    return CursorPages.page(rows,limit,cursorCodec,scope,InterviewJobOption::getSortTime,
        InterviewJobOption::getJdVersionId).map(assembler::toResponse);
  }

  private String historyScope(String kind, String interviewId) {
    var actor = authorization.currentActor();
    return CursorScopes.scope("interview." + kind, interviewId, actor.getUserId(),
        actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(), actor.visibleDepartmentIds(), actor.getPermissions());
  }

  private List<FeedbackView> visibleFeedback(List<Feedback> visible) {
    List<FeedbackView> result = new ArrayList<>(visible.size());
    for (int offset = 0; offset < visible.size(); offset += MAX_FEEDBACK_VERSIONS_PER_BATCH) {
      List<Feedback> batch = visible.subList(offset, Math.min(offset + MAX_FEEDBACK_VERSIONS_PER_BATCH, visible.size()));
      Map<String, Integer> requested = new LinkedHashMap<>();
      batch.forEach(value -> requested.put(value.getId(), value.getCurrentVersionNo()));
      Map<String, FeedbackVersion> versions = feedbackVersionPersistenceService.findFeedbackVersions(requested);
      batch.forEach(value -> result.add(new FeedbackView(value, versions.get(value.getId()))));
    }
    return List.copyOf(result);
  }

  private FeedbackView view(Feedback feedback) {
    if (feedback == null) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_REPLAY_INCOMPLETE", "原面评请求尚未完成，请稍后重试");
    }
    return new FeedbackView(
        feedback,
        feedbackVersionPersistenceService.findFeedbackVersion(feedback.getId(), feedback.getCurrentVersionNo()));
  }

  private Interview requireVisible(String id) {
    Interview value = persistence.findInterview(id);
    if (value == null) {
      throw PlatformExceptions.notFound("INTERVIEW_NOT_FOUND", "面试不存在");
    }
    if (!sharedCandidate(value.getCandidateId()) && !canView(value)) {
      throw PlatformExceptions.forbidden("INTERVIEW_ACCESS_FORBIDDEN", "无权查看该面试");
    }
    return value;
  }

  private boolean sharedCandidate(String candidateId) {
    authorization.currentActor();
    return candidateId != null && candidateLibrary.eligibleCandidateIds(List.of(candidateId)).contains(candidateId);
  }

  private Set<String> sharedInterviewIds(List<Interview> interviews) {
    Set<String> shared = candidateLibrary.eligibleCandidateIds(interviews.stream()
        .map(Interview::getCandidateId).filter(Objects::nonNull).distinct().toList());
    return interviews.stream().filter(value -> value.getCandidateId() != null && shared.contains(value.getCandidateId()))
        .map(Interview::getId).collect(Collectors.toSet());
  }

  private Interview requireLockedInterview(String id) {
    String candidateId = persistence.findCandidateId(id);
    if (candidateId == null) {
      throw PlatformExceptions.notFound("INTERVIEW_NOT_FOUND", "面试不存在");
    }
    blacklist.lockCandidateForCommand(candidateId);
    Interview value = persistence.lockInterview(id);
    if (value == null) {
      throw PlatformExceptions.notFound("INTERVIEW_NOT_FOUND", "面试不存在");
    }
    return value;
  }

  private static void requireScheduled(Interview value) {
    if (!InterviewStatus.SCHEDULED.matches(value.getStatus())) {
      throw PlatformExceptions.conflict("INTERVIEW_NOT_SCHEDULED", "只有待进行面试可以修改、改期或取消");
    }
  }

  private Interview requireLockedVisible(String id) {
    Interview value = requireLockedInterview(id);
    if (!canView(value)) {
      throw PlatformExceptions.forbidden("INTERVIEW_ACCESS_FORBIDDEN", "无权查看该面试");
    }
    return value;
  }

  private static void requireNotificationApplicable(Interview interview, NotificationIntent intent) {
    if (InterviewStatus.COMPLETED.matches(interview.getStatus())) {
      throw PlatformExceptions.conflict("INTERVIEW_COMPLETED", "面试已完成，不能补记或重试发送通知");
    }
    if (InterviewStatus.CANCELLED.matches(interview.getStatus())
        && !InterviewNotificationType.INTERVIEW_CANCEL.matches(intent.getNotificationType())) {
      throw PlatformExceptions.conflict("INTERVIEW_CANCELLED", "面试已取消，不能继续发送原排期通知");
    }
  }

  /** 校验并获取通知。 */
  private NotificationIntent requireNotification(String id) {
    lockCandidateForNotification(id);
    NotificationIntent value = persistence.lockNotification(id);
    if (value == null) {
      throw PlatformExceptions.notFound("NOTIFICATION_NOT_FOUND", "通知不存在");
    }
    return value;
  }

  private void lockCandidateForNotification(String id) {
    String candidateId = persistence.findNotificationCandidateId(id);
    if (candidateId == null) {
      throw PlatformExceptions.notFound("NOTIFICATION_NOT_FOUND", "通知不存在");
    }
    blacklist.lockCandidateForCommand(candidateId);
  }

  private boolean canView(Interview value) {
    return canView(authorization.currentActor(), value);
  }

  private boolean canView(CurrentActorSnapshot actor, Interview value) {
    if (participantPersistenceService.findParticipants(value.getId()).stream()
        .anyMatch(p -> p.getUserId().equals(actor.getUserId()))) {
      return true;
    }
    return !invitationOnly(actor)
        && authorization.canAccess(actor, "interview.feedback.read-authorized", target(value));
  }

  private Set<String> visibleInterviewIds(
      CurrentActorSnapshot actor, List<Interview> interviews) {
    Set<String> interviewIds = interviews.stream()
        .map(Interview::getId)
        .collect(Collectors.toSet());
    Set<String> visible = new LinkedHashSet<>(sharedInterviewIds(interviews));
    participantPersistenceService.findParticipantsByInterviewIds(interviewIds).stream()
        .filter(participant -> actor.getUserId().equals(participant.getUserId()))
        .map(Participant::getInterviewId)
        .forEach(visible::add);
    if (!invitationOnly(actor)) {
      visible.addAll(
        authorization.accessibleObjectIds(
            actor,
            "interview.feedback.read-authorized",
            interviews.stream().map(this::target).toList()));
    }
    return Set.copyOf(visible);
  }

  private static boolean invitationOnly(CurrentActorSnapshot actor) {
    // 仅面评角色的部门范围不构成面试授权；兼任招聘角色仍按招聘对象权限读取。
    return actor.hasPermission("interview.feedback.submit-own")
        && !actor.hasPermission("interview.schedule");
  }

  private void requireScope(Interview value) {
    authorization.requireAccess("interview.feedback.read-authorized", target(value));
  }

  private void requireScheduleAccess(Interview value) {
    authorization.requireAccess("interview.schedule", target(value));
  }

  private AuthorizationTarget target(Interview value) {
    return new AuthorizationTarget(
        "INTERVIEW", value.getId(), value.getOwnerUserId(), value.getOwnerDepartmentId(), Set.of());
  }

  /** 记录进度。 */
  private void recordProgress(String candidateId, String type, String reason) {
    var lock = engagementLocks.activeLock(candidateId);
    if (lock != null && lock.isCanContact()) {
      engagementLocks.recordProgress(candidateId, type, reason);
    }
  }

  private String reserve(
      String actor, String rawKey, String action, String request, String target) {
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    IdempotencyRecord existing = interviewIdempotencyPersistenceService.findIdempotency(actor, key);
    if (existing != null) {
      return verify(existing, action, request);
    }
    if (persistence.insertIdempotency(actor, key, action, request, target, clock.instant()) == 1) {
      return null;
    }
    // INSERT IGNORE=0 后读取已提交赢家，普通快照查询可能仍看不到它。
    existing = interviewIdempotencyPersistenceService.findIdempotencyForUpdate(actor, key);
    if (existing == null) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "幂等请求正在处理中，请稍后重试");
    }
    return verify(existing, action, request);
  }

  private static String verify(IdempotencyRecord value, String action, String request) {
    if (!action.equals(value.getActionCode()) || !request.equals(value.getRequestFingerprint())) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同面试命令");
    }
    return value.getTargetId();
  }

  private static String fingerprint(Object... values) {
    return Sha256Utils.fingerprintObjects(31, values);
  }

  /** 校验排期。 */
  private void validateSchedule(
      Instant start, Instant end, String timezone, String mode, String location, String url) {
    if (start == null || end == null || !end.isAfter(start)) {
      throw PlatformExceptions.unprocessable("INTERVIEW_TIME_INVALID", "面试结束时间必须晚于开始时间");
    }
    if (start.isBefore(clock.instant().minus(5, ChronoUnit.MINUTES))) {
      throw PlatformExceptions.unprocessable("INTERVIEW_TIME_INVALID", "不能新建或改期到已过去的时间");
    }
    try {
      ZoneId.of(required(timezone, "timezoneId", 64));
    } catch (DateTimeException ex) {
      throw PlatformExceptions.unprocessable("INTERVIEW_TIMEZONE_INVALID", "时区标识无效");
    }
    String normalized = CommonUtils.upperOrEmpty(mode);
    if (!InterviewMode.supports(normalized)) {
      throw PlatformExceptions.unprocessable("INTERVIEW_MODE_INVALID", "面试方式不受支持");
    }
    if (InterviewMode.ONSITE.matches(normalized) && CommonUtils.stripToNull(location) == null) {
      throw PlatformExceptions.unprocessable("INTERVIEW_LOCATION_REQUIRED", "现场面试必须填写地点");
    }
  }

  private static List<String> participants(List<String> users) {
    if (users == null || users.isEmpty()) {
      return List.of();
    }
    if (users.size() > MAX_PARTICIPANTS_PER_INTERVIEW) {
      throw PlatformExceptions.unprocessable("PARTICIPANT_LIMIT", "面试参与人数量超过限制");
    }
    return users.stream().map(userId -> require(userId, "participantUserId")).distinct().toList();
  }

  private static String interviewType(String recruitmentType, String stepCode) {
    if (WorkflowStepCode.PRE_SUBMISSION_INTERNAL_INTERVIEW.matches(stepCode)) {
      return InterviewType.INTERNAL.name();
    }
    if (WorkflowStepCode.CLIENT_AI_INTERVIEW.matches(stepCode)) {
      return InterviewType.EXTERNAL_CLIENT_AI.name();
    }
    return RecruitmentType.EXTERNAL.matches(recruitmentType)
        ? InterviewType.EXTERNAL_CLIENT.name()
        : InterviewType.INTERNAL.name();
  }

  private static int roundNo(String code) {
    if (code.endsWith("_1")) {
      return 1;
    }
    if (code.endsWith("_2")) {
      return 2;
    }
    if (code.endsWith("_3")) {
      return 3;
    }
    return 0;
  }

  private static void requireRetryableNotification(NotificationIntent intent, Instant now) {
    if (InterviewNotificationStatus.PENDING_MANUAL.matches(intent.getStatus())
        || InterviewNotificationStatus.FAILED_EXTERNAL.matches(intent.getStatus())) {
      return;
    }
    if (InterviewNotificationStatus.SENDING_EXTERNAL.matches(intent.getStatus())) {
      Instant claimedAt = intent.getUpdateTime();
      if (claimedAt != null && !claimedAt.isAfter(now.minus(EXTERNAL_DELIVERY_LEASE))) {
        return;
      }
      throw PlatformExceptions.conflict(
          "NOTIFICATION_DELIVERY_IN_PROGRESS", "通知正在通过外部渠道发送，请稍后重试");
    }
    if (InterviewNotificationStatus.SENT_MANUAL.matches(intent.getStatus())
        || InterviewNotificationStatus.SENT_EXTERNAL.matches(intent.getStatus())) {
      throw PlatformExceptions.conflict("NOTIFICATION_ALREADY_SENT", "通知已经发送，不能重复发送");
    }
    throw PlatformExceptions.conflict("NOTIFICATION_STATE_INVALID", "当前通知状态不允许外部重试");
  }

  private static DeliveryResult normalizeDeliveryResult(DeliveryResult result) {
    String externalMessageId =
        safeExternalValue(result.externalMessageId(), EXTERNAL_MESSAGE_ID_MAX_LENGTH);
    if (result.delivered()) {
      return new DeliveryResult(true, externalMessageId, null, null);
    }
    String errorCode = safeErrorCode(result.errorCode());
    String errorDetail =
        safeExternalValue(result.errorDetail(), EXTERNAL_ERROR_DETAIL_MAX_LENGTH);
    return failedDelivery(
        errorCode == null ? DELIVERY_REJECTED_CODE : errorCode,
        errorDetail == null ? DELIVERY_REJECTED_DETAIL : errorDetail);
  }

  private static DeliveryResult failedDelivery(String errorCode, String errorDetail) {
    return new DeliveryResult(false, null, errorCode, errorDetail);
  }

  private static String safeErrorCode(String value) {
    String sanitized = safeExternalValue(value, EXTERNAL_ERROR_CODE_MAX_LENGTH);
    if (sanitized == null) {
      return null;
    }
    StringBuilder result = new StringBuilder(sanitized.length());
    boolean previousSeparator = false;
    for (int index = 0; index < sanitized.length(); ) {
      int codePoint = sanitized.codePointAt(index);
      index += Character.charCount(codePoint);
      boolean accepted =
          codePoint >= 'A' && codePoint <= 'Z'
              || codePoint >= 'a' && codePoint <= 'z'
              || codePoint >= '0' && codePoint <= '9'
              || codePoint == '.'
              || codePoint == ':'
              || codePoint == '-';
      if (accepted) {
        result.appendCodePoint(codePoint);
        previousSeparator = false;
      } else if (!previousSeparator) {
        result.append('_');
        previousSeparator = true;
      }
    }
    return CommonUtils.stripToNull(result.toString());
  }

  /** 清理第三方返回的控制字符并按数据库字符列上限安全截断。 */
  private static String safeExternalValue(String value, int maximumCodePoints) {
    String stripped = CommonUtils.stripToNull(value);
    if (stripped == null) {
      return null;
    }
    StringBuilder result = new StringBuilder(Math.min(stripped.length(), maximumCodePoints));
    boolean previousWhitespace = false;
    int retained = 0;
    for (int index = 0; index < stripped.length() && retained < maximumCodePoints; ) {
      int codePoint = stripped.codePointAt(index);
      index += Character.charCount(codePoint);
      int type = Character.getType(codePoint);
      boolean whitespaceOrControl =
          Character.isWhitespace(codePoint)
              || type == Character.CONTROL
              || type == Character.FORMAT;
      if (whitespaceOrControl) {
        if (!previousWhitespace && result.length() > 0) {
          result.append(' ');
          retained++;
        }
        previousWhitespace = true;
      } else {
        result.appendCodePoint(codePoint);
        previousWhitespace = false;
        retained++;
      }
    }
    return CommonUtils.stripToNull(result.toString());
  }

  private static <T> T requireTransactionResult(T value) {
    if (value == null) {
      throw new IllegalStateException("通知事务未返回处理结果");
    }
    return value;
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class NotificationRetryPreparation {

    private final String notificationId;
    private final String channel;
    private final String recipientReference;
    private final String subject;
    private final String body;
    private final String actorUserId;
    private final long claimedVersion;
    private final NotificationIntent replayedIntent;

    private static NotificationRetryPreparation claimed(
        NotificationIntent intent, String actorUserId) {
      return new NotificationRetryPreparation(
          intent.getId(),
          intent.getChannel(),
          intent.getRecipientReference(),
          intent.getRenderedSubject(),
          intent.getRenderedBody(),
          actorUserId,
          Math.addExact(intent.getVersion(), 1L),
          null);
    }

    private static NotificationRetryPreparation replay(NotificationIntent intent) {
      return new NotificationRetryPreparation(null, null, null, null, null, null, 0L, intent);
    }
  }

  private static String snapshot(Interview value) {
    Map<String, String> snapshot = new LinkedHashMap<>();
    snapshot.put("status", value.getStatus());
    snapshot.put("interviewType", value.getInterviewType());
    snapshot.put("startAt", value.getScheduledStartAt().toString());
    snapshot.put("endAt", value.getScheduledEndAt().toString());
    snapshot.put("timezone", value.getTimezoneId());
    snapshot.put("mode", value.getMeetingMode());
    return JsonText.write(snapshot);
  }

  private static PlatformException state() {
    return PlatformExceptions.conflict("INTERVIEW_STATE_CHANGED", "面试状态已变化，请刷新后重试");
  }

  private String activitySource(String applicationId, String sourceType, String sourceId) {
    if (ActivityRequestContext.isPresent()) activityOrigins.record(applicationId, sourceType, sourceId);
    return sourceId;
  }
}

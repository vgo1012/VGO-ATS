package com.ezaigc.hr.recruiting.interview.controller;

import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewCreateRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewFeedbackRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewManualSendRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewRescheduleRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewUpdateRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewVersionReasonRequest;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewExternalCalendarReconciliationRequest;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarReconciliationDTO;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewDetailsResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewScheduleHistoryResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackBatchResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewNotificationResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewTaskResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewJobOptionResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class InterviewController {

  private static final int MINIMUM_ROUND_NUMBER = 0;
  private static final int MAXIMUM_ROUND_NUMBER = Integer.MAX_VALUE;

  @Resource private InterviewApplicationManager manager;
  @Resource private InterviewAssembler assembler;

  @PostMapping("/interviews")
  @ResponseStatus(HttpStatus.CREATED)
  InterviewDetailsResponse create(
      @Valid @RequestBody InterviewCreateRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.create(assembler.toDTO(request), key);
  }

  @GetMapping("/interviews")
  CursorPage<InterviewResponse> list(
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalFilterCode @RequestParam(required = false) String stepCode,
      @Min(MINIMUM_ROUND_NUMBER) @Max(MAXIMUM_ROUND_NUMBER)
          @RequestParam(required = false) Integer roundNo,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId,
      @OptionalFilterCode @RequestParam(required = false) String status,
      @OptionalFilterCode @RequestParam(required = false) String result,
      @OptionalFilterCode @RequestParam(required = false) String interviewType,
      @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String applicationId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String workflowStepId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return manager.listResponses(
        jdId, jdVersionId, stepCode, roundNo, ownerUserId, status, result, from, to, cursor, limit,
        interviewType, clientId, applicationId, workflowStepId);
  }

  @GetMapping("/interviews/{id}")
  InterviewDetailsResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.getResponse(id);
  }

  /** 更新指定业务记录。 */
  @PatchMapping("/interviews/{id}")
  InterviewDetailsResponse update(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewUpdateRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.update(id, assembler.toDTO(request), key);
  }

  @PostMapping("/interviews/{id}/reschedule")
  InterviewDetailsResponse reschedule(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewRescheduleRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.reschedule(id, assembler.toDTO(request), key);
  }

  @PostMapping("/interviews/{id}/cancel")
  InterviewDetailsResponse cancel(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewVersionReasonRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.cancelResponse(id, parse(request.getVersion()), request.getReason(), key);
  }

  @PostMapping("/interviews/{id}/external-calendar/retry")
  InterviewDetailsResponse retryExternalCalendar(@PlatformIdentifier @PathVariable String id) {
    return manager.retryExternalCalendar(id);
  }

  @PostMapping("/interviews/{id}/external-calendar/reconcile")
  InterviewDetailsResponse reconcileExternalCalendar(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewExternalCalendarReconciliationRequest request) {
    return manager.reconcileExternalCalendar(id, new InterviewExternalCalendarReconciliationDTO(
        parse(request.getVersion()), request.getIntentId(), request.getAction(), request.getExternalObjectId(),
        request.getReason(), request.getOriginalRequestTerminated()));
  }

  @PostMapping("/interviews/{id}/feedback")
  InterviewFeedbackResponse feedback(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewFeedbackRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.saveFeedback(id, assembler.toDTO(request), key);
  }

  @GetMapping("/interviews/{id}/feedback")
  CursorPage<InterviewFeedbackResponse> feedback(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return manager.feedbackResponses(id, cursor, limit);
  }

  @GetMapping("/interview-feedback-batch")
  ResponseEntity<InterviewFeedbackBatchResponse> feedbackBatch(
      @Size(min = 1, max = 100) @RequestParam List<@PlatformIdentifier String> interviewIds,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(manager.feedbackBatchResponses(interviewIds, cursor, limit));
  }

  @GetMapping("/my/interview-job-options")
  CursorPage<InterviewJobOptionResponse> invitedJobOptions(
      @Size(max = 200) @RequestParam(required = false) String query,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return manager.invitedJobOptions(query,jdId,cursor,limit);
  }

  @GetMapping("/interviews/{id}/schedule-history")
  CursorPage<InterviewScheduleHistoryResponse> history(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return manager.historyResponses(id, cursor, limit);
  }

  @GetMapping("/interviews/{id}/notifications")
  CursorPage<InterviewNotificationResponse> notifications(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit) {
    return manager.notificationResponses(id, cursor, limit);
  }

  /** 记录人工发送。 */
  @PostMapping("/notifications/{id}/record-manual-send")
  InterviewNotificationResponse recordManualSend(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody InterviewManualSendRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.recordManualSendResponse(
        id, request.getChannel(), request.getExternalMessageId(), request.getSentAt(), key);
  }

  @PostMapping("/notifications/{id}/retry")
  InterviewNotificationResponse retry(
      @PlatformIdentifier @PathVariable String id,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.retryNotificationResponse(id, key);
  }

  @GetMapping("/my/interview-tasks")
  List<InterviewTaskResponse> myTasks(
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
    return manager.myTaskResponses(limit);
  }
}

package com.ezaigc.hr.recruiting.recruitment.controller;

import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ExtendedPageLimit;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.manager.CandidateRecruitmentHistoryApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCollaboratorRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationNextActionRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCommunicationRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCompleteRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCreateRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationDecisionRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationFollowUpRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationIntentionRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOfferRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOnboardingRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOwnerRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationPathRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationReasonRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationReasonVersionRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationVersionRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationCommunicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDecisionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDetailsResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CandidateRecruitmentHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationFollowUpResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationIntentionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationWorkflowStepResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OfferStatusResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingEventResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.RecruitmentProcessingAccessResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.WorkItemResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCandidateFilterRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import java.time.Instant;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationStageCountResponse;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {

  @Resource private RecruitmentApplicationManager manager;
  @Resource private CandidateRecruitmentHistoryApplicationManager candidateHistory;
  @Resource private ApplicationAssembler assembler;

  @GetMapping("/candidates/{candidateId}/recruitment-history")
  CandidateRecruitmentHistoryResponse candidateHistory(
      @PlatformIdentifier @PathVariable String candidateId) {
    return candidateHistory.history(candidateId);
  }

  @PostMapping("/applications")
  @ResponseStatus(HttpStatus.CREATED)
  ApplicationDetailsResponse create(
      @Valid @RequestBody ApplicationCreateRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.createApplication(assembler.toDTO(r), key);
  }

  @GetMapping("/applications/stage-counts")
  List<ApplicationStageCountResponse> stageCounts(
      @RequestParam Instant from, @RequestParam Instant to,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId) {
    return manager.stageCounts(from, to, jdId, jdVersionId);
  }

  @GetMapping("/applications")
  CursorPage<ApplicationResponse> list(
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalFilterCode @RequestParam(required = false) String stage,
      @OptionalFilterCode @RequestParam(required = false) String workflowStepCode,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId,
      @OptionalFilterCode @RequestParam(required = false) String recruitmentType,
      @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
      @OptionalFilterCode @RequestParam(defaultValue = "ALL") String lifecycle,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") Integer limit,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @OptionalFilterCode @RequestParam(required = false) String interviewType,
      @RequestParam(defaultValue = "false") boolean onboarding,
      @Valid @ModelAttribute ApplicationCandidateFilterRequest candidateFilter) {
    return manager.applicationResponses(
        jdId, jdVersionId, stage, workflowStepCode, ownerUserId, cursor, limit, recruitmentType, clientId, lifecycle, assembler.toDTO(candidateFilter), from, to, interviewType, onboarding);
  }

  @GetMapping("/applications/count")
  CursorCount count(
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalFilterCode @RequestParam(required = false) String stage,
      @OptionalFilterCode @RequestParam(required = false) String workflowStepCode,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId,
      @OptionalFilterCode @RequestParam(required = false) String recruitmentType,
      @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
      @OptionalFilterCode @RequestParam(defaultValue = "ALL") String lifecycle,
      @ValidCursor @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @OptionalFilterCode @RequestParam(required = false) String interviewType,
      @RequestParam(defaultValue = "false") boolean onboarding,
      @Valid @ModelAttribute ApplicationCandidateFilterRequest candidateFilter) {
    return manager.countApplications(
        jdId, jdVersionId, stage, workflowStepCode, ownerUserId, cursor,
        recruitmentType, clientId, lifecycle, assembler.toDTO(candidateFilter),
        from, to, interviewType, onboarding);
  }

  @GetMapping("/applications/{id}")
  ApplicationDetailsResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.applicationResponse(id);
  }

  @GetMapping("/jd-versions/{jdVersionId}/recruitment-processing-access")
  RecruitmentProcessingAccessResponse processingAccess(
      @PlatformIdentifier @PathVariable String jdVersionId) {
    return new RecruitmentProcessingAccessResponse(manager.canProcessJdVersion(jdVersionId));
  }

  /** 更新指定业务记录。 */
  @PatchMapping("/applications/{id}")
  ApplicationDetailsResponse update(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationCollaboratorRequest r) {
    return manager.updateCollaboratorsResponse(
        id, r.getCollaboratorUserIds(), parse(r.getVersion()), r.getReason());
  }

  @PostMapping("/applications/{id}/assign")
  ApplicationDetailsResponse assign(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationOwnerRequest r) {
    return manager.transferResponse(
        id, r.getTargetUserId(), r.getReason(), parse(r.getVersion()), "ASSIGN");
  }

  @PostMapping("/applications/{id}/transfer")
  ApplicationDetailsResponse transfer(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationOwnerRequest r) {
    return manager.transferResponse(
        id, r.getTargetUserId(), r.getReason(), parse(r.getVersion()), "TRANSFER");
  }

  @PostMapping("/applications/{id}/transitions")
  ApplicationDecisionResponse transition(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationDecisionRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.decideResponse(id, r.getDecision(), r.getReason(), r.getClientSubmissionId(), key);
  }

  @PostMapping("/applications/{id}/next-actions")
  ApplicationDetailsResponse nextAction(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationNextActionRequest request) {
    return manager.nextActionResponse(id, request.getStepCode(), request.getReason(), parse(request.getVersion()), request.getRoundNo());
  }

  @GetMapping("/applications/{id}/workflow-steps")
  List<ApplicationWorkflowStepResponse> steps(@PlatformIdentifier @PathVariable String id) {
    return manager.stepResponses(id);
  }

  @PostMapping("/applications/{id}/workflow-path-selections")
  ApplicationDetailsResponse selectPath(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationPathRequest r) {
    return manager.selectPathResponse(
        id, r.getSelectedStepIds(), r.getReason(), parse(r.getVersion()), false);
  }

  @PostMapping("/applications/{id}/workflow-path-amendments")
  ApplicationDetailsResponse amendPath(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationPathRequest r) {
    return manager.selectPathResponse(
        id, r.getSelectedStepIds(), r.getReason(), parse(r.getVersion()), true);
  }

  @PostMapping("/applications/{id}/workflow-steps/{stepId}/start")
  ApplicationWorkflowStepResponse start(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String stepId,
      @Valid @RequestBody ApplicationVersionRequest r) {
    return manager.startStepResponse(id, stepId, parse(r.getVersion()));
  }

  @PostMapping("/applications/{id}/workflow-steps/{stepId}/follow-ups")
  @ResponseStatus(HttpStatus.CREATED)
  ApplicationFollowUpResponse followUp(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String stepId,
      @Valid @RequestBody ApplicationFollowUpRequest r) {
    return manager.followUpResponse(
        id, stepId, r.getChannel(), r.getSummary(), r.getNextPlan(), r.getContactedAt());
  }

  @GetMapping("/applications/{id}/workflow-steps/{stepId}/follow-ups")
  List<ApplicationFollowUpResponse> followUps(
      @PlatformIdentifier @PathVariable String id, @PlatformIdentifier @PathVariable String stepId) {
    return manager.followUpResponses(id, stepId);
  }

  @PostMapping("/applications/{id}/workflow-steps/{stepId}/complete")
  ApplicationDetailsResponse complete(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String stepId,
      @Valid @RequestBody ApplicationCompleteRequest r) {
    return manager.completeStepResponse(
        id,
        stepId,
        r.getResult(),
        r.getReason(),
        r.getClientSubmissionId(),
        parse(r.getVersion()), r.getChannel(), r.getNextPlan());
  }

  @PostMapping("/applications/{id}/workflow-steps/{stepId}/skip")
  ApplicationDetailsResponse skip(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String stepId,
      @Valid @RequestBody ApplicationReasonVersionRequest r) {
    return manager.skipStepResponse(id, stepId, r.getReason(), parse(r.getVersion()));
  }

  @PostMapping("/applications/{id}/decisions")
  @ResponseStatus(HttpStatus.CREATED)
  ApplicationDecisionResponse decide(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationDecisionRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.decideResponse(id, r.getDecision(), r.getReason(), r.getClientSubmissionId(), key);
  }

  /** 更正人工决定。 */
  @PostMapping("/applications/{id}/decisions/{decisionId}/corrections")
  ApplicationDecisionResponse correctDecision(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String decisionId,
      @Valid @RequestBody ApplicationDecisionRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.correctDecisionResponse(
        id, decisionId, r.getDecision(), r.getReason(), r.getClientSubmissionId(), key);
  }

  @GetMapping("/applications/{id}/communications")
  List<ApplicationCommunicationResponse> communications(@PlatformIdentifier @PathVariable String id) {
    return manager.communicationResponses(id);
  }

  @PostMapping("/applications/{id}/communications")
  @ResponseStatus(HttpStatus.CREATED)
  ApplicationCommunicationResponse communicate(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationCommunicationRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.communicateResponse(
        id, r.getChannel(), r.getSummary(), r.getResult(), r.getNextPlan(), r.getCommunicatedAt(), key);
  }

  @GetMapping("/applications/{id}/intentions")
  List<ApplicationIntentionResponse> intentions(@PlatformIdentifier @PathVariable String id) {
    return manager.intentionResponses(id);
  }

  @PostMapping("/applications/{id}/intentions")
  @ResponseStatus(HttpStatus.CREATED)
  ApplicationIntentionResponse intention(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody ApplicationIntentionRequest r) {
    return manager.addIntentionResponse(
        id,
        r.getExpectedSalaryMin(),
        r.getExpectedSalaryMax(),
        r.getSalaryPeriod(),
        r.getAvailability(),
        r.getLocations(),
        r.getReason());
  }

  @GetMapping("/applications/{id}/offer-status-records")
  List<OfferStatusResponse> offers(@PlatformIdentifier @PathVariable String id) {
    return manager.offerResponses(id);
  }

  @PostMapping("/applications/{id}/offer-status-records")
  @ResponseStatus(HttpStatus.CREATED)
  OfferStatusResponse offer(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationOfferRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.addOfferResponse(id, assembler.toDTO(r), key);
  }

  @PostMapping("/applications/{id}/offer-status-records/{recordId}/corrections")
  OfferStatusResponse correctOffer(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String recordId,
      @Valid @RequestBody ApplicationOfferRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.correctOfferResponse(id, recordId, assembler.toDTO(r), key);
  }

  @PostMapping("/applications/{id}/onboarding-events")
  @ResponseStatus(HttpStatus.CREATED)
  OnboardingEventResponse onboard(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationOnboardingRequest r,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.onboardResponse(id, r.getOnboardedAt(), r.getSourceReference(), r.getReason(), key);
  }

  @GetMapping("/workbench")
  List<WorkItemResponse> workbench(
      @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.workItemResponses(false, limit);
  }

  @GetMapping("/management/workbench")
  List<WorkItemResponse> managementWorkbench(
      @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.workItemResponses(true, limit);
  }

}

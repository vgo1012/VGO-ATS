package com.ezaigc.hr.recruiting.submission.controller;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.recruiting.submission.domain.request.ApplicationClientRestrictionRequest;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ExtendedPageLimit;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.submission.application.assembler.SubmissionAssembler;
import com.ezaigc.hr.recruiting.submission.application.manager.SubmissionApplicationManager;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionCreateRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionDisableRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionFeedbackRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionRestrictionRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionRiskRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionSendRequest;
import com.ezaigc.hr.recruiting.submission.domain.request.SubmissionWithdrawRequest;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientRestrictionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.ClientSubmissionResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.EligibilityResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.FeedbackResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.RiskAcknowledgementResponse;
import com.ezaigc.hr.recruiting.submission.domain.response.SubmissionDetailsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
public class SubmissionController {

  @Resource private SubmissionApplicationManager manager;
  @Resource private SubmissionAssembler assembler;

  @PostMapping("/applications/{id}/client-restrictions")
  @ResponseStatus(HttpStatus.CREATED)
  ClientRestrictionResponse restrictApplication(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationClientRestrictionRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.restrictApplication(id, assembler.toDTO(body), key);
  }

  @PostMapping("/client-submissions")
  @ResponseStatus(HttpStatus.CREATED)
  SubmissionDetailsResponse create(
      @Valid @RequestBody SubmissionCreateRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.create(assembler.toDTO(body), key);
  }

  @GetMapping("/client-submissions")
  CursorPage<ClientSubmissionResponse> list(
      @OptionalPlatformIdentifier @RequestParam(required = false) String applicationId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalFilterCode @RequestParam(required = false) String deliveryStatus,
      @OptionalFilterCode @RequestParam(required = false) String processStatus,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.listResponses(
        applicationId, clientId, jdId, deliveryStatus, processStatus, cursor, limit);
  }

  @GetMapping("/client-submissions/{id}")
  SubmissionDetailsResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.getResponse(id);
  }

  @PostMapping("/client-submissions/{id}/send")
  SubmissionDetailsResponse send(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody SubmissionSendRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.send(id, assembler.toDTO(body), key);
  }

  @PostMapping("/client-submissions/{id}/feedback")
  @ResponseStatus(HttpStatus.CREATED)
  FeedbackResponse feedback(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody SubmissionFeedbackRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.addFeedback(id, assembler.toDTO(body), key);
  }

  @PostMapping("/client-submissions/{id}/withdraw")
  SubmissionDetailsResponse withdraw(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody SubmissionWithdrawRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.withdraw(id, assembler.toDTO(body), key);
  }

  @PostMapping("/client-submissions/{id}/risk-acknowledgements")
  @ResponseStatus(HttpStatus.CREATED)
  RiskAcknowledgementResponse acknowledge(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody SubmissionRiskRequest body) {
    return manager.acknowledgeRisk(id, assembler.toDTO(body));
  }

  @GetMapping("/client-submissions/eligibility")
  EligibilityResponse eligibility(@PlatformIdentifier @RequestParam String applicationId) {
    return manager.eligibilityResponse(applicationId);
  }

  @GetMapping("/candidates/{candidateId}/client-restrictions")
  List<ClientRestrictionResponse> restrictions(
      @PlatformIdentifier @PathVariable String candidateId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String clientId,
      @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "false") boolean currentOnly) {
    return currentOnly ? manager.currentRestrictionResponses(candidateId, clientId)
        : manager.restrictionResponses(candidateId, clientId, limit);
  }

  /** 更正限制。 */
  @PostMapping(value = "/client-restrictions/{id}/corrections", headers = "Idempotency-Key")
  ClientRestrictionResponse correctRestriction(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody SubmissionRestrictionRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.correctRestriction(id, assembler.toDTO(body), key);
  }

  /** 停用限制。 */
  @PostMapping(value = "/client-restrictions/{id}/disable", headers = "Idempotency-Key")
  ClientRestrictionResponse disableRestriction(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody SubmissionDisableRequest body,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.disableRestriction(id, assembler.toDTO(body), key);
  }
  /** 兼容尚未传幂等键的旧页面，状态保护仍由业务层执行。 */
  @PostMapping(value = "/client-restrictions/{id}/corrections", headers = "!Idempotency-Key")
  ClientRestrictionResponse correctRestrictionWithoutKey(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody SubmissionRestrictionRequest body) {
    return manager.correctRestriction(id, assembler.toDTO(body));
  }

  /** 兼容旧页面；新入口传幂等键时路由至统一格式校验的方法。 */
  @PostMapping(value = "/client-restrictions/{id}/disable", headers = "!Idempotency-Key")
  ClientRestrictionResponse disableRestrictionWithoutKey(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody SubmissionDisableRequest body) {
    return manager.disableRestriction(id, assembler.toDTO(body));
  }

}

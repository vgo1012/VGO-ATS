package com.ezaigc.hr.recruiting.recruitment.controller;
import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.recruitment.application.manager.FollowUpPlanApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.FollowUpPlanAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.request.FollowUpPlanRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.FollowUpPlanCancelRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.request.FollowUpPlanCompleteRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanHistoryResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1")
public class FollowUpPlanController {
  @Resource private FollowUpPlanApplicationManager manager;
  @Resource private FollowUpPlanAssembler assembler;
  @GetMapping("/follow-up-plans")
  CursorPage<FollowUpPlanResponse> list(@RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to,
      @OptionalPlatformIdentifier @RequestParam(required=false) String applicationId,
      @OptionalPlatformIdentifier @RequestParam(required=false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required=false) String jdVersionId,
      @OptionalFilterCode @RequestParam(required=false) String recruitmentType,
      @OptionalPlatformIdentifier @RequestParam(required=false) String clientId,
      @OptionalFilterCode @RequestParam(required=false) String status,
      @ValidCursor @RequestParam(required=false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue="20") Integer limit) {
    return manager.list(from,to,applicationId,jdId,jdVersionId,recruitmentType,clientId,status,cursor,limit);
  }
  @GetMapping("/applications/{id}/follow-up-plan-history")
  CursorPage<FollowUpPlanHistoryResponse> history(@PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required=false) String cursor, @StandardPageLimit @RequestParam(defaultValue="20") Integer limit) {
    return manager.history(id,cursor,limit);
  }
  @PostMapping("/applications/{id}/follow-up-plans")
  @ResponseStatus(HttpStatus.CREATED)
  FollowUpPlanResponse create(@PlatformIdentifier @PathVariable String id, @Valid @RequestBody FollowUpPlanRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.create(id,assembler.toDTO(request),key);
  }
  @PatchMapping("/applications/{id}/follow-up-plans/{planId}")
  FollowUpPlanResponse update(@PlatformIdentifier @PathVariable String id, @PlatformIdentifier @PathVariable String planId,
      @Valid @RequestBody FollowUpPlanRequest request) { return manager.update(id,planId,assembler.toDTO(request)); }
  @PostMapping("/applications/{id}/follow-up-plans/{planId}/cancel")
  FollowUpPlanResponse cancel(@PlatformIdentifier @PathVariable String id, @PlatformIdentifier @PathVariable String planId,
      @Valid @RequestBody FollowUpPlanCancelRequest request) { return manager.finish(id,planId,parse(request.getVersion()),request.getReason(),false); }
  @PostMapping("/applications/{id}/follow-up-plans/{planId}/complete")
  FollowUpPlanResponse complete(@PlatformIdentifier @PathVariable String id, @PlatformIdentifier @PathVariable String planId,
      @Valid @RequestBody FollowUpPlanCompleteRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.complete(id,planId,parse(request.getVersion()),request.getReason(),request.getResult(),request.getNextPlan(),
        request.getWorkflowStepId(),request.getWorkflowStepVersion() == null ? null : parse(request.getWorkflowStepVersion()), key);
  }
}

package com.ezaigc.hr.intelligence.matching.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchRunDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.PreviewMatchDTO;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchRunCreateRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchRunPreviewRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.MatchRunReasonRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.BaseEvaluationResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchPreviewResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchResultPageResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchRunRunResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchRunTaskResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
public class MatchRunController {
  @Resource private MatchRunApplicationManager manager;

  @PostMapping("/match-runs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  MatchRunRunResponse create(
      @Valid @RequestBody MatchRunCreateRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.createResponse(
        new CreateMatchRunDTO(
            request.getJdVersionId(),
            request.getResumeVersionIds(),
            request.getWeightProfileVersionId(),
            request.getPersonalPreferenceVersionId(),
            request.getSelectedCriterionIds(),
            request.getSortMode()),
        key);
  }

  @GetMapping("/match-runs/{id}")
  MatchRunRunResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.getResponse(id);
  }

  @GetMapping("/match-runs/{id}/results")
  MatchResultPageResponse results(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.results(id, cursor, limit);
  }

  @PostMapping("/match-runs/{id}/cancel")
  MatchRunTaskResponse cancel(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody MatchRunReasonRequest request) {
    return manager.cancelResponse(id, request.getReason());
  }

  @PostMapping("/match-runs/{id}/retry-failed")
  MatchRunTaskResponse retry(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody MatchRunReasonRequest request) {
    return manager.retryResponse(id, request.getReason());
  }

  @PostMapping("/match-previews")
  MatchPreviewResponse preview(@Valid @RequestBody MatchRunPreviewRequest request) {
    return manager.previewResponse(
        new PreviewMatchDTO(
            request.getJdVersionId(),
            request.getResumeVersionId(),
            request.getWeightProfileVersionId(),
            request.getSelectedCriterionIds()));
  }

  @GetMapping("/base-evaluations/{id}")
  BaseEvaluationResponse base(@PlatformIdentifier @PathVariable String id) {
    return manager.base(id);
  }

}

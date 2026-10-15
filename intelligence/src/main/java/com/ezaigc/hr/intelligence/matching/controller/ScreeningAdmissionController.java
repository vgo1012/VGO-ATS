package com.ezaigc.hr.intelligence.matching.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.intelligence.matching.application.manager.ScreeningAdmissionApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningAdmissionRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningReturnRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningReturnBatchResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningAdmissionBatchResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningCandidateResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningSelectionCandidateResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
public class ScreeningAdmissionController {
  @Resource private ScreeningAdmissionApplicationManager manager;

  @GetMapping("/jd-versions/{jdVersionId}/screening-candidates")
  CursorPage<ScreeningSelectionCandidateResponse> selectionCandidates(
      @PathVariable @PlatformIdentifier String jdVersionId,
      @RequestParam(required = false) @Size(max = 128) String query,
      @RequestParam(required = false) @ValidCursor String cursor,
      @RequestParam(defaultValue = "20") @StandardPageLimit int limit) {
    return manager.selectionCandidates(jdVersionId, query, cursor, limit);
  }

  @GetMapping("/jd-versions/{jdVersionId}/screening-candidates/count")
  CursorCount countSelectionCandidates(
      @PathVariable @PlatformIdentifier String jdVersionId,
      @RequestParam(required = false) @Size(max = 128) String query,
      @RequestParam(required = false) @ValidCursor String cursor) {
    return manager.countSelectionCandidates(jdVersionId, query, cursor);
  }

  @GetMapping("/screening-candidates")
  List<ScreeningCandidateResponse> candidates(
      @RequestParam(required = false) @Size(max = 1000)
      List<@PlatformIdentifier String> candidateIds) {
    return manager.candidates(candidateIds);
  }

  @PostMapping("/screening-admissions")
  @ResponseStatus(HttpStatus.CREATED)
  ScreeningAdmissionBatchResponse admit(
      @Valid @RequestBody ScreeningAdmissionRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return manager.admit(request.getItems(), idempotencyKey);
  }

  @PostMapping("/screening-admissions/return")
  ScreeningReturnBatchResponse returnToResumes(
      @Valid @RequestBody ScreeningReturnRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return manager.returnToResumes(request.getItems(), idempotencyKey);
  }
}

package com.ezaigc.hr.intelligence.matching.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchJobApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobBasisResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResultSummaryResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResultResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/match-jobs")
public class MatchJobController {
  @Resource private MatchJobApplicationManager manager;

  @GetMapping
  CursorPage<MatchJobResponse> jobs(
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.jobs(jdVersionId, cursor, limit);
  }

  @GetMapping("/{jdVersionId}/bases")
  CursorPage<MatchJobBasisResponse> bases(@PlatformIdentifier @PathVariable String jdVersionId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.bases(jdVersionId, cursor, limit);
  }

  @GetMapping("/{jdVersionId}/results")
  CursorPage<MatchJobResultResponse> results(@PlatformIdentifier @PathVariable String jdVersionId,
      @PlatformIdentifier @RequestParam String basisRunId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.results(jdVersionId, basisRunId, cursor, limit);
  }

  @GetMapping("/{jdVersionId}/results-summary")
  MatchJobResultSummaryResponse resultsSummary(@PlatformIdentifier @PathVariable String jdVersionId,
      @PlatformIdentifier @RequestParam String basisRunId) {
    return manager.resultsSummary(jdVersionId, basisRunId);
  }
}

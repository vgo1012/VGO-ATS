package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.CandidateResumeSummaryApplicationManager;
import com.ezaigc.hr.talent.domain.response.CandidateResumeSummaryResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidateResumeSummaryController {
  @Resource private CandidateResumeSummaryApplicationManager manager;

  @GetMapping("/api/v1/candidate-resume-summaries")
  public ResponseEntity<List<CandidateResumeSummaryResponse>> summaries(
      @RequestParam @Size(max = 100) List<@PlatformIdentifier String> candidateIds) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(manager.summaries(candidateIds));
  }
}

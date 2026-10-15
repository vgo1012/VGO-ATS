package com.ezaigc.hr.intelligence.matching.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.intelligence.matching.application.manager.ScoringBindingApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.request.ScoringBindingRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.ScoringBindingResponse;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jd-versions/{jdVersionId}/scoring-plan")
public class ScoringBindingController {
  @Resource
  private ScoringBindingApplicationManager manager;

  @GetMapping
  public ScoringBindingResponse get(@PlatformIdentifier @PathVariable String jdVersionId) {
    return manager.get(jdVersionId);
  }

  @PutMapping
  public ScoringBindingResponse bind(@PlatformIdentifier @PathVariable String jdVersionId,
      @Valid @RequestBody ScoringBindingRequest request) {
    return manager.bind(jdVersionId, request.getPlanVersionId(), Long.parseLong(request.getVersion()));
  }
}

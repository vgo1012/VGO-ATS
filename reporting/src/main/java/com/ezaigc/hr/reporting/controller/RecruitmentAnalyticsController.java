package com.ezaigc.hr.reporting.controller;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.reporting.application.manager.RecruitmentAnalyticsManager;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecruitmentAnalyticsController {
  @Resource private RecruitmentAnalyticsManager manager;

  @GetMapping("/api/v1/reports/recruitment-analysis")
  public RecruitmentAnalyticsResponse report(
      @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
      @OptionalPlatformIdentifier @RequestParam(required=false) String departmentId,
      @OptionalPlatformIdentifier @RequestParam(required=false) String jdId,
      @Pattern(regexp = "INTERNAL|EXTERNAL") @RequestParam(required=false) String recruitmentType) {
    return manager.report(from,to,departmentId,jdId,recruitmentType);
  }
}

package com.ezaigc.hr.reporting.controller;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.reporting.application.manager.MetricApplicationManager;
import com.ezaigc.hr.reporting.domain.response.MetricReportResponse;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class MetricController {
  @Resource private MetricApplicationManager manager;

  @GetMapping("/overview")
  MetricReportResponse overview(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId) {
    return manager.report("overview", from, to, jdId, jdVersionId, ownerUserId);
  }

  @GetMapping("/funnel")
  MetricReportResponse funnel(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId) {
    return manager.report("funnel", from, to, jdId, jdVersionId, ownerUserId);
  }

  @GetMapping("/workload")
  MetricReportResponse workload(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId) {
    return manager.report("workload", from, to, jdId, jdVersionId, ownerUserId);
  }

  @GetMapping("/ai-effect")
  MetricReportResponse ai(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String jdVersionId,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId) {
    return manager.report("ai-effect", from, to, jdId, jdVersionId, ownerUserId);
  }
}

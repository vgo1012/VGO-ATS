package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.domain.response.ResumeParsingParseTaskResponse;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 简历解析 HTTP 控制器，负责接收请求并返回接口响应。 */
@RestController
@RequestMapping("/api/v1/resume-versions")
public class ResumeParsingController {

  @Resource private ResumeParsingApplicationManager service;

  @PostMapping("/{id}/parse")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ResumeParsingParseTaskResponse parse(
      @PlatformIdentifier @PathVariable String id,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResumeParsingParseTaskResponse.from(service.enqueue(id, idempotencyKey));
  }

  @GetMapping("/{id}/facts")
  FactBundleResponse facts(@PlatformIdentifier @PathVariable String id) {
    return service.factResponse(id);
  }

  @GetMapping("/{id}/facts/{snapshotId}")
  FactBundleResponse historicalFacts(
      @PlatformIdentifier @PathVariable String id,
      @PlatformIdentifier @PathVariable String snapshotId) {
    return service.historicalFactResponse(id, snapshotId);
  }

}

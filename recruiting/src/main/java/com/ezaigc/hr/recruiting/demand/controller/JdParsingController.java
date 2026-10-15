package com.ezaigc.hr.recruiting.demand.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import com.ezaigc.hr.recruiting.demand.domain.response.JdFactsResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.JdParsingParseTaskResponse;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** JD 解析 HTTP 控制器，负责接收请求并返回接口响应。 */
@RestController
@RequestMapping("/api/v1/jd-versions")
public class JdParsingController {

  @Resource private JdParsingApplicationManager service;

  @PostMapping("/{id}/parse")
  @ResponseStatus(HttpStatus.ACCEPTED)
  JdParsingParseTaskResponse parse(
      @PlatformIdentifier @PathVariable String id,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return JdParsingParseTaskResponse.from(service.enqueue(id, key));
  }

  @GetMapping("/{id}/facts")
  JdFactsResponse facts(@PlatformIdentifier @PathVariable String id) {
    return service.facts(id);
  }
}

package com.ezaigc.hr.reporting.outbox.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.reporting.domain.request.ReasonRequest;
import com.ezaigc.hr.reporting.outbox.application.manager.SearchTaskRecoveryApplicationManager;
import com.ezaigc.hr.reporting.outbox.domain.response.SearchTaskRecoveryResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 搜索人工恢复入口，使用原任务 ID 查询关联，恢复结果仍由全局任务接口跟踪。 */
@RestController
@RequestMapping("/api/v1/tasks/{id}/search-recovery")
public class SearchTaskRecoveryController {
  @Resource private SearchTaskRecoveryApplicationManager manager;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  SearchTaskRecoveryResponse create(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ReasonRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.create(id, request.getReason(), key);
  }

  @GetMapping
  SearchTaskRecoveryResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.get(id);
  }
}

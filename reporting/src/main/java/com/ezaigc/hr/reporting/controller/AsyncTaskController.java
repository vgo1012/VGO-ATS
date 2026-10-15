package com.ezaigc.hr.reporting.controller;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.reporting.application.manager.AsyncTaskApplicationManager;
import com.ezaigc.hr.reporting.domain.request.ReasonRequest;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 异步任务 HTTP 控制器，负责接收请求并返回接口响应。 */
@RestController
@RequestMapping("/api/v1/tasks")
public class AsyncTaskController {

  @Resource private AsyncTaskApplicationManager manager;

  @GetMapping
  List<AsyncTaskResponse> tasks(
      @OptionalFilterCode @RequestParam(required = false) String status,
      @OptionalFilterCode @RequestParam(required = false) String taskType,
      @OptionalPlatformIdentifier @RequestParam(required = false) String ownerUserId,
      @OptionalFilterCode @RequestParam(required = false) String resourceType,
      @OptionalPlatformIdentifier @RequestParam(required = false) String resourceId,
      @StandardPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.listResponses(status, taskType, ownerUserId, resourceType, resourceId, limit);
  }

  @GetMapping("/{id}")
  AsyncTaskResponse task(@PlatformIdentifier @PathVariable String id) {
    return manager.getResponse(id);
  }

  @PostMapping("/{id}/cancel")
  AsyncTaskResponse cancel(@PlatformIdentifier @PathVariable String id, @Valid @RequestBody ReasonRequest request) {
    return manager.cancelResponse(id, request.getReason());
  }

  @PostMapping("/{id}/retry-failed")
  AsyncTaskResponse retry(@PlatformIdentifier @PathVariable String id, @Valid @RequestBody ReasonRequest request) {
    return manager.retryFailedResponse(id, request.getReason());
  }
}

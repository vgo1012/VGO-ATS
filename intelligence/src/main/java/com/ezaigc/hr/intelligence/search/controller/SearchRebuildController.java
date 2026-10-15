package com.ezaigc.hr.intelligence.search.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.intelligence.search.application.manager.SearchProjectionApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.response.RebuildRunResponse;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search-index-rebuilds")
public class SearchRebuildController {
  @Resource private SearchProjectionApplicationManager manager;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  RebuildRunResponse create(
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.createRebuild(key);
  }

  @GetMapping("/{id}")
  RebuildRunResponse get(@PlatformIdentifier @PathVariable String id) {
    return manager.get(id);
  }
}

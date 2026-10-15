package com.ezaigc.hr.intelligence.search.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexResourceApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.request.SearchIndexReconciliationRequest;
import com.ezaigc.hr.intelligence.search.domain.response.LegacySearchIndexTargetResponse;
import com.ezaigc.hr.intelligence.search.domain.response.SearchIndexResourceResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search-index-resources")
public class SearchIndexResourceController {
  @Resource private SearchIndexResourceApplicationManager manager;

  @GetMapping("/legacy-runs/{runId}")
  List<LegacySearchIndexTargetResponse> legacy(@PlatformIdentifier @PathVariable String runId) {
    return manager.legacyTargets(runId);
  }

  @PostMapping("/legacy-runs/{runId}/attempts/{attempt}/adopt")
  SearchIndexResourceResponse adopt(@PlatformIdentifier @PathVariable String runId,
      @Min(1) @PathVariable int attempt, @Valid @RequestBody SearchIndexReconciliationRequest request) {
    return manager.adoptLegacy(runId, attempt, request);
  }

  @GetMapping("/{id}")
  SearchIndexResourceResponse get(@PlatformIdentifier @PathVariable String id) { return manager.getResource(id); }

  @PostMapping("/{id}/reconcile")
  SearchIndexResourceResponse reconcile(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody SearchIndexReconciliationRequest request) { return manager.reconcile(id, request); }
}

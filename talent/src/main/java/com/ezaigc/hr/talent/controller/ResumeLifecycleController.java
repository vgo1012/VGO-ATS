package com.ezaigc.hr.talent.controller;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.ResumeLifecycleAdministrationManager;
import com.ezaigc.hr.talent.domain.request.ResumeLifecycleRetryRequest;
import com.ezaigc.hr.talent.domain.response.ResumeLifecycleRunsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/admin/resume-lifecycle/runs")
public class ResumeLifecycleController {
  @Resource private ResumeLifecycleAdministrationManager manager;
  @GetMapping public ResumeLifecycleRunsResponse status() { return manager.status(); }
  @PostMapping("/{id}/retry")
  public ResumeLifecycleRunsResponse retry(@PathVariable @PlatformIdentifier String id,
      @Valid @RequestBody ResumeLifecycleRetryRequest request,
      @RequestHeader("Idempotency-Key") @ValidIdempotencyKey String key) {
    return manager.retry(id,request.getReason(),key);
  }
}

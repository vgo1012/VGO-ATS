package com.ezaigc.hr.recruiting.demand.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.application.manager.DemandCatalogApplicationManager;
import com.ezaigc.hr.recruiting.demand.domain.request.JdDemandBindingRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.StandardWorkflowActivationRequest;
import com.ezaigc.hr.recruiting.demand.domain.response.JdResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.RecruitmentReadinessResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecruitmentReadinessController {
  @Resource private DemandCatalogApplicationManager manager;
  @Resource private DemandCatalogAssembler assembler;

  @GetMapping("/api/v1/jds/{id}")
  JdResponse jd(@PlatformIdentifier @PathVariable String id) {
    return assembler.toJdResponse(manager.getJd(id));
  }

  @GetMapping("/api/v1/jd-versions/{id}/recruitment-readiness")
  RecruitmentReadinessResponse readiness(@PlatformIdentifier @PathVariable String id) {
    return assembler.toReadinessResponse(manager.recruitmentReadiness(id));
  }

  @PostMapping("/api/v1/jd-versions/{id}/standard-workflow-activation")
  RecruitmentReadinessResponse activate(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody StandardWorkflowActivationRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return assembler.toReadinessResponse(manager.activateStandardWorkflow(id, request.getReason(), key));
  }

  @PostMapping("/api/v1/jds/{id}/demand-binding")
  JdResponse bind(@PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody JdDemandBindingRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return assembler.toJdResponse(manager.bindSourceJdToDemand(id, request.getDemandId(), request.getReason(), key));
  }
}

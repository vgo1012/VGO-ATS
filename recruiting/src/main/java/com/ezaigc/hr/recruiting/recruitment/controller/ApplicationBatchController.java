package com.ezaigc.hr.recruiting.recruitment.controller;

import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.recruiting.recruitment.application.manager.ApplicationBatchApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationBatchCreateRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationBatchResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationBatchController {
  @Resource private ApplicationAssembler assembler;
  @Resource private ApplicationBatchApplicationManager manager;

  @PostMapping("/api/v1/applications/batch")
  ApplicationBatchResponse create(@Valid @RequestBody ApplicationBatchCreateRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.create(assembler.toDTO(request), key);
  }
}

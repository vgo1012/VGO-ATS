package com.ezaigc.hr.recruiting.recruitment.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationHireRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOnboardingReviewRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationOnboardingReturnRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OfferStatusResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OnboardingProcessResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications/{id}")
public class OnboardingFlowController {
  @Resource private RecruitmentApplicationManager manager;

  @PostMapping("/hire")
  @ResponseStatus(HttpStatus.CREATED)
  OnboardingProcessResponse hire(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationHireRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.hireResponse(id, request.getOfferStatus(), request.getReason(), key);
  }

  @PostMapping("/onboarding-reviews")
  @ResponseStatus(HttpStatus.CREATED)
  OnboardingProcessResponse review(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationOnboardingReviewRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.reviewOnboardingResponse(id, request.getOnboardedAt(), request.getReason(), key);
  }

  @PostMapping("/onboarding-return")
  @ResponseStatus(HttpStatus.CREATED)
  OfferStatusResponse returnToShared(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody ApplicationOnboardingReturnRequest request,
      @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String key) {
    return manager.returnOnboardingToSharedResponse(
        id, request.getReasonCategory(), request.getReasonDetail(), key);
  }
}

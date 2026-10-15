package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.request.ExtendedPageLimit;
import com.ezaigc.hr.talent.application.manager.RiskApplicationManager;
import com.ezaigc.hr.talent.domain.request.RiskBlacklistRequest;
import com.ezaigc.hr.talent.domain.request.RiskEducationRequest;
import com.ezaigc.hr.talent.domain.request.RiskReasonRequest;
import com.ezaigc.hr.talent.domain.response.AffectedWorkflowResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistHistoryResponse;
import com.ezaigc.hr.talent.domain.response.EducationVerificationResponse;
import com.ezaigc.hr.talent.domain.response.RestrictionSummaryResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RiskController {
  @Resource private RiskApplicationManager manager;

  @GetMapping("/candidate-blacklist-tags")
  List<String> blacklistTags(@RequestParam @Size(max = 100) List<@PlatformIdentifier String> candidateIds) {
    return manager.blacklistTags(candidateIds);
  }

  @GetMapping("/candidates/{id}/restriction-summaries")
  List<RestrictionSummaryResponse> restrictions(@PlatformIdentifier @PathVariable String id) {
    return manager.restrictions(id);
  }

  @GetMapping("/candidates/{id}/company-blacklist-records")
  CompanyBlacklistHistoryResponse blacklistHistory(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @ExtendedPageLimit @RequestParam(required = false) Integer limit) {
    return manager.blacklistHistory(id, cursor, limit);
  }

  @GetMapping("/company-blacklist-records/{id}/affected-workflows/page")
  CursorPage<AffectedWorkflowResponse> affectedPage(
      @PlatformIdentifier @PathVariable String id,
      @ValidCursor @RequestParam(required = false) String cursor,
      @ExtendedPageLimit @RequestParam(required = false) Integer limit) {
    return manager.affectedPage(id, cursor, limit);
  }

  @PostMapping("/candidates/{id}/company-blacklist-records")
  @ResponseStatus(HttpStatus.CREATED)
  CompanyBlacklistResponse blacklist(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody RiskBlacklistRequest r) {
    return manager.createBlacklist(
        id,
        r.getReasonCode(),
        r.getReasonDetail(),
        r.getSourceChannel(),
        r.getEvidenceReference(),
        r.getOccurredAt());
  }

  @GetMapping("/company-blacklist-records/{id}/affected-workflows")
  List<AffectedWorkflowResponse> affected(
      @PlatformIdentifier @PathVariable String id,
      @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.affected(id, limit);
  }

  @PostMapping("/company-blacklist-records/{id}/corrections")
  CompanyBlacklistResponse correct(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody RiskBlacklistRequest r) {
    return manager.correctBlacklist(
        id,
        r.getReasonCode(),
        r.getReasonDetail(),
        r.getSourceChannel(),
        r.getEvidenceReference(),
        r.getOccurredAt());
  }

  @PostMapping("/company-blacklist-records/{id}/disable")
  CompanyBlacklistResponse disable(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody RiskReasonRequest r) {
    return manager.disableBlacklist(id, r.getReason());
  }

  @GetMapping("/candidates/{id}/education-verification-records")
  List<EducationVerificationResponse> education(
      @PlatformIdentifier @PathVariable String id,
      @ExtendedPageLimit @RequestParam(defaultValue = "20") int limit) {
    return manager.education(id, limit);
  }

  @PostMapping("/applications/{id}/education-verification-records")
  @ResponseStatus(HttpStatus.CREATED)
  EducationVerificationResponse education(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody RiskEducationRequest r) {
    return manager.education(
        id,
        r.getCandidateId(),
        r.getEducationFactId(),
        r.getRecordType(),
        r.getVerificationStatus(),
        r.getStudyMode(),
        r.getSourceChannel(),
        r.getEvidenceReference(),
        r.getNote(),
        r.getCorrectsRecordId(),
        r.getOccurredAt());
  }

  /** 更正教育经历。 */
  @PostMapping("/education-verification-records/{id}/corrections")
  EducationVerificationResponse correctEducation(
      @PlatformIdentifier @PathVariable String id, @Valid @RequestBody RiskEducationRequest r) {
    return manager.education(
        r.getApplicationId(),
        r.getCandidateId(),
        r.getEducationFactId(),
        "CORRECTION",
        r.getVerificationStatus(),
        r.getStudyMode(),
        r.getSourceChannel(),
        r.getEvidenceReference(),
        r.getNote(),
        id,
        r.getOccurredAt());
  }
}

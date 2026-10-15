package com.ezaigc.hr.integration.mail.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import com.ezaigc.hr.integration.mail.domain.MailResumeIngestRun;
import com.ezaigc.hr.integration.mail.domain.MailJdOptionResponse;
import com.ezaigc.hr.integration.mail.domain.MailResumeLinkRequest;
import com.ezaigc.hr.integration.mail.domain.MailResumeReviewResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MailResumeController {

  @Resource private MailResumeApplicationManager manager;

  @GetMapping("/mail-resume-reviews")
  CursorPage<MailResumeReviewResponse> reviews(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) @Size(max = 180) String title,
      @RequestParam(required = false) @Size(max = 180) String region,
      @RequestParam(required = false) @Size(max = 255) String attachment,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.reviews(from, to, title, region, attachment, cursor, limit);
  }

  @GetMapping("/mail-resume-reviews/count")
  CursorCount countReviews(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) @Size(max = 180) String title,
      @RequestParam(required = false) @Size(max = 180) String region,
      @RequestParam(required = false) @Size(max = 255) String attachment) {
    return manager.countReviews(from, to, title, region, attachment);
  }

  @PutMapping("/mail-resume-reviews/{id}/jd")
  MailResumeReviewResponse link(
      @PlatformIdentifier @PathVariable String id,
      @Valid @RequestBody MailResumeLinkRequest request) {
    return manager.link(id, request);
  }

  @GetMapping("/mail-resume-jds")
  List<MailJdOptionResponse> jds(
      @RequestParam(required = false) @Size(max = 180) String query) {
    return manager.jdOptions(query);
  }

  @GetMapping("/admin/mail-resume-ingest-runs/{id}")
  MailResumeIngestRun run(@PlatformIdentifier @PathVariable String id) {
    return manager.run(id);
  }
}

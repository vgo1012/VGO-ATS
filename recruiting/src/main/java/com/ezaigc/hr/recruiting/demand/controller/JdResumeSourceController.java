package com.ezaigc.hr.recruiting.demand.controller;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.StandardPageLimit;
import com.ezaigc.hr.common.web.request.ValidCursor;
import com.ezaigc.hr.recruiting.demand.application.assembler.JdResumeSourceAssembler;
import com.ezaigc.hr.recruiting.demand.application.manager.JdResumeSourceApplicationManager;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdResumeSourceRequest;
import com.ezaigc.hr.recruiting.demand.domain.response.JdResumeSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.MailSourceMessageResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class JdResumeSourceController {
  @Resource private JdResumeSourceApplicationManager manager;
  @Resource private JdResumeSourceAssembler assembler;

  /** 幂等键为当前收件人、原始 Message-ID 和可信附件哈希。 */
  @PostMapping("/jd-versions/{jdVersionId}/resume-sources")
  JdResumeSourceResponse register(@PlatformIdentifier @PathVariable String jdVersionId,
      @Valid @RequestBody CreateJdResumeSourceRequest request) {
    return assembler.toResponse(manager.register(jdVersionId, assembler.toDTO(request)));
  }

  @GetMapping("/jd-versions/{jdVersionId}/resume-sources")
  CursorPage<JdResumeSourceResponse> list(@PlatformIdentifier @PathVariable String jdVersionId,
      @ValidCursor @RequestParam(required = false) String cursor,
      @StandardPageLimit @RequestParam(required = false) Integer limit) {
    return manager.list(jdVersionId, cursor, limit).map(assembler::toResponse);
  }

  @GetMapping("/jd-versions/{jdVersionId}/resume-sources/count")
  CursorCount count(@PlatformIdentifier @PathVariable String jdVersionId) {
    return manager.count(jdVersionId);
  }

  @GetMapping("/mail-sources/{mailSourceId}")
  MailSourceMessageResponse mail(@PlatformIdentifier @PathVariable String mailSourceId) {
    return assembler.toResponse(manager.mail(mailSourceId));
  }
}

package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.ResumeSourceApplicationManager;
import com.ezaigc.hr.talent.domain.response.ResumeSourceResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeSourceController {
  @Resource private ResumeSourceApplicationManager manager;

  @GetMapping("/api/v1/candidates/{candidateId}/resume-versions/{versionId}/source")
  ResumeSourceResponse source(@PlatformIdentifier @PathVariable String candidateId,
      @PlatformIdentifier @PathVariable String versionId) {
    return manager.source(candidateId, versionId);
  }
}

package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.ResumeVersionSourceApplicationManager;
import com.ezaigc.hr.talent.domain.response.ResumeVersionSourceResponse;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeVersionSourceController {
  @Resource private ResumeVersionSourceApplicationManager manager;

  /** 保持去重后的请求顺序；不可读取、不存在或不可用的版本省略。 */
  @GetMapping("/api/v1/resume-version-sources")
  public ResponseEntity<List<ResumeVersionSourceResponse>> sources(
      @RequestParam @Size(max = 100) List<@PlatformIdentifier String> resumeVersionIds) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(manager.sources(resumeVersionIds));
  }
}

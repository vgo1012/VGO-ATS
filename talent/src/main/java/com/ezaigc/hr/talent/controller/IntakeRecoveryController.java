package com.ezaigc.hr.talent.controller;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.talent.application.manager.IntakeRecoveryApplicationManager;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intakes")
public class IntakeRecoveryController {
  @Resource private IntakeRecoveryApplicationManager manager;

  @GetMapping("/{id}")
  ResumeIntakeResponse detail(@PathVariable @PlatformIdentifier String id) {
    return manager.detail(id);
  }
}

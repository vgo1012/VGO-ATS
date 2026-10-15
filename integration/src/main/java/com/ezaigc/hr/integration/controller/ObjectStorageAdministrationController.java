package com.ezaigc.hr.integration.controller;

import com.ezaigc.hr.integration.application.manager.ObjectStorageAdministrationManager;
import com.ezaigc.hr.integration.domain.response.ObjectStorageOverviewResponse;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/storage")
@ConditionalOnProperty(name = "hr.object-storage.mode", havingValue = "oss")
public class ObjectStorageAdministrationController {
  @Resource private ObjectStorageAdministrationManager manager;

  @GetMapping("/overview")
  public ObjectStorageOverviewResponse overview() {
    return manager.overview();
  }
}

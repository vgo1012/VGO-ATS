package com.ezaigc.hr.integration.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.integration.application.provider.ObjectStorageAdministrationPort;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.integration.domain.dto.ObjectStorageSnapshotDTO;
import com.ezaigc.hr.integration.domain.response.ObjectStorageOverviewResponse;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "hr.object-storage.mode", havingValue = "oss")
public class ObjectStorageAdministrationManager {
  @Resource private AuthorizationService authorization;
  @Resource private IntegrationProperties properties;
  @Resource private ObjectStorageAdministrationPort storage;

  public ObjectStorageOverviewResponse overview() {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) {
      throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
    }
    IntegrationProperties.ObjectStorage objectStorage = properties.getObjectStorage();
    IntegrationProperties.Oss oss = objectStorage.getOss();
    ObjectStorageSnapshotDTO snapshot = storage.snapshot();
    return new ObjectStorageOverviewResponse(
        "ALIYUN_OSS",
        snapshot.isAvailable() ? "AVAILABLE" : "UNAVAILABLE",
        oss.getRegion(),
        storage.bucketDisplayName(),
        "ECS_RAM_ROLE",
        oss.getRoleName(),
        objectStorage.isLegacyMinioFallbackEnabled(),
        snapshot.getObjectCount(),
        snapshot.getStorageBytes(),
        snapshot.getMultipartUploadCount(),
        snapshot.getStatisticsUpdatedAt(),
        snapshot.getSampledAt(),
        snapshot.isAvailable() ? "OSS 连接正常" : "OSS 当前不可访问或无权读取 Bucket 统计");
  }
}

package com.ezaigc.hr.integration.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.integration.application.provider.ObjectStorageAdministrationPort;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.integration.domain.dto.ObjectStorageSnapshotDTO;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ObjectStorageAdministrationManagerTest {

  @Test
  void exposesOnlySanitizedBucketStatisticsToSuperAdministrators() {
    AuthorizationService authorization = mock(AuthorizationService.class);
    ObjectStorageAdministrationPort storage = mock(ObjectStorageAdministrationPort.class);
    when(authorization.currentActor()).thenReturn(actor(true));
    Instant sampledAt = Instant.parse("2026-09-17T07:00:00Z");
    when(storage.snapshot()).thenReturn(
        new ObjectStorageSnapshotDTO(true, 18L, 8192L, 1L,
            Instant.parse("2026-09-17T06:00:00Z"), sampledAt));
    when(storage.bucketDisplayName()).thenReturn("ez***hr");

    var response = manager(authorization, storage).overview();

    assertThat(response.getStatus()).isEqualTo("AVAILABLE");
    assertThat(response.getBucketDisplayName()).isEqualTo("ez***hr");
    assertThat(response.getCredentialMode()).isEqualTo("ECS_RAM_ROLE");
    assertThat(response.getObjectCount()).isEqualTo(18L);
    assertThat(response.getStorageBytes()).isEqualTo(8192L);
    assertThat(response.getSampledAt()).isEqualTo(sampledAt);
  }

  @Test
  void rejectsNonAdministratorsBeforeQueryingCloudStorage() {
    AuthorizationService authorization = mock(AuthorizationService.class);
    when(authorization.currentActor()).thenReturn(actor(false));

    assertThatThrownBy(() -> manager(authorization, mock(ObjectStorageAdministrationPort.class)).overview())
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("超级管理员");
  }

  private static ObjectStorageAdministrationManager manager(
      AuthorizationService authorization, ObjectStorageAdministrationPort storage) {
    IntegrationProperties properties = new IntegrationProperties();
    properties.getObjectStorage().setLegacyMinioFallbackEnabled(true);
    properties.getObjectStorage().setOss(new IntegrationProperties.Oss(
        "ecs-ram-role",
        "https://oss-cn-hangzhou-internal.aliyuncs.com",
        "cn-hangzhou",
        "vgo-example-private",
        "vgo-example-oss-role"));
    ObjectStorageAdministrationManager manager = new ObjectStorageAdministrationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "properties", properties);
    ReflectionTestUtils.setField(manager, "storage", storage);
    return manager;
  }

  private static CurrentActorSnapshot actor(boolean superAdmin) {
    return new CurrentActorSnapshot(
        "user-1",
        "管理员",
        "admin@example.test",
        "dept-1",
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        false,
        superAdmin);
  }
}

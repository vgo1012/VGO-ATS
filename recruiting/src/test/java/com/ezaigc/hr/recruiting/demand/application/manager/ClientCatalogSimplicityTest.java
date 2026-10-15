package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateClientDTO;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateNameDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandIdempotencyPersistenceService;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ClientCatalogSimplicityTest {
  private final DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);

  @BeforeEach
  void setUp() {
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("actor");
    when(actor.getHomeDepartmentId()).thenReturn("department");
    var ids = mock(PlatformIdGenerator.class); when(ids.newId()).thenReturn("client-id");
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", catalog);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
    ReflectionTestUtils.setField(manager, "demandIdempotencyPersistenceService", mock(DemandIdempotencyPersistenceService.class));
    ReflectionTestUtils.setField(manager, "demandAuditEventPersistenceService", mock(DemandAuditEventPersistenceService.class));
  }

  @Test
  void administratorCreatesClientWithOnlyNameAndIgnoresLegacyManualCode() {
    when(actor.isSuperAdmin()).thenReturn(true);
    var input = new CreateClientDTO(); input.setName("客户 A"); input.setCode("MANUAL-CODE");
    manager.createClient(input, "create-client");
    var code = ArgumentCaptor.forClass(String.class);
    verify(catalog).insertClient(eq("client-id"), code.capture(), eq("客户 A"), eq("actor"),
        eq("department"), eq("actor"), any());
    assertThat(code.getValue()).isNotBlank().isNotEqualTo("MANUAL-CODE");
  }

  @Test
  void hrCanReadAdministratorClientButCannotCreateOrRenameIt() {
    var client = new ClientEntity(); client.setId("client-id"); client.setOwnerUserId("admin");
    when(catalog.findClient("client-id")).thenReturn(client);
    assertThat(manager.getClient("client-id")).isSameAs(client);
    verify(authorization).requirePermission("demand.read");
    var create = new CreateClientDTO(); create.setName("客户 B");
    assertThatThrownBy(() -> manager.createClient(create, "not-admin"))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("ADMIN_REQUIRED"));
    assertThatThrownBy(() -> manager.updateClient("client-id", new UpdateNameDTO()))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("ADMIN_REQUIRED"));
    verify(catalog, never()).insertClient(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
  }
}

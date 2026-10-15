package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ClientDeletionApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DemandCatalogPersistenceService persistence = mock(DemandCatalogPersistenceService.class);
  private final DemandAuditEventPersistenceService audit = mock(DemandAuditEventPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final ClientDeletionApplicationManager manager = new ClientDeletionApplicationManager();
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final VersionReasonDTO command = new VersionReasonDTO();
  private final Instant now = Instant.parse("2026-09-14T00:00:00Z");

  ClientDeletionApplicationManagerTest() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "audit", audit);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    actor.setUserId("admin"); actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    var client = new ClientEntity(); client.setId("client"); client.setVersion(4L);
    when(persistence.findClient("client")).thenReturn(client);
    when(persistence.deleteClient("client", 4)).thenReturn(1);
    when(ids.newId()).thenReturn("event");
    command.setVersion(4); command.setReason(" 管理员删除客户 ");
  }

  @Test void deletesUnreferencedClientAndAuditsActualActor() {
    manager.deleteClient("client", command);
    verify(authorization).requirePermission("demand.manage");
    verify(authorization).requireAccess(eq("demand.manage"), any());
    var order = inOrder(persistence, audit);
    order.verify(persistence).findClient("client");
    order.verify(persistence).deleteClient("client", 4);
    order.verify(audit).insertAudit("event", "CLIENT_DELETED", "admin", "CLIENT", "client",
        "管理员删除客户", now);
  }

  @Test void rejectsOrdinaryUserBeforeReadingClient() {
    actor.setSuperAdmin(false);
    assertCode("ADMIN_REQUIRED", 403);
    verifyNoInteractions(persistence, audit);
  }

  @Test void permissionFailureNeverDeletes() {
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "禁止")).when(authorization).requirePermission("demand.manage");
    assertCode("FORBIDDEN", 403);
    verifyNoInteractions(persistence, audit);
  }

  @Test void missingOrRepeatedDeletionDoesNotCreateAnotherAudit() {
    when(persistence.findClient("client")).thenReturn(null);
    assertCode("CLIENT_NOT_FOUND", 404);
    verify(persistence, never()).deleteClient(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test void staleVersionDoesNotAudit() {
    when(persistence.deleteClient("client", 4)).thenReturn(0);
    assertCode("CLIENT_VERSION_CONFLICT", 409);
    verifyNoInteractions(audit);
  }

  @Test void anyForeignKeyDependencyOrConcurrentReferenceReturnsActionableConflict() {
    when(persistence.deleteClient("client", 4)).thenThrow(new DataIntegrityViolationException("FK"));
    assertCode("CLIENT_IN_USE", 409);
    assertThatThrownBy(() -> manager.deleteClient("client", command)).hasMessageContaining("停用");
    verifyNoInteractions(audit);
  }

  @Test void invalidReasonNeverDeletes() {
    command.setReason(" ");
    assertThatThrownBy(() -> manager.deleteClient("client", command)).isInstanceOf(PlatformException.class);
    verify(persistence, never()).deleteClient(any(), anyLong());
    verifyNoInteractions(audit);
  }

  private void assertCode(String code, int status) {
    assertThatThrownBy(() -> manager.deleteClient("client", command))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo(code);
          assertThat(error.status().value()).isEqualTo(status);
        });
  }
}

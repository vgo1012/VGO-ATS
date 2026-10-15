package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.ReportingAssembler;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;
import com.ezaigc.hr.reporting.service.AuditCaptureService;
import com.ezaigc.hr.reporting.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

class AuditApplicationManagerTest {

  private static final Instant NOW = Instant.parse("2026-09-03T08:00:00Z");
  private static final String USER_ID = "01KUSER0000000000000000000";

  private final AuditService service = mock(AuditService.class);
  private final AuditCaptureService captureService = mock(AuditCaptureService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final AuditApplicationManager manager = new AuditApplicationManager();
  private final CurrentActorSnapshot actor =
      new CurrentActorSnapshot(USER_ID, "Audit User", "audit@example.test",
          "01KDEPARTMENT00000000000000", Set.of("support-department"), Set.of(),
          Set.of("explicit-department"), Set.of(), Set.of(), false, false);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "captureService", captureService);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "assembler", new ReportingAssembler());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    when(authorization.currentActor())
        .thenReturn(
            actor);
    when(service.search(
            any(),
            any(),
            nullable(String.class),
            nullable(String.class),
            nullable(String.class),
            nullable(String.class),
            nullable(Instant.class),
            nullable(String.class),
            anyBoolean(),
            anyString(),
            anyList(),
            anyList(),
            anyInt()))
        .thenReturn(List.of());
  }

  @Test
  void searchUsesCompanyAndManagedOwnerScopeWithoutDepartmentExpansion() {
    actor.setCompanyScope(true);
    actor.setManagedUserIds(Set.of("managed-user"));

    manager.search(null, null, null, null, null, null, null, 20);

    verify(service).search(any(), any(), nullable(String.class), nullable(String.class),
        nullable(String.class), nullable(String.class), nullable(Instant.class), nullable(String.class),
        eq(true), eq(USER_ID), eq(List.of("managed-user")), eq(List.of()), anyInt());
  }

  @Test
  void searchReadsThePreparedProjectionWithoutRunningCaptureWrites() {
    var response = manager.search(null, null, null, null, null, null, null, 20);

    assertThat(response.getItems()).isEmpty();
    verify(service, never()).capture();
    verify(service)
        .search(
            any(),
            any(),
            nullable(String.class),
            nullable(String.class),
            nullable(String.class),
            nullable(String.class),
            nullable(Instant.class),
            nullable(String.class),
            anyBoolean(),
            anyString(),
            anyList(),
            anyList(),
            anyInt());
  }

  @Test
  void searchTransactionIsReadOnly() throws Exception {
    var method =
        AuditApplicationManager.class.getMethod(
            "search",
            Instant.class,
            Instant.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            int.class);
    var attributes = new AnnotationTransactionAttributeSource();

    assertThat(attributes.getTransactionAttribute(method, AuditApplicationManager.class))
        .isNotNull()
        .extracting(attribute -> attribute.isReadOnly())
        .isEqualTo(true);
  }

  @Test
  void captureNextDelegatesWithinAWriteTransaction() throws Exception {
    when(captureService.captureNext(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 100))
        .thenReturn(7);

    assertThat(manager.captureNext(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 100))
        .isEqualTo(7);
    verify(captureService)
        .captureNext(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 100);

    var method =
        AuditApplicationManager.class.getMethod(
            "captureNext", AuditCaptureSource.class, AuditCaptureLane.class, int.class);
    var attributes = new AnnotationTransactionAttributeSource();

    assertThat(attributes.getTransactionAttribute(method, AuditApplicationManager.class))
        .isNotNull()
        .extracting(attribute -> attribute.isReadOnly())
        .isEqualTo(false);
  }
}

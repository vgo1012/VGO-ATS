package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.ReportingAssembler;
import com.ezaigc.hr.reporting.config.MetricCaptureProperties;
import com.ezaigc.hr.reporting.domain.dto.MetricProjectionStatusDTO;
import com.ezaigc.hr.reporting.service.MetricCaptureService;
import com.ezaigc.hr.reporting.service.MetricService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

class MetricApplicationManagerTest {
  private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
  private final MetricService service = mock(MetricService.class);
  private final MetricCaptureService capture = mock(MetricCaptureService.class);
  private final MetricCaptureProperties properties = new MetricCaptureProperties();
  private final MetricApplicationManager manager = new MetricApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot("actor", "Actor", null, "department",
      Set.of("support-department"), Set.of(), Set.of("explicit-department"), Set.of(),
      Set.of("reporting.read-own"), false, false);

  MetricApplicationManagerTest() {
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "captureService", capture);
    ReflectionTestUtils.setField(manager, "captureProperties", properties);
    ReflectionTestUtils.setField(manager, "assembler", new ReportingAssembler());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void overviewAllowsExactly366Days() {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW, NOW));
    Instant from = NOW.minus(Duration.ofDays(366));

    var response = manager.report("overview", from, NOW, null, null, null);

    assertThat(response.getFrom()).isEqualTo(from);
    assertThat(response.getTo()).isEqualTo(NOW);
    verify(service).aggregate(eq(from), eq(NOW), any(), any(), any(),
        eq(false), eq("actor"), eq(List.of()), eq(List.of()));
    verify(service).daily(eq(from), eq(NOW), any(), any(), any(),
        eq(false), eq("actor"), eq(List.of()), eq(List.of()));
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 1_000_000_000, 86_400_000_000_000L})
  void overviewRejectsEveryExcessBeforeReadingProjectionOrMetrics(long excessNanos) {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW, NOW));
    Instant from = NOW.minus(Duration.ofDays(366)).minusNanos(excessNanos);

    assertThatThrownBy(() -> manager.report("overview", from, NOW, null, null, null))
        .isInstanceOfSatisfying(PlatformException.class, failure ->
            assertThat(failure.code()).isEqualTo("REPORT_TIME_RANGE_TOO_LONG"));
    verifyNoInteractions(capture, service);
  }

  @Test
  void otherReportsKeepTheirExistingLongRangeBehavior() {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW, NOW));
    Instant from = NOW.minus(Duration.ofDays(367));

    for (String report : List.of("funnel", "workload", "ai-effect")) {
      var response = manager.report(report, from, NOW, null, null, null);
      assertThat(response.getFrom()).isEqualTo(from);
      assertThat(response.getTo()).isEqualTo(NOW);
    }
  }

  @Test
  void overviewUsesTheSameOwnerScopeForEveryAggregateWithoutDepartmentExpansion() {
    actor.setManagedUserIds(Set.of("managed-user"));
    actor.setCompanyScope(true);
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW, NOW));

    manager.report("overview", null, null, null, null, null);

    verify(service).aggregate(any(), any(), any(), any(), any(),
        eq(true), eq("actor"), eq(List.of("managed-user")), eq(List.of()));
    verify(service).daily(any(), any(), any(), any(), any(),
        eq(true), eq("actor"), eq(List.of("managed-user")), eq(List.of()));
  }

  @Test
  void everyReportIsReadOnlyAndReportsPersistedFreshnessRatherThanRequestTime() throws Exception {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW.minusSeconds(10), NOW.minusSeconds(90)));
    for (String report : List.of("overview", "funnel", "workload", "ai-effect")) {
      var response = manager.report(report, null, null, null, null, null);
      assertThat(response.getDataUpdatedAt()).isEqualTo(NOW.minusSeconds(10));
      assertThat(response.getProjectionStatus()).isEqualTo("READY");
      assertThat(response.getReconciliationUpdatedAt()).isEqualTo(NOW.minusSeconds(90));
    }
    verify(service, never()).capture(any(), any(), any());
    var transaction = new AnnotationTransactionAttributeSource().getTransactionAttribute(
        MetricApplicationManager.class.getMethod("report", String.class, Instant.class, Instant.class,
            String.class, String.class, String.class), MetricApplicationManager.class);
    assertThat(transaction).isNotNull();
    assertThat(transaction.isReadOnly()).isTrue();
  }

  @Test
  void staleProjectionRemainsReadableWithLaggingStatus() {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW.minus(properties.getStaleAfter()).minusSeconds(1), null));
    assertThat(manager.report("overview", null, null, null, null, null).getProjectionStatus()).isEqualTo("LAGGING");
  }

  @Test
  void normalCaptureDelayWithinFiveMinutesDoesNotProduceFalseLagAlerts() {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(NOW.minusSeconds(120), null));
    assertThat(manager.report("overview", null, null, null, null, null).getProjectionStatus()).isEqualTo("READY");
  }

  @Test
  void uninitializedOrDisabledProjectionNeverReturnsAnApparentlyEmptyReport() {
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(null, null));
    assertThatThrownBy(() -> manager.report("overview", null, null, null, null, null)).isInstanceOf(PlatformException.class)
        .hasMessageContaining("初始化");
    properties.setEnabled(false);
    assertThatThrownBy(() -> manager.report("overview", null, null, null, null, null)).isInstanceOf(PlatformException.class)
        .hasMessageContaining("停用");
    verify(service, never()).definitions();
  }
}

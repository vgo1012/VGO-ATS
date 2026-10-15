package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.ReportingAssembler;
import com.ezaigc.hr.reporting.service.MetricService;
import com.ezaigc.hr.reporting.service.MetricCaptureService;
import com.ezaigc.hr.reporting.config.MetricCaptureProperties;
import com.ezaigc.hr.reporting.domain.dto.MetricProjectionStatusDTO;
import com.ezaigc.hr.reporting.domain.dto.MetricAggregateDTO;
import com.ezaigc.hr.reporting.domain.response.MetricAggregateResponse;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import java.util.Arrays;
import java.util.Map;
import com.ezaigc.hr.reporting.domain.dto.ParseCoverageDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OverviewReportTest {
  @Test
  void aiCoverageUsesTheAuthorizedCohortAndRejectsOutOfScopeOwners() {
    MetricService service = mock(MetricService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("hr");
    when(authorization.currentActor()).thenReturn(actor);
    var from = Instant.parse("2026-09-01T00:00:00Z");
    var to = Instant.parse("2026-09-08T00:00:00Z");
    when(service.parseCoverage(from, to, "jd", null, "hr", false, "hr", List.of(), List.of()))
        .thenReturn(new ParseCoverageDTO(2, 1));
    var manager = new MetricApplicationManager();
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(to, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", new ReportingAssembler());
    projectionReady(manager);
    var report = manager.report("ai-effect", from, to, "jd", null, "hr");
    assertThat(report.getParseCoverage().getResumeVersionCount()).isEqualTo(2);
    assertThat(report.getParseCoverage().getParsedResumeVersionCount()).isEqualTo(1);
    clearInvocations(service);
    assertThatThrownBy(() -> manager.report("ai-effect", from, to, null, null, "other"))
        .hasMessageContaining("目标用户");
    verifyNoInteractions(service);
  }

  @Test
  void supportsHistoricalRangeAndChecksItBeforeReadingData() {
    MetricService service = mock(MetricService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("hr");
    when(authorization.currentActor()).thenReturn(actor);
    when(service.definitions()).thenReturn(List.of());
    MetricApplicationManager manager = new MetricApplicationManager();
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", new ReportingAssembler());
    projectionReady(manager);
    Instant from = Instant.parse("2026-07-31T16:00:00Z");
    Instant to = Instant.parse("2026-08-31T16:00:00Z");
    var report = manager.report("overview", from, to, null, null, "hr");
    assertThat(report.getFrom()).isEqualTo(from);
    assertThat(report.getTo()).isEqualTo(to);
    verify(service).aggregate(from, to, null, null, "hr", false, "hr", List.of(), List.of());
    clearInvocations(service);
    assertThatThrownBy(() -> manager.report("overview", to, from, null, null, "hr"))
        .hasMessageContaining("开始时间");
    verify(service, never()).capture(any(), any(), anyList());
  }

  @Test
  void readsOnlyTheRequestedReportEventsInsideTheResolvedWindowWithoutCapturing() {
    MetricService service = mock(MetricService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("hr");
    when(authorization.currentActor()).thenReturn(actor);
    when(service.definitions()).thenReturn(List.of());
    when(service.aggregate(any(), any(), any(), any(), any(), anyBoolean(), any(), anyList(), anyList()))
        .thenReturn(List.of());
    when(service.daily(any(), any(), any(), any(), any(), anyBoolean(), any(), anyList(), anyList()))
        .thenReturn(List.of());
    when(service.parseCoverage(any(), any(), any(), any(), any(), anyBoolean(), any(), anyList(), anyList()))
        .thenReturn(new ParseCoverageDTO(0, 0));
    Instant from = Instant.parse("2026-09-01T00:00:00Z");
    Instant to = Instant.parse("2026-09-08T00:00:00Z");
    MetricApplicationManager manager = new MetricApplicationManager();
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(to, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "assembler", new ReportingAssembler());
    projectionReady(manager);

    var rows = Arrays.stream(MetricEventType.values()).map(type -> {
      var row = new MetricAggregateDTO();
      row.setEventType(type.getCode());
      row.setEventCount(1);
      return row;
    }).toList();
    when(service.aggregate(from, to, null, null, null, false, "hr", List.of(), List.of())).thenReturn(rows);
    var expected = Map.of(
        "overview", List.of("RESUME_INGESTED", "APPLICATION_CREATED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED"),
        "funnel", List.of("RESUME_INGESTED", "APPLICATION_CREATED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED"),
        "workload", List.of("HUMAN_SCREENED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED"),
        "ai-effect", List.of("RESUME_INGESTED", "RESUME_PARSED"));
    for (var entry : expected.entrySet()) {
      clearInvocations(service);
      var report = manager.report(entry.getKey(), from, to, null, null, null);
      assertThat(report.getPoints()).extracting(MetricAggregateResponse::getEventType)
          .containsExactlyInAnyOrderElementsOf(entry.getValue());
      verify(service).aggregate(from, to, null, null, null, false, "hr", List.of(), List.of());
      verify(service, never()).capture(any(), any(), anyList());
      if (entry.getKey().equals("overview")) {
        verify(service).daily(from, to, null, null, null, false, "hr", List.of(), List.of());
      } else {
        verify(service, never()).daily(any(), any(), any(), any(), any(), anyBoolean(), any(), anyList(), anyList());
      }
      if (entry.getKey().equals("ai-effect")) {
        verify(service).parseCoverage(from, to, null, null, null, false, "hr", List.of(), List.of());
        assertThat(report.getParseCoverage()).isNotNull();
      } else {
        verify(service, never()).parseCoverage(any(), any(), any(), any(), any(), anyBoolean(), any(), anyList(), anyList());
      }
    }
  }

  private static void projectionReady(MetricApplicationManager manager) {
    var capture = mock(MetricCaptureService.class);
    when(capture.status()).thenReturn(new MetricProjectionStatusDTO(Instant.parse("2026-09-07T00:00:00Z"), null));
    ReflectionTestUtils.setField(manager, "captureService", capture);
    ReflectionTestUtils.setField(manager, "captureProperties", new MetricCaptureProperties());
  }
}

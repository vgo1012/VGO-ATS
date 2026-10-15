package com.ezaigc.hr.reporting.service;

import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.reporting.domain.entity.MetricCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import com.ezaigc.hr.reporting.mapper.MetricCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.service.impl.MetricCaptureServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MetricCaptureServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
  private final MetricCaptureCheckpointMapper checkpoints = mock(MetricCaptureCheckpointMapper.class);
  private final MetricCaptureServiceImpl service = new MetricCaptureServiceImpl();

  MetricCaptureServiceTest() {
    ReflectionTestUtils.setField(service, "baseMapper", checkpoints);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    when(checkpoints.advance(any(), anyString(), any())).thenReturn(1);
  }

  @Test
  void persistsFixedUpperBoundAndCompletesOnlyAfterLastBatch() {
    var row = checkpoint(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL);
    when(checkpoints.lock(anyString())).thenReturn(row);
    when(checkpoints.upperResumeIngestedId()).thenReturn("b");
    when(checkpoints.nextResumeIngestedIds(MIN_ID, "b", 1)).thenReturn(List.of("a"));
    assertThat(service.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 1)).isEqualTo(1);
    assertThat(row.getLastSourceId()).isEqualTo("a");
    assertThat(row.getCycleUpperId()).isEqualTo("b");
    assertThat(row.getLastCompletedAt()).isNull();
    when(checkpoints.nextResumeIngestedIds("a", "b", 1)).thenReturn(List.of("b"));
    service.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 1);
    assertThat(row.getLastCompletedAt()).isEqualTo(NOW);
    assertThat(row.getCycleUpperId()).isNull();
    verify(checkpoints).upperResumeIngestedId();
    verify(checkpoints).captureResumeIngestedRange(List.of("a"));
    verify(checkpoints).captureResumeIngestedRange(List.of("b"));
  }

  @Test
  void projectionFailureNeverAdvancesCheckpoint() {
    var row = checkpoint(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL);
    when(checkpoints.lock(anyString())).thenReturn(row);
    when(checkpoints.upperResumeIngestedId()).thenReturn("a");
    when(checkpoints.nextResumeIngestedIds(MIN_ID, "a", 5)).thenReturn(List.of("a"));
    when(checkpoints.captureResumeIngestedRange(List.of("a"))).thenThrow(new IllegalStateException("projection failed"));
    assertThatThrownBy(() -> service.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 5))
        .hasMessage("projection failed");
    verify(checkpoints, never()).advance(any(), anyString(), any());
  }

  @Test
  void reconciliationStartsAgainAndEmptySourcesComplete() {
    var row = checkpoint(MetricEventType.RESUME_INGESTED, MetricCaptureLane.RECONCILIATION);
    row.setLastSourceId("z");
    when(checkpoints.lock(anyString())).thenReturn(row);
    when(checkpoints.upperResumeIngestedId()).thenReturn(null);
    service.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.RECONCILIATION, 5);
    verify(checkpoints).nextResumeIngestedIds(MIN_ID, MIN_ID, 5);
    assertThat(row.getLastCompletedAt()).isEqualTo(NOW);
    assertThat(row.getLastSourceId()).isEqualTo(MIN_ID);
  }

  @Test
  void statusRequiresAllSevenSourcesAndKeepsMinimumCompletedCycle() {
    var rows = new ArrayList<MetricCaptureCheckpointEntity>();
    when(checkpoints.checkpoints()).thenReturn(rows);
    assertThat(service.status().getDataUpdatedAt()).isNull();
    for (var source : MetricEventType.values()) {
      var row = checkpoint(source, MetricCaptureLane.INCREMENTAL);
      row.setLastCompletedAt(NOW);
      rows.add(row);
    }
    rows.getFirst().setLastCompletedAt(NOW.minusSeconds(120));
    assertThat(service.status().getDataUpdatedAt()).isEqualTo(NOW.minusSeconds(120));
    assertThat(service.status().getReconciliationUpdatedAt()).isNull();
  }

  @Test
  void refusesUnboundedBatchSize() {
    for (int limit : List.of(0, 1001)) {
      assertThatThrownBy(() -> service.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, limit))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verify(checkpoints, never()).lock(anyString());
  }

  private static MetricCaptureCheckpointEntity checkpoint(MetricEventType source, MetricCaptureLane lane) {
    var row = new MetricCaptureCheckpointEntity();
    row.setCheckpointName(source.getCode() + ':' + lane.getCode());
    row.setSourceCode(source.getCode());
    row.setLane(lane.getCode());
    row.setLastSourceId(MIN_ID);
    return row;
  }
}

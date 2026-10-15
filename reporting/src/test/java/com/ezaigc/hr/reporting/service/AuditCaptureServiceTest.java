package com.ezaigc.hr.reporting.service;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.reporting.domain.entity.AuditCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;
import com.ezaigc.hr.reporting.mapper.AuditCaptureCheckpointMapper;
import com.ezaigc.hr.reporting.service.impl.AuditCaptureServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditCaptureServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-03T08:00:00Z");
  private static final String FIRST_ID = "01KFIRST000000000000000000";
  private static final String LAST_ID = "01KLAST0000000000000000000";

  private final AuditCaptureCheckpointMapper checkpointMapper =
      mock(AuditCaptureCheckpointMapper.class);
  private final AuditCaptureService service = service();

  @Test
  void capturesOnlyTheSelectedBoundedRangeBeforeAdvancingTheCheckpoint() {
    AuditCaptureCheckpointEntity checkpoint =
        checkpoint(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, MIN_ID, 7);
    when(checkpointMapper.lock(checkpoint.getCheckpointName())).thenReturn(checkpoint);
    when(checkpointMapper.nextIdentityIds(MIN_ID, 200)).thenReturn(List.of(FIRST_ID, LAST_ID));
    when(checkpointMapper.captureIdentityRange(MIN_ID, LAST_ID)).thenReturn(2);
    when(checkpointMapper.advance(
            checkpoint.getCheckpointName(), LAST_ID, 2, 7, SYSTEM_ACTOR_ID, NOW))
        .thenReturn(1);

    assertThat(service.captureNext(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 200))
        .isEqualTo(2);

    var ordered = inOrder(checkpointMapper);
    ordered.verify(checkpointMapper).nextIdentityIds(MIN_ID, 200);
    ordered.verify(checkpointMapper).captureIdentityRange(MIN_ID, LAST_ID);
    ordered
        .verify(checkpointMapper)
        .advance(checkpoint.getCheckpointName(), LAST_ID, 2, 7, SYSTEM_ACTOR_ID, NOW);
  }

  @Test
  void captureFailureLeavesTheCheckpointForRetry() {
    AuditCaptureCheckpointEntity checkpoint =
        checkpoint(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, MIN_ID, 7);
    when(checkpointMapper.lock(checkpoint.getCheckpointName())).thenReturn(checkpoint);
    when(checkpointMapper.nextIdentityIds(MIN_ID, 200)).thenReturn(List.of(LAST_ID));
    RuntimeException failure = new RuntimeException("database unavailable");
    when(checkpointMapper.captureIdentityRange(MIN_ID, LAST_ID)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                service.captureNext(
                    AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 200))
        .isSameAs(failure);

    verify(checkpointMapper, never())
        .advance(checkpoint.getCheckpointName(), LAST_ID, 1, 7, SYSTEM_ACTOR_ID, NOW);
  }

  @Test
  void reconciliationRestartsAfterCompletingOneBoundedCycle() {
    AuditCaptureCheckpointEntity checkpoint =
        checkpoint(AuditCaptureSource.IDENTITY, AuditCaptureLane.RECONCILIATION, LAST_ID, 9);
    when(checkpointMapper.lock(checkpoint.getCheckpointName())).thenReturn(checkpoint);
    when(checkpointMapper.nextIdentityIds(LAST_ID, 50)).thenReturn(List.of());
    when(checkpointMapper.reset(
            checkpoint.getCheckpointName(), MIN_ID, 9, SYSTEM_ACTOR_ID, NOW))
        .thenReturn(1);

    assertThat(
            service.captureNext(
                AuditCaptureSource.IDENTITY, AuditCaptureLane.RECONCILIATION, 50))
        .isZero();

    verify(checkpointMapper)
        .reset(checkpoint.getCheckpointName(), MIN_ID, 9, SYSTEM_ACTOR_ID, NOW);
  }

  @Test
  void incrementalCursorDoesNotRestartWhenCaughtUp() {
    AuditCaptureCheckpointEntity checkpoint =
        checkpoint(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, LAST_ID, 9);
    when(checkpointMapper.lock(checkpoint.getCheckpointName())).thenReturn(checkpoint);
    when(checkpointMapper.nextIdentityIds(LAST_ID, 50)).thenReturn(List.of());

    assertThat(service.captureNext(AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 50))
        .isZero();

    verify(checkpointMapper, never())
        .reset(checkpoint.getCheckpointName(), MIN_ID, 9, SYSTEM_ACTOR_ID, NOW);
  }

  @Test
  void rejectsUnboundedBatchSizes() {
    assertThatThrownBy(
            () ->
                service.captureNext(
                    AuditCaptureSource.IDENTITY, AuditCaptureLane.INCREMENTAL, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.captureNext(
                    AuditCaptureSource.IDENTITY,
                    AuditCaptureLane.INCREMENTAL,
                    AuditCaptureService.MAX_BATCH_SIZE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void captureCodesMatchTheStableEnumNames() {
    assertThat(AuditCaptureSource.values())
        .allSatisfy(source -> assertThat(source.getCode()).isEqualTo(source.name()));
    assertThat(AuditCaptureLane.values())
        .allSatisfy(lane -> assertThat(lane.getCode()).isEqualTo(lane.name()));
  }

  private static AuditCaptureCheckpointEntity checkpoint(
      AuditCaptureSource source, AuditCaptureLane lane, String lastSourceId, int version) {
    AuditCaptureCheckpointEntity checkpoint = new AuditCaptureCheckpointEntity();
    checkpoint.setCheckpointName(source.checkpointName(lane));
    checkpoint.setSourceCode(source.getCode());
    checkpoint.setLane(lane.getCode());
    checkpoint.setLastSourceId(lastSourceId);
    checkpoint.setVersion(version);
    return checkpoint;
  }

  private AuditCaptureService service() {
    AuditCaptureServiceImpl implementation = new AuditCaptureServiceImpl();
    ReflectionTestUtils.setField(implementation, "baseMapper", checkpointMapper);
    ReflectionTestUtils.setField(
        implementation, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    return implementation;
  }
}

package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdFactPersistenceManagerTest {

  private static final Instant NOW = Instant.parse("2026-09-05T07:00:00Z");
  private static final String VERSION_ID = "VVVVVVVVVVVVVVVVVVVVVVVVVV";
  private static final String RUN_ID = "RRRRRRRRRRRRRRRRRRRRRRRRRR";
  private static final String SNAPSHOT_ID = "SSSSSSSSSSSSSSSSSSSSSSSSSS";

  private JdFactPersistenceManager manager;
  private JdParsingPersistenceService persistence;

  @BeforeEach
  void setUp() {
    manager = new JdFactPersistenceManager();
    persistence = mock(JdParsingPersistenceService.class);
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> SNAPSHOT_ID);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "persistence", persistence);
  }

  @Test
  void storesFirstSnapshotOnlyWhenEveryPersistenceInvariantHolds() {
    JdFactSnapshotEntity stored = snapshot(SNAPSHOT_ID, 1);
    when(persistence.clearCurrent(VERSION_ID, NOW)).thenReturn(0);
    when(persistence.insertSnapshot(
            eq(SNAPSHOT_ID),
            eq(VERSION_ID),
            eq(RUN_ID),
            eq(1),
            eq("jd-fact-v1"),
            eq("{}"),
            isNull(),
            eq(NOW)))
        .thenReturn(1);
    when(persistence.findSnapshot(SNAPSHOT_ID)).thenReturn(stored);

    JdFactSnapshotEntity result = manager.store(VERSION_ID, RUN_ID, "jd-fact-v1", "{}");

    assertThat(result).isSameAs(stored);
  }

  @Test
  void rejectsMissingClearWhenReplacingCurrentSnapshot() {
    when(persistence.findCurrentForUpdate(VERSION_ID)).thenReturn(snapshot("previous", 2));
    when(persistence.clearCurrent(VERSION_ID, NOW)).thenReturn(0);

    assertThatIllegalStateException()
        .isThrownBy(() -> manager.store(VERSION_ID, RUN_ID, "jd-fact-v1", "{}"))
        .withMessageContaining("清除当前 JD 事实快照影响行数异常");
  }

  @Test
  void rejectsSnapshotInsertThatDoesNotAffectExactlyOneRow() {
    when(persistence.clearCurrent(VERSION_ID, NOW)).thenReturn(0);
    when(persistence.insertSnapshot(
            anyString(),
            anyString(),
            anyString(),
            anyInt(),
            anyString(),
            anyString(),
            isNull(),
            any()))
        .thenReturn(0);

    assertThatIllegalStateException()
        .isThrownBy(() -> manager.store(VERSION_ID, RUN_ID, "jd-fact-v1", "{}"))
        .withMessageContaining("创建 JD 事实快照影响行数异常");
  }

  @Test
  void rejectsSnapshotThatCannotBeReadBackByGeneratedIdentifier() {
    when(persistence.clearCurrent(VERSION_ID, NOW)).thenReturn(0);
    when(persistence.insertSnapshot(
            anyString(),
            anyString(),
            anyString(),
            anyInt(),
            anyString(),
            anyString(),
            isNull(),
            any()))
        .thenReturn(1);
    when(persistence.findSnapshot(SNAPSHOT_ID)).thenReturn(snapshot("different", 1));

    assertThatIllegalStateException()
        .isThrownBy(() -> manager.store(VERSION_ID, RUN_ID, "jd-fact-v1", "{}"))
        .withMessageContaining("写入后无法按标识回读");
    verify(persistence).findSnapshot(SNAPSHOT_ID);
  }

  private static JdFactSnapshotEntity snapshot(String id, int snapshotVersion) {
    JdFactSnapshotEntity value = new JdFactSnapshotEntity();
    value.setId(id);
    value.setSnapshotVersion(snapshotVersion);
    return value;
  }
}

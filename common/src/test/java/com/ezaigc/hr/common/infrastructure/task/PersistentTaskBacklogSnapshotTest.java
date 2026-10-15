package com.ezaigc.hr.common.infrastructure.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PersistentTaskBacklogSnapshotTest {
  @Test
  void combinesCountsAndKeepsTheOldestReadyTime() {
    var first = new PersistentTaskBacklogSnapshot(
        2, Instant.parse("2026-09-18T00:02:00Z"));
    var second = new PersistentTaskBacklogSnapshot(
        3, Instant.parse("2026-09-18T00:01:00Z"));

    assertThat(first.plus(second)).isEqualTo(new PersistentTaskBacklogSnapshot(
        5, Instant.parse("2026-09-18T00:01:00Z")));
    assertThat(PersistentTaskBacklogSnapshot.empty().plus(first)).isEqualTo(first);
  }

  @Test
  void rejectsSnapshotsThatCouldMisrepresentAnEmptyQueue() {
    assertThatThrownBy(() -> new PersistentTaskBacklogSnapshot(-1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PersistentTaskBacklogSnapshot(1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PersistentTaskBacklogSnapshot(
        0, Instant.parse("2026-09-18T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 当前已到期且可领取的持久任务数量及其中最早可领取时间。 */
@Getter
@EqualsAndHashCode
@ToString
public final class PersistentTaskBacklogSnapshot {
  private final long readyCount;
  private final Instant oldestReadyAt;

  public PersistentTaskBacklogSnapshot(long readyCount, Instant oldestReadyAt) {
    if (readyCount < 0) throw new IllegalArgumentException("readyCount must not be negative");
    if (readyCount == 0 && oldestReadyAt != null) {
      throw new IllegalArgumentException("empty backlog must not have an oldest time");
    }
    if (readyCount > 0 && oldestReadyAt == null) {
      throw new IllegalArgumentException("non-empty backlog requires an oldest time");
    }
    this.readyCount = readyCount;
    this.oldestReadyAt = oldestReadyAt;
  }

  public PersistentTaskBacklogSnapshot plus(PersistentTaskBacklogSnapshot other) {
    if (other == null) throw new IllegalArgumentException("backlog snapshot is required");
    Instant oldest = oldestReadyAt;
    if (oldest == null || other.oldestReadyAt != null && other.oldestReadyAt.isBefore(oldest)) {
      oldest = other.oldestReadyAt;
    }
    return new PersistentTaskBacklogSnapshot(Math.addExact(readyCount, other.readyCount), oldest);
  }

  public static PersistentTaskBacklogSnapshot empty() {
    return new PersistentTaskBacklogSnapshot(0, null);
  }
}

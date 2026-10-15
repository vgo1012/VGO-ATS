package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import com.ezaigc.hr.common.infrastructure.task.enums.ScheduledScanState;
import lombok.Value;

@Value
public class ScheduledScanSnapshot {
    String runId;
    ScheduledScanState state;
    Instant queuedAt;
    Instant startedAt;
    Instant finishedAt;
    Long durationMillis;
    String errorCode;
}

package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import lombok.Value;

@Value
public class ScheduledScanResponse {
    String runId;
    String state;
    Instant queuedAt;
    Instant startedAt;
    Instant finishedAt;
    Long durationMillis;
    String errorCode;
}

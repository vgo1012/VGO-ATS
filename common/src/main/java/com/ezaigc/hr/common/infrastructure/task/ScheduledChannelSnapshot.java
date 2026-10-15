package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ScheduledChannelSnapshot {
    String code;
    Instant lastTriggeredAt;
    Instant lastCompletedAt;
    long triggers;
    long coalesced;
    long rejected;
    long failed;
    int queued;
    int running;
    List<ScheduledScanSnapshot> recentScans;
}

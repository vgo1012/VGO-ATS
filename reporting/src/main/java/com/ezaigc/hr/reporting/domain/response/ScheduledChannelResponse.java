package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ScheduledChannelResponse {
    String code;
    Instant lastTriggeredAt;
    Instant lastCompletedAt;
    long triggers;
    long coalesced;
    long rejected;
    long failed;
    int queued;
    int running;
    List<ScheduledScanResponse> recentScans;
}

package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ScheduledTaskResponse {
    String code;
    String name;
    String module;
    boolean enabled;
    String scheduleType;
    String expression;
    String zone;
    Long intervalMillis;
    Instant nextCronAt;
    List<String> businessTypes;
    List<ScheduledChannelResponse> channels;
    long idleBackoffMaxMillis;
    String persistentBacklogStatus;
    Long persistentReadyCount;
    Instant oldestReadyAt;
}

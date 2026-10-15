package com.ezaigc.hr.reporting.domain.dto;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class ScheduledTaskDefinitionDTO {
    String code;
    String name;
    String module;
    boolean enabled;
    String scheduleType;
    String expression;
    String zone;
    Long intervalMillis;
    Instant nextCronAt;
    List<String> channels;
    List<String> businessTypes;
    long idleBackoffMaxMillis;
}

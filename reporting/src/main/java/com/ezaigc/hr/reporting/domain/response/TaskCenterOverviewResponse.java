package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class TaskCenterOverviewResponse {
    String instanceId;
    Instant observedSince;
    Instant sampledAt;
    List<ScheduledTaskResponse> tasks;
}
